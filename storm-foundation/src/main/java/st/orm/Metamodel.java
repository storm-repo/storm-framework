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
package st.orm;

import jakarta.annotation.Nonnull;

/**
 * The metamodel is used to map database columns to the object model in a type-safe way.
 *
 * @param <T> the primary table type.
 * @param <E> the record component type of the designated element.
 * @since 1.2
 */
public interface Metamodel<T, E> {

    /**
     * Creates a new metamodel for the given record type.
     *
     * @param table the root table to create the metamodel for.
     * @return a new metamodel for the given record type.
     * @param <T> the root table type.
     */
    static <T> Metamodel<T, T> root(@Nonnull Class<T> table) {
        return MetamodelHelper.root(table);
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
    static <T extends Data, E> Metamodel<T, E> of(@Nonnull Class<T> rootTable, @Nonnull String path) {
        return MetamodelHelper.of(rootTable, path);
    }

    /**
     * Returns {@code true} if the metamodel corresponds to a database column, returns {@code false} otherwise, for
     * example, if the metamodel refers to the root metamodel or an inline record.
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
    Metamodel<T, ?> table();

    /**
     * Returns the path to the database table.
     *
     * @return path to the database table.
     */
    String path();

    /**
     * Returns the field type of the designated element.
     *
     * @return the field type of the designated element.
     */
    Class<E> fieldType();

    /**
     * Returns the field name.
     *
     * @return field name.
     */
    String field();

    /**
     * Returns the field path.
     *
     * @return field path.
     */
    default String fieldPath() {
        String path = path();
        String field = field();
        return path.isEmpty() ? field : field.isEmpty() ? path : "%s.%s".formatted(path, field());
    }
}
