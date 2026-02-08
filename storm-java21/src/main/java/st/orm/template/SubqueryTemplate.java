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
package st.orm.template;

import jakarta.annotation.Nonnull;
import st.orm.Data;

import static java.lang.StringTemplate.RAW;
import static st.orm.SelectMode.DECLARED;
import static st.orm.template.Templates.select;

/**
 * Provides factory methods for constructing subqueries that can be correlated with or embedded in an outer query.
 *
 * <p>Unlike regular queries created by {@link QueryTemplate}, subqueries only select fields from the primary table;
 * fields from nested (foreign key) records are not included. Subqueries cannot be directly executed -- they must be
 * passed as a {@link QueryBuilder} object to the outer query (e.g., via
 * {@link WhereBuilder#exists(QueryBuilder)} or {@link Templates#subquery(QueryBuilder, boolean)}).</p>
 *
 * <h2>Example: EXISTS subquery</h2>
 * <pre>{@code
 * List<User> usersWithOrders = userRepository
 *         .select()
 *         .where(predicate -> predicate.exists(
 *             predicate.subquery(Order.class)
 *                 .where(Order_.userId, EQUALS, User_.id)))
 *         .getResultList();
 * }</pre>
 *
 * @since 1.1
 * @see QueryTemplate
 * @see WhereBuilder#exists(QueryBuilder)
 */
public interface SubqueryTemplate {

    /**
     * Create a subquery for the given table.
     *
     * @param fromType the table to create the subquery for.
     * @param <T> the table type to select from.
     * @return the subquery builder.
     */
    default <T extends Data> QueryBuilder<T, ?, ?> subquery(@Nonnull Class<T> fromType) {
        return subquery(fromType, fromType);
    }

    /**
     * Create a subquery for the given table and select type.
     *
     * @param fromType the table to create the subquery for.
     * @param selectType the type to select.
     * @return the subquery builder.
     * @param <T> the table type to select from.
     * @param <R> the result type.
     */
    default <T extends Data, R extends Data> QueryBuilder<T, ?, ?> subquery(@Nonnull Class<T> fromType,
                                                                            @Nonnull Class<R> selectType) {
        return subquery(fromType, RAW."\{select(selectType, DECLARED)}");
    }

    /**
     * Create a subquery for the given table and select type using the given {@code template}.
     *
     * @param fromType the table to create the subquery for.
     * @param template the select clause template.
     * @return the subquery builder.
     * @param <T> the table type to select from.
     */
    <T extends Data> QueryBuilder<T, ?, ?> subquery(@Nonnull Class<T> fromType,
                                                    @Nonnull StringTemplate template);
}
