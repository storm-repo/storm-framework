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
package st.orm.kt.repository

import st.orm.Metamodel
import st.orm.Operator.EQUALS
import st.orm.Operator.IN
import st.orm.Projection
import st.orm.Ref
import st.orm.kt.template.*
import java.util.stream.Stream
import kotlin.reflect.KClass

/**
 * Provides a generic interface with read operations for projections.
 *
 * Projection repositories provide a high-level abstraction for reading projections in the database. They offer a
 * set of methods for reading projections, as well as querying and filtering entities based on specific criteria. The
 * repository interface is designed to work with entity records that implement the [st.orm.Projection] interface,
 * providing a consistent and type-safe way to interact with the database.
 *
 * @since 1.1
 * @see QueryBuilder
 *
 * @param <P> the type of projection managed by this repository.
 * @param <ID> the type of the primary key of the projection, or [Void] if the projection has no primary key.
 */
interface ProjectionRepository<P, ID : Any> : Repository where P : Record, P : Projection<ID> {
    /**
     * Returns the projection model associated with this repository.
     *
     * @return the projection model.
     */
    val model: Model<P, ID>

    /**
     * Creates a new ref projection instance with the specified primary key.
     *
     * @param id the primary key of the projection.
     * @return a ref projection instance.
     */
    fun ref(id: ID): Ref<P>

    /**
     * Creates a new ref projection instance with the specified projection.
     *
     * @param projection the projection.
     * @return a ref projection instance.
     */
    fun ref(projection: P, id: ID): Ref<P>

    // Query builder methods.
    /**
     * Creates a new query builder for the projection type managed by this repository.
     *
     * @return a new query builder for the projection type.
     */
    fun select(): QueryBuilder<P, P, ID>

    /**
     * Creates a new query builder for the projection type managed by this repository.
     *
     * @return a new query builder for the projection type.
     */
    fun selectCount(): QueryBuilder<P, Long, ID>

    /**
     * Creates a new query builder for the custom `selectType`.
     *
     * @param selectType the result type of the query.
     * @return a new query builder for the custom `selectType`.
     * @param <R> the result type of the query.
     */
    fun <R : Any> select(selectType: KClass<R>): QueryBuilder<P, R, ID>

    /**
     * Creates a new query builder for selecting refs to projections of the type managed by this repository.
     *
     *
     * This method is typically used when you only need the primary keys of the projection initially, and you want to
     * defer fetching the full data until it is actually required. The query builder will return ref instances that
     * encapsulate the primary key. To retrieve the full entity, call [Ref.fetch], which will perform an
     * additional database query on demand.
     *
     * @return a new query builder for selecting refs to projections.
     * @since 1.3
     */
    fun selectRef(): QueryBuilder<P, Ref<P>, ID>

    /**
     * Creates a new query builder for the custom `selectType` and custom `template` for the select clause.
     *
     * @param selectType the result type of the query.
     * @param builder the custom template for the select clause.
     * @return a new query builder for the custom `selectType`.
     * @param <R> the result type of the query.
     */
    fun <R : Any> select(selectType: KClass<R>, builder: TemplateBuilder): QueryBuilder<P, R, ID> {
        return select(selectType, builder.build())
    }

    /**
     * Creates a new query builder for the custom `selectType` and custom `template` for the select clause.
     *
     * @param selectType the result type of the query.
     * @param template the custom template for the select clause.
     * @return a new query builder for the custom `selectType`.
     * @param <R> the result type of the query.
     */
    fun <R : Any> select(selectType: KClass<R>, template: TemplateString): QueryBuilder<P, R, ID>

    /**
     * Creates a new query builder for selecting refs to projections of the type managed by this repository.
     *
     *
     * This method is typically used when you only need the primary keys of the projections initially, and you want to
     * defer fetching the full data until it is actually required. The query builder will return ref instances that
     * encapsulate the primary key. To retrieve the full projection, call [Ref.fetch], which will perform an
     * additional database query on demand.
     *
     * @param refType the type that is selected as ref.
     * @return a new query builder for selecting refs to projections.
     * @since 1.3
     */
    fun <R : Record> selectRef(refType: KClass<R>): QueryBuilder<P, Ref<R>, ID>

    // Base methods.
    /**
     * Returns the number of projections in the database of the projection type supported by this repository.
     *
     * @return the total number of projections in the database as a long value.
     * @throws st.orm.PersistenceException if the count operation fails due to underlying database issues, such as
     * connectivity.
     */
    fun count(): Long

    /**
     * Checks if any projection of the type managed by this repository exists in the database.
     *
     * @return true if at least one projection exists, false otherwise.
     * @throws st.orm.PersistenceException if there is an underlying database issue during the count operation.
     */
    fun exists(): Boolean

    /**
     * Checks if a projection with the specified primary key exists in the database.
     *
     *
     * This method determines the presence of a projection by checking if the count of projections with the given primary
     * key is greater than zero. It leverages the `selectCount` method, which performs a count operation on the
     * database.
     *
     * @param id the primary key of the projection to check for existence.
     * @return true if a projection with the specified primary key exists, false otherwise.
     * @throws st.orm.PersistenceException if there is an underlying database issue during the count operation.
     */
    fun existsById(id: ID): Boolean

    /**
     * Checks if a projection with the specified primary key exists in the database.
     *
     *
     * This method determines the presence of a projection by checking if the count of projections with the given primary
     * key is greater than zero. It leverages the `selectCount` method, which performs a count operation on the
     * database.
     *
     * @param ref the primary key of the projection to check for existence.
     * @return true if a projection with the specified primary key exists, false otherwise.
     * @throws st.orm.PersistenceException if there is an underlying database issue during the count operation.
     */
    fun existsByRef(ref: Ref<P>): Boolean

    // Singular findBy methods.

    /**
     * Retrieves a projection based on its primary key.
     *
     *
     * This method performs a lookup in the database, returning the corresponding projection if it exists.
     *
     * @param id the primary key of the projection to retrieve.
     * @return the projection associated with the provided primary key. The returned projection encapsulates all relevant data
     * as mapped by the projection model.
     * @throws st.orm.PersistenceException if the retrieval operation fails due to underlying database issues, such as
     * connectivity problems or query execution errors.
     */
    fun findById(id: ID): P?

    /**
     * Retrieves a projection based on its primary key.
     *
     *
     * This method performs a lookup in the database, returning the corresponding projection if it exists.
     *
     * @param ref the ref to match.
     * @return the projection associated with the provided primary key. The returned projection encapsulates all relevant data
     * as mapped by the projection model.
     * @throws st.orm.PersistenceException if the retrieval operation fails due to underlying database issues, such as
     * connectivity problems or query execution errors.
     */
    fun findByRef(ref: Ref<P>): P?

    /**
     * Retrieves a projection based on its primary key.
     *
     *
     * This method performs a lookup in the database, returning the corresponding projection if it exists.
     *
     * @param id the primary key of the projection to retrieve.
     * @return the projection associated with the provided primary key. The returned projection encapsulates all relevant data
     * as mapped by the projection model.
     * @throws st.orm.NoResultException if no projection is found matching the given primary key, indicating that there's no
     * corresponding data in the database.
     * @throws st.orm.PersistenceException if the retrieval operation fails due to underlying database issues, such as
     * connectivity problems or query execution errors.
     */
    fun getById(id: ID): P

    /**
     * Retrieves a projection based on its primary key.
     *
     *
     * This method performs a lookup in the database, returning the corresponding projection if it exists.
     *
     * @param ref the ref to match.
     * @return the projection associated with the provided primary key. The returned projection encapsulates all relevant data
     * as mapped by the projection model.
     * @throws st.orm.NoResultException if no projection is found matching the given primary key, indicating that there's no
     * corresponding data in the database.
     * @throws st.orm.PersistenceException if the retrieval operation fails due to underlying database issues, such as
     * connectivity problems or query execution errors.
     */
    fun getByRef(ref: Ref<P>): P

    // List based methods.

    /**
     * Returns a list of all projections of the type supported by this repository. Each element in the list represents
     * a projection in the database, encapsulating all relevant data as mapped by the projection model.
     *
     *
     * **Note:** Loading all projections into memory at once can be very memory-intensive if your table
     * is large.
     *
     * @return a stream of all entities of the type supported by this repository.
     * @throws st.orm.PersistenceException if the selection operation fails due to underlying database issues, such as
     * connectivity.
     */
    fun findAll(): List<P>

    /**
     * Retrieves a list of projections based on their primary keys.
     *
     *
     * This method retrieves projections matching the provided IDs in batches, consolidating them into a single list.
     * The batch-based retrieval minimizes database overhead, allowing efficient handling of larger collections of IDs.
     *
     *
     *
     * **Note:** The order of projections in the returned list is not guaranteed to match the order of
     * IDs in the input collection, as the database may not preserve insertion order during retrieval.
     *
     * @param ids the primary keys of the projections to retrieve, represented as an iterable collection.
     * @return a list of projections corresponding to the provided primary keys. Projections are returned without any
     * guarantee of order alignment with the input list. If an ID does not correspond to any projection in the
     * database, no corresponding projection will be included in the returned list.
     * @throws st.orm.PersistenceException if the selection operation fails due to database issues, such as connectivity
     * problems or invalid input parameters.
     */
    fun findAllById(ids: Iterable<ID>): List<P>

    /**
     * Retrieves a list of projections based on their primary keys.
     *
     *
     * This method retrieves projections matching the provided IDs in batches, consolidating them into a single list.
     * The batch-based retrieval minimizes database overhead, allowing efficient handling of larger collections of IDs.
     *
     *
     *
     * **Note:** The order of projections in the returned list is not guaranteed to match the order of
     * IDs in the input collection, as the database may not preserve insertion order during retrieval.
     *
     * @param refs the primary keys of the projections to retrieve, represented as an iterable collection.
     * @return a list of projections corresponding to the provided primary keys. Projections are returned without any
     * guarantee of order alignment with the input list. If an ID does not correspond to any projection in the
     * database, no corresponding projection will be included in the returned list.
     * @throws st.orm.PersistenceException if the selection operation fails due to database issues, such as connectivity
     * problems or invalid input parameters.
     */
    fun findAllByRef(refs: Iterable<Ref<P>>): List<P>

    // Stream based methods.
    //
    // The BatchCallback interface is used to allow the caller to process the results in batches. This approach is
    // preferred over returning a stream of results directly because it allows the repository to control the batch
    // processing and resource management. The repository can decide how to batch the results and ensure that the
    // resources are properly managed. The BatchCallback interface provides a clean and flexible way to process the
    // results in batches, allowing the caller to define the processing logic for each batch.
    //
    // If the repository returned a stream of results directly, that stream would effectively be linked to the input
    // stream. If the caller would fail to fully consume the resulting stream, the input stream would not be fully
    // processed. The BatchCallback approach prevents the caller from accidentally misusing the API.
    //

    /**
     * Returns a stream of all projections of the type supported by this repository. Each element in the stream represents
     * a projection in the database, encapsulating all relevant data as mapped by the projection model.
     *
     *
     * The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of projections.
     *
     *
     * **Note:** Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must be
     * closed after usage to prevent resource leaks. As the stream is `AutoCloseable`, it is recommended to use it
     * within a `try-with-resources` block.
     *
     * @return a stream of all projections of the type supported by this repository.
     * @throws st.orm.PersistenceException if the selection operation fails due to underlying database issues, such as
     * connectivity.
     */
    fun selectAll(): Stream<P>

    /**
     * Retrieves a stream of projections based on their primary keys.
     *
     *
     * This method executes queries in batches, depending on the number of primary keys in the specified ids stream.
     * This optimization aims to reduce the overhead of executing multiple queries and efficiently retrieve projections.
     * The batching strategy enhances performance, particularly when dealing with large sets of primary keys.
     *
     *
     * The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of projections.
     *
     *
     * **Note:** Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must be
     * closed after usage to prevent resource leaks. As the stream is `AutoCloseable`, it is recommended to use it
     * within a `try-with-resources` block.
     *
     * @param ids a stream of projection IDs to retrieve from the repository.
     * @return a stream of projections corresponding to the provided primary keys. The order of projections in the stream is
     * not guaranteed to match the order of ids in the input stream. If an id does not correspond to any projection
     * in the database, it will simply be skipped, and no corresponding projection will be included in the returned
     * stream. If the same projection is requested multiple times, it may be included in the stream multiple times
     * if it is part of a separate batch.
     * @throws st.orm.PersistenceException if the selection operation fails due to underlying database issues, such as
     * connectivity.
     */
    fun selectById(ids: Stream<ID>): Stream<P>

    /**
     * Retrieves a stream of projections based on their primary keys.
     *
     *
     * This method executes queries in batches, depending on the number of primary keys in the specified ids stream.
     * This optimization aims to reduce the overhead of executing multiple queries and efficiently retrieve projections.
     * The batching strategy enhances performance, particularly when dealing with large sets of primary keys.
     *
     *
     * The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of projections.
     *
     *
     * **Note:** Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must be
     * closed after usage to prevent resource leaks. As the stream is `AutoCloseable`, it is recommended to use it
     * within a `try-with-resources` block.
     *
     * @param refs a stream of refs to retrieve from the repository.
     * @return a stream of projections corresponding to the provided primary keys. The order of projections in the stream is
     * not guaranteed to match the order of ids in the input stream. If an id does not correspond to any projection
     * in the database, it will simply be skipped, and no corresponding projection will be included in the returned
     * stream. If the same projection is requested multiple times, it may be included in the stream multiple times
     * if it is part of a separate batch.
     * @throws st.orm.PersistenceException if the selection operation fails due to underlying database issues, such as
     * connectivity.
     */
    fun selectByRef(refs: Stream<Ref<P>>): Stream<P>

    /**
     * Retrieves a stream of projections based on their primary keys.
     *
     *
     * This method executes queries in batches, with the batch size determined by the provided parameter. This
     * optimization aims to reduce the overhead of executing multiple queries and efficiently retrieve projections. The
     * batching strategy enhances performance, particularly when dealing with large sets of primary keys.
     *
     *
     * The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of projections.
     *
     *
     * **Note:** Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must be
     * closed after usage to prevent resource leaks. As the stream is `AutoCloseable`, it is recommended to use it
     * within a `try-with-resources` block.
     *
     * @param ids a stream of projection IDs to retrieve from the repository.
     * @param batchSize the number of primary keys to include in each batch. This parameter determines the size of the
     * batches used to execute the selection operation. A larger batch size can improve performance, especially when
     * dealing with large sets of primary keys.
     * @return a stream of projections corresponding to the provided primary keys. The order of projections in the stream is
     * not guaranteed to match the order of ids in the input stream. If an id does not correspond to any projection in the
     * database, it will simply be skipped, and no corresponding projection will be included in the returned stream. If the
     * same projection is requested multiple times, it may be included in the stream multiple times if it is part of a
     * separate batch.
     * @throws st.orm.PersistenceException if the selection operation fails due to underlying database issues, such as
     * connectivity.
     */
    fun selectById(ids: Stream<ID>, batchSize: Int): Stream<P>

    /**
     * Retrieves a stream of projections based on their primary keys.
     *
     *
     * This method executes queries in batches, with the batch size determined by the provided parameter. This
     * optimization aims to reduce the overhead of executing multiple queries and efficiently retrieve projections. The
     * batching strategy enhances performance, particularly when dealing with large sets of primary keys.
     *
     *
     * The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of projections.
     *
     *
     * **Note:** Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must be
     * closed after usage to prevent resource leaks. As the stream is `AutoCloseable`, it is recommended to use it
     * within a `try-with-resources` block.
     *
     * @param refs a stream of refs to retrieve from the repository.
     * @param batchSize the number of primary keys to include in each batch. This parameter determines the size of the
     * batches used to execute the selection operation. A larger batch size can improve performance, especially when
     * dealing with large sets of primary keys.
     * @return a stream of projections corresponding to the provided primary keys. The order of projections in the stream is
     * not guaranteed to match the order of refs in the input stream. If an id does not correspond to any projection in the
     * database, it will simply be skipped, and no corresponding projection will be included in the returned stream. If the
     * same projection is requested multiple times, it may be included in the stream multiple times if it is part of a
     * separate batch.
     * @throws st.orm.PersistenceException if the selection operation fails due to underlying database issues, such as
     * connectivity.
     */
    fun selectByRef(refs: Stream<Ref<P>>, batchSize: Int): Stream<P>

    /**
     * Counts the number of projections identified by the provided stream of IDs using the default batch size.
     *
     *
     * This method calculates the total number of projections that match the provided primary keys. The counting
     * is performed in batches, which helps optimize performance and manage database load when dealing with
     * large sets of IDs.
     *
     * @param ids a stream of IDs for which to count matching projections.
     * @return the total count of projections matching the provided IDs.
     * @throws st.orm.PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    fun countById(ids: Stream<ID>): Long

    /**
     * Counts the number of projections identified by the provided stream of IDs, with the counting process divided into
     * batches of the specified size.
     *
     *
     * This method performs the counting operation in batches, specified by the `batchSize` parameter. This
     * batching approach is particularly useful for efficiently handling large volumes of IDs, reducing the overhead on
     * the database and improving performance.
     *
     * @param ids a stream of IDs for which to count matching projections.
     * @param batchSize the size of the batches to use for the counting operation. A larger batch size can improve
     * performance but may also increase the load on the database.
     * @return the total count of projections matching the provided IDs.
     * @throws st.orm.PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    fun countById(ids: Stream<ID>, batchSize: Int): Long

    /**
     * Counts the number of projections identified by the provided stream of refs using the default batch size.
     *
     *
     * This method calculates the total number of projections that match the provided primary keys. The counting
     * is performed in batches, which helps optimize performance and manage database load when dealing with
     * large sets of IDs.
     *
     * @param refs a stream of refs for which to count matching projections.
     * @return the total count of projections matching the provided IDs.
     * @throws st.orm.PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    fun countByRef(refs: Stream<Ref<P>>): Long

    /**
     * Counts the number of projections identified by the provided stream of refs, with the counting process divided into
     * batches of the specified size.
     *
     *
     * This method performs the counting operation in batches, specified by the `batchSize` parameter. This
     * batching approach is particularly useful for efficiently handling large volumes of IDs, reducing the overhead on
     * the database and improving performance.
     *
     * @param refs a stream of refs for which to count matching projections.
     * @param batchSize the size of the batches to use for the counting operation. A larger batch size can improve
     * performance but may also increase the load on the database.
     * @return the total count of projections matching the provided IDs.
     * @throws st.orm.PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    fun countByRef(refs: Stream<Ref<P>>, batchSize: Int): Long

    // Kotlin specific DSL

    /**
     * Retrieves all entities of type [T] from the repository.
     *
     * @return a list containing all entities.
     */
    fun findAllRef(): List<Ref<P>> =
        selectRef().resultList

    /**
     * Retrieves all entities of type [T] from the repository.
     *
     * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
     * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of entities.
     *
     * Note: Calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
     * while in use, it must be closed after usage to prevent resource leaks.
     *
     * @return a sequence containing all entities.
     */
    fun selectAllRef(): Stream<Ref<P>> =
        selectRef().resultStream

    /**
     * Retrieves an optional entity of type [T] based on a single field and its value.
     * Returns null if no matching entity is found.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return an optional entity, or null if none found.
     */
    fun <V> findBy(field: Metamodel<P, V>, value: V): P? =
        select().where(field, EQUALS, value).optionalResult

    /**
     * Retrieves an optional entity of type [T] based on a single field and its value.
     * Returns null if no matching entity is found.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return an optional entity, or null if none found.
     */
    fun <V : Record> findBy(field: Metamodel<P, V>, value: Ref<V>): P? =
        select().where(field, value).optionalResult

    /**
     * Retrieves entities of type [T] matching a single field and a single value.
     * Returns an empty list if no entities are found.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return list of matching entities.
     */
    fun <V> findAllBy(field: Metamodel<P, V>, value: V): List<P> =
        select().where(field, EQUALS, value).resultList

    /**
     * Retrieves entities of type [T] matching a single field and a single value.
     * Returns an empty sequence if no entities are found.
     *
     * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
     * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of entities.
     *
     * Note: Calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
     * while in use, it must be closed after usage to prevent resource leaks.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return a sequence of matching entities.
     */
    fun <V> selectBy(field: Metamodel<P, V>, value: V): Stream<P> =
        select().where(field, EQUALS, value).resultStream

    /**
     * Retrieves entities of type [T] matching a single field and a single value.
     * Returns an empty list if no entities are found.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return a list of matching entities.
     */
    fun <V : Record> findAllBy(field: Metamodel<P, V>, value: Ref<V>): List<P> =
        select().where(field, value).resultList

    /**
     * Retrieves entities of type [T] matching a single field and a single value.
     * Returns an empty sequence if no entities are found.
     *
     * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
     * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of entities.
     *
     * Note: Calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
     * while in use, it must be closed after usage to prevent resource leaks.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return a sequence of matching entities.
     */
    fun <V : Record> selectBy(field: Metamodel<P, V>, value: Ref<V>): Stream<P> =
        select().where(field, value).resultStream

    /**
     * Retrieves entities of type [T] matching a single field against multiple values.
     * Returns an empty list if no entities are found.
     *
     * @param field metamodel reference of the entity field.
     * @param values Iterable of values to match against.
     * @return list of matching entities.
     */
    fun <V> findAllBy(field: Metamodel<P, V>, values: Iterable<V>): List<P> =
        select().where(field, IN, values).resultList

    /**
     * Retrieves entities of type [T] matching a single field against multiple values.
     * Returns an empty sequence if no entities are found.
     *
     * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
     * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of entities.
     *
     * Note: Calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
     * while in use, it must be closed after usage to prevent resource leaks.
     *
     * @param field metamodel reference of the entity field.
     * @param values Iterable of values to match against.
     * @return at sequence of matching entities.
     */
    fun <V> selectBy(field: Metamodel<P, V>, values: Iterable<V>): Stream<P> =
        select().where(field, IN, values).resultStream

    /**
     * Retrieves entities of type [T] matching a single field against multiple values.
     * Returns an empty list if no entities are found.
     *
     * @param field metamodel reference of the entity field.
     * @param values Iterable of values to match against.
     * @return a list of matching entities.
     */
    fun <V : Record> findAllByRef(field: Metamodel<P, V>, values: Iterable<Ref<V>>): List<P> =
        select().whereRef(field, values).resultList

    /**
     * Retrieves entities of type [T] matching a single field against multiple values.
     * Returns an empty sequence if no entities are found.
     *
     * @param field metamodel reference of the entity field.
     * @param values Iterable of values to match against.
     * @return a sequence of matching entities.
     */
    fun <V : Record> selectByRef(field: Metamodel<P, V>, values: Iterable<Ref<V>>): Stream<P> =
        select().whereRef(field, values).resultStream

    /**
     * Retrieves exactly one entity of type [T] based on a single field and its value.
     * Throws an exception if no entity or more than one entity is found.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return the matching entity.
     * @throws st.orm.NoResultException if there is no result.
     * @throws st.orm.NonUniqueResultException if more than one result.
     */
    fun <V> getBy(field: Metamodel<P, V>, value: V): P =
        select().where(field, EQUALS, value).singleResult

    /**
     * Retrieves exactly one entity of type [T] based on a single field and its value.
     * Throws an exception if no entity or more than one entity is found.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return the matching entity.
     * @throws st.orm.NoResultException if there is no result.
     * @throws st.orm.NonUniqueResultException if more than one result.
     */
    fun <V : Record> getBy(field: Metamodel<P, V>, value: Ref<V>): P =
        select().where(field, value).singleResult

    /**
     * Retrieves an optional entity of type [T] based on a single field and its value.
     * Returns a ref with a null value if no matching entity is found.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return an optional entity, or null if none found.
     */
    fun <T, ID, V> findRefBy(field: Metamodel<P, V>, value: V): Ref<P> =
        selectRef().where(field, EQUALS, value).optionalResult ?: Ref.ofNull()

    /**
     * Retrieves an optional entity of type [T] based on a single field and its value.
     * Returns a ref with a null value if no matching entity is found.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return an optional entity, or null if none found.
     */
    fun <V : Record> findRefBy(field: Metamodel<P, V>, value: Ref<V>): Ref<P> =
        selectRef().where(field, value).optionalResult ?: Ref.ofNull()

    /**
     * Retrieves entities of type [T] matching a single field and a single value.
     * Returns an empty list if no entities are found.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return a list of matching entities.
     */
    fun <V> findAllRefBy(field: Metamodel<P, V>, value: V): List<Ref<P>> =
        selectRef().where(field, EQUALS, value).resultList

    /**
     * Retrieves entities of type [T] matching a single field and a single value.
     * Returns an empty sequence if no entities are found.
     *
     * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
     * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of entities.
     *
     * Note: Calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
     * while in use, it must be closed after usage to prevent resource leaks.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return a sequence of matching entities.
     */
    fun <V> selectRefBy(field: Metamodel<P, V>, value: V): Stream<Ref<P>> =
        selectRef().where(field, EQUALS, value).resultStream

    /**
     * Retrieves entities of type [T] matching a single field and a single value.
     * Returns an empty list if no entities are found.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return a list of matching entities.
     */
    fun <V : Record> findAllRefBy(field: Metamodel<P, V>, value: Ref<V>): List<Ref<P>> =
        selectRef().where(field, value).resultList

    /**
     * Retrieves entities of type [T] matching a single field and a single value.
     * Returns an empty sequence if no entities are found.
     *
     * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
     * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of entities.
     *
     * Note: Calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
     * while in use, it must be closed after usage to prevent resource leaks.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return a sequence of matching entities.
     */
    fun <V : Record> selectRefBy(field: Metamodel<P, V>, value: Ref<V>): Stream<Ref<P>> =
        selectRef().where(field, value).resultStream

    /**
     * Retrieves entities of type [T] matching a single field against multiple values.
     * Returns an empty list if no entities are found.
     *
     * @param field metamodel reference of the entity field.
     * @param values Iterable of values to match against.
     * @return a list of matching entities.
     */
    fun <V : Record> findAllRefBy(field: Metamodel<P, V>, values: Iterable<V>): List<Ref<P>> =
        selectRef().where(field, IN, values).resultList

    /**
     * Retrieves entities of type [T] matching a single field against multiple values.
     * Returns an empty sequence if no entities are found.
     *
     * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
     * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of entities.
     *
     * Note: Calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
     * while in use, it must be closed after usage to prevent resource leaks.
     *
     * @param field metamodel reference of the entity field.
     * @param values Iterable of values to match against.
     * @return a sequence of matching entities.
     */
    fun <V> selectRefBy(field: Metamodel<P, V>, values: Iterable<V>): Stream<Ref<P>> =
        selectRef().where(field, IN, values).resultStream

    /**
     * Retrieves entities of type [T] matching a single field against multiple values.
     * Returns an empty list if no entities are found.
     *
     * @param field metamodel reference of the entity field.
     * @param values Iterable of values to match against.
     * @return a list of matching entities.
     */
    fun <V : Record> findAllRefByRef(field: Metamodel<P, V>, values: Iterable<Ref<V>>): List<Ref<P>> =
        selectRef().whereRef(field, values).resultList

    /**
     * Retrieves entities of type [T] matching a single field against multiple values.
     * Returns an empty sequence if no entities are found.
     *
     * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
     * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of entities.
     *
     * Note: Calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
     * while in use, it must be closed after usage to prevent resource leaks.
     *
     * @param field metamodel reference of the entity field.
     * @param values Iterable of values to match against.
     * @return a sequence of matching entities.
     */
    fun <V : Record> selectRefByRef(field: Metamodel<P, V>, values: Iterable<Ref<V>>): Stream<Ref<P>> =
        selectRef().whereRef(field, values).resultStream

    /**
     * Retrieves exactly one entity of type [T] based on a single field and its value.
     * Throws an exception if no entity or more than one entity is found.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return the matching entity.
     * @throws st.orm.NoResultException if there is no result.
     * @throws st.orm.NonUniqueResultException if more than one result.
     */
    fun <V> getRefBy(field: Metamodel<P, V>, value: V): Ref<P> =
        selectRef().where(field, EQUALS, value).singleResult

    /**
     * Retrieves exactly one entity of type [T] based on a single field and its value.
     * Throws an exception if no entity or more than one entity is found.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return the matching entity.
     * @throws st.orm.NoResultException if there is no result.
     * @throws st.orm.NonUniqueResultException if more than one result.
     */
    fun <V : Record> getRefBy(field: Metamodel<P, V>, value: Ref<V>): Ref<P> =
        selectRef().where(field, value).singleResult

    /**
     * Retrieves entities of type [T] matching the specified predicate.
     *
     * @return a list of matching entities.
     */
    fun findAll(predicate: WhereBuilder<P, P, ID>.() -> PredicateBuilder<P, *, *>): List<P> =
        select().whereBuilder(predicate).resultList

    /**
     * Retrieves entities of type [T] matching the specified predicate.
     *
     * @return a list of matching entities.
     */
    fun findAll(predicate: PredicateBuilder<P, *, *>): List<P> =
        select().where(predicate).resultList

    /**
     * Retrieves entities of type [T] matching the specified predicate.
     *
     * @return a list of matching entities.
     */
    fun findAllRef(
        predicate: WhereBuilder<P, Ref<P>, ID>.() -> PredicateBuilder<P, *, *>
    ): List<Ref<P>> =
        selectRef().whereBuilder(predicate).resultList

    /**
     * Retrieves entities of type [T] matching the specified predicate.
     *
     * @return a list of matching entities.
     */
    fun findAllRef(predicate: PredicateBuilder<P, *, *>): List<Ref<P>> =
        selectRef().where(predicate).resultList

    /**
     * Retrieves an optional entity of type [T] matching the specified predicate.
     * Returns null if no matching entity is found.
     *
     * @return an optional entity, or null if none found.
     */
    fun find(
        predicate: WhereBuilder<P, P, ID>.() -> PredicateBuilder<P, *, *>
    ): P? =
        select().whereBuilder(predicate).optionalResult

    /**
     * Retrieves an optional entity of type [T] matching the specified predicate.
     * Returns null if no matching entity is found.
     *
     * @return an optional entity, or null if none found.
     */
    fun find(
        predicate: PredicateBuilder<P, *, *>
    ): P? =
        select().where(predicate).optionalResult

    /**
     * Retrieves an optional entity of type [T] matching the specified predicate.
     * Returns null if no matching entity is found.
     *
     * @return an optional entity, or null if none found.
     */
    fun findRef(
        predicate: WhereBuilder<P, Ref<P>, ID>.() -> PredicateBuilder<P, *, *>
    ): Ref<P> =
        selectRef().whereBuilder(predicate).optionalResult ?: Ref.ofNull()

    /**
     * Retrieves an optional entity of type [T] matching the specified predicate.
     * Returns a ref with a null value if no matching entity is found.
     *
     * @return an optional entity, or null if none found.
     */
    fun findRef(
        predicate: PredicateBuilder<P, *, *>
    ): Ref<P> =
        selectRef().where(predicate).optionalResult ?: Ref.ofNull()

    /**
     * Retrieves a single entity of type [T] matching the specified predicate.
     * Throws an exception if no entity or more than one entity is found.
     *
     * @return the matching entity.
     * @throws st.orm.NoResultException if there is no result.
     * @throws st.orm.NonUniqueResultException if more than one result.
     */
    fun get(
        predicate: WhereBuilder<P, P, ID>.() -> PredicateBuilder<P, *, *>
    ): P =
        select().whereBuilder(predicate).singleResult

    /**
     * Retrieves a single entity of type [T] matching the specified predicate.
     * Throws an exception if no entity or more than one entity is found.
     *
     * @return the matching entity.
     * @throws st.orm.NoResultException if there is no result.
     * @throws st.orm.NonUniqueResultException if more than one result.
     */
    fun get(
        predicate: PredicateBuilder<P, *, *>
    ): P =
        select().where(predicate).singleResult

    /**
     * Retrieves a single entity of type [T] matching the specified predicate.
     * Throws an exception if no entity or more than one entity is found.
     *
     * @return the matching entity.
     * @throws st.orm.NoResultException if there is no result.
     * @throws st.orm.NonUniqueResultException if more than one result.
     */
    fun getRef(
        predicate: WhereBuilder<P, Ref<P>, ID>.() -> PredicateBuilder<P, *, *>
    ): Ref<P> =
        selectRef().whereBuilder(predicate).singleResult

    /**
     * Retrieves a single entity of type [T] matching the specified predicate.
     * Throws an exception if no entity or more than one entity is found.
     *
     * @return the matching entity.
     * @throws st.orm.NoResultException if there is no result.
     * @throws st.orm.NonUniqueResultException if more than one result.
     */
    fun getRef(
        predicate: PredicateBuilder<P, *, *>
    ): Ref<P> =
        selectRef().where(predicate).singleResult

    /**
     * Retrieves entities of type [T] matching the specified predicate.
     *
     * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
     * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of entities.
     *
     * Note: Calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
     * while in use, it must be closed after usage to prevent resource leaks.
     *
     * @return a sequence of matching entities.
     */
    fun select(
        predicate: WhereBuilder<P, P, ID>.() -> PredicateBuilder<P, *, *>
    ): Stream<P> =
        select().whereBuilder(predicate).resultStream

    /**
     * Retrieves entities of type [T] matching the specified predicate.
     *
     * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
     * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of entities.
     *
     * Note: Calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
     * while in use, it must be closed after usage to prevent resource leaks.
     *
     * @return a sequence of matching entities.
     */
    fun select(
        predicate: PredicateBuilder<P, *, *>
    ): Stream<P> =
        select().where(predicate).resultStream

    /**
     * Retrieves entities of type [T] matching the specified predicate.
     *
     * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
     * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of entities.
     *
     * Note: Calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
     * while in use, it must be closed after usage to prevent resource leaks.
     *
     * @return a sequence of matching entities.
     */
    fun selectRef(
        predicate: WhereBuilder<P, Ref<P>, ID>.() -> PredicateBuilder<P, *, *>
    ): Stream<Ref<P>> =
        selectRef().whereBuilder(predicate).resultStream

    /**
     * Retrieves entities of type [T] matching the specified predicate.
     *
     * The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
     * are consumed by the sequence. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of entities.
     *
     * Note: Calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
     * while in use, it must be closed after usage to prevent resource leaks.
     *
     * @return a sequence of matching entities.
     */
    fun selectRef(
        predicate: PredicateBuilder<P, *, *>
    ): Stream<Ref<P>> =
        selectRef().where(predicate).resultStream

    /**
     * Counts entities of type [T] matching the specified field and value.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return the count of matching entities.
     */
    fun <V> countBy(
        field: Metamodel<P, V>,
        value: V
    ): Long =
        selectCount().where(field, EQUALS, value).singleResult

    /**
     * Counts entities of type [T] matching the specified field and referenced value.
     *
     * @param field metamodel reference of the entity field.
     * @param value the referenced value to match against.
     * @return the count of matching entities.
     */
    fun <V : Record> countBy(
        field: Metamodel<P, V>,
        value: Ref<V>
    ): Long =
        selectCount().where(field, value).singleResult

    /**
     * Counts entities of type [T] matching the specified predicate.
     *
     * @param predicate Lambda to build the WHERE clause.
     * @return the count of matching entities.
     */
    fun count(
        predicate: WhereBuilder<P, *, ID>.() -> PredicateBuilder<P, *, *>
    ): Long =
        selectCount().whereBuilder(predicate).singleResult

    /**
     * Counts entities of type [T] matching the specified predicate.
     *
     * @param predicate Lambda to build the WHERE clause.
     * @return the count of matching entities.
     */
    fun count(
        predicate: PredicateBuilder<P, *, *>
    ): Long =
        selectCount().where(predicate).singleResult

    /**
     * Checks if entities of type [T] matching the specified field and value exists.
     *
     * @param field metamodel reference of the entity field.
     * @param value the value to match against.
     * @return true if any matching entities exist, false otherwise.
     */
    fun <V> existsBy(
        field: Metamodel<P, V>,
        value: V
    ): Boolean =
        selectCount().where(field, EQUALS, value).singleResult > 0

    /**
     * Checks if entities of type [T] matching the specified field and referenced value exists.
     *
     * @param field metamodel reference of the entity field.
     * @param value the referenced value to match against.
     * @return true if any matching entities exist, false otherwise.
     */
    fun <V : Record> existsBy(
        field: Metamodel<P, V>,
        value: Ref<V>
    ): Boolean =
        selectCount().where(field, value).singleResult > 0

    /**
     * Checks if entities of type [T] matching the specified predicate exists.
     *
     * @param predicate Lambda to build the WHERE clause.
     * @return true if any matching entities exist, false otherwise.
     */
    fun exists(
        predicate: WhereBuilder<P, *, ID>.() -> PredicateBuilder<P, *, *>
    ): Boolean =
        selectCount().whereBuilder(predicate).singleResult > 0

    /**
     * Checks if entities of type [T] matching the specified predicate exists.
     *
     * @param predicate Lambda to build the WHERE clause.
     * @return true if any matching entities exist, false otherwise.
     */
    fun exists(
        predicate: PredicateBuilder<P, *, *>
    ): Boolean =
        selectCount().where(predicate).singleResult > 0
}
