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
package st.orm;

import jakarta.annotation.Nonnull;

/**
 * The metamodel provides type-safe references to entity fields for use in queries. Generated metamodel classes
 * (e.g., {@code User_} for a {@code User} entity) contain static fields representing each entity field, enabling
 * compile-time verification of field references.
 *
 * <h2>Nested Paths (Fully Qualified)</h2>
 *
 * <p>Nested paths traverse from the root entity through relationships by chaining field accessors:
 * <pre>{@code
 * // Traverse User → City → Country → name
 * User_.city.country.name
 * }</pre>
 *
 * <p>Nested paths are <strong>always unambiguous</strong> because they explicitly specify the traversal from the
 * root entity. Storm automatically generates the necessary JOINs based on the path.
 *
 * <h2>Short Form</h2>
 *
 * <p>Short form references a table's metamodel directly without specifying the path:
 * <pre>{@code
 * // Reference Country directly
 * Country_.name
 * }</pre>
 *
 * <p>Short form works <strong>only when the table appears exactly once</strong> in the entity graph. If the table
 * is referenced in multiple places, Storm cannot determine which occurrence you mean and throws an exception.
 *
 * <p>Short form is also used to reference tables added via custom joins, since those tables are not reachable
 * through nested paths.
 *
 * <h2>Path Resolution</h2>
 *
 * <p>When resolving a metamodel reference, Storm follows this order:
 * <ol>
 *   <li><strong>Nested path</strong> — If a path is specified (e.g., {@code User_.city.country}), use the alias
 *       for that specific traversal</li>
 *   <li><strong>Unique table lookup</strong> — If short form (e.g., {@code Country_}), check if the table appears
 *       exactly once in the entity graph or registered joins</li>
 *   <li><strong>Error</strong> — If multiple paths exist, throw an exception indicating the ambiguity</li>
 * </ol>
 *
 * @param <T> the root table type (the entity from which the path originates).
 * @param <E> the field type of the designated element.
 * @since 1.2
 */
public interface Metamodel<T extends Data, E> {

    /**
     * Creates a new metamodel for the given record type.
     *
     * @param table the root table to create the metamodel for.
     * @return a new metamodel for the given record type.
     * @param <T> the root table type.
     */
    static <T extends Data> Metamodel<T, T> root(@Nonnull Class<T> table) {
        return MetamodelHelper.root(table);
    }

    /**
     * Creates a new metamodel for the given root rootTable and path.
     *
     * <p>This method is typically used to manually create a metamodel for a component of a record, which can be useful
     * in cases where the metamodel cannot be generated automatically, for example, local records.</p>
     *
     * @param rootTable the root rootTable to create the metamodel for.
     * @param path a dot separated path starting from the root table.
     * @return a new metamodel for the given root rootTable and path.
     * @param <T> the root rootTable type.
     * @param <E> the record component type of the designated component.
     * @throws PersistenceException if the metamodel cannot be created for the root rootTable and path.
     */
    static <T extends Data, E> Metamodel<T, E> of(@Nonnull Class<T> rootTable, @Nonnull String path) {
        return MetamodelHelper.of(rootTable, path);
    }

    /**
     * Returns the canonical metamodel for the field represented by {@code this} metamodel. The resulting metamodel
     * captures only the table type and field.
     *
     * <p>The result is independent of the position of this field within a table graph. This makes the normalized form
     * suitable for equality checks, for example, to determine whether two metamodels refer to the same underlying
     * field.</p>
     *
     * @return the canonical metamodel for this metamodel.
     * @since 1.8
     */
    default Metamodel<? extends Data, E> canonical() {
        return of(tableType(), field());
    }

    /**
     * Returns {@code true} if the metamodel corresponds to a database column, returns {@code false} otherwise, for
     * example, if the metamodel refers to the root metamodel or an inline record.
     *
     * <p>Note that a column can also be a table, for example, in the case of a foreign key.</p>
     *
     * @return {@code true} if this metamodel maps to a column, {@code false} otherwise.
     */
    boolean isColumn();

    /**
     * Returns {@code true} if the metamodel corresponds to an inline record, returns {@code false} otherwise.
     *
     * @return {@code true} if this metamodel maps to an inline record, {@code false} otherwise.
     */
    boolean isInline();

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
    Metamodel<T, ? extends Data> table();

    /**
     * Returns the type of the table that holds the column to which this metamodel is pointing.
     *
     * @return the type of the table that holds the column to which this metamodel is pointing.
     * @since 1.7
     */
    default Class<? extends Data> tableType() {
        return table().fieldType();
    }

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

    /**
     * Extracts the value from the given record as specified by this metamodel.
     *
     * <p>The returned value may be {@code null} if this metamodel represents a nullable field or if any parent
     * metamodel in the access path resolves to {@code null} (for example, when navigating through an optional or
     * nullable nested record).</p>
     *
     * <p>Implementations may return a non-null value, but callers must not rely on that unless they statically know
     * they are using a non-null metamodel variant.</p>
     *
     * @param record the root record from which the value is extracted.
     * @return the extracted value, or {@code null} if the value cannot be resolved.
     * @since 1.7
     */
    Object getValue(@Nonnull T record);

    /**
     * Checks whether the value extracted from {@code a} is identical to the value extracted from {@code b}.
     *
     * <p><strong>Semantics:</strong>
     * This method performs an <em>identity comparison</em> on the extracted field value.
     * It returns {@code true} if and only if both extracted values refer to the
     * <strong>same object instance</strong>.
     *
     * <p>This operation is only meaningful for <em>reference-typed fields</em>.
     * It is <strong>not defined for primitive-typed fields</strong>.
     *
     * <p><strong>Performance guarantees:</strong>
     * <ul>
     *   <li>No boxing or unboxing is performed.</li>
     *   <li>No value coercion or conversion is performed.</li>
     *   <li>No {@code equals(...)} or comparator logic is used.</li>
     * </ul>
     *
     * @param a the instance from which the left-hand value is extracted, must not be {@code null}.
     * @param b the instance from which the right-hand value is extracted, must not be {@code null}.
     * @return {@code true} if both extracted values are the same object instance.
     * @since 1.7
     */
    boolean isIdentical(@Nonnull T a, @Nonnull T b);

    /**
     * Checks whether the value extracted from {@code a} is the same as the value extracted from {@code b}.
     *
     * <p><strong>Semantics:</strong>
     * This method performs a <em>value comparison</em> on the extracted field value.
     * The comparison is defined by the field type:
     *
     * <ul>
     *   <li>For primitive-typed fields, values are compared using {@code ==}.</li>
     *   <li>For reference-typed fields, values are compared using their defined value semantics
     *       (for example {@code equals(...)} or an equivalent comparator).</li>
     * </ul>
     *
     * <p><strong>Performance guarantees:</strong>
     * <ul>
     *   <li>No boxing or unboxing is performed.</li>
     *   <li>No identity comparison is performed.</li>
     *   <li>The comparison operates directly on the extracted values.</li>
     * </ul>
     *
     * @param a the instance from which the left-hand value is extracted, must not be {@code null}.
     * @param b the instance from which the right-hand value is extracted, must not be {@code null}.
     * @return {@code true} if both extracted values are equal by value.
     * @since 1.7
     */
    boolean isSame(@Nonnull T a, @Nonnull T b);
}