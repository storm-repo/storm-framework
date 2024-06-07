package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.persistence.PersistenceException;
import st.orm.Query;
import st.orm.Templates;
import st.orm.template.JoinType;
import st.orm.template.ORMTemplate;
import st.orm.template.Operator;
import st.orm.template.QueryBuilder;
import st.orm.template.TemplateFunction;
import st.orm.template.impl.Elements.ObjectExpression;
import st.orm.template.impl.Elements.TableSource;
import st.orm.template.impl.Elements.TemplateExpression;
import st.orm.template.impl.Elements.TemplateSource;
import st.orm.template.impl.Elements.Where;
import st.orm.template.impl.SqlTemplateImpl.Join;
import st.orm.template.impl.SqlTemplateImpl.On;

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.StringTemplate.RAW;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterators.spliteratorUnknownSize;
import static st.orm.template.JoinType.cross;
import static st.orm.template.JoinType.inner;
import static st.orm.template.JoinType.left;
import static st.orm.template.JoinType.right;
import static st.orm.template.Operator.EQUALS;

public class QueryBuilderImpl<T, R, ID> implements QueryBuilder<T, R, ID> {
    public static final int DEFAULT_BATCH_SIZE = 1_000;

    private final ORMTemplate ORM;
    private final boolean distinct;
    private final Class<T> sourceType;
    private final Class<R> resultType;
    private final StringTemplate selectTemplate;
    private final StringTemplate fromTemplate;
    private final List<StringTemplate> templates;

    public QueryBuilderImpl(@Nonnull ORMTemplate orm, @Nonnull Class<T> sourceType, @Nonnull Class<R> resultType) {
        //noinspection unchecked
        this(orm, false, RAW."\{Templates.select((Class<? extends Record>) resultType)}", RAW."\{Templates.from((Class<? extends Record>) sourceType)}", List.of(), sourceType, resultType);
    }

    private QueryBuilderImpl(@Nonnull ORMTemplate orm, boolean distinct, @Nonnull StringTemplate selectTemplate, @Nonnull StringTemplate fromTemplate, @Nonnull List<StringTemplate> templates, @Nonnull Class<T> sourceType, @Nonnull Class<R> resultType) {
        this.ORM = orm;
        this.distinct = distinct;
        this.sourceType = sourceType;
        this.resultType = resultType;
        this.selectTemplate = selectTemplate;
        this.fromTemplate = fromTemplate;
        this.templates = List.copyOf(templates);
    }

    protected QueryBuilderImpl<T, R, ID> withTemplate(@Nonnull StringTemplate template) {
        List<StringTemplate> copy = new ArrayList<>(templates);
        copy.add(template);
        return new QueryBuilderImpl<>(ORM, distinct, selectTemplate, fromTemplate, copy, sourceType, resultType);
    }

    protected QueryBuilder<T, R, ID> invokeTemplateFunction(@Nonnull TemplateFunction function) {
        return withTemplate(getTemplate(function, true));
    }

    private StringTemplate getTemplate(@Nonnull TemplateFunction function, boolean newLine) {
        return TemplateFunctionHelper.template(function, newLine);
    }

    @Override
    public QueryBuilder<T, R, ID> withTemplate(@Nonnull TemplateFunction function) {
        return invokeTemplateFunction(function);
    }

    @Override
    public StringTemplate.Processor<QueryBuilder<T, R, ID>, PersistenceException> withTemplate() {
        return template -> withTemplate(StringTemplate.combine(RAW."\n", template));
    }

    @Override
    public QueryBuilder<T, R, ID> distinct() {
        return new QueryBuilderImpl<>(ORM, true, selectTemplate, fromTemplate, templates, sourceType, resultType);
    }

    @Override
    public <X extends Record> QueryBuilder<T, X, ID> select(@Nonnull Class<X> resultType) {
        return new QueryBuilderImpl<>(ORM, distinct, RAW."\{Templates.select(resultType)}", fromTemplate, templates, sourceType, resultType);
    }

    @Override
    public <X> QueryBuilder<T, X, ID> selectTemplate(@Nonnull Class<X> resultType, @Nonnull TemplateFunction function) {
        return selectTemplate(resultType).process(getTemplate(function, true));
    }

    @Override
    public <X> StringTemplate.Processor<QueryBuilder<T, X, ID>, PersistenceException> selectTemplate(@Nonnull Class<X> resultType) {
        return stringTemplate -> new QueryBuilderImpl<>(
                ORM,
                distinct,
                stringTemplate,
                fromTemplate,
                templates,
                sourceType,
                resultType);
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
        return selectTemplate(Long.class)."COUNT(*)"
                .singleResult();
    }

    @Override
    public QueryBuilder<T, R, ID> crossJoin(@Nonnull Class<? extends Record> relation) {
        return join(cross(), relation, "").on()."";
    }

    @Override
    public TypedJoinBuilder<T, R, ID> innerJoin(@Nonnull Class<? extends Record> relation) {
        return join(inner(), relation, "");
    }

    @Override
    public TypedJoinBuilder<T, R, ID> leftJoin(@Nonnull Class<? extends Record> relation) {
        return join(left(), relation, "");
    }

    @Override
    public TypedJoinBuilder<T, R, ID> rightJoin(@Nonnull Class<? extends Record> relation) {
        return join(right(), relation, "");
    }

    @Override
    public StringTemplate.Processor<JoinBuilder<T, R, ID>, PersistenceException> join(@Nonnull JoinType type, @Nonnull String alias) {
        requireNonNull(type, "type");
        requireNonNull(alias, "alias");
        return sourceTemplate -> (JoinBuilder<T, R, ID>) () -> new OnBuilder<>() {
            @Override
            public QueryBuilder<T, R, ID> template(@Nonnull TemplateFunction function) {
                return process(getTemplate(function, false));
            }

            @Override
            public QueryBuilder<T, R, ID> process(StringTemplate onTemplate) throws PersistenceException {
                return withTemplate(RAW."\{new Join(new TemplateSource(sourceTemplate), alias, onTemplate, type)}");
            }
        };
    }

    @Override
    public StringTemplate.Processor<QueryBuilder<T, R, ID>, PersistenceException> crossJoin() {
        return template -> join(cross(), "").process(template).on()."";
    }

    @Override
    public StringTemplate.Processor<JoinBuilder<T, R, ID>, PersistenceException> innerJoin(@Nonnull String alias) {
        return join(inner(), alias);
    }

    @Override
    public StringTemplate.Processor<JoinBuilder<T, R, ID>, PersistenceException> leftJoin(@Nonnull String alias) {
        return join(left(), alias);
    }

    @Override
    public StringTemplate.Processor<JoinBuilder<T, R, ID>, PersistenceException> rightJoin(@Nonnull String alias) {
        return join(right(), alias);
    }

    @Override
    public TypedJoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull Class<? extends Record> relation, @Nonnull String alias) {
        requireNonNull(type, "type");
        requireNonNull(relation, "relation");
        requireNonNull(alias, "alias");
        return new TypedJoinBuilder<>() {
            @Override
            public QueryBuilder<T, R, ID> on(@Nonnull Class<? extends Record> onRelation) {
                return withTemplate(RAW."\{new Join(new TableSource(relation), alias, RAW."\{new On(relation, onRelation)}", type)}");
            }

            @Override
            public OnBuilder<T, R, ID> on() {
                return new OnBuilder<>() {
                    @Override
                    public QueryBuilder<T, R, ID> template(@Nonnull TemplateFunction function) {
                        return process(getTemplate(function, false));
                    }

                    @Override
                    public QueryBuilder<T, R, ID> process(StringTemplate stringTemplate) throws PersistenceException {
                        return withTemplate(RAW."\{new Join(new TableSource(relation), alias, stringTemplate, type)}");
                    }
                };
            }
        };
    }

    @Override
    public JoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull String alias, @Nonnull TemplateFunction function) {
        return join(type, alias).process(getTemplate(function, false));
    }

    // Define the raw templates
    private static final StringTemplate RAW_AND = RAW." AND (";
    private static final StringTemplate RAW_OR = RAW." OR (";
    private static final StringTemplate RAW_CLOSE = RAW.")";

    @Override
    public QueryBuilder<T, R, ID> where(@Nonnull Function<WhereBuilder<T, R, ID>, PredicateBuilder<T, R, ID>> expression) {
        requireNonNull(expression, "expression");
        class PredicateBuilderImpl<TX, RX, IDX> implements PredicateBuilder<TX, RX, IDX> {
            private final List<StringTemplate> templates = new ArrayList<>();
            private final AtomicInteger nestLevel = new AtomicInteger(0);

            PredicateBuilderImpl(@Nonnull StringTemplate template) {
                templates.add(requireNonNull(template, "template"));
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> and(@Nonnull PredicateBuilder<TX, RX, IDX> predicate) {
                templates.add(RAW_AND);
                templates.addAll(((PredicateBuilderImpl<TX, RX, IDX>) predicate).templates);
                templates.add(RAW_CLOSE);
                return this;
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> or(@Nonnull PredicateBuilder<TX, RX, IDX> predicate) {
                templates.add(RAW_OR);
                templates.addAll(((PredicateBuilderImpl<TX, RX, IDX>) predicate).templates);
                templates.add(RAW_CLOSE);
                return this;
            }

            private List<StringTemplate> getTemplates() {
                return templates;
            }
        }
        class WhereBuilderImpl<TX, RX, IDX> implements WhereBuilder<TX, RX, IDX> {
            public WhereBuilderImpl() {
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> process(StringTemplate template) throws PersistenceException {
                return new PredicateBuilderImpl<>(template);
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> template(@Nonnull TemplateFunction function) {
                return new PredicateBuilderImpl<>(getTemplate(function, false));
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> filter(@Nonnull Object o) {
                return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(o, EQUALS, null)}");
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> filter(@Nonnull Iterable<?> it) {
                return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(it, EQUALS, null)}");
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> filter(@Nonnull String path, @Nonnull Operator operator, @Nonnull Iterable<?> it) {
                return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(it, operator, requireNonNull(path, "path"))}");
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> filter(@Nonnull String path, @Nonnull Operator operator, @Nonnull Object... o) {
                return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(o, operator, requireNonNull(path, "path"))}");
            }

            private QueryBuilder<TX, RX, IDX> build(List<StringTemplate> templates) {
                //noinspection unchecked
                return (QueryBuilder<TX, RX, IDX>) QueryBuilderImpl.this.withTemplate(RAW."\nWHERE \{new Where(new TemplateExpression(StringTemplate.combine(templates)), null)}");
            }
        }
        var whereBuilder = new WhereBuilderImpl<T, R, ID>();
        var predicate = expression.apply(whereBuilder);
        return whereBuilder.build(((PredicateBuilderImpl<T, R, ID>) predicate).getTemplates());
    }

    private Query build(@Nonnull StringTemplate template) {
        List<StringTemplate> copy = new ArrayList<>(templates);
        if (!template.fragments().isEmpty()) {
            copy.add(RAW."\n");
        }
        copy.add(template);
        StringTemplate combined = StringTemplate.combine(StringTemplate.combine(
                RAW."SELECT ",
                distinct ? RAW."DISTINCT " : RAW."",
                selectTemplate,
                RAW."\nFROM ",
                fromTemplate
        ), StringTemplate.combine(copy));
        return ORM.process(combined);
    }

    @Override
    public Query build() {
        return build(RAW."");
    }

    @Override
    public Stream<R> stream(@Nonnull TemplateFunction function) {
        return invokeTemplateFunction(function).stream();
    }

    @Override
    public Stream<R> stream() {
        return process(RAW."");
    }

    protected <X> Stream<X> toStream(@Nonnull Iterable<X> iterable) {
        return StreamSupport.stream(spliteratorUnknownSize(iterable.iterator(), Spliterator.ORDERED), false);
    }

    @Override
    public Stream<R> process(@Nonnull StringTemplate template) {
        return build(template).getResultStream(resultType);
    }
}
