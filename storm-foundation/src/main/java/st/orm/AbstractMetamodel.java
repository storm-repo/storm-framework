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
 * @param <E> the record component type of the designated element.
 * @since 1.2
 */
public abstract class AbstractMetamodel<T extends Record, E> implements Metamodel<T, E> {

    private final Class<E> componentType;
    private final String path;
    private final String component;
    private final boolean inline;
    private final Metamodel<T, ?> parent;
    private final boolean isColumn;

    public AbstractMetamodel(@Nonnull Class<E> componentType) {
        this(componentType, "","", false, null, false);
    }

    public AbstractMetamodel(@Nonnull Class<E> componentType,
                             @Nonnull String path) {
        this(componentType, path,"", false, null, true);
    }

    public AbstractMetamodel(@Nonnull Class<E> componentType,
                             @Nonnull String path,
                             @Nonnull String component,
                             boolean inline,
                             @Nullable Metamodel<T, ?> parent) {
        this(componentType, path, component, inline, parent, !inline);
    }

    private AbstractMetamodel(@Nonnull Class<E> componentType,
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
        return "Metamodel{root=%s, type=%s, path='%s', component='%s'}"
                .formatted(root().getSimpleName(), componentType.getSimpleName(), path, component);
    }

    private record SimpleMetamodel<T extends Record, E>(@Nonnull Class<T> root,
                                                        @Nonnull String path,
                                                        @Nonnull Class<E> componentType,
                                                        @Nonnull String component,
                                                        boolean isColumn,
                                                        @Nullable Metamodel<T, ? extends Record> table) implements Metamodel<T, E> {

        public SimpleMetamodel {
            if (!root.isRecord()) {
                throw new PersistenceException("Table must be a record type: %s.".formatted(root.getSimpleName()));
            }
        }

        @Override
        public String toString() {
            return "Metamodel{root=%s, type=%s, path='%s', component='%s'}"
                    .formatted(root.getSimpleName(), componentType.getSimpleName(), path, component);
        }
    }
}
