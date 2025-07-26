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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.JoinType;
import st.orm.Metamodel;
import st.orm.Operator;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.core.template.JoinBuilder;
import st.orm.core.template.Model;
import st.orm.core.template.PredicateBuilder;
import st.orm.core.template.QueryBuilder;
import st.orm.core.template.QueryTemplate;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.TemplateString;
import st.orm.core.template.TypedJoinBuilder;
import st.orm.core.template.WhereBuilder;
import st.orm.core.template.impl.Elements.ObjectExpression;
import st.orm.core.template.impl.Elements.TableSource;
import st.orm.core.template.impl.Elements.TableTarget;
import st.orm.core.template.impl.Elements.TemplateExpression;
import st.orm.core.template.impl.Elements.TemplateSource;
import st.orm.core.template.impl.Elements.TemplateTarget;
import st.orm.core.template.impl.Elements.Where;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static st.orm.JoinType.cross;
import static st.orm.JoinType.inner;
import static st.orm.JoinType.left;
import static st.orm.JoinType.right;
import static st.orm.Operator.EQUALS;
import static st.orm.Operator.IN;
import static st.orm.core.template.TemplateString.combine;
import static st.orm.core.template.TemplateString.wrap;

/**
 * Abstract query builder implementation.
 *
 * @param <T> the type of the table being queried.
 * @param <R> the type of the result.
 * @param <ID> the type of the primary key.
 */
abstract class QueryBuilderImpl<T extends Record, R, ID> extends QueryBuilder<T, R, ID> implements Subqueryable {

    protected final QueryTemplate queryTemplate;
    protected final Class<T> fromType;
    protected final List<Join> join;
    protected final List<Where> where;
    protected final List<TemplateString> templates;
    protected final Supplier<Model<T, ID>> modelSupplier;

    protected QueryBuilderImpl(@Nonnull QueryTemplate queryTemplate,
                               @Nonnull Class<T> fromType,
                               @Nonnull List<Join> join,
                               @Nonnull List<Where> where,
                               @Nonnull List<TemplateString> templates,
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
            throw new PersistenceException("Primary key type mismatch: expected %s, got %s.".formatted(model.primaryKeyType().getName(), pkType.getName()));
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
                                             @Nonnull List<TemplateString> templates);

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
    public QueryBuilder<T, R, ID> append(@Nonnull TemplateString template) {
        List<TemplateString> copy = new ArrayList<>(templates);
        if (!template.fragments().isEmpty()) {
            template = combine(TemplateString.of("\n"), template);
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
        return join(cross(), relation, "").on(TemplateString.EMPTY);
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
    public JoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull TemplateString template, @Nonnull String alias) {
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
        return onTemplate -> addJoin(new Join(new TemplateSource(wrap(subquery)), alias, new TemplateTarget(onTemplate), type, false));
    }

    /**
     * Adds a cross join to the query.
     *
     * @param template the template to join.
     * @return the query builder.
     */
    @Override
    public QueryBuilder<T, R, ID> crossJoin(@Nonnull TemplateString template) {
        return join(cross(), template, "").on(TemplateString.EMPTY);
    }

    /**
     * Adds an inner join to the query.
     *
     * @param template the template to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    @Override
    public JoinBuilder<T, R, ID> innerJoin(@Nonnull TemplateString template, @Nonnull String alias) {
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
    public JoinBuilder<T, R, ID> leftJoin(@Nonnull TemplateString template, @Nonnull String alias) {
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
    public JoinBuilder<T, R, ID> rightJoin(@Nonnull TemplateString template, @Nonnull String alias) {
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
            public QueryBuilder<T, R, ID> on(@Nonnull TemplateString onTemplate) {
                return addJoin(new Join(new TableSource(relation), alias, new TemplateTarget(onTemplate), type, false));
            }
        };
    }

    // Define the raw templates
    private static final TemplateString RAW_AND = TemplateString.of(" AND ");
    private static final TemplateString RAW_OR = TemplateString.of(" OR ");
    private static final TemplateString RAW_OPEN = TemplateString.of("(");
    private static final TemplateString RAW_CLOSE = TemplateString.of(")");

    static class PredicateBuilderImpl<TX extends Record, RX, IDX> implements PredicateBuilder<TX, RX, IDX> {
        private final List<TemplateString> templates = new ArrayList<>();
        private final boolean safe;

        PredicateBuilderImpl(@Nonnull TemplateString template) {
            this(template, true);
        }

        PredicateBuilderImpl(@Nonnull TemplateString template, boolean safe) {
            templates.add(requireNonNull(template, "template"));
            this.safe = safe;
        }

        @Override
        public PredicateBuilder<TX, RX, IDX> and(@Nonnull PredicateBuilder<TX, ?, ?> predicate) {
            add(RAW_AND, predicate);
            return this;
        }

        @Override
        public <TY extends Record, RY, IDY> PredicateBuilder<TY, RY, IDY> andAny(@Nonnull PredicateBuilder<TY, RY, IDY> predicate) {
            add(RAW_AND, predicate);
            //noinspection unchecked
            return (PredicateBuilder<TY, RY, IDY>) this;
        }

        @Override
        public PredicateBuilder<TX, RX, IDX> and(@Nonnull TemplateString template) {
            add(RAW_AND, combine(RAW_OPEN, template, RAW_CLOSE));   // Always wrap a template in parentheses as we don't know if it's a single expression or a complex one.
            return this;
        }

        @Override
        public PredicateBuilder<TX, RX, IDX> or(@Nonnull PredicateBuilder<TX, ?, ?> predicate) {
            add(RAW_OR, predicate);
            return this;
        }

        @Override
        public <TY extends Record, RY, IDY> PredicateBuilder<TY, RY, IDY> orAny(@Nonnull PredicateBuilder<TY, RY, IDY> predicate) {
            add(RAW_OR, predicate);
            //noinspection unchecked
            return (PredicateBuilder<TY, RY, IDY>) this;
        }

        @Override
        public PredicateBuilder<TX, RX, IDX> or(@Nonnull TemplateString template) {
            add(RAW_OR, combine(RAW_OPEN, template, RAW_CLOSE));
            return this;
        }

        private void add(@Nonnull TemplateString operator, @Nonnull PredicateBuilder<?, ?, ?> predicate) {
            var list = ((PredicateBuilderImpl<?, ?, ?>) predicate).templates;
            assert !list.isEmpty();
            if (list.size() > 1) {
                var wrap = new ArrayList<TemplateString>();
                wrap.add(RAW_OPEN);
                wrap.addAll(list);
                wrap.add(RAW_CLOSE);
                add(operator, combine(wrap));
            } else {
                add(operator, list.getFirst());
            }
        }

        private void add(@Nonnull TemplateString operator, @Nonnull TemplateString template) {
            if (templates.size() == 1 && !safe) {
                // Wrap the first template in parentheses if it's the only one.
                templates.addFirst(RAW_OPEN);
                templates.addLast(RAW_CLOSE);
            }
            templates.add(operator);
            templates.add(template);
        }

        private List<TemplateString> getTemplates() {
            return templates;
        }
    }

    static class WhereBuilderImpl<TX extends Record, RX, IDX> extends WhereBuilder<TX, RX, IDX> {
        private final QueryBuilderImpl<TX, RX, IDX> queryBuilder;

        WhereBuilderImpl(@Nonnull QueryBuilderImpl<TX, RX, IDX> queryBuilder) {
            this.queryBuilder = queryBuilder;
        }

        @Override
        public <F extends Record> QueryBuilder<F, ?, ?> subquery(@Nonnull Class<F> fromType, @Nonnull TemplateString template) {
            return queryBuilder.queryTemplate.subquery(fromType, template);
        }

        @Override
        public PredicateBuilder<TX, RX, IDX> exists(@Nonnull QueryBuilder<?, ?, ?> subquery) {
            return new PredicateBuilderImpl<>(TemplateString.raw("EXISTS (\0)", subquery));
        }

        @Override
        public PredicateBuilder<TX, RX, IDX> notExists(@Nonnull QueryBuilder<?, ?, ?> subquery) {
            return new PredicateBuilderImpl<>(TemplateString.raw("NOT EXISTS (\0)", subquery));
        }

        @Override
        public PredicateBuilder<TX, RX, IDX> whereId(@Nonnull IDX id) {
            return new PredicateBuilderImpl<>(wrap(new ObjectExpression(EQUALS, id)));
        }

        @Override
        public PredicateBuilder<TX, RX, IDX> whereRef(@Nonnull Ref<TX> ref) {
            return new PredicateBuilderImpl<>(wrap(new ObjectExpression(EQUALS, ref)));
        }

        @Override
        public PredicateBuilder<TX, RX, IDX> whereAnyRef(@Nonnull Ref<? extends Record> ref) {
            return new PredicateBuilderImpl<>(wrap(new ObjectExpression(EQUALS, ref)));
        }

        @Override
        public PredicateBuilder<TX, RX, IDX> where(@Nonnull TX record) {
            return new PredicateBuilderImpl<>(wrap(new ObjectExpression(EQUALS, record)));
        }

        @Override
        public PredicateBuilder<TX, RX, IDX> whereAny(@Nonnull Record record) {
            return new PredicateBuilderImpl<>(wrap(new ObjectExpression(EQUALS, record)));
        }

        @Override
        public PredicateBuilder<TX, RX, IDX> whereId(@Nonnull Iterable<? extends IDX> it) {
            return new PredicateBuilderImpl<>(wrap(new ObjectExpression(IN, it)));
        }

        @Override
        public PredicateBuilder<TX, RX, IDX> whereRef(@Nonnull Iterable<? extends Ref<TX>> it) {
            return new PredicateBuilderImpl<>(wrap(new ObjectExpression(IN, it)));
        }

        @Override
        public PredicateBuilder<TX, RX, IDX> whereAnyRef(@Nonnull Iterable<? extends Ref<? extends Record>> it) {
            return new PredicateBuilderImpl<>(wrap(new ObjectExpression(IN, it)));
        }

        @Override
        public PredicateBuilder<TX, RX, IDX> where(@Nonnull Iterable<? extends TX> it) {
            return new PredicateBuilderImpl<>(wrap(new ObjectExpression(IN, it)));
        }

        @Override
        public PredicateBuilder<TX, RX, IDX> whereAny(@Nonnull Iterable<? extends Record> it) {
            return new PredicateBuilderImpl<>(wrap(new ObjectExpression(IN, it)));
        }

        @Override
        public <V extends Record> PredicateBuilder<TX, RX, IDX> where(@Nonnull Metamodel<TX, V> path, @Nonnull Ref<V> ref) {
            return new PredicateBuilderImpl<>(wrap(new ObjectExpression(path, EQUALS, ref)));
        }

        @Override
        public <V extends Record> PredicateBuilder<TX, RX, IDX> whereAny(@Nonnull Metamodel<?, V> path, @Nonnull Ref<V> ref) {
            return new PredicateBuilderImpl<>(wrap(new ObjectExpression(path, EQUALS, ref)));
        }

        @Override
        public <V extends Record> PredicateBuilder<TX, RX, IDX> whereRef(@Nonnull Metamodel<TX, V> path, @Nonnull Iterable<? extends Ref<V>> it) {
            return new PredicateBuilderImpl<>(wrap(new ObjectExpression(path, IN, it)));
        }

        @Override
        public <V extends Record> PredicateBuilder<TX, RX, IDX> whereAnyRef(@Nonnull Metamodel<?, V> path, @Nonnull Iterable<? extends Ref<V>> it) {
            return new PredicateBuilderImpl<>(wrap(new ObjectExpression(path, IN, it)));
        }

        @Override
        public <V> PredicateBuilder<TX, RX, IDX> where(@Nonnull Metamodel<TX, V> path, @Nonnull Operator operator, @Nonnull Iterable<? extends V> it) {
            return new PredicateBuilderImpl<>(wrap(new ObjectExpression(path, operator, it)));
        }

        @Override
        public <V> PredicateBuilder<TX, RX, IDX> whereAny(@Nonnull Metamodel<?, V> path, @Nonnull Operator operator, @Nonnull Iterable<? extends V> it) {
            return new PredicateBuilderImpl<>(wrap(new ObjectExpression(path, operator, it)));
        }

        @Override
        public PredicateBuilder<TX, RX, IDX> where(@Nonnull TemplateString template) {
            return new PredicateBuilderImpl<>(template, false);
        }

        @Override
        protected <V> PredicateBuilder<TX, RX, IDX> whereImpl(@Nonnull Metamodel<?, V> path, @Nonnull Operator operator, @Nonnull V[] o) {
            try {
                try {
                    return PredicateBuilderFactory.createWithId(path, operator, List.of(o));
                } catch (NullPointerException e) {
                    throw new SqlTemplateException("Null value not allowed.");
                }
            } catch (SqlTemplateException e) {
                throw new PersistenceException(e);
            }
        }

        private QueryBuilder<TX, RX, IDX> build(List<TemplateString> templates) {
            return queryBuilder.addWhere(new Where(new TemplateExpression(combine(templates)), null));
        }
    }

    /**
     * Adds a WHERE clause to the query using a {@link WhereBuilder}.
     *
     * @param predicate the predicate to add.
     * @return the query builder.
     */
    @Override
    public QueryBuilder<T, R, ID> where(@Nonnull Function<WhereBuilder<T, R, ID>, PredicateBuilder<T, ?, ?>> predicate) {
        requireNonNull(predicate, "predicate");
        var whereBuilder = new WhereBuilderImpl<>(this);
        return whereBuilder.build(((PredicateBuilderImpl<T, ?, ?>) predicate.apply(whereBuilder)).getTemplates());
    }

    /**
     * Adds a WHERE clause to the query using a {@link WhereBuilder}.
     *
     * @param predicate the predicate to add.
     * @return the query builder.
     */
    @Override
    public QueryBuilder<T, R, ID> whereAny(@Nonnull Function<WhereBuilder<T, R, ID>, PredicateBuilder<?, ?, ?>> predicate) {
        requireNonNull(predicate, "predicate");
        var whereBuilder = new WhereBuilderImpl<>(this);
        return whereBuilder.build(((PredicateBuilderImpl<?, ?, ?>) predicate.apply(whereBuilder)).getTemplates());
    }
}
