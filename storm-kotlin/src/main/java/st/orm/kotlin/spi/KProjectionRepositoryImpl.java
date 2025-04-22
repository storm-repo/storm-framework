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
package st.orm.kotlin.spi;

import jakarta.annotation.Nonnull;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import st.orm.Ref;
import st.orm.NoResultException;
import st.orm.PersistenceException;
import st.orm.kotlin.KResultCallback;
import st.orm.kotlin.template.KModel;
import st.orm.kotlin.repository.KProjectionRepository;
import st.orm.kotlin.template.KORMTemplate;
import st.orm.kotlin.template.KQueryBuilder;
import st.orm.kotlin.template.impl.KModelImpl;
import st.orm.kotlin.template.impl.KORMTemplateImpl;
import st.orm.kotlin.template.impl.KQueryBuilderImpl;
import st.orm.repository.Projection;
import st.orm.repository.ProjectionRepository;
import st.orm.template.impl.ModelImpl;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;
import static java.util.Spliterators.spliteratorUnknownSize;
import static kotlin.sequences.SequencesKt.sequenceOf;

/**
 */
public final class KProjectionRepositoryImpl<P extends Record & Projection<ID>, ID> implements KProjectionRepository<P, ID> {
    private final ProjectionRepository<P, ID> projectionRepository;

    public KProjectionRepositoryImpl(@Nonnull ProjectionRepository<P, ID> projectionRepository) {
        this.projectionRepository = requireNonNull(projectionRepository);
    }

    private static <X> Sequence<X> toSequence(@Nonnull Stream<X> stream) {
        return SequencesKt.asSequence(stream.iterator());
    }

    private static <X> Stream<X> toStream(@Nonnull Sequence<X> sequence) {
        Iterator<X> iterator = sequence.iterator();
        Spliterator<X> spliterator = spliteratorUnknownSize(iterator, Spliterator.ORDERED);
        return StreamSupport.stream(spliterator, false);
    }

    /**
     * Returns the projection model associated with this repository.
     *
     * @return the projection model.
     */
    @Override
    public KModel<P, ID> model() {
        return new KModelImpl<>((ModelImpl<P, ID>) projectionRepository.model());
    }

    /**
     * Creates a new ref projection instance with the specified primary key.
     *
     * @param id the primary key of the projection.
     * @return a ref projection instance.
     */
    @Override
    public Ref<P> ref(@Nonnull ID id) {
        return projectionRepository.ref(id);
    }

    /**
     * Creates a new ref projection instance with the specified projection.
     *
     * @param projection the projection.
     * @return a ref projection instance.
     */
    @Override
    public Ref<P> ref(@Nonnull P projection, @Nonnull ID id) {
        return projectionRepository.ref(projection, id);
    }

    /**
     * Creates a new query builder for the projection type managed by this repository.
     *
     * @return a new query builder for the projection type.
     */
    @Override
    public KQueryBuilder<P, P, ID> select() {
        return new KQueryBuilderImpl<>(projectionRepository.select());
    }

    /**
     * Creates a new query builder for the projection type managed by this repository.
     *
     * @return a new query builder for the projection type.
     */
    @Override
    public KQueryBuilder<P, Long, ID> selectCount() {
        return new KQueryBuilderImpl<>(projectionRepository.selectCount());
    }

    /**
     * Creates a new query builder for the custom {@code selectType}.
     *
     * @param selectType the result type of the query.
     * @return a new query builder for the custom {@code selectType}.
     * @param <R> the result type of the query.
     */
    @Override
    public <R> KQueryBuilder<P, R, ID> select(@Nonnull Class<R> selectType) {
        return new KQueryBuilderImpl<>(projectionRepository.select(selectType));
    }

    /**
     * Creates a new query builder for the custom {@code selectType} and custom {@code template} for the select clause.
     *
     * @param selectType the result type of the query.
     * @param template the custom template for the select clause.
     * @return a new query builder for the custom {@code selectType}.
     * @param <R> the result type of the query.
     */
    @Override
    public <R> KQueryBuilder<P, R, ID> select(@Nonnull Class<R> selectType, @Nonnull StringTemplate template) {
        return new KQueryBuilderImpl<>(projectionRepository.select(selectType, template));
    }

    /**
     * Provides access to the underlying ORM template.
     *
     * @return the ORM template.
     */
    @Override
    public KORMTemplate orm() {
        return new KORMTemplateImpl(projectionRepository.orm());
    }

    /**
     * Returns the number of projections in the database of the projection type supported by this repository.
     *
     * @return the total number of projections in the database as a long value.
     * @throws PersistenceException if the count operation fails due to underlying database issues, such as
     * connectivity.
     */
    @Override
    public long count() {
        return projectionRepository.count();
    }

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
    @Override
    public boolean existsById(@Nonnull ID id) {
        return projectionRepository.existsById(id);
    }

    /**
     * Checks if a projection with the specified primary key exists in the database.
     *
     * <p>This method determines the presence of a projection by checking if the count of projections with the given primary
     * key is greater than zero. It leverages the {@code selectCount} method, which performs a count operation on the
     * database.</p>
     *
     * @param ref the primary key of the projection to check for existence.
     * @return true if a projection with the specified primary key exists, false otherwise.
     * @throws PersistenceException if there is an underlying database issue during the count operation.
     */
    @Override
    public boolean existsByRef(@Nonnull Ref<P> ref) {
        return projectionRepository.existsByRef(ref);
    }

    // Singular findBy methods.

    /**
     * Retrieves a projection based on its primary key.
     *
     * <p>This method performs a lookup in the database, returning the corresponding projection if it exists.</p>
     *
     * @param id the primary key of the projection to retrieve.
     * @return the projection associated with the provided primary key. The returned projection encapsulates all relevant data
     * as mapped by the projection model.
     * @throws PersistenceException if the retrieval operation fails due to underlying database issues, such as
     *                              connectivity problems or query execution errors.
     */
    @Override
    public Optional<P> findById(@Nonnull ID id) {
        return projectionRepository.findById(id);
    }

    /**
     * Retrieves a projection based on its primary key.
     *
     * <p>This method performs a lookup in the database, returning the corresponding projection if it exists.</p>
     *
     * @param ref the ref to match.
     * @return the projection associated with the provided primary key. The returned projection encapsulates all relevant data
     * as mapped by the projection model.
     * @throws PersistenceException if the retrieval operation fails due to underlying database issues, such as
     *                              connectivity problems or query execution errors.
     */
    @Override
    public Optional<P> findByRef(@Nonnull Ref<P> ref) {
        return projectionRepository.findByRef(ref);
    }

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
    @Override
    public P getById(@Nonnull ID id) {
        return projectionRepository.getById(id);
    }

    /**
     * Retrieves a projection based on its primary key.
     *
     * <p>This method performs a lookup in the database, returning the corresponding projection if it exists.</p>
     *
     * @param ref the ref to match.
     * @return the projection associated with the provided primary key. The returned projection encapsulates all relevant data
     * as mapped by the projection model.
     * @throws NoResultException if no projection is found matching the given primary key, indicating that there's no
     *                           corresponding data in the database.
     * @throws PersistenceException if the retrieval operation fails due to underlying database issues, such as
     *                              connectivity problems or query execution errors.
     */
    @Override
    public P getByRef(@Nonnull Ref<P> ref) {
        return projectionRepository.getByRef(ref);
    }

    // List based methods.

    /**
     * Retrieves a list of projections based on their primary keys.
     *
     * <p>This method retrieves projections matching the provided IDs in batches, consolidating them into a single list.
     * The batch-based retrieval minimizes database overhead, allowing efficient handling of larger collections of IDs.
     * Note that the order of projections in the returned list is not guaranteed to match the order of IDs in the input
     * collection, as the database may not preserve insertion order during retrieval.</p>
     *
     * @param ids the primary keys of the projections to retrieve, represented as an iterable collection.
     * @return a list of projections corresponding to the provided primary keys. Projections are returned without any
     *         guarantee of order alignment with the input list. If an ID does not correspond to any projection in the
     *         database, no corresponding projection will be included in the returned list.
     * @throws PersistenceException if the selection operation fails due to database issues, such as connectivity
     *         problems or invalid input parameters.
     */
    @Override
    public List<P> findAllById(@Nonnull Iterable<ID> ids) {
        return projectionRepository.findAllById(ids);
    }

    /**
     * Retrieves a list of projections based on their primary keys.
     *
     * <p>This method retrieves projections matching the provided IDs in batches, consolidating them into a single list.
     * The batch-based retrieval minimizes database overhead, allowing efficient handling of larger collections of IDs.
     * Note that the order of projections in the returned list is not guaranteed to match the order of IDs in the input
     * collection, as the database may not preserve insertion order during retrieval.</p>
     *
     * @param refs the primary keys of the projections to retrieve, represented as an iterable collection.
     * @return a list of projections corresponding to the provided primary keys. Projections are returned without any
     *         guarantee of order alignment with the input list. If an ID does not correspond to any projection in the
     *         database, no corresponding projection will be included in the returned list.
     * @throws PersistenceException if the selection operation fails due to database issues, such as connectivity
     *         problems or invalid input parameters.
     */
    @Override
    public List<P> findAllByRef(@Nonnull Iterable<Ref<P>> refs) {
        return projectionRepository.findAllByRef(refs);
    }

    // Stream based methods. These methods operate in multiple batches.

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
    @Override
    public <R> R findAll(@Nonnull KResultCallback<P, R> callback) {
        return projectionRepository.findAll(stream -> callback.process(toSequence(stream)));
    }

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
    @Override
    public <R> R findAllById(@Nonnull Sequence<ID> ids, @Nonnull KResultCallback<P, R> callback) {
        return projectionRepository.findAllById(toStream(ids), stream -> callback.process(toSequence(stream)));
    }

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
    @Override
    public <R> R findAllById(@Nonnull Sequence<ID> ids, int batchSize, @Nonnull KResultCallback<P, R> callback) {
        return projectionRepository.findAllById(toStream(ids), batchSize, stream -> callback.process(toSequence(stream)));
    }

    /**
     * Processes a sequence of projections corresponding to the provided refs using the specified callback.
     * This method retrieves projections matching the given IDs and applies the callback to process the results,
     * returning the outcome produced by the callback.
     *
     * <p>This method is designed for efficient data handling by only retrieving specified projections as needed.
     * It also manages lifecycle of the underlying resources of the callback sequence, automatically closing those
     * resources after processing to prevent resource leaks.</p>
     *
     * @param refs a sequence of refs to retrieve from the repository.
     * @param callback a {@link KResultCallback} defining how to process the sequence of projections and produce a result.
     * @param <R> the type of result produced by the callback after processing the projections.
     * @return the result produced by the callback's processing of the projection sequence.
     * @throws PersistenceException if the operation fails due to underlying database issues, such as connectivity.
     */
    @Override
    public <R> R findAllByRef(@Nonnull Sequence<Ref<P>> refs, @Nonnull KResultCallback<P, R> callback) {
        return projectionRepository.findAllByRef(toStream(refs), stream -> callback.process(toSequence(stream)));
    }

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
     * @param refs a sequence of refs to retrieve from the repository.
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
    @Override
    public <R> R findAllByRef(@Nonnull Sequence<Ref<P>> refs, int batchSize, @Nonnull KResultCallback<P, R> callback) {
        return projectionRepository.findAllByRef(toStream(refs), batchSize, stream -> callback.process(toSequence(stream)));
    }

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
    @Override
    public long countById(@Nonnull Sequence<ID> ids) {
        return projectionRepository.countById(toStream(ids));
    }

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
    @Override
    public long countById(@Nonnull Sequence<ID> ids, int batchSize) {
        return projectionRepository.countById(toStream(ids), batchSize);
    }

    /**
     * Counts the number of projections identified by the provided sequence of IDs using the default batch size.
     *
     * <p>This method calculates the total number of projections that match the provided primary keys. The counting
     * is performed in batches, which helps optimize performance and manage database load when dealing with
     * large sets of IDs.</p>
     *
     * @param refs a sequence of refs for which to count matching projections.
     * @return the total count of projections matching the provided IDs.
     * @throws PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    @Override
    public long countByRef(@Nonnull Sequence<Ref<P>> refs) {
        return projectionRepository.countByRef(toStream(refs));
    }

    /**
     * Counts the number of projections identified by the provided sequence of IDs, with the counting process divided into
     * batches of the specified size.
     *
     * <p>This method performs the counting operation in batches, specified by the {@code batchSize} parameter. This
     * batching approach is particularly useful for efficiently handling large volumes of IDs, reducing the overhead on
     * the database and improving performance.</p>
     *
     * @param refs a sequence of refs for which to count matching projections.
     * @param batchSize the size of the batches to use for the counting operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @return the total count of projections matching the provided IDs.
     * @throws PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    @Override
    public long countByRef(@Nonnull Sequence<Ref<P>> refs, int batchSize) {
        return projectionRepository.countByRef(toStream(refs), batchSize);
    }
}
