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
package st.orm.repository;

/**
 * Marker interface for record-based projections.
 *
 * <p>This interface is leveraged by {@link ProjectionRepository} to provide built-in read operations.</p>
 *
 * <p>Projections represent read-only data structures, such as database views or tables defined by custom
 * SQL queries using the {@link ProjectionQuery} annotation.</p>
 *
 * <p>Usage examples:
 *
 * <p>Define a projection record based on the {@code basket_summary_view} view, with a {@code basket_id} primary
 * key.
 * <pre>{@code
 * @DbTable("basket_summary_view")
 * record BasketSummary(@PK @FK Basket basket, int itemCount, BigDecimal totalPrice) implements Projection<Integer> {}
 * }</pre>
 *
 * <p>Then, you can use the projection in a query like this:
 * <pre>{@code
 * var baskets = ...
 * List<BasketSummary> summaries = ORM(dataSource).projection(BasketSummary.class)
 *     .select()
 *     .where(baskets)  // Type-safe.
 *     .getResultList();
 * }</pre>
 *
 * <p>Or use it as a foreign key in an entity:
 * <pre>{@code
 * record User(@PK int id, @FK("basket_id") BasketSummary basketSummary) implements Entity<Integer> {}
 * }</pre>
 *
 * <p>Then, you can query all users having a basket with at least 1 item:</p>
 * <pre>{@code
 * List<User> users = ORM(dataSource).projection(User.class)
 *     .select()
 *     .where(User_.basketSummary.itemCount, GREATER_THAN, 0)   // Type-safe metamodel.
 *     .getResultList();
 * }</pre>
 *
 * @see ProjectionRepository
 * @see ProjectionQuery
 * @param <ID> the type of the projection's primary key, or Void if the projection has no primary key.
 */
public interface Projection<ID> {
}