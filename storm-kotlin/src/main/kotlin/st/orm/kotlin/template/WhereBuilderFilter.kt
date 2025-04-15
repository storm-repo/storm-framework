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
package st.orm.kotlin.template

import st.orm.Ref
import st.orm.template.Metamodel
import st.orm.template.Operator
import st.orm.template.QueryBuilder.PredicateBuilder
import st.orm.template.QueryBuilder.WhereBuilder

/**
 * Adds a condition to the WHERE clause that matches the specified record. The record can represent any of
 * the related tables in the table graph.
 *
 * @param path the path to the object in the table graph.
 * @param record the records to match.
 * @return the predicate builder.
 */
fun <V: Record, T: Record, R, ID> WhereBuilder<T, R, ID>.filter(
    path: Metamodel<T, V>,
    record: V
): PredicateBuilder<T, R, ID> = this.`when`(path, record)

/**
 * Adds a condition to the WHERE clause that matches the specified record. The record can represent any of
 * the related tables in the table graph or manually added joins.
 *
 * @param record the records to match.
 * @return the predicate builder.
 */
fun <V: Record, T: Record, R, ID> WhereBuilder<T, R, ID>.filterAny(
    path: Metamodel<*, V>,
    record: V
): PredicateBuilder<T, R, ID> = this.whenAny(path, record)

/**
 * Adds a condition to the WHERE clause that matches the specified ref. The record can represent any of
 * the related tables in the table graph.
 *
 * @param path the path to the object in the table graph.
 * @param ref the ref to match.
 * @return the predicate builder.
 */
fun <V: Record, T: Record, R, ID> WhereBuilder<T, R, ID>.filter(
    path: Metamodel<T, V>,
    ref: Ref<V>
): PredicateBuilder<T, R, ID> = this.`when`(path, ref)

/**
 * Adds a condition to the WHERE clause that matches the specified ref. The record can represent any of
 * the related tables in the table graph or manually added joins.
 *
 * @param path the path to the object in the table graph.
 * @param ref the ref to match.
 * @return the predicate builder.
 */
fun <V: Record, T: Record, R, ID> WhereBuilder<T, R, ID>.filterAny(
    path: Metamodel<*, V>,
    ref: Ref<V>
): PredicateBuilder<T, R, ID> = this.whenAny(path, ref)

/**
 * Adds a condition to the WHERE clause that matches the specified refs. The refs can represent any of
 * the related tables in the table graph.
 *
 * @param path the path to the ref in the table graph.
 * @param refs the refs to match.
 * @return the predicate builder.
 */
fun <V: Record, T: Record, R, ID> WhereBuilder<T, R, ID>.filterRef(
    path: Metamodel<T, V>,
    refs: Iterable<Ref<V>>
): PredicateBuilder<T, R, ID> = this.whenRef(path, refs)

/**
 * Adds a condition to the WHERE clause that matches the specified refs. The refs can represent any of
 * the related tables in the table graph.
 *
 * @param path the path to the ref in the table graph.
 * @param refs the refs to match.
 * @return the predicate builder.
 */
fun <V: Record, T: Record, R, ID> WhereBuilder<T, R, ID>.filterAnyRef(
    path: Metamodel<*, V>,
    refs: Iterable<Ref<V>>
): PredicateBuilder<T, R, ID> = this.whenAnyRef(path, refs)

/**
 * Adds a condition to the WHERE clause that matches the specified records. The records can represent any of
 * the related tables in the table graph.
 *
 * @param path the path to the object in the table graph.
 * @param records the records to match.
 * @return the predicate builder.
 */
fun <V: Record, T: Record, R, ID> WhereBuilder<T, R, ID>.filter(
    path: Metamodel<T, V>,
    records: Iterable<V>
): PredicateBuilder<T, R, ID> = this.`when`(path, records)

/**
 * Adds a condition to the WHERE clause that matches the specified records. The records can represent any of
 * the related tables in the table graph or manually added joins.
 *
 * @param path the path to the object in the table graph.
 * @param records the records to match.
 * @return the predicate builder.
 */
fun <V: Record, T: Record, R, ID> WhereBuilder<T, R, ID>.filterAny(
    path: Metamodel<*, V>,
    records: Iterable<V>
): PredicateBuilder<T, R, ID> = this.whenAny(path, records)

/**
 * Adds a condition to the WHERE clause that matches the specified objects at the specified path in the table
 * graph.
 *
 * @param path the path to the object in the table graph.
 * @param operator the operator to use for the comparison.
 * @param items the objects to match, which can be primary keys, records representing the table, or fields in the
 *          table graph.
 * @return the query builder.
 * @param <V> the type of the object that the metamodel represents.
 */
fun <V, T: Record, R, ID> WhereBuilder<T, R, ID>.filter(
    path: Metamodel<T, V>,
    operator: Operator,
    items: Iterable<V>
): PredicateBuilder<T, R, ID> = this.`when`(path, operator, items)

/**
 * Adds a condition to the WHERE clause that matches the specified objects at the specified path in the table
 * graph or manually added joins.
 *
 * @param path the path to the object in the table graph.
 * @param operator the operator to use for the comparison.
 * @param items the objects to match, which can be primary keys, records representing the table, or fields in the
 *          table graph.
 * @return the query builder.
 * @param <V> the type of the object that the metamodel represents.
 */
fun <V, T: Record, R, ID> WhereBuilder<T, R, ID>.filterAny(
    path: Metamodel<*, V>,
    operator: Operator,
    items: Iterable<V>
): PredicateBuilder<T, R, ID> = this.whenAny(path, operator, items)

/**
 * Adds a condition to the WHERE clause that matches the specified objects at the specified path in the table
 * graph.
 *
 * @param path the path to the object in the table graph.
 * @param operator the operator to use for the comparison.
 * @param items the object(s) to match, which can be primary keys, records representing the table, or fields in the
 *          table graph.
 * @return the query builder.
 * @param <V> the type of the object that the metamodel represents.
 */
fun <V, T: Record, R, ID> WhereBuilder<T, R, ID>.filter(
    path: Metamodel<T, V>,
    operator: Operator,
    vararg items: V
): PredicateBuilder<T, R, ID> = this.`when`(path, operator, *items)

/**
 * Adds a condition to the WHERE clause that matches the specified objects at the specified path in the table
 * graph or manually added joins.
 *
 * @param path the path to the object in the table graph.
 * @param operator the operator to use for the comparison.
 * @param items the object(s) to match, which can be primary keys, records representing the table, or fields in the
 *          table graph.
 * @return the query builder.
 * @param <V> the type of the object that the metamodel represents.
 */
fun <V, T: Record, R, ID> WhereBuilder<T, R, ID>.filterAny(
    path: Metamodel<*, V>,
    operator: Operator,
    vararg items: V
): PredicateBuilder<T, R, ID> = this.whenAny(path, operator, *items)