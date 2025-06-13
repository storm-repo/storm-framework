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
@file:Suppress("unused", "DuplicatedCode")

package st.orm.kotlin.repository

import st.orm.Ref
import st.orm.kotlin.CloseableSequence
import st.orm.kotlin.template.KQueryBuilder
import st.orm.kotlin.template.KQueryBuilder.KPredicateBuilder
import st.orm.kotlin.template.KQueryBuilder.KWhereBuilder
import st.orm.repository.Entity
import st.orm.repository.Projection
import st.orm.template.Metamodel
import st.orm.template.Operator.EQUALS
import st.orm.template.Operator.IN
import kotlin.jvm.optionals.getOrNull

/**
 * Extensions for [KRepositoryLookup] to provide convenient access to entity repositories.
 */
inline fun <reified T, ID> KRepositoryLookup.entityWithId(): KEntityRepository<T, ID>
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
inline fun <reified T, ID> KRepositoryLookup.projectionWithId(): KProjectionRepository<T, ID>
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
    return repository(R::class.java)
}

/**
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return list containing all records.
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
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return stream containing all records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.selectAll(): CloseableSequence<T>
        where T : Record =  when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectAll()
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectAll()
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return list containing all records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.findAllRef(): List<Ref<T>>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Retrieves all records of type [T] from the repository.
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return stream containing all records.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.selectAllRef(): CloseableSequence<Ref<T>>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().resultSequence
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().resultSequence
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.findBy(field: Metamodel<T, V>, value: Ref<V>): T?
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select().where(field, value).optionalResult.getOrNull()
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select().where(field, value).optionalResult.getOrNull()
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.selectBy(field: Metamodel<T, V>, value: V): CloseableSequence<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select()
            .where(field, EQUALS, value).resultSequence
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select()
            .where(field, EQUALS, value).resultSequence
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.findAllBy(field: Metamodel<T, V>, value: Ref<V>): List<T>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select().where(field, value).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select().where(field, value).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.selectBy(field: Metamodel<T, V>, value: Ref<V>): CloseableSequence<T>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select()
            .where(field, value).resultSequence
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select()
            .where(field, value).resultSequence
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.selectBy(field: Metamodel<T, V>, values: Iterable<V>): CloseableSequence<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select()
            .where(field, IN, values).resultSequence
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select()
            .where(field, IN, values).resultSequence
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.findAllByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): List<T>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select().whereRef(field, values).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select().whereRef(field, values).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.selectByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): CloseableSequence<T>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select()
            .whereRef(field, values).resultSequence
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select()
            .whereRef(field, values).resultSequence
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.getBy(field: Metamodel<T, V>, value: Ref<V>): T
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select().where(field, value).singleResult
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select().where(field, value).singleResult
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.findRefBy(field: Metamodel<T, V>, value: V): Ref<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().where(field, EQUALS, value).optionalResult.orElse(Ref.ofNull<T>())
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().where(field, EQUALS, value).optionalResult.orElse(Ref.ofNull<T>())
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.findRefBy(field: Metamodel<T, V>, value: Ref<V>): Ref<T>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().where(field, value).optionalResult.orElse(Ref.ofNull<T>())
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().where(field, value).optionalResult.orElse(Ref.ofNull<T>())
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.findAllRefBy(field: Metamodel<T, V>, value: V): List<Ref<T>>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().where(field, EQUALS, value).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().where(field, EQUALS, value).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.selectRefBy(field: Metamodel<T, V>, value: V): CloseableSequence<Ref<T>>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef()
            .where(field, EQUALS, value).resultSequence
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef()
            .where(field, EQUALS, value).resultSequence
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.findAllRefBy(field: Metamodel<T, V>, value: Ref<V>): List<Ref<T>>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().where(field, value).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().where(field, value).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.selectRefBy(field: Metamodel<T, V>, value: Ref<V>): CloseableSequence<Ref<T>>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef()
            .where(field, value).resultSequence
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef()
            .where(field, value).resultSequence
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.findAllRefBy(field: Metamodel<T, V>, values: Iterable<V>): List<Ref<T>>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().where(field, IN, values).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().where(field, IN, values).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.selectRefBy(field: Metamodel<T, V>, values: Iterable<V>): CloseableSequence<Ref<T>>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef()
            .where(field, IN, values).resultSequence
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef()
            .where(field, IN, values).resultSequence
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.findAllRefByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): List<Ref<T>>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().whereRef(field, values).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().whereRef(field, values).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.selectRefByRef(field: Metamodel<T, V>, values: Iterable<Ref<V>>): CloseableSequence<Ref<T>>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef()
            .whereRef(field, values).resultSequence
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef()
            .whereRef(field, values).resultSequence
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.getRefBy(field: Metamodel<T, V>, value: V): Ref<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().where(field, EQUALS, value).singleResult
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().where(field, EQUALS, value).singleResult
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
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
inline fun <reified T, V> KRepositoryLookup.getRefBy(field: Metamodel<T, V>, value: Ref<V>): Ref<T>
        where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().where(field, value).singleResult
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().where(field, value).singleResult
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.findAll(
    noinline predicate: KWhereBuilder<T, T, *>.() -> KPredicateBuilder<T, *, *>
): List<T> where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select().where(predicate).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select().where(predicate).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.findAll(predicateBuilder: KPredicateBuilder<T, T, *>): List<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select().where { predicateBuilder }.resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select().where { predicateBuilder }.resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.findAllRef(
    noinline predicate: KWhereBuilder<T, Ref<T>, *>.() -> KPredicateBuilder<T, *, *>
): List<Ref<T>> where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().where(predicate).resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().where(predicate).resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.findAllRef(predicateBuilder: KPredicateBuilder<T, T, *>): List<Ref<T>>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().where { predicateBuilder }.resultList
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().where { predicateBuilder }.resultList
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.find(
    noinline predicate: KWhereBuilder<T, T, *>.() -> KPredicateBuilder<T, *, *>
): T? where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select().where(predicate).optionalResult.getOrNull()
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select().where(predicate).optionalResult.getOrNull()
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.find(predicateBuilder: KPredicateBuilder<T, T, *>): T?
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select().where { predicateBuilder }.optionalResult.getOrNull()
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select().where { predicateBuilder }.optionalResult.getOrNull()
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.findRef(
    noinline predicate: KWhereBuilder<T, Ref<T>, *>.() -> KPredicateBuilder<T, *, *>
): Ref<T> where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().where(predicate).optionalResult.orElse(Ref.ofNull())
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().where(predicate).optionalResult.orElse(Ref.ofNull())
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.findRef(predicateBuilder: KPredicateBuilder<T, T, *>): Ref<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().where { predicateBuilder }.optionalResult.orElse(Ref.ofNull())
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().where { predicateBuilder }.optionalResult.orElse(Ref.ofNull())
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.get(
    noinline predicate: KWhereBuilder<T, T, *>.() -> KPredicateBuilder<T, *, *>
): T where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select().where(predicate).singleResult
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select().where(predicate).singleResult
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.get(predicateBuilder: KPredicateBuilder<T, T, *>): T
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select().where { predicateBuilder }.singleResult
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select().where { predicateBuilder }.singleResult
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.getRef(
    noinline predicate: KWhereBuilder<T, Ref<T>, *>.() -> KPredicateBuilder<T, *, *>
): Ref<T> where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().where(predicate).singleResult
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().where(predicate).singleResult
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.getRef(predicateBuilder: KPredicateBuilder<T, Ref<T>, *>): Ref<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().where { predicateBuilder }.singleResult
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().where { predicateBuilder }.singleResult
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.select(
    noinline predicate: KWhereBuilder<T, T, *>.() -> KPredicateBuilder<T, *, *>
): CloseableSequence<T> where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select().where(predicate).resultSequence
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select().where(predicate).resultSequence
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.select(predicateBuilder: KPredicateBuilder<T, T, *>): CloseableSequence<T>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).select().where { predicateBuilder }.resultSequence
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).select().where { predicateBuilder }.resultSequence
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.selectRef(
    noinline predicate: KWhereBuilder<T, Ref<T>, *>.() -> KPredicateBuilder<T, *, *>
): CloseableSequence<Ref<T>> where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().where(predicate).resultSequence
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().where(predicate).resultSequence
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.selectRef(predicateBuilder: KPredicateBuilder<T, Ref<T>, *>): CloseableSequence<Ref<T>>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef().where { predicateBuilder }.resultSequence
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef().where { predicateBuilder }.resultSequence
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Creates a query builder to select records of type [T].
 *
 * [T] must be either an Entity or Projection type.
 *
 * @return A [KQueryBuilder] for selecting records of type [T].
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
 * @return A [KQueryBuilder] for selecting references of entity records of type [T].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> KRepositoryLookup.selectRef(): KQueryBuilder<T, Ref<T>, *>
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectRef()
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectRef()
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Counts entities of type [T] matching the specified field and value.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return the count of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> KRepositoryLookup.countBy(
    field: Metamodel<T, V>,
    value: V
): Long where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectCount().where(field, EQUALS, value).singleResult
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectCount().where(field, EQUALS, value).singleResult
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Counts entities of type [T] matching the specified field and referenced value.
 *
 * @param field metamodel reference of the entity field.
 * @param value the referenced value to match against.
 * @return the count of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> KRepositoryLookup.countBy(
    field: Metamodel<T, V>,
    value: Ref<V>
): Long where T : Record, V : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectCount().where(field, value).singleResult
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectCount().where(field, value).singleResult
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Counts entities of type [T] matching the specified predicate.
 *
 * @param predicate Lambda to build the WHERE clause.
 * @return the count of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> KRepositoryLookup.count(
    noinline predicate: KWhereBuilder<T, *, *>.() -> KPredicateBuilder<T, *, *>
): Long where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectCount().where(predicate).singleResult
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectCount().where(predicate).singleResult
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Counts entities of type [T] matching the specified predicate.
 *
 * @param predicateBuilder Lambda to build the WHERE clause.
 * @return the count of matching entities.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T, V> KRepositoryLookup.count(predicateBuilder: KPredicateBuilder<T, *, *>): Long
        where T : Record = when {
    Entity::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("entity", Class::class.java)
        (method.invoke(this, T::class.java) as KEntityRepository<T, *>).selectCount().where { predicateBuilder }.singleResult
    }
    Projection::class.java.isAssignableFrom(T::class.java) -> {
        val method = this::class.java.getMethod("projection", Class::class.java)
        (method.invoke(this, T::class.java) as KProjectionRepository<T, *>).selectCount().where { predicateBuilder }.singleResult
    }
    else -> error("Type ${T::class.simpleName} must be either Entity or Projection")
}

/**
 * Inserts an entity of type [T] into the repository.
 *
 * @param entity The entity to insert.
 * @return The inserted entity after fetching from the database.
 */
inline infix fun <reified T> KRepositoryLookup.insert(entity: T): T
        where T : Record, T : Entity<*> =
    entity<T>().insertAndFetch(entity)

/**
 * Inserts multiple entities of type [T] into the repository.
 *
 * @param entity Iterable collection of entities to insert.
 * @return list of inserted entities after fetching from the database.
 */
inline infix fun <reified T> KRepositoryLookup.insert(entity: Iterable<T>): List<T>
        where T : Record, T : Entity<*> =
    entity<T>().insertAndFetch(entity)

/**
 * Upserts (inserts or updates) an entity of type [T] into the repository.
 *
 * @param entity The entity to upsert.
 * @return The upserted entity after fetching from the database.
 */
inline infix fun <reified T> KRepositoryLookup.upsert(entity: T): T
        where T : Record, T : Entity<*> =
    entity<T>().upsertAndFetch(entity)

/**
 * Upserts (inserts or updates) multiple entities of type [T] into the repository.
 *
 * @param entity Iterable collection of entities to upsert.
 * @return list of upserted entities after fetching from the database.
 */
inline infix fun <reified T> KRepositoryLookup.upsert(entity: Iterable<T>): List<T>
        where T : Record, T : Entity<*> =
    entity<T>().upsertAndFetch(entity)

/**
 * Creates a query builder to delete records of type [T].
 *
 * @return A [KQueryBuilder] for deleting records of type [T].
 */
inline fun <reified T> KRepositoryLookup.delete(): KQueryBuilder<T, *, *>
        where T : Record, T: Entity<*> =
    entity<T>().delete()

/**
 * Deletes an entity of type [T] from the repository.
 *
 * @param entity The entity to delete.
 */
inline infix fun <reified T> KRepositoryLookup.delete(entity: T)
        where T : Record, T : Entity<*> =
    entity<T>().delete(entity)

/**
 * Deletes multiple entities of type [T] from the repository.
 *
 * @param entity List of entities to delete.
 */
inline infix fun <reified T> KRepositoryLookup.delete(entity: Iterable<T>)
        where T : Record, T : Entity<*> =
    entity<T>().delete(entity)

/**
 * Deletes an entity of type [T] from the repository.
 *
 * @param ref The entity to delete.
 */
inline infix fun <reified T> KRepositoryLookup.deleteByRef(ref: Ref<T>)
        where T : Record, T : Entity<*> =
    entity<T>().deleteByRef(ref)

/**
 * Deletes multiple entities of type [T] from the repository.
 *
 * @param refs List of entities to delete.
 */
inline infix fun <reified T> KRepositoryLookup.deleteByRef(refs: Iterable<Ref<T>>)
        where T : Record, T : Entity<*> =
    entity<T>().deleteByRef(refs)

/**
 * Updates an entity of type [T] in the repository.
 *
 * @param entity The entity to update.
 * @return The updated entity after fetching from the database.
 */
inline infix fun <reified T> KRepositoryLookup.update(entity: T): T
        where T : Record, T : Entity<*> =
    entity<T>().updateAndFetch(entity)

/**
 * Updates multiple entities of type [T] in the repository.
 *
 * @param entity Iterable collection of entities to update.
 * @return list of updated entities after fetching from the database.
 */
inline infix fun <reified T> KRepositoryLookup.update(entity: Iterable<T>): List<T>
        where T : Record, T : Entity<*> =
    entity<T>().updateAndFetch(entity)

/**
 * Deletes entities of type [T] matching the specified ID field and its value.
 *
 * @param field metamodel reference of the ID field.
 * @param value the ID value to match against.
 * @return the number of entities deleted (0 or 1).
 */
inline fun <reified T, ID> KRepositoryLookup.deleteBy(
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
inline fun <reified T> KRepositoryLookup.deleteBy(
    field: Metamodel<T, T>,
    value: Ref<T>
): Int where T : Record, T : Entity<*> =
    entity<T>().delete().where(value).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified field and value.
 *
 * @param field metamodel reference of the entity field.
 * @param value the value to match against.
 * @return the number of entities deleted.
 */
inline fun <reified T, V> KRepositoryLookup.deleteAllBy(
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
inline fun <reified T, V> KRepositoryLookup.deleteAllBy(
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
inline fun <reified T, V> KRepositoryLookup.deleteAllBy(
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
inline fun <reified T, V> KRepositoryLookup.deleteAllByRef(
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
inline fun <reified T> KRepositoryLookup.delete(
    noinline predicate: KWhereBuilder<T, *, *>.() -> KPredicateBuilder<T, *, *>
): Int where T : Record, T : Entity<*> =
    entity<T>().delete().where(predicate).executeUpdate()

/**
 * Deletes entities of type [T] matching the specified predicate.
 *
 * @param predicateBuilder Lambda to build the WHERE clause.
 * @return the number of entities deleted.
 */
inline fun <reified T> KRepositoryLookup.delete(predicateBuilder: KPredicateBuilder<T, *, *>): Int
        where T : Record, T : Entity<*> =
    entity<T>().delete().where { predicateBuilder }.executeUpdate()

/**
 * Ensures that the sequence is closed after use, thereby preventing resource leaks.
 *
 * Important: This method closes the underlying resources only if the sequence is fully consumed.
 * Operations such as `take`, `drop`, or other partial-consuming operations will prevent automatic closure,
 * resulting in potential resource leaks. If partial consumption is necessary, manually manage sequence closure.
 *
 * Usage example:
 * ```kotlin
 * myCloseableSequence.closing().forEach { println(it) }
 * // The underlying resources are closed automatically.
 * ```
 *
 * @return A standard [Sequence] that automatically closes the underlying resources upon complete iteration.
 */
fun <T> CloseableSequence<T>.closing(): Sequence<T> = sequence {
        use { sequence -> // ensure close after sequence completion.
            for (item in sequence) yield(item)
        }
    }

/**
 * Yields all elements from the given [CloseableSequence] into this [SequenceScope].
 *
 * Once the inner sequence is fully iterated, its [close()][CloseableSequence.close] method
 * will be called automatically. If iteration stops before consuming every element
 * (for example, using `take(n)`), the caller must close the sequence manually to avoid leaks.
 *
 * @receiver The [SequenceScope] into which elements are yielded.
 * @param sequence The [CloseableSequence] whose elements should be produced.
 */
suspend fun <T> SequenceScope<T>.yieldClosing(sequence: CloseableSequence<T>) {
    sequence.use { yielded ->
        yieldAll(yielded)
    }
}