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
package st.orm.kt.repository

import kotlinx.coroutines.flow.Flow
import st.orm.Entity
import st.orm.Metamodel
import st.orm.Operator.EQUALS
import st.orm.Operator.IN
import st.orm.Projection
import st.orm.Ref
import st.orm.kt.template.PredicateBuilder
import st.orm.kt.template.QueryBuilder
import st.orm.kt.template.WhereBuilder
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberFunctions

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
    fun <T, ID : Any> entity(type: KClass<T>): EntityRepository<T, ID> where T : Record, T : Entity<ID>

    /**
     * Returns the repository for the given projection type.
     *
     * @param type the projection type.
     * @param <T> the projection type.
     * @param <ID> the type of the projection's primary key, or Void if the projection specifies no primary key.
     * @return the repository for the given projection type.
     */
    fun <T, ID: Any> projection(type: KClass<T>): ProjectionRepository<T, ID> where T : Record, T : Projection<ID>

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
inline fun <reified T, ID : Any> RepositoryLookup.entityWithId(): EntityRepository<T, ID>
        where T : Record, T : Entity<ID> =
    entity(T::class)

/**
 * Extensions for [RepositoryLookup] to provide convenient access to entity repositories.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.entity(): EntityRepository<T, *>
        where T : Record, T : Entity<*> =
    this::class
        .memberFunctions
        .first { it.name == "entity" }
        .call(this, T::class) as EntityRepository<T, *>

/**
 * Extensions for [RepositoryLookup] to provide convenient access to projection repositories.
 */
inline fun <reified T, ID : Any> RepositoryLookup.projectionWithId(): ProjectionRepository<T, ID>
        where T : Record, T : Projection<ID> =
    projection(T::class)

/**
 * Extensions for [RepositoryLookup] to provide convenient access to projection repositories.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.projection(): ProjectionRepository<T, *>
        where T : Record, T : Projection<*> =
    this::class
        .memberFunctions
        .first { it.name == "projection" }
        .call(this, T::class) as ProjectionRepository<T, *>

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
inline fun <reified T> RepositoryLookup.findAll(): List<T>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).findAll()
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).findAll()
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return stream containing all records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.selectAll(): Flow<T>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectAll()
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectAll()
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return list containing all records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.findAllRef(): List<Ref<T>>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().resultList
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().resultList
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return stream containing all records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.selectAllRef(): Flow<Ref<T>>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().resultFlow
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().resultFlow
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

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
inline fun <reified T, V> RepositoryLookup.findBy(field: Metamodel<T, V>, value: V): T?
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().where(field, EQUALS, value).optionalResult
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().where(field, EQUALS, value).optionalResult
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

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
inline fun <reified T, V> RepositoryLookup.findBy(field: Metamodel<T, V>, value: Ref<V>): T?
        where T : Record, V : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().where(field, value).optionalResult
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().where(field, value).optionalResult
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

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
inline fun <reified T, V> RepositoryLookup.findAllBy(field: Metamodel<T, V>, value: V): List<T>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().where(field, EQUALS, value).resultList
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().where(field, EQUALS, value).resultList
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

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
inline fun <reified T, V> RepositoryLookup.selectBy(field: Metamodel<T, V>, value: V): Flow<T>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().where(field, EQUALS, value).resultFlow
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().where(field, EQUALS, value).resultFlow
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

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
inline fun <reified T, V> RepositoryLookup.findAllBy(field: Metamodel<T, V>, value: Ref<V>): List<T>
        where T : Record, V : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().where(field, value).resultList
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().where(field, value).resultList
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

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
inline fun <reified T, V> RepositoryLookup.selectBy(field: Metamodel<T, V>, value: Ref<V>): Flow<T>
        where T : Record, V : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().where(field, value).resultFlow
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().where(field, value).resultFlow
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

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
inline fun <reified T, V> RepositoryLookup.findAllBy(field: Metamodel<T, V>, values: Iterable<V>): List<T>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().where(field, IN, values).resultList
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().where(field, IN, values).resultList
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

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
inline fun <reified T, V> RepositoryLookup.selectBy(field: Metamodel<T, V>, values: Iterable<V>): Flow<T>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().where(field, IN, values).resultFlow
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().where(field, IN, values).resultFlow
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

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
inline fun <reified T, V> RepositoryLookup.findAllByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): List<T>
        where T : Record, V : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().whereRef(field, values).resultList
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().whereRef(field, values).resultList
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

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
inline fun <reified T, V> RepositoryLookup.selectByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): Flow<T>
        where T : Record, V : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().whereRef(field, values).resultFlow
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().whereRef(field, values).resultFlow
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

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
inline fun <reified T, V> RepositoryLookup.getBy(field: Metamodel<T, V>, value: V): T
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().where(field, EQUALS, value).singleResult
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().where(field, EQUALS, value).singleResult
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

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
inline fun <reified T, V> RepositoryLookup.getBy(field: Metamodel<T, V>, value: Ref<V>): T
        where T : Record, V : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().where(field, value).singleResult
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().where(field, value).singleResult
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Retrieves an optional entity of type [T] based on a single field and its value.
 * Returns a ref with a null value if no matching entity is found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return an optional entity, or null if none found.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.findRefBy(field: Metamodel<T, V>, value: V): Ref<T>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().where(field, EQUALS, value).optionalResult ?: Ref.ofNull()
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().where(field, EQUALS, value).optionalResult ?: Ref.ofNull()
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Retrieves an optional entity of type [T] based on a single field and its value.
 * Returns a ref with a null value if no matching entity is found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return an optional entity, or null if none found.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.findRefBy(field: Metamodel<T, V>, value: Ref<V>): Ref<T>
        where T : Record, V : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().where(field, value).optionalResult ?: Ref.ofNull()
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().where(field, value).optionalResult ?: Ref.ofNull()
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return list of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.findAllRefBy(field: Metamodel<T, V>, value: V): List<Ref<T>>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().where(field, EQUALS, value).resultList
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().where(field, EQUALS, value).resultList
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty stream if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return stream of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.selectRefBy(field: Metamodel<T, V>, value: V): Flow<Ref<T>>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().where(field, EQUALS, value).resultFlow
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().where(field, EQUALS, value).resultFlow
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return list of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.findAllRefBy(field: Metamodel<T, V>, value: Ref<V>): List<Ref<T>>
        where T : Record, V : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().where(field, value).resultList
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().where(field, value).resultList
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty stream if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return stream of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.selectRefBy(field: Metamodel<T, V>, value: Ref<V>): Flow<Ref<T>>
        where T : Record, V : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().where(field, value).resultFlow
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().where(field, value).resultFlow
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return list of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.findAllRefBy(field: Metamodel<T, V>, values: Iterable<V>): List<Ref<T>>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().where(field, IN, values).resultList
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().where(field, IN, values).resultList
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty stream if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return stream of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.selectRefBy(field: Metamodel<T, V>, values: Iterable<V>): Flow<Ref<T>>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().where(field, IN, values).resultFlow
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().where(field, IN, values).resultFlow
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty list if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return list of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.findAllRefByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): List<Ref<T>>
        where T : Record, V : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().whereRef(field, values).resultList
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().whereRef(field, values).resultList
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty stream if no entities are found.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return stream of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.selectRefByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): Flow<Ref<T>>
        where T : Record, V : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().whereRef(field, values).resultFlow
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().whereRef(field, values).resultFlow
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

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
inline fun <reified T, V> RepositoryLookup.getRefBy(field: Metamodel<T, V>, value: V): Ref<T>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().where(field, EQUALS, value).singleResult
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().where(field, EQUALS, value).singleResult
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

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
inline fun <reified T, V> RepositoryLookup.getRefBy(field: Metamodel<T, V>, value: Ref<V>): Ref<T>
        where T : Record, V : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().where(field, value).singleResult
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().where(field, value).singleResult
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.findAll(
    noinline predicate: WhereBuilder<T, T, *>.() -> PredicateBuilder<T, *, *>
): List<T> where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().whereBuilder(predicate).resultList
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().whereBuilder(predicate).resultList
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.findAll(predicate: PredicateBuilder<T, T, *>): List<T>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().where(predicate).resultList
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().where(predicate).resultList
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.findAllRef(
    noinline predicate: WhereBuilder<T, Ref<T>, *>.() -> PredicateBuilder<T, *, *>
): List<Ref<T>> where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().whereBuilder(predicate).resultList
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().whereBuilder(predicate).resultList
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.findAllRef(predicate: PredicateBuilder<T, T, *>): List<Ref<T>>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().where(predicate).resultList
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().where(predicate).resultList
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.find(
    noinline predicate: WhereBuilder<T, T, *>.() -> PredicateBuilder<T, *, *>
): T? where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().whereBuilder(predicate).optionalResult
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().whereBuilder(predicate).optionalResult
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.find(predicate: PredicateBuilder<T, T, *>): T?
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().where(predicate).optionalResult
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().where(predicate).optionalResult
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.findRef(
    noinline predicate: WhereBuilder<T, Ref<T>, *>.() -> PredicateBuilder<T, *, *>
): Ref<T> where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().whereBuilder(predicate).optionalResult ?: Ref.ofNull()
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().whereBuilder(predicate).optionalResult ?: Ref.ofNull()
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.findRef(predicate: PredicateBuilder<T, T, *>): Ref<T>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().where(predicate).singleResult
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().where(predicate).singleResult
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.get(
    noinline predicate: WhereBuilder<T, T, *>.() -> PredicateBuilder<T, *, *>
): T where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().whereBuilder(predicate).singleResult
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().whereBuilder(predicate).singleResult
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.get(predicate: PredicateBuilder<T, T, *>): T
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().where(predicate).singleResult
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().where(predicate).singleResult
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.getRef(
    noinline predicate: WhereBuilder<T, Ref<T>, *>.() -> PredicateBuilder<T, *, *>
): Ref<T> where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().whereBuilder(predicate).singleResult
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().whereBuilder(predicate).singleResult
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.getRef(predicate: PredicateBuilder<T, Ref<T>, *>): Ref<T>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().where(predicate).singleResult
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().where(predicate).singleResult
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.select(
    noinline predicate: WhereBuilder<T, T, *>.() -> PredicateBuilder<T, *, *>
): Flow<T> where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().whereBuilder(predicate).resultFlow
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().whereBuilder(predicate).resultFlow
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.select(predicate: PredicateBuilder<T, T, *>): Flow<T>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select().where(predicate).resultFlow
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select().where(predicate).resultFlow
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.selectRef(
    noinline predicate: WhereBuilder<T, Ref<T>, *>.() -> PredicateBuilder<T, *, *>
): Flow<Ref<T>> where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().whereBuilder(predicate).resultFlow
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().whereBuilder(predicate).resultFlow
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.selectRef(predicate: PredicateBuilder<T, Ref<T>, *>): Flow<Ref<T>>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef().where(predicate).resultFlow
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef().where(predicate).resultFlow
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.select(): QueryBuilder<T, T, *>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).select()
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).select()
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Creates a query builder to select references of entity records of type [T].
 *
 * @return A [KQueryBuilder] for selecting references of entity records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.selectRef(): QueryBuilder<T, Ref<T>, *>
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectRef()
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectRef()
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Counts entities of type [T] matching the specified field and value.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return the count of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.countBy(
    field: Metamodel<T, V>,
    value: V
): Long where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectCount().where(field, EQUALS, value).singleResult
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectCount().where(field, EQUALS, value).singleResult
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Counts entities of type [T] matching the specified field and referenced value.
 *
 * @param field metamodel reference of the entity field.
 * @param value the referenced value to match against.
 * @return the count of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.countBy(
    field: Metamodel<T, V>,
    value: Ref<V>
): Long where T : Record, V : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectCount().where(field, value).singleResult
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectCount().where(field, value).singleResult
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Counts entities of type [T] matching the specified predicate.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return the count of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.count(
    noinline predicate: WhereBuilder<T, *, *>.() -> PredicateBuilder<T, *, *>
): Long where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectCount().whereBuilder(predicate).singleResult
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectCount().whereBuilder(predicate).singleResult
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Counts entities of type [T] matching the specified predicate.
 *
 * @return the count of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.countAll(): Long
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).count()
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).count()
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Counts entities of type [T] matching the specified predicate.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return the count of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.count(predicate: PredicateBuilder<T, *, *>): Long
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectCount().where(predicate).singleResult
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectCount().where(predicate).singleResult
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Checks if entities of type [T] matching the specified field and value exists.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return true if any matching entities exist, false otherwise.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.existsBy(
    field: Metamodel<T, V>,
    value: V
): Boolean where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectCount().where(field, EQUALS, value).singleResult > 0
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectCount().where(field, EQUALS, value).singleResult > 0
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Checks if entities of type [T] matching the specified field and referenced value exists.
 *
 * @param field metamodel reference of the entity field.
 * @param value the referenced value to match against.
 * @return true if any matching entities exist, false otherwise.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.existsBy(
    field: Metamodel<T, V>,
    value: Ref<V>
): Boolean where T : Record, V : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectCount().where(field, value).singleResult > 0
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectCount().where(field, value).singleResult > 0
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Checks if entities of type [T] matching the specified predicate exists.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return true if any matching entities exist, false otherwise.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.exists(
    noinline predicate: WhereBuilder<T, *, *>.() -> PredicateBuilder<T, *, *>
): Boolean where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectCount().whereBuilder(predicate).singleResult > 0
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectCount().whereBuilder(predicate).singleResult > 0
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Checks if entities of type [T] matching the specified predicate exists.
 *
 * @return true if any matching entities exist, false otherwise.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.exists(): Boolean
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).exists()
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).exists()
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Checks if entities of type [T] matching the specified predicate exists.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return true if any matching entities exist, false otherwise.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.exists(predicate: PredicateBuilder<T, *, *>): Boolean
        where T : Record = this::class
    .memberFunctions
    .first { it.name == if (T::class.isSubclassOf(Entity::class)) "entity" else "projection" }
    .call(this, T::class)
    .let { repository ->
        when (repository) {
            is EntityRepository<*, *> -> (repository as EntityRepository<T, *>).selectCount().where(predicate).singleResult > 0
            is ProjectionRepository<*, *> -> (repository as ProjectionRepository<T, *>).selectCount().where(predicate).singleResult > 0
            else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
        }
    }

/**
 * Inserts an entity of type [T] into the repository.
 *
 * @param entity The entity to insert.
 * @return The inserted entity after fetching from the database.
 */
inline infix fun <reified T> RepositoryLookup.insert(entity: T): T
        where T : Record, T : Entity<*> =
    entity<T>().insertAndFetch(entity)

/**
 * Inserts multiple entities of type [T] into the repository.
 *
 * @param entities Iterable collection of entities to insert.
 * @return list of inserted entities after fetching from the database.
 */
inline infix fun <reified T> RepositoryLookup.insert(entities: Iterable<T>): List<T>
        where T : Record, T : Entity<*> =
    entity<T>().insertAndFetch(entities)

/**
 * Inserts multiple entities of type [T] into the repository.
 *
 * @param entities Flow of entities to insert.
 * @return flow of inserted entities after fetching from the database.
 */
inline infix fun <reified T> RepositoryLookup.insert(entities: Flow<T>): Flow<T>
        where T : Record, T : Entity<*> =
    entity<T>().insertAndFetch(entities)

/**
 * Upserts (inserts or updates) an entity of type [T] into the repository.
 *
 * @param entity The entity to upsert.
 * @return The upserted entity after fetching from the database.
 */
inline infix fun <reified T> RepositoryLookup.upsert(entity: T): T
        where T : Record, T : Entity<*> =
    entity<T>().upsertAndFetch(entity)

/**
 * Upserts (inserts or updates) multiple entities of type [T] into the repository.
 *
 * @param entities Iterable collection of entities to upsert.
 * @return list of upserted entities after fetching from the database.
 */
inline infix fun <reified T> RepositoryLookup.upsert(entities: Iterable<T>): List<T>
        where T : Record, T : Entity<*> =
    entity<T>().upsertAndFetch(entities)

/**
 * Upserts (inserts or updates) multiple entities of type [T] into the repository.
 *
 * @param entities Flow of entities to upsert.
 * @return flow of upserted entities after fetching from the database.
 */
inline infix fun <reified T> RepositoryLookup.upsert(entities: Flow<T>): Flow<T>
        where T : Record, T : Entity<*> =
    entity<T>().upsertAndFetch(entities)

/**
 * Creates a query builder to delete records of type [T].
 *
 * @return A [KQueryBuilder] for deleting records of type [T].
 */
inline fun <reified T> RepositoryLookup.delete(): QueryBuilder<T, *, *>
        where T : Record, T: Entity<*> =
    entity<T>().delete()

/**
 * Deletes an entity of type [T] from the repository.
 *
 * @param entity The entity to delete.
 */
inline infix fun <reified T> RepositoryLookup.delete(entity: T)
        where T : Record, T : Entity<*> =
    entity<T>().delete(entity)

/**
 * Deletes multiple entities of type [T] from the repository.
 *
 * @param entity List of entities to delete.
 */
inline infix fun <reified T> RepositoryLookup.delete(entity: Iterable<T>)
        where T : Record, T : Entity<*> =
    entity<T>().delete(entity)

/**
 * Deletes multiple entities of type [T] from the repository.
 *
 * @param entity Flow of entities to delete.
 */
inline infix suspend fun <reified T> RepositoryLookup.delete(entity: Flow<T>)
        where T : Record, T : Entity<*> =
    entity<T>().delete(entity)

/**
 * Deletes an entity of type [T] from the repository.
 *
 * @param ref The entity to delete.
 */
inline infix fun <reified T> RepositoryLookup.deleteByRef(ref: Ref<T>)
        where T : Record, T : Entity<*> =
    entity<T>().deleteByRef(ref)

/**
 * Deletes multiple entities of type [T] from the repository.
 *
 * @param refs List of entities to delete.
 */
inline infix fun <reified T> RepositoryLookup.deleteByRef(refs: Iterable<Ref<T>>)
        where T : Record, T : Entity<*> =
    entity<T>().deleteByRef(refs)

/**
 * Deletes multiple entities of type [T] from the repository.
 *
 * @param refs Flow of entities to delete.
 */
inline infix suspend fun <reified T> RepositoryLookup.deleteByRef(refs: Flow<Ref<T>>)
        where T : Record, T : Entity<*> =
    entity<T>().deleteByRef(refs)

/**
 * Updates an entity of type [T] in the repository.
 *
 * @param entity The entity to update.
 * @return The updated entity after fetching from the database.
 */
inline infix fun <reified T> RepositoryLookup.update(entity: T): T
        where T : Record, T : Entity<*> =
    entity<T>().updateAndFetch(entity)

/**
 * Updates multiple entities of type [T] in the repository.
 *
 * @param entities Iterable collection of entities to update.
 * @return list of updated entities after fetching from the database.
 */
inline infix fun <reified T> RepositoryLookup.update(entities: Iterable<T>): List<T>
        where T : Record, T : Entity<*> =
    entity<T>().updateAndFetch(entities)

/**
 * Updates multiple entities of type [T] in the repository.
 *
 * @param entities Flow of entities to update.
 * @return flow of updated entities after fetching from the database.
 */
inline infix fun <reified T> RepositoryLookup.update(entities: Flow<T>): Flow<T>
        where T : Record, T : Entity<*> =
    entity<T>().updateAndFetch(entities)

/**
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return list containing all records.
 */
inline fun <reified T> RepositoryLookup.deleteAll()
        where T : Record, T : Entity<*> =
    entity<T>().deleteAll()

/**
 * Deletes entities of type [T] matching the specified ID field and its value.
 *
 * @param field metamodel reference of the ID field.
 * @param value the ID value to match against.
 * @return the number of entities deleted (0 or 1).
 */
inline fun <reified T, ID> RepositoryLookup.deleteBy(
    field: Metamodel<T, ID>,
    value: ID
): Int where T : Record, T : Entity<ID> =
    entity<T>().delete().where(field, EQUALS, value).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified ID field and its value.
 *
 * @param field metamodel reference of the ID field.
 * @param value the ID value to match against.
 * @return the number of entities deleted (0 or 1).
 */
inline fun <reified T> RepositoryLookup.deleteBy(
    field: Metamodel<T, T>,
    value: Ref<T>
): Int where T : Record, T : Entity<*> =
    entity<T>().delete().where(field, value).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified field and value.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return the number of entities deleted.
 */
inline fun <reified T, V> RepositoryLookup.deleteAllBy(
    field: Metamodel<T, V>,
    value: V
): Int where T : Record, T : Entity<*> =
    entity<T>().delete().where(field, EQUALS, value).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified field and referenced value.
 *
 * @param field metamodel reference of the entity field.
 * @param value the referenced value to match against.
 * @return the number of entities deleted.
 */
inline fun <reified T, V> RepositoryLookup.deleteAllBy(
    field: Metamodel<T, V>,
    value: Ref<V>
): Int where T : Record, T : Entity<*>, V : Record =
    entity<T>().delete().where(field, value).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified field against multiple values.
 *
 * @param field metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return the number of entities deleted.
 */
inline fun <reified T, V> RepositoryLookup.deleteAllBy(
    field: Metamodel<T, V>,
    values: Iterable<V>
): Int where T : Record, T : Entity<*> =
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
): Int where T : Record, T : Entity<*>, V : Record =
    entity<T>().delete().whereRef(field, values).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified predicate.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return the number of entities deleted.
 */
inline fun <reified T> RepositoryLookup.delete(
    noinline predicate: WhereBuilder<T, *, *>.() -> PredicateBuilder<T, *, *>
): Int where T : Record, T : Entity<*> =
    entity<T>().delete().whereBuilder(predicate).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified predicate.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return the number of entities deleted.
 */
inline fun <reified T> RepositoryLookup.delete(predicate: PredicateBuilder<T, *, *>): Int
        where T : Record, T : Entity<*> =
    entity<T>().delete().whereBuilder { predicate }.executeUpdate()
