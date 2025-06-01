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

import st.orm.repository.Projection
import st.orm.repository.RepositoryLookup
import st.orm.template.Metamodel
import st.orm.template.Operator.EQUALS
import st.orm.template.Operator.IN
import kotlin.jvm.optionals.getOrNull

/**
 * Retrieves an optional projection of type [T] based on a single field and its value.
 * Returns null if no matching projection is found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return An optional projection, or null if none found.
 */
inline fun <reified T, V> RepositoryLookup.findBy(field: Metamodel<T, V>, value: V): T?
        where T : Record, T : Projection<*> =
    projection<T>().select().where(field, EQUALS, value).optionalResult.getOrNull()

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty list if no entities are found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return List of matching entities.
 */
inline fun <reified T, V> RepositoryLookup.findAllBy(field: Metamodel<T, V>, value: V): List<T>
        where T : Record, T : Projection<*> =
    projection<T>().select().where(field, EQUALS, value).resultList

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty list if no entities are found.
 *
 * @param field Metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return List of matching entities.
 */
inline fun <reified T, V> RepositoryLookup.findAllBy(field: Metamodel<T, V>, values: Iterable<V>): List<T>
        where T : Record, T : Projection<*> =
    projection<T>().select().where(field, IN, values).resultList

/**
 * Retrieves exactly one projection of type [T] based on a single field and its value.
 * Throws an exception if no projection or more than one projection is found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return The matching projection.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
inline fun <reified T, V> RepositoryLookup.getBy(field: Metamodel<T, V>, value: V): T
        where T : Record, T : Projection<*> =
    projection<T>().select().where(field, EQUALS, value).singleResult

/**
 * Retrieves an optional projection of type [T] based on a single field and its value.
 * Returns null if no matching projection is found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return An optional projection, or null if none found.
 */
inline fun <reified T, V> KRepositoryLookup.findBy(field: Metamodel<T, V>, value: V): T?
        where T : Record, T : Projection<*> =
    projection<T>().select().where(field, EQUALS, value).optionalResult.getOrNull()

/**
 * Retrieves entities of type [T] matching a single field and a single value.
 * Returns an empty list if no entities are found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return List of matching entities.
 */
inline fun <reified T, V> KRepositoryLookup.findAllBy(field: Metamodel<T, V>, value: V): List<T>
        where T : Record, T : Projection<*> =
    projection<T>().select().where(field, EQUALS, value).resultList

/**
 * Retrieves entities of type [T] matching a single field against multiple values.
 * Returns an empty list if no entities are found.
 *
 * @param field Metamodel reference of the projection field.
 * @param values Iterable of values to match against.
 * @return List of matching entities.
 */
inline fun <reified T, V> KRepositoryLookup.findAllBy(field: Metamodel<T, V>, values: Iterable<V>): List<T>
        where T : Record, T : Projection<*> =
    projection<T>().select().where(field, IN, values).resultList

/**
 * Retrieves exactly one projection of type [T] based on a single field and its value.
 * Throws an exception if no projection or more than one projection is found.
 *
 * @param field Metamodel reference of the projection field.
 * @param value The value to match against.
 * @return The matching projection.
 * @throws st.orm.NoResultException if there is no result.
 * @throws st.orm.NonUniqueResultException if more than one result.
 */
inline fun <reified T, V> KRepositoryLookup.getBy(field: Metamodel<T, V>, value: V): T
        where T : Record, T : Projection<*> =
    projection<T>().select().where(field, EQUALS, value).singleResult
