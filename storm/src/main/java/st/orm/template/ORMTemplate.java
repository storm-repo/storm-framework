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

import st.orm.repository.RepositoryLookup;

/**
 * <p>The {@link ORMTemplate} supports a fluent API that allows you to build queries in a more concise manner:
 *
 * <pre>{@code
 * DataSource dataSource = ...;
 * List<User> users = Templates.ORM(dataSource).entity(User.class)
 *     .select()
 *     .where("city", Operator.EQUALS, "Sunnyvale")
 *     .getResultList();
 * }</pre>
 *
 * <p>In this example, the query is constructed by chaining method calls, enhancing readability and reducing boilerplate code.
 * The {@code entity(Class<T> clazz)} method specifies the entity type for the query, and subsequent methods like {@code select()}
 * and {@code where()} build upon it.
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li><strong>Type Safety:</strong> Leverages Java generics and type inference to ensure that queries are type-safe.</li>
 *   <li><strong>Fluent API:</strong> Supports method chaining to build queries in a readable and concise manner.</li>
 *   <li><strong>Flexibility:</strong> Compatible with both JPA and JDBC, allowing you to choose the underlying technology.</li>
 *   <li><strong>SQL Templates:</strong> Integrates with SQL string templates for dynamic query construction.</li>
 *   <li><strong>Ease of Use:</strong> Reduces boilerplate code and simplifies common database operations.</li>
 * </ul>
 *
 * <h2>Creating ORMTemplate Instances</h2>
 *
 * <p>The {@code Templates} interface provides static methods to create {@link ORMTemplate} instances based on your data source:
 *
 * <h3>Using EntityManager (JPA)</h3>
 * <pre>{@code
 * EntityManager entityManager = ...;
 * ORMTemplate orm = Templates.ORM(entityManager);
 * }</pre>
 *
 * <h3>Using DataSource (JDBC)</h3>
 * <pre>{@code
 * DataSource dataSource = ...;
 * ORMTemplate orm = Templates.ORM(dataSource);
 * }</pre>
 *
 * <h3>Using Connection (JDBC)</h3>
 *
 * <p><strong>Note:</strong> The caller is responsible for closing the connection after usage.
 *
 * <pre>{@code
 * Connection connection = ...;
 * ORMTemplate orm = Templates.ORM(connection);
 * }</pre>
 *
 * <h2>Example: Querying with Fluent API</h2>
 * <p>Here is a more detailed example demonstrating how to use the fluent API to perform a query:
 * <pre>{@code
 * DataSource dataSource = ...;
 * List<User> users = ORM(dataSource).entity(User.class)
 *     .select()
 *     .where("city.name", Operator.EQUALS, "Sunnyvale")
 *     .getResultList();
 * }</pre>
 *
 * <p>In this example:
 * <ul>
 *   <li>{@code entity(User.class)} specifies the entity to query.</li>
 *   <li>{@code select()} constructs the SELECT clause.</li>
 *   <li>{@code where("city", Operator.EQUALS, "Sunnyvale")} adds a WHERE condition.</li>
 *   <li>{@code getResultList()} executes the query and returns the results as a list.</li>
 * </ul>
 *
 * <h2>Integration with SQL Templates</h2>
 * <p>You can seamlessly integrate the fluent API with SQL templates when needed. For instance, you can combine method chaining with template-based queries:
 *
 * <pre>{@code
 * City city = ORM(dataSource).entity(City.class)
 *     .select()
 *     .where(RAW."name = \{"Sunnyvale"}")
 *     .getSingleResult();
 * List<User> users = ORM(dataSource).query(RAW."""
 *         SELECT \{User.class}
 *         FROM \{User.class}
 *         WHERE \{city)}""")
 *     .getResultList(User.class);
 * }</pre>
 *
 * <p>The {@code Templates} interface streamlines the process of writing database queries by integrating
 * object-relational mapping with SQL string templates and a fluent API, ensuring type safety and reducing boilerplate code.
 * Whether you prefer constructing queries using SQL templates or method chaining, the {@code Templates} interface provides
 * flexible options to suit your development style.
 *
 * @see st.orm.Templates
 */
public interface ORMTemplate extends QueryTemplate, RepositoryLookup {
}
