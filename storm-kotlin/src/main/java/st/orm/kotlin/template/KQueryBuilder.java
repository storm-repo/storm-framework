package st.orm.kotlin.template;

import jakarta.annotation.Nonnull;
import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.PersistenceException;
import kotlin.reflect.KClass;
import st.orm.kotlin.KQuery;
import st.orm.template.JoinType;
import st.orm.template.Operator;
import st.orm.template.TemplateFunction;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public interface KQueryBuilder<T, R, ID> extends StringTemplate.Processor<Stream<R>, PersistenceException> {

    // Don't let Builder extend Iterable<R>, because that would disallow us from closing the underlying stream.

    KQueryBuilder<T, R, ID> distinct();

    <X extends Record> KQueryBuilder<T, X, ID> select(@Nonnull KClass<X> resultType);

    <X> KQueryBuilder<T, X, ID> selectTemplate(@Nonnull KClass<X> resultType, @Nonnull TemplateFunction function);

    <X> StringTemplate.Processor<KQueryBuilder<T, X, ID>, PersistenceException> selectTemplate(@Nonnull KClass<X> resultType);

    /**
     * Returns the number of entities in the database of the entity type supported by this repository.
     *
     * @return the total number of entities in the database as a long value.
     * @throws PersistenceException if the count operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    long count();

    interface KTypedJoinBuilder<T, R, ID> extends KJoinBuilder<T, R, ID> {

        KQueryBuilder<T, R, ID> on(@Nonnull KClass<? extends Record> relation);
    }

    interface KJoinBuilder<T, R, ID> {

        KOnBuilder<T, R, ID> on();
    }

    interface KOnBuilder<T, R, ID> extends StringTemplate.Processor<KQueryBuilder<T, R, ID>, PersistenceException> {
        KQueryBuilder<T, R, ID> template(@Nonnull TemplateFunction function);
    }

    interface KWhereBuilder<T, R, ID> extends StringTemplate.Processor<KPredicateBuilder<T, R, ID>, PersistenceException> {
        KPredicateBuilder<T, R, ID> template(@Nonnull TemplateFunction function);

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

    StringTemplate.Processor<KJoinBuilder<T, R, ID>, PersistenceException> join(@Nonnull JoinType type, @Nonnull String alias);

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

    KQueryBuilder<T, R, ID> withTemplate(@Nonnull TemplateFunction function);

    StringTemplate.Processor<KQueryBuilder<T, R, ID>, PersistenceException> withTemplate();

    KQuery build();

    default KQuery prepare() {
        return build().prepare();
    }

    Stream<R> stream(@Nonnull TemplateFunction function);

    Stream<R> stream();

    default List<R> toList() {
        return stream().toList();
    }

    default R singleResult() {
        return stream()
                .reduce((_, _) -> {
                    throw new NonUniqueResultException("Expected single result, but found more than one.");
                }).orElseThrow(() -> new NoResultException("Expected single result, but found none."));
    }

    default Optional<R> optionalResult() {
        return stream()
                .reduce((_, _) -> {
                    throw new NonUniqueResultException("Expected single result, but found more than one.");
                });
    }
}
