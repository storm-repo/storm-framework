/*
 * Copyright 2024 the original author or authors.
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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.template.JoinType;
import st.orm.template.Operator;
import st.orm.template.QueryBuilder;
import st.orm.template.QueryTemplate;
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

import static java.lang.StringTemplate.RAW;
import static java.util.Objects.requireNonNull;
import static st.orm.template.JoinType.cross;
import static st.orm.template.JoinType.inner;
import static st.orm.template.JoinType.left;
import static st.orm.template.JoinType.right;
import static st.orm.template.Operator.EQUALS;
import static st.orm.template.Operator.IN;

abstract class QueryBuilderImpl<T extends Record, R, ID> implements QueryBuilder<T, R, ID>, Subqueryable {
    protected final QueryTemplate queryTemplate;
    protected final Class<T> fromType;
    protected final List<Join> join;
    protected final List<Where> where;
    protected final List<StringTemplate> templates;

    protected QueryBuilderImpl(@Nonnull QueryTemplate queryTemplate,
                               @Nonnull Class<T> fromType,
                               @Nonnull List<Join> join,
                               @Nonnull List<Where> where,
                               @Nonnull List<StringTemplate> templates) {
        this.queryTemplate = queryTemplate;
        this.fromType = fromType;
        this.join = List.copyOf(join);
        this.where = List.copyOf(where);
        this.templates = List.copyOf(templates);
    }

    abstract QueryBuilder<T, R, ID> copyWith(@Nonnull QueryTemplate orm,
                                             @Nonnull Class<T> fromType,
                                             @Nonnull List<Join> join,
                                             @Nonnull List<Where> where,
                                             @Nonnull List<StringTemplate> templates);

    abstract boolean supportsJoin();

    private QueryBuilder<T, R, ID> join(@Nonnull Join join) {
        List<Join> copy = new ArrayList<>(this.join);
        copy.add(join);
        return copyWith(queryTemplate, fromType, copy, where, templates);
    }

    private QueryBuilder<T, R, ID> where(@Nonnull Where where) {
        List<Where> copy = new ArrayList<>(this.where);
        copy.add(where);
        return copyWith(queryTemplate, fromType, join, copy, templates);
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
        return copyWith(queryTemplate, fromType, join, where, copy);
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
     * Adds a join of the specified type to the query using a template.
     *
     * @param type the join type.
     * @param template the template to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    @Override
    public JoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull StringTemplate template, @Nonnull String alias) {
        requireNonNull(type, "type");
        requireNonNull(type, "template");
        requireNonNull(alias, "alias");
        return onTemplate -> join(new Join(new TemplateSource(template), alias, new TemplateTarget(onTemplate), type));
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
    public JoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull QueryBuilder<?, ?, ?> subquery, @Nonnull String alias) {
        requireNonNull(type, "type");
        requireNonNull(type, "subquery");
        requireNonNull(alias, "alias");
        return onTemplate -> join(new Join(new TemplateSource(RAW."\{subquery}"), alias, new TemplateTarget(onTemplate), type));
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
        if (!supportsJoin()) {
            throw new UnsupportedOperationException("Joins are not supported in this query.");
        }
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
    public QueryBuilder<T, R, ID> where(@Nonnull Function<WhereBuilder<T, R, ID>, PredicateBuilder<?, ?, ?>> predicate) {
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

            @Override
            public <F extends Record, S> QueryBuilder<F, S, ?> subquery(@Nonnull Class<F> fromType, @Nonnull Class<S> selectType, @Nonnull StringTemplate template) {
                return queryTemplate.subquery(fromType, selectType, template);
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> expression(@Nonnull StringTemplate template) {
                return new PredicateBuilderImpl<>(template);
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> exists(@Nonnull QueryBuilder<?, ?, ?> subquery) {
                return new PredicateBuilderImpl<>(RAW."EXISTS (\{subquery})");
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> notExists(@Nonnull QueryBuilder<?, ?, ?> subquery) {
                return new PredicateBuilderImpl<>(RAW."NOT EXISTS (\{subquery})");
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
        return whereBuilder.build(((PredicateBuilderImpl<?, ?, ?>) predicate.apply(whereBuilder)).getTemplates());
    }
}
