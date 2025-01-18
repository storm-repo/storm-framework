package st.orm.kotlin.template.impl;

import jakarta.annotation.Nonnull;
import kotlin.reflect.KClass;
import st.orm.PersistenceException;
import st.orm.kotlin.KQuery;
import st.orm.kotlin.template.KQueryBuilder;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.JoinType;
import st.orm.template.Metamodel;
import st.orm.template.Operator;
import st.orm.template.QueryBuilder;
import st.orm.template.QueryBuilder.JoinBuilder;
import st.orm.template.QueryBuilder.PredicateBuilder;
import st.orm.template.QueryBuilder.TypedJoinBuilder;
import st.orm.template.QueryBuilder.WhereBuilder;
import st.orm.template.TemplateFunction;
import st.orm.template.impl.Subqueryable;

import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static st.orm.template.JoinType.cross;
import static st.orm.template.JoinType.inner;
import static st.orm.template.JoinType.left;
import static st.orm.template.JoinType.right;

public final class KQueryBuilderImpl<T extends Record, R, ID> extends KQueryBuilder<T, R, ID> implements Subqueryable {
    private final static ORMReflection REFLECTION = Providers.getORMReflection();

    private final QueryBuilder<T, R, ID> builder;

    public KQueryBuilderImpl(@Nonnull QueryBuilder<T, R, ID> builder) {
        this.builder = requireNonNull(builder, "builder");
    }

    /**
     * Returns a typed query builder for the specified primary key type.
     *
     * @param pkType the primary key type.
     * @return the typed query builder.
     * @param <X> the type of the primary key.
     * @throws PersistenceException if the pk type is not valid.
     * @since 1.2
     */
    @Override
    public <X> KQueryBuilder<T, R, X> typed(@Nonnull KClass<X> pkType) {
        //noinspection unchecked
        return new KQueryBuilderImpl<>(builder.typed((Class<X>) REFLECTION.getType(pkType)));
    }

    /**
     * Marks the current query as a distinct query.
     *
     * @return the query builder.
     */
    @Override
    public KQueryBuilder<T, R, ID> distinct() {
        return new KQueryBuilderImpl<>(builder.distinct());
    }

    /**
     * Returns a processor that can be used to append the query with a string template.
     *
     * @param template the string template to append.
     * @return a processor that can be used to append the query with a string template.
     */
    @Override
    public KQueryBuilder<T, R, ID> append(@Nonnull StringTemplate template) {
        return new KQueryBuilderImpl<>(builder.append(template));
    }

    /**
     * Locks the selected rows for reading.
     *
     * @return the query builder.
     * @throws PersistenceException if the database does not support the specified lock mode, or if the lock mode is
     * not supported for the current query.
     * @since 1.2
     */
    @Override
    public KQueryBuilder<T, R, ID> forShare() {
        return new KQueryBuilderImpl<>(builder.forShare());
    }

    /**
     * Locks the selected rows for reading.
     *
     * @return the query builder.
     * @throws PersistenceException if the database does not support the specified lock mode, or if the lock mode is
     * not supported for the current query.
     * @since 1.2
     */
    @Override
    public KQueryBuilder<T, R, ID> forUpdate() {
        return new KQueryBuilderImpl<>(builder.forUpdate());
    }

    /**
     * Locks the selected rows using a custom lock mode.
     *
     * <p>Note that this method results in non-portable code, as the lock mode is specific to the underlying database.</p>
     *
     * @return the query builder.
     * @throws PersistenceException if the lock mode is not supported for the current query.
     * @since 1.2
     */
    @Override
    public KQueryBuilder<T, R, ID> forLock(@Nonnull StringTemplate template) {
        return new KQueryBuilderImpl<>(builder.forLock(template));
    }

    /**
     * Builds the query based on the current state of the query builder.
     *
     * @return the constructed query.
     */
    @Override
    public KQuery build() {
        return new KQueryImpl(builder.build());
    }

    /**
     * Executes the query and returns a stream of results.
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
    @Override
    public Stream<R> getResultStream() {
        return builder.getResultStream();
    }

    /**
     * Adds a cross join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    @Override
    public KQueryBuilder<T, R, ID> crossJoin(@Nonnull KClass<? extends Record> relation) {
        return join(cross(), relation, "").on(TemplateFunction.template(_ -> ""));
    }

    /**
     * Adds an inner join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    @Override
    public KTypedJoinBuilder<T, R, ID> innerJoin(@Nonnull KClass<? extends Record> relation) {
        return join(inner(), relation, "");
    }

    /**
     * Adds a left join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    @Override
    public KTypedJoinBuilder<T, R, ID> leftJoin(@Nonnull KClass<? extends Record> relation) {
        return join(left(), relation, "");
    }

    /**
     * Adds a right join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    @Override
    public KTypedJoinBuilder<T, R, ID> rightJoin(@Nonnull KClass<? extends Record> relation) {
        return join(right(), relation, "");
    }

    /**
     * Adds a join of the specified type to the query.
     *
     * @param type the type of the join (e.g., INNER, LEFT, RIGHT).
     * @param relation the relation to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    @Override
    public KTypedJoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull KClass<? extends Record> relation, @Nonnull String alias) {
        TypedJoinBuilder<T, R, ID> joinBuilder = builder.join(type, REFLECTION.getRecordType(relation), alias);
        return new KTypedJoinBuilder<>() {
            @Override
            public KQueryBuilder<T, R, ID> on(@Nonnull KClass<? extends Record> relation) {
                return new KQueryBuilderImpl<>(joinBuilder.on(REFLECTION.getRecordType(relation)));
            }

            @Override
            public KQueryBuilder<T, R, ID> on(@Nonnull StringTemplate template) {
                return new KQueryBuilderImpl<>(joinBuilder.on(template));
            }
        };
    }

    /**
     * Adds a cross join to the query.
     *
     * @param template the condition to join.
     * @return the query builder.
     */
    @Override
    public KQueryBuilder<T, R, ID> crossJoin(@Nonnull StringTemplate template) {
        return join(cross(), template, "").on(_ -> "");
    }

    /**
     * Adds an inner join to the query.
     *
     * @param template the condition to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    @Override
    public KJoinBuilder<T, R, ID> innerJoin(@Nonnull StringTemplate template, @Nonnull String alias) {
        return join(inner(), template, alias);
    }

    /**
     * Adds a left join to the query.
     *
     * @param template the condition to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    @Override
    public KJoinBuilder<T, R, ID> leftJoin(@Nonnull StringTemplate template, @Nonnull String alias) {
        return join(left(), template, alias);
    }

    /**
     * Adds a right join to the query.
     *
     * @param template the condition to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    @Override
    public KJoinBuilder<T, R, ID> rightJoin(@Nonnull StringTemplate template, @Nonnull String alias) {
        return join(right(), template, alias);
    }

    /**
     * Adds a join of the specified type to the query using a template.
     *
     * @param type the join type.
     * @param template the template to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    @Override
    public KJoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull StringTemplate template, @Nonnull String alias) {
        JoinBuilder<T, R, ID> joinBuilder = builder.join(type, template, alias);
        return onTemplate -> new KQueryBuilderImpl<>(joinBuilder.on(onTemplate));
    }

    /**
     * Adds a join of the specified type to the query using a subquery.
     *
     * @param type the join type.
     * @param subquery the subquery to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    @Override
    public KJoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull KQueryBuilder<?, ?, ?> subquery, @Nonnull String alias) {
        JoinBuilder<T, R, ID> joinBuilder = builder.join(type, ((KQueryBuilderImpl<?, ?, ?>) subquery).builder, alias);
        return onTemplate -> new KQueryBuilderImpl<>(joinBuilder.on(onTemplate));
    }

    static class KPredicateBuilderImpl<TX extends Record, RX, IDX> implements KPredicateBuilder<TX, RX, IDX> {
        private final PredicateBuilder<TX, RX, IDX> predicateBuilder;

        private KPredicateBuilderImpl(@Nonnull PredicateBuilder<TX, RX, IDX> predicateBuilder) {
            this.predicateBuilder = predicateBuilder;
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> and(@Nonnull KPredicateBuilder<TX, RX, IDX> predicate) {
            return new KPredicateBuilderImpl<>(predicateBuilder.and(((KPredicateBuilderImpl<TX, RX, IDX>) predicate).predicateBuilder));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> or(@Nonnull KPredicateBuilder<TX, RX, IDX> predicate) {
            return new KPredicateBuilderImpl<>(predicateBuilder.or(((KPredicateBuilderImpl<TX, RX, IDX>) predicate).predicateBuilder));
        }
    }

    static class KWhereBuilderImpl<TX extends Record, RX, IDX> extends KWhereBuilder<TX, RX, IDX> {
        private final WhereBuilder<TX, RX, IDX> whereBuilder;

        private KWhereBuilderImpl(@Nonnull WhereBuilder<TX, RX, IDX> whereBuilder) {
            this.whereBuilder = whereBuilder;
        }

        @Override
        public <T extends Record, R> KQueryBuilder<T, R, ?> subquery(@Nonnull KClass<T> fromType, @Nonnull KClass<R> selectType, @Nonnull StringTemplate template) {
            //noinspection unchecked
            return new KQueryBuilderImpl<>(whereBuilder.subquery((Class<T>) REFLECTION.getRecordType(fromType), (Class<R>) REFLECTION.getType(selectType), template));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> expression(@Nonnull StringTemplate template) throws PersistenceException {
            return new KPredicateBuilderImpl<>(whereBuilder.expression(template));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> exists(@Nonnull KQueryBuilder<?, ?, ?> subquery) {
            return new KPredicateBuilderImpl<>(whereBuilder.exists(((KQueryBuilderImpl<?, ?, ?>) subquery).builder));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> notExists(@Nonnull KQueryBuilder<?, ?, ?> subquery) {
            return new KPredicateBuilderImpl<>(whereBuilder.notExists(((KQueryBuilderImpl<?, ?, ?>) subquery).builder));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> filter(@Nonnull IDX o) {
            return new KPredicateBuilderImpl<>(whereBuilder.filter(o));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> filter(@Nonnull TX record) {
            return new KPredicateBuilderImpl<>(whereBuilder.filter(record));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> filterAny(@Nonnull Record record) {
            return new KPredicateBuilderImpl<>(whereBuilder.filterAny(record));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> filterIds(@Nonnull Iterable<? extends IDX> it) {
            return new KPredicateBuilderImpl<>(whereBuilder.filterIds(it));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> filter(@Nonnull Iterable<? extends TX> it) {
            return new KPredicateBuilderImpl<>(whereBuilder.filter(it));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> filterAny(@Nonnull Iterable<? extends Record> it) {
            return new KPredicateBuilderImpl<>(whereBuilder.filterAny(it));
        }

        @Override
        public <V> KPredicateBuilder<TX, RX, IDX> filter(@Nonnull Metamodel<TX, V> path, @Nonnull Operator operator, @Nonnull Iterable<? extends V> it) {
            return new KPredicateBuilderImpl<>(whereBuilder.filter(path, operator, it));
        }

        @Override
        public <V> KPredicateBuilder<TX, RX, IDX> filterAny(@Nonnull Metamodel<?, V> path, @Nonnull Operator operator, @Nonnull Iterable<? extends V> it) {
            return new KPredicateBuilderImpl<>(whereBuilder.filterAny(path, operator, it));
        }

        @Override
        protected <V> KPredicateBuilder<TX, RX, IDX> filterImpl(@Nonnull Metamodel<?, V> path, @Nonnull Operator operator, @Nonnull V[] o) {
            return new KPredicateBuilderImpl<>(whereBuilder.filterAny(path, operator, o));
        }
    }

    /**
     * Adds a WHERE clause to the query using a {@link QueryBuilder.WhereBuilder}.
     *
     * @param predicate the predicate to add.
     * @return the query builder.
     */
    @Override
    public KQueryBuilder<T, R, ID> where(@Nonnull Function<KWhereBuilder<T, R, ID>, KPredicateBuilder<?, ?, ?>> predicate) {
        return new KQueryBuilderImpl<>(builder.where(whereBuilder -> {
            var builder = predicate.apply(new KWhereBuilderImpl<>(whereBuilder));
            return ((KPredicateBuilderImpl<?, ?, ?>) builder).predicateBuilder;
        }));
    }

    /**
     * Adds a LIMIT clause to the query.
     *
     * @param limit the maximum number of records to return.
     * @return the query builder.
     * @since 1.2
     */
    @Override
    public KQueryBuilder<T, R, ID> limit(int limit) {
        return new KQueryBuilderImpl<>(builder.limit(limit));
    }

    /**
     * Adds a LIMIT clause to the query.
     *
     * @param offset the offset.
     * @param limit the maximum number of records to return.
     * @return the query builder.
     * @since 1.2
     */
    @Override
    public KQueryBuilder<T, R, ID> limit(int offset, int limit) {
        return new KQueryBuilderImpl<>(builder.limit(offset, limit));
    }

    @Override
    public StringTemplate getStringTemplate() {
        return ((Subqueryable) builder).getStringTemplate();
    }
}
