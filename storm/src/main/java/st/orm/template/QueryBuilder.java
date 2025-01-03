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
public interface QueryBuilder<T extends Record, R, ID> {

    /**
     * Marks the current query as a distinct query.
     *
     * @return the query builder.
     */
    QueryBuilder<T, R, ID> distinct();

    /**
     * A builder for constructing join clause of the query.
     *
     * @param <T> the type of the table being queried.
     * @param <R> the type of the result.
     * @param <ID> the type of the primary key.
     */
    interface TypedJoinBuilder<T extends Record, R, ID> extends JoinBuilder<T, R, ID> {

        /**
         * Specifies the relation to join on.
         * 
         * @param relation the relation to join on.
         * @return the query builder.
         */
        QueryBuilder<T, R, ID> on(@Nonnull Class<? extends Record> relation);
    }

    /**
     * A builder for constructing join clause of the query using custom join conditions.
     *
     * @param <T> the type of the table being queried.
     * @param <R> the type of the result.
     * @param <ID> the type of the primary key.
     */
    interface JoinBuilder<T extends Record, R, ID> {

        /**
         * Specifies the join condition using a custom expression.
         * 
         * @param template the condition to join on.
         * @return the query builder.
         */
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

    /**
     * Adds a cross join to the query.
     *
     * @param template the condition to join.
     * @return the query builder.
     */
    QueryBuilder<T, R, ID> crossJoin(@Nonnull StringTemplate template);

    /**
     * Adds an inner join to the query.
     *
     * @param template the condition to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    JoinBuilder<T, R, ID> innerJoin(@Nonnull StringTemplate template, @Nonnull String alias);

    /**
     * Adds a left join to the query.
     *
     * @param template the template to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    JoinBuilder<T, R, ID> leftJoin(@Nonnull StringTemplate template, @Nonnull String alias);

    /**
     * Adds a right join to the query.
     *
     * @param template the template to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    JoinBuilder<T, R, ID> rightJoin(@Nonnull StringTemplate template, @Nonnull String alias);

    /**
     * Adds a join of the specified type to the query using a template.
     *
     * @param type the join type.
     * @param template the template to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    JoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull StringTemplate template, @Nonnull String alias);

    /**
     * Adds a join of the specified type to the query using a subquery.
     *
     * @param type the join type.
     * @param subquery the subquery to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    JoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull QueryBuilder<?, ?, ?> subquery, @Nonnull String alias);

    /**
     * A builder for constructing the WHERE clause of the query.
     *
     * @param <T> the type of the table being queried.
     * @param <R> the type of the result.
     * @param <ID> the type of the primary key.
     */
    interface WhereBuilder<T extends Record, R, ID> extends SubqueryTemplate {

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

        /**
         * Appends a custom expression to the WHERE clause.
         *
         * @param template the expression to add.
         * @return the predicate builder.
         */
        PredicateBuilder<T, R, ID> expression(@Nonnull StringTemplate template);

        /**
         * Adds an <code>EXISTS</code> condition to the WHERE clause using the specified subquery.
         *
         * <p>This method appends an <code>EXISTS</code> clause to the current query's WHERE condition.
         * It checks whether the provided subquery returns any rows, allowing you to filter results based
         * on the existence of related data. This is particularly useful for constructing queries that need
         * to verify the presence of certain records in a related table or subquery.
         *
         * @param subquery the subquery to check for existence.
         * @return the updated {@link PredicateBuilder} with the EXISTS condition applied.
         */
        PredicateBuilder<T, R, ID> exists(@Nonnull QueryBuilder<?, ?, ?> subquery);

        /**
         * Adds an <code>NOT EXISTS</code> condition to the WHERE clause using the specified subquery.
         *
         * <p>This method appends an <code>NOT EXISTS</code> clause to the current query's WHERE condition.
         * It checks whether the provided subquery returns any rows, allowing you to filter results based
         * on the existence of related data. This is particularly useful for constructing queries that need
         * to verify the absence of certain records in a related table or subquery.
         *
         * @param subquery the subquery to check for existence.
         * @return the updated {@link PredicateBuilder} with the NOT EXISTS condition applied.
         */
        PredicateBuilder<T, R, ID> notExists(@Nonnull QueryBuilder<?, ?, ?> subquery);

        /**
         * Adds a condition to the WHERE clause that matches the specified object. The object can be the primary
         * key of the table, or a record representing the table, or any of the related tables in the table graph.
         *
         * <p>If the object type cannot be unambiguously matched, the {@link #filter(String, Operator, Object...)}
         * method can be used to specify the path to the object in the table graph.</p>
         *
         * @param o the object to match.
         * @return the predicate builder.
         */
        PredicateBuilder<T, R, ID> filter(@Nonnull Object o);

        /**
         * Adds a condition to the WHERE clause that matches the specified objects. The objects can be the primary key
         * of the table, or a record representing the table, or any of the related tables in the table graph.
         *
         * <p>If the object type cannot be unambiguously matched, the {@link #filter(String, Operator, Iterable)} method
         * can be used to specify the path to the object in the table graph.</p>
         *
         * @param it the objects to match.
         * @return the predicate builder.
         */
        PredicateBuilder<T, R, ID> filter(@Nonnull Iterable<?> it);

        /**
         * Adds a condition to the WHERE clause that matches the specified objects at the specified path in the table
         * graph. When the objects are records representing a table, the `path` acts as a search path to locate the
         * table within the table graph - meaning it does not need to be an exact match. In other scenarios, where the
         * objects represent fields (including primary keys), the `path` must be an exact match to the field's location
         * in the table graph.
         *
         * @param path the path to the object in the table graph. For record (table) based filters, this is a search
         *             path to the table.
         * @param operator the operator to use for the comparison.
         * @param it the objects to match, which can be primary keys, records representing the table, or fields in the
         *           table graph.
         * @return the predicate builder.
         */
        PredicateBuilder<T, R, ID> filter(@Nonnull String path, @Nonnull Operator operator, @Nonnull Iterable<?> it);

        /**
         * Adds a condition to the WHERE clause that matches the specified object(s) at the specified path in the table
         * graph. When the object(s) are records representing a table, the `path` acts as a search path to locate the
         * table within the table graph — meaning it does not need to be an exact match. In other scenarios, where the
         * object(s) represent fields (including primary keys), the `path` must be an exact match to the field's
         * location in the table graph.
         *
         * @param path the path to the object in the table graph. For record (table) based filters, this is a search
         *             path to the table.
         * @param operator the operator to use for the comparison.
         * @param o the object(s) to match, which can be primary keys, records representing the table, or fields in the
         *          table graph.
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
    interface PredicateBuilder<T extends Record, R, ID> {

        /**
         * Adds a predicate to the WHERE clause using an AND condition.
         *
         * <p>This method combines the specified predicate with existing predicates using an AND operation, ensuring
         * that all added conditions must be true.</p>
         *
         * @param predicate the predicate to add.
         * @return the predicate builder.
         */
        PredicateBuilder<T, R, ID> and(@Nonnull PredicateBuilder<T, R, ID> predicate);

        /**
         * Adds a predicate to the WHERE clause using an OR condition.
         *
         * <p>This method combines the specified predicate with existing predicates using an OR operation, allowing any
         * of the added conditions to be true.</p>
         *
         * @param predicate the predicate to add.
         * @return the predicate builder.
         */
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
        return where(predicate -> predicate.filter(o));
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
        return where(predicate -> predicate.filter(it));
    }

    /**
     * Adds a condition to the WHERE clause that matches the specified objects at the specified path in the table
     * graph. When the objects are records representing a table, the `path` acts as a search path to locate the table
     * within the table graph — meaning it does not need to be an exact match. In other scenarios, where the objects
     * represent fields (including primary keys), the `path` must be an exact match to the field's location in the table
     * graph.
     *
     * @param path the path to the object in the table graph. For record (table) based filters, this is a search path to
     *             the table.
     * @param operator the operator to use for the comparison.
     * @param it the objects to match, which can be primary keys, records representing the table, or fields in the table
     *           graph.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> where(@Nonnull String path, @Nonnull Operator operator, @Nonnull Iterable<?> it) {
        return where(predicate -> predicate.filter(path, operator, it));
    }

    /**
     * Adds a condition to the WHERE clause that matches the specified object(s) at the specified path in the table
     * graph. When the object(s) are records representing a table, the `path` acts as a search path to locate the table
     * within the table graph — meaning it does not need to be an exact match. In other scenarios, where the object(s)
     * represent fields (including primary keys), the `path` must be an exact match to the field's location in the table
     * graph.
     *
     * @param path the path to the object in the table graph. For record (table) based filters, this is a search path to the table.
     * @param operator the operator to use for the comparison.
     * @param o the object(s) to match, which can be primary keys, records representing the table, or fields in the table graph.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> where(@Nonnull String path, @Nonnull Operator operator, @Nonnull Object... o) {
        return where(predicate -> predicate.filter(path, operator, o));
    }

    /**
     * Adds a WHERE clause to the query for the specified expression.
     *
     * @param template the expression.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> where(@Nonnull StringTemplate template) {
        return where(it -> it.expression(template));
    }

    /**
     * Adds a WHERE clause to the query using a {@link WhereBuilder}.
     *
     * @param predicate the predicate to add.
     * @return the query builder.
     */
    QueryBuilder<T, R, ID> where(@Nonnull Function<WhereBuilder<T, R, ID>, PredicateBuilder<?, ?, ?>> predicate);

    /**
     * Append the query with a string template.
     *
     * @param template the string template to append.
     * @return the query builder.
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
     * <p>Note that the prepared query must be closed after usage to prevent resource leaks. As the prepared query is
     * AutoCloseable, it is recommended to use it within a try-with-resources block.</p>
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
     * <p>The resulting stream is lazily loaded, meaning that the records are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of records.</p>
     *
     * <p>Note that calling this method does trigger the execution of the underlying query, so it should only be invoked 
     * when the query is intended to run. Since the stream holds resources open while in use, it must be closed after 
     * usage to prevent resource leaks. As the stream is AutoCloseable, it is recommended to use it within a 
     * try-with-resources block.</p>
     *
     * @return a stream of results.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    Stream<R> getResultStream();

    /**
     * Executes the query and returns a stream of using the specified callback. This method retrieves the records and
     * applies the provided callback to process them, returning the result produced by the callback.
     *
     * <p>This method ensures efficient handling of large data sets by loading entities only as needed.
     * It also manages lifecycle of the callback stream, automatically closing the stream after processing to prevent
     * resource leaks.</p>
     *
     * @param callback a {@link ResultCallback} defining how to process the stream of records and produce a result.
     * @param <X> the type of result produced by the callback after processing the entities.
     * @return the result produced by the callback's processing of the record stream.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     * connectivity.
     */
    default <X> X getResult(@Nonnull ResultCallback<R, X> callback) {
        try (Stream<R> stream = getResultStream()) {
            return callback.process(stream);
        }
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
     * Execute a DELETE statement.
     *
     * @return the number of rows impacted as result of the statement.
     * @throws PersistenceException if the statement fails.
     */
    default int executeUpdate() {
        return build().executeUpdate();
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
    static <X, Y> Stream<Y> slice(@Nonnull Stream<X> stream,
                                  int batchSize,
                                  @Nonnull Function<List<X>, Stream<Y>> function) {
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
