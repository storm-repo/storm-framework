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
import jakarta.annotation.Nullable;
import st.orm.BindVars;
import st.orm.Lazy;
import st.orm.PreparedQuery;
import st.orm.Query;

import static java.lang.StringTemplate.RAW;

/**
 * The query template is used to construct queries.
 */
public interface QueryTemplate extends SubqueryTemplate {

    /**
     * Get the SQL dialect for this template.
     *
     * @return the SQL dialect.
     * @since 1.2
     */
    SqlDialect dialect();

    /**
     * Create a new bind variables instance that can be used to add bind variables to a batch.
     *
     * @return a new bind variables instance.
     * @see PreparedQuery#addBatch(Record)
     */
    BindVars createBindVars();

    /**
     * Creates a lazy instance for the specified record {@code type} and {@code pk}. This method can be used to generate
     * lazy instances for entities, projections and regular records.
     *
     * @param type record type.
     * @param pk primary key.
     * @return lazy instance.
     * @param <T> table type.
     * @param <ID> primary key type.
     */
    <T extends Record, ID> Lazy<T, ID> lazy(@Nonnull Class<T> type, @Nullable ID pk);

    /**
     * Get the model for the specified record {@code type}. The model provides information about the type's database
     * name, primary keys and columns.
     *
     * @param type record type.
     * @return the model.
     * @param <T> table type.
     * @param <ID> primary key type.
     */
    <T extends Record, ID> Model<T, ID> model(@Nonnull Class<T> type);

    /**
     * Creates a query builder for the specified table.
     *
     * @param fromType the table to select from.
     * @return the query builder.
     * @param <T> the table type to select from.
     */
    default <T extends Record> QueryBuilder<T, T, ?> selectFrom(@Nonnull Class<T> fromType) {
        return selectFrom(fromType, fromType);
    }

    /**
     * Creates a query builder for the specified table and select type.
     *
     * @param fromType the table to select from.
     * @param selectType the result type of the query.
     * @return the query builder.
     * @param <T> the table type to select from.
     * @param <R> the result type.
     */
    default <T extends Record, R extends Record> QueryBuilder<T, R, ?> selectFrom(@Nonnull Class<T> fromType,
                                                                                  @Nonnull Class<R> selectType) {
        return selectFrom(fromType, selectType, RAW."\{selectType}");
    }

    /**
     * Creates a query builder for the specified table and select type using the given {@code template}.
     *
     * @param fromType the table to select from.
     * @param selectType the result type of the query.
     * @param template the select clause template.
     * @return the query builder.
     * @param <T> the table type to select from.
     * @param <R> the result type.
     */
    <T extends Record, R> QueryBuilder<T, R, ?> selectFrom(@Nonnull Class<T> fromType,
                                                           @Nonnull Class<R> selectType,
                                                           @Nonnull StringTemplate template);

    /**
     * Creates a query builder for the specified table to delete from.
     *
     * @param fromType the table to delete from.
     * @return the query builder.
     * @param <T> the table type to delete from.
     */
    <T extends Record> QueryBuilder<T, ?, ?> deleteFrom(@Nonnull Class<T> fromType);

    /**
     * Creates a query for the specified query {@code template}.
     *
     * @param template the query template.
     * @return the query.
     */
    Query query(@Nonnull StringTemplate template);
}
