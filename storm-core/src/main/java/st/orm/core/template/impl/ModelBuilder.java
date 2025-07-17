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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.PersistenceException;
import st.orm.config.ColumnNameResolver;
import st.orm.config.ForeignKeyResolver;
import st.orm.core.template.Model;
import st.orm.core.template.SqlTemplateException;
import st.orm.config.TableNameResolver;

import java.util.function.Supplier;

/**
 * Builder for creating a model.
 *
 * @since 1.2
 */
public interface ModelBuilder {

    /**
     * Creates a new instance of the model builder.
     *
     * @return a new instance of the model builder.
     */
    static ModelBuilder newInstance() {
        return new ModelBuilderImpl();
    }

    /**
     * Returns the table name resolver for the model.
     */
    TableNameResolver tableNameResolver();

    /**
     * Sets the table name resolver for the model.
     *
     * @param tableNameResolver the table name resolver.
     * @return this model builder.
     */
    ModelBuilder tableNameResolver(@Nullable TableNameResolver tableNameResolver);

    /**
     * Returns the column name resolver for the model.
     */
    ColumnNameResolver columnNameResolver();

    /**
     * Sets the column name resolver for the model.
     *
     * @param columnNameResolver the column name resolver.
     * @return this model builder.
     */
    ModelBuilder columnNameResolver(@Nullable ColumnNameResolver columnNameResolver);

    /**
     * Returns the foreign key resolver for the model.
     */
    ForeignKeyResolver foreignKeyResolver();

    /**
     * Sets the foreign key resolver for the model.
     *
     * @param foreignKeyResolver the foreign key resolver.
     * @return this model builder.
     */
    ModelBuilder foreignKeyResolver(@Nullable ForeignKeyResolver foreignKeyResolver);

    /**
     * Builds the model.
     *
     * @return the model.
     * @param <ID> the primary key type.
     * @throws SqlTemplateException if an error occurs while building the model.
     */
    <T extends Record, ID> Model<T, ID> build(@Nonnull T record, boolean requirePrimaryKey) throws SqlTemplateException;

    /**
     * Builds the model.
     *
     * @return the model.
     * @param <ID> the primary key type.
     * @throws SqlTemplateException if an error occurs while building the model.
     */
    <T extends Record, ID> Model<T, ID> build(@Nonnull Class<T> type, boolean requirePrimaryKey) throws SqlTemplateException;

    /**
     * Returns a supplier for the model.
     *
     * @param type the record type.
     * @param requirePrimaryKey {@code true} if the primary key is required, {@code false} otherwise.
     * @return a supplier for the model.
     * @param <T> the record type.
     * @param <ID> the primary key type.
     */
    default <T extends Record, ID> Supplier<Model<T, ID>> supplier(@Nonnull Class<T> type, boolean requirePrimaryKey) {
        return () -> {
            try {
                return build(type, requirePrimaryKey);
            } catch (SqlTemplateException e) {
                throw new PersistenceException(e);
            }
        };
    }
}
