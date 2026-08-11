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
package st.orm.core.repository.impl;

import java.util.List;
import org.jspecify.annotations.Nullable;
import st.orm.PK;
import st.orm.core.spi.Instantiators;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.mapping.Instantiator;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;

/**
 * Rebuilds immutable records with individual components replaced.
 *
 * <p>Entities are records, so a value the database assigns can only be reflected by constructing a new instance.
 * Rebuilds construct through the generated metamodel code when a type registers an {@link Instantiator}, and fall
 * back to the canonical constructor otherwise.</p>
 *
 * @since 1.13
 */
final class RecordRebuilder {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private RecordRebuilder() {
    }

    /**
     * Rebuild metadata per record class: the record type with its canonical constructor made accessible once, and
     * the generated metamodel instantiator when one is registered, so rebuilds construct through generated code
     * rather than reflection.
     */
    private record RebuildType(RecordType recordType, @Nullable Instantiator<?> instantiator) {
        Object newInstance(Object[] args) {
            return instantiator != null ? instantiator.instantiate(args) : recordType.newInstance(args);
        }

        /**
         * Reads the record's component values in declaration order, through the generated deconstructor when the
         * metamodel registered one, so rebuilds run as generated code on both sides of the round trip.
         */
        @SuppressWarnings("unchecked")
        Object[] deconstruct(Object record) {
            if (instantiator != null) {
                Object[] args = ((Instantiator<Object>) instantiator).deconstruct(record);
                if (args != null) {
                    return args;
                }
            }
            List<RecordField> fields = recordType.fields();
            Object[] args = new Object[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                args[i] = REFLECTION.invoke(fields.get(i), record);
            }
            return args;
        }
    }

    private static final ClassValue<RebuildType> REBUILD_TYPES = new ClassValue<>() {
        @Override
        protected RebuildType computeValue(Class<?> type) {
            RecordType recordType = REFLECTION.getRecordType(type);
            recordType.constructor().trySetAccessible();
            return new RebuildType(recordType, Instantiators.find(type));
        }
    };

    private static final ClassValue<Integer> PRIMARY_KEY_INDEXES = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> type) {
            // Recognizing the type is delegated to the reflection provider, so Kotlin data classes resolve on the
            // same terms as Java records.
            return REFLECTION.findRecordType(type)
                    .map(recordType -> {
                        List<RecordField> fields = recordType.fields();
                        for (int i = 0; i < fields.size(); i++) {
                            if (fields.get(i).isAnnotationPresent(PK.class)) {
                                return i;
                            }
                        }
                        return -1;
                    })
                    .orElse(-1);
        }
    };

    /**
     * Returns the component index of the {@link PK} annotated component of the given type, or {@code -1} when the
     * type declares none or is not a recognized record type.
     *
     * <p>Resolved against the concrete type of an instance rather than the modelled entity type: a joined sealed
     * hierarchy models a base type that is not itself a record, while every instance is.</p>
     */
    static int primaryKeyIndex(Class<?> type) {
        return PRIMARY_KEY_INDEXES.get(type);
    }

    /**
     * Rebuilds the record with the component at the given path replaced, reconstructing nested records as needed.
     *
     * <p>Intermediate components along the path must be non-null: paths are only walked where a value was resolved
     * through the same path, and records are immutable.</p>
     */
    static Object withComponent(Object record, int[] path, @Nullable Object newValue) {
        return replace(record, path, 0, newValue);
    }

    /** Rebuilds the record with the component at the given index replaced. */
    static Object withComponent(Object record, int index, @Nullable Object newValue) {
        return replace(record, new int[] {index}, 0, newValue);
    }

    /** Replaces the component at {@code path[depth]}, descending into the nested record while the path continues. */
    private static Object replace(Object record,
                                  int[] path,
                                  int depth,
                                  @Nullable Object newValue) {
        RebuildType rebuildType = REBUILD_TYPES.get(record.getClass());
        Object[] args = rebuildType.deconstruct(record);
        int index = path[depth];
        args[index] = depth == path.length - 1
                ? newValue
                : replace(args[index], path, depth + 1, newValue);
        return rebuildType.newInstance(args);
    }
}
