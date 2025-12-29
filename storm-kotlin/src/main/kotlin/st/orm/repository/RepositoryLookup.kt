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
package st.orm.repository

import kotlinx.coroutines.flow.Flow
import st.orm.Data
import st.orm.Entity
import st.orm.Metamodel
import st.orm.Operator.EQUALS
import st.orm.Operator.IN
import st.orm.Projection
import st.orm.Ref
import st.orm.template.PredicateBuilder
import st.orm.template.QueryBuilder
import st.orm.template.WhereBuilder
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * Provides access to repositories.
 *
 * Entity repositories returned by `entity` provide basic CRUD operations for database tables. Projection
 * repositories returned by `projection` provide read operations for database views and projection queries. The
 * repositories returned by the `repository` method
 * allow to implement specialized repository logic by implementing default methods. The default methods have full access
 * to the CRUD and `QueryBuilder` logic of the repository it extends:
 *
 * ```
 * interface UserRepository : EntityRepository<User, Integer> {
 *   fun findByName(String name): User? =
 *     find { User_.name eq name }  // Type-safe metamodel.
 *
 *   fun findByCity(City city): List<User> =
 *     findAll { User_.city eq city }    // Type-safe metamodel.
 * }
 * ```
 *
 * @see EntityRepository
 * @see ProjectionRepository
 */
interface RepositoryLookup {
    /**
     * Returns the repository for the given entity type.
     *
     * @param type the entity type.
     * @param <T> the entity type.
     * @param <ID> the type of the entity's primary key.
     * @return the repository for the given entity type.
     */
    fun <T, ID : Any> entity(type: KClass<T>): EntityRepository<T, ID> where T : Entity<ID>

    /**
     * Returns the repository for the given projection type.
     *
     * @param type the projection type.
     * @param <T> the projection type.
     * @param <ID> the type of the projection's primary key, or Void if the projection specifies no primary key.
     * @return the repository for the given projection type.
     */
    fun <T, ID: Any> projection(type: KClass<T>): ProjectionRepository<T, ID> where T : Projection<ID>

    /**
     * Returns a proxy for the repository of the given type.
     *
     * @param type the repository type.
     * @param <R> the repository type.
     * @return a proxy for the repository of the given type.
     */
    fun <R : Repository> repository(type: KClass<R>): R
}

// Kotlin specific DSL

/**
 * Extensions for [RepositoryLookup] to provide convenient access to entity repositories.
 */
inline fun <reified T : Entity<ID>, ID : Any> RepositoryLookup.entityWithId(): EntityRepository<T, ID> =
    entity(T::class)

/**
 * Extensions for [RepositoryLookup] to provide convenient access to entity repositories.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Entity<*>> RepositoryLookup.entity(): EntityRepository<T, *> =
    entity(T::class as KClass<Entity<Any>>) as EntityRepository<T, *>

/**
 * Extensions for [RepositoryLookup] to provide convenient access to projection repositories.
 */
inline fun <reified T : Projection<ID>, ID : Any> RepositoryLookup.projectionWithId(): ProjectionRepository<T, ID> =
    projection(T::class)

/**
 * Extensions for [RepositoryLookup] to provide convenient access to projection repositories.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Projection<*>> RepositoryLookup.projection(): ProjectionRepository<T, *> =
    projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<T, *>

/**
 * Extensions for [RepositoryLookup] to provide convenient access to repositories.
 */
inline fun <reified R : Repository> RepositoryLookup.repository(): R =
    repository(R::class)

/**
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return list containing all records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.findAll(): List<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).findAll() as List<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).findAll() as List<T>

/**
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return stream containing all records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.selectAll(): Flow<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectAll() as Flow<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectAll() as Flow<T>

/**
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return list containing all records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.findAllRef(): List<Ref<T>> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().resultList as List<Ref<T>>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().resultList as List<Ref<T>>

/**
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return stream containing all records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.selectAllRef(): Flow<Ref<T>> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().resultFlow as Flow<Ref<T>>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().resultFlow as Flow<Ref<T>>

/**
 * Retrieves an optional record of type [T] based on a single field and its value.
 * Returns null if no matching record is found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field metamodel reference of the record field.
 * @param value the value to match against.
 * @return an optional record, or null if none found.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V> RepositoryLookup.findBy(field: Metamodel<T, V>, value: V): T? =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().where(field as Metamodel<Entity<*>, V>, EQUALS, value).optionalResult as T?
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().where(field as Metamodel<Projection<*>, V>, EQUALS, value).optionalResult as T?

/**
 * Retrieves an optional record of type [T] based on a single field and its value.
 * Returns null if no matching record is found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field metamodel reference of the record field.
 * @param value the value to match against.
 * @return an optional record, or null if none found.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V : Data> RepositoryLookup.findBy(field: Metamodel<T, V>, value: Ref<V>): T? =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().where(field as Metamodel<Entity<*>, V>, value).optionalResult as T?
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().where(field as Metamodel<Projection<*>, V>, value).optionalResult as T?

/**
 * Retrieves records of type [T] matching a single field and a single value.
 * Returns an empty list if no records are found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field metamodel reference of the record field.
 * @param value the value to match against.
 * @return list of matching records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V> RepositoryLookup.findAllBy(field: Metamodel<T, V>, value: V): List<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().where(field as Metamodel<Entity<*>, V>, EQUALS, value).resultList as List<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().where(field as Metamodel<Projection<*>, V>, EQUALS, value).resultList as List<T>

/**
 * Retrieves records of type [T] matching a single field and a single value.
 * Returns an empty stream if no records are found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field metamodel reference of the record field.
 * @param value the value to match against.
 * @return stream of matching records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V> RepositoryLookup.selectBy(field: Metamodel<T, V>, value: V): Flow<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().where(field as Metamodel<Entity<*>, V>, EQUALS, value).resultFlow as Flow<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().where(field as Metamodel<Projection<*>, V>, EQUALS, value).resultFlow as Flow<T>

/**
 * Retrieves records of type [T] matching a single field and a single value.
 * Returns an empty list if no records are found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field metamodel reference of the record field.
 * @param value the value to match against.
 * @return list of matching records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V : Data> RepositoryLookup.findAllBy(field: Metamodel<T, V>, value: Ref<V>): List<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().where(field as Metamodel<Entity<*>, V>, value).resultList as List<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().where(field as Metamodel<Projection<*>, V>, value).resultList as List<T>

/**
 * Retrieves records of type [T] matching a single field and a single value.
 * Returns an empty stream if no records are found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field metamodel reference of the record field.
 * @param value the value to match against.
 * @return stream of matching records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V : Data> RepositoryLookup.selectBy(field: Metamodel<T, V>, value: Ref<V>): Flow<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().where(field as Metamodel<Entity<*>, V>, value).resultFlow as Flow<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().where(field as Metamodel<Projection<*>, V>, value).resultFlow as Flow<T>

/**
 * Retrieves records of type [T] matching a single field against multiple values.
 * Returns an empty list if no records are found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field metamodel reference of the record field.
 * @param values Iterable of values to match against.
 * @return list of matching records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V> RepositoryLookup.findAllBy(field: Metamodel<T, V>, values: Iterable<V>): List<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().where(field as Metamodel<Entity<*>, V>, IN, values).resultList as List<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().where(field as Metamodel<Projection<*>, V>, IN, values).resultList as List<T>

/**
 * Retrieves records of type [T] matching a single field against multiple values.
 * Returns an empty stream if no records are found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field metamodel reference of the record field.
 * @param values Iterable of values to match against.
 * @return stream of matching records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V> RepositoryLookup.selectBy(field: Metamodel<T, V>, values: Iterable<V>): Flow<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().where(field as Metamodel<Entity<*>, V>, IN, values).resultFlow as Flow<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().where(field as Metamodel<Projection<*>, V>, IN, values).resultFlow as Flow<T>

/**
 * Retrieves records of type [T] matching a single field against multiple values.
 * Returns an empty list if no records are found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field metamodel reference of the record field.
 * @param values Iterable of values to match against.
 * @return list of matching records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V : Data> RepositoryLookup.findAllByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): List<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().whereRef(field as Metamodel<Entity<*>, V>, values).resultList as List<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().whereRef(field as Metamodel<Projection<*>, V>, values).resultList as List<T>

/**
 * Retrieves records of type [T] matching a single field against multiple values.
 * Returns an empty stream if no records are found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field metamodel reference of the record field.
 * @param values Iterable of values to match against.
 * @return stream of matching records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V : Data> RepositoryLookup.selectByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): Flow<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().whereRef(field as Metamodel<Entity<*>, V>, values).resultFlow as Flow<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().whereRef(field as Metamodel<Projection<*>, V>, values).resultFlow as Flow<T>

/**
 * Retrieves exactly one record of type [T] based on a single field and its value.
 * Throws an exception if no record or more than one record is found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field metamodel reference of the record field.
 * @param value the value to match against.
 * @return The matching record.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V> RepositoryLookup.getBy(field: Metamodel<T, V>, value: V): T =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().where(field as Metamodel<Entity<*>, V>, EQUALS, value).singleResult as T
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().where(field as Metamodel<Projection<*>, V>, EQUALS, value).singleResult as T

/**
 * Retrieves exactly one record of type [T] based on a single field and its value.
 * Throws an exception if no record or more than one record is found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field metamodel reference of the record field.
 * @param value the value to match against.
 * @return The matching record.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V : Data> RepositoryLookup.getBy(field: Metamodel<T, V>, value: Ref<V>): T =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().where(field as Metamodel<Entity<*>, V>, value).singleResult as T
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().where(field as Metamodel<Projection<*>, V>, value).singleResult as T

/**
 * Retrieves an optional entity of type [T] based on a single field and its value.
 * Returns a ref with a null value if no matching entity is found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return an optional entity, or null if none found.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V> RepositoryLookup.findRefBy(field: Metamodel<T, V>, value: V): Ref<T>? =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().where(field as Metamodel<Entity<*>, V>, EQUALS, value).optionalResult as Ref<T>?
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().where(field as Metamodel<Projection<*>, V>, EQUALS, value).optionalResult as Ref<T>?

/**
 * Retrieves an optional entity of type [T] based on a single field and its value.
 * Returns a ref with a null value if no matching entity is found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return an optional entity, or null if none found.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V : Data> RepositoryLookup.findRefBy(field: Metamodel<T, V>, value: Ref<V>): Ref<T>? =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().where(field as Metamodel<Entity<*>, V>, value).optionalResult as Ref<T>?
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().where(field as Metamodel<Projection<*>, V>, value).optionalResult as Ref<T>?

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return list of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V> RepositoryLookup.findAllRefBy(field: Metamodel<T, V>, value: V): List<Ref<T>> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().where(field as Metamodel<Entity<*>, V>, EQUALS, value).resultList as List<Ref<T>>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().where(field as Metamodel<Projection<*>, V>, EQUALS, value).resultList as List<Ref<T>>

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty stream if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return stream of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V> RepositoryLookup.selectRefBy(field: Metamodel<T, V>, value: V): Flow<Ref<T>> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().where(field as Metamodel<Entity<*>, V>, EQUALS, value).resultFlow as Flow<Ref<T>>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().where(field as Metamodel<Projection<*>, V>, EQUALS, value).resultFlow as Flow<Ref<T>>

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return list of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V : Data> RepositoryLookup.findAllRefBy(field: Metamodel<T, V>, value: Ref<V>): List<Ref<T>> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().where(field as Metamodel<Entity<*>, V>, value).resultList as List<Ref<T>>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().where(field as Metamodel<Projection<*>, V>, value).resultList as List<Ref<T>>

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty stream if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return stream of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V : Data> RepositoryLookup.selectRefBy(field: Metamodel<T, V>, value: Ref<V>): Flow<Ref<T>> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().where(field as Metamodel<Entity<*>, V>, value).resultFlow as Flow<Ref<T>>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().where(field as Metamodel<Projection<*>, V>, value).resultFlow as Flow<Ref<T>>

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return list of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V> RepositoryLookup.findAllRefBy(field: Metamodel<T, V>, values: Iterable<V>): List<Ref<T>> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().where(field as Metamodel<Entity<*>, V>, IN, values).resultList as List<Ref<T>>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().where(field as Metamodel<Projection<*>, V>, IN, values).resultList as List<Ref<T>>

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty stream if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return stream of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V> RepositoryLookup.selectRefBy(field: Metamodel<T, V>, values: Iterable<V>): Flow<Ref<T>> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().where(field as Metamodel<Entity<*>, V>, IN, values).resultFlow as Flow<Ref<T>>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().where(field as Metamodel<Projection<*>, V>, IN, values).resultFlow as Flow<Ref<T>>

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return list of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V : Data> RepositoryLookup.findAllRefByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): List<Ref<T>> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().whereRef(field as Metamodel<Entity<*>, V>, values).resultList as List<Ref<T>>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().whereRef(field as Metamodel<Projection<*>, V>, values).resultList as List<Ref<T>>

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty stream if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return stream of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V : Data> RepositoryLookup.selectRefByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): Flow<Ref<T>> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().whereRef(field as Metamodel<Entity<*>, V>, values).resultFlow as Flow<Ref<T>>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().whereRef(field as Metamodel<Projection<*>, V>, values).resultFlow as Flow<Ref<T>>

/**
 * Retrieves exactly one entity of type [T] based on a single field and its value.
 * Throws an exception if no entity or more than one entity is found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return The matching entity.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V> RepositoryLookup.getRefBy(field: Metamodel<T, V>, value: V): Ref<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().where(field as Metamodel<Entity<*>, V>, EQUALS, value).singleResult as Ref<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().where(field as Metamodel<Projection<*>, V>, EQUALS, value).singleResult as Ref<T>

/**
 * Retrieves exactly one entity of type [T] based on a single field and its value.
 * Throws an exception if no entity or more than one entity is found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return The matching entity.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V : Data> RepositoryLookup.getRefBy(field: Metamodel<T, V>, value: Ref<V>): Ref<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().where(field as Metamodel<Entity<*>, V>, value).singleResult as Ref<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().where(field as Metamodel<Projection<*>, V>, value).singleResult as Ref<T>

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.findAll(
    noinline predicate: WhereBuilder<T, T, *>.() -> PredicateBuilder<T, *, *>
): List<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().whereBuilder(predicate as WhereBuilder<Entity<*>, Entity<*>, *>.() -> PredicateBuilder<Entity<*>, *, *>).resultList as List<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().whereBuilder(predicate as WhereBuilder<Projection<*>, Projection<*>, *>.() -> PredicateBuilder<Projection<*>, *, *>).resultList as List<T>

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.findAll(predicate: PredicateBuilder<T, T, *>): List<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().where(predicate as PredicateBuilder<Entity<*>, Entity<*>, *>).resultList as List<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().where(predicate as PredicateBuilder<Projection<*>, Projection<*>, *>).resultList as List<T>

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.findAllRef(
    noinline predicate: WhereBuilder<T, Ref<T>, *>.() -> PredicateBuilder<T, *, *>
): List<Ref<T>> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().whereBuilder(predicate as WhereBuilder<Entity<*>, Ref<Entity<*>>, *>.() -> PredicateBuilder<Entity<*>, *, *>).resultList as List<Ref<T>>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().whereBuilder(predicate as WhereBuilder<Projection<*>, Ref<Projection<*>>, *>.() -> PredicateBuilder<Projection<*>, *, *>).resultList as List<Ref<T>>

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.findAllRef(predicate: PredicateBuilder<T, T, *>): List<Ref<T>> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().where(predicate as PredicateBuilder<Entity<*>, Entity<*>, *>).resultList as List<Ref<T>>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().where(predicate as PredicateBuilder<Projection<*>, Projection<*>, *>).resultList as List<Ref<T>>

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.find(
    noinline predicate: WhereBuilder<T, T, *>.() -> PredicateBuilder<T, *, *>
): T? =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().whereBuilder(predicate as WhereBuilder<Entity<*>, Entity<*>, *>.() -> PredicateBuilder<Entity<*>, *, *>).optionalResult as T?
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().whereBuilder(predicate as WhereBuilder<Projection<*>, Projection<*>, *>.() -> PredicateBuilder<Projection<*>, *, *>).optionalResult as T?

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.find(predicate: PredicateBuilder<T, T, *>): T? =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().where(predicate as PredicateBuilder<Entity<*>, Entity<*>, *>).optionalResult as T?
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().where(predicate as PredicateBuilder<Projection<*>, Projection<*>, *>).optionalResult as T?

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.findRef(
    noinline predicate: WhereBuilder<T, Ref<T>, *>.() -> PredicateBuilder<T, *, *>
): Ref<T>? =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().whereBuilder(predicate as WhereBuilder<Entity<*>, Ref<Entity<*>>, *>.() -> PredicateBuilder<Entity<*>, *, *>).optionalResult as Ref<T>?
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().whereBuilder(predicate as WhereBuilder<Projection<*>, Ref<Projection<*>>, *>.() -> PredicateBuilder<Projection<*>, *, *>).optionalResult as Ref<T>?

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.findRef(predicate: PredicateBuilder<T, T, *>): Ref<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().where(predicate as PredicateBuilder<Entity<*>, Entity<*>, *>).singleResult as Ref<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().where(predicate as PredicateBuilder<Projection<*>, Projection<*>, *>).singleResult as Ref<T>

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.get(
    noinline predicate: WhereBuilder<T, T, *>.() -> PredicateBuilder<T, *, *>
): T =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().whereBuilder(predicate as WhereBuilder<Entity<*>, Entity<*>, *>.() -> PredicateBuilder<Entity<*>, *, *>).singleResult as T
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().whereBuilder(predicate as WhereBuilder<Projection<*>, Projection<*>, *>.() -> PredicateBuilder<Projection<*>, *, *>).singleResult as T

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.get(predicate: PredicateBuilder<T, T, *>): T =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().where(predicate as PredicateBuilder<Entity<*>, Entity<*>, *>).singleResult as T
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().where(predicate as PredicateBuilder<Projection<*>, Projection<*>, *>).singleResult as T

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.getRef(
    noinline predicate: WhereBuilder<T, Ref<T>, *>.() -> PredicateBuilder<T, *, *>
): Ref<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().whereBuilder(predicate as WhereBuilder<Entity<*>, Ref<Entity<*>>, *>.() -> PredicateBuilder<Entity<*>, *, *>).singleResult as Ref<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().whereBuilder(predicate as WhereBuilder<Projection<*>, Ref<Projection<*>>, *>.() -> PredicateBuilder<Projection<*>, *, *>).singleResult as Ref<T>

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.getRef(predicate: PredicateBuilder<T, Ref<T>, *>): Ref<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().where(predicate as PredicateBuilder<Entity<*>, Ref<Entity<*>>, *>).singleResult as Ref<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().where(predicate as PredicateBuilder<Projection<*>, Ref<Projection<*>>, *>).singleResult as Ref<T>

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.select(
    noinline predicate: WhereBuilder<T, T, *>.() -> PredicateBuilder<T, *, *>
): Flow<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().whereBuilder(predicate as WhereBuilder<Entity<*>, Entity<*>, *>.() -> PredicateBuilder<Entity<*>, *, *>).resultFlow as Flow<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().whereBuilder(predicate as WhereBuilder<Projection<*>, Projection<*>, *>.() -> PredicateBuilder<Projection<*>, *, *>).resultFlow as Flow<T>

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.select(predicate: PredicateBuilder<T, T, *>): Flow<T> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select().where(predicate as PredicateBuilder<Entity<*>, Entity<*>, *>).resultFlow as Flow<T>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select().where(predicate as PredicateBuilder<Projection<*>, Projection<*>, *>).resultFlow as Flow<T>

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.selectRef(
    noinline predicate: WhereBuilder<T, Ref<T>, *>.() -> PredicateBuilder<T, *, *>
): Flow<Ref<T>> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().whereBuilder(predicate as WhereBuilder<Entity<*>, Ref<Entity<*>>, *>.() -> PredicateBuilder<Entity<*>, *, *>).resultFlow as Flow<Ref<T>>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().whereBuilder(predicate as WhereBuilder<Projection<*>, Ref<Projection<*>>, *>.() -> PredicateBuilder<Projection<*>, *, *>).resultFlow as Flow<Ref<T>>

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.selectRef(predicate: PredicateBuilder<T, Ref<T>, *>): Flow<Ref<T>> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef().where(predicate as PredicateBuilder<Entity<*>, Ref<Entity<*>>, *>).resultFlow as Flow<Ref<T>>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef().where(predicate as PredicateBuilder<Projection<*>, Ref<Projection<*>>, *>).resultFlow as Flow<Ref<T>>

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.select(): QueryBuilder<T, T, *> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).select() as QueryBuilder<T, T, *>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).select() as QueryBuilder<T, T, *>

/**
 * Creates a query builder to select references of entity records of type [T].
 *
 * @return A [QueryBuilder] for selecting references of entity records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.selectRef(): QueryBuilder<T, Ref<T>, *> =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectRef() as QueryBuilder<T, Ref<T>, *>
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectRef() as QueryBuilder<T, Ref<T>, *>

/**
 * Counts entities of type [T] matching the specified field and value.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return the count of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V> RepositoryLookup.countBy(field: Metamodel<T, V>, value: V): Long =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectCount().where(field as Metamodel<Entity<*>, V>, EQUALS, value).singleResult
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectCount().where(field as Metamodel<Projection<*>, V>, EQUALS, value).singleResult

/**
 * Counts entities of type [T] matching the specified field and referenced value.
 *
 * @param field metamodel reference of the entity field.
 * @param value the referenced value to match against.
 * @return the count of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V : Data> RepositoryLookup.countBy(field: Metamodel<T, V>, value: Ref<V>): Long =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectCount().where(field as Metamodel<Entity<*>, V>, value).singleResult
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectCount().where(field as Metamodel<Projection<*>, V>, value).singleResult

/**
 * Counts entities of type [T] matching the specified predicate.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return the count of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.count(
    noinline predicate: WhereBuilder<T, *, *>.() -> PredicateBuilder<T, *, *>
): Long =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectCount().whereBuilder(predicate as WhereBuilder<Entity<*>, *, *>.() -> PredicateBuilder<Entity<*>, *, *>).singleResult
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectCount().whereBuilder(predicate as WhereBuilder<Projection<*>, *, *>.() -> PredicateBuilder<Projection<*>, *, *>).singleResult

/**
 * Counts entities of type [T] matching the specified predicate.
 *
 * @return the count of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.countAll(): Long =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).count()
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).count()

/**
 * Counts entities of type [T] matching the specified predicate.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return the count of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.count(predicate: PredicateBuilder<T, *, *>): Long =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectCount().where(predicate as PredicateBuilder<Entity<*>, *, *>).singleResult
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectCount().where(predicate as PredicateBuilder<Projection<*>, *, *>).singleResult

/**
 * Checks if entities of type [T] matching the specified field and value exists.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return true if any matching entities exist, false otherwise.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V> RepositoryLookup.existsBy(field: Metamodel<T, V>, value: V): Boolean =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectCount().where(field as Metamodel<Entity<*>, V>, EQUALS, value).singleResult > 0
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectCount().where(field as Metamodel<Projection<*>, V>, EQUALS, value).singleResult > 0

/**
 * Checks if entities of type [T] matching the specified field and referenced value exists.
 *
 * @param field metamodel reference of the entity field.
 * @param value the referenced value to match against.
 * @return true if any matching entities exist, false otherwise.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data, V : Data> RepositoryLookup.existsBy(field: Metamodel<T, V>, value: Ref<V>): Boolean =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectCount().where(field as Metamodel<Entity<*>, V>, value).singleResult > 0
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectCount().where(field as Metamodel<Projection<*>, V>, value).singleResult > 0

/**
 * Checks if entities of type [T] matching the specified predicate exists.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return true if any matching entities exist, false otherwise.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.exists(
    noinline predicate: WhereBuilder<T, *, *>.() -> PredicateBuilder<T, *, *>
): Boolean =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectCount().whereBuilder(predicate as WhereBuilder<Entity<*>, *, *>.() -> PredicateBuilder<Entity<*>, *, *>).singleResult > 0
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectCount().whereBuilder(predicate as WhereBuilder<Projection<*>, *, *>.() -> PredicateBuilder<Projection<*>, *, *>).singleResult > 0

/**
 * Checks if entities of type [T] matching the specified predicate exists.
 *
 * @return true if any matching entities exist, false otherwise.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.exists(): Boolean =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).exists()
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).exists()

/**
 * Checks if entities of type [T] matching the specified predicate exists.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return true if any matching entities exist, false otherwise.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Data> RepositoryLookup.exists(predicate: PredicateBuilder<T, *, *>): Boolean =
    if (T::class.isSubclassOf(Entity::class))
        (entity(T::class as KClass<Entity<Any>>) as EntityRepository<Entity<*>, *>).selectCount().where(predicate as PredicateBuilder<Entity<*>, *, *>).singleResult > 0
    else
        (projection(T::class as KClass<Projection<Any>>) as ProjectionRepository<Projection<*>, *>).selectCount().where(predicate as PredicateBuilder<Projection<*>, *, *>).singleResult > 0

/**
 * Inserts an entity of type [T] into the repository.
 *
 * @param entity The entity to insert.
 * @return The inserted entity after fetching from the database.
 */
inline infix fun <reified T : Entity<*>> RepositoryLookup.insert(entity: T): T =
    entity<T>().insertAndFetch(entity)

/**
 * Inserts multiple entities of type [T] into the repository.
 *
 * @param entities Iterable collection of entities to insert.
 * @return list of inserted entities after fetching from the database.
 */
inline infix fun <reified T : Entity<*>> RepositoryLookup.insert(entities: Iterable<T>): List<T> =
    entity<T>().insertAndFetch(entities)

/**
 * Inserts multiple entities of type [T] into the repository.
 *
 * @param entities Flow of entities to insert.
 * @return flow of inserted entities after fetching from the database.
 */
inline infix fun <reified T : Entity<*>> RepositoryLookup.insert(entities: Flow<T>): Flow<T> =
    entity<T>().insertAndFetch(entities)

/**
 * Upserts (inserts or updates) an entity of type [T] into the repository.
 *
 * @param entity The entity to upsert.
 * @return The upserted entity after fetching from the database.
 */
inline infix fun <reified T : Entity<*>> RepositoryLookup.upsert(entity: T): T =
    entity<T>().upsertAndFetch(entity)

/**
 * Upserts (inserts or updates) multiple entities of type [T] into the repository.
 *
 * @param entities Iterable collection of entities to upsert.
 * @return list of upserted entities after fetching from the database.
 */
inline infix fun <reified T : Entity<*>> RepositoryLookup.upsert(entities: Iterable<T>): List<T> =
    entity<T>().upsertAndFetch(entities)

/**
 * Upserts (inserts or updates) multiple entities of type [T] into the repository.
 *
 * @param entities Flow of entities to upsert.
 * @return flow of upserted entities after fetching from the database.
 */
inline infix fun <reified T : Entity<*>> RepositoryLookup.upsert(entities: Flow<T>): Flow<T> =
    entity<T>().upsertAndFetch(entities)

/**
 * Creates a query builder to delete records of type [T].
 *
 * @return A [QueryBuilder] for deleting records of type [T].
 */
inline fun <reified T> RepositoryLookup.delete(): QueryBuilder<T, *, *>
        where T : Data, T : Entity<*> =
    entity<T>().delete()

/**
 * Deletes an entity of type [T] from the repository.
 *
 * @param entity The entity to delete.
 */
inline infix fun <reified T : Entity<*>> RepositoryLookup.delete(entity: T) =
    entity<T>().delete(entity)

/**
 * Deletes multiple entities of type [T] from the repository.
 *
 * @param entity List of entities to delete.
 */
inline infix fun <reified T : Entity<*>> RepositoryLookup.delete(entity: Iterable<T>) =
    entity<T>().delete(entity)

/**
 * Deletes multiple entities of type [T] from the repository.
 *
 * @param entity Flow of entities to delete.
 */
inline infix suspend fun <reified T : Entity<*>> RepositoryLookup.delete(entity: Flow<T>) =
    entity<T>().delete(entity)

/**
 * Deletes an entity of type [T] from the repository.
 *
 * @param ref The entity to delete.
 */
inline infix fun <reified T : Entity<*>> RepositoryLookup.deleteByRef(ref: Ref<T>) =
    entity<T>().deleteByRef(ref)

/**
 * Deletes multiple entities of type [T] from the repository.
 *
 * @param refs List of entities to delete.
 */
inline infix fun <reified T : Entity<*>> RepositoryLookup.deleteByRef(refs: Iterable<Ref<T>>) =
    entity<T>().deleteByRef(refs)

/**
 * Deletes multiple entities of type [T] from the repository.
 *
 * @param refs Flow of entities to delete.
 */
inline infix suspend fun <reified T : Entity<*>> RepositoryLookup.deleteByRef(refs: Flow<Ref<T>>) =
    entity<T>().deleteByRef(refs)

/**
 * Updates an entity of type [T] in the repository.
 *
 * @param entity The entity to update.
 * @return The updated entity after fetching from the database.
 */
inline infix fun <reified T : Entity<*>> RepositoryLookup.update(entity: T): T =
    entity<T>().updateAndFetch(entity)

/**
 * Updates multiple entities of type [T] in the repository.
 *
 * @param entities Iterable collection of entities to update.
 * @return list of updated entities after fetching from the database.
 */
inline infix fun <reified T : Entity<*>> RepositoryLookup.update(entities: Iterable<T>): List<T> =
    entity<T>().updateAndFetch(entities)

/**
 * Updates multiple entities of type [T] in the repository.
 *
 * @param entities Flow of entities to update.
 * @return flow of updated entities after fetching from the database.
 */
inline infix fun <reified T : Entity<*>> RepositoryLookup.update(entities: Flow<T>): Flow<T> =
    entity<T>().updateAndFetch(entities)

/**
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return list containing all records.
 */
inline fun <reified T : Entity<*>> RepositoryLookup.deleteAll() =
    entity<T>().deleteAll()

/**
 * Deletes entities of type [T] matching the specified ID field and its value.
 *
 * @param field metamodel reference of the ID field.
 * @param value the ID value to match against.
 * @return the number of entities deleted (0 or 1).
 */
inline fun <reified T : Entity<ID>, ID> RepositoryLookup.deleteBy(field: Metamodel<T, ID>, value: ID): Int =
    entity<T>().delete().where(field, EQUALS, value).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified ID field and its value.
 *
 * @param field metamodel reference of the ID field.
 * @param value the ID value to match against.
 * @return the number of entities deleted (0 or 1).
 */
inline fun <reified T : Entity<*>> RepositoryLookup.deleteBy(field: Metamodel<T, T>, value: Ref<T>): Int =
    entity<T>().delete().where(field, value).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified field and value.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return the number of entities deleted.
 */
inline fun <reified T : Entity<*>, V> RepositoryLookup.deleteAllBy(field: Metamodel<T, V>, value: V): Int =
    entity<T>().delete().where(field, EQUALS, value).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified field and referenced value.
 *
 * @param field metamodel reference of the entity field.
 * @param value the referenced value to match against.
 * @return the number of entities deleted.
 */
inline fun <reified T : Entity<*>, V : Data> RepositoryLookup.deleteAllBy(field: Metamodel<T, V>, value: Ref<V>): Int =
    entity<T>().delete().where(field, value).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified field against multiple values.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return the number of entities deleted.
 */
inline fun <reified T : Entity<*>, V> RepositoryLookup.deleteAllBy(field: Metamodel<T, V>, values: Iterable<V>): Int =
    entity<T>().delete().where(field, IN, values).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified field against multiple referenced values.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of referenced values to match against.
 * @return the number of entities deleted.
 */
inline fun <reified T, V> RepositoryLookup.deleteAllByRef(
    field: Metamodel<T, V>,
    values: Iterable<Ref<V>>
): Int where T : Entity<*>, V : Data =
    entity<T>().delete().whereRef(field, values).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified predicate.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return the number of entities deleted.
 */
inline fun <reified T> RepositoryLookup.delete(
    noinline predicate: WhereBuilder<T, *, *>.() -> PredicateBuilder<T, *, *>
): Int where T : Entity<*> =
    entity<T>().delete().whereBuilder(predicate).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified predicate.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return the number of entities deleted.
 */
inline fun <reified T> RepositoryLookup.delete(predicate: PredicateBuilder<T, *, *>): Int
        where T : Entity<*> =
    entity<T>().delete().whereBuilder { predicate }.executeUpdate()
