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
import st.orm.repository.Entity
import st.orm.repository.EntityRepository
import st.orm.repository.Projection
import st.orm.repository.ProjectionRepository
import st.orm.repository.Repository
import st.orm.repository.RepositoryLookup
import st.orm.template.Metamodel
import st.orm.template.Operator.EQUALS
import st.orm.template.Operator.IN
import st.orm.template.QueryBuilder
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull

/**
 * Extensions for [RepositoryLookup] to provide convenient access to entity repositories.
 */
inline fun <reified T, ID> RepositoryLookup.typedEntity(): EntityRepository<T, ID>
        where T : Record, T : Entity<ID> {
    return entity(T::class.java)
}

/**
 * Extensions for [RepositoryLookup] to provide convenient access to entity repositories.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.entity(): EntityRepository<T, *>
        where T : Record, T : Entity<*> {
    // Use reflection to prevent the need for the ID parameter. The compiler takes care of the type-safety but is
    // unable to infer the type of the ID parameter at compile time.
    val method = this::class.java.getMethod("entity", Class::class.java)
    return method.invoke(this, T::class.java) as EntityRepository<T, *>
}

/**
 * Extensions for [RepositoryLookup] to provide convenient access to projection repositories.
 */
inline fun <reified T, ID> RepositoryLookup.typedProjection(): ProjectionRepository<T, ID>
        where T : Record, T : Projection<ID> {
    return projection(T::class.java)
}

/**
 * Extensions for [RepositoryLookup] to provide convenient access to projection repositories.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.projection(): ProjectionRepository<T, *>
            where T : Record, T : Projection<*> {
    // Use reflection to prevent the need for the ID parameter. The compiler takes care of the type-safety but is
    // unable to infer the type of the ID parameter at compile time.
    val method = this::class.java.getMethod("projection", Class::class.java)
    return method.invoke(this, T::class.java) as ProjectionRepository<T, *>
}

/**
 * Extensions for [RepositoryLookup] to provide convenient access to repositories.
 */
inline fun <reified R : Repository> RepositoryLookup.repository(): R {
    return repository(R::class.java)
}

/**
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return List containing all records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.findAll(): List<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).findAll()
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).findAll()
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return Stream containing all records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.selectAll(): Stream<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).selectAll()
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).selectAll()
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return List containing all records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.findAllRef(): List<Ref<T>>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).selectRef().resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).selectRef().resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return Stream containing all records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.selectAllRef(): Stream<Ref<T>>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).selectRef().resultStream
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).selectRef().resultStream
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
inline fun <reified T, V> RepositoryLookup.findBy(field: Metamodel<T, V>, value: V): T?
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).select().where(field, EQUALS, value).optionalResult.getOrNull()
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).select().where(field, EQUALS, value).optionalResult.getOrNull()
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
inline fun <reified T, V> RepositoryLookup.findBy(field: Metamodel<T, V>, value: Ref<V>): T?
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).select().where(field, value).optionalResult.getOrNull()
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).select().where(field, value).optionalResult.getOrNull()
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
inline fun <reified T, V> RepositoryLookup.findAllBy(field: Metamodel<T, V>, value: V): List<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).select().where(field, EQUALS, value).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).select().where(field, EQUALS, value).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves records of type [T] matching a single field and a single value.
 * Returns an empty stream if no records are found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field Metamodel reference of the record field.
 * @param value The value to match against.
 * @return Stream of matching records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.selectAllBy(field: Metamodel<T, V>, value: V): Stream<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).select().where(field, EQUALS, value).resultStream
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).select().where(field, EQUALS, value).resultStream
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
inline fun <reified T, V> RepositoryLookup.findAllBy(field: Metamodel<T, V>, value: Ref<V>): List<T>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).select().where(field, value).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).select().where(field, value).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves records of type [T] matching a single field and a single value.
 * Returns an empty stream if no records are found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field Metamodel reference of the record field.
 * @param value The value to match against.
 * @return Stream of matching records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.selectAllBy(field: Metamodel<T, V>, value: Ref<V>): Stream<T>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).select().where(field, value).resultStream
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).select().where(field, value).resultStream
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
inline fun <reified T, V> RepositoryLookup.findAllBy(field: Metamodel<T, V>, values: Iterable<V>): List<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).select().where(field, IN, values).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).select().where(field, IN, values).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves records of type [T] matching a single field against multiple values.
 * Returns an empty stream if no records are found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field Metamodel reference of the record field.
 * @param values Iterable of values to match against.
 * @return Stream of matching records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.selectAllBy(field: Metamodel<T, V>, values: Iterable<V>): Stream<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).select().where(field, IN, values).resultStream
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).select().where(field, IN, values).resultStream
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
inline fun <reified T, V> RepositoryLookup.findAllByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): List<T>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).select().whereRef(field, values).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).select().whereRef(field, values).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves records of type [T] matching a single field against multiple values.
 * Returns an empty stream if no records are found.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @param field Metamodel reference of the record field.
 * @param values Iterable of values to match against.
 * @return Stream of matching records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.selectAllByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): Stream<T>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).select().whereRef(field, values).resultStream
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).select().whereRef(field, values).resultStream
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
inline fun <reified T, V> RepositoryLookup.getBy(field: Metamodel<T, V>, value: V): T
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).select().where(field, EQUALS, value).singleResult
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).select().where(field, EQUALS, value).singleResult
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
inline fun <reified T, V> RepositoryLookup.getBy(field: Metamodel<T, V>, value: Ref<V>): T
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).select().where(field, value).singleResult
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).select().where(field, value).singleResult
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
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.findRefBy(field: Metamodel<T, V>, value: V): Ref<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).selectRef().where(field, EQUALS, value).optionalResult.orElse(Ref.ofNull<T>())
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).selectRef().where(field, EQUALS, value).optionalResult.orElse(Ref.ofNull<T>())
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
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.findRefBy(field: Metamodel<T, V>, value: Ref<V>): Ref<T>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).selectRef().where(field, value).optionalResult.orElse(Ref.ofNull<T>())
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).selectRef().where(field, value).optionalResult.orElse(Ref.ofNull<T>())
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty list if no entities are found.
 *
 * @param field Metamodel reference of the entity field.
 * @param value The value to match against.
 * @return List of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.findAllRefBy(field: Metamodel<T, V>, value: V): List<Ref<T>>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).selectRef().where(field, EQUALS, value).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).selectRef().where(field, EQUALS, value).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty stream if no entities are found.
 *
 * @param field Metamodel reference of the entity field.
 * @param value The value to match against.
 * @return Stream of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.selectAllRefBy(field: Metamodel<T, V>, value: V): Stream<Ref<T>>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).selectRef().where(field, EQUALS, value).resultStream
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).selectRef().where(field, EQUALS, value).resultStream
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty list if no entities are found.
 *
 * @param field Metamodel reference of the entity field.
 * @param value The value to match against.
 * @return List of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.findAllRefBy(field: Metamodel<T, V>, value: Ref<V>): List<Ref<T>>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).selectRef().where(field, value).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).selectRef().where(field, value).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty stream if no entities are found.
 *
 * @param field Metamodel reference of the entity field.
 * @param value The value to match against.
 * @return Stream of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.selectAllRefBy(field: Metamodel<T, V>, value: Ref<V>): Stream<Ref<T>>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).selectRef().where(field, value).resultStream
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).selectRef().where(field, value).resultStream
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty list if no entities are found.
 *
 * @param field Metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return List of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.findAllRefBy(field: Metamodel<T, V>, values: Iterable<V>): List<Ref<T>>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).selectRef().where(field, IN, values).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).selectRef().where(field, IN, values).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty stream if no entities are found.
 *
 * @param field Metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return Stream of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.selectAllRefBy(field: Metamodel<T, V>, values: Iterable<V>): Stream<Ref<T>>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).selectRef().where(field, IN, values).resultStream
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).selectRef().where(field, IN, values).resultStream
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty list if no entities are found.
 *
 * @param field Metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return List of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.findAllRefByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): List<Ref<T>>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).selectRef().whereRef(field, values).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).selectRef().whereRef(field, values).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty stream if no entities are found.
 *
 * @param field Metamodel reference of the entity field.
 * @param values Iterable of values to match against.
 * @return Stream of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.selectAllRefByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): Stream<Ref<T>>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).selectRef().whereRef(field, values).resultStream
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).selectRef().whereRef(field, values).resultStream
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

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
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.getRefBy(field: Metamodel<T, V>, value: V): Ref<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).selectRef().where(field, EQUALS, value).singleResult
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).selectRef().where(field, EQUALS, value).singleResult
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

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
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> RepositoryLookup.getRefBy(field: Metamodel<T, V>, value: Ref<V>): Ref<T>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).selectRef().where(field, value).singleResult
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).selectRef().where(field, value).singleResult
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [QueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.select(): QueryBuilder<T, T, *>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).select()
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).select()
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select references of entity records of type [T].
 *
 * @return A [QueryBuilder] for selecting references of entity records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> RepositoryLookup.selectRef(): QueryBuilder<T, Ref<T>, *>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as EntityRepository<T, *>).selectRef()
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as ProjectionRepository<T, *>).selectRef()
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
 * @param entity Iterable collection of entities to insert.
 * @return List of inserted entities after fetching from the database.
 */
inline infix fun <reified T> RepositoryLookup.insert(entity: Iterable<T>): List<T>
        where T : Record, T : Entity<*> =
    entity<T>().insertAndFetch(entity)

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
 * @param entity Iterable collection of entities to upsert.
 * @return List of upserted entities after fetching from the database.
 */
inline infix fun <reified T> RepositoryLookup.upsert(entity: Iterable<T>): List<T>
        where T : Record, T : Entity<*> =
    entity<T>().upsertAndFetch(entity)

/**
 * Creates a query builder to delete records of type [T].
 *
 * @return A [QueryBuilder] for deleting records of type [T].
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
 * @param entity Iterable collection of entities to update.
 * @return List of updated entities after fetching from the database.
 */
inline infix fun <reified T> RepositoryLookup.update(entity: Iterable<T>): List<T>
        where T : Record, T : Entity<*> =
    entity<T>().updateAndFetch(entity)
