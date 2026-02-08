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

/**
 * A builder for specifying the ON condition of a JOIN clause using a custom string template expression.
 *
 * <p>{@code JoinBuilder} is returned by the join methods on {@link QueryBuilder} that accept a {@link StringTemplate}
 * or a subquery. It provides a single {@link #on(StringTemplate)} method to specify the join condition.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * List<User> users = userRepository
 *         .select()
 *         .innerJoin(RAW."\{Order.class}", "o")
 *             .on(RAW."\{User_.id} = o.user_id")
 *         .getResultList();
 * }</pre>
 *
 * @param <T>  the type of the table being queried.
 * @param <R>  the type of the result.
 * @param <ID> the type of the primary key.
 * @see TypedJoinBuilder
 * @see QueryBuilder
 */
public interface JoinBuilder<T extends Data, R, ID> {

    /**
     * Specifies the join condition using a custom expression.
     *
     * @param template the condition to join on.
     * @return the query builder.
     */
    QueryBuilder<T, R, ID> on(@Nonnull StringTemplate template);
}
