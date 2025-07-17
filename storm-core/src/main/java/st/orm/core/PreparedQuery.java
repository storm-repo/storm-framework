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
package st.orm.core;

import jakarta.annotation.Nonnull;
import st.orm.PersistenceException;

import java.util.stream.Stream;

/**
 * Represents a prepared query that can be executed multiple times, allows adding records to a batch and retrieving
 * generated keys.
 */
public interface PreparedQuery extends Query, AutoCloseable {

    /**
     * Add a record to the batch.
     *
     * @param record the record to add to the batch.
     * @throws PersistenceException if adding the batch fails, for instance when query has not specified
     * {@code BatchVars}.
     */
    void addBatch(@Nonnull Record record);

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
