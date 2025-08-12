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
package st.orm.template

import st.orm.SelectMode.FLAT
import st.orm.template.Templates.select
import kotlin.reflect.KClass

/**
 * The subquery template is used to construct subqueries that can be linked to the outer query.
 *
 * Unlike regular queries, subqueries only select fields from the primary table; fields from nested (foreign key)
 * records are not included. Additionally, subqueries cannot be directly built — they should be passed as
 * a query builder object when constructing the outer query.
 *
 * @since 1.1
 */
interface SubqueryTemplate {
    /**
     * Create a subquery for the given table.
     *
     * @param fromType the table to create the subquery for.
     * @param <T> the table type to select from.
     * @return the subquery builder.
     */
    fun <T : Record> subquery(fromType: KClass<T>): QueryBuilder<T, *, *> {
        return subquery(fromType, fromType)
    }

    /**
     * Crate a subquery for the given table and select type.
     *
     * @param fromType the table to create the subquery for.
     * @param selectType the type to select.
     * @return the subquery builder.
     * @param <T> the table type to select from.
     * @param <R> the result type.
     */
    fun <T : Record, R : Record> subquery(fromType: KClass<T>, selectType: KClass<R>): QueryBuilder<T, *, *> {
        return subquery(fromType) { t(select(selectType, FLAT)) }
    }

    /**
     * Create a subquery for the given table and select type using the given `template`.
     *
     * @param fromType the table to create the subquery for.
     * @param template the select clause template.
     * @return the subquery builder.
     * @param <T> the table type to select from.
     */
    fun <T : Record> subquery(fromType: KClass<T>, builder: TemplateBuilder): QueryBuilder<T, *, *> {
        return subquery(fromType, builder.build())
    }

    /**
     * Create a subquery for the given table and select type using the given `template`.
     *
     * @param fromType the table to create the subquery for.
     * @param template the select clause template.
     * @return the subquery builder.
     * @param <T> the table type to select from.
     */
    fun <T : Record> subquery(fromType: KClass<T>, template: TemplateString): QueryBuilder<T, *, *>
}
