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
package st.orm.kotlin.template;

import jakarta.annotation.Nonnull;
import st.orm.Ref;
import st.orm.template.Metamodel;
import st.orm.template.Operator;
import st.orm.template.PredicateBuilder;
import st.orm.template.TemplateFunction;

import static java.lang.StringTemplate.RAW;
import static st.orm.template.Operator.EQUALS;
import static st.orm.template.Operator.IN;

/**
 * A builder for constructing the WHERE clause of the query.
 *
 * @param <T>  the type of the table being queried.
 * @param <R>  the type of the result.
 * @param <ID> the type of the primary key.
 */
public abstract class KWhereBuilder<T extends Record, R, ID> implements KSubqueryTemplate {

    /**
     * A predicate that always evaluates to true.
     */
    public final KPredicateBuilder<T, R, ID> TRUE() {
        return where(RAW."TRUE");
    }

    /**
     * A predicate that always evaluates to false.
     */
    public final KPredicateBuilder<T, R, ID> FALSE() {
        return where(RAW."FALSE");
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
     * @return the updated {@link PredicateBuilder} with the EXISTS condition applied.
     */
    public abstract KPredicateBuilder<T, R, ID> exists(@Nonnull KQueryBuilder<?, ?, ?> subquery);

    /**
     * Adds a <code>NOT EXISTS</code> condition to the WHERE clause using the specified subquery.
     *
     * <p>This method appends a <code>NOT EXISTS</code> clause to the current query's WHERE condition.
     * It checks whether the provided subquery returns any rows, allowing you to filter results based
     * on the existence of related data. This is particularly useful for constructing queries that need
     * to verify the absence of certain records in a related table or subquery.
     *
     * @param subquery the subquery to check for existence.
     * @return the updated {@link PredicateBuilder} with the NOT EXISTS condition applied.
     */
    public abstract KPredicateBuilder<T, R, ID> notExists(@Nonnull KQueryBuilder<?, ?, ?> subquery);

    /**
     * Adds a condition to the WHERE clause that matches the specified primary key of the table.
     *
     * @param id the id to match.
     * @return the predicate builder.
     */
    public abstract KPredicateBuilder<T, R, ID> whereId(@Nonnull ID id);

    /**
     * Adds a condition to the WHERE clause that matches the specified primary key of the table, expressed by a ref.
     *
     * @param ref the ref to match.
     * @return the predicate builder.
     * @since 1.3
     */
    public abstract KPredicateBuilder<T, R, ID> whereRef(@Nonnull Ref<T> ref);

    /**
     * Adds a condition to the WHERE clause that matches the specified primary key of the table, expressed by a ref.
     * The ref can represent any of the related tables in the table graph or manually added joins.
     *
     * @param ref the ref to match.
     * @return the predicate builder.
     * @since 1.3
     */
    public abstract KPredicateBuilder<T, R, ID> whereAnyRef(@Nonnull Ref<? extends Record> ref);

    /**
     * Adds a condition to the WHERE clause that matches the specified record.
     *
     * @param record the record to match.
     * @return the predicate builder.
     */
    public abstract KPredicateBuilder<T, R, ID> where(@Nonnull T record);

    /**
     * Adds a condition to the WHERE clause that matches the specified record. The record can represent any of the
     * related tables in the table graph or manually added joins.
     *
     * @param record the record to match.
     * @return the predicate builder.
     * @since 1.2
     */
    public abstract KPredicateBuilder<T, R, ID> whereAny(@Nonnull Record record);

    /**
     * Adds a condition to the WHERE clause that matches the specified primary keys of the table.
     *
     * @param it the ids to match.
     * @return the predicate builder.
     * @since 1.2
     */
    public abstract KPredicateBuilder<T, R, ID> whereId(@Nonnull Iterable<? extends ID> it);

    /**
     * Adds a condition to the WHERE clause that matches the specified primary keys of the table, expressed by a ref.
     *
     * @param it the refs to match.
     * @return the predicate builder.
     * @since 1.3
     */
    public abstract KPredicateBuilder<T, R, ID> whereRef(@Nonnull Iterable<? extends Ref<T>> it);

    /**
     * Adds a condition to the WHERE clause that matches the specified objects at the specified path in the table
     * graph or manually added joins.
     *
     * @param path     the path to the object in the table graph.
     * @param operator the operator to use for the comparison.
     * @param o        the object(s) to match, which can be primary keys, records representing the table, or fields in the
     *                 table graph.
     * @param <V>      the type of the object that the metamodel represents.
     * @return the query builder.
     * @since 1.2
     */
    protected abstract <V> KPredicateBuilder<T, R, ID> whereImpl(@Nonnull Metamodel<?, V> path,
                                                                 @Nonnull Operator operator,
                                                                 @Nonnull V[] o);

    /**
     * Adds a condition to the WHERE clause that matches the specified primary keys of the table, expressed by a ref.
     * The ref can represent any of the related tables in the table graph or manually added joins.
     *
     * @param it the refs to match.
     * @return the predicate builder.
     * @since 1.3
     */
    public abstract KPredicateBuilder<T, R, ID> whereAnyRef(@Nonnull Iterable<? extends Ref<? extends Record>> it);

    /**
     * Adds a condition to the WHERE clause that matches the specified records.
     *
     * @param it the records to match.
     * @return the predicate builder.
     */
    public abstract KPredicateBuilder<T, R, ID> where(@Nonnull Iterable<? extends T> it);

    /**
     * Adds a condition to the WHERE clause that matches the specified records. The record can represent any of the
     * related tables in the table graph or manually added joins.
     *
     * @param it the records to match.
     * @return the query builder.
     */
    public abstract KPredicateBuilder<T, R, ID> whereAny(@Nonnull Iterable<? extends Record> it);

    /**
     * Adds a condition to the WHERE clause that matches the specified record. The record can represent any of
     * the related tables in the table graph.
     *
     * @param path   the path to the object in the table graph.
     * @param record the records to match.
     * @return the predicate builder.
     */
    public final <V extends Record> KPredicateBuilder<T, R, ID> where(@Nonnull Metamodel<T, V> path, @Nonnull V record) {
        return where(path, EQUALS, record);
    }

    /**
     * Adds a condition to the WHERE clause that matches the specified record. The record can represent any of
     * the related tables in the table graph or manually added joins.
     *
     * @param record the records to match.
     * @return the predicate builder.
     */
    public final <V extends Record> KPredicateBuilder<T, R, ID> whereAny(@Nonnull Metamodel<?, V> path, @Nonnull V record) {
        return whereAny(path, EQUALS, record);
    }

    /**
     * Adds a condition to the WHERE clause that matches the specified ref. The record can represent any of
     * the related tables in the table graph.
     *
     * @param path the path to the object in the table graph.
     * @param ref  the ref to match.
     * @return the predicate builder.
     * @since 1.3
     */
    public abstract <V extends Record> KPredicateBuilder<T, R, ID> where(@Nonnull Metamodel<T, V> path, @Nonnull Ref<V> ref);

    /**
     * Adds a condition to the WHERE clause that matches the specified ref. The record can represent any of
     * the related tables in the table graph or manually added joins.
     *
     * @param path the path to the object in the table graph.
     * @param ref  the ref to match.
     * @return the predicate builder.
     * @since 1.3
     */
    public abstract <V extends Record> KPredicateBuilder<T, R, ID> whereAny(@Nonnull Metamodel<?, V> path, @Nonnull Ref<V> ref);

    /**
     * Adds a condition to the WHERE clause that matches the specified refs. The refs can represent any of
     * the related tables in the table graph.
     *
     * @param path the path to the ref in the table graph.
     * @param it   the refs to match.
     * @return the predicate builder.
     * @since 1.3
     */
    public abstract <V extends Record> KPredicateBuilder<T, R, ID> whereRef(@Nonnull Metamodel<T, V> path, @Nonnull Iterable<? extends Ref<V>> it);

    /**
     * Adds a condition to the WHERE clause that matches the specified refs. The refs can represent any of
     * the related tables in the table graph.
     *
     * @param path the path to the ref in the table graph.
     * @param it   the refs to match.
     * @return the predicate builder.
     * @since 1.3
     */
    public abstract <V extends Record> KPredicateBuilder<T, R, ID> whereAnyRef(@Nonnull Metamodel<?, V> path, @Nonnull Iterable<? extends Ref<V>> it);

    /**
     * Adds a condition to the WHERE clause that matches the specified records. The records can represent any of
     * the related tables in the table graph.
     *
     * @param path the path to the object in the table graph.
     * @param it   the records to match.
     * @return the predicate builder.
     */
    public final <V extends Record> KPredicateBuilder<T, R, ID> where(@Nonnull Metamodel<T, V> path, @Nonnull Iterable<V> it) {
        return where(path, IN, it);
    }

    /**
     * Adds a condition to the WHERE clause that matches the specified records. The records can represent any of
     * the related tables in the table graph or manually added joins.
     *
     * @param path the path to the object in the table graph.
     * @param it   the records to match.
     * @return the predicate builder.
     */
    public final <V extends Record> KPredicateBuilder<T, R, ID> whereAny(@Nonnull Metamodel<?, V> path, @Nonnull Iterable<V> it) {
        return whereAny(path, IN, it);
    }

    /**
     * Adds a condition to the WHERE clause that matches the specified objects at the specified path in the table
     * graph.
     *
     * @param path     the path to the object in the table graph.
     * @param operator the operator to use for the comparison.
     * @param it       the objects to match, which can be primary keys, records representing the table, or fields in the
     *                 table graph.
     * @param <V>      the type of the object that the metamodel represents.
     * @return the query builder.
     * @since 1.2
     */
    public abstract <V> KPredicateBuilder<T, R, ID> where(@Nonnull Metamodel<T, V> path,
                                                          @Nonnull Operator operator,
                                                          @Nonnull Iterable<? extends V> it);

    /**
     * Adds a condition to the WHERE clause that matches the specified objects at the specified path in the table
     * graph or manually added joins.
     *
     * @param path     the path to the object in the table graph.
     * @param operator the operator to use for the comparison.
     * @param it       the objects to match, which can be primary keys, records representing the table, or fields in the
     *                 table graph.
     * @param <V>      the type of the object that the metamodel represents.
     * @return the query builder.
     * @since 1.2
     */
    public abstract <V> KPredicateBuilder<T, R, ID> whereAny(@Nonnull Metamodel<?, V> path,
                                                             @Nonnull Operator operator,
                                                             @Nonnull Iterable<? extends V> it);

    /**
     * Adds a condition to the WHERE clause that matches the specified objects at the specified path in the table
     * graph.
     *
     * @param path     the path to the object in the table graph.
     * @param operator the operator to use for the comparison.
     * @param o        the object(s) to match, which can be primary keys, records representing the table, or fields in the
     *                 table graph.
     * @param <V>      the type of the object that the metamodel represents.
     * @return the query builder.
     * @since 1.2
     */
    @SafeVarargs
    public final <V> KPredicateBuilder<T, R, ID> where(@Nonnull Metamodel<T, V> path,
                                                       @Nonnull Operator operator,
                                                       @Nonnull V... o) {
        return whereImpl(path, operator, o);
    }

    /**
     * Adds a condition to the WHERE clause that matches the specified objects at the specified path in the table
     * graph or manually added joins.
     *
     * @param path     the path to the object in the table graph.
     * @param operator the operator to use for the comparison.
     * @param o        the object(s) to match, which can be primary keys, records representing the table, or fields in the
     *                 table graph.
     * @param <V>      the type of the object that the metamodel represents.
     * @return the query builder.
     * @since 1.2
     */
    @SafeVarargs
    public final <V> KPredicateBuilder<T, R, ID> whereAny(@Nonnull Metamodel<?, V> path,
                                                          @Nonnull Operator operator,
                                                          @Nonnull V... o) {
        return whereImpl(path, operator, o);
    }

    /**
     * Adds a custom expression to the WHERE clause.
     *
     * @param template the expression to add.
     * @return the predicate builder.
     */
    public abstract KPredicateBuilder<T, R, ID> where(@Nonnull StringTemplate template);

    /**
     * Adds a custom expression to the WHERE clause.
     *
     * @param function used to define the expression to add.
     * @return the predicate builder.
     */
    public final KPredicateBuilder<T, R, ID> where(@Nonnull TemplateFunction function) {
        return where(TemplateFunction.template(function));
    }

    /**
     * Adds a custom expression to the WHERE clause.
     *
     * <p>This method calls the {@link #where(TemplateFunction)}</p> method.
     *
     * @param function used to define the expression to add.
     * @return the predicate builder.
     */
    public final KPredicateBuilder<T, R, ID> invoke(@Nonnull TemplateFunction function) {
        return where(function);
    }
}
