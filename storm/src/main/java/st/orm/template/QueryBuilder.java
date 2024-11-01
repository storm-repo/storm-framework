package st.orm.template;

import jakarta.annotation.Nonnull;
import st.orm.NoResultException;
import st.orm.NonUniqueResultException;
import st.orm.PersistenceException;
import st.orm.PreparedQuery;
import st.orm.Query;
import st.orm.ResultCallback;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.StringTemplate.RAW;
import static java.util.Spliterators.spliteratorUnknownSize;

/**
 * A query builder that constructs a query from a template.
 *
 * @param <T> the type of the table being queried.
 * @param <R> the type of the result.
 * @param <ID> the type of the primary key.
 */
public interface QueryBuilder<T, R, ID> {

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

        QueryBuilder<T, R, ID> on(@Nonnull StringTemplate template);
    }

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

    QueryBuilder<T, R, ID> crossJoin(@Nonnull StringTemplate template);

    JoinBuilder<T, R, ID> innerJoin(@Nonnull StringTemplate template, @Nonnull String alias);

    JoinBuilder<T, R, ID> leftJoin(@Nonnull StringTemplate template, @Nonnull String alias);

    JoinBuilder<T, R, ID> rightJoin(@Nonnull StringTemplate template, @Nonnull String alias);

    JoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull StringTemplate template, @Nonnull String alias);

    /**
     * A builder for constructing the WHERE clause of the query.
     *
     * @param <T> the type of the table being queried.
     * @param <R> the type of the result.
     * @param <ID> the type of the primary key.
     */
    interface WhereBuilder<T, R, ID> {

        /**
         * A predicate that always evaluates to true.
         */
        default PredicateBuilder<T, R, ID> TRUE() {
            return expression(RAW."TRUE");
        }

        /**
         * A predicate that always evaluates to false.
         */
        default PredicateBuilder<T, R, ID> FALSE() {
            return expression(RAW."FALSE");
        }

        PredicateBuilder<T, R, ID> expression(@Nonnull StringTemplate template);

        /**
         * Adds a condition to the WHERE clause that matches the specified object. The object can be the primary
         * key of the table, or a record representing the table, or any of the related tables in the table graph.
         *
         * <p>If the object type cannot be unambiguously matched, the {@link #filter(String, Operator, Object...)} method can be used to
         * specify the path to the object in the table graph.</p>
         *
         * @param o the object to match.
         * @return the predicate builder.
         */
        PredicateBuilder<T, R, ID> filter(@Nonnull Object o);

        /**
         * Adds a condition to the WHERE clause that matches the specified objects. The objects can be the primary key
         * of the table, or a record representing the table, or any of the related tables in the table graph.
         *
         * <p>If the object type cannot be unambiguously matched, the {@link #filter(String, Operator, Iterable)} method can be used to
         * specify the path to the object in the table graph.</p>
         *
         * @param it the objects to match.
         * @return the predicate builder.
         */
        PredicateBuilder<T, R, ID> filter(@Nonnull Iterable<?> it);

        /**
         * Adds a condition to the WHERE clause that matches the specified objects at the specified path in the table
         * graph. The objects can be the primary key of the table, or a record representing the table, or any field in
         * the table graph.
         *
         * @param path the path to the object in the table graph.
         * @param operator the operator to use for the comparison.
         * @param it the objects to match.
         * @return the predicate builder.
         */
        PredicateBuilder<T, R, ID> filter(@Nonnull String path, @Nonnull Operator operator, @Nonnull Iterable<?> it);

        /**
         * Adds a condition to the WHERE clause that matches the specified object(s) at the specified path in the table
         * graph. The object(s) can be the primary key of the table, or a record representing the table, or any field in the
         * table graph.
         *
         * @param path the path to the object in the table graph.
         * @param operator the operator to use for the comparison.
         * @param o the object(s) to match.
         * @return the predicate builder.
         */
        PredicateBuilder<T, R, ID> filter(@Nonnull String path, @Nonnull Operator operator, @Nonnull Object... o);
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
     * Adds a condition to the WHERE clause that matches the specified object(s). The object(s) can be the primary key
     * of the table, or a record representing the table, or any of the related tables in the table graph.
     *
     * <p>If the object type cannot be unambiguously matched, the {@link #where(String, Operator, Object...)} method
     * can be used to specify the path to the object in the table graph.</p>
     *
     * @param o the object(s) to match.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> where(@Nonnull Object o) {
        return wherePredicate(predicate -> predicate.filter(o));
    }

    /**
     * Adds a condition to the WHERE clause that matches the specified objects. The objects can be the primary key of
     * the table, or a record representing the table, or any of the related tables in the table graph.
     *
     * <p>If the object type cannot be unambiguously matched, the {@link #where(String, Operator, Iterable)} method
     * can be used to specify the path to the object in the table graph.</p>
     *
     * @param it the objects to match.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> where(@Nonnull Iterable<?> it) {
        return wherePredicate(predicate -> predicate.filter(it));
    }

    /**
     * Adds a condition to the WHERE clause that matches the specified objects at the specified path in the table
     * graph. The objects can be the primary key of the table, or a record representing the table, or any field in the
     * table graph.
     *
     * @param path the path to the object in the table graph.
     * @param operator the operator to use for the comparison.
     * @param it the objects to match.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> where(@Nonnull String path, @Nonnull Operator operator, @Nonnull Iterable<?> it) {
        return wherePredicate(predicate -> predicate.filter(path, operator, it));
    }

    /**
     * Adds a condition to the WHERE clause that matches the specified object(s) at the specified path in the table
     * graph. The object(s) can be the primary key of the table, or a record representing the table, or any field in the
     * table graph.
     *
     * @param path the path to the object in the table graph.
     * @param operator the operator to use for the comparison.
     * @param o the object(s) to match.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> where(@Nonnull String path, @Nonnull Operator operator, @Nonnull Object... o) {
        return wherePredicate(predicate -> predicate.filter(path, operator, o));
    }

    default QueryBuilder<T, R, ID> where(@Nonnull StringTemplate template) {
        return wherePredicate(it -> it.expression(template));
    }

    QueryBuilder<T, R, ID> wherePredicate(@Nonnull Function<WhereBuilder<T, R, ID>, PredicateBuilder<T, R, ID>> predicate);

    /**
     * Returns a processor that can be used to append the query with a string template.
     *
     * @return a processor that can be used to append the query with a string template.
     */
    QueryBuilder<T, R, ID> append(@Nonnull StringTemplate template);

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

    //
    // Execution methods.
    //

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
    Stream<R> getResultStream();

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
    default <X> X getResult(@Nonnull ResultCallback<R, X> callback) {
        try (Stream<R> stream = getResultStream()) {
            return callback.process(stream);
        }
    }

    /**
     * Returns the number of entities in the database of the entity type supported by this repository.
     *
     * @return the total number of entities in the database as a long value.
     * @throws PersistenceException if the count operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    default long getResultCount() {
        try (var stream = getResultStream()) {
            return stream.count();
        }
    }

    /**
     * Executes the query and returns a list of results.
     *
     * @return the list of results.
     * @throws PersistenceException if the query fails.
     */
    default List<R> getResultList() {
        try (var stream = getResultStream()) {
            return stream.toList();
        }
    }

    /**
     * Executes the query and returns a single result.
     *
     * @return the single result.
     * @throws NoResultException if there is no result.
     * @throws NonUniqueResultException if more than one result.
     * @throws PersistenceException if the query fails.
     */
    default R getSingleResult() {
        try (var stream = getResultStream()) {
            return stream
                    .reduce((_, _) -> {
                        throw new NonUniqueResultException("Expected single result, but found more than one.");
                    }).orElseThrow(() -> new NoResultException("Expected single result, but found none."));
        }
    }

    /**
     * Executes the query and returns an optional result.
     *
     * @return the optional result.
     * @throws NonUniqueResultException if more than one result.
     * @throws PersistenceException if the query fails.
     */
    default Optional<R> getOptionalResult() {
        try (var stream = getResultStream()) {
            return stream.reduce((_, _) -> {
                throw new NonUniqueResultException("Expected single result, but found more than one.");
            });
        }
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
    static <X, Y> Stream<Y> slice(@Nonnull Stream<X> stream, int batchSize, @Nonnull Function<List<X>, Stream<Y>> function) {
        return slice(stream, batchSize)
                .flatMap(function); // Note that the flatMap operation closes the stream passed to it.
    }

    /**
     * Generates a stream of slices, each containing a subset of elements from the original stream up to a specified
     * size. This method is designed to facilitate batch processing of large streams by dividing the stream into
     * smaller manageable slices, which can be processed independently.
     *
     * <p>If the specified size is equal to {@code Integer.MAX_VALUE}, this method will return a single slice containing
     * the original stream, effectively bypassing the slicing mechanism. This is useful for operations that can handle
     * all elements at once without the need for batching.</p>
     *
     * @param <X> the type of elements in the stream.
     * @param stream the original stream of elements to be sliced.
     * @param size the maximum number of elements to include in each slice. If {@code size} is
     * {@code Integer.MAX_VALUE}, only one slice will be returned.
     * @return a stream of slices, where each slice contains up to {@code size} elements from the original stream.
     */
    static <X> Stream<List<X>> slice(@Nonnull Stream<X> stream, int size) {
        if (size == MAX_VALUE) {
            return Stream.of(stream.toList());
        }
        // We're lifting the resource closing logic from the input stream to the output stream.
        final Iterator<X> iterator = stream.iterator();
        var it = new Iterator<List<X>>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public List<X> next() {
                Iterator<X> sliceIterator = new Iterator<>() {
                    private int count = 0;

                    @Override
                    public boolean hasNext() {
                        return count < size && iterator.hasNext();
                    }

                    @Override
                    public X next() {
                        if (count >= size) {
                            throw new IllegalStateException("Size exceeded.");
                        }
                        count++;
                        return iterator.next();
                    }
                };
                return StreamSupport.stream(spliteratorUnknownSize(sliceIterator, 0), false).toList();
            }
        };
        return StreamSupport.stream(spliteratorUnknownSize(it, 0), false)
                .onClose(stream::close);
    }
}
