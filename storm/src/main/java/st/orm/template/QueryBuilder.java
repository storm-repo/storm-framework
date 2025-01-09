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
package st.orm.template;

import jakarta.annotation.Nonnull;
import st.orm.NoResultException;
import st.orm.NonUniqueResultException;
import st.orm.PersistenceException;
import st.orm.PreparedQuery;
import st.orm.Query;
import st.orm.ResultCallback;
import st.orm.template.impl.Elements;
import st.orm.template.impl.Elements.ObjectExpression;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.StringTemplate.RAW;
import static java.util.Spliterators.spliteratorUnknownSize;
import static st.orm.template.Operator.EQUALS;
import static st.orm.template.Operator.IN;

/**
 * A query builder that constructs a query from a template.
 *
 * @param <T> the type of the table being queried.
 * @param <R> the type of the result.
 * @param <ID> the type of the primary key.
 */
public interface QueryBuilder<T extends Record, R, ID> {

    /**
     * Returns a typed query builder for the specified primary key type.
     *
     * @param pkType the primary key type.
     * @return the typed query builder.
     * @param <X> the type of the primary key.
     * @throws PersistenceException if the pk type is not valid.
     * @since 1.2
     */
    <X> QueryBuilder<T, R, X> typed(@Nonnull Class<X> pkType);

    /**
     * Marks the current query as a distinct query.
     *
     * @return the query builder.
     */
    QueryBuilder<T, R, ID> distinct();

    /**
     * A builder for constructing join clause of the query.
     *
     * @param <T> the type of the table being queried.
     * @param <R> the type of the result.
     * @param <ID> the type of the primary key.
     */
    interface TypedJoinBuilder<T extends Record, R, ID> extends JoinBuilder<T, R, ID> {

        /**
         * Specifies the relation to join on.
         * 
         * @param relation the relation to join on.
         * @return the query builder.
         */
        QueryBuilder<T, R, ID> on(@Nonnull Class<? extends Record> relation);
    }

    /**
     * A builder for constructing join clause of the query using custom join conditions.
     *
     * @param <T> the type of the table being queried.
     * @param <R> the type of the result.
     * @param <ID> the type of the primary key.
     */
    interface JoinBuilder<T extends Record, R, ID> {

        /**
         * Specifies the join condition using a custom expression.
         * 
         * @param template the condition to join on.
         * @return the query builder.
         */
        QueryBuilder<T, R, ID> on(@Nonnull StringTemplate template);
    }

    /**
     * Adds a cross join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    QueryBuilder<T, R, ID> crossJoin(@Nonnull Class<? extends Record> relation);

    /**
     * Adds an inner join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    TypedJoinBuilder<T, R, ID> innerJoin(@Nonnull Class<? extends Record> relation);

    /**
     * Adds a left join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    TypedJoinBuilder<T, R, ID> leftJoin(@Nonnull Class<? extends Record> relation);

    /**
     * Adds a right join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    TypedJoinBuilder<T, R, ID> rightJoin(@Nonnull Class<? extends Record> relation);

    /**
     * Adds a join of the specified type to the query.
     *
     * @param type the type of the join (e.g., INNER, LEFT, RIGHT).
     * @param relation the relation to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    TypedJoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull Class<? extends Record> relation, @Nonnull String alias);

    /**
     * Adds a cross join to the query.
     *
     * @param template the condition to join.
     * @return the query builder.
     */
    QueryBuilder<T, R, ID> crossJoin(@Nonnull StringTemplate template);

    /**
     * Adds an inner join to the query.
     *
     * @param template the condition to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    JoinBuilder<T, R, ID> innerJoin(@Nonnull StringTemplate template, @Nonnull String alias);

    /**
     * Adds a left join to the query.
     *
     * @param template the template to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    JoinBuilder<T, R, ID> leftJoin(@Nonnull StringTemplate template, @Nonnull String alias);

    /**
     * Adds a right join to the query.
     *
     * @param template the template to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    JoinBuilder<T, R, ID> rightJoin(@Nonnull StringTemplate template, @Nonnull String alias);

    /**
     * Adds a join of the specified type to the query using a template.
     *
     * @param type the join type.
     * @param template the template to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    JoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull StringTemplate template, @Nonnull String alias);

    /**
     * Adds a join of the specified type to the query using a subquery.
     *
     * @param type the join type.
     * @param subquery the subquery to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    JoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull QueryBuilder<?, ?, ?> subquery, @Nonnull String alias);

    /**
     * A builder for constructing the WHERE clause of the query.
     *
     * @param <T> the type of the table being queried.
     * @param <R> the type of the result.
     * @param <ID> the type of the primary key.
     */
    interface WhereBuilder<T extends Record, R, ID> extends SubqueryTemplate {

        /**
         * A predicate that always evaluates to true.
         */
        default PredicateBuilder<T, R, ID> TRUE() {
            return expression(RAW."TRUE");
        }

        /**
         * A predicate that always evaluates to false.
         */
        default PredicateBuilder<T, R, ID> FALSE() {
            return expression(RAW."FALSE");
        }

        /**
         * Appends a custom expression to the WHERE clause.
         *
         * @param template the expression to add.
         * @return the predicate builder.
         */
        PredicateBuilder<T, R, ID> expression(@Nonnull StringTemplate template);

        /**
         * Adds an <code>EXISTS</code> condition to the WHERE clause using the specified subquery.
         *
         * <p>This method appends an <code>EXISTS</code> clause to the current query's WHERE condition.
         * It checks whether the provided subquery returns any rows, allowing you to filter results based
         * on the existence of related data. This is particularly useful for constructing queries that need
         * to verify the presence of certain records in a related table or subquery.
         *
         * @param subquery the subquery to check for existence.
         * @return the updated {@link PredicateBuilder} with the EXISTS condition applied.
         */
        PredicateBuilder<T, R, ID> exists(@Nonnull QueryBuilder<?, ?, ?> subquery);

        /**
         * Adds an <code>NOT EXISTS</code> condition to the WHERE clause using the specified subquery.
         *
         * <p>This method appends an <code>NOT EXISTS</code> clause to the current query's WHERE condition.
         * It checks whether the provided subquery returns any rows, allowing you to filter results based
         * on the existence of related data. This is particularly useful for constructing queries that need
         * to verify the absence of certain records in a related table or subquery.
         *
         * @param subquery the subquery to check for existence.
         * @return the updated {@link PredicateBuilder} with the NOT EXISTS condition applied.
         */
        PredicateBuilder<T, R, ID> notExists(@Nonnull QueryBuilder<?, ?, ?> subquery);

        /**
         * Adds a condition to the WHERE clause that matches the specified primary key of the table.
         *
         * @param id the id to match.
         * @return the predicate builder.
         */
        PredicateBuilder<T, R, ID> filter(@Nonnull ID id);

        /**
         * Adds a condition to the WHERE clause that matches the specified record.
         *
         * @param record the record to match.
         * @return the predicate builder.
         */
        PredicateBuilder<T, R, ID> filter(@Nonnull T record);

        /**
         * Adds a condition to the WHERE clause that matches the specified record. The record can represent any of the
         * related tables in the table graph or manually added joins.
         *
         * @param record the record to match.
         * @return the predicate builder.
         * @since 1.2
         */
        PredicateBuilder<T, R, ID> filterAny(@Nonnull Record record);

        /**
         * Adds a condition to the WHERE clause that matches the specified primary keys of the table.
         *
         * @param it the ids to match.
         * @return the predicate builder.
         * @since 1.2
         */
        PredicateBuilder<T, R, ID> filterIds(@Nonnull Iterable<? extends ID> it);

        /**
         * Adds a condition to the WHERE clause that matches the specified records.
         *
         * @param it the records to match.
         * @return the predicate builder.
         */
        PredicateBuilder<T, R, ID> filter(@Nonnull Iterable<? extends T> it);

        /**
         * Adds a condition to the WHERE clause that matches the specified records. The record can represent any of the
         * related tables in the table graph or manually added joins.
         *
         * @param it the records to match.
         * @return the query builder.
         */
        PredicateBuilder<T, R, ID> filterAny(@Nonnull Iterable<? extends Record> it);

        /**
         * Adds a condition to the WHERE clause that matches the specified record. The record can represent any of
         * the related tables in the table graph or manually added joins.
         *
         * @param record the records to match.
         * @return the predicate builder.
         */
        default <V extends Record> PredicateBuilder<T, R, ID> filter(@Nonnull Metamodel<T, V> path, V record) {
            //noinspection unchecked
            return filter(path, EQUALS, record);
        }

        /**
         * Adds a condition to the WHERE clause that matches the specified record. The record can represent any of
         * the related tables in the table graph or manually added joins.
         *
         * @param record the records to match.
         * @return the predicate builder.
         */
        default <V extends Record> PredicateBuilder<T, R, ID> filterAny(@Nonnull Metamodel<?, V> path, V record) {
            //noinspection unchecked
            return filterAny(path, EQUALS, record);
        }

        /**
         * Adds a condition to the WHERE clause that matches the specified records. The records can represent any of
         * the related tables in the table graph.
         *
         * @param it the records to match.
         * @return the predicate builder.
         */
        default <V extends Record> PredicateBuilder<T, R, ID> filter(@Nonnull Metamodel<T, V> path, Iterable<V> it) {
            return filter(path, IN, it);
        }

        /**
         * Adds a condition to the WHERE clause that matches the specified records. The records can represent any of
         * the related tables in the table graph or manually added joins.
         *
         * @param it the records to match.
         * @return the predicate builder.
         */
        default <V extends Record> PredicateBuilder<T, R, ID> filterAny(@Nonnull Metamodel<?, V> path, Iterable<V> it) {
            return filterAny(path, IN, it);
        }

        /**
         * Adds a condition to the WHERE clause that matches the specified objects at the specified path in the table
         * graph.
         *
         * @param path the path to the object in the table graph.
         * @param operator the operator to use for the comparison.
         * @param it the objects to match, which can be primary keys, records representing the table, or fields in the
         *          table graph.
         * @return the query builder.
         * @param <V> the type of the object that the metamodel represents.
         * @since 1.2
         */
        <V> PredicateBuilder<T, R, ID> filter(@Nonnull Metamodel<T, V> path,
                                              @Nonnull Operator operator,
                                              @Nonnull Iterable<? extends V> it);

        /**
         * Adds a condition to the WHERE clause that matches the specified objects at the specified path in the table
         * graph.
         *
         * @param path the path to the object in the table graph.
         * @param operator the operator to use for the comparison.
         * @param it the objects to match, which can be primary keys, records representing the table, or fields in the
         *          table graph.
         * @return the query builder.
         * @param <V> the type of the object that the metamodel represents.
         * @since 1.2
         */
        <V> PredicateBuilder<T, R, ID> filterAny(@Nonnull Metamodel<?, V> path,
                                                 @Nonnull Operator operator,
                                                 @Nonnull Iterable<? extends V> it);

        /**
         * Adds a condition to the WHERE clause that matches the specified objects at the specified path in the table
         * graph.
         *
         * @param path the path to the object in the table graph.
         * @param operator the operator to use for the comparison.
         * @param o the object(s) to match, which can be primary keys, records representing the table, or fields in the
         *          table graph.
         * @return the query builder.
         * @param <V> the type of the object that the metamodel represents.
         * @since 1.2
         */
        @SuppressWarnings("unchecked")
        <V> PredicateBuilder<T, R, ID> filter(@Nonnull Metamodel<T, V> path,
                                              @Nonnull Operator operator,
                                              @Nonnull V... o);

        /**
         * Adds a condition to the WHERE clause that matches the specified objects at the specified path in the table
         * graph.
         *
         * @param path the path to the object in the table graph.
         * @param operator the operator to use for the comparison.
         * @param o the object(s) to match, which can be primary keys, records representing the table, or fields in the
         *          table graph.
         * @return the query builder.
         * @param <V> the type of the object that the metamodel represents.
         * @since 1.2
         */
        @SuppressWarnings("unchecked")
        <V> PredicateBuilder<T, R, ID> filterAny(@Nonnull Metamodel<?, V> path,
                                                 @Nonnull Operator operator,
                                                 @Nonnull V... o);
    }

    /**
     * A builder for constructing the predicates of the WHERE clause of the query.
     *
     * @param <T> the type of the table being queried.
     * @param <R> the type of the result.
     * @param <ID> the type of the primary key.
     */
    interface PredicateBuilder<T extends Record, R, ID> {

        /**
         * Adds a predicate to the WHERE clause using an AND condition.
         *
         * <p>This method combines the specified predicate with existing predicates using an AND operation, ensuring
         * that all added conditions must be true.</p>
         *
         * @param predicate the predicate to add.
         * @return the predicate builder.
         */
        PredicateBuilder<T, R, ID> and(@Nonnull PredicateBuilder<T, R, ID> predicate);

        /**
         * Adds a predicate to the WHERE clause using an OR condition.
         *
         * <p>This method combines the specified predicate with existing predicates using an OR operation, allowing any
         * of the added conditions to be true.</p>
         *
         * @param predicate the predicate to add.
         * @return the predicate builder.
         */
        PredicateBuilder<T, R, ID> or(@Nonnull PredicateBuilder<T, R, ID> predicate);
    }

    /**
     * Adds a WHERE clause that matches the specified primary key of the table.
     *
     * @param id the id to match.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> where(@Nonnull ID id) {
        return where(predicate -> predicate.filter(id));
    }

    /**
     * Adds a WHERE clause that matches the specified record.
     *
     * @param record the record to match.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> where(@Nonnull T record) {
        return where(predicate -> predicate.filter(record));
    }

    /**
     * Adds a WHERE clause that matches the specified record. The record can represent any of the related tables in the
     * table graph or manually added joins.
     *
     * @param record the record to match.
     * @return the query builder.
     * @since 1.2
     */
    default QueryBuilder<T, R, ID> whereAny(@Nonnull Record record) {
        return where(predicate -> predicate.filterAny(record));
    }

    /**
     * Adds a WHERE clause that matches the specified primary keys of the table.
     *
     * @param it ids to match.
     * @return the query builder.
     * @since 1.2
     */
    default QueryBuilder<T, R, ID> whereIds(@Nonnull Iterable<? extends ID> it) {
        return where(predicate -> predicate.filterIds(it));
    }

    /**
     * Adds WHERE clause that matches the specified record. The record can represent any of the related tables in the
     * table graph.
     *
     * @param path the path to the object in the table graph.
     * @param record the records to match.
     * @return the predicate builder.
     */
    default <V extends Record> QueryBuilder<T, R, ID> where(@Nonnull Metamodel<T, V> path, V record) {
        //noinspection unchecked
        return where(path, EQUALS, record);
    }

    /**
     * Adds WHERE clause that matches the specified record. The record can represent any of the related tables in the
     * table graph or manually added joins.
     *
     * @param path the path to the object in the table graph.
     * @param record the records to match.
     * @return the predicate builder.
     */
    default <V extends Record> QueryBuilder<T, R, ID> whereAny(@Nonnull Metamodel<?, V> path, V record) {
        //noinspection unchecked
        return whereAny(path, EQUALS, record);
    }

    /**
     * Adds a WHERE clause that matches the specified records. The records can represent any of the related tables in
     * the table graph.
     *
     * @param path the path to the object in the table graph.
     * @param it the records to match.
     * @return the predicate builder.
     */
    default <V extends Record> QueryBuilder<T, R, ID> where(@Nonnull Metamodel<T, V> path, Iterable<V> it) {
        return where(path, IN, it);
    }

    /**
     * Adds a WHERE clause that matches the specified records. The records can represent any of the related tables in
     * the table graph or manually added joins.
     *
     * @param path the path to the object in the table graph.
     * @param it the records to match.
     * @return the predicate builder.
     */
    default <V extends Record> QueryBuilder<T, R, ID> whereAny(@Nonnull Metamodel<?, V> path, Iterable<V> it) {
        return whereAny(path, IN, it);
    }

    /**
     * Adds a WHERE clause that matches the specified records.
     *
     * @param it the records to match.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> where(@Nonnull Iterable<? extends T> it) {
        return where(predicate -> predicate.filter(it));
    }

    /**
     * Adds a WHERE clause that matches the specified records. The record can represent any of the related tables in the
     * table graph or manually added joins.
     *
     * @param it the records to match.
     * @return the query builder.
     * @since 1.2
     */
    default QueryBuilder<T, R, ID> whereAny(@Nonnull Iterable<? extends Record> it) {
        return where(predicate -> predicate.filterAny(it));
    }

    /**
     * Adds a WHERE clause that matches the specified objects at the specified path in the table graph.
     *
     * @param path the path to the object in the table graph.
     * @param operator the operator to use for the comparison.
     * @param it the objects to match, which can be primary keys, records representing the table, or fields in the table
     *           graph.
     * @return the query builder.
     * @param <V> the type of the object that the metamodel represents.
     * @since 1.2
     */
    default <V> QueryBuilder<T, R, ID> where(@Nonnull Metamodel<T, V> path,
                                             @Nonnull Operator operator,
                                             @Nonnull Iterable<? extends V> it) {
        return where(predicate -> predicate.filter(path, operator, it));
    }

    /**
     * Adds a WHERE clause that matches the specified objects at the specified path in the table graph.
     *
     * @param path the path to the object in the table graph.
     * @param operator the operator to use for the comparison.
     * @param it the objects to match, which can be primary keys, records representing the table, or fields in the
     *          table graph.
     * @return the query builder.
     * @param <V> the type of the object that the metamodel represents.
     * @since 1.2
     */
    default <V> QueryBuilder<T, R, ID> whereAny(@Nonnull Metamodel<?, V> path,
                                                @Nonnull Operator operator,
                                                @Nonnull Iterable<? extends V> it) {
        return where(predicate -> predicate.filterAny(path , operator, it));
    }

    /**
     * Adds a WHERE clause that matches the specified objects at the specified path in the table graph.
     *
     * @param path the path to the object in the table graph.
     * @param operator the operator to use for the comparison.
     * @param o the object(s) to match, which can be primary keys, records representing the table, or fields in the
     *          table graph.
     * @return the query builder.
     * @param <V> the type of the object that the metamodel represents.
     * @since 1.2
     */
    @SuppressWarnings("unchecked")
    default <V> QueryBuilder<T, R, ID> where(@Nonnull Metamodel<T, V> path,
                                             @Nonnull Operator operator,
                                             @Nonnull V... o) {
        return where(predicate -> predicate.filter(path, operator, o));
    }

    /**
     * Adds a WHERE clause that matches the specified objects at the specified path in the table graph. The metamodel
     * can refer to manually added joins.
     *
     * @param path the path to the object in the table graph.
     * @param operator the operator to use for the comparison.
     * @param o the object(s) to match, which can be primary keys, records representing the table, or fields in the
     *          table graph.
     * @return the query builder.
     * @param <V> the type of the object that the metamodel represents.
     * @since 1.2
     */
    @SuppressWarnings("unchecked")
    default <V> QueryBuilder<T, R, ID> whereAny(@Nonnull Metamodel<?, V> path,
                                                @Nonnull Operator operator,
                                                V... o) {
        return where(predicate -> predicate.filterAny(path, operator, o));
    }

    /**
     * Adds a WHERE clause to the query for the specified expression.
     *
     * @param template the expression.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> where(@Nonnull StringTemplate template) {
        return where(it -> it.expression(template));
    }

    /**
     * Adds a WHERE clause to the query using a {@link WhereBuilder}.
     *
     * @param predicate the predicate to add.
     * @return the query builder.
     */
    QueryBuilder<T, R, ID> where(@Nonnull Function<WhereBuilder<T, R, ID>, PredicateBuilder<?, ?, ?>> predicate);

    /**
     * Adds a GROUP BY clause to the query for field at the specified path in the table graph.
     *
     * @param path the path to group by.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> groupBy(@Nonnull Metamodel<T, ?> path) {
        return groupBy(RAW."\{path}");
    }

    /**
     * Adds a GROUP BY clause to the query for field at the specified path in the table graph. The metamodel can refer
     * to manually added joins.
     *
     * @param path the path to group by.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> groupBy(@Nonnull Metamodel<?, ?>... path) {
        if (path.length == 0) {
            throw new PersistenceException("At least one path must be provided for GROUP BY clause.");
        }
        List<StringTemplate> templates = Stream.of(path)
                .flatMap(metamodel -> Stream.of(RAW."\{metamodel}", RAW.", "))
                .toList();
        return groupBy(StringTemplate.combine(templates.subList(0, templates.size() - 1).toArray(new StringTemplate[0])));
    }

    /**
     * Adds a GROUP BY clause to the query using a string template.
     *
     * @param template the template to group by.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> groupBy(@Nonnull StringTemplate template) {
        return append(StringTemplate.combine(RAW."GROUP BY ", template));
    }

    /**
     * Adds a HAVING clause to the query using the specified expression.
     *
     * @param path the path to the object in the table graph.
     * @param operator the operator to use for the comparison.
     * @param o the object(s) to match, which can be primary keys, records representing the table, or fields in the
     *          table graph.
     * @return the query builder.
     */
    @SuppressWarnings("unchecked")
    default <V> QueryBuilder<T, R, ID> having(@Nonnull Metamodel<T, V> path,
                                              @Nonnull Operator operator,
                                              V... o) {
        return havingAny(path, operator, o);
    }

    /**
     * Adds a HAVING clause to the query using the specified expression.
     *
     * @param path the path to the object in the table graph.
     * @param operator the operator to use for the comparison.
     * @param o the object(s) to match, which can be primary keys, records representing the table, or fields in the
     *          table graph or manually added joins.
     * @return the query builder.
     */
    @SuppressWarnings("unchecked")
    default <V> QueryBuilder<T, R, ID> havingAny(@Nonnull Metamodel<?, V> path,
                                                 @Nonnull Operator operator,
                                                 V... o) {
        return having(RAW."\{new ObjectExpression(path, operator, o)}");
    }

    /**
     * Adds a HAVING clause to the query using the specified expression.
     *
     * @param template the expression to add.
     * @return the query builder.
     */
    default <V> QueryBuilder<T, R, ID> having(@Nonnull StringTemplate template) {
        return append(StringTemplate.combine(RAW."HAVING ", template));
    }

    /**
     * Adds an ORDER BY clause to the query for the field at the specified path in the table graph.
     *
     * @param path the path to order by.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> orderBy(@Nonnull Metamodel<T, ?> path) {
        return orderBy(RAW."\{path}");
    }

    /**
     * Adds an ORDER BY clause to the query for the field at the specified path in the table graph.
     *
     * @param path the path to order by.
     * @param ascending whether to order in ascending order.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> orderBy(@Nonnull Metamodel<T, ?> path, boolean ascending) {
        return orderBy(RAW."\{path} \{ascending ? "ASC" : "DESC"}");
    }

    /**
     * Adds an ORDER BY clause to the query for the field at the specified path in the table graph or manually added
     * joins.
     *
     * @param path the path to order by.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> orderBy(@Nonnull Metamodel<?, ?>... path) {
        if (path.length == 0) {
            throw new PersistenceException("At least one path must be provided for ORDER BY clause.");
        }
        List<StringTemplate> templates = Stream.of(path)
                .flatMap(metamodel -> Stream.of(RAW."\{metamodel}", RAW.", "))
                .toList();
        return orderBy(StringTemplate.combine(templates.subList(0, templates.size() - 1).toArray(new StringTemplate[0])));
    }

    /**
     * Adds an ORDER BY clause to the query using a string template.
     *
     * @param template the template to order by.
     * @return the query builder.
     */
    default QueryBuilder<T, R, ID> orderBy(@Nonnull StringTemplate template) {
        return append(StringTemplate.combine(RAW."ORDER BY ", template));
    }

    /**
     * Append the query with a string template.
     *
     * @param template the string template to append.
     * @return the query builder.
     */
    QueryBuilder<T, R, ID> append(@Nonnull StringTemplate template);

    /**
     * Builds the query based on the current state of the query builder.
     *
     * @return the constructed query.
     */
    Query build();

    /**
     * Prepares the query for execution.
     *
     * <p>Unlike regular queries, which are constructed lazily, prepared queries are constructed eagerly.
     * Prepared queries allow the use of bind variables and enable reading generated keys after row insertion.</p>
     *
     * <p>Note that the prepared query must be closed after usage to prevent resource leaks. As the prepared query is
     * AutoCloseable, it is recommended to use it within a try-with-resources block.</p>
     *
     * @return the prepared query.
     * @throws PersistenceException if the query preparation fails.
     */
    default PreparedQuery prepare() {
        return build().prepare();
    }

    //
    // Execution methods.
    //

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
    Stream<R> getResultStream();

    /**
     * Executes the query and returns a stream of using the specified callback. This method retrieves the records and
     * applies the provided callback to process them, returning the result produced by the callback.
     *
     * <p>This method ensures efficient handling of large data sets by loading entities only as needed.
     * It also manages lifecycle of the callback stream, automatically closing the stream after processing to prevent
     * resource leaks.</p>
     *
     * @param callback a {@link ResultCallback} defining how to process the stream of records and produce a result.
     * @param <X> the type of result produced by the callback after processing the entities.
     * @return the result produced by the callback's processing of the record stream.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     * connectivity.
     */
    default <X> X getResult(@Nonnull ResultCallback<R, X> callback) {
        try (Stream<R> stream = getResultStream()) {
            return callback.process(stream);
        }
    }

    /**
     * Returns the number of results of this query.
     *
     * @return the total number of results of this query as a long value.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    default long getResultCount() {
        try (var stream = getResultStream()) {
            return stream.count();
        }
    }

    /**
     * Executes the query and returns a list of results.
     *
     * @return the list of results.
     * @throws PersistenceException if the query fails.
     */
    default List<R> getResultList() {
        try (var stream = getResultStream()) {
            return stream.toList();
        }
    }

    /**
     * Executes the query and returns a single result.
     *
     * @return the single result.
     * @throws NoResultException if there is no result.
     * @throws NonUniqueResultException if more than one result.
     * @throws PersistenceException if the query fails.
     */
    default R getSingleResult() {
        try (var stream = getResultStream()) {
            return stream
                    .reduce((_, _) -> {
                        throw new NonUniqueResultException("Expected single result, but found more than one.");
                    }).orElseThrow(() -> new NoResultException("Expected single result, but found none."));
        }
    }

    /**
     * Executes the query and returns an optional result.
     *
     * @return the optional result.
     * @throws NonUniqueResultException if more than one result.
     * @throws PersistenceException if the query fails.
     */
    default Optional<R> getOptionalResult() {
        try (var stream = getResultStream()) {
            return stream.reduce((_, _) -> {
                throw new NonUniqueResultException("Expected single result, but found more than one.");
            });
        }
    }

    /**
     * Execute a DELETE statement.
     *
     * @return the number of rows impacted as result of the statement.
     * @throws PersistenceException if the statement fails.
     */
    default int executeUpdate() {
        return build().executeUpdate();
    }

    /**
     * Performs the function in multiple batches, each containing up to {@code batchSize} elements from the stream.
     *
     * @param stream the stream to batch.
     * @param batchSize the maximum number of elements to include in each batch.
     * @param function the function to apply to each batch.
     * @return a stream of results from each batch.
     * @param <X> the type of elements in the stream.
     * @param <Y> the type of elements in the result stream.
     */
    static <X, Y> Stream<Y> slice(@Nonnull Stream<X> stream,
                                  int batchSize,
                                  @Nonnull Function<List<X>, Stream<Y>> function) {
        return slice(stream, batchSize)
                .flatMap(function); // Note that the flatMap operation closes the stream passed to it.
    }

    /**
     * Generates a stream of slices, each containing a subset of elements from the original stream up to a specified
     * size. This method is designed to facilitate batch processing of large streams by dividing the stream into
     * smaller manageable slices, which can be processed independently.
     *
     * <p>If the specified size is equal to {@code Integer.MAX_VALUE}, this method will return a single slice containing
     * the original stream, effectively bypassing the slicing mechanism. This is useful for operations that can handle
     * all elements at once without the need for batching.</p>
     *
     * @param <X> the type of elements in the stream.
     * @param stream the original stream of elements to be sliced.
     * @param size the maximum number of elements to include in each slice. If {@code size} is
     * {@code Integer.MAX_VALUE}, only one slice will be returned.
     * @return a stream of slices, where each slice contains up to {@code size} elements from the original stream.
     */
    static <X> Stream<List<X>> slice(@Nonnull Stream<X> stream, int size) {
        if (size == MAX_VALUE) {
            return Stream.of(stream.toList());
        }
        // We're lifting the resource closing logic from the input stream to the output stream.
        final Iterator<X> iterator = stream.iterator();
        var it = new Iterator<List<X>>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public List<X> next() {
                Iterator<X> sliceIterator = new Iterator<>() {
                    private int count = 0;

                    @Override
                    public boolean hasNext() {
                        return count < size && iterator.hasNext();
                    }

                    @Override
                    public X next() {
                        if (count >= size) {
                            throw new IllegalStateException("Size exceeded.");
                        }
                        count++;
                        return iterator.next();
                    }
                };
                return StreamSupport.stream(spliteratorUnknownSize(sliceIterator, 0), false).toList();
            }
        };
        return StreamSupport.stream(spliteratorUnknownSize(it, 0), false)
                .onClose(stream::close);
    }
}
