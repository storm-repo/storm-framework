/*
 * Copyright 2024 - 2026 the original author or authors.
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
package st.orm.core.template;

import jakarta.annotation.Nonnull;
import st.orm.Data;

/**
 * A compiled query plan backed by a pre-processed template.
 *
 * <p>A plan is created via {@link QueryTemplate#plan(TemplateString)}. Template processing runs once at plan
 * creation; executions skip the per-call template processing that {@link QueryTemplate#query(TemplateString)}
 * performs. Plans come in two forms:</p>
 * <ul>
 *   <li><strong>Record-bound</strong>: the template expresses its variable parts as bind variables (see
 *       {@link QueryTemplate#createBindVars()}); each {@link #bind(Data)} call extracts the record's parameter
 *       values and returns a regular {@link Query} backed by the already generated statement.</li>
 *   <li><strong>Constant</strong>: the template has no parameters at all, such as an unfiltered select or count;
 *       {@link #query()} returns a query over the fixed statement.</li>
 * </ul>
 *
 * <p>Plans are immutable, thread-safe, and independent of any connection: the query returned by {@code bind} acquires
 * a connection on execution like any other query. Registered {@code SqlInterceptor} instances observe every bound
 * statement. A plan is pinned to the SQL template configuration it was compiled with: scoped template customizations
 * that start after compilation do not affect the plan's statement.</p>
 *
 * @since 1.13
 */
public interface QueryPlan {

    /**
     * Binds the given record's values against the plan's statement and returns an executable query.
     *
     * @param record the record supplying the values for the plan's bind variables.
     * @return a query bound to the record's values.
     * @throws st.orm.PersistenceException if this plan is constant, or the record does not match the plan's bind
     *                                     variables.
     */
    Query bind(@Nonnull Data record);

    /**
     * Binds the given primary key against the plan's statement and returns an executable query.
     *
     * <p>Id binding is available when the plan's bind variables consist solely of a WHERE clause matching the
     * primary key, such as a find, exists, or delete by id. Composite primary keys bind their id record's
     * constituent values.</p>
     *
     * @param id the primary key supplying the values for the plan's bind variables.
     * @return a query bound to the primary key.
     * @throws st.orm.PersistenceException if this plan is constant, or its bind variables are not purely
     *                                     primary-key based.
     * @since 1.13
     */
    Query bindId(@Nonnull Object id);

    /**
     * Returns an executable query for a constant plan.
     *
     * <p>Constant plans are compiled from templates without bind variables and carry no per-call values; each call
     * returns a fresh query over the plan's statement. Plans with bind variables bind records via
     * {@link #bind(Data)} instead.</p>
     *
     * @return a query over the plan's statement.
     * @throws st.orm.PersistenceException if this plan has bind variables.
     * @since 1.13
     */
    Query query();
}
