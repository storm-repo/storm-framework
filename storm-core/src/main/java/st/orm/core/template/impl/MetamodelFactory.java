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
import st.orm.AbstractMetamodel;
import st.orm.Data;
import st.orm.FK;
import st.orm.Metamodel;
import st.orm.PersistenceException;
import st.orm.Ref;
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

    private MetamodelFactory() {
        // Prevent instantiation.
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
            tableModel = root(rootTable);
        } else {
            try {
                RecordField field = getRecordField(rootTable, path);
                // Leaf component name/type.
                effectiveComponent = field.name();
                if (Ref.class.isAssignableFrom(field.type())) {
                    componentType = (Class<E>) getRefDataType(field);
                    isColumn = true;
                    effectivePath = stripLast(path); // Parent path.
                } else {
                    componentType = (Class<E>) field.type();
                    effectivePath = path;
                    if (!isRecord(field.type()) || field.isAnnotationPresent(FK.class)) {
                        isColumn = true;
                    }
                    // For non-Ref, the "table path" starts at the parent of the leaf.
                    effectivePath = stripLast(effectivePath);
                }
                // Collapse embedded (non-FK) parents into the field name, until we hit FK or root.
                while (!effectivePath.isEmpty()) {
                    RecordField parent = getRecordField(rootTable, effectivePath);
                    if (parent.isAnnotationPresent(FK.class)) {
                        break; // FK defines the table boundary.
                    }
                    effectiveComponent = parent.name() + "." + effectiveComponent;
                    effectivePath = stripLast(effectivePath);
                }
            } catch (SqlTemplateException e) {
                throw new PersistenceException(e);
            }
            tableModel = of(rootTable, effectivePath);
        }
        return new SimpleMetamodel<>(rootTable, effectivePath, componentType, effectiveComponent, isColumn, tableModel);
    }

    private static String stripLast(String p) {
        int idx = p.lastIndexOf('.');
        return idx == -1 ? "" : p.substring(0, idx);
    }

    private static final class SimpleMetamodel<T extends Data, E>
            extends AbstractMetamodel<T, E> {

        private final Class<T> root;
        private final Metamodel<T, ? extends Data> table;

        SimpleMetamodel(@Nonnull Class<T> root,
                        @Nonnull String path,
                        @Nonnull Class<E> fieldType,
                        @Nonnull String field,
                        boolean isColumn,
                        @Nonnull Metamodel<T, ? extends Data> table) {
            super(fieldType, path, field, false, null, isColumn);
            this.root = root;
            this.table = table;
        }

        @Override
        public Class<T> root() {
            return root;
        }

        @Override
        public Metamodel<T, ?> table() {
            return table;
        }
    }
}
