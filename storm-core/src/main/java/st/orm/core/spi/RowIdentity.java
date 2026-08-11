/*
 * Copyright 2024 - 2026 the original author or authors.
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
package st.orm.core.spi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import st.orm.Entity;
import st.orm.Ref;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;

/**
 * Normalizes primary key values to row identity: the value the SQL layer binds in a WHERE clause.
 *
 * <p>A scalar key is its own row identity. An entity-typed key carries more than the key column: it holds every
 * non-key column of the entity, and of each entity its foreign keys reach. Structural equality on it is therefore
 * wider than the identity it denotes: two representations of the same row diverge whenever a non-key column does
 * not round-trip bit-exact (a second-precision timestamp column, a database-managed column, a numeric scale
 * difference). Normalization reduces such a key to its scalar form: an entity contributes only its own primary
 * key, applied recursively, a ref contributes the key it wraps, and a composite key record contributes its
 * components, normalized element-wise.
 * Composite keys are detected through the pluggable reflection support, covering Java records and Kotlin data
 * classes alike.</p>
 *
 * <p>Whether a class requires normalization is decided once per class, from its declared component types, and
 * cached. Values of classes whose key graph cannot carry non-key state (scalars, and composite key records
 * containing only scalars) are returned unchanged, so lookups keyed by such values pay a single cached class probe
 * and no allocation.</p>
 *
 * @since 1.13
 */
public final class RowIdentity {

    private RowIdentity() {
    }

    /** Defers provider resolution to first use, keeping class initialization free of provider lookups. */
    private static final class ReflectionHolder {
        static final ORMReflection REFLECTION = Providers.getORMReflection();
    }

    /** Decided once per class: whether values of this class can carry non-key state that normalization must strip. */
    private static final ClassValue<Boolean> NORMALIZATION_REQUIRED = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return requiresNormalization(type, new HashSet<>());
        }
    };

    private static boolean requiresNormalization(Class<?> type, Set<Class<?>> visited) {
        if (Ref.class.isAssignableFrom(type) || Entity.class.isAssignableFrom(type)) {
            return true;
        }
        if (!visited.add(type)) {
            // A self-referential component type contributes no component types beyond those already inspected.
            return false;
        }
        Optional<RecordType> recordType = ReflectionHolder.REFLECTION.findRecordType(type);
        if (recordType.isEmpty()) {
            return false;
        }
        for (RecordField field : recordType.get().fields()) {
            if (requiresNormalization(field.type(), visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether values of the given class require normalization to obtain their row identity.
     *
     * <p>The decision is made once per class and cached. Values of classes for which this method returns
     * {@code false} are their own row identity: {@link #normalize(Object)} returns them unchanged, so callers that
     * resolve this decision at type level can skip normalization entirely for such values.</p>
     *
     * @param type the class of the primary key value.
     * @return {@code true} if values of this class carry non-key state that normalization must strip.
     */
    public static boolean requiresNormalization(Class<?> type) {
        return NORMALIZATION_REQUIRED.get(type);
    }

    /**
     * Returns the hash code of a ref identity: its type and the row identity of its id.
     *
     * <p>Shared by the ref implementations so that the hash contract cannot drift between them: every
     * implementation of an equal ref identity must produce this value.</p>
     *
     * @param type the ref's record type.
     * @param rowId the row identity of the ref's id.
     * @return the identity hash code.
     */
    public static int hash(@Nullable Class<?> type, @Nullable Object rowId) {
        return (31 + Objects.hashCode(type)) * 31 + Objects.hashCode(rowId);
    }

    /**
     * Returns whether a ref identity, given as its type and the row identity of its id, equals the identity of the
     * given ref.
     *
     * <p>Shared by the ref implementations so that the equality contract cannot drift between them; implementations
     * may only shortcut this comparison when both sides are known to hold their identity in the same form.</p>
     *
     * @param type the ref's record type.
     * @param rowId the row identity of the ref's id.
     * @param other the ref to compare against.
     * @return {@code true} if both describe the same database row.
     */
    public static boolean refEquals(@Nullable Class<?> type, @Nullable Object rowId, Ref<?> other) {
        return Objects.equals(type, other.type()) && Objects.equals(rowId, normalize(other.id()));
    }

    /**
     * Returns the row identity of the given primary key value.
     *
     * <p>Values of classes that cannot carry non-key state are returned as-is; equal inputs always map to equal
     * results, and results of the same key class are directly comparable with each other.</p>
     *
     * @param id the primary key value to normalize, may be {@code null}.
     * @return the row identity of the value, or the value itself when its class requires no normalization.
     */
    @Nullable
    public static Object normalize(@Nullable Object id) {
        if (id == null || !NORMALIZATION_REQUIRED.get(id.getClass())) {
            return id;
        }
        if (id instanceof Ref<?> ref) {
            return normalize(ref.id());
        }
        if (id instanceof Entity<?> entity) {
            return normalize(entity.id());
        }
        RecordType recordType = ReflectionHolder.REFLECTION.getRecordType(id.getClass());
        int fieldCount = recordType.fields().size();
        List<Object> normalized = new ArrayList<>(fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            normalized.add(normalize(ReflectionHolder.REFLECTION.getRecordValue(id, i)));
        }
        return normalized;
    }
}
