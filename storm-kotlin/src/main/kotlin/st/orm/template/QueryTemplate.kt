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
package st.orm.template

import st.orm.BindVars
import st.orm.Data
import st.orm.Ref
import st.orm.template.TemplateString.Companion.wrap
import kotlin.reflect.KClass

/**
 * The query template is used to construct queries.
 */
interface QueryTemplate : SubqueryTemplate {
    /**
     * Create a new bind variables instance that can be used to add bind variables to a batch.
     *
     * @return a new bind variables instance.
     * @see PreparedQuery.addBatch
     */
    fun createBindVars(): BindVars

    /**
     * Creates a ref instance for the specified record `type` and `pk`. This method can be used to generate
     * ref instances for entities, projections and regular records.
     *
     * @param type record type.
     * @param id primary key.
     * @return ref instance.
     * @param <T> table type.
     * @param <ID> primary key type.
     * @since 1.3
     */
    fun <T : Data, ID : Any> ref(type: KClass<T>, id: ID): Ref<T>

    /**
     * Creates a ref instance for the specified record `type` and `id`. This method can be used to generate
     * ref instances for entities, projections and regular records. The object returned by this method already contains
     * the fetched record.
     *
     * @param id primary key.
     * @return ref instance.
     * @param <T> table type.
     * @param <ID> primary key type.
     * @since 1.3
     */
    fun <T : Data, ID : Any> ref(record: T, id: ID): Ref<T>

    /**
     * Get the model for the specified record `type`. The model provides information about the type's database
     * name, primary keys and columns.
     *
     * @param type record type.
     * @return the model.
     * @param <T> table type.
     * @param <ID> primary key type.
     */
    fun <T : Data> model(type: KClass<T>): Model<T, *> {
        return model(type, false)
    }

    /**
     * Get the model for the specified record `type`. The model provides information about the type's database
     * name, primary keys and columns.
     *
     * @param type record type.
     * @param requirePrimaryKey whether to require a primary key.
     * @return the model.
     * @param <T> table type.
     * @param <ID> primary key type.
     * @since 1.3
     */
    fun <T : Data> model(type: KClass<T>, requirePrimaryKey: Boolean): Model<T, *>

    /**
     * Creates a query builder for the specified table.
     *
     * @param fromType the table to select from.
     * @return the query builder.
     * @param <T> the table type to select from.
     */
    fun <T : Data> selectFrom(fromType: KClass<T>): QueryBuilder<T, T, *> {
        return selectFrom(fromType, fromType)
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
    fun <T : Data, R : Data> selectFrom(
        fromType: KClass<T>,
        selectType: KClass<R>
    ): QueryBuilder<T, R, *> {
        return selectFrom(fromType, selectType, wrap(selectType))
    }

    /**
     * Creates a query builder for the specified table and select type using the given `template`.
     *
     * @param fromType the table to select from.
     * @param selectType the result type of the query.
     * @param template the select clause template.
     * @return the query builder.
     * @param <T> the table type to select from.
     * @param <R> the result type.
     */
    fun <T : Data, R : Any> selectFrom(
        fromType: KClass<T>,
        selectType: KClass<R>,
        template: TemplateBuilder
    ): QueryBuilder<T, R, *> {
        return selectFrom(fromType, selectType, template.build())
    }

    /**
     * Creates a query builder for the specified table and select type using the given `template`.
     *
     * @param fromType the table to select from.
     * @param selectType the result type of the query.
     * @param template the select clause template.
     * @return the query builder.
     * @param <T> the table type to select from.
     * @param <R> the result type.
     */
    fun <T : Data, R : Any> selectFrom(
        fromType: KClass<T>,
        selectType: KClass<R>,
        template: TemplateString
    ): QueryBuilder<T, R, *>

    /**
     * Creates a query builder for the specified table to delete from.
     *
     * @param fromType the table to delete from.
     * @return the query builder.
     * @param <T> the table type to delete from.
     */
    fun <T : Data> deleteFrom(fromType: KClass<T>): QueryBuilder<T, *, *>

    /**
     * Creates a query for the specified `query` string.
     *
     * @param query the query.
     * @return the query.
     */
    fun query(query: String): Query

    /**
     * Creates a query for the specified query `template`.
     *
     * @param template the query template.
     * @return the query.
     */
    fun query(template: TemplateBuilder): Query {
        return query(template.build())
    }

    /**
     * Creates a query for the specified query `template`.
     *
     * @param template the query template.
     * @return the query.
     */
    fun query(template: TemplateString): Query
}
