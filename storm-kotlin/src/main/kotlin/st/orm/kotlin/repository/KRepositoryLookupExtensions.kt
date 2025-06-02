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
import st.orm.kotlin.template.KQueryBuilder
import st.orm.repository.Entity
import st.orm.repository.EntityRepository
import st.orm.repository.Projection
import st.orm.repository.ProjectionRepository
import st.orm.repository.RepositoryLookup
import st.orm.template.Metamodel
import st.orm.template.Operator.EQUALS
import st.orm.template.Operator.IN
import st.orm.template.QueryBuilder
import kotlin.jvm.optionals.getOrNull

/**
 * Extensions for [KRepositoryLookup] to provide convenient access to entity repositories.
 */
inline fun <reified T, ID> KRepositoryLookup.typedEntity(): KEntityRepository<T, ID>
        where T : Record, T : Entity<ID> {
    return entity(T::class.java)
}

/**
 * Extensions for [KRepositoryLookup] to provide convenient access to entity repositories.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.entity(): KEntityRepository<T, *>
        where T : Record, T : Entity<*> {
    // Use reflection to prevent the need for the ID parameter. The compiler takes care of the type-safety but is
    // unable to infer the type of the ID parameter at compile time.
    val method = this::class.java.getMethod("entity", Class::class.java)
    return method.invoke(this, T::class.java) as KEntityRepository<T, *>
}

/**
 * Extensions for [KRepositoryLookup] to provide convenient access to projection repositories.
 */
inline fun <reified T, ID> KRepositoryLookup.typedProjection(): KProjectionRepository<T, ID>
        where T : Record, T : Projection<ID> {
    return projection(T::class.java)
}

/**
 * Extensions for [KRepositoryLookup] to provide convenient access to projection repositories.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.projection(): KProjectionRepository<T, *>
        where T : Record, T : Projection<*> {
    // Use reflection to prevent the need for the ID parameter. The compiler takes care of the type-safety but is
    // unable to infer the type of the ID parameter at compile time.
    val method = this::class.java.getMethod("projection", Class::class.java)
    return method.invoke(this, T::class.java) as KProjectionRepository<T, *>
}

/**
 * Extensions for [KRepositoryLookup] to provide convenient access to repositories.
 */
inline fun <reified R : KRepository> KRepositoryLookup.repository(): R {
    return repository(R::class)
}
/**
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return List containing all records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.findAll(): List<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).findAll()
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).findAll()
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves an optional record of type [T] based on a single field and its value.
 * Returns null if no matching record is found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field Metamodel reference of the record field.
 * @param value The value to match against.
 * @return An optional record, or null if none found.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> KRepositoryLookup.findBy(field: Metamodel<T, V>, value: V): T?
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select().where(field, EQUALS, value).optionalResult.getOrNull()
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select().where(field, EQUALS, value).optionalResult.getOrNull()
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves records of type [T] matching a single field and a single value.
 * Returns an empty list if no records are found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field Metamodel reference of the record field.
 * @param value The value to match against.
 * @return List of matching records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> KRepositoryLookup.findAllBy(field: Metamodel<T, V>, value: V): List<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select().where(field, EQUALS, value).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select().where(field, EQUALS, value).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves records of type [T] matching a single field against multiple values.
 * Returns an empty list if no records are found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field Metamodel reference of the record field.
 * @param values Iterable of values to match against.
 * @return List of matching records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> KRepositoryLookup.findAllBy(field: Metamodel<T, V>, values: Iterable<V>): List<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select().where(field, IN, values).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select().where(field, IN, values).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves exactly one record of type [T] based on a single field and its value.
 * Throws an exception if no record or more than one record is found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field Metamodel reference of the record field.
 * @param value The value to match against.
 * @return The matching record.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> KRepositoryLookup.getBy(field: Metamodel<T, V>, value: V): T
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select().where(field, EQUALS, value).singleResult
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select().where(field, EQUALS, value).singleResult
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves an optional entity of type [T] based on a single field and its value.
 * Returns null if no matching entity is found.
 *
 * @param field Metamodel reference of the entity field.
 * @param value The value to match against.
 * @return An optional entity, or null if none found.
 */
inline fun <reified T, V> KRepositoryLookup.findRefBy(field: Metamodel<T, V>, value: V): Ref<T>
        where T : Record, T : Entity<*> =
    entity<T>().selectRef().where(field, EQUALS, value).optionalResult.orElse(Ref.ofNull<T>())

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty list if no entities are found.
 *
 * @param field Metamodel reference of the entity field.
 * @param value The value to match against.
 * @return List of matching entities.
 */
inline fun <reified T, V> KRepositoryLookup.findAllRefBy(field: Metamodel<T, V>, value: V): List<Ref<T>>
        where T : Record, T : Entity<*> =
    entity<T>().selectRef().where(field, EQUALS, value).resultList

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty list if no entities are found.
 *
 * @param field Metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return List of matching entities.
 */
inline fun <reified T, V> KRepositoryLookup.findAllRefBy(field: Metamodel<T, V>, values: Iterable<V>): List<Ref<T>>
        where T : Record, T : Entity<*> =
    entity<T>().selectRef().where(field, IN, values).resultList

/**
 * Retrieves exactly one entity of type [T] based on a single field and its value.
 * Throws an exception if no entity or more than one entity is found.
 *
 * @param field Metamodel reference of the entity field.
 * @param value The value to match against.
 * @return The matching entity.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
inline fun <reified T, V> KRepositoryLookup.getRefBy(field: Metamodel<T, V>, value: V): Ref<T>
        where T : Record, T : Entity<*> =
    entity<T>().selectRef().where(field, EQUALS, value).singleResult

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.select(): KQueryBuilder<T, T, *>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select()
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select()
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select references of entity records of type [T].
 *
 * @return A [QueryBuilder] for selecting references of entity records of type [T].
 */
inline fun <reified T> KRepositoryLookup.selectRef(): KQueryBuilder<T, Ref<T>, *>
        where T : Record, T: Entity<*> = entity<T>().selectRef()

/**
 * Inserts an entity of type [T] into the KRepository.
 *
 * @param entity The entity to insert.
 * @return The inserted entity after fetching from the database.
 */
inline infix fun <reified T> KRepositoryLookup.insert(entity: T): T
        where T : Record, T : Entity<*> =
    entity<T>().insertAndFetch(entity)

/**
 * Inserts multiple entities of type [T] into the KRepository.
 *
 * @param entity Iterable collection of entities to insert.
 * @return List of inserted entities after fetching from the database.
 */
inline infix fun <reified T> KRepositoryLookup.insert(entity: Iterable<T>): List<T>
        where T : Record, T : Entity<*> =
    entity<T>().insertAndFetch(entity)

/**
 * Upserts (inserts or updates) an entity of type [T] into the KRepository.
 *
 * @param entity The entity to upsert.
 * @return The upserted entity after fetching from the database.
 */
inline infix fun <reified T> KRepositoryLookup.upsert(entity: T): T
        where T : Record, T : Entity<*> =
    entity<T>().upsertAndFetch(entity)

/**
 * Upserts (inserts or updates) multiple entities of type [T] into the KRepository.
 *
 * @param entity Iterable collection of entities to upsert.
 * @return List of upserted entities after fetching from the database.
 */
inline infix fun <reified T> KRepositoryLookup.upsert(entity: Iterable<T>): List<T>
        where T : Record, T : Entity<*> =
    entity<T>().upsertAndFetch(entity)

/**
 * Creates a query builder to delete records of type [T].
 *
 * @return A [QueryBuilder] for deleting records of type [T].
 */
inline fun <reified T> KRepositoryLookup.delete(): KQueryBuilder<T, *, *>
        where T : Record, T: Entity<*> =
    entity<T>().delete()

/**
 * Deletes an entity of type [T] from the KRepository.
 *
 * @param entity The entity to delete.
 */
inline infix fun <reified T> KRepositoryLookup.delete(entity: T)
        where T : Record, T : Entity<*> =
    entity<T>().delete(entity)

/**
 * Deletes multiple entities of type [T] from the KRepository.
 *
 * @param entity List of entities to delete.
 */
inline infix fun <reified T> KRepositoryLookup.delete(entity: Iterable<T>)
        where T : Record, T : Entity<*> =
    entity<T>().delete(entity)

/**
 * Deletes an entity of type [T] from the KRepository.
 *
 * @param ref The entity to delete.
 */
inline infix fun <reified T> KRepositoryLookup.deleteByRef(ref: Ref<T>)
        where T : Record, T : Entity<*> =
    entity<T>().deleteByRef(ref)

/**
 * Deletes multiple entities of type [T] from the KRepository.
 *
 * @param refs List of entities to delete.
 */
inline infix fun <reified T> KRepositoryLookup.deleteByRef(refs: Iterable<Ref<T>>)
        where T : Record, T : Entity<*> =
    entity<T>().deleteByRef(refs)

/**
 * Updates an entity of type [T] in the KRepository.
 *
 * @param entity The entity to update.
 * @return The updated entity after fetching from the database.
 */
inline infix fun <reified T> KRepositoryLookup.update(entity: T): T
        where T : Record, T : Entity<*> =
    entity<T>().updateAndFetch(entity)

/**
 * Updates multiple entities of type [T] in the KRepository.
 *
 * @param entity Iterable collection of entities to update.
 * @return List of updated entities after fetching from the database.
 */
inline infix fun <reified T> KRepositoryLookup.update(entity: Iterable<T>): List<T>
        where T : Record, T : Entity<*> =
    entity<T>().updateAndFetch(entity)