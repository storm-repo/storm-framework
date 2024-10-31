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
import kotlin.sequences.Sequence;
import st.orm.NoResultException;
import st.orm.PersistenceException;
import st.orm.kotlin.KResultCallback;
import st.orm.kotlin.template.KQueryBuilder;
import st.orm.repository.Projection;

import java.util.List;

/**
 * Provides a generic interface with CRUD operations for entities.
 */
public interface KProjectionRepository<P extends Record & Projection<ID>, ID> extends KRepository {

    /**
     * Returns the entity model associated with this repository.
     *
     * @return the entity model.
     */
    KModel<P, ID> model();

    // Query builder methods.

    KQueryBuilder<P, P, ID> select();

    KQueryBuilder<P, Long, ID> selectCount();

    // Base methods.

    /**
     * Retrieves an entity based on its primary key.
     *
     * <p>This method performs a lookup in the database, returning the corresponding entity if it exists.</p>
     *
     * @param id the primary key of the entity to retrieve.
     * @return the entity associated with the provided primary key. The returned entity encapsulates all relevant data
     * as mapped by the entity model.
     * @throws NoResultException    if no entity is found matching the given primary key, indicating that there's no
     *                              corresponding data in the database.
     * @throws PersistenceException if the retrieval operation fails due to underlying database issues, such as
     *                              connectivity problems or query execution errors.
     */
    P select(@Nonnull ID id);

    /**
     * Returns the number of entities in the database of the entity type supported by this repository.
     *
     * @return the total number of entities in the database as a long value.
     * @throws PersistenceException if the count operation fails due to underlying database issues, such as
     * connectivity.
     */
    long count();

    /**
     * Checks if an entity with the specified primary key exists in the database.
     *
     * This method determines the presence of an entity by checking if the count of entities with the given primary key
     * is greater than zero. It leverages the {@code selectCount} method, which performs a count operation on the
     * database.
     *
     * @param id the primary key of the entity to check for existence.
     * @return true if an entity with the specified primary key exists, false otherwise.
     * @throws PersistenceException if there is an underlying database issue during the count operation.
     */
    boolean exists(@Nonnull ID id);

    // List based methods.

    /**
     * Retrieves a list of entities based on their primary keys.
     *
     * <p>This method executes a single query to select multiple entities from the database, using a collection of primary
     * keys. It is optimized to efficiently retrieve a batch of entities in one operation, reducing the overhead of
     * executing multiple individual queries.</p>
     *
     * @param ids the primary keys of the entities to retrieve. This iterable collection should contain the unique
     *            identifiers of the desired entities. The collection must not contain null elements.
     * @return a list of entities corresponding to the provided primary keys. The order of entities in the list is not
     * guaranteed to match the order of ids in the input collection. If an id does not correspond to any entity in the
     * database, it will simply be skipped, and no corresponding entity will be included in the returned list.
     * @throws PersistenceException if the selection operation fails. This could be due to issues such as connectivity
     *                              problems, query execution errors, or invalid input parameters.
     */
    List<P> select(@Nonnull Iterable<ID> ids);

    // Sequence based methods.

    /**
     * Returns a stream of all entities of the type supported by this repository. Each element in the stream represents
     * an entity in the database, encapsulating all relevant data as mapped by the entity model.
     *
     * <p>The resulting stream is lazily loaded, meaning that the entities are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of entities.</p>
     *
     * <p>The resulting stream will automatically close the underlying resources when a terminal operation is
     * invoked, such as {@code collect}, {@code forEach}, or {@code toList}, among others. If no terminal operation is
     * invoked, the stream will not close the resources, and it's the responsibility of the caller to ensure that the
     * stream is properly closed to release the resources.</p>
     *
     * @return a stream of all entities of the type supported by this repository.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    <R> R selectAll(@Nonnull KResultCallback<P, R> callback);

    /**
     * Retrieves a stream of entities based on their primary keys.
     *
     * <p>This method executes queries in batches, depending on the number of primary keys in the specified ids stream.
     * This optimization aims to reduce the overhead of executing multiple queries and efficiently retrieve entities.
     * The batching strategy enhances performance, particularly when dealing with large sets of primary keys.</p>
     *
     * <p>The resulting stream is lazily loaded, meaning that the entities are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of entities.</p>
     *
     * <p>The resulting stream will automatically close the underlying resources when a terminal operation is
     * invoked, such as {@code collect}, {@code forEach}, or {@code toList}, among others. If no terminal operation is
     * invoked, the stream will not close the resources, and it's the responsibility of the caller to ensure that the
     * stream is properly closed to release the resources.</p>
     *
     * @return a stream of entities corresponding to the provided primary keys. The order of entities in the stream is
     * not guaranteed to match the order of ids in the input stream. If an id does not correspond to any entity in the
     * database, it will simply be skipped, and no corresponding entity will be included in the returned stream. If the
     * same entity is requested multiple times, it may be included in the stream multiple times if it is part of a
     * separate batch.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    <R> R select(@Nonnull Sequence<ID> ids, @Nonnull KResultCallback<P, R> callback);

    /**
     * Retrieves a stream of entities based on their primary keys.
     *
     * <p>This method executes queries in batches, with the batch size determined by the provided parameter. This
     * optimization aims to reduce the overhead of executing multiple queries and efficiently retrieve entities. The
     * batching strategy enhances performance, particularly when dealing with large sets of primary keys.</p>
     *
     * <p>The resulting stream is lazily loaded, meaning that the entities are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of entities.</p>
     *
     * <p>The resulting stream will automatically close the underlying resources when a terminal operation is
     * invoked, such as {@code collect}, {@code forEach}, or {@code toList}, among others. If no terminal operation is
     * invoked, the stream will not close the resources, and it's the responsibility of the caller to ensure that the
     * stream is properly closed to release the resources.</p>
     *
     * @param sliceSize the number of primary keys to include in each batch. This parameter determines the size of the
     *                  batches used to execute the selection operation. A larger batch size can improve performance, especially when
     *                  dealing with large sets of primary keys.
     * @return a stream of entities corresponding to the provided primary keys. The order of entities in the stream is
     * not guaranteed to match the order of ids in the input stream. If an id does not correspond to any entity in the
     * database, it will simply be skipped, and no corresponding entity will be included in the returned stream. If the
     * same entity is requested multiple times, it may be included in the stream multiple times if it is part of a
     * separate batch.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    <R> R select(@Nonnull Sequence<ID> ids, int sliceSize, @Nonnull KResultCallback<P, R> callback);

    /**
     * Counts the number of entities identified by the provided stream of IDs using the default batch size.
     *
     * This method calculates the total number of entities that match the provided primary keys. The counting
     * is performed in batches, which helps optimize performance and manage database load when dealing with
     * large sets of IDs.
     *
     * @param ids a stream of IDs for which to count matching entities.
     * @return the total count of entities matching the provided IDs.
     * @throws PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    long count(@Nonnull Sequence<ID> ids);

    /**
     * Counts the number of entities identified by the provided stream of IDs, with the counting process divided into
     * batches of the specified size.
     *
     * This method performs the counting operation in batches, specified by the {@code batchSize} parameter. This
     * batching approach is particularly useful for efficiently handling large volumes of IDs, reducing the overhead on
     * the database and improving performance.
     *
     * @param ids a stream of IDs for which to count matching entities.
     * @param batchSize the size of the batches to use for the counting operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @return the total count of entities matching the provided IDs.
     * @throws PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    long count(@Nonnull Sequence<ID> ids, int batchSize);
}
