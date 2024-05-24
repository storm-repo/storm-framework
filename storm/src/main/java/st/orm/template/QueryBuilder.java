package st.orm.template;

import jakarta.annotation.Nonnull;
import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.PersistenceException;
import st.orm.PreparedQuery;
import st.orm.Query;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A query builder that constructs a query from a template.
 *
 * @param <T> the type of the table being queried.
 * @param <R> the type of the result.
 * @param <ID> the type of the primary key.
 */
public interface QueryBuilder<T, R, ID> extends StringTemplate.Processor<Stream<R>, PersistenceException> {

    // Don't let Builder extend Iterable<R>, because that would disallow us from closing the underlying stream.

    QueryBuilder<T, R, ID> distinct();

    /**
     * A builder for constructing join clause of the query.
     *
     * @param <T> the type of the table being queried.
     * @param <R> the type of the result.
     * @param <ID> the type of the primary key.
     */
    interface TypedJoinBuilder<T, R, ID> extends JoinBuilder<T, R, ID> {

        QueryBuilder<T, R, ID> on(@Nonnull Class<? extends Record> relation);
    }

    /**
     * A builder for constructing join clause of the query using custom join conditions.
     *
     * @param <T> the type of the table being queried.
     * @param <R> the type of the result.
     * @param <ID> the type of the primary key.
     */
    interface JoinBuilder<T, R, ID> {

        /**
         * Returns the builder for constructing the ON clause of the join.
         *
         * @return the builder for constructing the ON clause of the join.
         */
        OnBuilder<T, R, ID> on();
    }

    /**
     * A builder for constructing the ON clause of the join.
     *
     * @param <T> the type of the table being queried.
     * @param <R> the type of the result.
     * @param <ID> the type of the primary key.
     */
    interface OnBuilder<T, R, ID> extends StringTemplate.Processor<QueryBuilder<T, R, ID>, PersistenceException> {

        QueryBuilder<T, R, ID> template(@Nonnull TemplateFunction function);
    }

    /**
     * A builder for constructing the WHERE clause of the query.
     *
     * @param <T> the type of the table being queried.
     * @param <R> the type of the result.
     * @param <ID> the type of the primary key.
     */
    interface WhereBuilder<T, R, ID> extends StringTemplate.Processor<PredicateBuilder<T, R, ID>, PersistenceException> {

        PredicateBuilder<T, R, ID> template(@Nonnull TemplateFunction function);

        PredicateBuilder<T, R, ID> matches(@Nonnull Object o);
    }

    /**
     * A builder for constructing the predicates of the WHERE clause of the query.
     *
     * @param <T> the type of the table being queried.
     * @param <R> the type of the result.
     * @param <ID> the type of the primary key.
     */
    interface PredicateBuilder<T, R, ID> {

        PredicateBuilder<T, R, ID> and(@Nonnull PredicateBuilder<T, R, ID> predicate);

        PredicateBuilder<T, R, ID> or(@Nonnull PredicateBuilder<T, R, ID> predicate);
    }

    /**
     * Selects the {@code resultType} for the query.
     *
     * <p>The query will still target the original table specified by {@code T}, but the results will be mapped to the
     * specified {@code resultType}.</p>
     *
     * @param resultType the type of the result.
     * @param <X> the type of the result.
     * @return the query builder.
     */
    <X extends Record> QueryBuilder<T, X, ID> select(@Nonnull Class<X> resultType);

    <X> QueryBuilder<T, X, ID> selectTemplate(@Nonnull Class<X> resultType, @Nonnull TemplateFunction function);

    <X> StringTemplate.Processor<QueryBuilder<T, X, ID>, PersistenceException> selectTemplate(@Nonnull Class<X> resultType);

    /**
     * Adds a cross join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    QueryBuilder<T, R, ID> crossJoin(@Nonnull Class<? extends Record> relation);

    /**
     * Adds an inner join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    TypedJoinBuilder<T, R, ID> innerJoin(@Nonnull Class<? extends Record> relation);

    /**
     * Adds a left join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    TypedJoinBuilder<T, R, ID> leftJoin(@Nonnull Class<? extends Record> relation);

    /**
     * Adds a right join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    TypedJoinBuilder<T, R, ID> rightJoin(@Nonnull Class<? extends Record> relation);

    /**
     * Adds a join of the specified type to the query.
     *
     * @param type the type of the join (e.g., INNER, LEFT, RIGHT).
     * @param relation the relation to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    TypedJoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull Class<? extends Record> relation, @Nonnull String alias);

    StringTemplate.Processor<QueryBuilder<T, R, ID>, PersistenceException> crossJoin();

    StringTemplate.Processor<JoinBuilder<T, R, ID>, PersistenceException> innerJoin(@Nonnull String alias);

    StringTemplate.Processor<JoinBuilder<T, R, ID>, PersistenceException> leftJoin(@Nonnull String alias);

    StringTemplate.Processor<JoinBuilder<T, R, ID>, PersistenceException> rightJoin(@Nonnull String alias);

    StringTemplate.Processor<JoinBuilder<T, R, ID>, PersistenceException> join(@Nonnull JoinType type, @Nonnull String alias);

    JoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull String alias, @Nonnull TemplateFunction function);

    default QueryBuilder<T, R, ID> where(@Nonnull Object o) {
        return where(predicate -> predicate.matches(o));
    }

    QueryBuilder<T, R, ID> where(@Nonnull Function<WhereBuilder<T, R, ID>, PredicateBuilder<T, R, ID>> expression);

    /**
     * Appends the query with the string provided by the specified template {@code function}.
     *
     * @param function function that provides the string to append to the query using String interpolation.
     * @return the query builder.
     */
    QueryBuilder<T, R, ID> withTemplate(@Nonnull TemplateFunction function);

    /**
     * Returns a processor that can be used to append the query with a string template.
     *
     * @return a processor that can be used to append the query with a string template.
     */
    StringTemplate.Processor<QueryBuilder<T, R, ID>, PersistenceException> withTemplate();

    /**
     * Builds the query based on the current state of the query builder.
     *
     * @return the constructed query.
     */
    Query build();

    /**
     * Prepares the query for execution.
     *
     * <p>Unlike regular queries, which are constructed lazily, prepared queries are constructed eagerly.
     * Prepared queries allow the use of bind variables and enable reading generated keys after row insertion.</p>
     *
     * @return the prepared query.
     * @throws PersistenceException if the query preparation fails.
     */
    default PreparedQuery prepare() {
        return build().prepare();
    }

    /**
     * Appends the query with the string provided by the specified template {@code function}, executes the query, and
     * returns a stream of results.
     *
     * @param function function that provides the string to append to the query using String interpolation.
     * @return a stream of results.
     * @throws PersistenceException if the query fails.
     */
    Stream<R> stream(@Nonnull TemplateFunction function);

    /**
     * Executes the query and returns a stream of results.
     *
     * <p>The resulting stream will automatically close the underlying resources when a terminal operation is
     * invoked, such as {@code collect}, {@code forEach}, or {@code toList}, among others. If no terminal operation is
     * invoked, the stream will not close the resources, and it's the responsibility of the caller to ensure that the
     * stream is properly closed to release the resources.</p>
     *
     * @return a stream of results.
     * @throws PersistenceException if the query fails.
     */
    Stream<R> stream();

    /**
     * Executes the query and returns a list of results.
     *
     * @return the list of results.
     * @throws PersistenceException if the query fails.
     */
    default List<R> toList() {
        return stream().toList();
    }

    /**
     * Executes the query and returns a single result.
     *
     * @return the single result.
     * @throws NoResultException if there is no result.
     * @throws NonUniqueResultException if more than one result.
     * @throws PersistenceException if the query fails.
     */
    default R singleResult() {
        return stream()
                .reduce((_, _) -> {
                    throw new NonUniqueResultException("Expected single result, but found more than one.");
                }).orElseThrow(() -> new NoResultException("Expected single result, but found none."));
    }

    /**
     * Executes the query and returns an optional result.
     *
     * @return the optional result.
     * @throws NonUniqueResultException if more than one result.
     * @throws PersistenceException if the query fails.
     */
    default Optional<R> optionalResult() {
        return stream()
                .reduce((_, _) -> {
                    throw new NonUniqueResultException("Expected single result, but found more than one.");
                });
    }

    /**
     * Performs the function in multiple batches.
     *
     * @param stream the stream to batch.
     * @param function the function to apply to each batch.
     * @return a stream of results from each batch.
     * @param <X> the type of elements in the stream.
     * @param <Y> the type of elements in the result stream.
     */
    default <X, Y> Stream<Y> batch(@Nonnull Stream<X> stream, @Nonnull Function<Stream<X>, Stream<Y>> function) {
        return autoClose(slice(stream).flatMap(function)).onClose(stream::close);
    }

    /**
     * Performs the function in multiple batches, each containing up to {@code batchSize} elements from the stream.
     *
     * @param stream the stream to batch.
     * @param batchSize the maximum number of elements to include in each batch.
     * @param function the function to apply to each batch.
     * @return a stream of results from each batch.
     * @param <X> the type of elements in the stream.
     * @param <Y> the type of elements in the result stream.
     */
    default <X, Y> Stream<Y> batch(@Nonnull Stream<X> stream, int batchSize, @Nonnull Function<Stream<X>, Stream<Y>> function) {
        return autoClose(slice(stream, batchSize).flatMap(function)).onClose(stream::close);
    }

    /**
     * Wraps the stream in a stream that is automatically closed after a terminal operation.
     *
     * @param stream the stream to wrap.
     * @return a stream that is automatically closed after a terminal operation.
     * @param <X> the type of the stream.
     */
    <X> Stream<X> autoClose(@Nonnull Stream<X> stream);

    /**
     * Generates a stream of slices. This method is designed to facilitate batch processing of large streams by
     * dividing the stream into smaller manageable slices, which can be processed independently.
     *
     * <p>The method utilizes a "tripwire" mechanism to ensure that the original stream is properly managed and closed upon
     * completion of processing, preventing resource leaks.</p>
     *
     * @param <X> the type of elements in the stream.
     * @param stream the original stream of elements to be sliced.
     * {@code Integer.MAX_VALUE}, only one slice will be returned.
     * @return a stream of slices, where each slice contains up to {@code batchSize} elements from the original stream.
     */
    <X> Stream<Stream<X>> slice(@Nonnull Stream<X> stream);

    /**
     * Generates a stream of slices, each containing a subset of elements from the original stream up to a specified
     * size. This method is designed to facilitate batch processing of large streams by dividing the stream into
     * smaller manageable slices, which can be processed independently.
     *
     * <p>If the specified size is equal to {@code Integer.MAX_VALUE}, this method will return a single slice containing
     * the original stream, effectively bypassing the slicing mechanism. This is useful for operations that can handle
     * all elements at once without the need for batching.</p>
     *
     * <p>The method utilizes a "tripwire" mechanism to ensure that the original stream is properly managed and closed upon
     * completion of processing, preventing resource leaks.</p>
     *
     * @param <X> the type of elements in the stream.
     * @param stream the original stream of elements to be sliced.
     * @param size the maximum number of elements to include in each slice. If {@code size} is
     * {@code Integer.MAX_VALUE}, only one slice will be returned.
     * @return a stream of slices, where each slice contains up to {@code batchSize} elements from the original stream.
     */
    <X> Stream<Stream<X>> slice(@Nonnull Stream<X> stream, int size);
}
