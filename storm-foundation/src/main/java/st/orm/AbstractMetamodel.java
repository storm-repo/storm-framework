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
import jakarta.annotation.Nullable;

import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * Implementation that is used by the generated models.
 *
 * @param <T> the primary table type.
 * @param <E> the field type of the designated element.
 * @since 1.2
 */
public abstract class AbstractMetamodel<T, E> implements Metamodel<T, E> {

    private final Class<E> fieldType;
    private final String path;
    private final String field;
    private final boolean inline;
    private final Metamodel<T, ?> parent;
    private final boolean isColumn;

    public AbstractMetamodel(@Nonnull Class<E> fieldType) {
        this(fieldType, "","", false, null, false);
    }

    public AbstractMetamodel(@Nonnull Class<E> fieldType,
                             @Nonnull String path) {
        this(fieldType, path,"", false, null, true);
    }

    public AbstractMetamodel(@Nonnull Class<E> fieldType,
                             @Nonnull String path,
                             @Nonnull String field,
                             boolean inline,
                             @Nullable Metamodel<T, ?> parent) {
        this(fieldType, path, field, inline, parent, !inline);
    }

    private AbstractMetamodel(@Nonnull Class<E> fieldType,
                              @Nonnull String path,
                              @Nonnull String field,
                              boolean inline,
                              @Nullable Metamodel<T, ?> parent,
                              boolean isColumn) {
        this.fieldType = fieldType;
        this.path = path;
        this.field = field;
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
                .orElseGet(() -> (Class<T>) fieldType());
    }

    /**
     * Returns the table that holds the column to which this metamodel is pointing. If the metamodel points to an
     * inline record, the table is the parent table of the inline record. If the metamodel is a root metamodel, the
     * root table is returned.
     *
     * @return the table that holds the column to which this metamodel is pointing.
     */
    @Override
    public Metamodel<T, ?> table() {
        if (isInline()) {
            return parent().orElseThrow().table();
        }
        return parent().orElse(this);
    }

    /**
     * Returns true if the field is an inline record.
     *
     * @return true if the field is an inline record.
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
     * Returns the field type of the designated element.
     *
     * @return the field type of the designated element.
     */
    @Override
    public Class<E> fieldType() {
        return fieldType;
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
     * Returns the field name.
     *
     * @return field name.
     */
    @Override
    public String field() {
        return field;
    }

    @Override
    public String toString() {
        return "Metamodel{root=%s, type=%s, path='%s', field='%s'}"
                .formatted(root().getSimpleName(), fieldType.getSimpleName(), path, field);
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
            return "Metamodel{root=%s, type=%s, path='%s', field='%s'}"
                    .formatted(root.getSimpleName(), fieldType.getSimpleName(), path, field);
        }
    }
}
