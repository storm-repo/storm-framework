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
package st.orm.kotlin.repository

import st.orm.Ref
import st.orm.kotlin.CloseableSequence
import st.orm.kotlin.template.KQueryBuilder.KPredicateBuilder
import st.orm.kotlin.template.KQueryBuilder.KWhereBuilder
import st.orm.repository.Entity
import st.orm.template.Metamodel
import st.orm.template.Operator.EQUALS
import st.orm.template.Operator.IN
import kotlin.jvm.optionals.getOrNull

/**
 * Retrieves all entities of type [T] from the repository.
 *
 * @return a list containing all entities.
 */
fun <T, ID, V> KEntityRepository<T, ID>.findAllRef(): List<Ref<T>>
        where T : Record, T : Entity<ID> =
    selectRef().resultList

/**
 * Retrieves all entities of type [T] from the repository.
 *
 * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @return a sequence containing all entities.
 */
fun <T, ID, V> KEntityRepository<T, ID>.selectAllRef(): CloseableSequence<Ref<T>>
        where T : Record, T : Entity<ID> =
    selectRef().resultSequence

/**
 * Retrieves an optional entity of type [T] based on a single field and its value.
 * Returns null if no matching entity is found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return an optional entity, or null if none found.
 */
fun <T, ID, V> KEntityRepository<T, ID>.findBy(field: Metamodel<T, V>, value: V): T?
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
fun <T, ID, V> KEntityRepository<T, ID>.findBy(field: Metamodel<T, V>, value: Ref<V>): T?
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
fun <T, ID, V> KEntityRepository<T, ID>.findAllBy(field: Metamodel<T, V>, value: V): List<T>
        where T : Record, T : Entity<ID> =
    select().where(field, EQUALS, value).resultList

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty sequence if no entities are found.
 *
 * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 * 
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return a sequence of matching entities.
 */
fun <T, ID, V> KEntityRepository<T, ID>.selectBy(field: Metamodel<T, V>, value: V): CloseableSequence<T>
        where T : Record, T : Entity<ID> =
    select().where(field, EQUALS, value).resultSequence

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return a list of matching entities.
 */
fun <T, ID, V> KEntityRepository<T, ID>.findAllBy(field: Metamodel<T, V>, value: Ref<V>): List<T>
        where T : Record, T : Entity<ID>, V : Record =
    select().where(field, value).resultList

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty sequence if no entities are found.
 *
 * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return a sequence of matching entities.
 */
fun <T, ID, V> KEntityRepository<T, ID>.selectBy(field: Metamodel<T, V>, value: Ref<V>): CloseableSequence<T>
        where T : Record, T : Entity<ID>, V : Record =
    select().where(field, value).resultSequence

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return list of matching entities.
 */
fun <T, ID, V> KEntityRepository<T, ID>.findAllBy(field: Metamodel<T, V>, values: Iterable<V>): List<T>
        where T : Record, T : Entity<ID> =
    select().where(field, IN, values).resultList

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty sequence if no entities are found.
 * 
 * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return at sequence of matching entities.
 */
fun <T, ID, V> KEntityRepository<T, ID>.selectBy(field: Metamodel<T, V>, values: Iterable<V>): CloseableSequence<T>
        where T : Record, T : Entity<ID> =
    select().where(field, IN, values).resultSequence

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return a list of matching entities.
 */
fun <T, ID, V> KEntityRepository<T, ID>.findAllByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): List<T>
        where T : Record, T : Entity<ID>, V : Record =
    select().whereRef(field, values).resultList

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty sequence if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return a sequence of matching entities.
 */
fun <T, ID, V> KEntityRepository<T, ID>.selectByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): CloseableSequence<T>
        where T : Record, T : Entity<ID>, V : Record =
    select().whereRef(field, values).resultSequence

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
fun <T, ID, V> KEntityRepository<T, ID>.getBy(field: Metamodel<T, V>, value: V): T
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
fun <T, ID, V> KEntityRepository<T, ID>.getBy(field: Metamodel<T, V>, value: Ref<V>): T
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
fun <T, ID, V> KEntityRepository<T, ID>.findRefBy(field: Metamodel<T, V>, value: V): Ref<T>
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
fun <T, ID, V> KEntityRepository<T, ID>.findRefBy(field: Metamodel<T, V>, value: Ref<V>): Ref<T>
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
fun <T, ID, V> KEntityRepository<T, ID>.findAllRefBy(field: Metamodel<T, V>, value: V): List<Ref<T>>
        where T : Record, T : Entity<ID> =
    selectRef().where(field, EQUALS, value).resultList

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty sequence if no entities are found.
 *
 * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 * 
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return a sequence of matching entities.
 */
fun <T, ID, V> KEntityRepository<T, ID>.selectRefBy(field: Metamodel<T, V>, value: V): CloseableSequence<Ref<T>>
        where T : Record, T : Entity<ID> =
    selectRef().where(field, EQUALS, value).resultSequence

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return a list of matching entities.
 */
fun <T, ID, V> KEntityRepository<T, ID>.findAllRefBy(field: Metamodel<T, V>, value: Ref<V>): List<Ref<T>>
        where T : Record, T : Entity<ID>, V : Record =
    selectRef().where(field, value).resultList

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty sequence if no entities are found.
 *
 * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 * 
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return a sequence of matching entities.
 */
fun <T, ID, V> KEntityRepository<T, ID>.selectRefBy(field: Metamodel<T, V>, value: Ref<V>): CloseableSequence<Ref<T>>
        where T : Record, T : Entity<ID>, V : Record =
    selectRef().where(field, value).resultSequence

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return a list of matching entities.
 */
fun <T, ID, V> KEntityRepository<T, ID>.findAllRefBy(field: Metamodel<T, V>, values: Iterable<V>): List<Ref<T>>
        where T : Record, T : Entity<ID> =
    selectRef().where(field, IN, values).resultList

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty sequence if no entities are found.
 *
 * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 * 
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return a sequence of matching entities.
 */
fun <T, ID, V> KEntityRepository<T, ID>.selectRefBy(field: Metamodel<T, V>, values: Iterable<V>): CloseableSequence<Ref<T>>
        where T : Record, T : Entity<ID> =
    selectRef().where(field, IN, values).resultSequence

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return a list of matching entities.
 */
fun <T, ID, V> KEntityRepository<T, ID>.findAllRefByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): List<Ref<T>>
        where T : Record, T : Entity<ID>, V : Record =
    selectRef().whereRef(field, values).resultList


/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty sequence if no entities are found.
 *
 * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 * 
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return a sequence of matching entities.
 */
fun <T, ID, V> KEntityRepository<T, ID>.selectRefByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): CloseableSequence<Ref<T>>
        where T : Record, T : Entity<ID>, V : Record =
    selectRef().whereRef(field, values).resultSequence

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
fun <T, ID, V> KEntityRepository<T, ID>.getRefBy(field: Metamodel<T, V>, value: V): Ref<T>
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
fun <T, ID, V> KEntityRepository<T, ID>.getRefBy(field: Metamodel<T, V>, value: Ref<V>): Ref<T>
        where T : Record, T : Entity<ID>, V : Record =
    selectRef().where(field, value).singleResult

/**
 * Retrieves entities of type [T] matching the specified predicate.
 *
 * @return a list of matching entities.
 */
fun <T, ID> KEntityRepository<T, ID>.findAll(
    predicate: KWhereBuilder<T, T, ID>.() -> KPredicateBuilder<T, *, *>
): List<T> where T : Record, T : Entity<ID> =
    select().where(predicate).resultList

/**
 * Retrieves entities of type [T] matching the specified predicate.
 *
 * @return a list of matching entities.
 */
fun <T, ID> KEntityRepository<T, ID>.findAll(predicateBuilder: KPredicateBuilder<T, *, *>): List<T>
        where T : Record, T : Entity<ID> =
    select().where { predicateBuilder }.resultList

/**
 * Retrieves entities of type [T] matching the specified predicate.
 *
 * @return a list of matching entities.
 */
fun <T, ID> KEntityRepository<T, ID>.findAllRef(
    predicate: KWhereBuilder<T, Ref<T>, ID>.() -> KPredicateBuilder<T, *, *>
): List<Ref<T>> where T : Record, T : Entity<ID> =
    selectRef().where(predicate).resultList

/**
 * Retrieves entities of type [T] matching the specified predicate.
 *
 * @return a list of matching entities.
 */
fun <T, ID> KEntityRepository<T, ID>.findAllRef(predicateBuilder: KPredicateBuilder<T, *, *>): List<Ref<T>>
        where T : Record, T : Entity<ID> =
    selectRef().where { predicateBuilder }.resultList

/**
 * Retrieves an optional entity of type [T] matching the specified predicate.
 * Returns null if no matching entity is found.
 *
 * @return an optional entity, or null if none found.
 */
fun <T, ID> KEntityRepository<T, ID>.find(
    predicate: KWhereBuilder<T, T, ID>.() -> KPredicateBuilder<T, *, *>
): T? where T : Record, T : Entity<ID> =
    select().where(predicate).optionalResult.getOrNull()

/**
 * Retrieves an optional entity of type [T] matching the specified predicate.
 * Returns null if no matching entity is found.
 *
 * @return an optional entity, or null if none found.
 */
fun <T, ID> KEntityRepository<T, ID>.find(predicateBuilder: KPredicateBuilder<T, *, *>): T?
        where T : Record, T : Entity<ID> =
    select().where { predicateBuilder }.optionalResult.getOrNull()

/**
 * Retrieves an optional entity of type [T] matching the specified predicate.
 * Returns null if no matching entity is found.
 *
 * @return an optional entity, or null if none found.
 */
fun <T, ID> KEntityRepository<T, ID>.findRef(
    predicate: KWhereBuilder<T, Ref<T>, ID>.() -> KPredicateBuilder<T, *, *>
): Ref<T> where T : Record, T : Entity<ID> =
    selectRef().where(predicate).optionalResult.orElse(Ref.ofNull())

/**
 * Retrieves an optional entity of type [T] matching the specified predicate.
 * Returns a ref with a null value if no matching entity is found.
 *
 * @return an optional entity, or null if none found.
 */
fun <T, ID> KEntityRepository<T, ID>.findRef(predicateBuilder: KPredicateBuilder<T, *, *>): Ref<T>
        where T : Record, T : Entity<ID> =
    selectRef().where { predicateBuilder }.optionalResult.orElse(Ref.ofNull())

/**
 * Retrieves a single entity of type [T] matching the specified predicate.
 * Throws an exception if no entity or more than one entity is found.
 *
 * @return the matching entity.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID> KEntityRepository<T, ID>.get(
    predicate: KWhereBuilder<T, T, ID>.() -> KPredicateBuilder<T, *, *>
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
fun <T, ID> KEntityRepository<T, ID>.get(predicateBuilder: KPredicateBuilder<T, *, *>): T
        where T : Record, T : Entity<ID> =
    select().where { predicateBuilder }.singleResult

/**
 * Retrieves a single entity of type [T] matching the specified predicate.
 * Throws an exception if no entity or more than one entity is found.
 *
 * @return the matching entity.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
fun <T, ID> KEntityRepository<T, ID>.getRef(
    predicate: KWhereBuilder<T, Ref<T>, ID>.() -> KPredicateBuilder<T, *, *>
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
fun <T, ID> KEntityRepository<T, ID>.getRef(predicateBuilder: KPredicateBuilder<T, *, *>): Ref<T>
        where T : Record, T : Entity<ID> =
    selectRef().where { predicateBuilder }.singleResult

/**
 * Retrieves entities of type [T] matching the specified predicate.
 *
 * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 * 
 * @return a sequence of matching entities.
 */
fun <T, ID> KEntityRepository<T, ID>.select(
    predicate: KWhereBuilder<T, T, ID>.() -> KPredicateBuilder<T, *, *>
): CloseableSequence<T> where T : Record, T : Entity<ID> =
    select().where(predicate).resultSequence

/**
 * Retrieves entities of type [T] matching the specified predicate.
 *
 * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @return a sequence of matching entities.
 */
fun <T, ID> KEntityRepository<T, ID>.select(predicateBuilder: KPredicateBuilder<T, *, *>): CloseableSequence<T>
        where T : Record, T : Entity<ID> =
    select().where { predicateBuilder }.resultSequence

/**
 * Retrieves entities of type [T] matching the specified predicate.
 *
 * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 * 
 * @return a sequence of matching entities.
 */
fun <T, ID> KEntityRepository<T, ID>.selectRef(
    predicate: KWhereBuilder<T, Ref<T>, ID>.() -> KPredicateBuilder<T, *, *>
): CloseableSequence<Ref<T>> where T : Record, T : Entity<ID> =
    selectRef().where(predicate).resultSequence

/**
 * Retrieves entities of type [T] matching the specified predicate.
 *
 * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
 * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
 * dealing with large volumes of entities.
 *
 * Note that calling this method does trigger the execution of the underlying
 * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
 * while in use, it must be closed after usage to prevent resource leaks.
 *
 * @return a sequence of matching entities.
 */
fun <T, ID> KEntityRepository<T, ID>.selectRef(predicateBuilder: KPredicateBuilder<T, *, *>): CloseableSequence<Ref<T>>
        where T : Record, T : Entity<ID> =
    selectRef().where { predicateBuilder }.resultSequence

/**
 * Counts entities of type [T] matching the specified field and value.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return the count of matching entities.
 */
fun <T, ID, V> KEntityRepository<T, ID>.countBy(
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
fun <T, ID, V> KEntityRepository<T, ID>.countBy(
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
fun <T, ID> KEntityRepository<T, ID>.count(
    predicate: KWhereBuilder<T, *, ID>.() -> KPredicateBuilder<T, *, *>
): Long where T : Record, T : Entity<ID> =
    selectCount().where(predicate).singleResult

/**
 * Counts entities of type [T] matching the specified predicate.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return the count of matching entities.
 */
fun <T, ID> KEntityRepository<T, ID>.count(predicateBuilder: KPredicateBuilder<T, *, *>): Long
        where T : Record, T : Entity<ID> =
    selectCount().where { predicateBuilder }.singleResult

/**
 * Deletes entities of type [T] matching the specified field and value.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return the number of entities deleted.
 */
fun <T, ID, V> KEntityRepository<T, ID>.deleteAllBy(
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
fun <T, ID, V> KEntityRepository<T, ID>.deleteAllBy(
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
fun <T, ID, V> KEntityRepository<T, ID>.deleteAllBy(
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
fun <T, ID, V> KEntityRepository<T, ID>.deleteAllByRef(
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
fun <T, ID> KEntityRepository<T, ID>.delete(
    predicate: KWhereBuilder<T, *, ID>.() -> KPredicateBuilder<T, *, *>
): Int where T : Record, T : Entity<ID> =
    delete().where(predicate).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified predicate.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return the number of entities deleted.
 */
fun <T, ID> KEntityRepository<T, ID>.delete(predicateBuilder: KPredicateBuilder<T, *, *>): Int
        where T : Record, T : Entity<ID> =
    delete().where { predicateBuilder }.executeUpdate()

