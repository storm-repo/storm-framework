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
import st.orm.kotlin.template.KQueryBuilder.KPredicateBuilder
import st.orm.kotlin.template.impl.KPredicateBuilderFactory.bridge
import st.orm.repository.Entity
import st.orm.repository.EntityRepository
import st.orm.template.Metamodel
import st.orm.template.Operator.EQUALS
import st.orm.template.Operator.IN
import st.orm.template.QueryBuilder.PredicateBuilder
import st.orm.template.QueryBuilder.WhereBuilder
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull

/**
 * Retrieves all entities of type [T] from the repository.
 *
 * @return a list containing all entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.findAllRef(): List<Ref<T>>
        where T : Record, T : Entity<ID> =
    selectRef().resultList

/**
 * Retrieves all entities of type [T] from the repository.
 *
 * The resulting stream is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @return a stream containing all entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.selectAllRef(): Stream<Ref<T>>
        where T : Record, T : Entity<ID> =
    selectRef().resultStream

/**
 * Retrieves an optional entity of type [T] based on a single field and its value.
 * Returns null if no matching entity is found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return an optional entity, or null if none found.
 */
fun <T, ID, V> EntityRepository<T, ID>.findBy(field: Metamodel<T, V>, value: V): T?
        where T : Record, T : Entity<ID> =
    select().where(field, EQUALS, value).optionalResult.getOrNull()

/**
 * Retrieves an optional entity of type [T] based on a single field and its value.
 * Returns null if no matching entity is found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return an optional entity, or null if none found.
 */
fun <T, ID, V> EntityRepository<T, ID>.findBy(field: Metamodel<T, V>, value: Ref<V>): T?
        where T : Record, T : Entity<ID>, V : Record =
    select().where(field, value).optionalResult.getOrNull()

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return list of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.findAllBy(field: Metamodel<T, V>, value: V): List<T>
        where T : Record, T : Entity<ID> =
    select().where(field, EQUALS, value).resultList

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty stream if no entities are found.
 *
 * The resulting stream is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return a stream of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.selectBy(field: Metamodel<T, V>, value: V): Stream<T>
        where T : Record, T : Entity<ID> =
    select().where(field, EQUALS, value).resultStream

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return a list of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.findAllBy(field: Metamodel<T, V>, value: Ref<V>): List<T>
        where T : Record, T : Entity<ID>, V : Record =
    select().where(field, value).resultList

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty stream if no entities are found.
 *
 * The resulting stream is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return a stream of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.selectBy(field: Metamodel<T, V>, value: Ref<V>): Stream<T>
        where T : Record, T : Entity<ID>, V : Record =
    select().where(field, value).resultStream

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return list of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.findAllBy(field: Metamodel<T, V>, values: Iterable<V>): List<T>
        where T : Record, T : Entity<ID> =
    select().where(field, IN, values).resultList

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty stream if no entities are found.
 *
 * The resulting stream is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return at stream of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.selectBy(field: Metamodel<T, V>, values: Iterable<V>): Stream<T>
        where T : Record, T : Entity<ID> =
    select().where(field, IN, values).resultStream

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return a list of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.findAllByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): List<T>
        where T : Record, T : Entity<ID>, V : Record =
    select().whereRef(field, values).resultList

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty stream if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return a stream of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.selectByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): Stream<T>
        where T : Record, T : Entity<ID>, V : Record =
    select().whereRef(field, values).resultStream

/**
 * Retrieves exactly one entity of type [T] based on a single field and its value.
 * Throws an exception if no entity or more than one entity is found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return the matching entity.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID, V> EntityRepository<T, ID>.getBy(field: Metamodel<T, V>, value: V): T
        where T : Record, T : Entity<ID> =
    select().where(field, EQUALS, value).singleResult

/**
 * Retrieves exactly one entity of type [T] based on a single field and its value.
 * Throws an exception if no entity or more than one entity is found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return the matching entity.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID, V> EntityRepository<T, ID>.getBy(field: Metamodel<T, V>, value: Ref<V>): T
        where T : Record, T : Entity<ID>, V : Record =
    select().where(field, value).singleResult

/**
 * Retrieves an optional entity of type [T] based on a single field and its value.
 * Returns a ref with a null value if no matching entity is found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return an optional entity, or null if none found.
 */
fun <T, ID, V> EntityRepository<T, ID>.findRefBy(field: Metamodel<T, V>, value: V): Ref<T>
        where T : Record, T : Entity<ID> =
    selectRef().where(field, EQUALS, value).optionalResult.orElse(Ref.ofNull<T>())

/**
 * Retrieves an optional entity of type [T] based on a single field and its value.
 * Returns a ref with a null value if no matching entity is found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return an optional entity, or null if none found.
 */
fun <T, ID, V> EntityRepository<T, ID>.findRefBy(field: Metamodel<T, V>, value: Ref<V>): Ref<T>
        where T : Record, T : Entity<ID>, V : Record =
    selectRef().where(field, value).optionalResult.orElse(Ref.ofNull<T>())

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return a list of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.findAllRefBy(field: Metamodel<T, V>, value: V): List<Ref<T>>
        where T : Record, T : Entity<ID> =
    selectRef().where(field, EQUALS, value).resultList

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty stream if no entities are found.
 *
 * The resulting stream is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return a stream of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.selectRefBy(field: Metamodel<T, V>, value: V): Stream<Ref<T>>
        where T : Record, T : Entity<ID> =
    selectRef().where(field, EQUALS, value).resultStream

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return a list of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.findAllRefBy(field: Metamodel<T, V>, value: Ref<V>): List<Ref<T>>
        where T : Record, T : Entity<ID>, V : Record =
    selectRef().where(field, value).resultList

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty stream if no entities are found.
 *
 * The resulting stream is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return a stream of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.selectRefBy(field: Metamodel<T, V>, value: Ref<V>): Stream<Ref<T>>
        where T : Record, T : Entity<ID>, V : Record =
    selectRef().where(field, value).resultStream

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return a list of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.findAllRefBy(field: Metamodel<T, V>, values: Iterable<V>): List<Ref<T>>
        where T : Record, T : Entity<ID> =
    selectRef().where(field, IN, values).resultList

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty stream if no entities are found.
 *
 * The resulting stream is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return a stream of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.selectRefBy(field: Metamodel<T, V>, values: Iterable<V>): Stream<Ref<T>>
        where T : Record, T : Entity<ID> =
    selectRef().where(field, IN, values).resultStream

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return a list of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.findAllRefByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): List<Ref<T>>
        where T : Record, T : Entity<ID>, V : Record =
    selectRef().whereRef(field, values).resultList


/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty stream if no entities are found.
 *
 * The resulting stream is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return a stream of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.selectRefByRef(
    field: Metamodel<T, V>,
    values: Iterable<Ref<V>>
): Stream<Ref<T>>
        where T : Record, T : Entity<ID>, V : Record =
    selectRef().whereRef(field, values).resultStream

/**
 * Retrieves exactly one entity of type [T] based on a single field and its value.
 * Throws an exception if no entity or more than one entity is found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return the matching entity.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID, V> EntityRepository<T, ID>.getRefBy(field: Metamodel<T, V>, value: V): Ref<T>
        where T : Record, T : Entity<ID> =
    selectRef().where(field, EQUALS, value).singleResult

/**
 * Retrieves exactly one entity of type [T] based on a single field and its value.
 * Throws an exception if no entity or more than one entity is found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return the matching entity.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID, V> EntityRepository<T, ID>.getRefBy(field: Metamodel<T, V>, value: Ref<V>): Ref<T>
        where T : Record, T : Entity<ID>, V : Record =
    selectRef().where(field, value).singleResult

/**
 * Retrieves entities of type [T] matching the specified predicate.
 *
 * @return a list of matching entities.
 */
fun <T, ID> EntityRepository<T, ID>.findAll(
    predicate: WhereBuilder<T, T, ID>.() -> PredicateBuilder<T, *, *>
): List<T> where T : Record, T : Entity<ID> =
    select().where(predicate).resultList

/**
 * Retrieves entities of type [T] matching the specified predicate.
 *
 * @return a list of matching entities.
 */
fun <T, ID> EntityRepository<T, ID>.findAll(predicateBuilder: KPredicateBuilder<T, *, *>): List<T>
        where T : Record, T : Entity<ID> =
    select().where { bridge(predicateBuilder) }.resultList

/**
 * Retrieves entities of type [T] matching the specified predicate.
 *
 * @return a list of matching entities.
 */
fun <T, ID> EntityRepository<T, ID>.findAllRef(
    predicate: WhereBuilder<T, Ref<T>, ID>.() -> PredicateBuilder<T, *, *>
): List<Ref<T>> where T : Record, T : Entity<ID> =
    selectRef().where(predicate).resultList

/**
 * Retrieves entities of type [T] matching the specified predicate.
 *
 * @return a list of matching entities.
 */
fun <T, ID> EntityRepository<T, ID>.findAllRef(predicateBuilder: KPredicateBuilder<T, *, *>): List<Ref<T>>
        where T : Record, T : Entity<ID> =
    selectRef().where { bridge(predicateBuilder) }.resultList

/**
 * Retrieves an optional entity of type [T] matching the specified predicate.
 * Returns null if no matching entity is found.
 *
 * @return an optional entity, or null if none found.
 */
fun <T, ID> EntityRepository<T, ID>.find(
    predicate: WhereBuilder<T, T, ID>.() -> PredicateBuilder<T, *, *>
): T? where T : Record, T : Entity<ID> =
    select().where(predicate).optionalResult.getOrNull()

/**
 * Retrieves an optional entity of type [T] matching the specified predicate.
 * Returns null if no matching entity is found.
 *
 * @return an optional entity, or null if none found.
 */
fun <T, ID> EntityRepository<T, ID>.find(predicateBuilder: KPredicateBuilder<T, *, *>): T?
        where T : Record, T : Entity<ID> =
    select().where { bridge(predicateBuilder) }.optionalResult.getOrNull()

/**
 * Retrieves an optional entity of type [T] matching the specified predicate.
 * Returns a ref with a null value if no matching entity is found.
 *
 * @return an optional entity, or null if none found.
 */
fun <T, ID> EntityRepository<T, ID>.findRef(
    predicate: WhereBuilder<T, Ref<T>, ID>.() -> PredicateBuilder<T, *, *>
): Ref<T> where T : Record, T : Entity<ID> =
    selectRef().where(predicate).optionalResult.orElse(Ref.ofNull())

/**
 * Retrieves an optional entity of type [T] matching the specified predicate.
 * Returns a ref with a null value if no matching entity is found.
 *
 * @return an optional entity, or null if none found.
 */
fun <T, ID> EntityRepository<T, ID>.findRef(predicateBuilder: KPredicateBuilder<T, *, *>): Ref<T>
        where T : Record, T : Entity<ID> =
    selectRef().where { bridge(predicateBuilder) }.optionalResult.orElse(Ref.ofNull())

/**
 * Retrieves a single entity of type [T] matching the specified predicate.
 * Throws an exception if no entity or more than one entity is found.
 *
 * @return the matching entity.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID> EntityRepository<T, ID>.get(
    predicate: WhereBuilder<T, T, ID>.() -> PredicateBuilder<T, *, *>
): T where T : Record, T : Entity<ID> =
    select().where(predicate).singleResult

/**
 * Retrieves a single entity of type [T] matching the specified predicate.
 * Throws an exception if no entity or more than one entity is found.
 *
 * @return the matching entity.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID> EntityRepository<T, ID>.get(predicateBuilder: KPredicateBuilder<T, *, *>): T
        where T : Record, T : Entity<ID> =
    select().where { bridge(predicateBuilder) }.singleResult

/**
 * Retrieves a single entity of type [T] matching the specified predicate.
 * Throws an exception if no entity or more than one entity is found.
 *
 * @return the matching entity.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID> EntityRepository<T, ID>.getRef(
    predicate: WhereBuilder<T, Ref<T>, ID>.() -> PredicateBuilder<T, *, *>
): Ref<T> where T : Record, T : Entity<ID> =
    selectRef().where(predicate).singleResult

/**
 * Retrieves a single entity of type [T] matching the specified predicate.
 * Throws an exception if no entity or more than one entity is found.
 *
 * @return the matching entity.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID> EntityRepository<T, ID>.getRef(predicateBuilder: KPredicateBuilder<T, *, *>): Ref<T>
        where T : Record, T : Entity<ID> =
    selectRef().where { bridge(predicateBuilder) }.singleResult

/**
 * Retrieves entities of type [T] matching the specified predicate.
 *
 * The resulting stream is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @return a stream of matching entities.
 */
fun <T, ID> EntityRepository<T, ID>.select(
    predicate: WhereBuilder<T, T, ID>.() -> PredicateBuilder<T, *, *>
): Stream<T> where T : Record, T : Entity<ID> =
    select().where(predicate).resultStream

/**
 * Retrieves entities of type [T] matching the specified predicate.
 *
 * The resulting stream is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @return a stream of matching entities.
 */
fun <T, ID> EntityRepository<T, ID>.select(predicateBuilder: KPredicateBuilder<T, *, *>): Stream<T>
        where T : Record, T : Entity<ID> =
    select().where { bridge(predicateBuilder) }.resultStream

/**
 * Retrieves entities of type [T] matching the specified predicate.
 *
 * The resulting stream is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @return a stream of matching entities.
 */
fun <T, ID> EntityRepository<T, ID>.selectRef(
    predicate: WhereBuilder<T, Ref<T>, ID>.() -> PredicateBuilder<T, *, *>
): Stream<Ref<T>> where T : Record, T : Entity<ID> =
    selectRef().where(predicate).resultStream

/**
 * Retrieves entities of type [T] matching the specified predicate.
 *
 * The resulting stream is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @return a stream of matching entities.
 */
fun <T, ID> EntityRepository<T, ID>.selectRef(predicateBuilder: KPredicateBuilder<T, *, *>): Stream<Ref<T>>
        where T : Record, T : Entity<ID> =
    selectRef().where { bridge(predicateBuilder) }.resultStream

/**
 * Counts entities of type [T] matching the specified field and value.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return the count of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.countBy(
    field: Metamodel<T, V>,
    value: V
): Long where T : Record, T : Entity<ID> =
    selectCount().where(field, EQUALS, value).singleResult

/**
 * Counts entities of type [T] matching the specified field and referenced value.
 *
 * @param field metamodel reference of the entity field.
 * @param value the referenced value to match against.
 * @return the count of matching entities.
 */
fun <T, ID, V> EntityRepository<T, ID>.countBy(
    field: Metamodel<T, V>,
    value: Ref<V>
): Long where T : Record, T : Entity<ID>, V : Record =
    selectCount().where(field, value).singleResult

/**
 * Counts entities of type [T] matching the specified predicate.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return the count of matching entities.
 */
fun <T, ID> EntityRepository<T, ID>.count(
    predicate: WhereBuilder<T, *, ID>.() -> PredicateBuilder<T, *, *>
): Long where T : Record, T : Entity<ID> =
    selectCount().where(predicate).singleResult

/**
 * Counts entities of type [T] matching the specified predicate.
 *
 * @param predicateBuilder Lambda to build the WHERE clause.
 * @return the count of matching entities.
 */
fun <T, ID> EntityRepository<T, ID>.count(predicateBuilder: KPredicateBuilder<T, *, *>): Long
        where T : Record, T : Entity<ID> =
    selectCount().where { bridge(predicateBuilder) }.singleResult

/**
 * Deletes entities of type [T] matching the specified field and value.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return the number of entities deleted.
 */
fun <T, ID, V> EntityRepository<T, ID>.deleteAllBy(
    field: Metamodel<T, V>,
    value: V
): Int where T : Record, T : Entity<ID> =
    delete().where(field, EQUALS, value).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified field and referenced value.
 *
 * @param field metamodel reference of the entity field.
 * @param value the referenced value to match against.
 * @return the number of entities deleted.
 */
fun <T, ID, V> EntityRepository<T, ID>.deleteAllBy(
    field: Metamodel<T, V>,
    value: Ref<V>
): Int where T : Record, T : Entity<ID>, V : Record =
    delete().where(field, value).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified field against multiple values.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return the number of entities deleted.
 */
fun <T, ID, V> EntityRepository<T, ID>.deleteAllBy(
    field: Metamodel<T, V>,
    values: Iterable<V>
): Int where T : Record, T : Entity<ID> =
    delete().where(field, IN, values).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified field against multiple referenced values.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of referenced values to match against.
 * @return the number of entities deleted.
 */
fun <T, ID, V> EntityRepository<T, ID>.deleteAllByRef(
    field: Metamodel<T, V>,
    values: Iterable<Ref<V>>
): Int where T : Record, T : Entity<ID>, V : Record =
    delete().whereRef(field, values).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified predicate.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return the number of entities deleted.
 */
fun <T, ID> EntityRepository<T, ID>.delete(
    predicate: WhereBuilder<T, *, ID>.() -> PredicateBuilder<T, *, *>
): Int where T : Record, T : Entity<ID> =
    delete().where(predicate).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified predicate.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return the number of entities deleted.
 */
fun <T, ID> EntityRepository<T, ID>.delete(predicateBuilder: KPredicateBuilder<T, *, *>): Int
        where T : Record, T : Entity<ID> =
    delete().where { bridge(predicateBuilder) }.executeUpdate()

