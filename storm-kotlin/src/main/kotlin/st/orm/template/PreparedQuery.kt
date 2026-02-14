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
package st.orm.template

import st.orm.Data
import java.util.stream.Stream
import kotlin.reflect.KClass

/**
 * Represents an eagerly constructed, reusable query that supports batch operations and generated key retrieval.
 *
 * Unlike regular [Query] instances which are constructed lazily, a `PreparedQuery` is constructed eagerly when
 * [Query.prepare] or [QueryBuilder.prepare] is called. This enables:
 * - **Batch operations** -- Add multiple records via [addBatch] and execute them in a single database round-trip
 *   with [executeBatch].
 * - **Generated keys** -- Retrieve auto-generated primary keys after INSERT via [getGeneratedKeys].
 *
 * As `PreparedQuery` implements [AutoCloseable], it must be closed after usage to release database resources.
 * Use it within a `use` block:
 *
 * ```kotlin
 * val bindVars = orm.createBindVars()
 * orm.query("INSERT INTO ${User::class} VALUES ${bindVars}").prepare().use { query ->
 *     users.forEach { query.addBatch(it) }
 *     query.executeBatch()
 * }
 * ```
 *
 * @see Query.prepare
 * @see QueryBuilder.prepare
 */
interface PreparedQuery :
    Query,
    AutoCloseable {
    /**
     * Add a record to the batch.
     *
     * @param record the record to add to the batch.
     * @throws st.orm.PersistenceException if adding the batch fails, for instance when query has not specified
     * `BatchVars`.
     */
    fun addBatch(record: Data)

    /**
     * Returns a stream of generated keys as the result of an insert statement. Returns an empty stream if the insert
     * statement did not generate any keys.
     *
     *
     * The returned stream allows for lazy processing, meaning elements are generated only as they are consumed,
     * optimizing resource usage. Note, however, that calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
     * while in use, it must be closed after usage to prevent resource leaks. As the stream is AutoCloseable, it is
     * recommended to use it within a try-with-resources block.
     *
     * @param <ID> the type of generated keys
     * @param type the class of the keys
     * @return a stream of generated keys resulting from an insert statement; returns an empty stream if no keys are
     * generated.
     * @throws st.orm.PersistenceException if the statement fails
     </ID> */
    fun <ID : Any> getGeneratedKeys(type: KClass<ID>): Stream<ID>

    /**
     * Close the resources associated with this query.
     *
     * @throws st.orm.PersistenceException if the resource cannot be closed.
     */
    override fun close()
}
