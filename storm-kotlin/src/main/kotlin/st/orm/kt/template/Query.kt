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
package st.orm.kt.template

import jakarta.annotation.Nonnull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.stream.consumeAsFlow
import st.orm.NoResultException
import st.orm.NonUniqueResultException
import st.orm.Ref
import java.util.function.Supplier
import java.util.stream.Stream
import kotlin.reflect.KClass

/**
 * Abstraction for DQL (Data Query Language) statements, such as SELECT queries and  DML (Data Manipulation Language)
 * statements, such as INSERT, UPDATE and DELETE statements
 */
interface Query {
    /**
     * Prepares the query for execution.
     *
     * Queries are normally constructed in a lazy fashion, unlike prepared queries which are constructed eagerly.
     * Prepared queries allow the use of bind variables and enable reading generated keys after row insertion.
     *
     * **Note:** The prepared query must be closed after usage to prevent resource leaks. As the
     * prepared query is `AutoCloseable`, it is recommended to use it within a `try-with-resources` block.
     *
     * @return the prepared query.
     * @throws st.orm.PersistenceException if the query preparation fails.
     */
    fun prepare(): PreparedQuery

    /**
     * Returns a new query that is marked as safe. This means that dangerous operations, such as DELETE and UPDATE
     * without a WHERE clause, will be allowed.
     *
     * @return a new query that is marked as safe.
     * @since 1.2
     */
    fun safe(): Query

    val singleResult: Array<Any>
        /**
         * Execute a SELECT query and returns a single row, where the columns of the row corresponds to the order of values
         * in the list.
         *
         * @return a single row, where the columns of the row corresponds to the order of values the list.
         * @throws st.orm.NoResultException if there is no result.
         * @throws st.orm.NonUniqueResultException if more than one result.
         * @throws st.orm.PersistenceException if the query fails.
         */
        get() = singleResult(this.resultStream)

    val optionalResult: Array<Any>?
        /**
         * Execute a SELECT query and returns a single row, where the columns of the row corresponds to the order of values
         * in the list.
         *
         * @return a single row, where the columns of the row corresponds to the order of values the list, or an empty
         * optional if there is no result.
         * @throws st.orm.NonUniqueResultException if more than one result.
         * @throws st.orm.PersistenceException if the query fails.
         */
        get() = optionalResult(this.resultStream)

    val resultCount: Long
        /**
         * Returns the number of results of this query.
         *
         * @return the total number of results of this query as a long value.
         * @throws st.orm.PersistenceException if the query operation fails due to underlying database issues, such as
         * connectivity.
         */
        get() {
            this.resultStream.use { stream ->
                return stream.count()
            }
        }

    /**
     * Execute a SELECT query and returns a single row, where the columns of the row are mapped to the constructor
     * arguments of the specified `type`.
     *
     * @param type the type of the result.
     * @return a single row, where the columns of the row corresponds to the order of values the list.
     * @throws st.orm.NoResultException if there is no result.
     * @throws st.orm.NonUniqueResultException if more than one result.
     * @throws st.orm.PersistenceException if the query fails.
     */
    fun <T : Any> getSingleResult(type: KClass<T>): T {
        return singleResult(getResultStream(type))
    }

    /**
     * Execute a SELECT query and returns a single row, where the columns of the row are mapped to the constructor
     * arguments of the specified `type`.
     *
     * @param type the type of the result.
     * @return a single row, where the columns of the row corresponds to the order of values the list, or an empty
     * optional if there is no result.
     * @throws st.orm.NonUniqueResultException if more than one result.
     * @throws st.orm.PersistenceException if the query fails.
     */
    fun <T : Any> getOptionalResult(type: KClass<T>): T? {
        return optionalResult(getResultStream(type))
    }

    val resultList: List<Array<Any>>
        /**
         * Execute a SELECT query and return the resulting rows as a list of row instances.
         *
         * Each element in the list represents a row in the result, where the columns of the row corresponds to the
         * order of values in the row array.
         *
         * @return the result list.
         * @throws st.orm.PersistenceException if the query fails.
         */
        get() {
            this.resultStream.use { stream ->
                return stream.toList()
            }
        }

    /**
     * Execute a SELECT query and return the resulting rows as a list of row instances.
     *
     * Each element in the list represents a row in the result, where the columns of the row are mapped to the
     * constructor arguments of the specified `type`.
     *
     * @param type the type of the result.
     * @return the result list.
     * @throws st.orm.PersistenceException if the query fails.
     */
    fun <T : Any> getResultList(type: KClass<T>): List<T> {
        getResultStream(type).use { stream ->
            return stream.toList()
        }
    }

    /**
     * Execute a SELECT query and return the resulting rows as a list of ref instances.
     *
     * Each element in the list represents a row in the result, where the columns of the row are mapped to the
     * constructor arguments primary key type.
     *
     * @param type the type of the results that are being referenced.
     * @param pkType the primary key type.
     * @return the result list.
     * @throws st.orm.PersistenceException if the query fails.
     * @since 1.3
     */
    fun <T : Record> getRefList(type: KClass<T>, pkType: KClass<*>): List<Ref<T>> {
        getRefStream(type, pkType).use { stream ->
            return stream.toList()
        }
    }

    /**
     * Execute a SELECT query and return the resulting rows as a stream of row instances.
     *
     * Each element in the stream represents a row in the result, where the columns of the row corresponds to the
     * order of values in the row array.
     *
     * The resulting stream is lazily loaded, meaning that the records are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of records.
     *
     * **Note:** Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must be
     * closed after usage to prevent resource leaks. As the stream is `AutoCloseable`, it is recommended to use it
     * within a `try-with-resources` block.
     *
     * @return a stream of results.
     * @throws st.orm.PersistenceException if the query operation fails due to underlying database issues, such as
     * connectivity.
     */
    val resultStream: Stream<Array<Any>>

    /**
     * Execute a SELECT query and return the resulting rows as a flow of row instances.
     *
     * Each element in the flow represents a row in the result, where the columns of the row corresponds to the
     * order of values in the row array.
     *
     * @return a flow of results.
     * @throws st.orm.PersistenceException if the query operation fails due to underlying database issues, such as
     * connectivity.
     * @since 1.5
     */
    val resultFlow: Flow<Array<Any>>
        get() = resultStream.consumeAsFlow()

    /**
     * Execute a SELECT query and return the resulting rows as a stream of row instances.
     *
     * Each element in the stream represents a row in the result, where the columns of the row are mapped to the
     * constructor arguments of the specified `type`.
     *
     * The resulting stream is lazily loaded, meaning that the records are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of records.
     *
     * **Note:** Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must be
     * closed after usage to prevent resource leaks. As the stream is `AutoCloseable`, it is recommended to use it
     * within a `try-with-resources` block.
     *
     * @return a stream of results.
     * @throws st.orm.PersistenceException if the query operation fails due to underlying database issues, such as
     * connectivity.
     */
    fun <T : Any> getResultStream(type: KClass<T>): Stream<T>

    /**
     * Execute a SELECT query and return the resulting rows as a flow of row instances.
     *
     * Each element in the flow represents a row in the result, where the columns of the row are mapped to the
     * constructor arguments of the specified `type`.
     *
     * @return a flow of results.
     * @throws st.orm.PersistenceException if the query operation fails due to underlying database issues, such as
     * connectivity.
     * @since 1.5
     */
    fun <T : Any> getResultFlow(type: KClass<T>): Flow<T> =
        getResultStream(type).consumeAsFlow()

    /**
     * Execute a SELECT query and return the resulting rows as a stream of ref instances.
     *
     * Each element in the stream represents a row in the result, where the columns of the row are mapped to the
     * constructor arguments primary key type.
     *
     * **Note:** Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must be
     * closed after usage to prevent resource leaks. As the stream is `AutoCloseable`, it is recommended to use it
     * within a `try-with-resources` block.
     *
     * @param type the type of the results that are being referenced.
     * @param pkType the primary key type.
     * @return a stream of ref instances.
     * @throws st.orm.PersistenceException if the query fails.
     * @since 1.3
     */
    fun <T : Record> getRefStream(type: KClass<T>, pkType: KClass<*>): Stream<Ref<T>>

    /**
     * Execute a SELECT query and return the resulting rows as a flow of ref instances.
     *
     * Each element in the flow represents a row in the result, where the columns of the row are mapped to the
     * constructor arguments primary key type.
     *
     * @param type the type of the results that are being referenced.
     * @param pkType the primary key type.
     * @return a flow of ref instances.
     * @throws st.orm.PersistenceException if the query fails.
     * @since 1.5
     */
    fun <T : Record> getRefFlow(type: KClass<T>, pkType: KClass<*>): Flow<Ref<T>> =
        getRefStream(type, pkType).consumeAsFlow()

    /**
     * Returns true if the query is version aware, false otherwise.
     *
     * @return true if the query is version aware, false otherwise.
     */
    val versionAware: Boolean

    /**
     * Execute a command, such as an INSERT, UPDATE or DELETE statement.
     *
     * @return the number of rows impacted as result of the statement.
     * @throws st.orm.PersistenceException if the statement fails.
     */
    fun executeUpdate(): Int

    /**
     * Execute a batch of commands.
     *
     * @throws st.orm.PersistenceException if the batch fails.
     * @return an array of update counts containing one element for each command in the batch. The elements of the
     * array are ordered according to the order in which commands were added to the batch, following
     * `Statement.executeBatch` semantics.
     */
    fun executeBatch(): IntArray

    /**
     * Returns the single result of the stream.
     *
     * @param stream the stream to get the single result from.
     * @return the single result of the stream.
     * @param <T> the type of the result.
     * @throws st.orm.NoResultException if there is no result.
     * @throws st.orm.NonUniqueResultException if more than one result.
     */
    private fun <T> singleResult(stream: Stream<T>): T {
        stream.use {
            return stream
                .reduce { _, _ ->
                    throw NonUniqueResultException("Expected single result, but found more than one.")
                }
                .orElseThrow(Supplier { NoResultException("Expected single result, but found none.") })
        }
    }

    /**
     * Returns the single result of the stream, or an empty optional if there is no result.
     *
     * @param stream the stream to get the single result from.
     * @return the single result of the stream.
     * @param <T> the type of the result.
     * @throws NonUniqueResultException if more than one result.
     */
    private fun <T> optionalResult(@Nonnull stream: Stream<T>): T? {
        stream.use {
            return stream
                .reduce { _, _ ->
                    throw NonUniqueResultException("Expected single result, but found more than one.")
                }
                .orElse(null)
        }
    }
}
