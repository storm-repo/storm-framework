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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Projection records marked with this annotation type can specify a SQL query that represents the projection.
 *
 * <p>Usage examples:
 *
 * <p>Define a projection record with a SQL query:
 * <pre>{@code
 * @ProjectionQuery("""
 *     SELECT b.id, COUNT(*) AS item_count, SUM(price) AS total_price
 *     FROM basket b
 *       LEFT JOIN basket_item bi ON b.id = bi.basket_id
 *     GROUP BY b.id""")
 * record BasketSummary(@PK @FK("id") Basket basket, int itemCount, BigDecimal totalPrice) implements Projection<Integer> {}
 * }</pre>
 *
 * <p>Then, you can use the projection in a query like this:
 * <pre>{@code
 * var baskets = ...
 * List<BasketSummary> summaries = ORM(dataSource).projection(BasketSummary.class)
 *     .select()
 *     .where(baskets)
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
 *     .where("basketSummary.itemCount", GREATER_THAN, 0)
 *     .getResultList();
 * }</pre>
 */
@Target({TYPE})
@Retention(RUNTIME)
public @interface ProjectionQuery {

    /**
     * The SQL query that represents the projection.
     */
    String value();
}
