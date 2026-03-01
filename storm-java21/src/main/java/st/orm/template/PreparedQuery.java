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
package st.orm.template;

import jakarta.annotation.Nonnull;
import java.util.stream.Stream;
import st.orm.Data;
import st.orm.PersistenceException;

/**
 * Represents an eagerly constructed, reusable query that supports batch operations and generated key retrieval.
 *
 * <p>Unlike regular {@link Query} instances which are constructed lazily, a {@code PreparedQuery} is constructed
 * eagerly when {@link Query#prepare()} or {@link QueryBuilder#prepare()} is called. This enables:</p>
 * <ul>
 *   <li><strong>Batch operations</strong> -- Add multiple records via {@link #addBatch(Data)} and execute them
 *       in a single database round-trip with {@link #executeBatch()}.</li>
 *   <li><strong>Generated keys</strong> -- Retrieve auto-generated primary keys after INSERT via
 *       {@link #getGeneratedKeys(Class)}.</li>
 * </ul>
 *
 * <p>As {@code PreparedQuery} implements {@link AutoCloseable}, it must be closed after usage to release database
 * resources. Use it within a try-with-resources block:</p>
 *
 * <pre>{@code
 * var bindVars = orm.createBindVars();
 * try (var query = orm.query(RAW."""
 *         INSERT INTO \{User.class}
 *         VALUES \{bindVars}""").prepare()) {
 *     users.forEach(query::addBatch);
 *     query.executeBatch();
 * }
 * }</pre>
 *
 * @see Query#prepare()
 * @see QueryBuilder#prepare()
 */
public interface PreparedQuery extends Query, AutoCloseable {

    /**
     * Add a record to the batch.
     *
     * @param record the record to add to the batch.
     * @throws PersistenceException if adding the batch fails, for instance when query has not specified
     * {@code BatchVars}.
     */
    void addBatch(@Nonnull Data record);

    /**
     * Returns a stream of generated keys as the result of an insert statement. Returns an empty stream if the insert
     * statement did not generate any keys.
     *
     * <p>The returned stream allows for lazy processing, meaning elements are generated only as they are consumed,
     * optimizing resource usage. Note, however, that calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
     * while in use, it must be closed after usage to prevent resource leaks. As the stream is AutoCloseable, it is
     * recommended to use it within a try-with-resources block.</p>
     *
     * @param <ID> the type of generated keys
     * @param type the class of the keys
     * @return a stream of generated keys resulting from an insert statement; returns an empty stream if no keys are
     * generated.
     * @throws PersistenceException if the statement fails
     */
    <ID> Stream<ID> getGeneratedKeys(@Nonnull Class<ID> type);

    /**
     * Close the resources associated with this query.
     *
     * @throws PersistenceException if the resource cannot be closed.
     */
    @Override
    void close();
}
