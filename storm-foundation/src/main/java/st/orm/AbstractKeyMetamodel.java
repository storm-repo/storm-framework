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
import jakarta.annotation.Nullable;

/**
 * Extension of {@link AbstractMetamodel} that implements {@link Metamodel.Key}, indicating that the field has a
 * uniqueness constraint.
 *
 * <p>The metamodel processor generates instances of this class for record components annotated with
 * {@link UK @UK} or {@link PK @PK} (which is meta-annotated with {@code @UK}).</p>
 *
 * @param <T> the primary table type.
 * @param <E> the field type of the designated element.
 * @param <V> the value type of the designated element.
 * @since 1.9
 */
public abstract class AbstractKeyMetamodel<T extends Data, E, V> extends AbstractMetamodel<T, E, V>
        implements Metamodel.Key<T, E> {

    private final boolean nullable;

    public AbstractKeyMetamodel(@Nonnull Class<E> fieldType) {
        super(fieldType);
        this.nullable = false;
    }

    public AbstractKeyMetamodel(@Nonnull Class<E> fieldType,
                                @Nonnull String path) {
        super(fieldType, path);
        this.nullable = false;
    }

    public AbstractKeyMetamodel(@Nonnull Class<E> fieldType,
                                @Nonnull String path,
                                @Nonnull String field,
                                boolean inline,
                                @Nullable Metamodel<T, ?> parent) {
        super(fieldType, path, field, inline, parent);
        this.nullable = false;
    }

    protected AbstractKeyMetamodel(@Nonnull Class<E> fieldType,
                                   @Nonnull String path,
                                   @Nonnull String field,
                                   boolean inline,
                                   @Nullable Metamodel<T, ?> parent,
                                   boolean isColumn) {
        super(fieldType, path, field, inline, parent, isColumn);
        this.nullable = false;
    }

    /**
     * Constructs a key metamodel with explicit column and nullable flags. This constructor is used by the metamodel
     * processor to propagate the nullable status of unique key fields to the runtime.
     *
     * @param fieldType the field type.
     * @param path the path to the table.
     * @param field the field name.
     * @param inline whether this is an inline record field.
     * @param parent the parent metamodel.
     * @param isColumn whether this metamodel corresponds to a database column.
     * @param nullable whether this key allows duplicate NULLs (nullable with {@code nullsDistinct = true}).
     * @since 1.9
     */
    public AbstractKeyMetamodel(@Nonnull Class<E> fieldType,
                                @Nonnull String path,
                                @Nonnull String field,
                                boolean inline,
                                @Nullable Metamodel<T, ?> parent,
                                boolean isColumn,
                                boolean nullable) {
        super(fieldType, path, field, inline, parent, isColumn);
        this.nullable = nullable;
    }

    @Override
    public boolean isNullable() {
        return nullable;
    }
}
