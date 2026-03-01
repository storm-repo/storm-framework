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
package st.orm.core.template;

import static st.orm.core.template.TemplateString.wrap;

import jakarta.annotation.Nonnull;
import st.orm.BindVars;
import st.orm.Data;
import st.orm.Ref;

/**
 * The query template is used to construct queries.
 */
public interface QueryTemplate extends SubqueryTemplate {

    /**
     * Get the SQL dialect for this template.
     *
     * <p>This method is aware of any registered {@code SqlInterceptor} instances and returns the SQL dialect used by
     * the underlying SQL template. The dialect is determined based on the SQL template's configuration and the
     * interceptors that have been applied to it.</p>
     *
     * @return the SQL dialect.
     * @since 1.2
     */
    SqlDialect dialect();

    /**
     * Create a new bind variables instance that can be used to add bind variables to a batch.
     *
     * @return a new bind variables instance.
     * @see PreparedQuery#addBatch(Data)
     */
    BindVars createBindVars();

    /**
     * Creates a ref instance for the specified record {@code type} and {@code pk}. This method can be used to generate
     * ref instances for entities, projections and regular records.
     *
     * @param type record type.
     * @param id primary key.
     * @return ref instance.
     * @param <T> table type.
     * @param <ID> primary key type.
     * @since 1.3
     */
    <T extends Data, ID> Ref<T> ref(@Nonnull Class<T> type, @Nonnull ID id);

    /**
     * Creates a ref instance for the specified record {@code type} and {@code id}. This method can be used to generate
     * ref instances for entities, projections and regular records. The object returned by this method already contains
     * the fetched record.
     *
     * @param id primary key.
     * @return ref instance.
     * @param <T> table type.
     * @param <ID> primary key type.
     * @since 1.3
     */
    <T extends Data, ID> Ref<T> ref(@Nonnull T record, @Nonnull ID id);

    /**
     * Get the model for the specified record {@code type}. The model provides information about the type's database
     * name, primary keys and columns.
     *
     * @param type record type.
     * @return the model.
     * @param <T> table type.
     * @param <ID> primary key type.
     */
    default <T extends Data, ID> Model<T, ID> model(@Nonnull Class<T> type) {
        return model(type, false);
    }

    /**
     * Get the model for the specified record {@code type}. The model provides information about the type's database
     * name, primary keys and columns.
     *
     * @param type record type.
     * @param requirePrimaryKey whether to require a primary key.
     * @return the model.
     * @param <T> table type.
     * @param <ID> primary key type.
     * @since 1.3
     */
    <T extends Data, ID> Model<T, ID> model(@Nonnull Class<T> type, boolean requirePrimaryKey);

    /**
     * Creates a query builder for the specified table.
     *
     * @param fromType the table to select from.
     * @return the query builder.
     * @param <T> the table type to select from.
     */
    default <T extends Data> QueryBuilder<T, T, ?> selectFrom(@Nonnull Class<T> fromType) {
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
    default <T extends Data, R> QueryBuilder<T, R, ?> selectFrom(@Nonnull Class<T> fromType,
                                                                 @Nonnull Class<R> selectType) {
        return selectFrom(fromType, selectType, wrap(fromType));
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
    <T extends Data, R> QueryBuilder<T, R, ?> selectFrom(@Nonnull Class<T> fromType,
                                                         @Nonnull Class<R> selectType,
                                                         @Nonnull TemplateString template);

    /**
     * Creates a query builder for the specified table to delete from.
     *
     * @param fromType the table to delete from.
     * @return the query builder.
     * @param <T> the table type to delete from.
     */
    <T extends Data> QueryBuilder<T, ?, ?> deleteFrom(@Nonnull Class<T> fromType);

    /**
     * Creates a query for the specified {@code query} string.
     *
     * @param query the query.
     * @return the query.
     */
    Query query(@Nonnull String query);

    /**
     * Creates a query for the specified query {@code template}.
     *
     * @param template the query template.
     * @return the query.
     */
    Query query(@Nonnull TemplateString template);
}
