package st.orm.kotlin.template;

import jakarta.annotation.Nonnull;
import kotlin.reflect.KClass;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import st.orm.NoResultException;
import st.orm.NonUniqueResultException;
import st.orm.PersistenceException;
import st.orm.kotlin.KPreparedQuery;
import st.orm.kotlin.KQuery;
import st.orm.kotlin.KResultCallback;
import st.orm.template.JoinType;
import st.orm.template.Metamodel;
import st.orm.template.Operator;
import st.orm.template.QueryBuilder;
import st.orm.template.TemplateFunction;
import st.orm.template.impl.Elements.ObjectExpression;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.StringTemplate.RAW;
import static st.orm.template.Operator.EQUALS;
import static st.orm.template.Operator.IN;

public abstract class KQueryBuilder<T extends Record, R, ID> {

    /**
     * Returns a typed query builder for the specified primary key type.
     *
     * @param pkType the primary key type.
     * @return the typed query builder.
     * @param <X> the type of the primary key.
     * @throws PersistenceException if the pk type is not valid.
     * @since 1.2
     */
    public abstract <X> KQueryBuilder<T, R, X> typed(@Nonnull KClass<X> pkType);

    /**
     * Marks the current query as a distinct query.
     *
     * @return the query builder.
     */
    public abstract KQueryBuilder<T, R, ID> distinct();

    /**
     * A builder for constructing join clause of the query.
     *
     * @param <T> the type of the table being queried.
     * @param <R> the type of the result.
     * @param <ID> the type of the primary key.
     */
    public interface KTypedJoinBuilder<T extends Record, R, ID> extends KJoinBuilder<T, R, ID> {

        /**
         * Specifies the relation to join on.
         *
         * @param relation the relation to join on.
         * @return the query builder.
         */
        KQueryBuilder<T, R, ID> on(@Nonnull KClass<? extends Record> relation);
    }

    /**
     * A builder for constructing join clause of the query using custom join conditions.
     *
     * @param <T> the type of the table being queried.
     * @param <R> the type of the result.
     * @param <ID> the type of the primary key.
     */
    public interface KJoinBuilder<T extends Record, R, ID> {

        /**
         * Specifies the join condition using a custom expression.
         *
         * @param template the condition to join on.
         * @return the query builder.
         */
        KQueryBuilder<T, R, ID> on(@Nonnull StringTemplate template);

        /**
         * Specifies the join condition using a custom expression.
         *
         * @param function used to define the condition to join on.
         * @return the query builder.
         */
        default KQueryBuilder<T, R, ID> on(@Nonnull TemplateFunction function) {
            return on(TemplateFunction.template(function));
        }
    }

    /**
     * Adds a cross join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    public abstract KQueryBuilder<T, R, ID> crossJoin(@Nonnull KClass<? extends Record> relation);

    /**
     * Adds an inner join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    public abstract KTypedJoinBuilder<T, R, ID> innerJoin(@Nonnull KClass<? extends Record> relation);

    /**
     * Adds a left join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    public abstract KTypedJoinBuilder<T, R, ID> leftJoin(@Nonnull KClass<? extends Record> relation);

    /**
     * Adds a right join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    public abstract KTypedJoinBuilder<T, R, ID> rightJoin(@Nonnull KClass<? extends Record> relation);

    /**
     * Adds a join of the specified type to the query.
     *
     * @param type the type of the join (e.g., INNER, LEFT, RIGHT).
     * @param relation the relation to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    public abstract KTypedJoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull KClass<? extends Record> relation, @Nonnull String alias);

    /**
     * Adds a cross join to the query.
     *
     * @param template the condition to join.
     * @return the query builder.
     */
    public abstract KQueryBuilder<T, R, ID> crossJoin(@Nonnull StringTemplate template);

    /**
     * Adds a cross join to the query.
     *
     * @param function used to define the condition to join.
     * @return the query builder.
     */
    public final KQueryBuilder<T, R, ID> crossJoin(@Nonnull TemplateFunction function) {
        return crossJoin(TemplateFunction.template(function));
    }

    /**
     * Adds an inner join to the query.
     *
     * @param template the condition to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    public abstract KJoinBuilder<T, R, ID> innerJoin(@Nonnull StringTemplate template, @Nonnull String alias);

    /**
     * Adds an inner join to the query.
     *
     * @param function used to define the condition to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    public final KJoinBuilder<T, R, ID> innerJoin(@Nonnull TemplateFunction function, @Nonnull String alias) {
        return innerJoin(TemplateFunction.template(function), alias);
    }

    /**
     * Adds a left join to the query.
     *
     * @param template the condition to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    public abstract KJoinBuilder<T, R, ID> leftJoin(@Nonnull StringTemplate template, @Nonnull String alias);

    /**
     * Adds a left join to the query.
     *
     * @param function used to define the condition to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    public final KJoinBuilder<T, R, ID> leftJoin(@Nonnull TemplateFunction function, @Nonnull String alias) {
        return leftJoin(TemplateFunction.template(function), alias);
    }

    /**
     * Adds a right join to the query.
     *
     * @param template the condition to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    public abstract KJoinBuilder<T, R, ID> rightJoin(@Nonnull StringTemplate template, @Nonnull String alias);

    /**
     * Adds a right join to the query.
     *
     * @param function used to define the condition to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    public final KJoinBuilder<T, R, ID> rightJoin(@Nonnull TemplateFunction function, @Nonnull String alias) {
        return rightJoin(TemplateFunction.template(function), alias);
    }

    /**
     * Adds a join of the specified type to the query using a template.
     *
     * @param type the join type.
     * @param template the template to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    public abstract KJoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull StringTemplate template, @Nonnull String alias);

    /**
     * Adds a join of the specified type to the query using a subquery.
     *
     * @param type the join type.
     * @param subquery the subquery to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    public abstract KJoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull KQueryBuilder<?, ?, ?> subquery, @Nonnull String alias);

    /**
     * Adds a join of the specified type to the query.
     *
     * @param type the join type.
     * @param function the function used to define the condition to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */  
    public final KJoinBuilder<T, R, ID> join(@Nonnull JoinType type, @Nonnull TemplateFunction function, @Nonnull String alias) {
        return join(type, TemplateFunction.template(function), alias);
    }

    /**
     * A builder for constructing the WHERE clause of the query.
     *
     * @param <T> the type of the table being queried.
     * @param <R> the type of the result.
     * @param <ID> the type of the primary key.
     */
    public static abstract class KWhereBuilder<T extends Record, R, ID> implements KSubqueryTemplate {

        /**
         * A predicate that always evaluates to true.
         */
        public final KPredicateBuilder<T, R, ID> TRUE() {
            return expression(RAW."TRUE");
        }

        /**
         * A predicate that always evaluates to false.
         */
        public final KPredicateBuilder<T, R, ID> FALSE() {
            return expression(RAW."FALSE");
        }

        /**
         * Adds a custom expression to the WHERE clause.
         *
         * @param template the expression to add.
         * @return the predicate builder.
         */
        public abstract KPredicateBuilder<T, R, ID> expression(@Nonnull StringTemplate template);

        /**
         * Adds a custom expression to the WHERE clause.
         *
         * @param function used to define the expression to add.
         * @return the predicate builder.
         */
        public final KPredicateBuilder<T, R, ID> expression(@Nonnull TemplateFunction function) {
            return expression(TemplateFunction.template(function));
        }

        /**
         * Adds an <code>EXISTS</code> condition to the WHERE clause using the specified subquery.
         *
         * <p>This method appends an <code>EXISTS</code> clause to the current query's WHERE condition.
         * It checks whether the provided subquery returns any rows, allowing you to filter results based
         * on the existence of related data. This is particularly useful for constructing queries that need
         * to verify the presence of certain records in a related table or subquery.
         *
         * @param subquery the subquery to check for existence.
         * @return the updated {@link QueryBuilder.PredicateBuilder} with the EXISTS condition applied.
         */
        public abstract KPredicateBuilder<T, R, ID> exists(@Nonnull KQueryBuilder<?, ?, ?> subquery);

        /**
         * Adds an <code>NOT EXISTS</code> condition to the WHERE clause using the specified subquery.
         *
         * <p>This method appends an <code>NOT EXISTS</code> clause to the current query's WHERE condition.
         * It checks whether the provided subquery returns any rows, allowing you to filter results based
         * on the existence of related data. This is particularly useful for constructing queries that need
         * to verify the absence of certain records in a related table or subquery.
         *
         * @param subquery the subquery to check for existence.
         * @return the updated {@link QueryBuilder.PredicateBuilder} with the NOT EXISTS condition applied.
         */
        public abstract KPredicateBuilder<T, R, ID> notExists(@Nonnull KQueryBuilder<?, ?, ?> subquery);

        /**
         * Adds a condition to the WHERE clause that matches the specified primary key of the table.
         *
         * @param id the id to match.
         * @return the predicate builder.
         */
        public abstract KPredicateBuilder<T, R, ID> filter(@Nonnull ID id);

        /**
         * Adds a condition to the WHERE clause that matches the specified record.
         *
         * @param record the record to match.
         * @return the predicate builder.
         */
        public abstract KPredicateBuilder<T, R, ID> filter(@Nonnull T record);

        /**
         * Adds a condition to the WHERE clause that matches the specified record. The record can represent any of the
         * related tables in the table graph or manually added joins.
         *
         * @param record the record to match.
         * @return the predicate builder.
         * @since 1.2
         */
        public abstract KPredicateBuilder<T, R, ID> filterAny(@Nonnull Record record);

        /**
         * Adds a condition to the WHERE clause that matches the specified primary keys of the table.
         *
         * @param it the ids to match.
         * @return the predicate builder.
         * @since 1.2
         */
        public abstract KPredicateBuilder<T, R, ID> filterIds(@Nonnull Iterable<? extends ID> it);

        /**
         * Adds a condition to the WHERE clause that matches the specified records.
         *
         * @param it the records to match.
         * @return the predicate builder.
         */
        public abstract KPredicateBuilder<T, R, ID> filter(@Nonnull Iterable<? extends T> it);

        /**
         * Adds a condition to the WHERE clause that matches the specified records. The record can represent any of the
         * related tables in the table graph or manually added joins.
         *
         * @param it the records to match.
         * @return the query builder.
         * @since 1.2
         */
        public abstract KPredicateBuilder<T, R, ID> filterAny(@Nonnull Iterable<? extends Record> it);

        /**
         * Adds a condition to the WHERE clause that matches the specified record. The record can represent any of
         * the related tables in the table graph or manually added joins.
         *
         * @param record the records to match.
         * @return the predicate builder.
         */
        public final <V extends Record> KPredicateBuilder<T, R, ID> filter(@Nonnull Metamodel<T, V> path, @Nonnull V record) {
            return filter(path, EQUALS, record);
        }

        /**
         * Adds a condition to the WHERE clause that matches the specified record. The record can represent any of
         * the related tables in the table graph or manually added joins.
         *
         * @param record the records to match.
         * @return the predicate builder.
         */
        public final <V extends Record> KPredicateBuilder<T, R, ID> filterAny(@Nonnull Metamodel<?, V> path, @Nonnull V record) {
            return filterAny(path, EQUALS, record);
        }

        /**
         * Adds a condition to the WHERE clause that matches the specified records. The records can represent any of
         * the related tables in the table graph or manually added joins.
         *
         * @param it the records to match.
         * @return the predicate builder.
         */
        public final <V extends Record> KPredicateBuilder<T, R, ID> filter(@Nonnull Metamodel<T, V> path, @Nonnull Iterable<V> it) {
            return filter(path, IN, it);
        }

        /**
         * Adds a condition to the WHERE clause that matches the specified records. The records can represent any of
         * the related tables in the table graph or manually added joins.
         *
         * @param it the records to match.
         * @return the predicate builder.
         */
        public final <V extends Record> KPredicateBuilder<T, R, ID> filterAny(@Nonnull Metamodel<?, V> path, @Nonnull Iterable<V> it) {
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
        public abstract <V> KPredicateBuilder<T, R, ID> filter(@Nonnull Metamodel<T, V> path,
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
        public abstract <V> KPredicateBuilder<T, R, ID> filterAny(@Nonnull Metamodel<?, V> path,
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
        @SafeVarargs
        public final <V> KPredicateBuilder<T, R, ID> filter(@Nonnull Metamodel<T, V> path,
                                                            @Nonnull Operator operator,
                                                            @Nonnull V... o) {
            return filterImpl(path, operator, o);
        }

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
        @SafeVarargs
        public final <V> KPredicateBuilder<T, R, ID> filterAny(@Nonnull Metamodel<?, V> path,
                                                               @Nonnull Operator operator,
                                                               @Nonnull V... o) {
            return filterImpl(path, operator, o);
        }

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
        protected abstract <V> KPredicateBuilder<T, R, ID> filterImpl(@Nonnull Metamodel<?, V> path,
                                                                      @Nonnull Operator operator,
                                                                      @Nonnull V[] o);

        /**
         * Adds a custom expression to the WHERE clause.
         *
         * <p>This method calls the {@link #expression(TemplateFunction)}</p> method.
         *
         * @param function used to define the expression to add.
         * @return the predicate builder.
         */
        public final KPredicateBuilder<T, R, ID> invoke(@Nonnull TemplateFunction function) {
            return expression(function);
        }
    }

    /**
     * A builder for constructing the predicates of the WHERE clause of the query.
     *
     * @param <T> the type of the table being queried.
     * @param <R> the type of the result.
     * @param <ID> the type of the primary key.
     */
    public interface KPredicateBuilder<T extends Record, R, ID> {

        /**
         * Adds a predicate to the WHERE clause using an AND condition.
         *
         * <p>This method combines the specified predicate with existing predicates using an AND operation, ensuring
         * that all added conditions must be true.</p>
         *
         * @param predicate the predicate to add.
         * @return the predicate builder.
         */
        KPredicateBuilder<T, R, ID> and(@Nonnull KPredicateBuilder<T, R, ID> predicate);

        /**
         * Adds a predicate to the WHERE clause using an OR condition.
         *
         * <p>This method combines the specified predicate with existing predicates using an OR operation, allowing any
         * of the added conditions to be true.</p>
         *
         * @param predicate the predicate to add.
         * @return the predicate builder.
         */
        KPredicateBuilder<T, R, ID> or(@Nonnull KPredicateBuilder<T, R, ID> predicate);
    }

    /**
     * Adds a WHERE clause that matches the specified primary key of the table.
     *
     * @param id the id to match.
     * @return the query builder.
     */
    public final KQueryBuilder<T, R, ID> where(@Nonnull ID id) {
        return where(predicate -> predicate.filter(id));
    }

    /**
     * Adds a WHERE clause that matches the specified record.
     *
     * @param record the record to match.
     * @return the query builder.
     */
    public final KQueryBuilder<T, R, ID> where(@Nonnull T record) {
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
    public final KQueryBuilder<T, R, ID> whereAny(@Nonnull Record record) {
        return where(predicate -> predicate.filterAny(record));
    }

    /**
     * Adds a WHERE clause that matches the specified primary keys of the table.
     *
     * @param it ids to match.
     * @return the query builder.
     * @since 1.2
     */
    public final KQueryBuilder<T, R, ID> whereIds(@Nonnull Iterable<? extends ID> it) {
        return where(predicate -> predicate.filterIds(it));
    }

    /**
     * Adds WHERE clause that matches the specified record. The record can represent any of the related tables in the
     * table graph.
     *
     * @param record the records to match.
     * @return the predicate builder.
     */
    public final <V extends Record> KQueryBuilder<T, R, ID> where(@Nonnull Metamodel<T, V> path, @Nonnull V record) {
        return where(path, EQUALS, record);
    }

    /**
     * Adds WHERE clause that matches the specified record. The record can represent any of the related tables in the
     * table graph or manually added joins.
     *
     * @param record the records to match.
     * @return the predicate builder.
     */
    public final <V extends Record> KQueryBuilder<T, R, ID> whereAny(@Nonnull Metamodel<?, V> path, @Nonnull V record) {
        return whereAny(path, EQUALS, record);
    }

    /**
     * Adds a WHERE clause that matches the specified records. The records can represent any of the related tables in
     * the table graph.
     *
     * @param it the records to match.
     * @return the predicate builder.
     */
    public final <V extends Record> KQueryBuilder<T, R, ID> where(@Nonnull Metamodel<T, V> path, @Nonnull Iterable<V> it) {
        return where(path, IN, it);
    }

    /**
     * Adds a WHERE clause that matches the specified records. The records can represent any of the related tables in
     * the table graph or manually added joins.
     *
     * @param it the records to match.
     * @return the predicate builder.
     */
    public final <V extends Record> KQueryBuilder<T, R, ID> whereAny(@Nonnull Metamodel<?, V> path, @Nonnull Iterable<V> it) {
        return whereAny(path, IN, it);
    }

    /**
     * Adds a WHERE clause that matches the specified records.
     *
     * @param it the records to match.
     * @return the query builder.
     */
    public final KQueryBuilder<T, R, ID> where(@Nonnull Iterable<? extends T> it) {
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
    public final KQueryBuilder<T, R, ID> whereAny(@Nonnull Iterable<? extends Record> it) {
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
    public final <V> KQueryBuilder<T, R, ID> where(@Nonnull Metamodel<T, V> path,
                                                   @Nonnull Operator operator,
                                                   @Nonnull Iterable<? extends V> it) {
        return where(predicate -> predicate.filter(path , operator, it));
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
    public final <V> KQueryBuilder<T, R, ID> whereAny(@Nonnull Metamodel<?, V> path,
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
    @SafeVarargs
    public final <V> KQueryBuilder<T, R, ID> where(@Nonnull Metamodel<T, V> path,
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
    @SafeVarargs
    public final <V> KQueryBuilder<T, R, ID> whereAny(@Nonnull Metamodel<?, V> path,
                                                      @Nonnull Operator operator,
                                                      @Nonnull V... o) {
        return where(predicate -> predicate.filterAny(path, operator, o));
    }

    /**
     * Adds a WHERE clause to the query for the specified expression.
     *
     * @param template the expression.
     * @return the query builder.
     */
    public final KQueryBuilder<T, R, ID> where(@Nonnull StringTemplate template) {
        return where(it -> it.expression(template));
    }

    //
    // We can't use this method because it's not possible to infer the type of the template function. We added an
    // invoke method to the KWhereBuilder interface to allow the use of template functions.
    //
    // Instead of invoking where { "a = b" }, you can use where { it { "a = b" } }.
    //

//
//    /**
//     * Adds a WHERE clause to the query for the specified expression.
//     *
//     * @param function used to define the expression.
//     * @return the query builder.
//     */
//    default KQueryBuilder<T, R, ID> where(@Nonnull TemplateFunction function) {
//        return where(expression(function));
//    }

    /**
     * Adds a WHERE clause to the query using a {@link QueryBuilder.WhereBuilder}.
     *
     * @param predicate the predicate to add.
     * @return the query builder.
     */
    public abstract KQueryBuilder<T, R, ID> where(@Nonnull Function<KWhereBuilder<T, R, ID>, KPredicateBuilder<?, ?, ?>> predicate);

    /**
     * Adds a GROUP BY clause to the query for field at the specified path in the table graph.
     *
     * @param path the path to group by.
     * @return the query builder.
     * @since 1.2
     */
    public final KQueryBuilder<T, R, ID> groupBy(@Nonnull Metamodel<T, ?> path) {
        return groupBy(RAW."\{path}");
    }

    /**
     * Adds a GROUP BY clause to the query for field at the specified path in the table graph. The metamodel can refer
     * to manually added joins.
     *
     * @param path the path to group by.
     * @return the query builder.
     * @since 1.2
     */
    public final KQueryBuilder<T, R, ID> groupBy(@Nonnull Metamodel<?, ?>... path) {
        // We can safely invoke groupByAny as the underlying logic is identical. The main purpose of having these
        // separate methods is to provide (more) type safety when using metamodels that are guaranteed to be present in
        // the table graph.
        return groupByAny(path);
    }

    /**
     * Adds a GROUP BY clause to the query for field at the specified path in the table graph. The metamodel can refer
     * to manually added joins.
     *
     * @param path the path to group by.
     * @return the query builder.
     * @since 1.2
     */
    public final KQueryBuilder<T, R, ID> groupByAny(@Nonnull Metamodel<?, ?>... path) {
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
     * @since 1.2
     */
    public final KQueryBuilder<T, R, ID> groupBy(@Nonnull StringTemplate template) {
        return append(StringTemplate.combine(RAW."GROUP BY ", template));
    }

    /**
     * Adds a GROUP BY clause to the query using a string template.
     *
     * @param function used to define the template to group by.
     * @return the query builder.
     * @since 1.2
     */
    public final KQueryBuilder<T, R, ID> groupBy(@Nonnull TemplateFunction function) {
        return groupBy(TemplateFunction.template(function));
    }

    /**
     * Adds a HAVING clause to the query using the specified expression.
     *
     * @param path the path to the object in the table graph.
     * @param operator the operator to use for the comparison.
     * @param o the object(s) to match, which can be primary keys, records representing the table, or fields in the
     *          table graph.
     * @return the query builder.
     * @since 1.2
     */
    @SafeVarargs
    public final <V> KQueryBuilder<T, R, ID> having(@Nonnull Metamodel<T, V> path,
                                                    @Nonnull Operator operator,
                                                    @Nonnull V... o) {
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
     * @since 1.2
     */
    @SafeVarargs
    public final <V> KQueryBuilder<T, R, ID> havingAny(@Nonnull Metamodel<?, V> path,
                                                       @Nonnull Operator operator,
                                                       @Nonnull V... o) {
        return having(RAW."\{new ObjectExpression(path, operator, o)}");
    }

    /**
     * Adds a HAVING clause to the query using the specified expression.
     *
     * @param template the expression to add.
     * @return the query builder.
     * @since 1.2
     */
    public final <V> KQueryBuilder<T, R, ID> having(@Nonnull StringTemplate template) {
        return append(StringTemplate.combine(RAW."HAVING ", template));
    }

    /**
     * Adds an ORDER BY clause to the query for the field at the specified path in the table graph.
     *
     * @param path the path to order by.
     * @return the query builder.
     * @since 1.2
     */
    public final KQueryBuilder<T, R, ID> orderBy(@Nonnull Metamodel<T, ?> path) {
        return orderBy(RAW."\{path}");
    }

    /**
     * Adds an ORDER BY clause to the query for the field at the specified path in the table graph.
     *
     * @param path the path to order by.
     * @param ascending whether to order in ascending order.
     * @return the query builder.
     * @since 1.2
     */
    public final KQueryBuilder<T, R, ID> orderBy(@Nonnull Metamodel<T, ?> path, boolean ascending) {
        return orderBy(RAW."\{path} \{ascending ? "ASC" : "DESC"}");
    }

    /**
     * Adds an ORDER BY clause to the query for the field at the specified path in the table graph or manually added
     * joins.
     *
     * @param path the path to order by.
     * @return the query builder.
     * @since 1.2
     */
    @SafeVarargs
    public final KQueryBuilder<T, R, ID> orderBy(@Nonnull Metamodel<T, ?>... path) {
        // We can safely invoke orderByAny as the underlying logic is identical. The main purpose of having these
        // separate methods is to provide (more) type safety when using metamodels that are guaranteed to be present in
        // the table graph.
        return orderByAny(path);
    }

    /**
     * Adds an ORDER BY clause to the query for the field at the specified path in the table graph or manually added
     * joins.
     *
     * @param path the path to order by.
     * @return the query builder.
     * @since 1.2
     */
    public final KQueryBuilder<T, R, ID> orderByAny(@Nonnull Metamodel<?, ?>... path) {
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
     * @since 1.2
     */
    public final KQueryBuilder<T, R, ID> orderBy(@Nonnull StringTemplate template) {
        return append(StringTemplate.combine(RAW."ORDER BY ", template));
    }

    /**
     * Adds an ORDER BY clause to the query using a string template.
     *
     * @param function used to define the template to order by.
     * @return the query builder.
     * @since 1.2
     */
    public final KQueryBuilder<T, R, ID> orderBy(@Nonnull TemplateFunction function) {
        return groupBy(TemplateFunction.template(function));
    }

    /**
     * Adds a LIMIT clause to the query.
     *
     * @param limit the maximum number of records to return.
     * @return the query builder.
     * @since 1.2
     */
    public abstract KQueryBuilder<T, R, ID> limit(int limit);

    /**
     * Adds a LIMIT clause to the query.
     *
     * @param offset the offset.
     * @param limit the maximum number of records to return.
     * @return the query builder.
     * @since 1.2
     */
    public abstract KQueryBuilder<T, R, ID> limit(int offset, int limit);

    /**
     * Returns a processor that can be used to append the query with a string template.
     *
     * @param template the string template to append.
     * @return a processor that can be used to append the query with a string template.
     */
    public abstract KQueryBuilder<T, R, ID> append(@Nonnull StringTemplate template);

    /**
     * Returns a processor that can be used to append the query with a string template.
     *
     * @param function used to define the string template to append.
     * @return a processor that can be used to append the query with a string template.
     */
    public final KQueryBuilder<T, R, ID> append(@Nonnull TemplateFunction function) {
        return append(TemplateFunction.template(function));
    }

    //
    // Locking.
    //

    /**
     * Locks the selected rows for reading.
     *
     * @return the query builder.
     * @throws PersistenceException if the database does not support the specified lock mode, or if the lock mode is
     * not supported for the current query.
     * @since 1.2
     */
    public abstract KQueryBuilder<T, R, ID> forShare();

    /**
     * Locks the selected rows for reading.
     *
     * @return the query builder.
     * @throws PersistenceException if the database does not support the specified lock mode, or if the lock mode is
     * not supported for the current query.
     * @since 1.2
     */
    public abstract KQueryBuilder<T, R, ID> forUpdate();

    /**
     * Locks the selected rows using a custom lock mode.
     *
     * <p>Note that this method results in non-portable code, as the lock mode is specific to the underlying database.</p>
     *
     * @return the query builder.
     * @throws PersistenceException if the lock mode is not supported for the current query.
     * @since 1.2
     */
    public abstract KQueryBuilder<T, R, ID> forLock(@Nonnull StringTemplate template);

    //
    // Finalization.
    //

    /**
     * Builds the query based on the current state of the query builder.
     *
     * @return the constructed query.
     */
    public abstract KQuery build();

    /**
     * Prepares the query for execution.
     *
     * <p>Unlike regular queries, which are constructed lazily, prepared queries are constructed eagerly.
     * Prepared queries allow the use of bind variables and enable reading generated keys after row insertion.</p>
     *
     * <p>Note that the prepared query must be closed after usage to prevent resource leaks.</p>
     *
     * @return the prepared query.
     * @throws PersistenceException if the query preparation fails.
     */
    public final KPreparedQuery prepare() {
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
     * usage to prevent resource leaks.</p>
     *
     * @return a stream of results.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    public abstract Stream<R> getResultStream();

    /**
     * Executes the query and returns a stream of using the specified callback. This method retrieves the records and
     * applies the provided callback to process them, returning the result produced by the callback.
     *
     * <p>This method ensures efficient handling of large data sets by loading entities only as needed.
     * It also manages lifecycle of the callback stream, automatically closing the stream after processing to prevent
     * resource leaks.</p>
     *
     * @param callback a {@link KResultCallback} defining how to process the stream of records and produce a result.
     * @param <X> the type of result produced by the callback after processing the entities.
     * @return the result produced by the callback's processing of the record stream.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     * connectivity.
     */
    public final <X> X getResult(@Nonnull KResultCallback<R, X> callback) {
        try (Stream<R> stream = getResultStream()) {
            return callback.process(toSequence(stream));
        }
    }

    /**
     * Returns the number of results of this query.
     *
     * @return the total number of results of this query as a long value.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    public final long getResultCount() {
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
    public final List<R> getResultList() {
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
    public final R getSingleResult() {
        return getResultStream()
                .reduce((_, _) -> {
                    throw new NonUniqueResultException("Expected single result, but found more than one.");
                }).orElseThrow(() -> new NoResultException("Expected single result, but found none."));
    }

    /**
     * Executes the query and returns an optional result.
     *
     * @return the optional result.
     * @throws NonUniqueResultException if more than one result.
     * @throws PersistenceException if the query fails.
     */
    public final Optional<R> getOptionalResult() {
        return getResultStream()
                .reduce((_, _) -> {
                    throw new NonUniqueResultException("Expected single result, but found more than one.");
                });
    }

    /**
     * Performs the function in multiple slices, each containing up to {@code size} elements from the stream.
     *
     * @param stream the stream to batch.
     * @param batchSize the maximum number of elements to include in each batch.
     * @param function the function to apply to each batch.
     * @return a stream of results from each batch.
     * @param <X> the type of elements in the stream.
     * @param <Y> the type of elements in the result stream.
     */
    public static <X, Y> Stream<Y> slice(@Nonnull Stream<X> stream, int batchSize, @Nonnull Function<List<X>, Stream<Y>> function) {
        return QueryBuilder.slice(stream, batchSize, function);
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
     * <p>The method utilizes a "tripwire" mechanism to ensure that the original stream is properly managed and closed upon
     * completion of processing, preventing resource leaks.</p>
     *
     * @param <X> the type of elements in the stream.
     * @param stream the original stream of elements to be sliced.
     * @param size the maximum number of elements to include in each slice. If {@code size} is
     * {@code Integer.MAX_VALUE}, only one slice will be returned.
     * @return a stream of slices, where each slice contains up to {@code batchSize} elements from the original stream.
     */
    public static <X> Stream<List<X>> slice(@Nonnull Stream<X> stream, int size) {
        return QueryBuilder.slice(stream, size);
    }

    private <X> Sequence<X> toSequence(@Nonnull Stream<X> stream) {
        return SequencesKt.asSequence(stream.iterator());
    }
}
