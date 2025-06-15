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
package st.orm.kotlin.template.impl;

import jakarta.annotation.Nonnull;
import kotlin.reflect.KClass;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.kotlin.KQuery;
import st.orm.kotlin.CloseableSequence;
import st.orm.kotlin.template.KJoinBuilder;
import st.orm.kotlin.template.KPredicateBuilder;
import st.orm.kotlin.template.KQueryBuilder;
import st.orm.kotlin.template.KTypedJoinBuilder;
import st.orm.kotlin.template.KWhereBuilder;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.JoinType;
import st.orm.template.Metamodel;
import st.orm.template.Operator;
import st.orm.template.QueryBuilder;
import st.orm.template.JoinBuilder;
import st.orm.template.PredicateBuilder;
import st.orm.template.TypedJoinBuilder;
import st.orm.template.WhereBuilder;
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
     * Returns a query builder that does not require a WHERE clause for UPDATE and DELETE queries.
     *
     * <p>This method is used to prevent accidental updates or deletions of all records in a table when a WHERE clause
     * is not provided.</p>
     *
     * @since 1.2
     */
    @Override
    public KQueryBuilder<T, R, ID> safe() {
        return new KQueryBuilderImpl<>(builder.safe());
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
     * Executes the query and returns a stream of results.
     *
     * <p>The resulting sequence is lazily loaded, meaning that the entities are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of entities.</p>
     *
     * <p>Note that calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the sequence holds resources open
     * while in use, it must be closed after usage to prevent resource leaks.</p>
     *
     * @return a stream of results.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     *                              connectivity.
     * @since 1.3
     */
    @Override
    public CloseableSequence<R> getResultSequence() {
        return CloseableSequence.from(getResultStream());
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
        final PredicateBuilder<TX, RX, IDX> predicateBuilder;

        KPredicateBuilderImpl(@Nonnull PredicateBuilder<TX, RX, IDX> predicateBuilder) {
            this.predicateBuilder = predicateBuilder;
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> and(@Nonnull KPredicateBuilder<TX, ?, ?> predicate) {
            return new KPredicateBuilderImpl<>(predicateBuilder.and(((KPredicateBuilderImpl<TX, ?, ?>) predicate).predicateBuilder));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> andAny(@Nonnull KPredicateBuilder<?, ?, ?> predicate) {
            return new KPredicateBuilderImpl<>(predicateBuilder.andAny(((KPredicateBuilderImpl<?, ?, ?>) predicate).predicateBuilder));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> or(@Nonnull KPredicateBuilder<TX, ?, ?> predicate) {
            return new KPredicateBuilderImpl<>(predicateBuilder.or(((KPredicateBuilderImpl<TX, ?, ?>) predicate).predicateBuilder));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> orAny(@Nonnull KPredicateBuilder<?, ?, ?> predicate) {
            return new KPredicateBuilderImpl<>(predicateBuilder.orAny(((KPredicateBuilderImpl<?, ?, ?>) predicate).predicateBuilder));
        }
    }

    static class KWhereBuilderImpl<TX extends Record, RX, IDX> extends KWhereBuilder<TX, RX, IDX> {
        private final WhereBuilder<TX, RX, IDX> whereBuilder;

        private KWhereBuilderImpl(@Nonnull WhereBuilder<TX, RX, IDX> whereBuilder) {
            this.whereBuilder = whereBuilder;
        }

        @Override
        public <T extends Record> KQueryBuilder<T, ?, ?> subquery(@Nonnull KClass<T> fromType, @Nonnull StringTemplate template) {
            //noinspection unchecked
            return new KQueryBuilderImpl<>(whereBuilder.subquery((Class<T>) REFLECTION.getRecordType(fromType), template));
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
        public KPredicateBuilder<TX, RX, IDX> whereId(@Nonnull IDX id) {
            return new KPredicateBuilderImpl<>(whereBuilder.whereId(id));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> whereRef(@Nonnull Ref<TX> ref) {
            return new KPredicateBuilderImpl<>(whereBuilder.whereRef(ref));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> whereAnyRef(@Nonnull Ref<? extends Record> ref) {
            return new KPredicateBuilderImpl<>(whereBuilder.whereAnyRef(ref));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> where(@Nonnull TX record) {
            return new KPredicateBuilderImpl<>(whereBuilder.where(record));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> whereAny(@Nonnull Record record) {
            return new KPredicateBuilderImpl<>(whereBuilder.whereAny(record));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> whereId(@Nonnull Iterable<? extends IDX> it) {
            return new KPredicateBuilderImpl<>(whereBuilder.whereId(it));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> whereRef(@Nonnull Iterable<? extends Ref<TX>> it) {
            return new KPredicateBuilderImpl<>(whereBuilder.whereRef(it));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> whereAnyRef(@Nonnull Iterable<? extends Ref<? extends Record>> it) {
            return new KPredicateBuilderImpl<>(whereBuilder.whereAnyRef(it));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> where(@Nonnull Iterable<? extends TX> it) {
            return new KPredicateBuilderImpl<>(whereBuilder.where(it));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> whereAny(@Nonnull Iterable<? extends Record> it) {
            return new KPredicateBuilderImpl<>(whereBuilder.whereAny(it));
        }

        @Override
        public <V extends Record> KPredicateBuilder<TX, RX, IDX> where(@Nonnull Metamodel<TX, V> path, @Nonnull Ref<V> ref) {
            return new KPredicateBuilderImpl<>(whereBuilder.where(path, ref));
        }

        @Override
        public <V extends Record> KPredicateBuilder<TX, RX, IDX> whereAny(@Nonnull Metamodel<?, V> path, @Nonnull Ref<V> ref) {
            return new KPredicateBuilderImpl<>(whereBuilder.whereAny(path, ref));
        }

        @Override
        public <V extends Record> KPredicateBuilder<TX, RX, IDX> whereRef(@Nonnull Metamodel<TX, V> path, @Nonnull Iterable<? extends Ref<V>> it) {
            return new KPredicateBuilderImpl<>(whereBuilder.whereRef(path, it));
        }

        @Override
        public <V extends Record> KPredicateBuilder<TX, RX, IDX> whereAnyRef(@Nonnull Metamodel<?, V> path, @Nonnull Iterable<? extends Ref<V>> it) {
            return new KPredicateBuilderImpl<>(whereBuilder.whereAnyRef(path, it));
        }

        @Override
        public <V> KPredicateBuilder<TX, RX, IDX> where(@Nonnull Metamodel<TX, V> path, @Nonnull Operator operator, @Nonnull Iterable<? extends V> it) {
            return new KPredicateBuilderImpl<>(whereBuilder.where(path, operator, it));
        }

        @Override
        public <V> KPredicateBuilder<TX, RX, IDX> whereAny(@Nonnull Metamodel<?, V> path, @Nonnull Operator operator, @Nonnull Iterable<? extends V> it) {
            return new KPredicateBuilderImpl<>(whereBuilder.whereAny(path, operator, it));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> where(@Nonnull StringTemplate template) throws PersistenceException {
            return new KPredicateBuilderImpl<>(whereBuilder.where(template));
        }

        @Override
        protected <V> KPredicateBuilder<TX, RX, IDX> whereImpl(@Nonnull Metamodel<?, V> path, @Nonnull Operator operator, @Nonnull V[] o) {
            return new KPredicateBuilderImpl<>(whereBuilder.whereAny(path, operator, o));
        }
    }

    /**
     * Adds a WHERE clause to the query using a {@link WhereBuilder}.
     *
     * @param predicate the predicate to add.
     * @return the query builder.
     */
    @Override
    public KQueryBuilder<T, R, ID> where(@Nonnull Function<KWhereBuilder<T, R, ID>, KPredicateBuilder<T, ?, ?>> predicate) {
        return new KQueryBuilderImpl<>(builder.where(whereBuilder -> {
            var builder = predicate.apply(new KWhereBuilderImpl<>(whereBuilder));
            return ((KPredicateBuilderImpl<T, ?, ?>) builder).predicateBuilder;
        }));
    }

    /**
     * Adds a WHERE clause to the query using a {@link WhereBuilder}.
     *
     * @param predicate the predicate to add.
     * @return the query builder.
     */
    @Override
    public KQueryBuilder<T, R, ID> whereAny(@Nonnull Function<KWhereBuilder<T, R, ID>, KPredicateBuilder<?, ?, ?>> predicate) {
        return new KQueryBuilderImpl<>(builder.whereAny(whereBuilder -> {
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
     * Adds an OFFSET clause to the query.
     *
     * @param offset the offset.
     * @return the query builder.
     * @since 1.2
     */
    @Override
    public KQueryBuilder<T, R, ID> offset(int offset) {
        return new KQueryBuilderImpl<>(builder.offset(offset));
    }

    @Override
    public StringTemplate getSubquery() {
        return ((Subqueryable) builder).getSubquery();
    }
}
