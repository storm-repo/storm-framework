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
package st.orm.kotlin;

import jakarta.annotation.Nonnull;
import kotlin.reflect.KClass;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import st.orm.NoResultException;
import st.orm.NonUniqueResultException;
import st.orm.PersistenceException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Abstraction for DQL (Data Query Language) statements, such as SELECT queries and  DML (Data Manipulation Language)
 * statements, such as INSERT, UPDATE and DELETE statements
 */
public interface KQuery {

    /**
     * Prepares the query for execution.
     *
     * <p>Queries are normally constructed in a lazy fashion, unlike prepared queries which are constructed eagerly.
     * Prepared queries allow the use of bind variables and enable reading generated keys after row insertion.</p>
     *
     * <p>Note that the prepared query must be closed after usage to prevent resource leaks. As the prepared query is
     * AutoCloseable, it is recommended to use it within a try-with-resources block.</p>
     *
     * @return the prepared query.
     * @throws PersistenceException if the query preparation fails.
     */
    KPreparedQuery prepare();

    /**
     * Execute a SELECT query and returns a single row, where the columns of the row corresponds to the order of values
     * in the list.
     *
     * @return a single row, where the columns of the row corresponds to the order of values the list.
     * @throws NoResultException if there is no result.
     * @throws NonUniqueResultException if more than one result.
     * @throws PersistenceException if the query fails.
     */
    default Object[] getSingleResult() {
        return singleResult(getResultStream());
    }

    /**
     * Execute a SELECT query and returns a single row, where the columns of the row corresponds to the order of values
     * in the list.
     *
     * @return a single row, where the columns of the row corresponds to the order of values the list, or an empty
     * optional if there is no result.
     * @throws NonUniqueResultException if more than one result.
     * @throws PersistenceException if the query fails.
     */
    default Optional<Object[]> getOptionalResult() {
        return optionalResult(getResultStream());
    }

    /**
     * Returns the number of results of this query.
     *
     * @return the total number of results of this query as a long value.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    default long getResultCount() {
        try (var stream = getResultStream()) {
            return stream.count();
        }
    }

    /**
     * Execute a SELECT query and returns a single row, where the columns of the row are mapped to the constructor
     * arguments of the specified {@code type}.
     *
     * @param type the type of the result.
     * @return a single row, where the columns of the row corresponds to the order of values the list.
     * @throws NoResultException if there is no result.
     * @throws NonUniqueResultException if more than one result.
     * @throws PersistenceException if the query fails.
     */
    default <T> T getSingleResult(KClass<T> type) {
        return singleResult(getResultStream(type));
    }

    /**
     * Execute a SELECT query and returns a single row, where the columns of the row are mapped to the constructor
     * arguments of the specified {@code type}.
     *
     * @param type the type of the result.
     * @return a single row, where the columns of the row corresponds to the order of values the list, or an empty
     * optional if there is no result.
     * @throws NonUniqueResultException if more than one result.
     * @throws PersistenceException if the query fails.
     */
    default <T> Optional<T> getOptionalResult(KClass<T> type) {
        return optionalResult(getResultStream(type));
    }

    /**
     * Execute a SELECT query and return the resulting rows as a list of row instances.
     *
     * <p>Each element in the list represents a row in the result, where the columns of the row corresponds to the
     * order of values in the row array.</p>
     *
     * @return the result list.
     * @throws PersistenceException if the query fails.
     */
    default List<Object[]> getResultList() {
        return getResultStream().toList();
    }

    /**
     * Execute a SELECT query and return the resulting rows as a list of row instances.
     *
     * <p>Each element in the list represents a row in the result, where the columns of the row are mapped to the
     * constructor arguments of the specified {@code type}.</p>
     *
     * @param type the type of the result.
     * @return the result list.
     * @throws PersistenceException if the query fails.
     */
    default <T> List<T> getResultList(@Nonnull KClass<T> type) {
        try (var stream = getResultStream(type)) {
            return stream.toList();
        }
    }

    /**
     * Execute a SELECT query and return the resulting rows as a stream of row instances.
     *
     * <p>Each element in the stream represents a row in the result, where the columns of the row corresponds to the
     * order of values in the row array.</p>
     *
     * <p>The resulting stream is lazily loaded, meaning that the records are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of records.</p>
     *
     * <p>Note that calling this method does trigger the execution of the underlying query, so it should only be invoked
     * when the query is intended to run. Since the stream holds resources open while in use, it must be closed after
     * usage to prevent resource leaks.</p>
     *
     * @return a stream of results.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    Stream<Object[]> getResultStream();

    /**
     * Execute a SELECT query and return the resulting rows as a stream of row instances.
     *
     * <p>Each element in the stream represents a row in the result, where the columns of the row corresponds to the
     * order of values in the row array.</p>
     *
     * <p>This method ensures efficient handling of large data sets by loading entities only as needed.
     * It also manages lifecycle of the callback stream, automatically closing the stream after processing to prevent
     * resource leaks.</p>
     *
     * @return the result stream.
     * @throws PersistenceException if the query fails.
     */
    default <R> R getResult(@Nonnull KResultCallback<Object[], R> callback) {
        try (var stream = getResultStream()) {
            return callback.process(toSequence(stream));
        }
    }

    /**
     * Execute a SELECT query and return the resulting rows as a stream of row instances.
     *
     * <p>Each element in the stream represents a row in the result, where the columns of the row are mapped to the
     * constructor arguments of the specified {@code type}.</p>
     *
     * <p>The resulting stream is lazily loaded, meaning that the records are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of records.</p>
     *
     * <p>Note that calling this method does trigger the execution of the underlying query, so it should only be invoked
     * when the query is intended to run. Since the stream holds resources open while in use, it must be closed after
     * usage to prevent resource leaks.</p>
     *
     * @return a stream of results.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    <T> Stream<T> getResultStream(@Nonnull KClass<T> type);

    /**
     * Execute a SELECT query and return the resulting rows as a stream of row instances.
     *
     * <p>Each element in the stream represents a row in the result, where the columns of the row are mapped to the
     * constructor arguments of the specified {@code type}.</p>
     *
     * <p>This method ensures efficient handling of large data sets by loading entities only as needed.
     * It also manages lifecycle of the callback stream, automatically closing the stream after processing to prevent
     * resource leaks.</p>
     *
     * @param type the type of the result.
     * @return the result stream.
     * @throws PersistenceException if the query fails.
     */
    default <T, R> R getResult(@Nonnull KClass<T> type, @Nonnull KResultCallback<T, R> callback) {
        try (var stream = getResultStream(type)) {
            return callback.process(toSequence(stream));
        }
    }

    /**
     * Returns true if the query is version aware, false otherwise.
     *
     * @return true if the query is version aware, false otherwise.
     */
    boolean isVersionAware();

    /**
     * Execute a command, such as an INSERT, UPDATE or DELETE statement.
     *
     * @return the number of rows impacted as result of the statement.
     * @throws PersistenceException if the statement fails.
     */
    int executeUpdate();

    /**
     * Execute a batch of commands.
     *
     * @throws PersistenceException if the batch fails.
     * @return an array of update counts containing one element for each command in the batch. The elements of the
     * array are ordered according to the order in which commands were added to the batch, following
     * {@code Statement.executeBatch} semantics.
     */
    int[] executeBatch();

    /**
     * Returns the single result of the stream.
     *
     * @param stream the stream to get the single result from.
     * @return the single result of the stream.
     * @param <T> the type of the result.
     * @throws NoResultException if there is no result.
     * @throws NonUniqueResultException if more than one result.
     */
    private <T> T singleResult(Stream<T> stream) {
        try (stream) {
            return stream
                    .reduce((_, _) -> {
                        throw new NonUniqueResultException("Expected single result, but found more than one.");
                    }).orElseThrow(() -> new NoResultException("Expected single result, but found none."));
        }
    }

    /**
     * Returns the single result of the stream, or an empty optional if there is no result.
     *
     * @param stream the stream to get the single result from.
     * @return the single result of the stream.
     * @param <T> the type of the result.
     * @throws NonUniqueResultException if more than one result.
     */
    private <T> Optional<T> optionalResult(Stream<T> stream) {
        try (stream) {
            return stream
                    .reduce((_, _) -> {
                        throw new NonUniqueResultException("Expected single result, but found more than one.");
                    });
        }
    }

    private <X> Sequence<X> toSequence(@Nonnull Stream<X> stream) {
        return SequencesKt.asSequence(stream.iterator());
    }
}
