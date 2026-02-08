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

import st.orm.Data

/**
 * Represents a composable predicate for the WHERE clause of a query, supporting `AND` and `OR` composition.
 *
 * `PredicateBuilder` instances are returned by the methods on [WhereBuilder] and can be combined using [and]
 * and [or] to build compound conditions. Each combinator returns a new `PredicateBuilder` that represents the
 * combined expression.
 *
 * Methods named `and`/`or` are type-safe and restrict predicates to the root table's entity graph.
 * Methods named `andAny`/`orAny` accept predicates from any table, including manually added joins.
 *
 * ## Example
 * ```kotlin
 * val users = userRepository
 *     .select()
 *     .where { predicate ->
 *         predicate
 *             .where(User_.active, EQUALS, true)
 *             .and(predicate.where(User_.email, IS_NOT_NULL))
 *             .or(predicate.where(User_.role, EQUALS, "admin"))
 *     }
 *     .getResultList()
 * ```
 *
 * @param T the type of the table being queried.
 * @param R the type of the result.
 * @param ID the type of the primary key.
 * @see WhereBuilder
 * @see QueryBuilder
 */
interface PredicateBuilder<T : Data, R, ID> {
    /**
     * Adds a predicate to the WHERE clause using an AND condition.
     *
     * This method combines the specified predicate with existing predicates using an AND operation, ensuring
     * that all added conditions must be true.
     *
     * @param predicate the predicate to add.
     * @return the predicate builder.
     */
    infix fun and(predicate: PredicateBuilder<T, *, *>): PredicateBuilder<T, R, ID>

    /**
     * Adds a predicate to the WHERE clause using an AND condition.
     *
     *
     * This method combines the specified predicate with existing predicates using an AND operation, ensuring
     * that all added conditions must be true.
     *
     * @param predicate the predicate to add.
     * @return the predicate builder.
     */
    infix fun <TX : Data, RX, IDX> andAny(predicate: PredicateBuilder<TX, RX, IDX>): PredicateBuilder<TX, RX, IDX>

    /**
     * Adds a predicate to the WHERE clause using an AND condition.
     *
     * This method combines the specified predicate with existing predicates using an AND operation, ensuring
     * that all added conditions must be true.
     *
     * @param template the predicate builder to add.
     * @return the predicate builder.
     */
    infix fun and(template: TemplateBuilder) : PredicateBuilder<T, R, ID> {
        return and(template.build())
    }

    /**
     * Adds a predicate to the WHERE clause using an AND condition.
     *
     * This method combines the specified predicate with existing predicates using an AND operation, ensuring
     * that all added conditions must be true.
     *
     * @param template the predicate template to add.
     * @return the predicate builder.
     */
    infix fun and(template: TemplateString): PredicateBuilder<T, R, ID>

    /**
     * Adds a predicate to the WHERE clause using an OR condition.
     *
     *
     * This method combines the specified predicate with existing predicates using an OR operation, allowing any
     * of the added conditions to be true.
     *
     * @param predicate the predicate to add.
     * @return the predicate builder.
     */
    infix fun or(predicate: PredicateBuilder<T, *, *>): PredicateBuilder<T, R, ID>

    /**
     * Adds a predicate to the WHERE clause using an OR condition.
     *
     *
     * This method combines the specified predicate with existing predicates using an OR operation, allowing any
     * of the added conditions to be true.
     *
     * @param predicate the predicate to add.
     * @return the predicate builder.
     */
    infix fun <TX : Data, RX, IDX> orAny(predicate: PredicateBuilder<TX, RX, IDX>): PredicateBuilder<TX, RX, IDX>

    /**
     * Adds a predicate to the WHERE clause using an OR condition.
     *
     * This method combines the specified predicate with existing predicates using an OR operation, ensuring
     * that all added conditions must be true.
     *
     * @param template the predicate builder to add.
     * @return the predicate builder.
     */
    infix fun or(template: TemplateBuilder) : PredicateBuilder<T, R, ID> {
        return or(template.build())
    }

    /**
     * Adds a predicate to the WHERE clause using an OR condition.
     *
     * This method combines the specified predicate with existing predicates using an OR operation, ensuring
     * that all added conditions must be true.
     *
     * @param template the predicate template to add.
     * @return the predicate builder.
     */
    infix fun or(template: TemplateString): PredicateBuilder<T, R, ID>
}
