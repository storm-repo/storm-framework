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
@file:Suppress("unused")

package st.orm.kotlin.repository

import st.orm.Ref
import st.orm.kotlin.template.KPredicateBuilder
import st.orm.kotlin.template.impl.KPredicateBuilderFactory.bridge
import st.orm.repository.Projection
import st.orm.repository.ProjectionRepository
import st.orm.template.Metamodel
import st.orm.template.Operator.EQUALS
import st.orm.template.Operator.IN
import st.orm.template.PredicateBuilder
import st.orm.template.WhereBuilder
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull

/**
 * Retrieves all projections of type [T] from the repository.
 *
 * @return a list containing all projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.findAllRef(): List<Ref<T>>
        where T : Record, T : Projection<ID> =
    selectRef().resultList

/**
 * Retrieves all projections of type [T] from the repository.
 *
 * The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @return a stream containing all projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.selectAllRef(): Stream<Ref<T>>
        where T : Record, T : Projection<ID> =
    selectRef().resultStream

/**
 * Retrieves an optional projection of type [T] based on a single field and its value.
 * Returns null if no matching projection is found.
 *
 * @param field metamodel reference of the projection field.
 * @param value The value to match against.
 * @return an optional projection, or null if none found.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.findBy(field: Metamodel<T, V>, value: V): T?
        where T : Record, T : Projection<ID> = 
    select().where(field, EQUALS, value).optionalResult.getOrNull()

/**
 * Retrieves an optional projection of type [T] based on a single field and its value.
 * Returns null if no matching projection is found.
 *
 * @param field metamodel reference of the projection field.
 * @param value The value to match against.
 * @return an optional projection, or null if none found.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.findBy(field: Metamodel<T, V>, value: Ref<V>): T?
        where T : Record, T : Projection<ID>, V : Record =
    select().where(field, value).optionalResult.getOrNull()

/**
 * Retrieves projections of type [T] matching a single field and a single value.
 * Returns an empty list if no projections are found.
 *
 * @param field metamodel reference of the projection field.
 * @param value The value to match against.
 * @return list of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.findAllBy(field: Metamodel<T, V>, value: V): List<T>
        where T : Record, T : Projection<ID> =
    select().where(field, EQUALS, value).resultList

/**
 * Retrieves projections of type [T] matching a single field and a single value.
 * Returns an empty stream if no projections are found.
 *
 * The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 * 
 * @param field metamodel reference of the projection field.
 * @param value The value to match against.
 * @return a stream of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.selectBy(field: Metamodel<T, V>, value: V): Stream<T>
        where T : Record, T : Projection<ID> =
    select().where(field, EQUALS, value).resultStream

/**
 * Retrieves projections of type [T] matching a single field and a single value.
 * Returns an empty list if no projections are found.
 *
 * @param field metamodel reference of the projection field.
 * @param value The value to match against.
 * @return a list of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.findAllBy(field: Metamodel<T, V>, value: Ref<V>): List<T>
        where T : Record, T : Projection<ID>, V : Record =
    select().where(field, value).resultList

/**
 * Retrieves projections of type [T] matching a single field and a single value.
 * Returns an empty stream if no projections are found.
 *
 * The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field metamodel reference of the projection field.
 * @param value The value to match against.
 * @return a stream of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.selectBy(field: Metamodel<T, V>, value: Ref<V>): Stream<T>
        where T : Record, T : Projection<ID>, V : Record =
    select().where(field, value).resultStream

/**
 * Retrieves projections of type [T] matching a single field against multiple values.
 * Returns an empty list if no projections are found.
 *
 * @param field metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return list of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.findAllBy(field: Metamodel<T, V>, values: Iterable<V>): List<T>
        where T : Record, T : Projection<ID> =
    select().where(field, IN, values).resultList

/**
 * Retrieves projections of type [T] matching a single field against multiple values.
 * Returns an empty stream if no projections are found.
 * 
 * The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return at stream of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.selectBy(field: Metamodel<T, V>, values: Iterable<V>): Stream<T>
        where T : Record, T : Projection<ID> =
    select().where(field, IN, values).resultStream

/**
 * Retrieves projections of type [T] matching a single field against multiple values.
 * Returns an empty list if no projections are found.
 *
 * @param field metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return a list of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.findAllByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): List<T>
        where T : Record, T : Projection<ID>, V : Record =
    select().whereRef(field, values).resultList

/**
 * Retrieves projections of type [T] matching a single field against multiple values.
 * Returns an empty stream if no projections are found.
 *
 * @param field metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return a stream of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.selectByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): Stream<T>
        where T : Record, T : Projection<ID>, V : Record =
    select().whereRef(field, values).resultStream

/**
 * Retrieves exactly one projection of type [T] based on a single field and its value.
 * Throws an exception if no projection or more than one projection is found.
 *
 * @param field metamodel reference of the projection field.
 * @param value The value to match against.
 * @return the matching projection.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.getBy(field: Metamodel<T, V>, value: V): T
        where T : Record, T : Projection<ID> =
    select().where(field, EQUALS, value).singleResult

/**
 * Retrieves exactly one projection of type [T] based on a single field and its value.
 * Throws an exception if no projection or more than one projection is found.
 *
 * @param field metamodel reference of the projection field.
 * @param value The value to match against.
 * @return the matching projection.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.getBy(field: Metamodel<T, V>, value: Ref<V>): T
        where T : Record, T : Projection<ID>, V : Record =
    select().where(field, value).singleResult

/**
 * Retrieves an optional projection of type [T] based on a single field and its value.
 * Returns null if no matching projection is found.
 *
 * @param field metamodel reference of the projection field.
 * @param value The value to match against.
 * @return an optional projection, or null if none found.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.findRefBy(field: Metamodel<T, V>, value: V): Ref<T>
        where T : Record, T : Projection<ID> =
    selectRef().where(field, EQUALS, value).optionalResult.orElse(Ref.ofNull<T>())

/**
 * Retrieves an optional projection of type [T] based on a single field and its value.
 * Returns null if no matching projection is found.
 *
 * @param field metamodel reference of the projection field.
 * @param value The value to match against.
 * @return an optional projection, or null if none found.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.findRefBy(field: Metamodel<T, V>, value: Ref<V>): Ref<T>
        where T : Record, T : Projection<ID>, V : Record =
    selectRef().where(field, value).optionalResult.orElse(Ref.ofNull<T>())

/**
 * Retrieves projections of type [T] matching a single field and a single value.
 * Returns an empty list if no projections are found.
 *
 * @param field metamodel reference of the projection field.
 * @param value The value to match against.
 * @return a list of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.findAllRefBy(field: Metamodel<T, V>, value: V): List<Ref<T>>
        where T : Record, T : Projection<ID> =
    selectRef().where(field, EQUALS, value).resultList

/**
 * Retrieves projections of type [T] matching a single field and a single value.
 * Returns an empty stream if no projections are found.
 *
 * The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 * 
 * @param field metamodel reference of the projection field.
 * @param value The value to match against.
 * @return a stream of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.selectRefBy(field: Metamodel<T, V>, value: V): Stream<Ref<T>>
        where T : Record, T : Projection<ID> =
    selectRef().where(field, EQUALS, value).resultStream

/**
 * Retrieves projections of type [T] matching a single field and a single value.
 * Returns an empty list if no projections are found.
 *
 * @param field metamodel reference of the projection field.
 * @param value The value to match against.
 * @return a list of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.findAllRefBy(field: Metamodel<T, V>, value: Ref<V>): List<Ref<T>>
        where T : Record, T : Projection<ID>, V : Record =
    selectRef().where(field, value).resultList

/**
 * Retrieves projections of type [T] matching a single field and a single value.
 * Returns an empty stream if no projections are found.
 *
 * The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 * 
 * @param field metamodel reference of the projection field.
 * @param value The value to match against.
 * @return a stream of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.selectRefBy(field: Metamodel<T, V>, value: Ref<V>): Stream<Ref<T>>
        where T : Record, T : Projection<ID>, V : Record =
    selectRef().where(field, value).resultStream

/**
 * Retrieves projections of type [T] matching a single field against multiple values.
 * Returns an empty list if no projections are found.
 *
 * @param field metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return a list of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.findAllRefBy(field: Metamodel<T, V>, values: Iterable<V>): List<Ref<T>>
        where T : Record, T : Projection<ID> =
    selectRef().where(field, IN, values).resultList

/**
 * Retrieves projections of type [T] matching a single field against multiple values.
 * Returns an empty stream if no projections are found.
 *
 * The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 * 
 * @param field metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return a stream of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.selectRefBy(field: Metamodel<T, V>, values: Iterable<V>): Stream<Ref<T>>
        where T : Record, T : Projection<ID> =
    selectRef().where(field, IN, values).resultStream

/**
 * Retrieves projections of type [T] matching a single field against multiple values.
 * Returns an empty list if no projections are found.
 *
 * @param field metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return a list of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.findAllRefByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): List<Ref<T>>
        where T : Record, T : Projection<ID>, V : Record =
    selectRef().whereRef(field, values).resultList


/**
 * Retrieves projections of type [T] matching a single field against multiple values.
 * Returns an empty stream if no projections are found.
 *
 * The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 * 
 * @param field metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return a stream of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.selectRefByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): Stream<Ref<T>>
        where T : Record, T : Projection<ID>, V : Record =
    selectRef().whereRef(field, values).resultStream

/**
 * Retrieves exactly one projection of type [T] based on a single field and its value.
 * Throws an exception if no projection or more than one projection is found.
 *
 * @param field metamodel reference of the projection field.
 * @param value The value to match against.
 * @return the matching projection.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.getRefBy(field: Metamodel<T, V>, value: V): Ref<T>
        where T : Record, T : Projection<ID> =
    selectRef().where(field, EQUALS, value).singleResult

/**
 * Retrieves exactly one projection of type [T] based on a single field and its value.
 * Throws an exception if no projection or more than one projection is found.
 *
 * @param field metamodel reference of the projection field.
 * @param value The value to match against.
 * @return the matching projection.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.getRefBy(field: Metamodel<T, V>, value: Ref<V>): Ref<T>
        where T : Record, T : Projection<ID>, V : Record =
    selectRef().where(field, value).singleResult

/**
 * Retrieves projections of type [T] matching the specified predicate.
 *
 * @return a list of matching projections.
 */
fun <T, ID> ProjectionRepository<T, ID>.findAll(
    predicate: WhereBuilder<T, T, ID>.() -> PredicateBuilder<T, *, *>
): List<T> where T : Record, T : Projection<ID> =
    select().where(predicate).resultList

/**
 * Retrieves projections of type [T] matching the specified predicate.
 *
 * @return a list of matching projections.
 */
fun <T, ID> ProjectionRepository<T, ID>.findAll(predicateBuilder: KPredicateBuilder<T, *, *>): List<T>
        where T : Record, T : Projection<ID> =
    select().where { bridge(predicateBuilder) }.resultList

/**
 * Retrieves projections of type [T] matching the specified predicate.
 *
 * @return a list of matching projections.
 */
fun <T, ID> ProjectionRepository<T, ID>.findAllRef(
    predicate: WhereBuilder<T, Ref<T>, ID>.() -> PredicateBuilder<T, *, *>
): List<Ref<T>> where T : Record, T : Projection<ID> =
    selectRef().where(predicate).resultList

/**
 * Retrieves projections of type [T] matching the specified predicate.
 *
 * @return a list of matching projections.
 */
fun <T, ID> ProjectionRepository<T, ID>.findAllRef(predicateBuilder: KPredicateBuilder<T, *, *>): List<Ref<T>>
        where T : Record, T : Projection<ID> =
    selectRef().where { bridge(predicateBuilder) }.resultList

/**
 * Retrieves an optional projection of type [T] matching the specified predicate.
 * Returns null if no matching projection is found.
 *
 * @return an optional projection, or null if none found.
 */
fun <T, ID> ProjectionRepository<T, ID>.find(
    predicate: WhereBuilder<T, T, ID>.() -> PredicateBuilder<T, *, *>
): T? where T : Record, T : Projection<ID> =
    select().where(predicate).optionalResult.getOrNull()

/**
 * Retrieves an optional projection of type [T] matching the specified predicate.
 * Returns null if no matching projection is found.
 *
 * @return an optional projection, or null if none found.
 */
fun <T, ID> ProjectionRepository<T, ID>.find(predicateBuilder: KPredicateBuilder<T, *, *>): T?
        where T : Record, T : Projection<ID> =
    select().where { bridge(predicateBuilder) }.optionalResult.getOrNull()

/**
 * Retrieves an optional projection of type [T] matching the specified predicate.
 * Returns null if no matching projection is found.
 *
 * @return an optional projection, or null if none found.
 */
fun <T, ID> ProjectionRepository<T, ID>.findRef(
    predicate: WhereBuilder<T, Ref<T>, ID>.() -> PredicateBuilder<T, *, *>
): Ref<T> where T : Record, T : Projection<ID> =
    selectRef().where(predicate).optionalResult.orElse(Ref.ofNull())

/**
 * Retrieves an optional projection of type [T] matching the specified predicate.
 * Returns null if no matching projection is found.
 *
 * @return an optional projection, or null if none found.
 */
fun <T, ID> ProjectionRepository<T, ID>.findRef(predicateBuilder: KPredicateBuilder<T, *, *>): Ref<T>
        where T : Record, T : Projection<ID> =
    selectRef().where { bridge(predicateBuilder) }.optionalResult.orElse(Ref.ofNull())

/**
 * Retrieves a single projection of type [T] matching the specified predicate.
 * Throws an exception if no projection or more than one projection is found.
 *
 * @return the matching projection.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID> ProjectionRepository<T, ID>.get(
    predicate: WhereBuilder<T, T, ID>.() -> PredicateBuilder<T, *, *>
): T where T : Record, T : Projection<ID> =
    select().where(predicate).singleResult

/**
 * Retrieves a single projection of type [T] matching the specified predicate.
 * Throws an exception if no projection or more than one projection is found.
 *
 * @return the matching projection.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID> ProjectionRepository<T, ID>.get(predicateBuilder: KPredicateBuilder<T, *, *>): T
        where T : Record, T : Projection<ID> =
    select().where { bridge(predicateBuilder) }.singleResult

/**
 * Retrieves a single projection of type [T] matching the specified predicate.
 * Throws an exception if no projection or more than one projection is found.
 *
 * @return the matching projection.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID> ProjectionRepository<T, ID>.getRef(
    predicate: WhereBuilder<T, Ref<T>, ID>.() -> PredicateBuilder<T, *, *>
): Ref<T> where T : Record, T : Projection<ID> =
    selectRef().where(predicate).singleResult

/**
 * Retrieves a single projection of type [T] matching the specified predicate.
 * Throws an exception if no projection or more than one projection is found.
 *
 * @return the matching projection.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID> ProjectionRepository<T, ID>.getRef(predicateBuilder: KPredicateBuilder<T, *, *>): Ref<T>
        where T : Record, T : Projection<ID> =
    selectRef().where { bridge(predicateBuilder) }.singleResult

/**
 * Retrieves projections of type [T] matching the specified predicate.
 *
 * The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 * 
 * @return a stream of matching projections.
 */
fun <T, ID> ProjectionRepository<T, ID>.select(
    predicate: WhereBuilder<T, T, ID>.() -> PredicateBuilder<T, *, *>
): Stream<T> where T : Record, T : Projection<ID> =
    select().where(predicate).resultStream

/**
 * Retrieves projections of type [T] matching the specified predicate.
 *
 * The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @return a stream of matching projections.
 */
fun <T, ID> ProjectionRepository<T, ID>.select(predicateBuilder: KPredicateBuilder<T, *, *>): Stream<T>
        where T : Record, T : Projection<ID> =
    select().where { bridge(predicateBuilder) }.resultStream

/**
 * Retrieves projections of type [T] matching the specified predicate.
 *
 * The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 * 
 * @return a stream of matching projections.
 */
fun <T, ID> ProjectionRepository<T, ID>.selectRef(
    predicate: WhereBuilder<T, Ref<T>, ID>.() -> PredicateBuilder<T, *, *>
): Stream<Ref<T>> where T : Record, T : Projection<ID> =
    selectRef().where(predicate).resultStream

/**
 * Retrieves projections of type [T] matching the specified predicate.
 *
 * The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @return a stream of matching projections.
 */
fun <T, ID> ProjectionRepository<T, ID>.selectRef(predicateBuilder: KPredicateBuilder<T, *, *>): Stream<Ref<T>>
        where T : Record, T : Projection<ID> =
    selectRef().where { bridge(predicateBuilder) }.resultStream

/**
 * Counts projections of type [T] matching the specified field and value.
 *
 * @param field metamodel reference of the projection field.
 * @param value the value to match against.
 * @return the count of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.countBy(
    field: Metamodel<T, V>,
    value: V
): Long where T : Record, T : Projection<ID> =
    selectCount().where(field, EQUALS, value).singleResult

/**
 * Counts projections of type [T] matching the specified field and referenced value.
 *
 * @param field metamodel reference of the projection field.
 * @param value the referenced value to match against.
 * @return the count of matching projections.
 */
fun <T, ID, V> ProjectionRepository<T, ID>.countByRef(
    field: Metamodel<T, V>,
    value: Ref<V>
): Long where T : Record, T : Projection<ID>, V : Record =
    selectCount().where(field, value).singleResult

/**
 * Counts projections of type [T] matching the specified predicate.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return the count of matching projections.
 */
fun <T, ID> ProjectionRepository<T, ID>.count(
    predicate: WhereBuilder<T, *, ID>.() -> PredicateBuilder<T, *, *>
): Long where T : Record, T : Projection<ID> =
    selectCount().where(predicate).singleResult

/**
 * Counts projections of type [T] matching the specified predicate.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return the count of matching projections.
 */
fun <T, ID> ProjectionRepository<T, ID>.count(predicateBuilder: KPredicateBuilder<T, *, *>): Long
        where T : Record, T : Projection<ID> =
    selectCount().where { bridge(predicateBuilder) }.singleResult
