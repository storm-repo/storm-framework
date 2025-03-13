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
import st.orm.template.ColumnNameResolver;
import st.orm.template.ForeignKeyResolver;
import st.orm.template.Model;
import st.orm.template.SqlTemplateException;
import st.orm.template.TableNameResolver;

/**
 * Builder for creating a model.
 *
 * @since 1.2
 */
record ModelBuilderImpl(
        TableNameResolver tableNameResolver,
        ColumnNameResolver columnNameResolver,
        ForeignKeyResolver foreignKeyResolver
) implements ModelBuilder {
    public ModelBuilderImpl() {
        this(TableNameResolver.DEFAULT, ColumnNameResolver.DEFAULT, ForeignKeyResolver.DEFAULT);
    }
    public ModelBuilderImpl {
        tableNameResolver = tableNameResolver == null ? TableNameResolver.DEFAULT : tableNameResolver;
        columnNameResolver = columnNameResolver == null ? ColumnNameResolver.DEFAULT : columnNameResolver;
        foreignKeyResolver = foreignKeyResolver == null ? ForeignKeyResolver.DEFAULT : foreignKeyResolver;
    }

    /**
     * Sets the table name resolver for the model.
     *
     * @param tableNameResolver the table name resolver.
     * @return this model builder.
     */
    @Override
    public ModelBuilder tableNameResolver(@Nullable TableNameResolver tableNameResolver) {
        return new ModelBuilderImpl(tableNameResolver, columnNameResolver, foreignKeyResolver);
    }

    /**
     * Sets the column name resolver for the model.
     *
     * @param columnNameResolver the column name resolver.
     * @return this model builder.
     */
    @Override
    public ModelBuilder columnNameResolver(@Nullable ColumnNameResolver columnNameResolver) {
        return new ModelBuilderImpl(tableNameResolver, columnNameResolver, foreignKeyResolver);
    }

    /**
     * Sets the foreign key resolver for the model.
     *
     * @param foreignKeyResolver the foreign key resolver.
     * @return this model builder.
     */
    @Override
    public ModelBuilder foreignKeyResolver(@Nullable ForeignKeyResolver foreignKeyResolver) {
        return new ModelBuilderImpl(tableNameResolver, columnNameResolver, foreignKeyResolver);
    }

    /**
     * Builds the model.
     *
     * @return the model.
     * @param <ID> the primary key type.
     * @throws SqlTemplateException if an error occurs while building the model.
     */
    @Override
    public <T extends Record, ID> Model<T, ID> build(@Nonnull T record, boolean requirePrimaryKey)
            throws SqlTemplateException {
        //noinspection unchecked
        return build((Class<T>) record.getClass(), requirePrimaryKey);
    }

    /**
     * Builds the model.
     *
     * @return the model.
     * @param <ID> the primary key type.
     * @throws SqlTemplateException if an error occurs while building the model.
     */
    @Override
    public <T extends Record, ID> Model<T, ID> build(@Nonnull Class<T> type, boolean requirePrimaryKey)
            throws SqlTemplateException {
        return ModelFactory.create(this, type, requirePrimaryKey);
    }
}
