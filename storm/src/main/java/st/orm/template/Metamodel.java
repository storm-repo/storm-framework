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
package st.orm.template;

import jakarta.annotation.Nonnull;
import st.orm.PersistenceException;
import st.orm.template.impl.MetamodelImpl;

/**
 * The metamodel is used to map database columns to the object model in a type-safe way.
 *
 * @param <T> the primary table type.
 * @param <E> the record component type of the designated element.
 * @since 1.2
 */
public interface Metamodel<T extends Record, E> {

    /**
     * Creates a new metamodel for the given record type.
     *
     * @param table the root table to create the metamodel for.
     * @return a new metamodel for the given record type.
     * @param <T> the root table type.
     */
    static <T extends Record> Metamodel<T, T> root(@Nonnull Class<T> table) {
        return MetamodelImpl.of(table);
    }

    /**
     * Creates a new metamodel for the given root table and path.
     *
     * <p>This method is typically used to manually create a metamodel for a component of a record, which can be useful
     * in cases where the metamodel cannot be generated automatically, for example, local records.</p>
     *
     * @param table the root table to create the metamodel for.
     * @param path a dot separated path starting from the root table.
     * @return a new metamodel for the given root table and path.
     * @param <T> the root table type.
     * @param <E> the record component type of the designated component.
     * @throws PersistenceException if the metamodel cannot be created for the root table and path.
     */
    static <T extends Record, E> Metamodel<T, E> of(@Nonnull Class<T> table, @Nonnull String path) {
        return MetamodelImpl.of(table, path);
    }

    /**
     * Returns {@code true} if the metamodel corresponds to a database column, returns {@code false} otherwise, for
     * example if the metamodel refers to the root metamodel or an inline record.
     *
     * @return {@code true} if this metamodel maps to a column, {@code false} otherwise.
     */
    boolean isColumn();

    /**
     * Returns the root metamodel. This is typically the table specified in the FROM clause of a query.
     *
     * @return the root metamodel.
     */
    Class<T> root();

    /**
     * Returns the table that holds the column to which this metamodel is pointing. If the metamodel points to an
     * inline record, the table is the parent table of the inline record. If the metamodel is a root metamodel, the
     * root table is returned.
     *
     * @return the table that holds the column to which this metamodel is pointing.
     */
    Metamodel<T, ? extends Record> table();

    /**
     * Returns the path to the database table.
     *
     * @return path to the database table.
     */
    String path();

    /**
     * Returns the component type of the designated element.
     *
     * @return the component type of the designated element.
     */
    Class<E> componentType();

    /**
     * Returns the component path.
     *
     * @return component path.
     */
    String component();

    /**
     * Returns the component path.
     *
     * @return component path.
     */
    default String componentPath() {
        String path = path();
        String component = component();
        return path.isEmpty() ? component : component.isEmpty() ? path : STR."\{path}.\{component()}";
    }
}
