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
 * <h2>Equality</h2>
 * Two metamodel instances are considered equal when they identify the same logical field location
 * in the schema:
 * <ul>
 *   <li>the same owning table as returned by {@link #fieldType()} of {@link #table()},</li>
 *   <li>the same {@link #path()}, and</li>
 *   <li>the same {@link #field()}.</li>
 * </ul>
 *
 * @param <T> the primary table type.
 * @param <E> the field type of the designated element.
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
     * Returns {@code true} if the metamodel corresponds to a database column, returns {@code false} otherwise, for
     * example, if the metamodel refers to the root metamodel or an inline record.
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