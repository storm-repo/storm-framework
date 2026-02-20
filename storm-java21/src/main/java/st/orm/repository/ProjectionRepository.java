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
package st.orm.repository;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import st.orm.Data;
import st.orm.Metamodel;
import st.orm.NoResultException;
import st.orm.PersistenceException;
import st.orm.Projection;
import st.orm.Ref;
import st.orm.Slice;
import st.orm.template.Model;
import st.orm.template.QueryBuilder;

/**
 * Provides a generic interface with read operations for projections.
 *
 * <p>Projection repositories provide a high-level abstraction for reading projections in the database. They offer a
 * set of methods for reading projections, as well as querying and filtering entities based on specific criteria. The
 * repository interface is designed to work with entity records that implement the {@link Projection} interface,
 * providing a consistent and type-safe way to interact with the database.</p>
 *
 * @since 1.1
 * @see QueryBuilder
 * @param <P> the type of projection managed by this repository.
 * @param <ID> the type of the primary key of the projection, or {@link Void} if the projection has no primary key.
 */
public interface ProjectionRepository<P extends Projection<ID>, ID> extends Repository {

    /**
     * Returns the projection model associated with this repository.
     *
     * @return the projection model.
     */
    Model<P, ID> model();

    /**
     * Creates a new ref projection instance with the specified primary key.
     *
     * @param id the primary key of the projection.
     * @return a ref projection instance.
     */
    Ref<P> ref(@Nonnull ID id);

    /**
     * Creates a new ref projection instance with the specified projection.
     *
     * @param projection the projection.
     * @return a ref projection instance.
     */
    Ref<P> ref(@Nonnull P projection, @Nonnull ID id);

    // Query builder methods.

    /**
     * Creates a new query builder for the projection type managed by this repository.
     *
     * @return a new query builder for the projection type.
     */
    QueryBuilder<P, P, ID> select();

    /**
     * Creates a new query builder for the projection type managed by this repository.
     *
     * @return a new query builder for the projection type.
     */
    QueryBuilder<P, Long, ID> selectCount();

    /**
     * Creates a new query builder for the custom {@code selectType}.
     *
     * @param selectType the result type of the query.
     * @return a new query builder for the custom {@code selectType}.
     * @param <R> the result type of the query.
     */
    <R> QueryBuilder<P, R, ID> select(@Nonnull Class<R> selectType);

    /**
     * Creates a new query builder for selecting refs to projections of the type managed by this repository.
     *
     * <p>This method is typically used when you only need the primary keys of the projection initially, and you want to
     * defer fetching the full data until it is actually required. The query builder will return ref instances that
     * encapsulate the primary key. To retrieve the full entity, call {@link Ref#fetch()}, which will perform an
     * additional database query on demand.</p>
     *
     * @return a new query builder for selecting refs to projections.
     * @since 1.3
     */
    QueryBuilder<P, Ref<P>, ID> selectRef();

    /**
     * Creates a new query builder for the custom {@code selectType} and custom {@code template} for the select clause.
     *
     * @param selectType the result type of the query.
     * @param template the custom template for the select clause.
     * @return a new query builder for the custom {@code selectType}.
     * @param <R> the result type of the query.
     */
    <R> QueryBuilder<P, R, ID> select(@Nonnull Class<R> selectType, @Nonnull StringTemplate template);

    /**
     * Creates a new query builder for selecting refs to projections of the type managed by this repository.
     *
     * <p>This method is typically used when you only need the primary keys of the projections initially, and you want to
     * defer fetching the full data until it is actually required. The query builder will return ref instances that
     * encapsulate the primary key. To retrieve the full projection, call {@link Ref#fetch()}, which will perform an
     * additional database query on demand.</p>
     *
     * @param refType the type that is selected as ref.
     * @return a new query builder for selecting refs to projections.
     * @since 1.3
     */
    <R extends Data> QueryBuilder<P, Ref<R>, ID> selectRef(@Nonnull Class<R> refType);

    // Base methods.

    /**
     * Returns the number of projections in the database of the projection type supported by this repository.
     *
     * @return the total number of projections in the database as a long value.
     * @throws PersistenceException if the count operation fails due to underlying database issues, such as
     * connectivity.
     */
    long count();

    /**
     * Checks if any projection of the type managed by this repository exists in the database.
     *
     * @return true if at least one projection exists, false otherwise.
     * @throws PersistenceException if there is an underlying database issue during the count operation.
     */
    boolean exists();

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
    boolean existsById(@Nonnull ID id);

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
    boolean existsByRef(@Nonnull Ref<P> ref);

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
    Optional<P> findById(@Nonnull ID id);

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
    Optional<P> findByRef(@Nonnull Ref<P> ref);

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
    P getById(@Nonnull ID id);

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
    P getByRef(@Nonnull Ref<P> ref);

    // Slice methods.

    /**
     * Returns the first slice of projections ordered by the specified key.
     *
     * <p>This method performs keyset pagination, returning at most {@code size} projections ordered by the given key
     * in ascending order. The returned slice indicates whether more results are available beyond the current page.</p>
     *
     * @param key the metamodel path for a unique column used for ordering and as keyset cursor.
     * @param size the maximum number of projections to include in the slice.
     * @param <V> the type of the key field.
     * @return a slice containing the first page of results.
     * @throws PersistenceException if the query fails due to underlying database issues.
     * @since 1.9
     */
    default <V> Slice<P> slice(@Nonnull Metamodel<P, V> key, int size) {
        return select().slice(key, size);
    }

    /**
     * Returns the next slice of projections after the specified cursor value.
     *
     * <p>This method performs forward keyset pagination, returning projections where the key is greater than
     * {@code after}, ordered ascending by the key. The returned slice indicates whether more results are available.</p>
     *
     * @param key the metamodel path for a unique column used for ordering and as keyset cursor.
     * @param after the cursor value; only projections with a key value greater than this will be returned.
     * @param size the maximum number of projections to include in the slice.
     * @param <V> the type of the key field.
     * @return a slice containing the next page of results.
     * @throws PersistenceException if the query fails due to underlying database issues.
     * @since 1.9
     */
    default <V> Slice<P> sliceAfter(@Nonnull Metamodel<P, V> key, @Nonnull V after, int size) {
        return select().sliceAfter(key, after, size);
    }

    /**
     * Returns the previous slice of projections before the specified cursor value.
     *
     * <p>This method performs backward keyset pagination, returning projections where the key is less than
     * {@code before}, ordered descending by the key. The returned slice indicates whether more results are
     * available.</p>
     *
     * @param key the metamodel path for a unique column used for ordering and as keyset cursor.
     * @param before the cursor value; only projections with a key value less than this will be returned.
     * @param size the maximum number of projections to include in the slice.
     * @param <V> the type of the key field.
     * @return a slice containing the previous page of results.
     * @throws PersistenceException if the query fails due to underlying database issues.
     * @since 1.9
     */
    default <V> Slice<P> sliceBefore(@Nonnull Metamodel<P, V> key, @Nonnull V before, int size) {
        return select().sliceBefore(key, before, size);
    }

    /**
     * Returns the first slice of projection refs ordered by the specified key.
     *
     * <p>This method performs keyset pagination, returning at most {@code size} refs ordered by the given key in
     * ascending order. Only the primary key is loaded for each projection; the full projection can be fetched on demand
     * via {@link Ref#fetch()}. The returned slice indicates whether more results are available beyond the current
     * page.</p>
     *
     * @param key the metamodel path for a unique column used for ordering and as keyset cursor.
     * @param size the maximum number of refs to include in the slice.
     * @param <V> the type of the key field.
     * @return a slice containing the first page of ref results.
     * @throws PersistenceException if the query fails due to underlying database issues.
     * @since 1.9
     */
    default <V> Slice<Ref<P>> sliceRef(@Nonnull Metamodel<P, V> key, int size) {
        return selectRef().slice(key, size);
    }

    /**
     * Returns the next slice of projection refs after the specified cursor value.
     *
     * <p>This method performs forward keyset pagination, returning refs where the key is greater than {@code after},
     * ordered ascending by the key. Only the primary key is loaded for each projection; the full projection can be
     * fetched on demand via {@link Ref#fetch()}. The returned slice indicates whether more results are available.</p>
     *
     * @param key the metamodel path for a unique column used for ordering and as keyset cursor.
     * @param after the cursor value; only projections with a key value greater than this will be returned.
     * @param size the maximum number of refs to include in the slice.
     * @param <V> the type of the key field.
     * @return a slice containing the next page of ref results.
     * @throws PersistenceException if the query fails due to underlying database issues.
     * @since 1.9
     */
    default <V> Slice<Ref<P>> sliceAfterRef(@Nonnull Metamodel<P, V> key, @Nonnull V after, int size) {
        return selectRef().sliceAfter(key, after, size);
    }

    /**
     * Returns the previous slice of projection refs before the specified cursor value.
     *
     * <p>This method performs backward keyset pagination, returning refs where the key is less than {@code before},
     * ordered descending by the key. Only the primary key is loaded for each projection; the full projection can be
     * fetched on demand via {@link Ref#fetch()}. The returned slice indicates whether more results are available.</p>
     *
     * @param key the metamodel path for a unique column used for ordering and as keyset cursor.
     * @param before the cursor value; only projections with a key value less than this will be returned.
     * @param size the maximum number of refs to include in the slice.
     * @param <V> the type of the key field.
     * @return a slice containing the previous page of ref results.
     * @throws PersistenceException if the query fails due to underlying database issues.
     * @since 1.9
     */
    default <V> Slice<Ref<P>> sliceBeforeRef(@Nonnull Metamodel<P, V> key, @Nonnull V before, int size) {
        return selectRef().sliceBefore(key, before, size);
    }

    /**
     * Returns the next slice of projections after the specified ref cursor value.
     *
     * <p>This method performs forward keyset pagination using a ref cursor, returning projections where the key is
     * greater than {@code after}, ordered ascending by the key.</p>
     *
     * @param key the metamodel path for a unique column used for ordering and as keyset cursor.
     * @param after the ref cursor value; only projections with a key value greater than this ref will be returned.
     * @param size the maximum number of projections to include in the slice.
     * @param <V> the type of the key field, which must extend {@link Data}.
     * @return a slice containing the next page of results.
     * @throws PersistenceException if the query fails due to underlying database issues.
     * @since 1.9
     */
    default <V extends Data> Slice<P> sliceAfter(@Nonnull Metamodel<P, V> key, @Nonnull Ref<V> after, int size) {
        return select().sliceAfter(key, after, size);
    }

    /**
     * Returns the previous slice of projections before the specified ref cursor value.
     *
     * <p>This method performs backward keyset pagination using a ref cursor, returning projections where the key is
     * less than {@code before}, ordered descending by the key.</p>
     *
     * @param key the metamodel path for a unique column used for ordering and as keyset cursor.
     * @param before the ref cursor value; only projections with a key value less than this ref will be returned.
     * @param size the maximum number of projections to include in the slice.
     * @param <V> the type of the key field, which must extend {@link Data}.
     * @return a slice containing the previous page of results.
     * @throws PersistenceException if the query fails due to underlying database issues.
     * @since 1.9
     */
    default <V extends Data> Slice<P> sliceBefore(@Nonnull Metamodel<P, V> key, @Nonnull Ref<V> before, int size) {
        return select().sliceBefore(key, before, size);
    }

    /**
     * Returns the next slice of projection refs after the specified ref cursor value.
     *
     * <p>This method performs forward keyset pagination using a ref cursor, returning refs where the key is
     * greater than {@code after}, ordered ascending by the key.</p>
     *
     * @param key the metamodel path for a unique column used for ordering and as keyset cursor.
     * @param after the ref cursor value; only projections with a key value greater than this ref will be returned.
     * @param size the maximum number of refs to include in the slice.
     * @param <V> the type of the key field, which must extend {@link Data}.
     * @return a slice containing the next page of ref results.
     * @throws PersistenceException if the query fails due to underlying database issues.
     * @since 1.9
     */
    default <V extends Data> Slice<Ref<P>> sliceAfterRef(@Nonnull Metamodel<P, V> key, @Nonnull Ref<V> after, int size) {
        return selectRef().sliceAfter(key, after, size);
    }

    /**
     * Returns the previous slice of projection refs before the specified ref cursor value.
     *
     * <p>This method performs backward keyset pagination using a ref cursor, returning refs where the key is
     * less than {@code before}, ordered descending by the key.</p>
     *
     * @param key the metamodel path for a unique column used for ordering and as keyset cursor.
     * @param before the ref cursor value; only projections with a key value less than this ref will be returned.
     * @param size the maximum number of refs to include in the slice.
     * @param <V> the type of the key field, which must extend {@link Data}.
     * @return a slice containing the previous page of ref results.
     * @throws PersistenceException if the query fails due to underlying database issues.
     * @since 1.9
     */
    default <V extends Data> Slice<Ref<P>> sliceBeforeRef(@Nonnull Metamodel<P, V> key, @Nonnull Ref<V> before, int size) {
        return selectRef().sliceBefore(key, before, size);
    }

    // Composite keyset slice methods.

    /**
     * Returns the first slice of projections ordered by a non-unique sort column with a unique tiebreaker.
     *
     * <p>This method performs composite keyset pagination, returning at most {@code size} projections ordered by
     * the given sort field and unique key in ascending order.</p>
     *
     * @param sort the metamodel field used as the (potentially non-unique) primary sort column.
     * @param key the metamodel field used as the unique tiebreaker for stable ordering.
     * @param size the maximum number of projections to include in the slice.
     * @param <S> the type of the sort field.
     * @param <V> the type of the unique key field.
     * @return a slice containing the first page of results.
     * @since 1.9
     */
    default <S, V> Slice<P> slice(@Nonnull Metamodel<P, S> sort, @Nonnull Metamodel<P, V> key, int size) {
        return select().slice(sort, key, size);
    }

    /**
     * Returns the next slice of projections after the specified composite cursor, using a non-unique sort column
     * with a unique tiebreaker.
     *
     * @param sort the metamodel field used as the (potentially non-unique) primary sort column.
     * @param sortAfter the cursor value for the sort column.
     * @param key the metamodel field used as the unique tiebreaker.
     * @param keyAfter the cursor value for the unique key column.
     * @param size the maximum number of projections to include in the slice.
     * @param <S> the type of the sort field.
     * @param <V> the type of the unique key field.
     * @return a slice containing the next page of results.
     * @since 1.9
     */
    default <S, V> Slice<P> sliceAfter(@Nonnull Metamodel<P, S> sort, @Nonnull S sortAfter,
                                       @Nonnull Metamodel<P, V> key, @Nonnull V keyAfter, int size) {
        return select().sliceAfter(sort, sortAfter, key, keyAfter, size);
    }

    /**
     * Returns the previous slice of projections before the specified composite cursor, using a non-unique sort column
     * with a unique tiebreaker.
     *
     * @param sort the metamodel field used as the (potentially non-unique) primary sort column.
     * @param sortBefore the cursor value for the sort column.
     * @param key the metamodel field used as the unique tiebreaker.
     * @param keyBefore the cursor value for the unique key column.
     * @param size the maximum number of projections to include in the slice.
     * @param <S> the type of the sort field.
     * @param <V> the type of the unique key field.
     * @return a slice containing the previous page of results.
     * @since 1.9
     */
    default <S, V> Slice<P> sliceBefore(@Nonnull Metamodel<P, S> sort, @Nonnull S sortBefore,
                                        @Nonnull Metamodel<P, V> key, @Nonnull V keyBefore, int size) {
        return select().sliceBefore(sort, sortBefore, key, keyBefore, size);
    }

    /**
     * Returns the next slice of projections after the specified composite cursor with a ref unique key, using a
     * non-unique sort column with a unique tiebreaker.
     *
     * @param sort the metamodel field used as the (potentially non-unique) primary sort column.
     * @param sortAfter the cursor value for the sort column.
     * @param key the metamodel field used as the unique tiebreaker.
     * @param keyAfter the ref cursor value for the unique key column.
     * @param size the maximum number of projections to include in the slice.
     * @param <S> the type of the sort field.
     * @param <V> the type of the unique key, which must extend {@link Data}.
     * @return a slice containing the next page of results.
     * @since 1.9
     */
    default <S, V extends Data> Slice<P> sliceAfter(@Nonnull Metamodel<P, S> sort, @Nonnull S sortAfter,
                                                    @Nonnull Metamodel<P, V> key, @Nonnull Ref<V> keyAfter,
                                                    int size) {
        return select().sliceAfter(sort, sortAfter, key, keyAfter, size);
    }

    /**
     * Returns the previous slice of projections before the specified composite cursor with a ref unique key, using a
     * non-unique sort column with a unique tiebreaker.
     *
     * @param sort the metamodel field used as the (potentially non-unique) primary sort column.
     * @param sortBefore the cursor value for the sort column.
     * @param key the metamodel field used as the unique tiebreaker.
     * @param keyBefore the ref cursor value for the unique key column.
     * @param size the maximum number of projections to include in the slice.
     * @param <S> the type of the sort field.
     * @param <V> the type of the unique key, which must extend {@link Data}.
     * @return a slice containing the previous page of results.
     * @since 1.9
     */
    default <S, V extends Data> Slice<P> sliceBefore(@Nonnull Metamodel<P, S> sort, @Nonnull S sortBefore,
                                                     @Nonnull Metamodel<P, V> key, @Nonnull Ref<V> keyBefore,
                                                     int size) {
        return select().sliceBefore(sort, sortBefore, key, keyBefore, size);
    }

    /**
     * Returns the first slice of projection refs ordered by a non-unique sort column with a unique tiebreaker.
     *
     * @param sort the metamodel field used as the (potentially non-unique) primary sort column.
     * @param key the metamodel field used as the unique tiebreaker for stable ordering.
     * @param size the maximum number of refs to include in the slice.
     * @param <S> the type of the sort field.
     * @param <V> the type of the unique key field.
     * @return a slice containing the first page of ref results.
     * @since 1.9
     */
    default <S, V> Slice<Ref<P>> sliceRef(@Nonnull Metamodel<P, S> sort, @Nonnull Metamodel<P, V> key, int size) {
        return selectRef().slice(sort, key, size);
    }

    /**
     * Returns the next slice of projection refs after the specified composite cursor, using a non-unique sort column
     * with a unique tiebreaker.
     *
     * @param sort the metamodel field used as the (potentially non-unique) primary sort column.
     * @param sortAfter the cursor value for the sort column.
     * @param key the metamodel field used as the unique tiebreaker.
     * @param keyAfter the cursor value for the unique key column.
     * @param size the maximum number of refs to include in the slice.
     * @param <S> the type of the sort field.
     * @param <V> the type of the unique key field.
     * @return a slice containing the next page of ref results.
     * @since 1.9
     */
    default <S, V> Slice<Ref<P>> sliceAfterRef(@Nonnull Metamodel<P, S> sort, @Nonnull S sortAfter,
                                               @Nonnull Metamodel<P, V> key, @Nonnull V keyAfter, int size) {
        return selectRef().sliceAfter(sort, sortAfter, key, keyAfter, size);
    }

    /**
     * Returns the previous slice of projection refs before the specified composite cursor, using a non-unique sort
     * column with a unique tiebreaker.
     *
     * @param sort the metamodel field used as the (potentially non-unique) primary sort column.
     * @param sortBefore the cursor value for the sort column.
     * @param key the metamodel field used as the unique tiebreaker.
     * @param keyBefore the cursor value for the unique key column.
     * @param size the maximum number of refs to include in the slice.
     * @param <S> the type of the sort field.
     * @param <V> the type of the unique key field.
     * @return a slice containing the previous page of ref results.
     * @since 1.9
     */
    default <S, V> Slice<Ref<P>> sliceBeforeRef(@Nonnull Metamodel<P, S> sort, @Nonnull S sortBefore,
                                                @Nonnull Metamodel<P, V> key, @Nonnull V keyBefore, int size) {
        return selectRef().sliceBefore(sort, sortBefore, key, keyBefore, size);
    }

    /**
     * Returns the next slice of projection refs after the specified composite cursor with a ref unique key, using a
     * non-unique sort column with a unique tiebreaker.
     *
     * @param sort the metamodel field used as the (potentially non-unique) primary sort column.
     * @param sortAfter the cursor value for the sort column.
     * @param key the metamodel field used as the unique tiebreaker.
     * @param keyAfter the ref cursor value for the unique key column.
     * @param size the maximum number of refs to include in the slice.
     * @param <S> the type of the sort field.
     * @param <V> the type of the unique key, which must extend {@link Data}.
     * @return a slice containing the next page of ref results.
     * @since 1.9
     */
    default <S, V extends Data> Slice<Ref<P>> sliceAfterRef(@Nonnull Metamodel<P, S> sort, @Nonnull S sortAfter,
                                                            @Nonnull Metamodel<P, V> key, @Nonnull Ref<V> keyAfter,
                                                            int size) {
        return selectRef().sliceAfter(sort, sortAfter, key, keyAfter, size);
    }

    /**
     * Returns the previous slice of projection refs before the specified composite cursor with a ref unique key,
     * using a non-unique sort column with a unique tiebreaker.
     *
     * @param sort the metamodel field used as the (potentially non-unique) primary sort column.
     * @param sortBefore the cursor value for the sort column.
     * @param key the metamodel field used as the unique tiebreaker.
     * @param keyBefore the ref cursor value for the unique key column.
     * @param size the maximum number of refs to include in the slice.
     * @param <S> the type of the sort field.
     * @param <V> the type of the unique key, which must extend {@link Data}.
     * @return a slice containing the previous page of ref results.
     * @since 1.9
     */
    default <S, V extends Data> Slice<Ref<P>> sliceBeforeRef(@Nonnull Metamodel<P, S> sort, @Nonnull S sortBefore,
                                                             @Nonnull Metamodel<P, V> key, @Nonnull Ref<V> keyBefore,
                                                             int size) {
        return selectRef().sliceBefore(sort, sortBefore, key, keyBefore, size);
    }

    // List based methods.

    /**
     * Returns a list of all projections of the type supported by this repository. Each element in the list represents
     * a projection in the database, encapsulating all relevant data as mapped by the projection model.
     *
     * <p><strong>Note:</strong> Loading all projections into memory at once can be very memory-intensive if your table
     * is large.</p>
     *
     * @return a stream of all entities of the type supported by this repository.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    List<P> findAll();

    /**
     * Retrieves a list of projections based on their primary keys.
     *
     * <p>This method retrieves projections matching the provided IDs in batches, consolidating them into a single list.
     * The batch-based retrieval minimizes database overhead, allowing efficient handling of larger collections of IDs.
     * </p>
     *
     * <p><strong>Note:</strong> The order of projections in the returned list is not guaranteed to match the order of
     * IDs in the input collection, as the database may not preserve insertion order during retrieval.</p>
     *
     * @param ids the primary keys of the projections to retrieve, represented as an iterable collection.
     * @return a list of projections corresponding to the provided primary keys. Projections are returned without any
     *         guarantee of order alignment with the input list. If an ID does not correspond to any projection in the
     *         database, no corresponding projection will be included in the returned list.
     * @throws PersistenceException if the selection operation fails due to database issues, such as connectivity
     *         problems or invalid input parameters.
     */
    List<P> findAllById(@Nonnull Iterable<ID> ids);

    /**
     * Retrieves a list of projections based on their primary keys.
     *
     * <p>This method retrieves projections matching the provided IDs in batches, consolidating them into a single list.
     * The batch-based retrieval minimizes database overhead, allowing efficient handling of larger collections of IDs.
     * </p>
     *
     * <p><strong>Note:</strong> The order of projections in the returned list is not guaranteed to match the order of
     * IDs in the input collection, as the database may not preserve insertion order during retrieval.</p>
     *
     * @param refs the primary keys of the projections to retrieve, represented as an iterable collection.
     * @return a list of projections corresponding to the provided primary keys. Projections are returned without any
     *         guarantee of order alignment with the input list. If an ID does not correspond to any projection in the
     *         database, no corresponding projection will be included in the returned list.
     * @throws PersistenceException if the selection operation fails due to database issues, such as connectivity
     *         problems or invalid input parameters.
     */
    List<P> findAllByRef(@Nonnull Iterable<Ref<P>> refs);

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
     * <p>The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of projections.</p>
     *
     * <p><strong>Note:</strong> Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must be
     * closed after usage to prevent resource leaks. As the stream is {@code AutoCloseable}, it is recommended to use it
     * within a {@code try-with-resources} block.</p>
     *
     * @return a stream of all projections of the type supported by this repository.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    Stream<P> selectAll();

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
     * <p><strong>Note:</strong> Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must be
     * closed after usage to prevent resource leaks. As the stream is {@code AutoCloseable}, it is recommended to use it
     * within a {@code try-with-resources} block.</p>
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
    Stream<P> selectById(@Nonnull Stream<ID> ids);

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
     * <p><strong>Note:</strong> Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must be
     * closed after usage to prevent resource leaks. As the stream is {@code AutoCloseable}, it is recommended to use it
     * within a {@code try-with-resources} block.</p>
     *
     * @param refs a stream of refs to retrieve from the repository.
     * @return a stream of projections corresponding to the provided primary keys. The order of projections in the stream is
     *         not guaranteed to match the order of ids in the input stream. If an id does not correspond to any projection
     *         in the database, it will simply be skipped, and no corresponding projection will be included in the returned
     *         stream. If the same projection is requested multiple times, it may be included in the stream multiple times
     *         if it is part of a separate batch.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    Stream<P> selectByRef(@Nonnull Stream<Ref<P>> refs);

    /**
     * Retrieves a stream of projections based on their primary keys.
     *
     * <p>This method executes queries in batches, with the batch size determined by the provided parameter. This
     * optimization aims to reduce the overhead of executing multiple queries and efficiently retrieve projections. The
     * batching strategy enhances performance, particularly when dealing with large sets of primary keys.</p>
     *
     * <p>The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of projections.</p>
     *
     * <p><strong>Note:</strong> Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must be
     * closed after usage to prevent resource leaks. As the stream is {@code AutoCloseable}, it is recommended to use it
     * within a {@code try-with-resources} block.</p>
     *
     * @param ids a stream of projection IDs to retrieve from the repository.
     * @param chunkSize the number of primary keys to include in each batch. This parameter determines the size of the
     *                  batches used to execute the selection operation. A larger batch size can improve performance, especially when
     *                  dealing with large sets of primary keys.
     * @return a stream of projections corresponding to the provided primary keys. The order of projections in the stream is
     * not guaranteed to match the order of ids in the input stream. If an id does not correspond to any projection in the
     * database, it will simply be skipped, and no corresponding projection will be included in the returned stream. If the
     * same projection is requested multiple times, it may be included in the stream multiple times if it is part of a
     * separate batch.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    Stream<P> selectById(@Nonnull Stream<ID> ids, int chunkSize);

    /**
     * Retrieves a stream of projections based on their primary keys.
     *
     * <p>This method executes queries in batches, with the batch size determined by the provided parameter. This
     * optimization aims to reduce the overhead of executing multiple queries and efficiently retrieve projections. The
     * batching strategy enhances performance, particularly when dealing with large sets of primary keys.</p>
     *
     * <p>The resulting stream is lazily loaded, meaning that the projections are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of projections.</p>
     *
     * <p><strong>Note:</strong> Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must be
     * closed after usage to prevent resource leaks. As the stream is {@code AutoCloseable}, it is recommended to use it
     * within a {@code try-with-resources} block.</p>
     *
     * @param refs a stream of refs to retrieve from the repository.
     * @param chunkSize the number of primary keys to include in each batch. This parameter determines the size of the
     *                  batches used to execute the selection operation. A larger batch size can improve performance, especially when
     *                  dealing with large sets of primary keys.
     * @return a stream of projections corresponding to the provided primary keys. The order of projections in the stream is
     * not guaranteed to match the order of refs in the input stream. If an id does not correspond to any projection in the
     * database, it will simply be skipped, and no corresponding projection will be included in the returned stream. If the
     * same projection is requested multiple times, it may be included in the stream multiple times if it is part of a
     * separate batch.
     * @throws PersistenceException if the selection operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    Stream<P> selectByRef(@Nonnull Stream<Ref<P>> refs, int chunkSize);

    /**
     * Counts the number of projections identified by the provided stream of IDs using the default batch size.
     *
     * <p>This method calculates the total number of projections that match the provided primary keys. The counting
     * is performed in batches, which helps optimize performance and manage database load when dealing with
     * large sets of IDs.</p>
     *
     * @param ids a stream of IDs for which to count matching projections.
     * @return the total count of projections matching the provided IDs.
     * @throws PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    long countById(@Nonnull Stream<ID> ids);

    /**
     * Counts the number of projections identified by the provided stream of IDs, with the counting process divided into
     * batches of the specified size.
     *
     * <p>This method performs the counting operation in batches, specified by the {@code chunkSize} parameter. This
     * batching approach is particularly useful for efficiently handling large volumes of IDs, reducing the overhead on
     * the database and improving performance.</p>
     *
     * @param ids a stream of IDs for which to count matching projections.
     * @param chunkSize the size of the batches to use for the counting operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @return the total count of projections matching the provided IDs.
     * @throws PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    long countById(@Nonnull Stream<ID> ids, int chunkSize);

    /**
     * Counts the number of projections identified by the provided stream of refs using the default batch size.
     *
     * <p>This method calculates the total number of projections that match the provided primary keys. The counting
     * is performed in batches, which helps optimize performance and manage database load when dealing with
     * large sets of IDs.</p>
     *
     * @param refs a stream of refs for which to count matching projections.
     * @return the total count of projections matching the provided IDs.
     * @throws PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    long countByRef(@Nonnull Stream<Ref<P>> refs);

    /**
     * Counts the number of projections identified by the provided stream of refs, with the counting process divided into
     * batches of the specified size.
     *
     * <p>This method performs the counting operation in batches, specified by the {@code chunkSize} parameter. This
     * batching approach is particularly useful for efficiently handling large volumes of IDs, reducing the overhead on
     * the database and improving performance.</p>
     *
     * @param refs a stream of refs for which to count matching projections.
     * @param chunkSize the size of the batches to use for the counting operation. A larger batch size can improve
     *                  performance but may also increase the load on the database.
     * @return the total count of projections matching the provided IDs.
     * @throws PersistenceException if there is an error during the counting operation, such as connectivity issues.
     */
    long countByRef(@Nonnull Stream<Ref<P>> refs, int chunkSize);
}
