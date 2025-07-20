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
package st.orm.kt.template

/**
 * A builder for constructing the predicates of the WHERE clause of the query.
 *
 * @param <T> the type of the table being queried.
 * @param <R> the type of the result.
 * @param <ID> the type of the primary key.
 */
interface PredicateBuilder<T : Record, R, ID> {
    /**
     * Adds a predicate to the WHERE clause using an AND condition.
     *
     * This method combines the specified predicate with existing predicates using an AND operation, ensuring
     * that all added conditions must be true.
     *
     * @param predicate the predicate to add.
     * @return the predicate builder.
     */
    fun and(predicate: PredicateBuilder<T, *, *>): PredicateBuilder<T, R, ID>

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
    fun andAny(predicate: PredicateBuilder<*, *, *>): PredicateBuilder<T, R, ID>

    /**
     * Adds a predicate to the WHERE clause using an AND condition.
     *
     * This method combines the specified predicate with existing predicates using an AND operation, ensuring
     * that all added conditions must be true.
     *
     * @param builder the predicate builder to add.
     * @return the predicate builder.
     */
    fun and(builder: TemplateBuilder) : PredicateBuilder<T, R, ID> {
        return and(builder.build())
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
    fun and(template: TemplateString): PredicateBuilder<T, R, ID>

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
    fun or(predicate: PredicateBuilder<T, *, *>): PredicateBuilder<T, R, ID>

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
    fun orAny(predicate: PredicateBuilder<*, *, *>): PredicateBuilder<T, R, ID>

    /**
     * Adds a predicate to the WHERE clause using an OR condition.
     *
     * This method combines the specified predicate with existing predicates using an OR operation, ensuring
     * that all added conditions must be true.
     *
     * @param builder the predicate builder to add.
     * @return the predicate builder.
     */
    fun or(builder: TemplateBuilder) : PredicateBuilder<T, R, ID> {
        return or(builder.build())
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
    fun or(template: TemplateString): PredicateBuilder<T, R, ID>
}
