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
package st.orm.template.impl

import st.orm.*
import st.orm.core.template.impl.Subqueryable
import st.orm.template.*
import java.util.stream.Stream
import kotlin.reflect.KClass

class QueryBuilderImpl<T : Record, R, ID>(
    private val core: st.orm.core.template.QueryBuilder<T, R, ID>
) : QueryBuilder<T, R, ID>, Subqueryable {

    /**
     * Returns a typed query builder for the specified primary key type.
     *
     * @param pkType the primary key type.
     * @return the typed query builder.
     * @param <X> the type of the primary key.
     * @throws PersistenceException if the pk type is not valid.
     * @since 1.2
     */
    override fun <X : Any> typed(pkType: KClass<X>): QueryBuilder<T, R, X> {
        return QueryBuilderImpl<T, R, X>(core.typed<X>(pkType.java))
    }

    /**
     * Returns a query builder that does not require a WHERE clause for UPDATE and DELETE queries.
     *
     *
     * This method is used to prevent accidental updates or deletions of all records in a table when a WHERE clause
     * is not provided.
     *
     * @since 1.2
     */
    override fun safe(): QueryBuilder<T, R, ID> {
        return QueryBuilderImpl<T, R, ID>(core.safe())
    }

    /**
     * Marks the current query as a distinct query.
     *
     * @return the query builder.
     */
    override fun distinct(): QueryBuilder<T, R, ID> {
        return QueryBuilderImpl<T, R, ID>(core.distinct())
    }

    /**
     * Returns a processor that can be used to append the query with a string template.
     *
     * @param template the string template to append.
     * @return a processor that can be used to append the query with a string template.
     */
    override fun append(template: TemplateString): QueryBuilder<T, R, ID> {
        return QueryBuilderImpl<T, R, ID>(core.append(template.unwrap))
    }

    /**
     * Locks the selected rows for reading.
     *
     * @return the query builder.
     * @throws PersistenceException if the database does not support the specified lock mode, or if the lock mode is
     * not supported for the current query.
     * @since 1.2
     */
    override fun forShare(): QueryBuilder<T, R, ID> {
        return QueryBuilderImpl<T, R, ID>(core.forShare())
    }

    /**
     * Locks the selected rows for reading.
     *
     * @return the query builder.
     * @throws PersistenceException if the database does not support the specified lock mode, or if the lock mode is
     * not supported for the current query.
     * @since 1.2
     */
    override fun forUpdate(): QueryBuilder<T, R, ID> {
        return QueryBuilderImpl<T, R, ID>(core.forUpdate())
    }

    /**
     * Locks the selected rows using a custom lock mode.
     *
     *
     * **Note:** This method results in non-portable code, as the lock mode is specific to the underlying database.
     *
     * @return the query builder.
     * @throws PersistenceException if the lock mode is not supported for the current query.
     * @since 1.2
     */
    override fun forLock(template: TemplateString): QueryBuilder<T, R, ID> {
        return QueryBuilderImpl<T, R, ID>(core.forLock(template.unwrap))
    }

    /**
     * Builds the query based on the current state of the query builder.
     *
     * @return the constructed query.
     */
    override fun build(): Query {
        return QueryImpl(core.build())
    }

    override val resultStream: Stream<R>
        /**
         * Executes the query and returns a stream of results.
         *
         *
         * The resulting stream is lazily loaded, meaning that the records are only retrieved from the database as they
         * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
         * dealing with large volumes of records.
         *
         *
         * **Note:** Calling this method does trigger the execution of the underlying query, so it should
         * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must
         * be closed after usage to prevent resource leaks.
         *
         * @return a stream of results.
         * @throws PersistenceException if the query operation fails due to underlying database issues, such as
         * connectivity.
         */
        get() = core.getResultStream()

    /**
     * Adds a cross join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    override fun crossJoin(relation: KClass<out Record>): QueryBuilder<T, R, ID> {
        return join(JoinType.cross(), relation, "").on { t("") }
    }

    /**
     * Adds an inner join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    override fun innerJoin(relation: KClass<out Record>): TypedJoinBuilder<T, R, ID> {
        return join(JoinType.inner(), relation, "")
    }

    /**
     * Adds a left join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    override fun leftJoin(relation: KClass<out Record>): TypedJoinBuilder<T, R, ID> {
        return join(JoinType.left(), relation, "")
    }

    /**
     * Adds a right join to the query.
     *
     * @param relation the relation to join.
     * @return the query builder.
     */
    override fun rightJoin(relation: KClass<out Record>): TypedJoinBuilder<T, R, ID> {
        return join(JoinType.right(), relation, "")
    }

    /**
     * Adds a join of the specified type to the query.
     *
     * @param type the type of the join (e.g., INNER, LEFT, RIGHT).
     * @param relation the relation to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    override fun join(
        type: JoinType,
        relation: KClass<out Record>,
        alias: String
    ): TypedJoinBuilder<T, R, ID> {
        val joinBuilder = core.join(type, relation.java, alias)
        return object : TypedJoinBuilder<T, R, ID> {
            override fun on(relation: KClass<out Record>): QueryBuilder<T, R, ID> {
                return QueryBuilderImpl(joinBuilder.on(relation.java))
            }

            override fun on(template: TemplateString): QueryBuilder<T, R, ID> {
                return QueryBuilderImpl<T, R, ID>(joinBuilder.on(template.unwrap))
            }
        }
    }

    /**
     * Adds a cross join to the query.
     *
     * @param template the condition to join.
     * @return the query builder.
     */
    override fun crossJoin(template: TemplateString): QueryBuilder<T, R, ID> {
        return join(JoinType.cross(), template, "").on { t("") }
    }

    /**
     * Adds an inner join to the query.
     *
     * @param template the condition to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    override fun innerJoin(template: TemplateString, alias: String): JoinBuilder<T, R, ID> {
        return join(JoinType.inner(), template, alias)
    }

    /**
     * Adds a left join to the query.
     *
     * @param template the condition to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    override fun leftJoin(template: TemplateString, alias: String): JoinBuilder<T, R, ID> {
        return join(JoinType.left(), template, alias)
    }

    /**
     * Adds a right join to the query.
     *
     * @param template the condition to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    override fun rightJoin(template: TemplateString, alias: String): JoinBuilder<T, R, ID> {
        return join(JoinType.right(), template, alias)
    }

    /**
     * Adds a join of the specified type to the query using a template.
     *
     * @param type the join type.
     * @param template the template to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    override fun join(
        type: JoinType,
        template: TemplateString,
        alias: String
    ): JoinBuilder<T, R, ID> {
        val joinBuilder = core.join(type, template.unwrap, alias)
        return object : JoinBuilder<T, R, ID> {
            override fun on(template: TemplateString): QueryBuilder<T, R, ID> {
                return QueryBuilderImpl<T, R, ID>(joinBuilder.on(template.unwrap))
            }
        }
    }

    /**
     * Adds a join of the specified type to the query using a subquery.
     *
     * @param type the join type.
     * @param subquery the subquery to join.
     * @param alias the alias to use for the joined relation.
     * @return the query builder.
     */
    override fun join(
        type: JoinType,
        subquery: QueryBuilder<*, *, *>,
        alias: String
    ): JoinBuilder<T, R, ID> {
        val joinBuilder = core.join(type, (subquery as QueryBuilderImpl<*, *, *>).core, alias)
        return object : JoinBuilder<T, R, ID> {
            override fun on(template: TemplateString): QueryBuilder<T, R, ID> {
                return QueryBuilderImpl<T, R, ID>(joinBuilder.on(template.unwrap))
            }
        }
    }

    internal class PredicateBuilderImpl<TX : Record, RX, IDX>(
        val core: st.orm.core.template.PredicateBuilder<TX, RX, IDX>
    ) : PredicateBuilder<TX, RX, IDX> {
        override fun and(predicate: PredicateBuilder<TX, *, *>): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.and((predicate as PredicateBuilderImpl<TX, *, *>).core))
        }

        override fun <TY : Record, RY, IDY> andAny(predicate: PredicateBuilder<TY, RY, IDY>): PredicateBuilder<TY, RY, IDY> {
            return PredicateBuilderImpl<TY, RY, IDY>(core.andAny((predicate as PredicateBuilderImpl<TY, RY, IDY>).core))
        }

        override fun and(template: TemplateString): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.and(template.unwrap))
        }

        override fun or(predicate: PredicateBuilder<TX, *, *>): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.or((predicate as PredicateBuilderImpl<TX, *, *>).core))
        }

        override fun <TY : Record, RY, IDY> orAny(predicate: PredicateBuilder<TY, RY, IDY>): PredicateBuilder<TY, RY, IDY> {
            return PredicateBuilderImpl<TY, RY, IDY>(core.orAny((predicate as PredicateBuilderImpl<TY, RY, IDY>).core))
        }

        override fun or(template: TemplateString): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.or(template.unwrap))
        }
    }

    internal class WhereBuilderImpl<TX : Record, RX, IDX>(
        val core: st.orm.core.template.WhereBuilder<TX, RX, IDX>
    ) : WhereBuilder<TX, RX, IDX> {
        override fun <T : Record> subquery(fromType: KClass<T>, template: TemplateString): QueryBuilder<T, *, *> {
            return QueryBuilderImpl(core.subquery(fromType.java, template.unwrap))
        }

        override fun exists(subquery: QueryBuilder<*, *, *>): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.exists((subquery as QueryBuilderImpl<*, *, *>).core))
        }

        override fun notExists(subquery: QueryBuilder<*, *, *>): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.notExists((subquery as QueryBuilderImpl<*, *, *>).core))
        }

        override fun whereId(id: IDX): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.whereId(id))
        }

        override fun whereRef(ref: Ref<TX>): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.whereRef(ref))
        }

        override fun whereAnyRef(ref: Ref<out Record>): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.whereAnyRef(ref))
        }

        override fun where(record: TX): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.where(record))
        }

        override fun whereAny(record: Record): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.whereAny(record))
        }

        override fun whereId(it: Iterable<IDX>): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.whereId(it))
        }

        override fun whereRef(it: Iterable<Ref<TX>>): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.whereRef(it))
        }

        override fun whereAnyRef(it: Iterable<Ref<out Record>>): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.whereAnyRef(it))
        }

        override fun where(it: Iterable<TX>): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.where(it))
        }

        override fun whereAny(it: Iterable<Record>): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.whereAny(it))
        }

        override fun <V : Record> where(
            path: Metamodel<TX, V>,
            ref: Ref<V>
        ): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.where<V>(path, ref))
        }

        override fun <V : Record> whereAny(
            path: Metamodel<*, V>,
            ref: Ref<V>
        ): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.whereAny<V>(path, ref))
        }

        override fun <V : Record> whereRef(
            path: Metamodel<TX, V>,
            it: Iterable<Ref<V>>
        ): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.whereRef<V>(path, it))
        }

        override fun <V : Record> whereAnyRef(
            path: Metamodel<*, V>,
            it: Iterable<Ref<V>>
        ): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.whereAnyRef<V>(path, it))
        }

        override fun <V> where(
            path: Metamodel<TX, V>,
            operator: Operator,
            it: Iterable<V>
        ): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.where<V>(path, operator, it))
        }

        override fun <V> whereAny(
            path: Metamodel<*, V>,
            operator: Operator,
            it: Iterable<V>
        ): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.whereAny<V>(path, operator, it))
        }

        override fun where(template: TemplateString): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.where((template as TemplateStringHolder).templateString))
        }

        override fun <V> whereAny(
            path: Metamodel<*, V>,
            operator: Operator,
            vararg o: V
        ): PredicateBuilder<TX, RX, IDX> {
            return PredicateBuilderImpl<TX, RX, IDX>(core.whereAny(path, operator, *o))
        }
    }

    /**
     * Adds a WHERE clause to the query using a [WhereBuilder].
     *
     * @param predicate the predicate to add.
     * @return the query builder.
     */
    override fun whereBuilder(predicate: (WhereBuilder<T, R, ID>) -> PredicateBuilder<T, *, *>): QueryBuilder<T, R, ID> {
        return QueryBuilderImpl(core.where { whereBuilder ->
            val builder = predicate(WhereBuilderImpl(whereBuilder))
            (builder as PredicateBuilderImpl<T, *, *>).core
        })
    }

    /**
     * Adds a WHERE clause to the query using a [WhereBuilder].
     *
     * @param predicate the predicate to add.
     * @return the query builder.
     */
    override fun whereAnyBuilder(predicate: (WhereBuilder<T, R, ID>) -> PredicateBuilder<*, *, *>): QueryBuilder<T, R, ID> {
        return QueryBuilderImpl(core.whereAny { whereBuilder ->
            val builder = predicate(WhereBuilderImpl(whereBuilder))
            (builder as PredicateBuilderImpl<*, *, *>).core
        })
    }

    /**
     * Adds a LIMIT clause to the query.
     *
     * @param limit the maximum number of records to return.
     * @return the query builder.
     * @since 1.2
     */
    override fun limit(limit: Int): QueryBuilder<T, R, ID> {
        return QueryBuilderImpl<T, R, ID>(core.limit(limit))
    }

    /**
     * Adds an OFFSET clause to the query.
     *
     * @param offset the offset.
     * @return the query builder.
     * @since 1.2
     */
    override fun offset(offset: Int): QueryBuilder<T, R, ID> {
        return QueryBuilderImpl<T, R, ID>(core.offset(offset))
    }

    override fun getSubquery(): st.orm.core.template.TemplateString {
        return (core as Subqueryable).subquery
    }
}
