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
package st.orm.repository;

import jakarta.annotation.Nonnull;
import st.orm.BatchCallback;
import st.orm.FK;
import st.orm.Inline;
import st.orm.Ref;
import st.orm.NoResultException;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.ResultCallback;
import st.orm.Templates;
import st.orm.template.Model;
import st.orm.template.QueryBuilder;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Provides a generic interface with CRUD operations for entities.
 *
 * <h1>Using Entity Repositories</h1>
 *
 * <p>Entity repositories provide a high-level abstraction for managing entities in the database. They offer a set of
 * methods for creating, reading, updating, and deleting entities, as well as querying and filtering entities based on
 * specific criteria. The {@code EntityRepository} interface is designed to work with entity records that implement the
 * {@link Entity} interface, providing a consistent and type-safe way to interact with the database.</p>
 *
 * <h2>Entity Definition</h2>
 * <p>Define the entity records to use them to in combination with repositories. The {@link Entity} interface is a
 * marker interface that indicates that the record is an entity and has a primary key of type {@code ID}. The {@link PK}
 * annotation is used to mark the primary key field of the entity record. The {@link FK} annotation is used to mark
 * the foreign key field of the entity record. The {@link Inline} annotation (optional) is used to mark the record
 * component that is inlined in the entity record.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * record City(@PK int id,
 *             String name,
 *             long population
 * ) implements Entity<City, Integer> {};
 *
 * record Address(String street, String postalCode, @FK City city)
 *
 * record User(@PK int id,
 *             String email,
 *             LocalDate birthDate,
 *             @Inline Address address
 * ) implements Entity<User, Integer> {};
 * }</pre>
 *
 * <h2>Repository Lookup</h2>
 * <p>An entity repository can be obtained by invoking {@code entity} on an {@code ORMTemplate} with the desired entity
 * class. The orm template can be requested as demonstrated below. Note that orm templates are supported for
 * Data Sources, JDBC Connections and JPA Entity Managers.</p>
 * <pre>{@code
 * ORMTemplate orm = Templates.ORM(dataSource);
 * EntityRepository<User> userRepository = orm.entity(User.class);
 * }</pre>
 * <p>Alternatively, a specialized repository can be requested by calling the {@code repository} method with the repository
 * class. Specialized repositories allow specialized repository methods to be defined in the repository interface. The
 * specialized repository can be used to implement specialized queries or operations that are specific to the entity type.
 * The specialized logic can utilize the {@link QueryBuilder} interface to build SELECT and DELETE statements.</p>
 * <pre>{@code
 * interface UserRepository extends EntityRepository<User> {
 *
 *     // Specialized repository methods go here:
 *
 *     default Optional<User> findByEmail(String email) {
 *         return select()
 *                 .where(User_.email, EQUALS, email)
 *                 .getOptionalResult();
 *     }
 * }
 *
 * UserRepository userRepository = orm.repository(UserRepository.class)
 * }</pre>
 *
 * <h2>Repository Injection</h2>
 * <p>A specialized repository can also be injected using Spring's dependency injection mechanism when the
 * {@code storm-spring} package is included in the project. Check the storm-spring package to lean how to make
 * repositories available to the application for dependency injection.</p>
 *
 * <h2>CRUD Operations</h2>
 * <p>Entity repositories provide a set of methods for creating, reading, updating, and deleting entities in the
 * database. The following sections provide examples of how to use these methods to interact with the database.</p>
 *
 * <h3>Create</h3>
 *
 * <p>Insert a user into the database. The template engine also supports insertion of multiple entries in batch mode by
 * passing a list of entities. Alternatively, insertion can also be executed using a stream of entities.</p>
 * <pre>{@code
 * User user = ...;
 * userRepository.insert(user);
 * }</pre>
 *
 * <h3>Read</h3>
 *
 * <p>Select all users from the database that are linked to cities with the name "Sunnyvale". The static metamodel is
 * used to specify the City entity in the QueryBuilder's entity graph.</p>
 * <pre>{@code
 * List<City> cities = cityRepository.findByName("Sunnyvale")
 * List<User> users = userRepository
 *         .select()
 *         .where(User_.address.city, cities) // Type-safe metamodel.
 *         .getResultList();
 * }</pre>
 * <p>Alternatively, {@code getResultStream()} can be invoked to load the users lazily.</p>
 *
 * <p>The QueryBuilder also allows the previous queries to be combined into a single select query, using the
 * User's static metamodel to specify the city name field in the QueryBuilder's entity graph.</p>
 * <pre>{@code
 * List<User> users = userRepository
 *         .select()
 *         .where(User_.address.city.name, EQUALS, "Sunnyvale") // Type-safe metamodel.
 *         .getResultList();
 * }</pre>
 *
 * <h4>Update</h4>
 *
 * <p>Update a user in the database. The repository also supports updates for multiple entries in batch model by passing
 * a list of entities. Alternatively, updates can also be executed using a stream of entities.</p>
 * <pre>{@code
 * User user = ...;
 * userRepository.update(user);
 * }</pre>
 *
 * <h3>Delete</h3>
 *
 * <p>Delete user in the database. The repository also supports updates for multiple entries in batch mode by passing a
 * list entities or primary keys. Alternatively, deletion can be executed in using a stream of entities.
 * <pre>{@code
 * User user = ...;
 * userRepository.delete(user);
 * }</pre>
 *
 * <p>Also here, the QueryBuilder can be used to create specialized statement, for instance, to delete all users where
 * the email field IS NULL.</p>
 * <pre>{@code
 * repository
 *         .delete()
 *         .where(User_.email, IS_NULL) // Type-safe metamodel.
 *         .executeUpdate();
 * }</pre>
 *
 * @see Templates#ORM(javax.sql.DataSource)
 * @see Templates#ORM(jakarta.persistence.EntityManager)
 * @see Templates#ORM(Connection)
 * @see QueryBuilder
 * @param <E> the type of entity managed by this repository.
 * @param <ID> the type of the primary key of the entity.
 */
public interface EntityRepository<E extends Record & Entity<ID>, ID> extends Repository {

    /**
     * Returns the entity model associated with this repository.
     *
     * @return the entity model.
     */
    Model<E, ID> model();

    /**
     * Creates a new ref entity instance with the specified primary key.
     *
     * <p>This method creates a lightweight reference that encapsulates only the primary key of an entity,
     * without loading the full entity data into memory. The complete record can be fetched on demand by invoking
     * {@link Ref#fetch()}, which will trigger a separate database call.</p>
     *
     * @param id the primary key of the entity.
     * @return a ref entity instance containing only the primary key.
     * @since 1.3
     */
    Ref<E> ref(@Nonnull ID id);

    /**
     * Creates a new ref entity instance for the specified entity.
     *
     * <p>This method wraps a fully loaded entity in a lightweight reference. Although the complete entity is provided,
     * the returned ref retains only the primary key for identification. In this case, calling {@link Ref#fetch()} will
     * return the full entity (which is already loaded), ensuring a consistent API for accessing entity records on
     * demand. This approach supports lazy-loading scenarios where only the identifier is needed initially.</p>
     *
     * @param entity the entity to wrap in a ref.
     * @return a ref entity instance containing the primary key of the provided entity.
     * @since 1.3
     */
    Ref<E> ref(@Nonnull E entity);

    /**
     * Unloads the given entity from memory by converting it into a lightweight ref containing only its primary key.
     *
     * <p>This method discards the full entity data and returns a ref that encapsulates just the primary key. The actual
     * record is not retained in memory, but can be retrieved on demand by calling {@link Ref#fetch()}, which will
     * trigger a new database call. This approach is particularly useful when you need to minimize memory usage while
     * keeping the option to re-fetch the complete record later.</p>
     *
     * @param entity the entity to unload into a lightweight ref.
     * @return a ref containing only the primary key of the entity, allowing the full record to be fetched again when
     * needed.
     * @since 1.3
     */
    Ref<E> unload(@Nonnull E entity);

    // Query builder methods.

    /**
     * Creates a new query builder for selecting entities of the type managed by this repository.
     *
     * @return a new query builder for the entity type.
     */
    QueryBuilder<E, E, ID> select();

    /**
     * Creates a new query builder for the entity type managed by this repository.
     *
     * @return a new query builder for the entity type.
     */
    QueryBuilder<E, Long, ID> selectCount();

    /**
     * Creates a new query builder for the specialized {@code selectType}.
     *
     * @param selectType the result type of the query.
     * @return a new query builder for the specialized {@code selectType}.
     * @param <R> the result type of the query.
     */
    <R> QueryBuilder<E, R, ID> select(@Nonnull Class<R> selectType);

    /**
     * Creates a new query builder for selecting refs to entities of the type managed by this repository.
     *
     * <p>This method is typically used when you only need the primary keys of the entities initially, and you want to
     * defer fetching the full data until it is actually required. The query builder will return ref instances that
     * encapsulate the primary key. To retrieve the full entity, call {@link Ref#fetch()}, which will perform an
     * additional database query on demand.</p>
     *
     * @return a new query builder for selecting refs to entities.
     * @since 1.3
     */
    QueryBuilder<E, Ref<E>, ID> selectRef();

    /**
     * Creates a new query builder for the specialized {@code selectType} and specialized {@code template} for the select clause.
     *
     * @param selectType the result type of the query.
     * @param template the specialized template for the select clause.
     * @return a new query builder for the specialized {@code selectType}.
     * @param <R> the result type of the query.
     */
    <R> QueryBuilder<E, R, ID> select(@Nonnull Class<R> selectType, @Nonnull StringTemplate template);

    /**
     * Creates a new query builder for selecting refs to entities of the type managed by this repository.
     *
     * <p>This method is typically used when you only need the primary keys of the entities initially, and you want to
     * defer fetching the full data until it is actually required. The query builder will return ref instances that
     * encapsulate the primary key. To retrieve the full entity, call {@link Ref#fetch()}, which will perform an
     * additional database query on demand.</p>
     *
     * @param refType the type that is selected as ref.
     * @return a new query builder for selecting refs to entities.
     * @since 1.3
     */
    <R extends Record & Entity<?>> QueryBuilder<E, Ref<R>, ID> selectRef(@Nonnull Class<R> refType);

    /**
     * Creates a new query builder for delete entities of the type managed by this repository.
     *
     * @return a new query builder for the entity type.
     */
    QueryBuilder<E, ?, ID> delete();

    // Base methods.

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
    boolean existsById(@Nonnull ID id);

    /**
     * Checks if an entity with the specified primary key exists in the database.
     *
     * <p>This method determines the presence of an entity by checking if the count of entities with the given primary
     * key is greater than zero. It leverages the {@code selectCount} method, which performs a count operation on the
     * database.</p>
     *
     * @param ref the primary key of the entity to check for existence, expressed as a ref.
     * @return true if an entity with the specified primary key exists, false otherwise.
     * @throws PersistenceException if there is an underlying database issue during the count operation.
     */
    boolean existsByRef(@Nonnull Ref<E> ref);

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
    void deleteById(@Nonnull ID id);

    /**
     * Deletes an entity from the database.
     *
     * <p>This method removes an existing entity from the database. It is important to ensure that the entity passed for
     * deletion exists in the database and is correctly identified by its primary key.</p>
     *
     * @param ref the entity to delete. The entity must exist in the database and should be correctly identified by
     *            its ref.
     * @throws PersistenceException if the deletion operation fails. Reasons for failure might include the entity not
     *                              being found in the database, violations of database constraints, connectivity
     *                              issues, or if the entity parameter is null.
     */
    void deleteByRef(@Nonnull Ref<E> ref);

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

    // Singular findBy methods.

    /**
     * Retrieves an entity based on its primary key.
     *
     * <p>This method performs a lookup in the database, returning the corresponding entity if it exists.</p>
     *
     * @param id the primary key of the entity to retrieve.
     * @return the entity associated with the provided primary key. The returned entity encapsulates all relevant data
     * as mapped by the entity model.
     * @throws PersistenceException if the retrieval operation fails due to underlying database issues, such as
     *                              connectivity problems or query execution errors.
     */
    Optional<E> findById(@Nonnull ID id);

    /**
     * Retrieves an entity based on its primary key, expressed by a ref.
     *
     * <p>This method performs a lookup in the database, returning the corresponding entity if it exists.</p>
     *
     * @param ref the ref to match.
     * @return the entity associated with the provided primary key. The returned entity encapsulates all relevant data
     * as mapped by the entity model.
     * @throws PersistenceException if the retrieval operation fails due to underlying database issues, such as
     *                              connectivity problems or query execution errors.
     */
    Optional<E> findByRef(@Nonnull Ref<E> ref);

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
    E getById(@Nonnull ID id);

    /**
     * Retrieves an entity based on its primary key, expressed by a ref.
     *
     * <p>This method performs a lookup in the database, returning the corresponding entity if it exists.</p>
     *
     * @param ref the ref to match.
     * @return the entity associated with the provided primary key. The returned entity encapsulates all relevant data
     * as mapped by the entity model.
     * @throws NoResultException if no entity is found matching the given primary key, indicating that there's no
     *                           corresponding data in the database.
     * @throws PersistenceException if the retrieval operation fails due to underlying database issues, such as
     *                              connectivity problems or query execution errors.
     */
    E getByRef(@Nonnull Ref<E> ref);

    // List based methods.

    /**
     * Returns a list of all entities of the type supported by this repository. Each element in the list represents
     * an entity in the database, encapsulating all relevant data as mapped by the entity model.
     *
     * <p><strong>Please note:</strong> loading all entities into memory at once can be very memory-intensive if your
     * table is large.</p>
     *
     * @return a stream of all entities of the type supported by this repository.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    List<E> findAll();

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
    List<E> findAllById(@Nonnull Iterable<ID> ids);

    /**
     * Retrieves a list of entities based on their primary keys.
     *
     * <p>This method retrieves entities matching the provided IDs in batches, consolidating them into a single list.
     * The batch-based retrieval minimizes database overhead, allowing efficient handling of larger collections of IDs.
     * Note that the order of entities in the returned list is not guaranteed to match the order of IDs in the input
     * collection, as the database may not preserve insertion order during retrieval.</p>
     *
     * @param refs the primary keys of the entities to retrieve, represented as an iterable collection.
     * @return a list of entities corresponding to the provided primary keys. Entities are returned without any
     *         guarantee of order alignment with the input list. If an ID does not correspond to any entity in the
     *         database, no corresponding entity will be included in the returned list.
     * @throws PersistenceException if the selection operation fails due to database issues, such as connectivity
     *         problems or invalid input parameters.
     */
    List<E> findAllByRef(@Nonnull Iterable<Ref<E>> refs);

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

    /**
     * Deletes a collection of entities from the database in batches.
     *
     * <p>This method processes the provided entities in batches to optimize performance when handling larger collections,
     * reducing database overhead. For each entity in the collection, the method removes the corresponding record from
     * the database, if it exists. Batch processing ensures efficient handling of deletions, particularly for large data sets.</p>
     *
     * @param refs an iterable collection of entities to be deleted. Each entity in the collection must be non-null
     *             and represent a valid database record for deletion.
     * @throws PersistenceException if the deletion operation fails due to database issues, such as connectivity problems
     *                              or constraints violations.
     */
    void deleteByRef(@Nonnull Iterable<Ref<E>> refs);

    // Stream based methods.

    //
    // The BatchCallback interface is used to allow the caller to process the results in batches. This approach is
    // preferred over returning a stream of results directly because it allows the repository to control the batch
    // processing and resource management. The repository can decide how to batch the results and ensure that the
    // resources are properly managed. The BatchCallback interface provides a clean and flexible way to process the
    // results in batches, allowing the caller to define the processing logic for each batch.
    //
    // If the repository had returned a stream of results directly, that stream would effectively be linked to the input
    // stream. If the caller would fail to fully consume the resulting stream, the input stream would not be fully
    // processed. The BatchCallback approach prevents the caller from accidentally misusing the API.
    //

    /**
     * Returns a stream of all entities of the type supported by this repository. Each element in the stream represents
     * an entity in the database, encapsulating all relevant data as mapped by the entity model.
     *
     * <p>The resulting stream is lazily loaded, meaning that the entities are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of entities.</p>
     *
     * <p>Note that calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
     * while in use, it must be closed after usage to prevent resource leaks. As the stream is AutoCloseable, it is
     * recommended to use it within a try-with-resources block.</p>
     *
     * @return a stream of all entities of the type supported by this repository.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    Stream<E> selectAll();

    /**
     * Processes a stream of all entities of the type supported by this repository using the specified callback.
     * This method retrieves the entities and applies the provided callback to process them, returning the
     * result produced by the callback.
     *
     * <p>This method ensures efficient handling of large data sets by loading entities only as needed.
     * It also manages lifecycle of the callback stream, automatically closing the stream after processing to prevent
     * resource leaks.</p>
     *
     * @param callback a {@link ResultCallback} defining how to process the stream of entities and produce a result.
     * @param <R> the type of result produced by the callback after processing the entities.
     * @return the result produced by the callback's processing of the entity stream.
     * @throws PersistenceException if the operation fails due to underlying database issues, such as connectivity.
     */
    default <R> R selectAll(@Nonnull ResultCallback<E, R> callback) {
        try (var stream = selectAll()) {
            return callback.process(stream);
        }
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
     * <p>Note that calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
     * while in use, it must be closed after usage to prevent resource leaks. As the stream is AutoCloseable, it is
     * recommended to use it within a try-with-resources block.</p>
     *
     * @param ids a stream of entity IDs to retrieve from the repository.
     * @return a stream of entities corresponding to the provided primary keys. The order of entities in the stream is
     *         not guaranteed to match the order of ids in the input stream. If an id does not correspond to any entity
     *         in the database, it will simply be skipped, and no corresponding entity will be included in the returned
     *         stream. If the same entity is requested multiple times, it may be included in the stream multiple times
     *         if it is part of a separate batch.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    Stream<E> selectAllById(@Nonnull Stream<ID> ids);

    /**
     * Processes a stream of entities corresponding to the provided IDs using the specified callback.
     * This method retrieves entities matching the given IDs and applies the callback to process the results,
     * returning the outcome produced by the callback.
     *
     * <p>This method is designed for efficient data handling by only retrieving specified entities as needed.
     * It also manages the lifecycle of the callback stream, automatically closing the stream after processing to
     * prevent resource leaks.</p>
     *
     * @param ids a stream of entity IDs to retrieve from the repository.
     * @param callback a {@link ResultCallback} defining how to process the stream of entities and produce a result.
     * @param <R> the type of result produced by the callback after processing the entities.
     * @return the result produced by the callback's processing of the entity stream.
     * @throws PersistenceException if the operation fails due to underlying database issues, such as connectivity.
     */
    default <R> R selectAllById(@Nonnull Stream<ID> ids, @Nonnull ResultCallback<E, R> callback) {
        try (var stream = selectAllById(ids)) {
            return callback.process(stream);
        }
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
     * <p>Note that calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
     * while in use, it must be closed after usage to prevent resource leaks. As the stream is AutoCloseable, it is
     * recommended to use it within a try-with-resources block.</p>
     *
     * @param refs a stream of refs to retrieve from the repository.
     * @return a stream of entities corresponding to the provided primary keys. The order of entities in the stream is
     *         not guaranteed to match the order of ids in the input stream. If an id does not correspond to any entity
     *         in the database, it will simply be skipped, and no corresponding entity will be included in the returned
     *         stream. If the same entity is requested multiple times, it may be included in the stream multiple times
     *         if it is part of a separate batch.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    Stream<E> selectAllByRef(@Nonnull Stream<Ref<E>> refs);

    /**
     * Processes a stream of entities corresponding to the provided IDs using the specified callback.
     * This method retrieves entities matching the given IDs and applies the callback to process the results,
     * returning the outcome produced by the callback.
     *
     * <p>This method is designed for efficient data handling by only retrieving specified entities as needed.
     * It also manages the lifecycle of the callback stream, automatically closing the stream after processing to
     * prevent resource leaks.</p>
     *
     * @param refs a stream of refs to retrieve from the repository.
     * @param callback a {@link ResultCallback} defining how to process the stream of entities and produce a result.
     * @param <R> the type of result produced by the callback after processing the entities.
     * @return the result produced by the callback's processing of the entity stream.
     * @throws PersistenceException if the operation fails due to underlying database issues, such as connectivity.
     */
    default <R> R selectAllByRef(@Nonnull Stream<Ref<E>> refs, @Nonnull ResultCallback<E, R> callback) {
        try (var stream = selectAllByRef(refs)) {
            return callback.process(stream);
        }
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
     * <p>Note that calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
     * while in use, it must be closed after usage to prevent resource leaks. As the stream is AutoCloseable, it is
     * recommended to use it within a try-with-resources block.</p>
     *
     * @param ids a stream of entity IDs to retrieve from the repository.
     * @param batchSize the number of primary keys to include in each batch. This parameter determines the size of the
     *                  batches used to execute the selection operation. A larger batch size can improve performance, especially when
     *                  dealing with large sets of primary keys.
     * @return a stream of entities corresponding to the provided primary keys. The order of entities in the stream is
     * not guaranteed to match the order of refs in the input stream. If an id does not correspond to any entity in the
     * database, it will simply be skipped, and no corresponding entity will be included in the returned stream. If the
     * same entity is requested multiple times, it may be included in the stream multiple times if it is part of a
     * separate batch.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    Stream<E> selectAllById(@Nonnull Stream<ID> ids, int batchSize);

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
     * <p>Note that calling this method does trigger the execution of the underlying query, so it should only
     * be invoked when the query is intended to run. Since the stream holds resources open
     * while in use, it must be closed after usage to prevent resource leaks. As the stream is AutoCloseable, it is
     * recommended to use it within a try-with-resources block.</p>
     *
     * @param ids a stream of entity IDs to retrieve from the repository.
     * @param batchSize the number of primary keys to include in each batch. This parameter determines the size of the
     *                  batches used to execute the selection operation. A larger batch size can improve performance, especially when
     *                  dealing with large sets of primary keys.
     * @return a stream of entities corresponding to the provided primary keys. The order of entities in the stream is
     * not guaranteed to match the order of refs in the input stream. If an id does not correspond to any entity in the
     * database, it will simply be skipped, and no corresponding entity will be included in the returned stream. If the
     * same entity is requested multiple times, it may be included in the stream multiple times if it is part of a
     * separate batch.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    default <R> R selectAllById(@Nonnull Stream<ID> ids, int batchSize, @Nonnull ResultCallback<E, R> callback) {
        try (var stream = selectAllById(ids, batchSize)) {
            return callback.process(stream);
        }
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
     * <p>Note that calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
     * while in use, it must be closed after usage to prevent resource leaks. As the stream is AutoCloseable, it is
     * recommended to use it within a try-with-resources block.</p>
     *
     * @param refs a stream of refs to retrieve from the repository.
     * @param batchSize the number of primary keys to include in each batch. This parameter determines the size of the
     *                  batches used to execute the selection operation. A larger batch size can improve performance, especially when
     *                  dealing with large sets of primary keys.
     * @return a stream of entities corresponding to the provided primary keys. The order of entities in the stream is
     * not guaranteed to match the order of refs in the input stream. If an id does not correspond to any entity in the
     * database, it will simply be skipped, and no corresponding entity will be included in the returned stream. If the
     * same entity is requested multiple times, it may be included in the stream multiple times if it is part of a
     * separate batch.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    Stream<E> selectAllByRef(@Nonnull Stream<Ref<E>> refs, int batchSize);

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
     * <p>Note that calling this method does trigger the execution of the underlying query, so it should only
     * be invoked when the query is intended to run. Since the stream holds resources open
     * while in use, it must be closed after usage to prevent resource leaks. As the stream is AutoCloseable, it is
     * recommended to use it within a try-with-resources block.</p>
     *
     * @param batchSize the number of primary keys to include in each batch. This parameter determines the size of the
     *                  batches used to execute the selection operation. A larger batch size can improve performance, especially when
     *                  dealing with large sets of primary keys.
     * @return a stream of entities corresponding to the provided primary keys. The order of entities in the stream is
     * not guaranteed to match the order of refs in the input stream. If an id does not correspond to any entity in the
     * database, it will simply be skipped, and no corresponding entity will be included in the returned stream. If the
     * same entity is requested multiple times, it may be included in the stream multiple times if it is part of a
     * separate batch.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    default <R> R selectAllByRef(@Nonnull Stream<Ref<E>> refs, int batchSize, @Nonnull ResultCallback<E, R> callback) {
        try (var stream = selectAllByRef(refs, batchSize)) {
            return callback.process(stream);
        }
    }

    /**
     * Counts the number of entities identified by the provided stream of IDs using the default batch size.
     *
     * <p>This method calculates the total number of entities that match the provided primary keys. The counting
     * is performed in batches, which helps optimize performance and manage database load when dealing with
     * large sets of IDs.</p>
     *
     * @param ids a stream of IDs for which to count matching entities.
     * @return the total count of entities matching the provided IDs.
     * @throws PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    long countById(@Nonnull Stream<ID> ids);

    /**
     * Counts the number of entities identified by the provided stream of IDs, with the counting process divided into
     * batches of the specified size.
     *
     * <p>This method performs the counting operation in batches, specified by the {@code batchSize} parameter. This
     * batching approach is particularly useful for efficiently handling large volumes of IDs, reducing the overhead on
     * the database and improving performance.</p>
     *
     * @param ids a stream of IDs for which to count matching entities.
     * @param batchSize the size of the batches to use for the counting operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @return the total count of entities matching the provided IDs.
     * @throws PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    long countById(@Nonnull Stream<ID> ids, int batchSize);

    /**
     * Counts the number of entities identified by the provided stream of refs using the default batch size.
     *
     * <p>This method calculates the total number of entities that match the provided primary keys. The counting
     * is performed in batches, which helps optimize performance and manage database load when dealing with
     * large sets of IDs.</p>
     *
     * @param refs a stream of IDs for which to count matching entities.
     * @return the total count of entities matching the provided IDs.
     * @throws PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    long countByRef(@Nonnull Stream<Ref<E>> refs);

    /**
     * Counts the number of entities identified by the provided stream of refs, with the counting process divided into
     * batches of the specified size.
     *
     * <p>This method performs the counting operation in batches, specified by the {@code batchSize} parameter. This
     * batching approach is particularly useful for efficiently handling large volumes of IDs, reducing the overhead on
     * the database and improving performance.</p>
     *
     * @param refs a stream of IDs for which to count matching entities.
     * @param batchSize the size of the batches to use for the counting operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @return the total count of entities matching the provided IDs.
     * @throws PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    long countByRef(@Nonnull Stream<Ref<E>> refs, int batchSize);

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
    void insert(@Nonnull Stream<E> entities);

    /**
     * Inserts a stream of entities into the database, with the insertion process divided into batches of the specified
     * size.
     *
     * <p>This method inserts entities provided in a stream and uses the specified batch size for the insertion
     * operation.  Batching the inserts can greatly enhance performance by minimizing the number of database
     * interactions, especially useful when dealing with large volumes of data.</p>
     *
     * @param entities a stream of entities to insert. Each entity must not be null and must conform to the model
     *                 constraints.
     * @param batchSize the size of the batches to use for the insertion operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @throws PersistenceException if there is an error during the insertion operation, such as a violation of database
     *                              constraints, connectivity issues, or if any entity in the stream is null.
     */
    void insert(@Nonnull Stream<E> entities, int batchSize);

    /**
     * Inserts a stream of entities into the database using the default batch size and returns a stream of their
     * generated primary keys.
     *
     * <p>This method facilitates the insertion of entities and fetches their primary keys immediately after insertion.
     * This is particularly useful when the primary keys are generated by the database (e.g., auto-increment fields). It
     * uses the default batch size to optimize the number of database interactions.</p>
     *
     * @param entities a stream of entities to insert. Each entity must not be null and must conform to the model
     *                constraints.
     * @param callback the callback to process the IDs of the inserted entities in batches.
     * @throws PersistenceException if there is an error during the insertion or key retrieval operation, such as a
     *                              violation of database constraints, connectivity issues, or if any entity in the
     *                              stream is null.
     */
    void insertAndFetchIds(@Nonnull Stream<E> entities, @Nonnull BatchCallback<ID> callback);

    /**
     * Inserts a stream of entities into the database using the default batch size and returns a stream of the inserted entities.
     *
     * <p>This method inserts entities into the database and retrieves them immediately after insertion. It is useful
     * for ensuring that the returned entities reflect any database-generated values or defaults. The insertion and
     * retrieval are performed using the default batch size to optimize database performance.</p>
     *
     * @param entities a stream of entities to insert. Each entity must not be null and must conform to the model
     *                 constraints.
     * @param callback the callback to process the inserted entities, reflecting their new state in the database,
     *                 in batches.
     * @throws PersistenceException if there is an error during the insertion or retrieval operation, such as a
     *                              violation of database constraints, connectivity issues, or if any entity in the
     *                              stream is null.
     */
    void insertAndFetch(@Nonnull Stream<E> entities, @Nonnull BatchCallback<E> callback);

    /**
     * Inserts a stream of entities into the database with the insertion process divided into batches of the specified size,
     * and returns a stream of their generated primary keys.
     *
     * <p>This method allows for efficient insertion of a large number of entities by batching them according to the
     * specified batch size. It also fetches the primary keys immediately after insertion, useful for entities with
     * database-generated keys.</p>
     *
     * @param entities a stream of entities to insert. Each entity must not be null and must conform to the model
     *                 constraints.
     * @param batchSize the size of the batches to use for the insertion operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @param callback the callback to process the IDs of the inserted entities in batches.
     * @throws PersistenceException if there is an error during the insertion or key retrieval operation, such as a
     *                              violation of database constraints, connectivity issues, or if any entity in the
     *                              stream is null.
     */
    void insertAndFetchIds(@Nonnull Stream<E> entities, int batchSize, @Nonnull BatchCallback<ID> callback);

    /**
     * Inserts a stream of entities into the database with the insertion process divided into batches of the specified size,
     * and returns a stream of the inserted entities.
     *
     * <p>This method provides an efficient way to insert a large number of entities by batching them according to the
     * specified batch size. It fetches the inserted entities immediately after insertion to ensure that the returned
     * entities reflect any database-generated values or defaults. This is particularly useful when database triggers or
     * default values are involved.</p>
     *
     * @param entities a stream of entities to insert. Each entity must not be null and must conform to the model
     *                 constraints.
     * @param batchSize the size of the batches to use for the insertion operation. A larger batch size can improve
     *                 performance but may also increase the load on the database.
     * @param callback the callback to process the inserted entities, reflecting their new state in the database,
     *                 in batches.
     * @throws PersistenceException if there is an error during the insertion or retrieval operation, such as a
     *                              violation of database constraints, connectivity issues, or if any entity in the
     *                              stream is null.
     */
    void insertAndFetch(@Nonnull Stream<E> entities, int batchSize, @Nonnull BatchCallback<E> callback);

    /**
     * Updates a stream of entities in the database using the default batch size.
     *
     * <p>This method updates entities provided in a stream, optimizing the update process by batching them
     * with a default size. This helps to reduce the number of database operations and can significantly improve
     * performance when updating large numbers of entities.</p>
     *
     * @param entities a stream of entities to update. Each entity must not be null, must already exist in the database,
     *                 and must conform to the model constraints.
     * @throws PersistenceException if there is an error during the update operation, such as a violation of database
     *                              constraints, connectivity issues, or if any entity in the stream is null.
     */
    void update(@Nonnull Stream<E> entities);

    /**
     * Updates a stream of entities in the database, with the update process divided into batches of the specified size.
     *
     * <p>This method updates entities provided in a stream and uses the specified batch size for the update operation.
     * Batching the updates can greatly enhance performance by minimizing the number of database interactions,
     * especially useful when dealing with large volumes of data.</p>
     *
     * @param entities a stream of entities to update. Each entity must not be null, must already exist in the database,
     *                 and must conform to the model constraints.
     * @param batchSize the size of the batches to use for the update operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @throws PersistenceException if there is an error during the update operation, such as a violation of database
     *                              constraints, connectivity issues, or if any entity in the stream is null.
     */
    void update(@Nonnull Stream<E> entities, int batchSize);

    /**
     * Updates a stream of entities in the database using the default batch size and returns a stream of the updated entities.
     *
     * <p>This method updates entities provided in a stream, optimizing the update process by batching them with the
     * default size. It fetches the updated entities immediately after updating to ensure that the returned entities
     * reflect any database-generated values or defaults. This is particularly useful when database triggers or default
     * values are involved.</p>
     *
     * @param entities a stream of entities to update. Each entity must not be null, must already exist in the database,
     *                 and must conform to the model constraints.
     * @param callback the callback to process the updated entities, reflecting their new state in the database,
     *                 in batches.
     * @throws PersistenceException if there is an error during the update or retrieval operation, such as a violation
     *                              of database constraints, connectivity issues, or if any entity in the stream is
     *                              null.
     */
    void updateAndFetch(@Nonnull Stream<E> entities, @Nonnull BatchCallback<E> callback);

    /**
     * Updates a stream of entities in the database, with the update process divided into batches of the specified size,
     * and returns a stream of the updated entities.
     *
     * <p>This method updates entities provided in a stream and uses the specified batch size for the update operation.
     * Batching the updates can greatly enhance performance by minimizing the number of database interactions,
     * especially useful when dealing with large volumes of data. It fetches the updated entities immediately after
     * updating to ensure that the returned entities reflect any database-generated values or defaults.</p>
     *
     * @param entities a stream of entities to update. Each entity must not be null, must already exist in the database,
     *                 and must conform to the model constraints.
     * @param batchSize the size of the batches to use for the update operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @param callback the callback to process the updated entities, reflecting their new state in the database,
     *                 in batches.
     * @throws PersistenceException if there is an error during the update or retrieval operation, such as a violation
     *                              of database constraints, connectivity issues, or if any entity in the stream is
     *                              null.
     */
    void updateAndFetch(@Nonnull Stream<E> entities, int batchSize, @Nonnull BatchCallback<E> callback);

    /**
     * Inserts or updates a stream of entities in the database in batches.
     *
     * <p>This method processes the provided stream of entities in batches, performing an "upsert" operation on each.
     * For each entity, it will be inserted into the database if it does not already exist; if it does exist, it will
     * be updated to reflect the current state of the entity. Batch processing optimizes the performance of the
     * upsert operation for larger data sets by reducing database overhead.</p>
     *
     * @param entities a stream of entities to be inserted or updated. Each entity in the stream must be non-null
     *                 and contain valid data for insertion or update in the database.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    void upsert(@Nonnull Stream<E> entities);

    /**
     * Inserts or updates a stream of entities in the database in configurable batch sizes.
     *
     * <p>This method processes the provided stream of entities in batches, performing an "upsert" operation on each.
     * For each entity, it will be inserted if it does not already exist in the database, or updated if it does.
     * The batch size can be configured to control the number of entities processed in each database operation,
     * allowing for optimized performance and memory management based on system requirements.</p>
     *
     * @param entities a stream of entities to be inserted or updated. Each entity in the stream must be non-null
     *                 and contain valid data for insertion or update in the database.
     * @param batchSize the number of entities to process in each batch. A larger batch size may improve performance
     *                  but increase memory usage, while a smaller batch size may reduce memory usage but increase
     *                  the number of database operations.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    void upsert(@Nonnull Stream<E> entities, int batchSize);

    /**
     * Inserts or updates a stream of entities in the database in batches and retrieves their IDs through a callback.
     *
     * <p>This method processes the provided stream of entities in batches, performing an "upsert" operation on each entity.
     * For each entity, it will be inserted if it does not already exist in the database or updated if it does. After
     * each batch operation, the IDs of the upserted entities are passed to the provided callback, allowing for
     * specializedized handling of the IDs as they are retrieved.</p>
     *
     * @param entities a stream of entities to be inserted or updated. Each entity in the stream must be non-null and
     *                 contain valid data for insertion or update in the database.
     * @param callback the callback to process the IDs of the upserted entities in batches.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity problems,
     *                              constraints violations, or invalid entity data.
     */
    void upsertAndFetchIds(@Nonnull Stream<E> entities, @Nonnull BatchCallback<ID> callback);

    /**
     * Inserts or updates a stream of entities in the database in batches and retrieves the updated entities through a callback.
     *
     * <p>This method processes the provided stream of entities in batches, performing an "upsert" operation on each entity.
     * For each entity, it will be inserted if it does not already exist in the database or updated if it does. After
     * each batch operation, the updated entities are passed to the provided callback, allowing for specializedized handling
     * of the entities as they are retrieved. The entities returned reflect their current state in the database, including
     * any changes such as generated primary keys, timestamps, or default values set by the database during the upsert process.</p>
     *
     * @param entities a stream of entities to be inserted or updated. Each entity in the stream must be non-null and
     *                 contain valid data for insertion or update in the database.
     * @param callback the callback to process the upserted entities, reflecting their new state in the database,
     *                 in batches.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity problems,
     *                              constraints violations, or invalid entity data.
     */
    void upsertAndFetch(@Nonnull Stream<E> entities, @Nonnull BatchCallback<E> callback);

    /**
     * Inserts or updates a stream of entities in the database in configurable batch sizes and retrieves their IDs through a callback.
     *
     * <p>This method processes the provided stream of entities in batches, performing an "upsert" operation on each entity.
     * For each entity, it will be inserted if it does not already exist in the database or updated if it does. The batch size
     * parameter allows control over the number of entities processed in each batch, optimizing memory and performance based
     * on system requirements. After each batch operation, the IDs of the upserted entities are passed to the provided
     * callback, allowing for specializedized handling of the IDs as they are retrieved.</p>
     *
     * @param entities a stream of entities to be inserted or updated. Each entity in the stream must be non-null and contain
     *                 valid data for insertion or update in the database.
     * @param batchSize the number of entities to process in each batch. Adjusting the batch size can optimize performance
     *                  and memory usage, with larger sizes potentially improving performance but using more memory.
     * @param callback the callback to process the IDs of the upserted entities in batches.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity problems,
     *                              constraints violations, or invalid entity data.
     */
    void upsertAndFetchIds(@Nonnull Stream<E> entities, int batchSize, @Nonnull BatchCallback<ID> callback);

    /**
     * Inserts or updates a stream of entities in the database in configurable batch sizes and retrieves the updated entities through a callback.
     *
     * <p>This method processes the provided stream of entities in batches, performing an "upsert" operation on each entity.
     * For each entity, it will be inserted if it does not already exist in the database or updated if it does. The
     * `batchSize` parameter allows control over the number of entities processed in each batch, optimizing performance
     * and memory usage based on system requirements. After each batch operation, the updated entities are passed to
     * the provided callback, allowing for specializedized handling of the entities as they are retrieved. The entities
     * returned reflect their current state in the database, including any changes such as generated primary keys,
     * timestamps, or default values applied during the upsert process.</p>
     *
     * @param entities a stream of entities to be inserted or updated. Each entity in the stream must be non-null and
     *                 contain valid data for insertion or update in the database.
     * @param batchSize the number of entities to process in each batch. Adjusting the batch size can optimize performance
     *                  and memory usage, with larger sizes potentially improving performance but using more memory.
     * @param callback the callback to process the upserted entities, reflecting their new state in the database,
     *                 in batches.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity problems,
     *                              constraints violations, or invalid entity data.
     */
    void upsertAndFetch(@Nonnull Stream<E> entities, int batchSize, @Nonnull BatchCallback<E> callback);

    /**
     * Deletes a stream of entities from the database in batches.
     *
     * <p>This method processes the provided stream of entities in batches to optimize performance for larger
     * data sets, reducing database overhead during deletion. For each entity in the stream, the method removes
     * the corresponding record from the database, if it exists. Batch processing allows efficient handling
     * of deletions, particularly for large collections of entities.</p>
     *
     * @param entities a stream of entities to be deleted. Each entity in the stream must be non-null and represent
     *                 a valid database record for deletion.
     * @throws PersistenceException if the deletion operation fails due to database issues, such as connectivity problems
     *                              or constraints violations.
     */
    void delete(@Nonnull Stream<E> entities);

    /**
     * Deletes a stream of entities from the database in configurable batch sizes.
     *
     * <p>This method processes the provided stream of entities in batches, with the size of each batch specified
     * by the `batchSize` parameter. This allows for control over the number of entities deleted in each database
     * operation, optimizing performance and memory usage based on system requirements. For each entity in the
     * stream, the method removes the corresponding record from the database, if it exists.</p>
     *
     * @param entities a stream of entities to be deleted. Each entity in the stream must be non-null and represent
     *                 a valid database record for deletion.
     * @param batchSize the number of entities to process in each batch. Larger batch sizes may improve performance
     *                  but require more memory, while smaller batch sizes may reduce memory usage but increase
     *                  the number of database operations.
     * @throws PersistenceException if the deletion operation fails due to database issues, such as connectivity problems
     *                              or constraints violations.
     */
    void delete(@Nonnull Stream<E> entities, int batchSize);

    /**
     * Deletes a stream of entities from the database in batches.
     *
     * <p>This method processes the provided stream of entities in batches to optimize performance for larger
     * data sets, reducing database overhead during deletion. For each entity in the stream, the method removes
     * the corresponding record from the database, if it exists. Batch processing allows efficient handling
     * of deletions, particularly for large collections of entities.</p>
     *
     * @param refs a stream of entities to be deleted. Each entity in the stream must be non-null and represent
     *             a valid database record for deletion.
     * @throws PersistenceException if the deletion operation fails due to database issues, such as connectivity problems
     *                              or constraints violations.
     */
    void deleteByRef(@Nonnull Stream<Ref<E>> refs);

    /**
     * Deletes a stream of entities from the database in configurable batch sizes.
     *
     * <p>This method processes the provided stream of entities in batches, with the size of each batch specified
     * by the `batchSize` parameter. This allows for control over the number of entities deleted in each database
     * operation, optimizing performance and memory usage based on system requirements. For each entity in the
     * stream, the method removes the corresponding record from the database, if it exists.</p>
     *
     * @param refs a stream of entities to be deleted. Each entity in the stream must be non-null and represent
     *              valid database record for deletion.
     * @param batchSize the number of entities to process in each batch. Larger batch sizes may improve performance
     *                  but require more memory, while smaller batch sizes may reduce memory usage but increase
     *                  the number of database operations.
     * @throws PersistenceException if the deletion operation fails due to database issues, such as connectivity problems
     *                              or constraints violations.
     */
    void deleteByRef(@Nonnull Stream<Ref<E>> refs, int batchSize);
}
