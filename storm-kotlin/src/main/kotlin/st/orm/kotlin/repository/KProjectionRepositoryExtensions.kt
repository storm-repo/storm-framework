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
import st.orm.kotlin.CloseableSequence
import st.orm.kotlin.template.KQueryBuilder.KPredicateBuilder
import st.orm.kotlin.template.KQueryBuilder.KWhereBuilder
import st.orm.repository.Projection
import st.orm.template.Metamodel
import st.orm.template.Operator.EQUALS
import st.orm.template.Operator.IN
import kotlin.jvm.optionals.getOrNull

/**
 * Retrieves all projections of type [T] from the repository.
 *
 * @return a list containing all projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.findAllRef(): List<Ref<T>>
        where T : Record, T : Projection<ID> =
    selectRef().resultList

/**
 * Retrieves all projections of type [T] from the repository.
 *
 * The resulting sequence is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @return a sequence containing all projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.selectAllRef(): CloseableSequence<Ref<T>>
        where T : Record, T : Projection<ID> =
    selectRef().resultSequence

/**
 * Retrieves an optional projection of type [T] based on a single field and its value.
 * Returns null if no matching projection is found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return An optional projection, or null if none found.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.findBy(field: Metamodel<T, V>, value: V): T?
        where T : Record, T : Projection<ID> =
    select().where(field, EQUALS, value).optionalResult.getOrNull()

/**
 * Retrieves an optional projection of type [T] based on a single field and its value.
 * Returns null if no matching projection is found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return An optional projection, or null if none found.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.findBy(field: Metamodel<T, V>, value: Ref<V>): T?
        where T : Record, T : Projection<ID>, V : Record =
    select().where(field, value).optionalResult.getOrNull()

/**
 * Retrieves projections of type [T] matching a single field and a single value.
 * Returns an empty list if no projections are found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return List of matching projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.findAllBy(field: Metamodel<T, V>, value: V): List<T>
        where T : Record, T : Projection<ID> =
    select().where(field, EQUALS, value).resultList

/**
 * Retrieves projections of type [T] matching a single field and a single value.
 * Returns an empty sequence if no projections are found.
 *
 * The resulting sequence is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return a sequence of matching projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.selectAllBy(field: Metamodel<T, V>, value: V): CloseableSequence<T>
        where T : Record, T : Projection<ID> =
    select().where(field, EQUALS, value).resultSequence

/**
 * Retrieves projections of type [T] matching a single field and a single value.
 * Returns an empty list if no projections are found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return a list of matching projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.findAllBy(field: Metamodel<T, V>, value: Ref<V>): List<T>
        where T : Record, T : Projection<ID>, V : Record =
    select().where(field, value).resultList

/**
 * Retrieves projections of type [T] matching a single field and a single value.
 * Returns an empty sequence if no projections are found.
 *
 * The resulting sequence is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return a sequence of matching projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.selectAllBy(field: Metamodel<T, V>, value: Ref<V>): CloseableSequence<T>
        where T : Record, T : Projection<ID>, V : Record =
    select().where(field, value).resultSequence

/**
 * Retrieves projections of type [T] matching a single field against multiple values.
 * Returns an empty list if no projections are found.
 *
 * @param field Metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return List of matching projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.findAllBy(field: Metamodel<T, V>, values: Iterable<V>): List<T>
        where T : Record, T : Projection<ID> =
    select().where(field, IN, values).resultList

/**
 * Retrieves projections of type [T] matching a single field against multiple values.
 * Returns an empty sequence if no projections are found.
 *
 * The resulting sequence is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field Metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return at sequence of matching projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.selectAllBy(field: Metamodel<T, V>, values: Iterable<V>): CloseableSequence<T>
        where T : Record, T : Projection<ID> =
    select().where(field, IN, values).resultSequence

/**
 * Retrieves projections of type [T] matching a single field against multiple values.
 * Returns an empty list if no projections are found.
 *
 * @param field Metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return a list of matching projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.findAllByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): List<T>
        where T : Record, T : Projection<ID>, V : Record =
    select().whereRef(field, values).resultList

/**
 * Retrieves projections of type [T] matching a single field against multiple values.
 * Returns an empty sequence if no projections are found.
 *
 * @param field Metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return a sequence of matching projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.selectAllByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): CloseableSequence<T>
        where T : Record, T : Projection<ID>, V : Record =
    select().whereRef(field, values).resultSequence

/**
 * Retrieves exactly one projection of type [T] based on a single field and its value.
 * Throws an exception if no projection or more than one projection is found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return the matching projection.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.getBy(field: Metamodel<T, V>, value: V): T
        where T : Record, T : Projection<ID> =
    select().where(field, EQUALS, value).singleResult

/**
 * Retrieves exactly one projection of type [T] based on a single field and its value.
 * Throws an exception if no projection or more than one projection is found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return the matching projection.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.getBy(field: Metamodel<T, V>, value: Ref<V>): T
        where T : Record, T : Projection<ID>, V : Record =
    select().where(field, value).singleResult

/**
 * Retrieves an optional projection of type [T] based on a single field and its value.
 * Returns null if no matching projection is found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return an optional projection, or null if none found.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.findRefBy(field: Metamodel<T, V>, value: V): Ref<T>
        where T : Record, T : Projection<ID> =
    selectRef().where(field, EQUALS, value).optionalResult.orElse(Ref.ofNull<T>())

/**
 * Retrieves an optional projection of type [T] based on a single field and its value.
 * Returns null if no matching projection is found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return an optional projection, or null if none found.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.findRefBy(field: Metamodel<T, V>, value: Ref<V>): Ref<T>
        where T : Record, T : Projection<ID>, V : Record =
    selectRef().where(field, value).optionalResult.orElse(Ref.ofNull<T>())

/**
 * Retrieves projections of type [T] matching a single field and a single value.
 * Returns an empty list if no projections are found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return a list of matching projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.findAllRefBy(field: Metamodel<T, V>, value: V): List<Ref<T>>
        where T : Record, T : Projection<ID> =
    selectRef().where(field, EQUALS, value).resultList

/**
 * Retrieves projections of type [T] matching a single field and a single value.
 * Returns an empty sequence if no projections are found.
 *
 * The resulting sequence is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return a sequence of matching projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.selectAllRefBy(field: Metamodel<T, V>, value: V): CloseableSequence<Ref<T>>
        where T : Record, T : Projection<ID> =
    selectRef().where(field, EQUALS, value).resultSequence

/**
 * Retrieves projections of type [T] matching a single field and a single value.
 * Returns an empty list if no projections are found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return a list of matching projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.findAllRefBy(field: Metamodel<T, V>, value: Ref<V>): List<Ref<T>>
        where T : Record, T : Projection<ID>, V : Record =
    selectRef().where(field, value).resultList

/**
 * Retrieves projections of type [T] matching a single field and a single value.
 * Returns an empty sequence if no projections are found.
 *
 * The resulting sequence is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return a sequence of matching projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.selectAllRefBy(field: Metamodel<T, V>, value: Ref<V>): CloseableSequence<Ref<T>>
        where T : Record, T : Projection<ID>, V : Record =
    selectRef().where(field, value).resultSequence

/**
 * Retrieves projections of type [T] matching a single field against multiple values.
 * Returns an empty list if no projections are found.
 *
 * @param field Metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return a list of matching projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.findAllRefBy(field: Metamodel<T, V>, values: Iterable<V>): List<Ref<T>>
        where T : Record, T : Projection<ID> =
    selectRef().where(field, IN, values).resultList

/**
 * Retrieves projections of type [T] matching a single field against multiple values.
 * Returns an empty sequence if no projections are found.
 *
 * The resulting sequence is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field Metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return a sequence of matching projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.selectAllRefBy(field: Metamodel<T, V>, values: Iterable<V>): CloseableSequence<Ref<T>>
        where T : Record, T : Projection<ID> =
    selectRef().where(field, IN, values).resultSequence

/**
 * Retrieves projections of type [T] matching a single field against multiple values.
 * Returns an empty list if no projections are found.
 *
 * @param field Metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return a list of matching projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.findAllRefByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): List<Ref<T>>
        where T : Record, T : Projection<ID>, V : Record =
    selectRef().whereRef(field, values).resultList


/**
 * Retrieves projections of type [T] matching a single field against multiple values.
 * Returns an empty sequence if no projections are found.
 *
 * The resulting sequence is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field Metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return a sequence of matching projections.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.selectAllRefByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): CloseableSequence<Ref<T>>
        where T : Record, T : Projection<ID>, V : Record =
    selectRef().whereRef(field, values).resultSequence

/**
 * Retrieves exactly one projection of type [T] based on a single field and its value.
 * Throws an exception if no projection or more than one projection is found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return the matching projection.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.getRefBy(field: Metamodel<T, V>, value: V): Ref<T>
        where T : Record, T : Projection<ID> =
    selectRef().where(field, EQUALS, value).singleResult

/**
 * Retrieves exactly one projection of type [T] based on a single field and its value.
 * Throws an exception if no projection or more than one projection is found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return the matching projection.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID, V> KProjectionRepository<T, ID>.getRefBy(field: Metamodel<T, V>, value: Ref<V>): Ref<T>
        where T : Record, T : Projection<ID>, V : Record =
    selectRef().where(field, value).singleResult

/**
 * Retrieves projections of type [T] matching the specified predicate.
 *
 * @return a list of matching projections.
 */
fun <T, ID> KProjectionRepository<T, ID>.findAll(
    predicate: KWhereBuilder<T, T, ID>.() -> KPredicateBuilder<*, *, *>
): List<T> where T : Record, T : Projection<ID> =
    select().where(predicate).resultList

/**
 * Retrieves projections of type [T] matching the specified predicate.
 *
 * @return a list of matching projections.
 */
fun <T, ID> KProjectionRepository<T, ID>.findAllRef(
    predicate: KWhereBuilder<T, Ref<T>, ID>.() -> KPredicateBuilder<*, *, *>
): List<Ref<T>> where T : Record, T : Projection<ID> =
    selectRef().where(predicate).resultList

/**
 * Retrieves an optional projection of type [T] matching the specified predicate.
 * Returns null if no matching projection is found.
 *
 * @return an optional projection, or null if none found.
 */
fun <T, ID> KProjectionRepository<T, ID>.find(
    predicate: KWhereBuilder<T, T, ID>.() -> KPredicateBuilder<*, *, *>
): T? where T : Record, T : Projection<ID> =
    select().where(predicate).optionalResult.getOrNull()

/**
 * Retrieves an optional projection of type [T] matching the specified predicate.
 * Returns null if no matching projection is found.
 *
 * @return an optional projection, or null if none found.
 */
fun <T, ID> KProjectionRepository<T, ID>.findRef(
    predicate: KWhereBuilder<T, Ref<T>, ID>.() -> KPredicateBuilder<*, *, *>
): Ref<T> where T : Record, T : Projection<ID> =
    selectRef().where(predicate).optionalResult.orElse(Ref.ofNull())

/**
 * Retrieves a single projection of type [T] matching the specified predicate.
 * Throws an exception if no projection or more than one projection is found.
 *
 * @return the matching projection.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID> KProjectionRepository<T, ID>.get(
    predicate: KWhereBuilder<T, T, ID>.() -> KPredicateBuilder<*, *, *>
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
fun <T, ID> KProjectionRepository<T, ID>.getRef(
    predicate: KWhereBuilder<T, Ref<T>, ID>.() -> KPredicateBuilder<*, *, *>
): Ref<T> where T : Record, T : Projection<ID> =
    selectRef().where(predicate).singleResult

/**
 * Retrieves projections of type [T] matching the specified predicate.
 *
 * The resulting sequence is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @return a sequence of matching projections.
 */
fun <T, ID> KProjectionRepository<T, ID>.select(
    predicate: KWhereBuilder<T, T, ID>.() -> KPredicateBuilder<*, *, *>
): CloseableSequence<T> where T : Record, T : Projection<ID> =
    select().where(predicate).resultSequence

/**
 * Retrieves projections of type [T] matching the specified predicate.
 *
 * The resulting sequence is lazily loaded, meaning that the projections are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of projections.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @return a sequence of matching projections.
 */
fun <T, ID> KProjectionRepository<T, ID>.selectRef(
    predicate: KWhereBuilder<T, Ref<T>, ID>.() -> KPredicateBuilder<*, *, *>
): CloseableSequence<Ref<T>> where T : Record, T : Projection<ID> =
    selectRef().where(predicate).resultSequence
