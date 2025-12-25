/*
 * Copyright 2024 - 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package st.orm.core.repository.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import st.orm.Data;
import st.orm.DirtyCheck;
import st.orm.DynamicUpdate;
import st.orm.Entity;
import st.orm.Metamodel;
import st.orm.UpdateMode;
import st.orm.core.spi.EntityCache;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.core.template.Model;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.lang.Integer.parseInt;
import static st.orm.UpdateMode.ENTITY;
import static st.orm.UpdateMode.OFF;

/**
 * Determines which fields of an entity are considered dirty for update purposes.
 *
 * <p>This class compares the current entity instance with its cached counterpart and derives the set of fields
 * that have changed, according to the configured {@link UpdateMode} and update shape limits.</p>
 *
 * <p>The default update behavior is controlled by the system property
 * {@code storm.update.defaultMode}, which selects the {@link UpdateMode} to apply when no explicit mode
 * is provided.</p>
 *
 * <p>When dynamic updates are enabled, the system property {@code storm.update.maxShapes} limits the number
 * of distinct update shapes that may be generated. If this limit is exceeded, the update falls back to a
 * full-entity update to prevent excessive statement fan-out.</p>
 *
 * <p>Dirty field detection is, by default, based on <em>instance identity</em> comparison. A field is considered
 * dirty as soon as its reference changes, which provides predictable and bounded performance characteristics.</p>
 *
 * <p>Value-based dirty checking can be enabled by setting the system property
 * {@code storm.update.dirtyCheck=value}. In this mode, fields are compared using semantic equality after a
 * fast reference check.</p>
 *
 * <p>The dirty check strategy can also be configured per entity using {@link DynamicUpdate#dirtyCheck()}.</p>
 *
 * <p>When performing dynamic updates, the version field is always included if present.</p>
 *
 * @param <E> the entity type
 * @param <ID> the entity identifier type
 * @since 1.7
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class DirtySupport<E extends Entity<ID>, ID> {

    // Implementation note: dirty field checks can be performed efficiently due to the immutable record types.

    private static final Logger LOGGER = LoggerFactory.getLogger("st.orm.update");

    private static final UpdateMode DEFAULT_UPDATE_MODE =
            UpdateMode.valueOf(System.getProperty("storm.update.defaultMode", "entity").trim().toUpperCase());

    private static final int MAX_SHAPES =
            Math.max(1, parseInt(System.getProperty("storm.update.maxShapes", "5")));

    private static final DirtyCheck DEFAULT_DIRTY_CHECK =
            DirtyCheck.valueOf(System.getProperty("storm.update.dirtyCheck", "instance").trim().toUpperCase());

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private static final Optional<Set<Metamodel<? extends Data, ?>>> CLEAN = Optional.empty();
    private static final Optional<Set<Metamodel<? extends Data, ?>>> DIRTY = Optional.of(Set.of());

    static {
        LOGGER.info("Using default update mode: {}.", DEFAULT_UPDATE_MODE);
        LOGGER.info("Using default dirty check: {}.", DEFAULT_DIRTY_CHECK);
    }

    private final Model<E, ID> model;
    private final UpdateMode updateMode;
    private final DirtyCheck dirtyCheck;
    private final RecordField[] updatableFields;
    private final ConcurrentMap<BitSet, Set<Metamodel<? extends Data, ?>>> dirtyFieldsCache = new ConcurrentHashMap<>();

    DirtySupport(@Nonnull Model<E, ID> model) {
        this.model = model;
        RecordType recordType = model.recordType();
        this.updateMode = getUpdateMode(recordType);
        this.dirtyCheck = getDirtyCheck(recordType);
        this.updatableFields = model.updatableFields().toArray(RecordField[]::new);  // Only check updatable fields.
    }

    /**
     * Returns the effective {@link UpdateMode} for the given record type.
     *
     * <p>If the record type is annotated with {@link DynamicUpdate}, the annotation value is used. Otherwise, the
     * globally configured default update mode applies.</p>
     *
     * <p>The global default update mode can be configured using the system property {@code storm.update.defaultMode}.
     * If the property is not set, the default is {@link UpdateMode#ENTITY}.</p>
     *
     * @param recordType the entity record type.
     * @return the effective update mode for the given record type.
     */
    public static UpdateMode getUpdateMode(@Nonnull RecordType recordType) {
        DynamicUpdate dynamicUpdate = recordType.getAnnotation(DynamicUpdate.class);
        return dynamicUpdate == null ? DEFAULT_UPDATE_MODE : dynamicUpdate.value();
    }

    /**
     * Returns the effective {@link DirtyCheck} strategy for the given record type.
     *
     * <p>If the record type is annotated with {@link DynamicUpdate} and the {@link DynamicUpdate#dirtyCheck()}
     * attribute is set to a value other than {@link DirtyCheck#DEFAULT}, that value is used.</p>
     *
     * <p>Otherwise, the globally configured dirty check strategy applies.</p>
     *
     * <p>The global default dirty check strategy can be configured using the system property
     * {@code storm.update.dirtyCheck}. If the property is not set, the default strategy is
     * {@link DirtyCheck#INSTANCE}.</p>
     *
     * @param recordType the entity record type.
     * @return the effective dirty check strategy for the given record type.
     */
    public static DirtyCheck getDirtyCheck(@Nonnull RecordType recordType) {
        DynamicUpdate dynamicUpdate = recordType.getAnnotation(DynamicUpdate.class);
        if (dynamicUpdate == null) {
            return DEFAULT_DIRTY_CHECK;
        }
        DirtyCheck configured = dynamicUpdate.dirtyCheck();
        return configured == DirtyCheck.DEFAULT ? DEFAULT_DIRTY_CHECK : configured;
    }

    /**
     * Returns the maximum number of distinct update shapes that may be generated when
     * dynamic updates are enabled.
     *
     * <p>This value is configured via the system property {@code storm.update.maxShapes}.
     * It limits the number of different column combinations that Storm will generate
     * UPDATE statements for.</p>
     *
     * <p>If the limit is exceeded, Storm falls back to a full-entity update to avoid
     * excessive statement fan-out and preserve batching efficiency.</p>
     *
     * @return the maximum number of allowed update shapes
     */
    public static int getMaxShapes() {
        return MAX_SHAPES;
    }

    /**
     * Compares two field values according to the configured dirty check strategy.
     *
     * <p>This method always performs a fast reference comparison first.</p>
     *
     * <p>If both values are {@link Entity} instances, they are compared by ID only, regardless of the configured dirty
     * check strategy.</p>
     *
     * <p>When using {@link DirtyCheck#INSTANCE}, differing references are considered dirty without invoking
     * {@code equals}.</p>
     *
     * <p>When using {@link DirtyCheck#VALUE}, semantic equality is used.</p>
     */
    private boolean fieldsEqual(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a instanceof Entity<?> e && b instanceof Entity<?> c) {
            return Objects.equals(e.id(), c.id());
        }
        if (dirtyCheck == DirtyCheck.INSTANCE) {
            return false;
        }
        return Objects.equals(a, b);
    }

    /**
     * Returns the dirty fields of the entity, an empty set if all fields must be regarded as dirty, or an empty
     * optional if the entity is not dirty.
     *
     * @param entity the entity to check.
     * @param cache the entity cache.
     * @return an optional containing the dirty fields, or an empty optional if the entity is not dirty.
     */
    Optional<Set<Metamodel<? extends Data, ?>>> getDirty(@Nonnull E entity,
                                                         @Nullable EntityCache<E, ID> cache) {
        if (updateMode == OFF) {
            // Update mode is OFF, treat all fields as dirty.
            return DIRTY;
        }
        if (cache == null) {
            // No cache available, assume all fields are dirty.
            return DIRTY;
        }
        var cached = cache.get(entity.id()).orElse(null);
        if (cached == null) {
            // Not cached, assume all fields are dirty.
            return DIRTY;
        }
        if (cached == entity) {
            // Cached, no fields are dirty.
            return CLEAN;
        }
        if (updateMode == ENTITY) {
            for (RecordField field : updatableFields) {
                var a = REFLECTION.invoke(field, entity);
                var b = REFLECTION.invoke(field, cached);
                if (fieldsEqual(a, b)) {
                    continue;
                }
                return DIRTY;
            }
            return CLEAN;
        }
        BitSet dirtyFields = new BitSet(updatableFields.length);
        for (int i = 0; i < updatableFields.length; i++) {
            var field = updatableFields[i];
            var a = REFLECTION.invoke(field, entity);
            var b = REFLECTION.invoke(field, cached);
            if (fieldsEqual(a, b)) {
                continue;
            }
            dirtyFields.set(i);
        }
        if (dirtyFields.isEmpty()) {
            // The entity instance changed, but the field content is the same.
            return CLEAN;
        }
        BitSet key = (BitSet) dirtyFields.clone(); // Defensive copy.
        return Optional.of(dirtyFieldsCache.computeIfAbsent(key, bits -> {
            Set<Metamodel<? extends Data, ?>> set = new HashSet<>();
            for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
                model.getColumns(updatableFields[i])
                        .forEach(column -> set.add(model.getMetamodel(column)));
            }
            model.versionField()
                    .map(model::getColumns)
                    .ifPresent(columns ->
                            columns.forEach(column -> set.add(model.getMetamodel(column)))
                    );
            return Set.copyOf(set);
        }));
    }
}