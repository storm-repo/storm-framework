/*
 * Copyright 2024 - 2026 the original author or authors.
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
package st.orm.template.impl

import st.orm.Data
import st.orm.Ref
import st.orm.template.PreparedQuery
import st.orm.template.Query
import java.util.stream.Stream
import kotlin.reflect.KClass

open class QueryImpl(private val core: st.orm.core.template.Query) : Query {

    /**
     * Prepares the query for execution.
     *
     * Queries are normally constructed in a lazy fashion, unlike prepared queries which are constructed eagerly.
     * Prepared queries allow the use of bind variables and enable reading generated keys after row insertion.
     *
     * **Note:** The prepared query must be closed after usage to prevent resource leaks.
     *
     * @return the prepared query.
     * @throws st.orm.PersistenceException if the query preparation fails.
     */
    override fun prepare(): PreparedQuery {
        return PreparedQueryImpl(core.prepare())
    }

    /**
     * Returns a new query that is marked as safe. This means that dangerous operations, such as DELETE and UPDATE
     * without a WHERE clause, will be allowed.
     *
     * @return a new query that is marked as safe.
     * @since 1.2
     */
    override fun safe(): Query {
        return QueryImpl(core.safe())
    }

    override val resultStream: Stream<Array<Any>>
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
         * closed after usage to prevent resource leaks.
         *
         * @return a stream of results.
         * @throws st.orm.PersistenceException if the query operation fails due to underlying database issues, such as
         * connectivity.
         */
        get() = core.getResultStream()

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
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must
     * be closed after usage to prevent resource leaks.
     *
     * @return a stream of results.
     * @throws st.orm.PersistenceException if the query operation fails due to underlying database issues, such as
     * connectivity.
     */
    override fun <T : Any> getResultStream(type: KClass<T>): Stream<T> {
        return core.getResultStream<T>(type.java)
    }

    /**
     * Execute a SELECT query and return the resulting rows as a stream of ref instances.
     *
     * Each element in the stream represents a row in the result, where the columns of the row are mapped to the
     * constructor arguments primary key type.
     *
     * **Note:** Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must
     * be closed after usage to prevent resource leaks.
     *
     * @param type the type of the results that are being referenced.
     * @param pkType the primary key type.
     * @return a stream of ref instances.
     * @throws st.orm.PersistenceException if the query fails.
     * @since 1.3
     */
    override fun <T : Data> getRefStream(
        type: KClass<T>,
        pkType: KClass<*>
    ): Stream<Ref<T>> {
        return core.getRefStream<T>(type.java, pkType.java)
    }

    override val versionAware: Boolean
        /**
         * Returns true if the query is version aware, false otherwise.
         *
         * @return true if the query is version aware, false otherwise.
         */
        get() = core.isVersionAware()

    /**
     * Execute a command, such as an INSERT, UPDATE, or DELETE statement.
     *
     * @return the number of rows impacted as result of the statement.
     * @throws st.orm.PersistenceException if the statement fails.
     */
    override fun executeUpdate(): Int {
        return core.executeUpdate()
    }

    /**
     * Execute a batch of commands.
     *
     * @throws st.orm.PersistenceException if the batch fails.
     * @return an array of update counts containing one element for each command in the batch. The elements of the
     * array are ordered according to the order in which commands were added to the batch, following
     * `Statement.executeBatch` semantics.
     */
    override fun executeBatch(): IntArray {
        return core.executeBatch()
    }
}
