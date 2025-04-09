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
package st.orm.kotlin.template;

import jakarta.annotation.Nonnull;
import kotlin.reflect.KClass;
import st.orm.BindVars;
import st.orm.Ref;
import st.orm.kotlin.KQuery;
import st.orm.kotlin.template.impl.KQueryTemplateImpl;
import st.orm.template.QueryTemplate;
import st.orm.template.TemplateFunction;

import static java.lang.StringTemplate.RAW;
import static st.orm.template.TemplateFunction.template;

/**
 * The query template is used to construct queries.
 */
public interface KQueryTemplate {

    /**
     * Create a new Kotlin query template instance.
     *
     * @param queryTemplate the query template to wrap.
     * @return a new Kotlin query template instance.
     */
    static KQueryTemplate from(@Nonnull QueryTemplate queryTemplate) {
        return new KQueryTemplateImpl(queryTemplate);
    }

    /**
     * Create a new bind variables instance that can be used to add bind variables to a batch.
     *
     * @return a new bind variables instance.
     * @see st.orm.PreparedQuery#addBatch(Record)
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
    <T extends Record, ID> Ref<T> ref(@Nonnull KClass<T> type, @Nonnull ID id);

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
    <T extends Record, ID> Ref<T> ref(@Nonnull T record, @Nonnull ID id);

    /**
     * Get the model for the specified record {@code type}. The model provides information about the type's database
     * name, primary keys and columns.
     *
     * @param type record type.
     * @return the model.
     * @param <T> table type.
     * @param <ID> primary key type.
     */
    <T extends Record, ID> KModel<T, ID> model(@Nonnull KClass<T> type);

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
    <T extends Record, ID> KModel<T, ID> model(@Nonnull KClass<T> type, boolean requirePrimaryKey);

    /**
     * Creates a query builder for the specified table.
     *
     * @param fromType the table to select from.
     * @return the query builder.
     * @param <T> the table type to select from.
     */
    default <T extends Record> KQueryBuilder<T, T, Object> selectFrom(@Nonnull KClass<T> fromType) {
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
    default <T extends Record, R> KQueryBuilder<T, R, Object> selectFrom(@Nonnull KClass<T> fromType,
                                                                         @Nonnull KClass<R> selectType) {
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
    <T extends Record, R> KQueryBuilder<T, R, Object> selectFrom(@Nonnull KClass<T> fromType,
                                                                 @Nonnull KClass<R> selectType,
                                                                 @Nonnull StringTemplate template);

    /**
     * Creates a query builder for the specified table and select type using the given {@code template}.
     *
     * @param fromType the table to select from.
     * @param selectType the result type of the query.
     * @param function used to define the condition to join on.
     * @return the query builder.
     * @param <T> the table type to select from.
     * @param <R> the result type.
     */
    default <T extends Record, R> KQueryBuilder<T, R, Object> selectFrom(@Nonnull KClass<T> fromType,
                                                                         @Nonnull KClass<R> selectType,
                                                                         @Nonnull TemplateFunction function) {
        return selectFrom(fromType, selectType, template(function));
    }

    /**
     * Creates a query builder for the specified table to delete from.
     *
     * @param fromType the table to delete from.
     * @return the query builder.
     * @param <T> the table type to delete from.
     */
    <T extends Record> KQueryBuilder<T, ?, ?> deleteFrom(@Nonnull KClass<T> fromType);

    /**
     * Creates a query for the specified query {@code template}.
     *
     * @param template the query template.
     * @return the query.
     */
    KQuery query(@Nonnull StringTemplate template);

    /**
     * Creates a query for the specified query {@code template}.
     *
     * @param function used to define the condition to join on.
     * @return the query.
     */
    default KQuery query(@Nonnull TemplateFunction function) {
        return query(template(function));
    }
}
