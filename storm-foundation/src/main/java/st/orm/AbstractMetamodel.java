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

import java.util.Objects;
import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * Implementation that is used by the generated models.
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
 * @param <V> the value type of the designated element.
 * @since 1.2
 */
public abstract class AbstractMetamodel<T, E, V> implements Metamodel<T, E> {

    private final Class<E> fieldType;
    private final String path;
    private final String field;
    private final boolean inline;
    private final Metamodel<T, ?> parent;
    private final boolean isColumn;

    public AbstractMetamodel(@Nonnull Class<E> fieldType) {
        this(fieldType, "", "", false, null, false);
    }

    public AbstractMetamodel(@Nonnull Class<E> fieldType,
                             @Nonnull String path) {
        this(fieldType, path, "", false, null, true);
    }

    public AbstractMetamodel(@Nonnull Class<E> fieldType,
                             @Nonnull String path,
                             @Nonnull String field,
                             boolean inline,
                             @Nullable Metamodel<T, ?> parent) {
        this(fieldType, path, field, inline, parent, !inline);
    }

    protected AbstractMetamodel(@Nonnull Class<E> fieldType,
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
     * Equality is based on {@link #fieldType()} of {@link #table()}, {@link #path()} and {@link #field()}.
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Metamodel<?, ?> other)) return false;
        return Objects.equals(this.table().fieldType(), other.table().fieldType())
                && Objects.equals(this.path, other.path())
                && Objects.equals(this.field, other.field());
    }

    /**
     * Hash code is based on {@link #fieldType()} of {@link #table()}, {@link #path()} and {@link #field()}.
     */
    @Override
    public final int hashCode() {
        return Objects.hash(table().fieldType(), path, field);
    }

    @Override
    public boolean isColumn() {
        return isColumn;
    }

    @Override
    public Class<T> root() {
        //noinspection unchecked
        return parent()
                .map(Metamodel::root)
                .orElseGet(() -> (Class<T>) fieldType());
    }

    @Override
    public Metamodel<T, ?> table() {
        var parent = parent().orElse(null);
        if (parent == null) {
            return this;
        }
        if (parent.isInline()) {
            return parent.table();
        }
        return parent;
    }

    @Override
    public abstract V getValue(@Nonnull T record);

    private Optional<Metamodel<T, ?>> parent() {
        return ofNullable(parent);
    }

    @Override
    public boolean isInline() {
        return inline;
    }

    @Override
    public Class<E> fieldType() {
        return fieldType;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String field() {
        return field;
    }

    @Override
    public String toString() {
        return "Metamodel{root=%s, type=%s, path='%s', field='%s'}"
                .formatted(root().getSimpleName(), fieldType.getSimpleName(), path, field);
    }
}