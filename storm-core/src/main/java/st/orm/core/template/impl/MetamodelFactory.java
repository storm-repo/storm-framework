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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.AbstractMetamodel;
import st.orm.Data;
import st.orm.FK;
import st.orm.Metamodel;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.core.template.SqlTemplateException;
import st.orm.mapping.RecordField;

import static st.orm.core.template.impl.RecordReflection.getRecordField;
import static st.orm.core.template.impl.RecordReflection.getRefDataType;
import static st.orm.core.template.impl.RecordReflection.isRecord;

/**
 * Implementation that is used by the generated models.
 *
 * @since 1.4
 */
public final class MetamodelFactory {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private MetamodelFactory() {
        // Prevent instantiation
    }

    /**
     * Creates a new metamodel for the given record type.
     *
     * @param table the root table to create the metamodel for.
     * @return a new metamodel for the given record type.
     * @param <T> the root table type.
     */
    public static <T extends Data> Metamodel<T, T> root(@Nonnull Class<T> table) {
        return new AbstractMetamodel<>(table) { };
    }

    /**
     * Creates a new metamodel for the given root rootTable and path.
     *
     * <p>This method is typically used to manually create a metamodel for a component of a record, which can be useful
     * in cases where the metamodel can not be generated automatically, for example local records.</p>
     *
     * @param rootTable the root rootTable to create the metamodel for.
     * @param path a dot separated path starting from the root rootTable.
     * @return a new metamodel for the given root rootTable and path.
     * @param <T> the root rootTable type.
     * @param <E> the record component type of the designated component.
     * @throws PersistenceException if the metamodel cannot be created for the root rootTable and path.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Data, E> Metamodel<T, E> of(@Nonnull Class<T> rootTable, @Nonnull String path) {
        Class<E> componentType;
        String effectivePath;
        String effectiveComponent;
        Metamodel<T, ? extends Data> tableModel;
        boolean isColumn = false;
        if (path.isEmpty()) {
            componentType = (Class<E>) rootTable;
            effectivePath = "";
            effectiveComponent = "";
            tableModel = null;
        } else {
            try {
                RecordField field = getRecordField(rootTable, path);
                if (Ref.class.isAssignableFrom(field.type())) {
                    int index = path.lastIndexOf('.');
                    effectivePath = index == -1 ? "" : path.substring(0, path.lastIndexOf('.')); // Remove last component.
                    effectiveComponent = field.name();
                    componentType = (Class<E>) getRefDataType(field);
                    isColumn = true;
                } else {
                    effectivePath = path;
                    effectiveComponent = field.name();
                    componentType = (Class<E>) field.type();
                    if (!isRecord(field.type()) || field.isAnnotationPresent(FK.class)) {
                        isColumn = true;
                    }
                    do {
                        int index = effectivePath.lastIndexOf('.');
                        effectivePath = effectivePath.substring(0, index == -1 ? 0 : index); // Remove last component.
                        if (effectivePath.isEmpty()) {
                            break;
                        }
                        field = getRecordField(rootTable, effectivePath);
                        if (field.isAnnotationPresent(FK.class)) {
                            break;
                        }
                        effectiveComponent = "%s.%s".formatted(field.name(), effectiveComponent);
                    } while (effectivePath.contains("."));
                }
            } catch (SqlTemplateException e) {
                throw new PersistenceException(e);
            }
            tableModel = of(rootTable, effectivePath);
        }
        return new SimpleMetamodel<>(rootTable, effectivePath, componentType, effectiveComponent, isColumn, tableModel);
    }


    private record SimpleMetamodel<T extends Data, E>(@Nonnull Class<T> root,
                                                      @Nonnull String path,
                                                      @Nonnull Class<E> fieldType,
                                                      @Nonnull String field,
                                                      boolean isColumn,
                                                      @Nullable Metamodel<T, ? extends Data> table) implements Metamodel<T, E> {

        @SuppressWarnings("NullableProblems")
        @Override
        public String toString() {
            return "Metamodel{root=%s, type=%s, path='%s', component='%s'}"
                    .formatted(root.getSimpleName(), fieldType.getSimpleName(), path, field);
        }
    }
}
