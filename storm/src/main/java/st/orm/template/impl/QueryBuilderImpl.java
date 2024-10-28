package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.Query;
import st.orm.template.JoinType;
import st.orm.template.ORMTemplate;
import st.orm.template.Operator;
import st.orm.template.QueryBuilder;
import st.orm.template.TemplateFunction;
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
import static st.orm.Templates.select;
import static st.orm.template.JoinType.cross;
import static st.orm.template.JoinType.inner;
import static st.orm.template.JoinType.left;
import static st.orm.template.JoinType.right;
import static st.orm.template.Operator.EQUALS;
import static st.orm.template.Operator.IN;

public class QueryBuilderImpl<T, R, ID> implements QueryBuilder<T, R, ID> {
    private final ORMTemplate orm;
    private final StringTemplate selectTemplate;
    private final Class<T> fromType;
    private final Class<R> selectType;
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
        this(orm, fromType, selectType, List.of(), List.of(), selectTemplate, List.of());
    }

    public QueryBuilderImpl(@Nonnull ORMTemplate orm,
                            @Nonnull Class<T> fromType,
                            @Nonnull Class<R> selectType,
                            @Nonnull TemplateFunction templateFunction) {
        this(orm, fromType, selectType, List.of(), List.of(), TemplateFunctionHelper.template(templateFunction), List.of());
    }

    private QueryBuilderImpl(@Nonnull ORMTemplate orm,
                             @Nonnull Class<T> fromType,
                             @Nonnull Class<R> selectType,
                             @Nonnull List<Join> join,
                             @Nonnull List<Where> where,
                             @Nonnull StringTemplate selectTemplate,
                             @Nonnull List<StringTemplate> templates) {
        this.orm = orm;
        this.fromType = fromType;
        this.selectType = selectType;
        this.join = List.copyOf(join);
        this.where = List.copyOf(where);
        this.selectTemplate = selectTemplate;
        this.templates = List.copyOf(templates);
    }

    private QueryBuilder<T, R, ID> join(@Nonnull Join join) {
        List<Join> copy = new ArrayList<>(this.join);
        copy.add(join);
        return new QueryBuilderImpl<>(orm, fromType, selectType, copy, where, selectTemplate, templates);
    }

    private QueryBuilder<T, R, ID> where(@Nonnull Where where) {
        List<Where> copy = new ArrayList<>(this.where);
        copy.add(where);
        return new QueryBuilderImpl<>(orm, fromType, selectType, join, copy, selectTemplate, templates);
    }

    @Override
    public QueryBuilder<T, R, ID> append(@Nonnull StringTemplate template) {
        List<StringTemplate> copy = new ArrayList<>(templates);
        if (!template.fragments().isEmpty()) {
            template = StringTemplate.combine(RAW."\n", template);
        }
        copy.add(template);
        return new QueryBuilderImpl<>(orm, fromType, selectType, join, where, selectTemplate, copy);
    }

    @Override
    public QueryBuilder<T, R, ID> append(@Nonnull TemplateFunction function) {
        return append(TemplateFunctionHelper.template(function));
    }

    @Override
    public QueryBuilder<T, R, ID> crossJoin(@Nonnull Class<? extends Record> relation) {
        return join(cross(), relation, "").on(RAW."");
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
    public JoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull String alias, @Nonnull StringTemplate template) {
        requireNonNull(type, "type");
        requireNonNull(alias, "alias");
        return new JoinBuilder<>() {
            @Override
            public QueryBuilder<T, R, ID> on(@Nonnull StringTemplate onTemplate) {
                return join(new Join(new TemplateSource(template), alias, new TemplateTarget(onTemplate), type));
            }

            @Override
            public QueryBuilder<T, R, ID> on(@Nonnull TemplateFunction function) {
                return on(TemplateFunctionHelper.template(function));
            }
        };
    }

    @Override
    public QueryBuilder<T, R, ID> crossJoin(@Nonnull StringTemplate template) {
        return join(cross(), "", template).on(RAW."");
    }

    @Override
    public JoinBuilder<T, R, ID> innerJoin(@Nonnull String alias, @Nonnull StringTemplate template) {
        return join(inner(), alias, template);
    }

    @Override
    public JoinBuilder<T, R, ID> leftJoin(@Nonnull String alias, @Nonnull StringTemplate template) {
        return join(left(), alias, template);
    }

    @Override
    public JoinBuilder<T, R, ID> rightJoin(@Nonnull String alias, @Nonnull StringTemplate template) {
        return join(right(), alias, template);
    }

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

            @Override
            public QueryBuilder<T, R, ID> on(@Nonnull TemplateFunction function) {
                return on(TemplateFunctionHelper.template(function));
            }
        };
    }

    @Override
    public JoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull String alias, @Nonnull TemplateFunction function) {
        return join(type, alias, TemplateFunctionHelper.template(function));
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
            public PredicateBuilder<TX, RX, IDX> expression(@Nonnull StringTemplate template) {
                return new PredicateBuilderImpl<>(template);
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> expression(@Nonnull TemplateFunction function) {
                return new PredicateBuilderImpl<>(TemplateFunctionHelper.template(function));
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
        var predicate = expression.apply(whereBuilder);
        return whereBuilder.build(((PredicateBuilderImpl<T, R, ID>) predicate).getTemplates());
    }

    @Override
    public Query build() {
        StringTemplate combined = StringTemplate.combine(RAW."SELECT ", selectTemplate);
        combined = StringTemplate.combine(combined, RAW."\nFROM \{fromType}");
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

    @Override
    public Stream<R> getResultStream() {
        return build().getResultStream(selectType);
    }
}
