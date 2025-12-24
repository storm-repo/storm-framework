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
 * <p>Dirty field detection is based on value comparison using {@link ORMReflection}. For performance reasons,
 * computed dirty field sets are cached by their bitset representation and reused across updates.</p>
 *
 * <p>If no cache entry is available, dirty tracking is disabled, or the update shape limit is exceeded,
 * all fields are treated as dirty. If the cached instance is identical to the current instance, the entity
 * is considered clean.</p>
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

    private static final UpdateMode DEFAULT_UPDATE_MODE = UpdateMode.valueOf(System.getProperty("storm.update.defaultMode", ENTITY.toString()).trim().toUpperCase());
    private static final int MAX_SHAPES = Math.max(1, parseInt(System.getProperty("storm.update.maxShapes", "5")));
    private static final ORMReflection REFLECTION = Providers.getORMReflection();
    private static final Optional<Set<Metamodel<? extends Data, ?>>> CLEAN = Optional.empty();
    private static final Optional<Set<Metamodel<? extends Data, ?>>> DIRTY = Optional.of(Set.of());

    static {
        LOGGER.info("Using default update mode: {}.", DEFAULT_UPDATE_MODE);
    }

    private final Model<E, ID> model;
    private final UpdateMode updateMode;
    private final RecordField[] updatableFields;
    private final ConcurrentMap<BitSet, Set<Metamodel<? extends Data, ?>>> dirtyFieldsCache = new ConcurrentHashMap<>();

    DirtySupport(@Nonnull Model<E, ID> model) {
        this.model = model;
        this.updateMode = getUpdateMode(model.recordType());
        this.updatableFields = model.updatableFields().toArray(RecordField[]::new);  // Only check updatable fields.
    }

    /**
     * Returns the effective update mode for entities of the given record type.
     *
     * <p>If the record type is annotated with {@link DynamicUpdate}, the annotation value takes precedence. Otherwise,
     * the default update mode configured via the system property {@code storm.update.defaultMode} is used.</p>
     *
     * @param recordType the entity record type.
     * @return the effective {@link UpdateMode} to apply for this record type.
     */
    public static UpdateMode getUpdateMode(@Nonnull RecordType recordType) {
        DynamicUpdate dynamicUpdate = recordType.getAnnotation(DynamicUpdate.class);
        return dynamicUpdate == null ? DEFAULT_UPDATE_MODE : dynamicUpdate.value();
    }

    /**
     * Returns the maximum number of distinct update shapes that may be generated when performing dynamic updates.
     *
     * <p>This value is configured via the system property {@code storm.update.maxShapes}. If the limit is exceeded,
     * dynamic dirty checking is bypassed and the update falls back to a full-entity update.</p>
     *
     * @return the maximum number of allowed update shapes.
     */
    public static int getMaxShapes() {
        return MAX_SHAPES;
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
                if (a == b) continue;
                if (a instanceof Entity<?> e && b instanceof Entity<?> c) {
                    if (Objects.equals(e.id(), c.id())) continue;
                } else if (Objects.equals(a, b)) {
                    continue;
                }
                return DIRTY;
            }
            return CLEAN;
        }
        // The entity instance changed, but the field content might be the same.
        BitSet dirtyFields = new BitSet(updatableFields.length);
        for (int i = 0; i < updatableFields.length; i++) {
            // Note that this is a shallow check, meaning we only look at top-level fields.
            var field = updatableFields[i];
            var entityField = REFLECTION.invoke(field, entity);
            var cachedField = REFLECTION.invoke(field, cached);
            if (entityField == cachedField) {   // Fastest check.
                continue;
            }
            if (entityField instanceof Entity<?> e && cachedField instanceof Entity<?> c) { // Only look at IDs when comparing entities.
                if (Objects.equals(e.id(), c.id())) {
                    continue;
                }
            } else if (Objects.equals(entityField, cachedField)) {  // Also covers Refs.
                continue;
            }
            // Dirty field.
            dirtyFields.set(i);
        }
        if (dirtyFields.isEmpty()) {
            // The entity instance changed, but the field content is the same.
            return CLEAN;
        }
        BitSet key = (BitSet) dirtyFields.clone();  // BitSet is mutable. Just to prevent any issues.
        // Update mode is FIELD. Report dirty fields.
        return Optional.of(dirtyFieldsCache.computeIfAbsent(key, bits -> {
            Set<Metamodel<? extends Data, ?>> set = new HashSet<>();
            for (int fieldIndex = bits.nextSetBit(0);
                 fieldIndex >= 0;
                 fieldIndex = bits.nextSetBit(fieldIndex + 1)) {
                set.add(model.getMetamodel(updatableFields[fieldIndex]));
            }
            // Always include the version field if present.
            model.versionField().ifPresent(versionField -> set.add(model.getMetamodel(versionField)));
            return Set.copyOf(set);
        }));
    }
}
