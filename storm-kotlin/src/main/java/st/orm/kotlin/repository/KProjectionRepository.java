/*
 * Copyright 2024 the original author or authors.
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
package st.orm.kotlin.repository;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import kotlin.sequences.Sequence;
import st.orm.Lazy;
import st.orm.NoResultException;
import st.orm.PersistenceException;
import st.orm.kotlin.KResultCallback;
import st.orm.kotlin.template.KQueryBuilder;
import st.orm.repository.Projection;

import java.util.List;

/**
 * Provides a generic interface with CRUD operations for projections.
 * 
 * @since 1.1
 */
public interface KProjectionRepository<P extends Record & Projection<ID>, ID> extends KRepository {

    /**
     * Returns the projection model associated with this repository.
     *
     * @return the projection model.
     */
    KModel<P, ID> model();

    /**
     * Creates a new lazy projection instance with the specified primary key.
     *
     * @param id the primary key of the projection.
     * @return a lazy projection instance.
     */
    Lazy<P, ID> lazy(@Nullable ID id);

    // Query builder methods.

    /**
     * Creates a new query builder for the projection type managed by this repository.
     *
     * @return a new query builder for the projection type.
     */
    KQueryBuilder<P, P, ID> select();

    /**
     * Creates a new query builder for the projection type managed by this repository.
     *
     * @return a new query builder for the projection type.
     */
    KQueryBuilder<P, Long, ID> selectCount();

    /**
     * Creates a new query builder for the custom {@code selectType}.
     *
     * @param selectType the result type of the query.
     * @return a new query builder for the custom {@code selectType}.
     * @param <R> the result type of the query.
     */
    <R> KQueryBuilder<P, R, ID> select(@Nonnull Class<R> selectType);

    /**
     * Creates a new query builder for the custom {@code selectType} and custom {@code template} for the select clause.
     *
     * @param selectType the result type of the query.
     * @param template the custom template for the select clause.
     * @return a new query builder for the custom {@code selectType}.
     * @param <R> the result type of the query.
     */
    <R> KQueryBuilder<P, R, ID> select(@Nonnull Class<R> selectType, @Nonnull StringTemplate template);

    // Base methods.

    /**
     * Retrieves a projection based on its primary key.
     *
     * <p>This method performs a lookup in the database, returning the corresponding projection if it exists.</p>
     *
     * @param id the primary key of the projection to retrieve.
     * @return the projection associated with the provided primary key. The returned projection encapsulates all relevant data
     * as mapped by the projection model.
     * @throws NoResultException if no projection is found matching the given primary key, indicating that there's no
     *                           corresponding data in the database.
     * @throws PersistenceException if the retrieval operation fails due to underlying database issues, such as
     *                              connectivity problems or query execution errors.
     */
    P select(@Nonnull ID id);

    /**
     * Returns the number of projections in the database of the projection type supported by this repository.
     *
     * @return the total number of projections in the database as a long value.
     * @throws PersistenceException if the count operation fails due to underlying database issues, such as
     * connectivity.
     */
    long count();

    /**
     * Checks if a projection with the specified primary key exists in the database.
     *
     * <p>This method determines the presence of a projection by checking if the count of projections with the given primary
     * key is greater than zero. It leverages the {@code selectCount} method, which performs a count operation on the
     * database.</p>
     *
     * @param id the primary key of the projection to check for existence.
     * @return true if a projection with the specified primary key exists, false otherwise.
     * @throws PersistenceException if there is an underlying database issue during the count operation.
     */
    boolean exists(@Nonnull ID id);

    // List based methods.

    /**
     * Retrieves a stream of projections based on their primary keys.
     *
     * <p>This method executes queries in batches, depending on the number of primary keys in the specified ids stream.
     * This optimization aims to reduce the overhead of executing multiple queries and efficiently retrieve projections.
     * The batching strategy enhances performance, particularly when dealing with large sets of primary keys.</p>
     *
     * <p>The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of projections.</p>
     *
     * <p>Note that calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
     * while in use, it must be closed after usage to prevent resource leaks. As the stream is AutoCloseable, it is
     * recommended to use it within a try-with-resources block.</p>
     *
     * @param ids a stream of projection IDs to retrieve from the repository.
     * @return a stream of projections corresponding to the provided primary keys. The order of projections in the stream is
     *         not guaranteed to match the order of ids in the input stream. If an id does not correspond to any projection
     *         in the database, it will simply be skipped, and no corresponding projection will be included in the returned
     *         stream. If the same projection is requested multiple times, it may be included in the stream multiple times
     *         if it is part of a separate batch.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    List<P> select(@Nonnull Iterable<ID> ids);

    // Sequence based methods.

    /**
     * Processes a sequence of all projections of the type supported by this repository using the specified callback.
     * This method retrieves the projections and applies the provided callback to process them, returning the
     * result produced by the callback.
     *
     * <p>This method ensures efficient handling of large data sets by loading projections only as needed.
     * It also manages lifecycle of the underlying resources of the callback sequence, automatically closing those
     * resources after processing to prevent resource leaks.</p>
     *
     * @param callback a {@link KResultCallback} defining how to process the sequence of projections and produce a result.
     * @param <R> the type of result produced by the callback after processing the projections.
     * @return the result produced by the callback's processing of the projection sequence.
     * @throws PersistenceException if the operation fails due to underlying database issues, such as connectivity.
     */
    <R> R selectAll(@Nonnull KResultCallback<P, R> callback);

    /**
     * Processes a sequence of projections corresponding to the provided IDs using the specified callback.
     * This method retrieves projections matching the given IDs and applies the callback to process the results,
     * returning the outcome produced by the callback.
     *
     * <p>This method is designed for efficient data handling by only retrieving specified projections as needed.
     * It also manages lifecycle of the underlying resources of the callback sequence, automatically closing those
     * resources after processing to prevent resource leaks.</p>
     *
     * @param ids a sequence of projection IDs to retrieve from the repository.
     * @param callback a {@link KResultCallback} defining how to process the sequence of projections and produce a result.
     * @param <R> the type of result produced by the callback after processing the projections.
     * @return the result produced by the callback's processing of the projection sequence.
     * @throws PersistenceException if the operation fails due to underlying database issues, such as connectivity.
     */
    <R> R select(@Nonnull Sequence<ID> ids, @Nonnull KResultCallback<P, R> callback);

    /**
     * Retrieves a sequence of projections based on their primary keys.
     *
     * <p>This method executes queries in batches, with the batch size determined by the provided parameter. This
     * optimization aims to reduce the overhead of executing multiple queries and efficiently retrieve projections. The
     * batching strategy enhances performance, particularly when dealing with large sets of primary keys.</p>
     *
     * <p>This method is designed for efficient data handling by only retrieving specified projections as needed.
     * It also manages lifecycle of the underlying resources of the callback sequence, automatically closing those
     * resources after processing to prevent resource leaks.</p>
     *
     * @param ids a sequence of projection IDs to retrieve from the repository.
     * @param batchSize the number of primary keys to include in each batch. This parameter determines the size of the
     *                  batches used to execute the selection operation. A larger batch size can improve performance, especially when
     *                  dealing with large sets of primary keys.
     * @return a sequence of projections corresponding to the provided primary keys. The order of projections in the sequence is
     * not guaranteed to match the order of ids in the input sequence. If an id does not correspond to any projection in the
     * database, it will simply be skipped, and no corresponding projection will be included in the returned sequence. If the
     * same projection is requested multiple times, it may be included in the sequence multiple times if it is part of a
     * separate batch.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    <R> R select(@Nonnull Sequence<ID> ids, int batchSize, @Nonnull KResultCallback<P, R> callback);

    /**
     * Counts the number of projections identified by the provided sequence of IDs using the default batch size.
     *
     * <p>This method calculates the total number of projections that match the provided primary keys. The counting
     * is performed in batches, which helps optimize performance and manage database load when dealing with
     * large sets of IDs.</p>
     *
     * @param ids a sequence of IDs for which to count matching projections.
     * @return the total count of projections matching the provided IDs.
     * @throws PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    long count(@Nonnull Sequence<ID> ids);

    /**
     * Counts the number of projections identified by the provided sequence of IDs, with the counting process divided into
     * batches of the specified size.
     *
     * <p>This method performs the counting operation in batches, specified by the {@code batchSize} parameter. This
     * batching approach is particularly useful for efficiently handling large volumes of IDs, reducing the overhead on
     * the database and improving performance.</p>
     *
     * @param ids a sequence of IDs for which to count matching projections.
     * @param batchSize the size of the batches to use for the counting operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @return the total count of projections matching the provided IDs.
     * @throws PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    long count(@Nonnull Sequence<ID> ids, int batchSize);
}
