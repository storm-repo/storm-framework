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
import st.orm.PersistenceException;
import st.orm.template.Model;
import st.orm.spi.Providers;
import st.orm.spi.SqlDialect;
import st.orm.template.JoinType;
import st.orm.template.Metamodel;
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
import java.util.function.Supplier;

import static java.lang.StringTemplate.RAW;
import static java.util.Objects.requireNonNull;
import static st.orm.template.JoinType.cross;
import static st.orm.template.JoinType.inner;
import static st.orm.template.JoinType.left;
import static st.orm.template.JoinType.right;
import static st.orm.template.Operator.EQUALS;
import static st.orm.template.Operator.IN;

/**
 * Abstract query builder implementation.
 *
 * @param <T> the type of the table being queried.
 * @param <R> the type of the result.
 * @param <ID> the type of the primary key.
 */
abstract class QueryBuilderImpl<T extends Record, R, ID> extends QueryBuilder<T, R, ID> implements Subqueryable {

    protected final static SqlDialect SQL_DIALECT = Providers.getSqlDialect();

    protected final QueryTemplate queryTemplate;
    protected final Class<T> fromType;
    protected final List<Join> join;
    protected final List<Where> where;
    protected final List<StringTemplate> templates;
    protected final Supplier<Model<T, ID>> modelSupplier;

    protected QueryBuilderImpl(@Nonnull QueryTemplate queryTemplate,
                               @Nonnull Class<T> fromType,
                               @Nonnull List<Join> join,
                               @Nonnull List<Where> where,
                               @Nonnull List<StringTemplate> templates,
                               @Nonnull Supplier<Model<T, ID>> modelSupplier) {
        this.queryTemplate = queryTemplate;
        this.fromType = fromType;
        this.join = List.copyOf(join);
        this.where = List.copyOf(where);
        this.templates = List.copyOf(templates);
        this.modelSupplier = requireNonNull(modelSupplier, "modelSupplier");
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
    public <X> QueryBuilder<T, R, X> typed(@Nonnull Class<X> pkType) {
        Model<T, ID> model = modelSupplier.get();
        if (model.primaryKeyType() != pkType) {
            throw new PersistenceException(STR."Primary key type mismatch: expected \{model.primaryKeyType()}, got \{pkType}.");
        }
        //noinspection unchecked
        return (QueryBuilder<T, R, X>) this;
    }

    /**
     * Returns a new query builder instance with the specified parameters.
     *
     * @param queryTemplate the query template.
     * @param fromType the type of the table being queried.
     * @param join the list of joins.
     * @param where the list of where clauses.
     * @param templates the list of string templates.
     * @return a new query builder.
     */
    abstract QueryBuilder<T, R, ID> copyWith(@Nonnull QueryTemplate queryTemplate,
                                             @Nonnull Class<T> fromType,
                                             @Nonnull List<Join> join,
                                             @Nonnull List<Where> where,
                                             @Nonnull List<StringTemplate> templates);

    /**
     * Returns true to indicate that the query supports joins, false otherwise.
     *
     * @return true if the query supports joins, false otherwise.
     */
    protected abstract boolean supportsJoin();

    /**
     * Returns a new query builder instance with the specified {@code join} added to the list of joins.
     *
     * @param join the join to add.
     * @return a new query builder.
     */
    private QueryBuilder<T, R, ID> addJoin(@Nonnull Join join) {
        List<Join> copy = new ArrayList<>(this.join);
        copy.add(join);
        return copyWith(queryTemplate, fromType, copy, where, templates);
    }

    /**
     * Returns a new query builder instance with the specified {@code where} added to the list of where clauses.
     *
     * @param where the where clause to add.
     * @return a new query builder.
     */
    private QueryBuilder<T, R, ID> addWhere(@Nonnull Where where) {
        List<Where> copy = new ArrayList<>(this.where);
        copy.add(where);
        return copyWith(queryTemplate, fromType, join, copy, templates);
    }

    /**
     * Append the query with a string template.
     *
     * @param template the string template to append.
     * @return the query builder.
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
        return onTemplate -> addJoin(new Join(new TemplateSource(template), alias, new TemplateTarget(onTemplate), type, false));
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
        return onTemplate -> addJoin(new Join(new TemplateSource(RAW."\{subquery}"), alias, new TemplateTarget(onTemplate), type, false));
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
                return addJoin(new Join(new TableSource(relation), alias, new TableTarget(onRelation), type, false));
            }

            @Override
            public QueryBuilder<T, R, ID> on(@Nonnull StringTemplate onTemplate) {
                return addJoin(new Join(new TableSource(relation), alias, new TemplateTarget(onTemplate), type, false));
            }
        };
    }

    // Define the raw templates
    private static final StringTemplate RAW_AND = RAW." AND ";
    private static final StringTemplate RAW_OR = RAW." OR ";
    private static final StringTemplate RAW_OPEN = RAW."(";
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
                add(RAW_AND, predicate);
                return this;
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> or(@Nonnull PredicateBuilder<TX, RX, IDX> predicate) {
                add(RAW_OR, predicate);
                return this;
            }

            private void add(@Nonnull StringTemplate operator, @Nonnull PredicateBuilder<TX, RX, IDX> predicate) {
                templates.add(operator);
                var list = ((PredicateBuilderImpl<TX, RX, IDX>) predicate).templates;
                if (list.size() > 1) {
                    templates.add(RAW_OPEN);
                }
                templates.addAll(list);
                if (list.size() > 1) {
                    templates.add(RAW_CLOSE);
                }
            }

            private List<StringTemplate> getTemplates() {
                return templates;
            }
        }
        class WhereBuilderImpl<TX extends Record, RX, IDX> extends WhereBuilder<TX, RX, IDX> {

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
            public PredicateBuilder<TX, RX, IDX> filter(@Nonnull IDX id) {
                return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(EQUALS, id)}");
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> filter(@Nonnull TX record) {
                return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(EQUALS, record)}");
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> filterAny(@Nonnull Record record) {
                return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(EQUALS, record)}");
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> filterIds(@Nonnull Iterable<? extends IDX> it) {
                return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(IN, it)}");
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> filter(@Nonnull Iterable<? extends TX> it) {
                return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(IN, it)}");
            }

            @Override
            public PredicateBuilder<TX, RX, IDX> filterAny(@Nonnull Iterable<? extends Record> it) {
                return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(IN, it)}");
            }

            @Override
            public <V> PredicateBuilder<TX, RX, IDX> filter(@Nonnull Metamodel<TX, V> path, @Nonnull Operator operator, @Nonnull Iterable<? extends V> it) {
                return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(path, operator, it)}");
            }

            @Override
            public <V> PredicateBuilder<TX, RX, IDX> filterAny(@Nonnull Metamodel<?, V> path, @Nonnull Operator operator, @Nonnull Iterable<? extends V> it) {
                return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(path, operator, it)}");
            }

            @Override
            protected <V> PredicateBuilder<TX, RX, IDX> filterImpl(@Nonnull Metamodel<?, V> path, @Nonnull Operator operator, @Nonnull V[] o) {
                return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(path, operator, o)}");
            }

            private QueryBuilder<TX, RX, IDX> build(List<StringTemplate> templates) {
                //noinspection unchecked
                return (QueryBuilder<TX, RX, IDX>) addWhere(new Where(new TemplateExpression(StringTemplate.combine(templates)), null));
            }
        }
        var whereBuilder = new WhereBuilderImpl<T, R, ID>();
        return whereBuilder.build(((PredicateBuilderImpl<?, ?, ?>) predicate.apply(whereBuilder)).getTemplates());
    }
}
