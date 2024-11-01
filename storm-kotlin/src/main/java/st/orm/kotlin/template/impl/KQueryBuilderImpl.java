package st.orm.kotlin.template.impl;

import jakarta.annotation.Nonnull;
import kotlin.reflect.KClass;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;
import st.orm.PersistenceException;
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

import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static st.orm.template.JoinType.cross;
import static st.orm.template.JoinType.inner;
import static st.orm.template.JoinType.left;
import static st.orm.template.JoinType.right;
import static st.orm.template.TemplateFunction.template;

public final class KQueryBuilderImpl<T, R, ID> implements KQueryBuilder<T, R, ID> {
    private final static ORMReflection REFLECTION = Providers.getORMReflection();

    private final QueryBuilder<T, R, ID> builder;

    public KQueryBuilderImpl(@Nonnull QueryBuilder<T, R, ID> builder) {
        this.builder = requireNonNull(builder, "builder");
    }

    private <X> Sequence<X> toSequence(@Nonnull Stream<X> stream) {
        return SequencesKt.asSequence(stream.iterator());
    }

    @Override
    public KQueryBuilder<T, R, ID> distinct() {
        return new KQueryBuilderImpl<>(builder.distinct());
    }

    @Override
    public KQueryBuilder<T, R, ID> append(@Nonnull StringTemplate template) {
        return new KQueryBuilderImpl<>(builder.append(template));
    }

    @Override
    public KQuery build() {
        return new KQueryImpl(builder.build());
    }

    @Override
    public Stream<R> getResultStream() {
        return builder.getResultStream();
    }

    @Override
    public <X> X getResult(@NotNull KResultCallback<R, X> callback) {
        try (Stream<R> stream = getResultStream()) {
            return callback.process(toSequence(stream));
        }
    }

    @Override
    public KQueryBuilder<T, R, ID> crossJoin(@Nonnull KClass<? extends Record> relation) {
        return join(cross(), relation, "").on(template(_ -> ""));
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
            public KQueryBuilder<T, R, ID> on(@NotNull StringTemplate template) {
                return new KQueryBuilderImpl<>(joinBuilder.on(template));
            }
        };
    }

    @Override
    public KQueryBuilder<T, R, ID> crossJoin(@Nonnull StringTemplate template) {
        return join(cross(), template, "").on(_ -> "");
    }

    @Override
    public KJoinBuilder<T, R, ID> innerJoin(@Nonnull StringTemplate template, @Nonnull String alias) {
        return join(inner(), template, alias);
    }

    @Override
    public KJoinBuilder<T, R, ID> leftJoin(@Nonnull StringTemplate template, @Nonnull String alias) {
        return join(left(), template, alias);
    }

    @Override
    public KJoinBuilder<T, R, ID> rightJoin(@Nonnull StringTemplate template, @Nonnull String alias) {
        return join(right(), template, alias);
    }

    @Override
    public KJoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull StringTemplate template, @Nonnull String alias) {
        final JoinBuilder<T, R, ID> joinBuilder = builder.join(type, template, alias);
        return onTemplate -> new KQueryBuilderImpl<>(joinBuilder.on(onTemplate));
    }

    private record KPredicateBuilderImpl<TX, RX, IDX>(PredicateBuilder<TX, RX, IDX> predicateBuilder)
            implements KPredicateBuilder<TX, RX, IDX> {
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

    private record KWhereBuilderImpl<TX, RX, IDX>(WhereBuilder<TX, RX, IDX> whereBuilder)
            implements KWhereBuilder<TX, RX, IDX> {
        private KWhereBuilderImpl(@Nonnull WhereBuilder<TX, RX, IDX> whereBuilder) {
            this.whereBuilder = whereBuilder;
        }

        @Override
        public KPredicateBuilder<TX, RX, IDX> expression(@Nonnull StringTemplate template) throws PersistenceException {
            return new KPredicateBuilderImpl<>(whereBuilder.expression(template));
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
    public KQueryBuilder<T, R, ID> wherePredicate(@Nonnull Function<KWhereBuilder<T, R, ID>, KPredicateBuilder<T, R, ID>> predicate) {
        return new KQueryBuilderImpl<>(builder.wherePredicate(whereBuilder -> {
            var builder = predicate.apply(new KWhereBuilderImpl<>(whereBuilder));
            return ((KPredicateBuilderImpl<T, R, ID>) builder).predicateBuilder;
        }));
    }
}
