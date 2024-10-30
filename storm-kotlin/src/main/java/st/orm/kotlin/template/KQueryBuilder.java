package st.orm.kotlin.template;

import jakarta.annotation.Nonnull;
import kotlin.reflect.KClass;
import st.orm.NoResultException;
import st.orm.NonUniqueResultException;
import st.orm.kotlin.KQuery;
import st.orm.kotlin.KResultCallback;
import st.orm.template.JoinType;
import st.orm.template.Operator;
import st.orm.template.QueryBuilder;
import st.orm.template.TemplateFunction;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.StringTemplate.RAW;

public interface KQueryBuilder<T, R, ID> {

    interface KTypedJoinBuilder<T, R, ID> extends KJoinBuilder<T, R, ID> {

        KQueryBuilder<T, R, ID> on(@Nonnull KClass<? extends Record> relation);
    }

    interface KJoinBuilder<T, R, ID> {

        KQueryBuilder<T, R, ID> on(@Nonnull StringTemplate template);

        KQueryBuilder<T, R, ID> on(@Nonnull TemplateFunction function);
    }

    interface KWhereBuilder<T, R, ID> {

        /**
         * A predicate that always evaluates to true.
         */
        default KPredicateBuilder<T, R, ID> TRUE() {
            return expression(RAW."TRUE");
        }

        /**
         * A predicate that always evaluates to false.
         */
        default KPredicateBuilder<T, R, ID> FALSE() {
            return expression(RAW."FALSE");
        }

        KPredicateBuilder<T, R, ID> expression(@Nonnull StringTemplate template);

        KPredicateBuilder<T, R, ID> expression(@Nonnull TemplateFunction function);

        /**
         * Adds a condition to the WHERE clause that matches the specified object. The object can be the primary
         * key of the table, or a record representing the table, or any of the related tables in the table graph.
         *
         * <p>If the object type cannot be unambiguously matched, the {@link #filter(String, Operator, Object...)} method can be used to
         * specify the path to the object in the table graph.</p>
         *
         * @param o the object(s) to match.
         * @return the predicate builder.
         */
        KPredicateBuilder<T, R, ID> filter(@Nonnull Object o);

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
        KPredicateBuilder<T, R, ID> filter(@Nonnull Iterable<?> it);

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
        KPredicateBuilder<T, R, ID> filter(@Nonnull String path, @Nonnull Operator operator, @Nonnull Iterable<?> it);

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
        KPredicateBuilder<T, R, ID> filter(@Nonnull String path, @Nonnull Operator operator, @Nonnull Object... o);
    }

    interface KPredicateBuilder<T, R, ID> {

        KPredicateBuilder<T, R, ID> and(@Nonnull KPredicateBuilder<T, R, ID> predicate);

        KPredicateBuilder<T, R, ID> or(@Nonnull KPredicateBuilder<T, R, ID> predicate);
    }

    KQueryBuilder<T, R, ID> crossJoin(@Nonnull KClass<? extends Record> relation);

    KTypedJoinBuilder<T, R, ID> innerJoin(@Nonnull KClass<? extends Record> relation);

    KTypedJoinBuilder<T, R, ID> leftJoin(@Nonnull KClass<? extends Record> relation);

    KTypedJoinBuilder<T, R, ID> rightJoin(@Nonnull KClass<? extends Record> relation);

    KTypedJoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull KClass<? extends Record> relation, @Nonnull String alias);

    KQueryBuilder<T, R, ID> crossJoin(@Nonnull TemplateFunction function);

    KJoinBuilder<T, R, ID> innerJoin(@Nonnull String alias, @Nonnull TemplateFunction function);

    KJoinBuilder<T, R, ID> leftJoin(@Nonnull String alias, @Nonnull TemplateFunction function);

    KJoinBuilder<T, R, ID> rightJoin(@Nonnull String alias, @Nonnull TemplateFunction function);

    KJoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull String alias, @Nonnull StringTemplate template);

    KJoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull String alias, @Nonnull TemplateFunction function);

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
    default KQueryBuilder<T, R, ID> where(@Nonnull Iterable<?> it) {
        return where(predicate -> predicate.filter(it));
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
    default KQueryBuilder<T, R, ID> where(@Nonnull Object... o) {
        return where(predicate -> predicate.filter(o));
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
    default KQueryBuilder<T, R, ID> where(@Nonnull String path, @Nonnull Operator operator, @Nonnull Iterable<?> it) {
        return where(predicate -> predicate.filter(path, operator, it));
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
    default KQueryBuilder<T, R, ID> where(@Nonnull String path, @Nonnull Operator operator, @Nonnull Object... o) {
        return where(predicate -> predicate.filter(path, operator, o));
    }

    KQueryBuilder<T, R, ID> where(@Nonnull Function<KWhereBuilder<T, R, ID>, KPredicateBuilder<T, R, ID>> expression);

    KQueryBuilder<T, R, ID> append(@Nonnull StringTemplate template);

    KQueryBuilder<T, R, ID> append(@Nonnull TemplateFunction function);

    KQuery build();

    default KQuery prepare() {
        return build().prepare();
    }

    Stream<R> getResultStream();

    <X> X getResult(@Nonnull KResultCallback<R, X> callback);

    default long getResultCount() {
        try (var stream = getResultStream()) {
            return stream.count();
        }
    }

    default List<R> getResultList() {
        try (var stream = getResultStream()) {
            return stream.toList();
        }
    }

    default R getSingleResult() {
        return getResultStream()
                .reduce((_, _) -> {
                    throw new NonUniqueResultException("Expected single result, but found more than one.");
                }).orElseThrow(() -> new NoResultException("Expected single result, but found none."));
    }

    default Optional<R> getOptionalResult() {
        return getResultStream()
                .reduce((_, _) -> {
                    throw new NonUniqueResultException("Expected single result, but found more than one.");
                });
    }

    /**
     * Performs the function in multiple slices, each containing up to {@code size} elements from the stream.
     *
     * @param stream the stream to batch.
     * @param batchSize the maximum number of elements to include in each batch.
     * @param function the function to apply to each batch.
     * @return a stream of results from each batch.
     * @param <X> the type of elements in the stream.
     * @param <Y> the type of elements in the result stream.
     */
    static <X, Y> Stream<Y> slice(@Nonnull Stream<X> stream, int batchSize, @Nonnull Function<List<X>, Stream<Y>> function) {
        return QueryBuilder.slice(stream, batchSize, function);
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
     * <p>The method utilizes a "tripwire" mechanism to ensure that the original stream is properly managed and closed upon
     * completion of processing, preventing resource leaks.</p>
     *
     * @param <X> the type of elements in the stream.
     * @param stream the original stream of elements to be sliced.
     * @param size the maximum number of elements to include in each slice. If {@code size} is
     * {@code Integer.MAX_VALUE}, only one slice will be returned.
     * @return a stream of slices, where each slice contains up to {@code batchSize} elements from the original stream.
     */
    static <X> Stream<List<X>> slice(@Nonnull Stream<X> stream, int size) {
        return QueryBuilder.slice(stream, size);
    }
}
