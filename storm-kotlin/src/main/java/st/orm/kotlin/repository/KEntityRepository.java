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
package st.orm.kotlin.repository;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import kotlin.sequences.Sequence;
import st.orm.Lazy;
import st.orm.NoResultException;
import st.orm.PersistenceException;
import st.orm.kotlin.KBatchCallback;
import st.orm.kotlin.KResultCallback;
import st.orm.kotlin.template.KModel;
import st.orm.kotlin.template.KQueryBuilder;
import st.orm.repository.Entity;

import java.util.List;

/**
 * Provides a generic interface with CRUD operations for entities.
 */
public interface KEntityRepository<E extends Record & Entity<ID>, ID> extends KRepository {

    /**
     * Returns the entity model associated with this repository.
     *
     * @return the entity model.
     */
    KModel<E, ID> model();

    /**
     * Creates a new lazy entity instance with the specified entity.
     *
     * @param entity the entity.
     * @return a lazy entity instance.
     */
    Lazy<E, ID> lazy(@Nullable E entity);

    /**
     * Creates a new lazy entity instance with the specified primary key.
     *
     * @param id the primary key of the entity.
     * @return a lazy entity instance.
     */
    Lazy<E, ID> lazy(@Nullable ID id);

    // Query builder methods.

    /**
     * Creates a new query builder for selecting entities of the type managed by this repository.
     *
     * @return a new query builder for the entity type.
     */
    KQueryBuilder<E, E, ID> select();

    /**
     * Creates a new query builder for the entity type managed by this repository.
     *
     * @return a new query builder for the entity type.
     */
    KQueryBuilder<E, Long, ID> selectCount();

    /**
     * Creates a new query builder for the custom {@code selectType}.
     *
     * @param selectType the result type of the query.
     * @return a new query builder for the custom {@code selectType}.
     * @param <R> the result type of the query.
     */
    <R> KQueryBuilder<E, R, ID> select(@Nonnull Class<R> selectType);

    /**
     * Creates a new query builder for the custom {@code selectType} and custom {@code template} for the select clause.
     *
     * @param selectType the result type of the query.
     * @param template the custom template for the select clause.
     * @return a new query builder for the custom {@code selectType}.
     * @param <R> the result type of the query.
     */
    <R> KQueryBuilder<E, R, ID> select(@Nonnull Class<R> selectType, @Nonnull StringTemplate template);

    /**
     * Creates a new query builder for delete entities of the type managed by this repository.
     *
     * @return a new query builder for the entity type.
     */
    KQueryBuilder<E, ?, ID> delete();

    // Base methods.

    /**
     * Retrieves an entity based on its primary key.
     *
     * <p>This method performs a lookup in the database, returning the corresponding entity if it exists.</p>
     *
     * @param id the primary key of the entity to retrieve.
     * @return the entity associated with the provided primary key. The returned entity encapsulates all relevant data
     * as mapped by the entity model.
     * @throws NoResultException if no entity is found matching the given primary key, indicating that there's no
     *                           corresponding data in the database.
     * @throws PersistenceException if the retrieval operation fails due to underlying database issues, such as
     *                              connectivity problems or query execution errors.
     */
    E select(@Nonnull ID id);

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
     * <p>This method determines the presence of an entity by checking if the count of entities with the given primary
     * key is greater than zero. It leverages the {@code selectCount} method, which performs a count operation on the
     * database.</p>
     *
     * @param id the primary key of the entity to check for existence.
     * @return true if an entity with the specified primary key exists, false otherwise.
     * @throws PersistenceException if there is an underlying database issue during the count operation.
     */
    boolean exists(@Nonnull ID id);

    /**
     * Inserts an entity into the database.
     *
     * <p>This method adds a new entity to the database. It ensures that the entity is persisted according to the defined
     * database constraints and entity model. It's critical for the entity to be fully initialized as per the entity
     * model requirements.</p>
     *
     * @param entity the entity to insert. The entity must satisfy all model constraints.
     * @throws PersistenceException if the insert operation fails. This can happen due to a variety of reasons,
     *                              including database constraints violations, connectivity issues, or if the entity parameter is null.
     */
    void insert(@Nonnull E entity);

    /**
     * Inserts an entity into the database and returns its primary key.
     *
     * <p>This method adds a new entity to the database and upon successful insertion, returns the primary key assigned to
     * the entity when the primary key is generated by the database (e.g., auto-incremented). Otherwise, if the primary
     * key is not generated by the database, the method returns an empty optional.</p>
     *
     * @param entity the entity to insert. The entity must satisfy all model constraints.
     * @return the generated primary key of the successfully inserted entity.
     * @throws PersistenceException if the insert operation fails for reasons such as database constraints violations,
     *                              connectivity issues, or if the entity parameter is null.
     */
    ID insertAndFetchId(@Nonnull E entity);

    /**
     * Inserts a single entity into the database and returns the inserted entity with its current state.
     *
     * <p>This method inserts the provided entity into the database. Upon successful insertion, it returns
     * the entity as it exists in the database after the operation. This ensures that the returned entity
     * includes any modifications applied during the insertion process, such as generated primary keys,
     * default values, or other automatic changes triggered by the database.</p>
     *
     * @param entity the entity to be inserted. The entity must be non-null and contain valid data for insertion
     *               into the database.
     * @return the inserted entity, reflecting its state in the database after insertion. This includes any
     *         database-applied changes such as primary key assignments or default values.
     * @throws PersistenceException if the insertion operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    E insertAndFetch(@Nonnull E entity);

    /**
     * Updates a single entity in the database.
     *
     * <p>This method updates the provided entity in the database, modifying its existing record to reflect the
     * current state of the entity. It is intended for cases where only one entity needs to be updated.</p>
     *
     * @param entity the entity to be updated. The entity must be non-null and contain valid data for updating
     *               its corresponding record in the database.
     * @throws PersistenceException if the update operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    void update(@Nonnull E entity);

    /**
     * Updates a single entity in the database and returns the updated entity with its current state.
     *
     * <p>This method updates the provided entity in the database and, upon successful completion,
     * returns the entity as it exists in the database after the update operation. This ensures that the returned
     * entity reflects any modifications applied during the update process, such as updated timestamps,
     * versioning, or other automatic changes triggered by the database.</p>
     *
     * @param entity the entity to be updated. The entity must be non-null and contain valid data for updating
     *               its corresponding record in the database.
     * @return the updated entity, reflecting its state in the database after the update. This includes any
     *         database-applied changes such as modified timestamps or version numbers.
     * @throws PersistenceException if the update operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    E updateAndFetch(@Nonnull E entity);

    /**
     * Inserts or updates a single entity in the database.
     *
     * <p>This method performs an "upsert" operation on the provided entity. If the entity does not already exist
     * in the database, it will be inserted. If it does exist, it will be updated to reflect the current state of
     * the entity. This approach ensures that the entity is either created or brought up-to-date, depending on
     * its existence in the database.</p>
     *
     * @param entity the entity to be inserted or updated. The entity must be non-null and contain valid data
     *               for insertion or update in the database.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    void upsert(@Nonnull E entity);

    /**
     * Inserts or updates a single entity in the database and returns its ID.
     *
     * <p>This method performs an "upsert" operation on the provided entity. If the entity does not already exist
     * in the database, it will be inserted; if it exists, it will be updated. Upon successful completion,
     * the method returns the ID of the entity as stored in the database. This approach ensures that the entity
     * is either created or brought up-to-date, depending on its existence in the database.</p>
     *
     * @param entity the entity to be inserted or updated. The entity must be non-null and contain valid data
     *               for insertion or update in the database.
     * @return the ID of the upserted entity, reflecting its identifier in the database.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    ID upsertAndFetchId(@Nonnull E entity);

    /**
     * Inserts or updates a single entity in the database and returns the entity with its current state.
     *
     * <p>This method performs an "upsert" operation on the provided entity. If the entity does not already exist
     * in the database, it will be inserted; if it exists, it will be updated. Upon successful completion,
     * the method returns the entity as it exists in the database after the upsert operation. This ensures that
     * the returned entity reflects any modifications applied during the upsert process, such as generated primary keys,
     * updated timestamps, or default values set by the database.</p>
     *
     * @param entity the entity to be inserted or updated. The entity must be non-null and contain valid data
     *               for insertion or update in the database.
     * @return the upserted entity, reflecting its current state in the database. This includes any
     *         database-applied changes, such as primary key assignments, default values, or timestamp updates.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    E upsertAndFetch(@Nonnull E entity);

    /**
     * Deletes an entity from the database based on its primary key.
     *
     * <p>This method removes an existing entity from the database. It is important to ensure that the entity passed for
     * deletion exists in the database.</p>
     *
     * @param id the primary key of the entity to delete.
     * @throws PersistenceException if the deletion operation fails. Reasons for failure might include the entity not
     *                              being found in the database, violations of database constraints, connectivity
     *                              issues, or if the entity parameter is null.
     */
    void delete(@Nonnull ID id);

    /**
     * Deletes an entity from the database.
     *
     * <p>This method removes an existing entity from the database. It is important to ensure that the entity passed for
     * deletion exists in the database and is correctly identified by its primary key.</p>
     *
     * @param entity the entity to delete. The entity must exist in the database and should be correctly identified by
     *               its primary key.
     * @throws PersistenceException if the deletion operation fails. Reasons for failure might include the entity not
     *                              being found in the database, violations of database constraints, connectivity
     *                              issues, or if the entity parameter is null.
     */
    void delete(@Nonnull E entity);

    /**
     * Deletes all entities from the database.
     *
     * <p>This method performs a bulk deletion operation, removing all instances of the entities managed by this
     * repository from the database.</p>
     *
     * @throws PersistenceException if the bulk deletion operation fails. Failure can occur for several reasons,
     *                              including but not limited to database access issues, transaction failures, or
     *                              underlying database constraints that prevent the deletion of certain records.
     */
    void deleteAll();

    // List based methods.

    /**
     * Retrieves a list of entities based on their primary keys.
     *
     * <p>This method retrieves entities matching the provided IDs in batches, consolidating them into a single list.
     * The batch-based retrieval minimizes database overhead, allowing efficient handling of larger collections of IDs.
     * Note that the order of entities in the returned list is not guaranteed to match the order of IDs in the input
     * collection, as the database may not preserve insertion order during retrieval.</p>
     *
     * @param ids the primary keys of the entities to retrieve, represented as an iterable collection.
     * @return a list of entities corresponding to the provided primary keys. Entities are returned without any
     *         guarantee of order alignment with the input list. If an ID does not correspond to any entity in the
     *         database, no corresponding entity will be included in the returned list.
     * @throws PersistenceException if the selection operation fails due to database issues, such as connectivity
     *         problems or invalid input parameters.
     */
    List<E> select(@Nonnull Iterable<ID> ids);

    /**
     * Inserts a collection of entities into the database in batches.
     *
     * <p>This method processes the provided entities in batches, optimizing insertion for larger collections by
     * reducing database overhead. Batch processing helps ensure that even large numbers of entities can be
     * inserted efficiently and minimizes potential memory and performance issues.</p>
     *
     * @param entities an iterable collection of entities to be inserted. Each entity in the collection must
     *                 be non-null and contain valid data for insertion.
     * @throws PersistenceException if the insertion operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    void insert(@Nonnull Iterable<E> entities);

    /**
     * Inserts a collection of entities into the database in batches.
     *
     * <p>This method processes the provided entities in batches, optimizing insertion for larger collections by
     * reducing database overhead. Batch processing helps ensure that even large numbers of entities can be
     * inserted efficiently and minimizes potential memory and performance issues.</p>
     *
     * <p>Upon successful insertion, it returns the primary keys assigned to the entities when the primary keys are
     * generated by the database (e.g., auto-incremented). Otherwise, if the primary keys are not generated by the
     * database, the method returns an empty list.</p>
     *
     * @param entities an iterable collection of entities to be inserted. Each entity in the collection must
     *                 be non-null and contain valid data for insertion.
     * @return the primary keys assigned to the entities when the primary keys are generated by the database,
     * @throws PersistenceException if the insertion operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    List<ID> insertAndFetchIds(@Nonnull Iterable<E> entities);

    /**
     * Inserts a collection of entities into the database in batches.
     *
     * <p>This method processes the provided entities in batches, optimizing insertion for larger collections by
     * reducing database overhead. Batch processing helps ensure that even large numbers of entities can be
     * inserted efficiently and minimizes potential memory and performance issues.</p>
     *
     * <p>Upon successful insertion, it returns the entities that were inserted. The returned entities reflect the
     * state of the entities as they exist in the database after the insertion operation. This ensures that the
     * returned entities include any changes that might have been applied during the insertion process, such as
     * primary key, default values or triggers.</p>
     *
     * @param entities an iterable collection of entities to be inserted. Each entity in the collection must
     *                 be non-null and contain valid data for insertion.
     * @return the entities that were inserted into the database.
     * @throws PersistenceException if the insertion operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    List<E> insertAndFetch(@Nonnull Iterable<E> entities);

    /**
     * Updates a collection of entities in the database in batches.
     *
     * <p>This method processes the provided entities in batches to optimize updating of larger collections,
     * reducing database overhead and improving performance. Batch processing allows efficient handling of
     * bulk updates, minimizing memory and processing costs.</p>
     *
     * @param entities an iterable collection of entities to be updated. Each entity in the collection must
     *                 be non-null and contain valid, up-to-date data for modification in the database.
     * @throws PersistenceException if the update operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    void update(@Nonnull Iterable<E> entities);

    /**
     * Updates a collection of entities in the database in batches and returns a list of the updated entities.
     *
     * <p>This method processes the provided entities in batches, optimizing performance for larger collections by
     * reducing database overhead. Upon successful update, it returns the entities as they exist in the database
     * after the update operation. This ensures that the returned entities reflect any modifications applied during
     * the update process, such as updated timestamps, versioning, or other automatic changes made by the database.</p>
     *
     * @param entities an iterable collection of entities to be updated. Each entity in the collection must be non-null
     *                 and contain valid data for modification in the database.
     * @return a list of entities reflecting their state in the database after the update. The order of entities in
     *         the returned list is not guaranteed to match the order of the input collection.
     * @throws PersistenceException if the update operation fails due to database issues, such as connectivity problems,
     *                              constraints violations, or invalid entity data.
     */
    List<E> updateAndFetch(@Nonnull Iterable<E> entities);

    /**
     * Inserts or updates a collection of entities in the database in batches.
     *
     * <p>This method processes the provided entities in batches, optimizing performance for larger collections by
     * reducing database overhead. For each entity, the method performs an "upsert" operation, meaning it will insert
     * the entity if it does not already exist in the database, or update it if it does. This approach ensures that
     * the entities are either created or brought up-to-date, depending on their existence in the database.</p>
     *
     * @param entities an iterable collection of entities to be inserted or updated. Each entity in the collection must
     *                 be non-null and contain valid data for insertion or update in the database.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity problems,
     *                              constraints violations, or invalid entity data.
     */
    void upsert(@Nonnull Iterable<E> entities);

    /**
     * Inserts or updates a collection of entities in the database in batches and returns a list of their IDs.
     *
     * <p>This method processes the provided entities in batches to optimize performance for larger collections,
     * reducing database overhead. For each entity, the method performs an "upsert" operation, inserting the entity
     * if it does not already exist in the database, or updating it if it does. Upon successful completion,
     * the method returns a list of the IDs of the upserted entities, reflecting their identifiers as stored
     * in the database.</p>
     *
     * @param entities an iterable collection of entities to be inserted or updated. Each entity in the collection
     *                 must be non-null and contain valid data for insertion or update in the database.
     * @return a list of IDs corresponding to the upserted entities. The order of IDs in the returned list
     *         is not guaranteed to match the order of the input collection.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity problems,
     *                              constraints violations, or invalid entity data.
     */
    List<ID> upsertAndFetchIds(@Nonnull Iterable<E> entities);

    /**
     * Inserts or updates a collection of entities in the database in batches and returns a list of the upserted
     * entities.
     *
     * <p>This method processes the provided entities in batches, optimizing performance for larger collections
     * by reducing database overhead. For each entity, it performs an "upsert" operation, inserting the entity if it
     * does not already exist in the database, or updating it if it does. Upon successful completion, it returns
     * the entities as they exist in the database after the operation. This ensures that the returned entities reflect
     * any changes applied during the upsert process, such as generated primary keys, updated timestamps, or default
     * values set by the database.</p>
     *
     * @param entities an iterable collection of entities to be inserted or updated. Each entity in the collection
     *                 must be non-null and contain valid data for insertion or update in the database.
     * @return a list of upserted entities reflecting their current state in the database. The order of entities
     *         in the returned list is not guaranteed to match the order of the input collection.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity problems,
     *                              constraints violations, or invalid entity data.
     */
    List<E> upsertAndFetch(@Nonnull Iterable<E> entities);

    /**
     * Deletes a collection of entities from the database in batches.
     *
     * <p>This method processes the provided entities in batches to optimize performance when handling larger collections,
     * reducing database overhead. For each entity in the collection, the method removes the corresponding record from
     * the database, if it exists. Batch processing ensures efficient handling of deletions, particularly for large data sets.</p>
     *
     * @param entities an iterable collection of entities to be deleted. Each entity in the collection must be non-null
     *                 and represent a valid database record for deletion.
     * @throws PersistenceException if the deletion operation fails due to database issues, such as connectivity problems
     *                              or constraints violations.
     */
    void delete(@Nonnull Iterable<E> entities);

    // Sequence based methods.

    /**
     * Processes a sequence of all entities of the type supported by this repository using the specified callback.
     * This method retrieves the entities and applies the provided callback to process them, returning the
     * result produced by the callback.
     *
     * <p>This method ensures efficient handling of large data sets by loading entities only as needed.
     * It also manages lifecycle of the underlying resources of the callback sequence, automatically closing those
     * resources after processing to prevent resource leaks.</p>
     *
     * @param callback a {@link KResultCallback} defining how to process the sequence of entities and produce a result.
     * @param <R> the type of result produced by the callback after processing the entities.
     * @return the result produced by the callback's processing of the entity sequence.
     * @throws PersistenceException if the operation fails due to underlying database issues, such as connectivity.
     */
    <R> R selectAll(@Nonnull KResultCallback<E, R> callback);

    /**
     * Processes a sequence of entities corresponding to the provided IDs using the specified callback.
     * This method retrieves entities matching the given IDs and applies the callback to process the results,
     * returning the outcome produced by the callback.
     *
     * <p>This method is designed for efficient data handling by only retrieving specified entities as needed.
     * It also manages lifecycle of the underlying resources of the callback sequence, automatically closing those
     * resources after processing to prevent resource leaks.</p>
     *
     * @param ids a sequence of entity IDs to retrieve from the repository.
     * @param callback a {@link KResultCallback} defining how to process the sequence of entities and produce a result.
     * @param <R> the type of result produced by the callback after processing the entities.
     * @return the result produced by the callback's processing of the entity sequence.
     * @throws PersistenceException if the operation fails due to underlying database issues, such as connectivity.
     */
    <R> R select(@Nonnull Sequence<ID> ids, @Nonnull KResultCallback<E, R> callback);

    /**
     * Retrieves a sequence of entities based on their primary keys.
     *
     * <p>This method executes queries in batches, with the batch size determined by the provided parameter. This
     * optimization aims to reduce the overhead of executing multiple queries and efficiently retrieve entities. The
     * batching strategy enhances performance, particularly when dealing with large sets of primary keys.</p>
     *
     * <p>This method is designed for efficient data handling by only retrieving specified entities as needed.
     * It also manages lifecycle of the underlying resources of the callback sequence, automatically closing those
     * resources after processing to prevent resource leaks.</p>
     *
     * @param ids a sequence of entity IDs to retrieve from the repository.
     * @param batchSize the number of primary keys to include in each batch. This parameter determines the size of the
     *                  batches used to execute the selection operation. A larger batch size can improve performance, especially when
     *                  dealing with large sets of primary keys.
     * @return a sequence of entities corresponding to the provided primary keys. The order of entities in the sequence is
     * not guaranteed to match the order of ids in the input sequence. If an id does not correspond to any entity in the
     * database, it will simply be skipped, and no corresponding entity will be included in the returned sequence. If the
     * same entity is requested multiple times, it may be included in the sequence multiple times if it is part of a
     * separate batch.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    <R> R select(@Nonnull Sequence<ID> ids, int batchSize, @Nonnull KResultCallback<E, R> callback);

    /**
     * Counts the number of entities identified by the provided sequence of IDs using the default batch size.
     *
     * <p>This method calculates the total number of entities that match the provided primary keys. The counting
     * is performed in batches, which helps optimize performance and manage database load when dealing with
     * large sets of IDs.</p>
     *
     * @param ids a sequence of IDs for which to count matching entities.
     * @return the total count of entities matching the provided IDs.
     * @throws PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    long count(@Nonnull Sequence<ID> ids);

    /**
     * Counts the number of entities identified by the provided sequence of IDs, with the counting process divided into
     * batches of the specified size.
     *
     * <p>This method performs the counting operation in batches, specified by the {@code batchSize} parameter. This
     * batching approach is particularly useful for efficiently handling large volumes of IDs, reducing the overhead on
     * the database and improving performance.</p>
     *
     * @param ids a sequence of IDs for which to count matching entities.
     * @param batchSize the size of the batches to use for the counting operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @return the total count of entities matching the provided IDs.
     * @throws PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    long count(@Nonnull Sequence<ID> ids, int batchSize);

    /**
     * Inserts entities in a batch mode to optimize performance and reduce database load.
     *
     * <p>For large volumes of entities, this method processes the inserts in multiple batches to ensure efficient
     * handling and minimize the impact on database resources. This structured approach facilitates the management of
     * large-scale insert operations.</p>
     *
     * @param entities the entities to insert. Must not be null.
     * @throws PersistenceException if the insert fails due to database constraints, connectivity issues, or if the
     *                              entities parameter is null.
     */
    void insert(@Nonnull Sequence<E> entities);

    /**
     * Inserts a sequence of entities into the database, with the insertion process divided into batches of the specified
     * size.
     *
     * <p>This method inserts entities provided in a sequence and uses the specified batch size for the insertion
     * operation.  Batching the inserts can greatly enhance performance by minimizing the number of database
     * interactions, especially useful when dealing with large volumes of data.</p>
     *
     * @param entities a sequence of entities to insert. Each entity must not be null and must conform to the model
     *                 constraints.
     * @param batchSize the size of the batches to use for the insertion operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @throws PersistenceException if there is an error during the insertion operation, such as a violation of database
     *                              constraints, connectivity issues, or if any entity in the sequence is null.
     */
    void insert(@Nonnull Sequence<E> entities, int batchSize);

    /**
     * Inserts a sequence of entities into the database using the default batch size and returns a sequence of their
     * generated primary keys.
     *
     * <p>This method facilitates the insertion of entities and fetches their primary keys immediately after insertion.
     * This is particularly useful when the primary keys are generated by the database (e.g., auto-increment fields). It
     * uses the default batch size to optimize the number of database interactions.</p>
     *
     * @param entities a sequence of entities to insert. Each entity must not be null and must conform to the model
     *                constraints.
     * @param callback the callback to process the IDs of the inserted entities in batches.
     * @throws PersistenceException if there is an error during the insertion or key retrieval operation, such as a
     *                              violation of database constraints, connectivity issues, or if any entity in the
     *                              sequence is null.
     */
    void insertAndFetchIds(@Nonnull Sequence<E> entities, @Nonnull KBatchCallback<ID> callback);

    /**
     * Inserts a sequence of entities into the database using the default batch size and returns a sequence of the inserted entities.
     *
     * <p>This method inserts entities into the database and retrieves them immediately after insertion. It is useful
     * for ensuring that the returned entities reflect any database-generated values or defaults. The insertion and
     * retrieval are performed using the default batch size to optimize database performance.</p>
     *
     * @param entities a sequence of entities to insert. Each entity must not be null and must conform to the model
     *                 constraints.
     * @param callback the callback to process the inserted entities, reflecting their new state in the database,
     *                 in batches.
     * @throws PersistenceException if there is an error during the insertion or retrieval operation, such as a
     *                              violation of database constraints, connectivity issues, or if any entity in the
     *                              sequence is null.
     */
    void insertAndFetch(@Nonnull Sequence<E> entities, @Nonnull KBatchCallback<E> callback);

    /**
     * Inserts a sequence of entities into the database with the insertion process divided into batches of the specified size,
     * and returns a sequence of their generated primary keys.
     *
     * <p>This method allows for efficient insertion of a large number of entities by batching them according to the
     * specified batch size. It also fetches the primary keys immediately after insertion, useful for entities with
     * database-generated keys.</p>
     *
     * @param entities a sequence of entities to insert. Each entity must not be null and must conform to the model
     *                 constraints.
     * @param batchSize the size of the batches to use for the insertion operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @param callback the callback to process the IDs of the inserted entities in batches.
     * @throws PersistenceException if there is an error during the insertion or key retrieval operation, such as a
     *                              violation of database constraints, connectivity issues, or if any entity in the
     *                              sequence is null.
     */
    void insertAndFetchIds(@Nonnull Sequence<E> entities, int batchSize, @Nonnull KBatchCallback<ID> callback);

    /**
     * Inserts a sequence of entities into the database with the insertion process divided into batches of the specified size,
     * and returns a sequence of the inserted entities.
     *
     * <p>This method provides an efficient way to insert a large number of entities by batching them according to the
     * specified batch size. It fetches the inserted entities immediately after insertion to ensure that the returned
     * entities reflect any database-generated values or defaults. This is particularly useful when database triggers or
     * default values are involved.</p>
     *
     * @param entities a sequence of entities to insert. Each entity must not be null and must conform to the model
     *                 constraints.
     * @param batchSize the size of the batches to use for the insertion operation. A larger batch size can improve
     *                 performance but may also increase the load on the database.
     * @param callback the callback to process the inserted entities, reflecting their new state in the database,
     *                 in batches.
     * @throws PersistenceException if there is an error during the insertion or retrieval operation, such as a
     *                              violation of database constraints, connectivity issues, or if any entity in the
     *                              sequence is null.
     */
    void insertAndFetch(@Nonnull Sequence<E> entities, int batchSize, @Nonnull KBatchCallback<E> callback);

    /**
     * Updates a sequence of entities in the database using the default batch size.
     *
     * <p>This method updates entities provided in a sequence, optimizing the update process by batching them
     * with a default size. This helps to reduce the number of database operations and can significantly improve
     * performance when updating large numbers of entities.</p>
     *
     * @param entities a sequence of entities to update. Each entity must not be null, must already exist in the database,
     *                 and must conform to the model constraints.
     * @throws PersistenceException if there is an error during the update operation, such as a violation of database
     *                              constraints, connectivity issues, or if any entity in the sequence is null.
     */
    void update(@Nonnull Sequence<E> entities);

    /**
     * Updates a sequence of entities in the database, with the update process divided into batches of the specified size.
     *
     * <p>This method updates entities provided in a sequence and uses the specified batch size for the update operation.
     * Batching the updates can greatly enhance performance by minimizing the number of database interactions,
     * especially useful when dealing with large volumes of data.</p>
     *
     * @param entities a sequence of entities to update. Each entity must not be null, must already exist in the database,
     *                 and must conform to the model constraints.
     * @param batchSize the size of the batches to use for the update operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @throws PersistenceException if there is an error during the update operation, such as a violation of database
     *                              constraints, connectivity issues, or if any entity in the sequence is null.
     */
    void update(@Nonnull Sequence<E> entities, int batchSize);

    /**
     * Updates a sequence of entities in the database using the default batch size and returns a sequence of the updated entities.
     *
     * <p>This method updates entities provided in a sequence, optimizing the update process by batching them with the
     * default size. It fetches the updated entities immediately after updating to ensure that the returned entities
     * reflect any database-generated values or defaults. This is particularly useful when database triggers or default
     * values are involved.</p>
     *
     * @param entities a sequence of entities to update. Each entity must not be null, must already exist in the database,
     *                 and must conform to the model constraints.
     * @param callback the callback to process the updated entities, reflecting their new state in the database,
     *                 in batches.
     * @throws PersistenceException if there is an error during the update or retrieval operation, such as a violation
     *                              of database constraints, connectivity issues, or if any entity in the sequence is
     *                              null.
     */
    void updateAndFetch(@Nonnull Sequence<E> entities, @Nonnull KBatchCallback<E> callback);

    /**
     * Updates a sequence of entities in the database, with the update process divided into batches of the specified size,
     * and returns a sequence of the updated entities.
     *
     * <p>This method updates entities provided in a sequence and uses the specified batch size for the update operation.
     * Batching the updates can greatly enhance performance by minimizing the number of database interactions,
     * especially useful when dealing with large volumes of data. It fetches the updated entities immediately after
     * updating to ensure that the returned entities reflect any database-generated values or defaults.</p>
     *
     * @param entities a sequence of entities to update. Each entity must not be null, must already exist in the database,
     *                 and must conform to the model constraints.
     * @param batchSize the size of the batches to use for the update operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @param callback the callback to process the updated entities, reflecting their new state in the database,
     *                 in batches.
     * @throws PersistenceException if there is an error during the update or retrieval operation, such as a violation
     *                              of database constraints, connectivity issues, or if any entity in the sequence is
     *                              null.
     */
    void updateAndFetch(@Nonnull Sequence<E> entities, int batchSize, @Nonnull KBatchCallback<E> callback);

    /**
     * Inserts or updates a sequence of entities in the database in batches.
     *
     * <p>This method processes the provided sequence of entities in batches, performing an "upsert" operation on each.
     * For each entity, it will be inserted into the database if it does not already exist; if it does exist, it will
     * be updated to reflect the current state of the entity. Batch processing optimizes the performance of the
     * upsert operation for larger data sets by reducing database overhead.</p>
     *
     * @param entities a sequence of entities to be inserted or updated. Each entity in the sequence must be non-null
     *                 and contain valid data for insertion or update in the database.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    void upsert(@Nonnull Sequence<E> entities);

    /**
     * Inserts or updates a sequence of entities in the database in configurable batch sizes.
     *
     * <p>This method processes the provided sequence of entities in batches, performing an "upsert" operation on each.
     * For each entity, it will be inserted if it does not already exist in the database, or updated if it does.
     * The batch size can be configured to control the number of entities processed in each database operation,
     * allowing for optimized performance and memory management based on system requirements.</p>
     *
     * @param entities a sequence of entities to be inserted or updated. Each entity in the sequence must be non-null
     *                 and contain valid data for insertion or update in the database.
     * @param batchSize the number of entities to process in each batch. A larger batch size may improve performance
     *                  but increase memory usage, while a smaller batch size may reduce memory usage but increase
     *                  the number of database operations.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    void upsert(@Nonnull Sequence<E> entities, int batchSize);

    /**
     * Inserts or updates a sequence of entities in the database in batches and retrieves their IDs through a callback.
     *
     * <p>This method processes the provided sequence of entities in batches, performing an "upsert" operation on each entity.
     * For each entity, it will be inserted if it does not already exist in the database or updated if it does. After
     * each batch operation, the IDs of the upserted entities are passed to the provided callback, allowing for
     * customized handling of the IDs as they are retrieved.</p>
     *
     * @param entities a sequence of entities to be inserted or updated. Each entity in the sequence must be non-null and
     *                 contain valid data for insertion or update in the database.
     * @param callback the callback to process the IDs of the upserted entities in batches.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity problems,
     *                              constraints violations, or invalid entity data.
     */
    void upsertAndFetchIds(@Nonnull Sequence<E> entities, @Nonnull KBatchCallback<ID> callback);

    /**
     * Inserts or updates a sequence of entities in the database in batches and retrieves the updated entities through a callback.
     *
     * <p>This method processes the provided sequence of entities in batches, performing an "upsert" operation on each entity.
     * For each entity, it will be inserted if it does not already exist in the database or updated if it does. After
     * each batch operation, the updated entities are passed to the provided callback, allowing for customized handling
     * of the entities as they are retrieved. The entities returned reflect their current state in the database, including
     * any changes such as generated primary keys, timestamps, or default values set by the database during the upsert process.</p>
     *
     * @param entities a sequence of entities to be inserted or updated. Each entity in the sequence must be non-null and
     *                 contain valid data for insertion or update in the database.
     * @param callback the callback to process the upserted entities, reflecting their new state in the database,
     *                 in batches.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity problems,
     *                              constraints violations, or invalid entity data.
     */
    void upsertAndFetch(@Nonnull Sequence<E> entities, @Nonnull KBatchCallback<E> callback);

    /**
     * Inserts or updates a sequence of entities in the database in configurable batch sizes and retrieves their IDs through a callback.
     *
     * <p>This method processes the provided sequence of entities in batches, performing an "upsert" operation on each entity.
     * For each entity, it will be inserted if it does not already exist in the database or updated if it does. The batch size
     * parameter allows control over the number of entities processed in each batch, optimizing memory and performance based
     * on system requirements. After each batch operation, the IDs of the upserted entities are passed to the provided
     * callback, allowing for customized handling of the IDs as they are retrieved.</p>
     *
     * @param entities a sequence of entities to be inserted or updated. Each entity in the sequence must be non-null and contain
     *                 valid data for insertion or update in the database.
     * @param batchSize the number of entities to process in each batch. Adjusting the batch size can optimize performance
     *                  and memory usage, with larger sizes potentially improving performance but using more memory.
     * @param callback the callback to process the IDs of the upserted entities in batches.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity problems,
     *                              constraints violations, or invalid entity data.
     */
    void upsertAndFetchIds(@Nonnull Sequence<E> entities, int batchSize, @Nonnull KBatchCallback<ID> callback);

    /**
     * Inserts or updates a sequence of entities in the database in configurable batch sizes and retrieves the updated entities through a callback.
     *
     * <p>This method processes the provided sequence of entities in batches, performing an "upsert" operation on each entity.
     * For each entity, it will be inserted if it does not already exist in the database or updated if it does. The
     * `batchSize` parameter allows control over the number of entities processed in each batch, optimizing performance
     * and memory usage based on system requirements. After each batch operation, the updated entities are passed to
     * the provided callback, allowing for customized handling of the entities as they are retrieved. The entities
     * returned reflect their current state in the database, including any changes such as generated primary keys,
     * timestamps, or default values applied during the upsert process.</p>
     *
     * @param entities a sequence of entities to be inserted or updated. Each entity in the sequence must be non-null and
     *                 contain valid data for insertion or update in the database.
     * @param batchSize the number of entities to process in each batch. Adjusting the batch size can optimize performance
     *                  and memory usage, with larger sizes potentially improving performance but using more memory.
     * @param callback the callback to process the upserted entities, reflecting their new state in the database,
     *                 in batches.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity problems,
     *                              constraints violations, or invalid entity data.
     */
    void upsertAndFetch(@Nonnull Sequence<E> entities, int batchSize, @Nonnull KBatchCallback<E> callback);

    /**
     * Deletes a sequence of entities from the database in batches.
     *
     * <p>This method processes the provided sequence of entities in batches to optimize performance for larger
     * data sets, reducing database overhead during deletion. For each entity in the sequence, the method removes
     * the corresponding record from the database, if it exists. Batch processing allows efficient handling
     * of deletions, particularly for large collections of entities.</p>
     *
     * @param entities a sequence of entities to be deleted. Each entity in the sequence must be non-null and represent
     *                 a valid database record for deletion.
     * @throws PersistenceException if the deletion operation fails due to database issues, such as connectivity problems
     *                              or constraints violations.
     */
    void delete(@Nonnull Sequence<E> entities);

    /**
     * Deletes a sequence of entities from the database in configurable batch sizes.
     *
     * <p>This method processes the provided sequence of entities in batches, with the size of each batch specified
     * by the `batchSize` parameter. This allows for control over the number of entities deleted in each database
     * operation, optimizing performance and memory usage based on system requirements. For each entity in the
     * sequence, the method removes the corresponding record from the database, if it exists.</p>
     *
     * @param entities a sequence of entities to be deleted. Each entity in the sequence must be non-null and represent
     *                 a valid database record for deletion.
     * @param batchSize the number of entities to process in each batch. Larger batch sizes may improve performance
     *                  but require more memory, while smaller batch sizes may reduce memory usage but increase
     *                  the number of database operations.
     * @throws PersistenceException if the deletion operation fails due to database issues, such as connectivity problems
     *                              or constraints violations.
     */
    void delete(@Nonnull Sequence<E> entities, int batchSize);
}
