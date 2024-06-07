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
package st.orm.spi;

import jakarta.annotation.Nonnull;
import jakarta.persistence.NoResultException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import st.orm.BindVars;
import st.orm.repository.Entity;
import st.orm.repository.EntityModel;
import st.orm.repository.EntityRepository;
import st.orm.repository.Repository;
import st.orm.template.ORMRepositoryTemplate;
import st.orm.template.impl.QueryBuilderImpl;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.Integer.MAX_VALUE;
import static java.util.Objects.requireNonNull;
import static st.orm.template.QueryBuilder.batch;
import static st.orm.template.QueryBuilder.slice;

/**
 */
@SuppressWarnings("DuplicatedCode")
public class EntityRepositoryImpl<E extends Entity<ID>, ID>
        extends QueryBuilderImpl<E, E, ID> implements EntityRepository<E, ID> {

    protected final ORMRepositoryTemplate ORM;
    protected final EntityModel<E, ID> model;

    public EntityRepositoryImpl(@Nonnull ORMRepositoryTemplate orm, @Nonnull EntityModel<E, ID> model) {
        super(orm, model.type(), model.type());
        this.ORM = requireNonNull(orm);
        this.model = requireNonNull(model);
    }

    @Override
    public ORMRepositoryTemplate template() {
        return ORM;
    }

    @Override
    public EntityModel<E, ID> model() {
        return model;
    }


    @Override
    public <T extends Entity<IDT>, IDT> EntityRepository<T, IDT> repository(@Nonnull Class<T> type) {
        return ORM.repository(type);
    }

    @Override
    public <R extends Repository> R repositoryProxy(@Nonnull Class<R> type) {
        return ORM.repositoryProxy(type);
    }

    /**
     * Creates and returns a new instance of {@link BindVars} for use in database queries.
     *
     * @return a new instance of {@link BindVars}, ready to be populated with data for a database query.
     */
    @Override
    public BindVars createBindVars() {
        return ORM.createBindVars();
    }

    /**
     * Retrieves an entity based on its primary key.
     *
     * <p>This method performs a lookup in the database, returning the corresponding entity if it exists.</p>
     *
     * @param id the primary key of the entity to retrieve.
     * @return the entity associated with the provided primary key. The returned entity encapsulates all relevant data
     * as mapped by the entity model.
     * @throws NoResultException if no entity is found matching the given primary key, indicating that there's no
     * corresponding data in the database.
     * @throws PersistenceException if the retrieval operation fails due to underlying database issues, such as
     * connectivity problems or query execution errors.
     */
    @Override
    public E select(@Nonnull ID id) {
        return where(id).singleResult();
    }

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
    @Override
    public boolean exists(@Nonnull ID id) {
        return count(Stream.of(id)) > 0;
    }

    /**
     * Inserts an entity into the database.
     *
     * <p>This method adds a new entity to the database. It ensures that the entity is persisted according to the defined
     * database constraints and entity model. It's critical for the entity to be fully initialized as per the entity
     * model requirements.</p>
     *
     * @param entity the entity to insert. The entity must satisfy all model constraints.
     * @throws PersistenceException if the insert operation fails. This can happen due to a variety of reasons,
     * including database constraints violations, connectivity issues, or if the entity parameter is null.
     */
    @Override
    public void insert(@Nonnull E entity) {
        var query = ORM."""
                INSERT INTO \{model.type()}
                VALUES \{entity}""";
        if (query.executeUpdate() != 1) {
            throw new PersistenceException("Insert failed.");
        }
    }

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
     * connectivity issues, or if the entity parameter is null.
     */
    @Override
    public ID insertAndFetchId(@Nonnull E entity) {
        try (var query = ORM."""
                INSERT INTO \{model.type()}
                VALUES \{entity}""".prepare()) {
            if (query.executeUpdate() != 1) {
                throw new PersistenceException("Insert failed.");
            }
            return singleResult(query.getGeneratedKeys(model.primaryKeyType()));
        }
    }

    /**
     * Inserts an entity into the database and retrieves it back.
     *
     * This method first inserts the entity into the database using the {@code insertAndFetchId} method,
     * which also fetches the primary key of the inserted entity. After insertion, it retrieves the
     * entity by its primary key using the {@code select} method. This ensures that the entity returned
     * includes all current fields from the database, especially useful if there are any triggers or
     * defaults set in the database that modify the data upon insertion.
     *
     * @param entity the entity to insert. Must not be null and must conform to the model constraints.
     * @return the newly inserted entity as it exists in the database after insertion.
     * @throws PersistenceException if there is an error during the insertion or fetch operation, such as
     *         a violation of database constraints or connectivity issues.
     */
    @Override
    public E insertAndFetch(@Nonnull E entity) {
        return select(insertAndFetchId(entity));
    }

    /**
     * Updates an existing entity in the database.
     *
     * <p>This method applies modifications to an existing entity within the database. It's essential
     * that the entity passed to this method already exists in the database and is properly identified
     * by its primary key. The method ensures that the entity's state is synchronized with the database,
     * reflecting any changes made to the entity's fields, according to the entity model constraints.</p>
     *
     * @param entity the entity to update. This entity must already exist in the database and must satisfy all model
     * constraints. The entity's state is updated in the database, and it's critical that the entity includes an
     * identifier (primary key) that matches an existing record.
     * @throws PersistenceException if the update operation fails. This can occur for several reasons, such as if the
     * entity does not exist in the database, if there are database constraints violations, connectivity issues, or if
     * the entity parameter is null.
     */
    @Override
    public void update(@Nonnull E entity) {
        var query = ORM."""
                UPDATE \{model.type()}
                SET \{entity}
                WHERE \{entity}""";
        int result = query.executeUpdate();
        if (query.isVersionAware() && result == 0) {
            throw new OptimisticLockException("Update failed due to optimistic lock.");
        } else if (result != 1) {
            throw new PersistenceException("Update failed.");
        }
    }

    /**
     * Updates an existing entity in the database and retrieves the updated entity.
     *
     * This method first updates the entity in the database using the {@code update} method,
     * ensuring that any changes to the entity's fields are persisted. Following the update,
     * it retrieves the entity by its primary key using the {@code select} method to ensure the
     * returned entity reflects all current data from the database, including any changes that
     * might have been applied during the update process (e.g., through triggers).
     *
     * @param entity the entity to update. This entity must not be null, must already exist in the database,
     *        and must conform to the model constraints.
     * @return the updated entity as it exists in the database after the update.
     * @throws PersistenceException if there is an error during the update or retrieval operation, such as
     *         a violation of database constraints, connectivity issues, or if the entity does not exist.
     */
    @Override
    public E updateAndFetch(@Nonnull E entity) {
        update(entity);
        return select(entity.id());
    }

    private PersistenceException upsertNotAvailable() {
        return new PersistenceException("Upsert is not available for the default implementation.");
    }

    /**
     * Performs an upsert operation for a single entity into the database. If the entity already exists
     * (as determined by its primary key or unique constraints), it is updated; otherwise, a new entity is inserted.
     *
     * This method handles duplicates by executing a database-specific upsert operation, which may involve SQL
     * extensions or functions depending on the underlying database implementation.
     *
     * @param entity the entity to insert or update. Must not be null and must conform to the model constraints.
     * @throws PersistenceException if there is an error during the upsert operation, such as a violation of database
     *                              constraints, connectivity issues, or if the entity is null.
     */
    @Override
    public void upsert(@Nonnull E entity) {
        throw upsertNotAvailable();
    }

    /**
     * Performs an upsert operation for a single entity and fetches the primary key of the inserted or updated entity.
     * This is typically used when the primary keys are generated by the database (e.g., auto-increment IDs).
     *
     * The operation ensures that if an entity with a matching key exists, it is updated; otherwise, a new entity is
     * inserted. The primary key of the entity is then retrieved.
     *
     * @param entity the entity to insert or update. Must not be null and must conform to the model constraints.
     * @return the primary key of the upserted entity.
     * @throws PersistenceException if there is an error during the upsert or key retrieval operation, such as a
     *                              violation of database constraints, connectivity issues, or if the entity is null.
     */
    @Override
    public ID upsertAndFetchId(@Nonnull E entity) {
        throw upsertNotAvailable();
    }

    /**
     * Performs an upsert operation for a single entity and fetches the entity as stored in the database after the
     * operation. This method is useful to obtain the complete entity including any fields that may be generated or
     * modified by the database.
     *
     * @param entity the entity to insert or update. Must not be null and must conform to the model constraints.
     * @return the upserted entity, as retrieved from the database.
     * @throws PersistenceException if there is an error during the upsert or retrieval operation, such as a violation
     *                              of database constraints, connectivity issues, or if the entity is null.
     */
    @Override
    public E upsertAndFetch(@Nonnull E entity) {
        throw upsertNotAvailable();
    }

    /**
     * Deletes an entity from the database.
     *
     * <p>This method removes an existing entity from the database. It is important to ensure that the entity passed for
     * deletion exists in the database and is correctly identified by its primary key.</p>
     *
     * @param entity the entity to delete. The entity must exist in the database and should be correctly identified by
     * its primary key.
     * @throws PersistenceException if the deletion operation fails. Reasons for failure might include the entity not
     * being found in the database, violations of database constraints, connectivity issues, or if the entity parameter
     * is null.
     */
    @Override
    public void delete(@Nonnull E entity) {
        var query = ORM."""
                DELETE FROM \{model.type()}
                WHERE \{entity}""";
        if (query.executeUpdate() != 1) {
            throw new PersistenceException("Delete failed.");
        }
    }

    /**
     * Deletes all entities from the database.
     *
     * <p>This method performs a bulk deletion operation, removing all instances of the entities managed by this
     * repository from the database.</p>
     *
     * @throws PersistenceException if the bulk deletion operation fails. Failure can occur for several reasons,
     * including but not limited to database access issues, transaction failures, or underlying database constraints
     * that prevent the deletion of certain records.
     */
    @Override
    public void deleteAll() {
        ORM."""
                DELETE FROM \{model.type()}"""
            .executeUpdate();
    }

    // List based methods. These methods operate in a single batch.

    /**
     * Retrieves a list of entities based on their primary keys.
     *
     * <p>This method executes a single query to select multiple entities from the database, using a collection of primary
     * keys. It is optimized to efficiently retrieve a batch of entities in one operation, reducing the overhead of
     * executing multiple individual queries.</p>
     *
     * @param ids the primary keys of the entities to retrieve. This iterable collection should contain the unique
     * identifiers of the desired entities. The collection must not contain null elements.
     * @return a list of entities corresponding to the provided primary keys. The order of entities in the list is not
     * guaranteed to match the order of ids in the input collection. If an id does not correspond to any entity in the
     * database, it will simply be skipped, and no corresponding entity will be included in the returned list.
     * @throws PersistenceException if the selection operation fails. This could be due to issues such as connectivity
     * problems, query execution errors, or invalid input parameters.
     */
    @Override
    public List<E> select(@Nonnull Iterable<ID> ids) {
        return select(toStream(ids), MAX_VALUE).toList();
    }

    /**
     * Inserts multiple entities in a single batch operation to optimize database performance and reduce load.
     *
     * <p>This method is designed to efficiently insert a collection of entities into the database by bundling them
     * into a single batch. This approach minimizes the number of database operations, significantly improving
     * performance and reducing the impact on database resources compared to inserting each entity individually.
     * It's particularly useful for bulk insertion tasks where numerous entities need to be persisted simultaneously.</p>
     *
     * @param entities the collection of entities to insert. Each entity in this collection must be fully initialized
     * and valid according to the entity model's constraints. The collection must not contain null elements.
     * @throws PersistenceException if the batch insert operation fails. This can be due to various reasons such as
     * violation of database constraints, connectivity issues, or invalid input parameters.
     */
    @Override
    public void insert(@Nonnull Iterable<E> entities) {
        insert(toStream(entities), MAX_VALUE);
    }

    /**
     * Inserts multiple entities in a single batch operation and returns their generated primary keys.
     *
     * <p>This method efficiently inserts a collection of entities into the database as a single batch, optimizing
     * performance and minimizing database load. Upon successful insertion, it returns the primary keys assigned to
     * the entities when the primary keys are generated by the database (e.g., auto-incremented).Otherwise, if the
     * primary keys are not generated by the database, the method returns an empty list.</p>
     *
     * @param entities the collection of entities to be inserted. Each entity must be fully initialized and adhere
     * to the entity model's constraints. The collection must not contain null elements.
     * @return the generated primary keys of the successfully inserted entities, or an empty list if the primary keys
     * are not generated by the database.
     * @throws PersistenceException if the batch insert operation fails. Failure can result from various issues,
     * including database constraints violations, connectivity problems, or invalid input parameters.
     */
    @Override
    public List<ID> insertAndFetchIds(@Nonnull Iterable<E> entities) {
        return insertAndFetchIds(toStream(entities), MAX_VALUE).toList();
    }

    /**
     * Inserts multiple entities into the database and retrieves them back as a list.
     *
     * This method first inserts a collection of entities into the database using the {@code insertAndFetchIds} method,
     * which also fetches the primary keys of the inserted entities. After insertion, it retrieves the entities by their
     * primary keys using the {@code select} method. This ensures that the returned entities include all current fields
     * from the database, particularly useful if there are any database triggers or defaults that modify the data upon insertion.
     *
     * @param entities an iterable collection of entities to insert. Each entity must not be null and must conform to the model constraints.
     * @return a list of newly inserted entities as they exist in the database after insertion.
     * @throws PersistenceException if there is an error during the insertion or retrieval operation, such as
     *         a violation of database constraints, connectivity issues, or if any entity in the collection is null.
     */
    @Override
    public List<E> insertAndFetch(@Nonnull Iterable<E> entities) {
        return select(insertAndFetchIds(entities));
    }

    /**
     * Updates multiple entities in a single batch operation to optimize performance and reduce database load.
     *
     * <p>This method is designed to perform a bulk update on a collection of entities, applying changes to all
     * provided entities in one transaction. This batch approach significantly improves efficiency and reduces
     * the impact on database resources compared to updating each entity individually.</p>
     *
     * @param entities the collection of entities to update. Each entity in this collection must already exist in the
     * database and be fully initialized with the desired state changes. The entities must adhere to the entity model's
     * constraints and should be correctly identified by their primary keys. The collection must not contain null
     * elements.
     * @throws PersistenceException if the batch update operation fails. This can be due to a variety of reasons, such
     * as violation of database constraints, connectivity issues, or invalid input parameters.
     */
    @Override
    public void update(@Nonnull Iterable<E> entities) {
        update(toStream(entities), MAX_VALUE);
    }

    /**
     * Updates multiple entities in the database and retrieves the updated entities as a list.
     *
     * This method first updates a collection of entities in the database using the {@code update} method,
     * ensuring that any modifications to the entities' fields are persisted. Following the update,
     * it retrieves the entities by their primary keys using the {@code select} method to ensure the
     * returned entities reflect all current data from the database, including any changes that
     * might have been applied during the update process (e.g., through triggers).
     *
     * @param entities an iterable collection of entities to update. Each entity must not be null, must already exist in the database,
     *        and must conform to the model constraints.
     * @return a list of updated entities as they exist in the database after the update.
     * @throws PersistenceException if there is an error during the update or retrieval operation, such as
     *         a violation of database constraints, connectivity issues, or if any entity does not exist.
     */
    @Override
    public List<E> updateAndFetch(@Nonnull Iterable<E> entities) {
        update(entities);
        return select(StreamSupport.stream(entities.spliterator(), false).map(Entity::id).toList());
    }

    /**
     * Performs an upsert operation for multiple entities provided as an iterable in a single batch.
     *
     * @param entities an iterable of entities to upsert. Each entity must not be null and must conform to the model
     *                constraints.
     * @throws PersistenceException if there is an error during the upsert operation, such as a violation of database
     *                              constraints, connectivity issues, or if any entity in the iterable is null.
     */
    @Override
    public void upsert(@Nonnull Iterable<E> entities) {
        throw upsertNotAvailable();
    }

    /**
     * Performs an upsert operation for multiple entities in a single batch and fetches their primary keys.
     *
     * @param entities an iterable of entities to upsert. Each entity must not be null and must conform to the model
     *                 constraints.
     * @return a list of primary keys of the upserted entities.
     * @throws PersistenceException if there is an error during the upsert or key retrieval operation, such as a
     *                              violation of database constraints, connectivity issues, or if any entity in the
     *                              iterable is null.
     */
    @Override
    public List<ID> upsertAndFetchIds(@Nonnull Iterable<E> entities) {
        throw upsertNotAvailable();
    }

    /**
     * Performs an upsert operation for multiple entities in a single batch and fetches the entities as stored in
     * the database after the upsert.
     *
     * @param entities an iterable of entities to upsert. Each entity must not be null and must conform to the model
     *                constraints.
     * @return a list of the upserted entities, as retrieved from the database.
     * @throws PersistenceException if there is an error during the upsert or retrieval operation, such as a violation
     *                              of database constraints, connectivity issues, or if any entity in the iterable is
     *                              null.
     */
    @Override
    public List<E> upsertAndFetch(@Nonnull Iterable<E> entities) {
        throw upsertNotAvailable();
    }

    /**
     * Deletes multiple entities in a single batch operation to optimize performance and reduce database load.
     *
     * <p>This method enables the efficient removal of a collection of entities from the database by processing
     * them as a single batch. Utilizing this batch deletion approach minimizes the number of individual transactions,
     * thereby reducing the impact on database resources and improving overall performance.</p>
     *
     * @param entities the collection of entities to be deleted. Each entity in this collection must exist in the
     * database and be correctly identified by its primary key. The collection must not contain null elements.
     * @throws PersistenceException if the batch delete operation fails. Reasons for failure might include the entities
     * not being found in the database, violations of database constraints, connectivity issues, or invalid input
     * parameters.
     */
    @Override
    public void delete(@Nonnull Iterable<E> entities) {
        delete(toStream(entities), MAX_VALUE);
    }

    // Stream based methods. These methods operate in multiple batches.

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
     * connectivity.
     */
    @Override
    public Stream<E> selectAll() {
        return stream();
    }

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
     * connectivity.
     */
    @Override
    public Stream<E> select(@Nonnull Stream<ID> ids) {
        return select(ids, DEFAULT_BATCH_SIZE);
    }

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
     * @param batchSize the number of primary keys to include in each batch. This parameter determines the size of the
     * batches used to execute the selection operation. A larger batch size can improve performance, especially when
     * dealing with large sets of primary keys.
     * @return a stream of entities corresponding to the provided primary keys. The order of entities in the stream is
     * not guaranteed to match the order of ids in the input stream. If an id does not correspond to any entity in the
     * database, it will simply be skipped, and no corresponding entity will be included in the returned stream. If the
     * same entity is requested multiple times, it may be included in the stream multiple times if it is part of a
     * separate batch.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     * connectivity.
     */
    @Override
    public Stream<E> select(@Nonnull Stream<ID> ids, int batchSize) {
        return batch(ids, batchSize, batch -> where(batch).stream());
    }

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
    @Override
    public long count(@Nonnull Stream<ID> ids) {
        return count(ids, DEFAULT_BATCH_SIZE);
    }

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
    @Override
    public long count(@Nonnull Stream<ID> ids, int batchSize) {
        try (ids) {  // Close the stream when done.
            return slice(ids, batchSize)
                    .mapToLong(slice -> selectTemplate(Long.class)."COUNT(*)"
                            .where(slice)
                            .singleResult())
                    .sum();
        }
    }

    /**
     * Inserts a stream of entities into the database using the default batch size.
     *
     * This method inserts entities provided in a stream, optimizing the insertion process by batching them
     * with a default size. This helps to reduce the number of database operations and can significantly improve
     * performance when inserting large numbers of entities.
     *
     * @param entities a stream of entities to insert. Each entity must not be null and must conform to the model
     *                 constraints.
     * @throws PersistenceException if there is an error during the insertion operation, such as a violation of database
     *                              constraints, connectivity issues, or if any entity in the stream is null.
     */
    @Override
    public void insert(@Nonnull Stream<E> entities) {
        insert(entities, DEFAULT_BATCH_SIZE);
    }

    /**
     * Inserts a stream of entities into the database, with the insertion process divided into batches of the specified
     * size.
     *
     * This method inserts entities provided in a stream and uses the specified batch size for the insertion operation.
     * Batching the inserts can greatly enhance performance by minimizing the number of database interactions,
     * especially useful when dealing with large volumes of data.
     *
     * @param entities a stream of entities to insert. Each entity must not be null and must conform to the model
     *                 constraints.
     * @param batchSize the size of the batches to use for the insertion operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @throws PersistenceException if there is an error during the insertion operation, such as a violation of database
     *                              constraints, connectivity issues, or if any entity in the stream is null.
     */
    @Override
    public void insert(@Nonnull Stream<E> entities, int batchSize) {
        var bindVars = ORM.createBindVars();
        try (entities;   // Close the stream when done.
             var query = ORM."""
            INSERT INTO \{model.type()}
            VALUES \{bindVars}""".prepare()) {
            slice(entities, batchSize)
                    .forEach(slice -> {
                        slice.stream().map(Record.class::cast).forEach(query::addBatch);
                        query.executeBatch();
                    });
        }
    }

    /**
     * Inserts a stream of entities into the database using the default batch size and returns a stream of their generated primary keys.
     *
     * This method facilitates the insertion of entities and fetches their primary keys immediately after insertion. This is
     * particularly useful when the primary keys are generated by the database (e.g., auto-increment fields). It uses the default
     * batch size to optimize the number of database interactions.
     *
     * @param entities a stream of entities to insert. Each entity must not be null and must conform to the model constraints.
     * @return a stream of primary keys for the inserted entities.
     * @throws PersistenceException if there is an error during the insertion or key retrieval operation, such as a violation
     *         of database constraints, connectivity issues, or if any entity in the stream is null.
     */
    @Override
    public Stream<ID> insertAndFetchIds(@Nonnull Stream<E> entities) {
        return insertAndFetchIds(entities, DEFAULT_BATCH_SIZE);
    }

    /**
     * Inserts a stream of entities into the database using the default batch size and returns a stream of the inserted entities.
     *
     * This method inserts entities into the database and retrieves them immediately after insertion. It is useful for ensuring
     * that the returned entities reflect any database-generated values or defaults. The insertion and retrieval are performed using
     * the default batch size to optimize database performance.
     *
     * @param entities a stream of entities to insert. Each entity must not be null and must conform to the model constraints.
     * @return a stream of the inserted entities as they are stored in the database.
     * @throws PersistenceException if there is an error during the insertion or retrieval operation, such as a violation of
     *         database constraints, connectivity issues, or if any entity in the stream is null.
     */
    @Override
    public Stream<E> insertAndFetch(@Nonnull Stream<E> entities) {
        return select(insertAndFetchIds(entities));
    }

    /**
     * Inserts a stream of entities into the database with the insertion process divided into batches of the specified size,
     * and returns a stream of their generated primary keys.
     *
     * This method allows for efficient insertion of a large number of entities by batching them according to the specified batch
     * size. It also fetches the primary keys immediately after insertion, useful for entities with database-generated keys.
     *
     * @param entities a stream of entities to insert. Each entity must not be null and must conform to the model constraints.
     * @param batchSize the size of the batches to use for the insertion operation. A larger batch size can improve performance
     *                  but may also increase the load on the database.
     * @return a stream of primary keys for the inserted entities.
     * @throws PersistenceException if there is an error during the insertion or key retrieval operation, such as a violation
     *         of database constraints, connectivity issues, or if any entity in the stream is null.
     */
    @Override
    public Stream<ID> insertAndFetchIds(@Nonnull Stream<E> entities, int batchSize) {
        var bindVars = ORM.createBindVars();
        var query = ORM."""
            INSERT INTO \{model.type()}
            VALUES \{bindVars}""".prepare();
        return batch(entities, batchSize, batch -> {
            batch.stream().map(Record.class::cast).forEach(query::addBatch);
            query.executeBatch();
            return query.getGeneratedKeys(model.primaryKeyType());
        }).onClose(query::close);
    }

    /**
     * Inserts a stream of entities into the database with the insertion process divided into batches of the specified size,
     * and returns a stream of the inserted entities.
     *
     * This method provides an efficient way to insert a large number of entities by batching them according to the specified
     * batch size. It fetches the inserted entities immediately after insertion to ensure that the returned entities reflect
     * any database-generated values or defaults. This is particularly useful when database triggers or default values are
     * involved.
     *
     * @param entities a stream of entities to insert. Each entity must not be null and must conform to the model constraints.
     * @param batchSize the size of the batches to use for the insertion operation. A larger batch size can improve performance
     *                  but may also increase the load on the database.
     * @return a stream of the inserted entities as they are stored in the database.
     * @throws PersistenceException if there is an error during the insertion or retrieval operation, such as a violation of
     *         database constraints, connectivity issues, or if any entity in the stream is null.
     */
    @Override
    public Stream<E> insertAndFetch(@Nonnull Stream<E> entities, int batchSize) {
        return select(insertAndFetchIds(entities, batchSize), batchSize);
    }

    /**
     * Updates a stream of entities in the database using the default batch size.
     *
     * This method updates entities provided in a stream, optimizing the update process by batching them
     * with a default size. This helps to reduce the number of database operations and can significantly improve
     * performance when updating large numbers of entities.
     *
     * @param entities a stream of entities to update. Each entity must not be null, must already exist in the database,
     *                 and must conform to the model constraints.
     * @throws PersistenceException if there is an error during the update operation, such as a violation of database constraints,
     *         connectivity issues, or if any entity in the stream is null.
     */
    @Override
    public void update(@Nonnull Stream<E> entities) {
        update(entities, DEFAULT_BATCH_SIZE);
    }

    /**
     * Updates a stream of entities in the database, with the update process divided into batches of the specified size.
     *
     * This method updates entities provided in a stream and uses the specified batch size for the update operation.
     * Batching the updates can greatly enhance performance by minimizing the number of database interactions, especially
     * useful when dealing with large volumes of data.
     *
     * @param entities a stream of entities to update. Each entity must not be null, must already exist in the database,
     *                 and must conform to the model constraints.
     * @param batchSize the size of the batches to use for the update operation. A larger batch size can improve performance
     *                  but may also increase the load on the database.
     * @throws PersistenceException if there is an error during the update operation, such as a violation of database constraints,
     *         connectivity issues, or if any entity in the stream is null.
     */
    @Override
    public void update(@Nonnull Stream<E> entities, int batchSize) {
        var bindVars = ORM.createBindVars();
        try (entities;  // Close the stream when done.
             var query = ORM."""
            UPDATE \{model.type()}
            SET \{bindVars}
            WHERE \{bindVars}""".prepare()) {
            slice(entities, batchSize)
                    .forEach(slice -> {
                        slice.stream().map(Record.class::cast).forEach(query::addBatch);
                        int[] result = query.executeBatch();
                        if (query.isVersionAware() && IntStream.of(result).anyMatch(r -> r == 0)) {
                            throw new OptimisticLockException("Update failed due to optimistic lock.");
                        } else if (IntStream.of(result).anyMatch(r -> r != 1)) {
                            throw new PersistenceException("Update failed.");
                        }
                    });
        }
    }

    /**
     * Updates a stream of entities in the database using the default batch size and returns a stream of the updated entities.
     *
     * This method updates entities provided in a stream, optimizing the update process by batching them with the default size.
     * It fetches the updated entities immediately after updating to ensure that the returned entities reflect any database-generated
     * values or defaults. This is particularly useful when database triggers or default values are involved.
     *
     * @param entities a stream of entities to update. Each entity must not be null, must already exist in the database,
     *                 and must conform to the model constraints.
     * @return a stream of the updated entities as they are stored in the database.
     * @throws PersistenceException if there is an error during the update or retrieval operation, such as a violation of
     *         database constraints, connectivity issues, or if any entity in the stream is null.
     */
    @Override
    public Stream<E> updateAndFetch(@Nonnull Stream<E> entities) {
        return updateAndFetch(entities, DEFAULT_BATCH_SIZE);
    }

    /**
     * Updates a stream of entities in the database, with the update process divided into batches of the specified size,
     * and returns a stream of the updated entities.
     *
     * This method updates entities provided in a stream and uses the specified batch size for the update operation.
     * Batching the updates can greatly enhance performance by minimizing the number of database interactions, especially
     * useful when dealing with large volumes of data. It fetches the updated entities immediately after updating to ensure
     * that the returned entities reflect any database-generated values or defaults.
     *
     * @param entities a stream of entities to update. Each entity must not be null, must already exist in the database,
     *                 and must conform to the model constraints.
     * @param batchSize the size of the batches to use for the update operation. A larger batch size can improve performance
     *                  but may also increase the load on the database.
     * @return a stream of the updated entities as they are stored in the database.
     * @throws PersistenceException if there is an error during the update or retrieval operation, such as a violation of
     *         database constraints, connectivity issues, or if any entity in the stream is null.
     */
    @Override
    public Stream<E> updateAndFetch(@Nonnull Stream<E> entities, int batchSize) {
        var bindVars = ORM.createBindVars();
        var query = ORM."""
                UPDATE \{model.type()}
                SET \{bindVars}
                WHERE \{bindVars}""".prepare();
        return batch(entities, batchSize, batch -> {
            batch.stream().map(Record.class::cast).forEach(query::addBatch);
            int[] result = query.executeBatch();
            if (query.isVersionAware() && IntStream.of(result).anyMatch(r -> r == 0)) {
                throw new OptimisticLockException("Update failed due to optimistic lock.");
            } else if (IntStream.of(result).anyMatch(r -> r != 1)) {
                throw new PersistenceException("Update failed.");
            }
            return select(query.getGeneratedKeys(model.primaryKeyType()));
        }).onClose(query::close);
    }

    /**
     * Performs an upsert operation for multiple entities provided as a stream. This method processes the entities with
     * the maximum batch size, optimizing the operation for large datasets by reducing the load on the database.
     *
     * @param entities a stream of entities to upsert. Each entity must not be null and must conform to the model
     *                constraints.
     * @throws PersistenceException if there is an error during the upsert operation, such as a violation of database
     *                              constraints, connectivity issues, or if any entity in the stream is null.
     */
    @Override
    public void upsert(@Nonnull Stream<E> entities) {
        throw upsertNotAvailable();
    }

    /**
     * Performs an upsert operation for multiple entities provided as a stream, with the operation divided into batches
     * of the specified size. This method is optimized for handling large datasets efficiently by reducing the number
     * of database transactions and spreading the load over multiple batches.
     *
     * Each entity in the stream is either inserted or updated based on whether it already exists in the database, as
     * determined by its primary key or unique constraints. This upsert operation is facilitated by a database-specific
     * command that handles duplicate keys by updating existing records.
     *
     * @param entities a stream of entities to upsert. Each entity must not be null and must conform to the model
     *                 constraints.
     * @param batchSize the size of the batches to use for the upsert operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @throws PersistenceException if there is an error during the upsert operation, such as a violation of database
     *                              constraints, connectivity issues, or if any entity in the stream is null.
     */
    @Override
    public void upsert(@Nonnull Stream<E> entities, int batchSize) {
        throw upsertNotAvailable();
    }

    /**
     * Performs an upsert operation for multiple entities provided as a stream and returns a stream of their generated
     * primary keys. This method uses the maximum batch size to process the entities efficiently, suitable for handling
     * large datasets.
     *
     * @param entities a stream of entities to upsert. Each entity must not be null and must conform to the model
     *                 constraints.
     * @return a stream of primary keys for the upserted entities.
     * @throws PersistenceException if there is an error during the upsert or key retrieval operation, such as a
     *                              violation of database constraints, connectivity issues, or if any entity in the
     *                              stream is null.
     */
    @Override
    public Stream<ID> upsertAndFetchIds(@Nonnull Stream<E> entities) {
        throw upsertNotAvailable();
    }

    /**
     * Performs an upsert operation for multiple entities provided as a stream and returns a stream of the entities as
     * stored in the database. This method uses the maximum batch size to optimize the database interaction, suitable
     * for handling large volumes of entities.
     *
     * @param entities a stream of entities to upsert. Each entity must not be null and must conform to the model
     *                 constraints.
     * @return a stream of the upserted entities, reflecting their current state in the database.
     * @throws PersistenceException if there is an error during the upsert or retrieval operation, such as a violation
     *                              of database constraints, connectivity issues, or if any entity in the stream is
     *                              null.
     */
    @Override
    public Stream<E> upsertAndFetch(@Nonnull Stream<E> entities) {
        throw upsertNotAvailable();
    }

    /**
     * Performs an upsert operation for multiple entities provided as a stream, with the operation divided into batches
     * of the specified size, and returns a stream of their generated primary keys. This method is optimized for
     * efficiently handling large datasets by reducing the load on the database and enhancing performance through
     * batching.
     *
     * @param entities a stream of entities to upsert. Each entity must not be null and must conform to the model
     *                 constraints.
     * @param batchSize the size of the batches to use for the upsert operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @return a stream of primary keys for the upserted entities.
     * @throws PersistenceException if there is an error during the upsert or key retrieval operation, such as a
     *                              violation of database constraints, connectivity issues, or if any entity in the
     *                              stream is null.
     */
    @Override
    public Stream<ID> upsertAndFetchIds(@Nonnull Stream<E> entities, int batchSize) {
        throw upsertNotAvailable();
    }

    /**
     * Performs an upsert operation for multiple entities provided as a stream, with the operation divided into batches
     * of the specified size, and returns a stream of the entities as stored in the database after the upsert. This
     * method is designed to optimize large-scale upsert operations by efficiently handling large datasets through batch
     * processing.
     *
     * The method first upserts the entities and retrieves their primary keys. It then fetches the entities from the
     * database to ensure that the returned stream reflects their current state, including any database-generated values
     * or modifications resulting from the upsert operation.
     *
     * @param entities a stream of entities to upsert. Each entity must not be null and must conform to the model
     *                 constraints.
     * @param batchSize the size of the batches to use for the upsert operation. Using a larger batch size can improve
     *                  performance by reducing the number of database interactions but may also increase the load on
     *                  the database.
     * @return a stream of the upserted entities, reflecting their current state in the database.
     * @throws PersistenceException if there is an error during the upsert or retrieval operation, such as a violation
     *                              of database constraints, connectivity issues, or if any entity in the stream is
     *                              null.
     */
    @Override
    public Stream<E> upsertAndFetch(@Nonnull Stream<E> entities, int batchSize) {
        throw upsertNotAvailable();
    }

    /**
     * Deletes a stream of entities from the database using the default batch size.
     *
     * This method deletes entities provided in a stream, optimizing the deletion process by batching them
     * with the default size. This helps to reduce the number of database operations and can significantly improve
     * performance when deleting large numbers of entities.
     *
     * @param entities a stream of entities to delete. Each entity must not be null, must already exist in the database,
     *                 and must conform to the model constraints.
     * @throws PersistenceException if there is an error during the deletion operation, such as a violation of database constraints,
     *         connectivity issues, or if any entity in the stream is null.
     */
    @Override
    public void delete(@Nonnull Stream<E> entities) {
        delete(entities, DEFAULT_BATCH_SIZE);
    }

    /**
     * Deletes a stream of entities from the database, with the deletion process divided into batches of the specified size.
     *
     * This method deletes entities provided in a stream and uses the specified batch size for the deletion operation.
     * Batching the deletions can greatly enhance performance by minimizing the number of database interactions, especially
     * useful when dealing with large volumes of data.
     *
     * @param entities a stream of entities to delete. Each entity must not be null, must already exist in the database,
     *                 and must conform to the model constraints.
     * @param batchSize the size of the batches to use for the deletion operation. A larger batch size can improve performance
     *                  but may also increase the load on the database.
     * @throws PersistenceException if there is an error during the deletion operation, such as a violation of database constraints,
     *         connectivity issues, or if any entity in the stream is null.
     */
    @Override
    public void delete(@Nonnull Stream<E> entities, int batchSize) {
        var bindVars = ORM.createBindVars();
        try (entities;  // Close the stream when done.
             var query = ORM."""
                DELETE FROM \{model.type()}
                WHERE \{bindVars}""".prepare()) {
            slice(entities, batchSize)
                    .forEach(slice -> {
                        slice.stream().map(Record.class::cast).forEach(query::addBatch);
                        query.executeBatch();
                    });
        }
    }
}
