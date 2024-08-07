package st.orm.kotlin.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.persistence.PersistenceException;
import kotlin.reflect.KClass;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import st.orm.kotlin.KQuery;
import st.orm.kotlin.KResultCallback;
import st.orm.kotlin.template.KQueryBuilder;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.JoinType;
import st.orm.template.Operator;
import st.orm.template.QueryBuilder;
import st.orm.template.QueryBuilder.JoinBuilder;
import st.orm.template.QueryBuilder.PredicateBuilder;
import st.orm.template.QueryBuilder.TypedJoinBuilder;
import st.orm.template.QueryBuilder.WhereBuilder;
import st.orm.template.TemplateFunction;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;
import static java.util.Spliterators.spliteratorUnknownSize;
import static st.orm.template.JoinType.cross;
import static st.orm.template.JoinType.inner;
import static st.orm.template.JoinType.left;
import static st.orm.template.JoinType.right;

public class KQueryBuilderImpl<T, R, ID> implements KQueryBuilder<T, R, ID> {
    private final static ORMReflection REFLECTION = Providers.getORMReflection();

    private final QueryBuilder<T, R, ID> builder;

    public KQueryBuilderImpl(@Nonnull QueryBuilder<T, R, ID> builder) {
        this.builder = requireNonNull(builder, "builder");
    }

    protected <X> Sequence<X> toSequence(@Nonnull Stream<X> stream) {
        return SequencesKt.asSequence(stream.iterator());
    }

    protected <X> Stream<X> toStream(@Nonnull Sequence<X> sequence) {
        Iterator<X> iterator = sequence.iterator();
        Spliterator<X> spliterator = spliteratorUnknownSize(iterator, Spliterator.ORDERED);
        return StreamSupport.stream(spliterator, false);
    }

    @Override
    public KQueryBuilder<T, R, ID> withTemplate(@Nonnull TemplateFunction function) {
        return new KQueryBuilderImpl<>(builder.withTemplate(function));
    }

    @Override
    public StringTemplate.Processor<KQueryBuilder<T, R, ID>, PersistenceException> withTemplate() {
        return template -> new KQueryBuilderImpl<>(builder.withTemplate().process(template));
    }

    /**
     * Returns the number of entities in the database of the entity type supported by this repository.
     *
     * @return the total number of entities in the database as a long value.
     * @throws PersistenceException if the count operation fails due to underlying database issues, such as
     * connectivity.
     */
    @Override
    public long count() {
        return builder.count();
    }

    @Override
    public KQuery build() {
        return new KQueryImpl(builder.build());
    }

    @Override
    public Stream<R> stream(@Nonnull TemplateFunction function) {
        return builder.stream(function);
    }

    @Override
    public Stream<R> stream() {
        return builder.stream();
    }

    @Override
    public <X> X result(@Nonnull KResultCallback<R, X> callback) {
        return builder.result(stream -> callback.process(toSequence(stream)));
    }

    @Override
    public KQueryBuilder<T, R, ID> distinct() {
        return new KQueryBuilderImpl<>(builder.distinct());
    }

    @Override
    public <X extends Record> KQueryBuilder<T, X, ID> select(@Nonnull KClass<X> resultType) {
        //noinspection unchecked
        return new KQueryBuilderImpl<>(builder.select((Class<X>) REFLECTION.getRecordType(resultType)));
    }

    @Override
    public <X> KQueryBuilder<T, X, ID> selectTemplate(@Nonnull KClass<X> resultType, @Nonnull TemplateFunction function) {
        //noinspection unchecked
        return new KQueryBuilderImpl<>(builder.selectTemplate((Class<X>) REFLECTION.getType(resultType), function));
    }

    @Override
    public <X> StringTemplate.Processor<KQueryBuilder<T, X, ID>, PersistenceException> selectTemplate(@Nonnull KClass<X> resultType) {
        //noinspection unchecked
        return selectBuilder -> new KQueryBuilderImpl<>(builder.selectTemplate((Class<X>) REFLECTION.getType(resultType)).process(selectBuilder));
    }

    @Override
    public KQueryBuilder<T, R, ID> crossJoin(@Nonnull KClass<? extends Record> relation) {
        return join(cross(), relation, "").on().template(_ -> "");
    }

    @Override
    public KTypedJoinBuilder<T, R, ID> innerJoin(@Nonnull KClass<? extends Record> relation) {
        return join(inner(), relation, "");
    }

    @Override
    public KTypedJoinBuilder<T, R, ID> leftJoin(@Nonnull KClass<? extends Record> relation) {
        return join(left(), relation, "");
    }

    @Override
    public KTypedJoinBuilder<T, R, ID> rightJoin(@Nonnull KClass<? extends Record> relation) {
        return join(right(), relation, "");
    }

    @Override
    public KTypedJoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull KClass<? extends Record> relation, @Nonnull String alias) {
        TypedJoinBuilder<T, R, ID> joinBuilder = builder.join(type, REFLECTION.getRecordType(relation), alias);
        return new KTypedJoinBuilder<>() {
            @Override
            public KQueryBuilder<T, R, ID> on(@Nonnull KClass<? extends Record> relation) {
                return new KQueryBuilderImpl<>(joinBuilder.on(REFLECTION.getRecordType(relation)));
            }

            @Override
            public KOnBuilder<T, R, ID> on() {
                return new KOnBuilder<>() {
                    @Override
                    public KQueryBuilder<T, R, ID> process(StringTemplate stringTemplate) throws PersistenceException {
                        return new KQueryBuilderImpl<>(joinBuilder.on().process(stringTemplate));
                    }

                    public KQueryBuilder<T, R, ID> template(@Nonnull TemplateFunction function) {
                        return new KQueryBuilderImpl<>(joinBuilder.on().template(function));
                    }
                };
            }
        };
    }

    @Override
    public KQueryBuilder<T, R, ID> crossJoin(@Nonnull TemplateFunction function) {
        return join(cross(), "", function).on().template(_ -> "");
    }

    @Override
    public KJoinBuilder<T, R, ID> innerJoin(@Nonnull String alias, @Nonnull TemplateFunction function) {
        return join(inner(), alias, function);
    }

    @Override
    public KJoinBuilder<T, R, ID> leftJoin(@Nonnull String alias, @Nonnull TemplateFunction function) {
        return join(left(), alias, function);
    }

    @Override
    public KJoinBuilder<T, R, ID> rightJoin(@Nonnull String alias, @Nonnull TemplateFunction function) {
        return join(right(), alias, function);
    }

    @Override
    public StringTemplate.Processor<KJoinBuilder<T, R, ID>, PersistenceException> join(@Nonnull JoinType type, @Nonnull String alias) {
        return template -> (KJoinBuilder<T, R, ID>) () -> new KOnBuilder<>() {
            final JoinBuilder<T, R, ID> joinBuilder = builder.join(type, alias).process(template);

            @Override
            public KQueryBuilder<T, R, ID> process(StringTemplate stringTemplate) throws PersistenceException {
                return new KQueryBuilderImpl<>(joinBuilder.on().process(stringTemplate));
            }

            @Override
            public KQueryBuilder<T, R, ID> template(@Nonnull TemplateFunction function) {
                return new KQueryBuilderImpl<>(joinBuilder.on().template(function));
            }
        };
    }

    @Override
    public KJoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull String alias, @Nonnull TemplateFunction function) {
        JoinBuilder<T, R, ID> joinBuilder = builder.join(type, alias, function);
        return () -> new KOnBuilder<>() {
            @Override
            public KQueryBuilder<T, R, ID> process(StringTemplate stringTemplate) throws PersistenceException {
                return new KQueryBuilderImpl<>(joinBuilder.on().process(stringTemplate));
            }

            @Override
            public KQueryBuilder<T, R, ID> template(@Nonnull TemplateFunction function1) {
                return new KQueryBuilderImpl<>(joinBuilder.on().template(function1));
            }
        };
    }

    static class KPredicateBuilderImpl<TX, RX, IDX> implements KPredicateBuilder<TX, RX, IDX> {
        private final PredicateBuilder<TX, RX, IDX> predicateBuilder;

        KPredicateBuilderImpl(@Nonnull PredicateBuilder<TX, RX, IDX> predicateBuilder) {
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

    static class KWhereBuilderImpl<TX, RX, IDX> implements KWhereBuilder<TX, RX, IDX> {
        private final WhereBuilder<TX, RX, IDX> whereBuilder;

        KWhereBuilderImpl(@Nonnull WhereBuilder<TX, RX, IDX> whereBuilder) {
            this.whereBuilder = whereBuilder;
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> process(StringTemplate stringTemplate) throws PersistenceException {
            return new KPredicateBuilderImpl<>(whereBuilder.process(stringTemplate));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> template(@Nonnull TemplateFunction function) {
            return new KPredicateBuilderImpl<>(whereBuilder.template(function));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> filter(@Nonnull Object o) {
            return new KPredicateBuilderImpl<>(whereBuilder.filter(o));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> filter(@Nonnull Iterable<?> it) {
            return new KPredicateBuilderImpl<>(whereBuilder.filter(it));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> filter(@Nonnull String path, @Nonnull Operator operator, @Nonnull Iterable<?> it) {
            return new KPredicateBuilderImpl<>(whereBuilder.filter(path, operator, it));
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> filter(@Nonnull String path, @Nonnull Operator operator, @Nonnull Object... o) {
            return new KPredicateBuilderImpl<>(whereBuilder.filter(path, operator, o));
        }
    }

    @Override
    public KQueryBuilder<T, R, ID> where(@Nonnull Function<KWhereBuilder<T, R, ID>, KPredicateBuilder<T, R, ID>> predicate) {
        return new KQueryBuilderImpl<>(builder.where(whereBuilder -> {
            var builder = predicate.apply(new KWhereBuilderImpl<>(whereBuilder));
            return ((KPredicateBuilderImpl<T, R, ID>) builder).predicateBuilder;
        }));
    }

    @Override
    public Stream<R> process(StringTemplate stringTemplate) throws PersistenceException {
        return builder.process(stringTemplate);
    }
}
