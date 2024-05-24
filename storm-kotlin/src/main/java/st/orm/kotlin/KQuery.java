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
import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.PersistenceException;
import kotlin.reflect.KClass;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Abstraction for DQL (Data Query Language) statements, such as SELECT queries and  DML (Data Manipulation Language)
 * statements, such as INSERT, UPDATE and DELETE statements
 */
public interface KQuery {

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
        return getResultStream(type).toList();
    }

    /**
     * Execute a SELECT query and return the resulting rows as a stream of row instances.
     *
     * <p>Each element in the stream represents a row in the result, where the columns of the row corresponds to the
     * order of values in the row array.</p>
     *
     * @return the result stream.
     * @throws PersistenceException if the query fails.
     */
    Stream<Object[]> getResultStream();

    /**
     * Execute a SELECT query and return the resulting rows as a stream of row instances.
     *
     * <p>Each element in the stream represents a row in the result, where the columns of the row are mapped to the
     * constructor arguments of the specified {@code type}.</p>
     *
     * @param type the type of the result.
     * @return the result stream.
     * @throws PersistenceException if the query fails.
     */
    <T> Stream<T> getResultStream(@Nonnull KClass<T> type);

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
        return stream
                .reduce((_, _) -> {
                    throw new NonUniqueResultException("Expected single result, but found more than one.");
                }).orElseThrow(() -> new NoResultException("Expected single result, but found none."));
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
        return stream
                .reduce((_, _) -> {
                    throw new NonUniqueResultException("Expected single result, but found more than one.");
                });
    }
}
