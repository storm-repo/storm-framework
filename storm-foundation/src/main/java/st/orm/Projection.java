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
package st.orm;

/**
 * Marker interface for record-based projections.
 *
 * <p>Usage examples:
 *
 * <p>Define a projection record based on the {@code basket_summary_view} view, with a {@code basket_id} primary
 * key.
 *
 * <p>Java:
 * <pre>{@code
 * @DbTable("basket_summary_view")
 * record BasketSummary(@PK @FK Basket basket, int itemCount, BigDecimal totalPrice) implements Projection<Integer> {}
 * }</pre>
 *
 * <p>Kotlin:
 * <pre>{@code
 * @DbTable("basket_summary_view")
 * data class BasketSummary(@PK @FK val basket: Basket, val itemCount: Int, val totalPrice: BigDecimal) : Projection<Int>
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
 * <p>Or use it as a foreign key in an entity.
 *
 * <p>Java:
 * <pre>{@code
 * record User(@PK int id, @FK("basket_id") BasketSummary basketSummary) implements Entity<Integer> {}
 * }</pre>
 *
 * <p>Kotlin:
 * <pre>{@code
 * data class User(@PK val id: Int, @FK("basket_id") val basketSummary: BasketSummary) : Entity<Int>
 * }</pre>
 *
 * <p>Then, you can query all users having a basket with at least 1 item:</p>
 * <pre>{@code
 * List<User> users = ORM(dataSource).entity(User.class)
 *     .select()
 *     .where(User_.basketSummary.itemCount, GREATER_THAN, 0)   // Type-safe metamodel.
 *     .getResultList();
 * }</pre>
 *
 * <h2>The {@code ID} parameter</h2>
 *
 * <p>{@code ID} is the projection's row identity type: the type the id-based operations work with, such as
 * {@code ProjectionRepository.findById(ID)}, {@code ProjectionRepository.ref(ID)} and
 * {@link Ref#projectionId(Ref)}. When the primary key component is a foreign key, the row identity is the
 * referenced table's key rather than the component value: {@code BasketSummary} above is identified by the
 * basket's {@code Integer} key, while its primary key component holds a {@code Basket}. Declare
 * {@code Projection<Void>} for a projection without a primary key; the id-based operations do not apply to such
 * projections.</p>
 *
 * <p>Unlike {@link Entity#id()}, this interface deliberately declares no id accessor: a projection's row
 * identity is not in general derivable from its components. It may differ in type from the primary key
 * component, as shown above, or the identity may not be among the mapped columns at all. Operations that need
 * the id of a projection instance therefore take it explicitly, as in {@link Ref#of(Projection, Object)}.</p>
 *
 * <p>The declared type argument is validated against the mapped primary key when the projection is used: a
 * projection that maps a primary key must not declare {@code Void}, and the declared type must match the key's
 * row identity type. A projection without a mapped primary key may declare a row identity type; this supports
 * detached refs via {@link Ref#of(Class, Object)}, while the id-based repository operations require the mapped
 * key.</p>
 *
 * @param <ID> the row identity type of the projection, or Void if the projection has no primary key.
 */
public interface Projection<ID> extends Data {
}
