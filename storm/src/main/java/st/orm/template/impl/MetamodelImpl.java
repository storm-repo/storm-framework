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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.FK;
import st.orm.Lazy;
import st.orm.PersistenceException;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.Metamodel;
import st.orm.template.SqlTemplateException;

import java.lang.reflect.RecordComponent;
import java.util.Optional;

import static java.util.Optional.ofNullable;
import static st.orm.template.impl.RecordReflection.getLazyRecordType;
import static st.orm.template.impl.RecordReflection.getRecordComponent;

/**
 * Implementation that is used by the generated models.
 *
 * @param <T> the primary table type.
 * @param <E> the record component type of the designated element.
 * @since 1.2
 */
public class MetamodelImpl<T extends Record, E> implements Metamodel<T, E> {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    /**
     * Creates a new metamodel for the given record type.
     *
     * @param table the root table to create the metamodel for.
     * @return a new metamodel for the given record type.
     * @param <T> the root table type.
     */
    public static <T extends Record> Metamodel<T, T> of(@Nonnull Class<T> table) {
        return new MetamodelImpl<>(table);
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
    public static <T extends Record, E> Metamodel<T, E> of(@Nonnull Class<T> rootTable, @Nonnull String path) {
        Class<?> componentType;
        String effectivePath;
        String effectiveComponent;
        Metamodel<T, ? extends Record> tableModel;
        boolean isColumn = false;
        if (path.isEmpty()) {
            componentType = rootTable;
            effectivePath = "";
            effectiveComponent = "";
            tableModel = null;
        } else {
            try {
                RecordComponent component = getRecordComponent(rootTable, path);
                if (Lazy.class.isAssignableFrom(component.getType())) {
                    effectivePath = path.substring(0, path.lastIndexOf('.')); // Remove last component.
                    effectiveComponent = component.getName();
                    componentType = getLazyRecordType(component);
                    isColumn = true;
                } else {
                    effectivePath = path;
                    effectiveComponent = component.getName();
                    componentType = component.getType();
                    if (!component.getType().isRecord() ||
                            REFLECTION.isAnnotationPresent(component, FK.class)) {
                        isColumn = true;
                    }
                    do {
                        int index = effectivePath.lastIndexOf('.');
                        effectivePath = effectivePath.substring(0, index == -1 ? 0 : index); // Remove last component.
                        if (effectivePath.isEmpty()) {
                            break;
                        }
                        component = getRecordComponent(rootTable, effectivePath);
                        if (!component.getDeclaringRecord().isRecord() || REFLECTION.isAnnotationPresent(component, FK.class)) {
                            break;
                        }
                        effectiveComponent = STR."\{component.getName()}.\{effectiveComponent}";
                    } while (effectivePath.contains("."));
                }
            } catch (SqlTemplateException e) {
                throw new PersistenceException(e);
            }
            tableModel = of(rootTable, effectivePath);
        }
        return new SimpleMetamodel<>(rootTable, effectivePath, componentType, effectiveComponent, isColumn, tableModel);
    }

    private final Class<E> componentType;
    private final String path;
    private final String component;
    private final boolean inline;
    private final Metamodel<T, ?> parent;
    private final boolean isColumn;

    public MetamodelImpl(@Nonnull Class<E> componentType) {
        this(componentType, "","", false, null, false);
    }

    public MetamodelImpl(@Nonnull Class<E> componentType,
                         @Nonnull String path) {
        this(componentType, path,"", false, null, true);
    }

    public MetamodelImpl(@Nonnull Class<E> componentType,
                         @Nonnull String path,
                         @Nonnull String component,
                         boolean inline,
                         @Nullable Metamodel<T, ?> parent) {
        this(componentType, path, component, inline, parent, !inline);
    }

    private MetamodelImpl(@Nonnull Class<E> componentType,
                          @Nonnull String path,
                          @Nonnull String component,
                          boolean inline,
                          @Nullable Metamodel<T, ?> parent,
                          boolean isColumn) {
        this.componentType = componentType;
        this.path = path;
        this.component = component;
        this.inline = inline;
        this.parent = parent;
        this.isColumn = isColumn;
    }

    /**
     * Returns {@code true} if the metamodel corresponds to a database column, returns {@code false} otherwise, for
     * example if the metamodel refers to the root metamodel or an inline record.
     *
     * @return {@code true} if this metamodel maps to a column, {@code false} otherwise.
     */
    @Override
    public boolean isColumn() {
        return isColumn;
    }

    /**
     * Returns the root metamodel. This is typically the table specified in the FROM clause of a query.
     *
     * @return the root metamodel.
     */
    @Override
    public Class<T> root() {
        //noinspection unchecked
        return parent()
                .map(Metamodel::root)
                .orElseGet(() -> (Class<T>) componentType());
    }

    /**
     * Returns the table that holds the column to which this metamodel is pointing. If the metamodel points to an
     * inline record, the table is the parent table of the inline record. If the metamodel is a root metamodel, the
     * root table is returned.
     *
     * @return the table that holds the column to which this metamodel is pointing.
     */
    @Override
    public Metamodel<T, ? extends Record> table() {
        if (isInline()) {
            return parent().orElseThrow().table();
        }
        //noinspection unchecked
        return (Metamodel<T, ? extends Record>) parent().orElse(this);
    }

    /**
     * Returns true if the component is an inline record.
     *
     * @return true if the component is an inline record.
     */
    private boolean isInline() {
        return inline;
    }

    /**
     * Returns the parent metamodel or an empty optional if this is the root metamodel.
     *
     * @return the parent metamodel or an empty optional if this is the root metamodel.
     */
    private Optional<Metamodel<T, ?>> parent() {
        return ofNullable(parent);
    }

    /**
     * Returns the component type of the designated element.
     *
     * @return the component type of the designated element.
     */
    @Override
    public Class<E> componentType() {
        return componentType;
    }

    /**
     * Returns the path to the database table.
     *
     * @return path to the database table.
     */
    @Override
    public String path() {
        return path;
    }

    /**
     * Returns the component path.
     *
     * @return component path.
     */
    @Override
    public String component() {
        return component;
    }

    @Override
    public String toString() {
        return STR."Metamodel{table=\{componentType}, path='\{path}', component='\{component}'}";
    }

    private static class SimpleMetamodel<T extends Record, E> implements Metamodel<T, E> {
        private final Class<T> rootTable;
        private final String path;
        private final Class<?> componentType;
        private final String component;
        private final boolean isColumn;
        private final Metamodel<T, ? extends Record> tableModel;

        public SimpleMetamodel(@Nonnull Class<T> rootTable,
                               @Nonnull String path,
                               @Nonnull Class<?> componentType,
                               @Nonnull String component,
                               boolean isColumn,
                               @Nullable Metamodel<T, ? extends Record> tableModel) {
            if (!rootTable.isRecord()) {
                throw new PersistenceException(STR."Table must be a record type: \{rootTable.getSimpleName()}.");
            }
            this.rootTable = rootTable;
            this.path = path;
            this.componentType = componentType;
            this.component = component;
            this.isColumn = isColumn;
            //noinspection unchecked
            this.tableModel = tableModel == null ? (Metamodel<T, ? extends Record>) (Object) this : tableModel;
        }

        @Override
        public boolean isColumn() {
            return isColumn;
        }

        @Override
        public Class<T> root() {
            return rootTable;
        }

        @Override
        public Metamodel<T, ? extends Record> table() {
            return tableModel;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public Class<E> componentType() {
            //noinspection unchecked
            return (Class<E>) componentType;
        }

        @Override
        public String component() {
            return component;
        }

        @Override
        public String toString() {
            return STR."Metamodel{table=\{componentType}, path='\{path}', component='\{component}'}";
        }
    }
}
