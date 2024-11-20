package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.PersistenceException;
import st.orm.Query;
import st.orm.template.JoinType;
import st.orm.template.ORMTemplate;
import st.orm.template.Operator;
import st.orm.template.QueryBuilder;
import st.orm.template.impl.Elements.ObjectExpression;
import st.orm.template.impl.Elements.TableSource;
import st.orm.template.impl.Elements.TableTarget;
import st.orm.template.impl.Elements.TemplateExpression;
import st.orm.template.impl.Elements.TemplateSource;
import st.orm.template.impl.Elements.TemplateTarget;
import st.orm.template.impl.Elements.Where;
import st.orm.template.impl.SqlTemplateImpl.Join;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.StringTemplate.RAW;
import static java.util.Objects.requireNonNull;
import static st.orm.Templates.from;
import static st.orm.Templates.select;
import static st.orm.template.JoinType.cross;
import static st.orm.template.JoinType.inner;
import static st.orm.template.JoinType.left;
import static st.orm.template.JoinType.right;
import static st.orm.template.Operator.EQUALS;
import static st.orm.template.Operator.IN;

public final class QueryBuilderImpl<T extends Record, R, ID> implements QueryBuilder<T, R, ID> {
    private final ORMTemplate orm;
    private final StringTemplate selectTemplate;
    private final Class<T> fromType;
    private final Class<R> selectType;
    private final boolean distinct;
    private final List<Join> join;
    private final List<Where> where;
    private final List<StringTemplate> templates;

    public QueryBuilderImpl(@Nonnull ORMTemplate orm, @Nonnull Class<T> fromType, @Nonnull Class<R> selectType) {
        //noinspection unchecked
        this(orm, fromType, selectType, RAW."\{select((Class<? extends Record>) selectType)}");
    }

    public QueryBuilderImpl(@Nonnull ORMTemplate orm,
                            @Nonnull Class<T> fromType,
                            @Nonnull Class<R> selectType,
                            @Nonnull StringTemplate selectTemplate) {
        this(orm, fromType, selectType, false, List.of(), List.of(), selectTemplate, List.of());
    }

    private QueryBuilderImpl(@Nonnull ORMTemplate orm,
                             @Nonnull Class<T> fromType,
                             @Nonnull Class<R> selectType,
                             boolean distinct,
                             @Nonnull List<Join> join,
                             @Nonnull List<Where> where,
                             @Nonnull StringTemplate selectTemplate,
                             @Nonnull List<StringTemplate> templates) {
        this.orm = orm;
        this.fromType = fromType;
        this.selectType = selectType;
        this.distinct = distinct;
        this.join = List.copyOf(join);
        this.where = List.copyOf(where);
        this.selectTemplate = selectTemplate;
        this.templates = List.copyOf(templates);
    }

    /**
     * Marks the current query as a distinct query.
     *
     * @return the query builder.
     */
    @Override
    public QueryBuilder<T, R, ID> distinct() {
        return new QueryBuilderImpl<>(orm, fromType, selectType, true, join, where, selectTemplate, templates);
    }

    private QueryBuilder<T, R, ID> join(@Nonnull Join join) {
        List<Join> copy = new ArrayList<>(this.join);
        copy.add(join);
        return new QueryBuilderImpl<>(orm, fromType, selectType, distinct, copy, where, selectTemplate, templates);
    }

    private QueryBuilder<T, R, ID> where(@Nonnull Where where) {
        List<Where> copy = new ArrayList<>(this.where);
        copy.add(where);
        return new QueryBuilderImpl<>(orm, fromType, selectType, distinct, join, copy, selectTemplate, templates);
    }

    /**
     * Returns a processor that can be used to append the query with a string template.
     *
     * @return a processor that can be used to append the query with a string template.
     */
    @Override
    public QueryBuilder<T, R, ID> append(@Nonnull StringTemplate template) {
        List<StringTemplate> copy = new ArrayList<>(templates);
        if (!template.fragments().isEmpty()) {
            template = StringTemplate.combine(RAW."\n", template);
        }
        copy.add(template);
        return new QueryBuilderImpl<>(orm, fromType, selectType, distinct, join, where, selectTemplate, copy);
    }

    /**
     * Adds a cross join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    @Override
    public QueryBuilder<T, R, ID> crossJoin(@Nonnull Class<? extends Record> relation) {
        return join(cross(), relation, "").on(RAW."");
    }

    /**
     * Adds an inner join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    @Override
    public TypedJoinBuilder<T, R, ID> innerJoin(@Nonnull Class<? extends Record> relation) {
        return join(inner(), relation, "");
    }

    /**
     * Adds a left join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    @Override
    public TypedJoinBuilder<T, R, ID> leftJoin(@Nonnull Class<? extends Record> relation) {
        return join(left(), relation, "");
    }

    /**
     * Adds a right join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    @Override
    public TypedJoinBuilder<T, R, ID> rightJoin(@Nonnull Class<? extends Record> relation) {
        return join(right(), relation, "");
    }

    /**
     * Adds a join of the specified type to the query.
     *
     * @param type the join type.
     * @param template the template to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    @Override
    public JoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull StringTemplate template, @Nonnull String alias) {
        requireNonNull(type, "type");
        requireNonNull(alias, "alias");
        return onTemplate -> join(new Join(new TemplateSource(template), alias, new TemplateTarget(onTemplate), type));
    }

    /**
     * Adds a cross join to the query.
     *
     * @param template the template to join.
     * @return the query builder.
     */
    @Override
    public QueryBuilder<T, R, ID> crossJoin(@Nonnull StringTemplate template) {
        return join(cross(), template, "").on(RAW."");
    }

    /**
     * Adds an inner join to the query.
     *
     * @param template the template to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    @Override
    public JoinBuilder<T, R, ID> innerJoin(@Nonnull StringTemplate template, @Nonnull String alias) {
        return join(inner(), template, alias);
    }

    /**
     * Adds a left join to the query.
     *
     * @param template the template to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    @Override
    public JoinBuilder<T, R, ID> leftJoin(@Nonnull StringTemplate template, @Nonnull String alias) {
        return join(left(), template, alias);
    }

    /**
     * Adds a right join to the query.
     *
     * @param template the template to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    @Override
    public JoinBuilder<T, R, ID> rightJoin(@Nonnull StringTemplate template, @Nonnull String alias) {
        return join(right(), template, alias);
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
    public TypedJoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull Class<? extends Record> relation, @Nonnull String alias) {
        requireNonNull(type, "type");
        requireNonNull(relation, "relation");
        requireNonNull(alias, "alias");
        return new TypedJoinBuilder<>() {
            @Override
            public QueryBuilder<T, R, ID> on(@Nonnull Class<? extends Record> onRelation) {
                return join(new Join(new TableSource(relation), alias, new TableTarget(onRelation), type));
            }

            @Override
            public QueryBuilder<T, R, ID> on(@Nonnull StringTemplate onTemplate) {
                return join(new Join(new TableSource(relation), alias, new TemplateTarget(onTemplate), type));
            }
        };
    }

    // Define the raw templates
    private static final StringTemplate RAW_AND = RAW." AND (";
    private static final StringTemplate RAW_OR = RAW." OR (";
    private static final StringTemplate RAW_CLOSE = RAW.")";

    /**
     * Adds a WHERE clause to the query using a {@link WhereBuilder}.
     *
     * @param predicate the predicate to add.
     * @return the query builder.
     */
    @Override
    public QueryBuilder<T, R, ID> wherePredicate(@Nonnull Function<WhereBuilder<T, R, ID>, PredicateBuilder<T, R, ID>> predicate) {
        requireNonNull(predicate, "predicate");
        class PredicateBuilderImpl<TX extends Record, RX, IDX> implements PredicateBuilder<TX, RX, IDX> {
            private final List<StringTemplate> templates = new ArrayList<>();

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
        class WhereBuilderImpl<TX extends Record, RX, IDX> implements WhereBuilder<TX, RX, IDX> {
            public WhereBuilderImpl() {
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> expression(@Nonnull StringTemplate template) {
                return new PredicateBuilderImpl<>(template);
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> filter(@Nonnull Object o) {
                return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(o, EQUALS, null)}");
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> filter(@Nonnull Iterable<?> it) {
                return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(it, IN, null)}");
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
                return (QueryBuilder<TX, RX, IDX>) where(new Where(new TemplateExpression(StringTemplate.combine(templates)), null));
            }
        }
        var whereBuilder = new WhereBuilderImpl<T, R, ID>();
        return whereBuilder.build(((PredicateBuilderImpl<T, R, ID>) predicate.apply(whereBuilder)).getTemplates());
    }

    /**
     * Builds the query based on the current state of the query builder.
     *
     * @return the constructed query.
     */
    @Override
    public Query build() {
        StringTemplate combined = StringTemplate.combine(StringTemplate.of(STR."SELECT \{distinct ? "DISTINCT " : ""}"), selectTemplate);
        combined = StringTemplate.combine(combined, RAW."\nFROM \{from(fromType, true)}");
        if (!join.isEmpty()) {
            combined = join.stream()
                    .reduce(combined,
                            (acc, join) -> StringTemplate.combine(acc, RAW."\{join}"),
                            StringTemplate::combine);
        }
        if (!where.isEmpty()) {
            // We'll leave handling of multiple where's to the sql processor.
            combined = where.stream()
                    .reduce(StringTemplate.combine(combined, RAW."\nWHERE "),
                            (acc, where) -> StringTemplate.combine(acc, RAW."\{where}"),
                            StringTemplate::combine);
        }
        if (!templates.isEmpty()) {
            combined = StringTemplate.combine(combined, StringTemplate.combine(templates));
        }
        return orm.query(combined);
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
     * usage to prevent resource leaks. As the stream is AutoCloseable, it is recommended to use it within a
     * try-with-resources block.</p>
     *
     * @return a stream of results.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    @Override
    public Stream<R> getResultStream() {
        return build().getResultStream(selectType);
    }
}
