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
package st.orm.kotlin;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import kotlin.reflect.KClass;
import st.orm.BindVars;
import st.orm.TemporalType;
import st.orm.kotlin.template.KORMTemplate;
import st.orm.kotlin.template.KQueryBuilder;
import st.orm.kotlin.template.KQueryTemplate;
import st.orm.kotlin.template.impl.KORMTemplateImpl;
import st.orm.kotlin.template.impl.KQueryTemplateImpl;
import st.orm.template.Metamodel;
import st.orm.template.ORMTemplate;
import st.orm.template.Operator;
import st.orm.template.QueryTemplate;
import st.orm.template.ResolveScope;
import st.orm.template.TemplateFunction;
import st.orm.template.impl.Element;
import st.orm.template.impl.Elements;
import st.orm.template.impl.Elements.Column;
import st.orm.template.impl.Elements.ObjectExpression;
import st.orm.template.impl.Elements.Subquery;
import st.orm.template.impl.Elements.TableSource;
import st.orm.template.impl.Elements.TemplateSource;
import st.orm.template.impl.Elements.Unsafe;
import st.orm.template.impl.JpaTemplateImpl;
import st.orm.template.impl.PreparedStatementTemplateImpl;
import st.orm.template.impl.Subqueryable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static st.orm.spi.Providers.getORMReflection;
import static st.orm.template.Operator.IN;
import static st.orm.template.ResolveScope.CASCADE;
import static st.orm.template.TemplateFunction.template;
import static st.orm.template.impl.Elements.Alias;
import static st.orm.template.impl.Elements.Delete;
import static st.orm.template.impl.Elements.From;
import static st.orm.template.impl.Elements.Insert;
import static st.orm.template.impl.Elements.Param;
import static st.orm.template.impl.Elements.Select;
import static st.orm.template.impl.Elements.Set;
import static st.orm.template.impl.Elements.Table;
import static st.orm.template.impl.Elements.Values;
import static st.orm.template.impl.Elements.Where;

/**
 * The {@code Templates} interface provides a collection of static methods for constructing SQL query elements
 * and creating ORM repository templates. It serves as a central point for building SQL queries and interacting
 * with databases in a type-safe and fluent manner, supporting both JPA and JDBC.
 *
 * <p>This interface includes methods for generating SQL clauses such as {@code SELECT}, {@code FROM}, {@code WHERE},
 * {@code INSERT}, {@code UPDATE}, and {@code DELETE}, as well as utility methods for working with parameters,
 * tables, aliases, and more.
 *
 * <p>Additionally, the {@code Templates} interface provides methods to create {@link KORMTemplate}
 * instances for use with different data sources like JPA's {@link EntityManager}, JDBC's {@link DataSource}, or
 * {@link Connection}. These repository templates facilitate database operations using the constructed SQL queries.
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Using JPA</h3>
 *
 * <p>Example of querying a database using JPA:
 *
 * <pre>{@code
 * EntityManager entityManager = ...;
 * KORMRepositoryTemplate orm = KTemplates.ORM(entityManager);
 * List<User> users = orm.query(RAW."""
 *         SELECT \{User.class}
 *         FROM \{User.class}
 *         WHERE city_id = \{1}""")
 *     .getResultList(User.class);
 * }</pre>
 *
 * <h3>Using JDBC</h3>
 *
 * <p>Example of querying a database using JDBC:
 *
 * <pre>{@code
 * DataSource dataSource = ...;
 * KORMRepositoryTemplate orm = KTemplates.ORM(dataSource);
 * List<User> users = orm.query(RAW."""
 *         SELECT \{User.class}
 *         FROM \{User.class}
 *         WHERE city_id = \{1}""")
 *     .getResultList(User.class);
 * }</pre>
 *
 * <h3>Fluent API Usage</h3>
 *
 * <p>The {@link KORMTemplate} also supports a fluent API that allows you to build queries in a more concise manner:
 *
 * <pre>{@code
 * DataSource dataSource = ...;
 * List<User> users = KTemplates.ORM(dataSource).entity(User.class)
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
 * <h2>Creating KORMRepositoryTemplate Instances</h2>
 *
 * <p>The {@code KTemplates} interface provides static methods to create {@link KORMTemplate} instances based on your data source:
 *
 * <h3>Using EntityManager (JPA)</h3>
 * <pre>{@code
 * EntityManager entityManager = ...;
 * KORMRepositoryTemplate orm = Kemplates.ORM(entityManager);
 * }</pre>
 *
 * <h3>Using DataSource (JDBC)</h3>
 * <pre>{@code
 * DataSource dataSource = ...;
 * KORMRepositoryTemplate orm = KTemplates.ORM(dataSource);
 * }</pre>
 *
 * <h3>Using Connection (JDBC)</h3>
 *
 * <p><strong>Note:</strong> The caller is responsible for closing the connection after usage.
 *
 * <pre>{@code
 * Connection connection = ...;
 * ORMRepositoryTemplate orm = KTemplates.KORM(connection);
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
 * <h2>Conclusion</h2>
 *
 * <p>The {@code KTemplates} interface streamlines the process of writing database queries by integrating
 * object-relational mapping with SQL string templates and a fluent API, ensuring type safety and reducing boilerplate code.
 * Whether you prefer constructing queries using SQL templates or method chaining, the {@code KTemplates} interface provides
 * flexible options to suit your development style.
 */
public interface KTemplates {

    /**
     * Converts an {@link QueryTemplate} to a {@link KQueryTemplate}.
     *
     * @param orm the {@link QueryTemplate} to convert.
     * @return a {@link KQueryTemplate} instance.
     */
    static KQueryTemplate ORM(@Nonnull QueryTemplate orm) {
        return new KQueryTemplateImpl(orm);
    }

    /**
     * Converts an {@link ORMTemplate} to a {@link KORMTemplate}.
     *
     * @param orm the {@link ORMTemplate} to convert.
     * @return a {@link KORMTemplate} instance.
     */
    static KORMTemplate ORM(@Nonnull ORMTemplate orm) {
        return new KORMTemplateImpl(orm);
    }

    /**
     * Returns an {@link KORMTemplate} for use with JPA.
     *
     * <p>This method creates an ORM repository template using the provided {@link EntityManager}.
     * It allows you to perform database operations using JPA in a fluent and type-safe manner.
     *
     * <p>Example usage:
     * <pre>{@code
     * EntityManager entityManager = ...;
     * KORMRepositoryTemplate orm = KTemplates.ORM(entityManager);
     * List<User> users = orm.query(RAW."""
     *         SELECT \{User.class}
     *         FROM \{User.class}
     *         WHERE city = \{"Sunnyvale"}""")
     *     .getResultList(User.class);
     * }</pre>
     *
     * @param entityManager the {@link EntityManager} to use for database operations; must not be {@code null}.
     * @return an {@link KORMTemplate} configured for use with JPA.
     */
    static KORMTemplate ORM(@Nonnull EntityManager entityManager) {
        return ORM(new JpaTemplateImpl(entityManager).toORM());
    }

    /**
     * Returns an {@link KORMTemplate} for use with JDBC.
     *
     * <p>This method creates an ORM repository template using the provided {@link DataSource}.
     * It allows you to perform database operations using JDBC in a fluent and type-safe manner.
     *
     * <p>Example usage:
     * <pre>{@code
     * DataSource dataSource = ...;
     * KORMRepositoryTemplate orm = KTemplates.ORM(dataSource);
     * List<User> users = orm.query(RAW."""
     *         SELECT \{User.class}
     *         FROM \{User.class}
     *         WHERE city = \{"Sunnyvale"}""")
     *     .getResultList(User.class);
     * }</pre>
     *
     * @param dataSource the {@link DataSource} to use for database operations; must not be {@code null}.
     * @return an {@link ORMTemplate} configured for use with JDBC.
     */
    static KORMTemplate ORM(@Nonnull DataSource dataSource) {
        return ORM(new PreparedStatementTemplateImpl(dataSource).toORM());
    }

    /**
     * Returns an {@link KORMTemplate} for use with JDBC.
     *
     * <p>This method creates an ORM repository template using the provided {@link Connection}.
     * It allows you to perform database operations using JDBC in a fluent and type-safe manner.
     * <strong>Note:</strong> The caller is responsible for closing the connection after usage.
     *
     * <p>Example usage:
     * <pre>{@code
     * try (Connection connection = ...) {
     *     KORMRepositoryTemplate orm = KTemplates.ORM(connection);
     *     List<User> users = orm.query(RAW."""
     *             SELECT \{User.class}
     *             FROM \{User.class}
     *             WHERE city = \{"Sunnyvale"}""")
     *         .getResultList(User.class)
     * }
     * }</pre>
     *
     * @param connection the {@link Connection} to use for database operations; must not be {@code null}.
     * @return an {@link ORMTemplate} configured for use with JDBC.
     */
    static KORMTemplate ORM(@Nonnull Connection connection) {
        return ORM(new PreparedStatementTemplateImpl(connection).toORM());
    }

    /**
     * Generates a SELECT element for the specified table class.
     *
     * <p>This method creates a {@code SELECT} clause for the provided table record, including all of its
     * columns as well as columns from any foreign key relationships defined within the record. It is
     * designed to be used within SQL string templates to dynamically construct queries based on the
     * table's structure.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT \{select(Table.class)}
     * FROM \{from(Table.class)}
     * }</pre>
     *
     * <p>For convenience, you can also use the shorthand notation. The SQL template engine
     * automatically detects that a SELECT element is required based on its placement in the query:
     * <pre>{@code
     * SELECT \{Table.class}
     * FROM \{Table.class}
     * }</pre>
     *
     * @param table the {@link Class} object representing the table record.
     * @return an {@link Element} representing the SELECT clause for the specified table.
     */
    static Element select(KClass<? extends Record> table) {
        return select(table, true);
    }

    /**
     * Generates a SELECT element for the specified table class.
     *
     * <p>This method creates a {@code SELECT} clause for the provided table record, including all of its
     * columns as well as columns from any foreign key relationships defined within the record. It is
     * designed to be used within SQL string templates to dynamically construct queries based on the
     * table's structure.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT \{select(Table.class)}
     * FROM \{from(Table.class)}
     * }</pre>
     *
     * <p>For convenience, you can also use the shorthand notation. The SQL template engine
     * automatically detects that a SELECT element is required based on its placement in the query:
     * <pre>{@code
     * SELECT \{Table.class}
     * FROM \{Table.class}
     * }</pre>
     *
     * @param table the {@link Class} object representing the table record.
     * @param nested if {@code true}, include columns from foreign key relationships.
     * @return an {@link Element} representing the SELECT clause for the specified table.
     */
    static Element select(KClass<? extends Record> table, boolean nested) {
        return new Select(getORMReflection().getRecordType(table), nested);
    }

    /**
     * Generates a FROM element for the specified table class without an alias and optional auto-joining of foreign
     * keys.
     *
     * <p>This method creates a {@code FROM} clause for the provided table record. If {@code autoJoin} is set
     * to {@code true}, it will automatically include JOIN clauses for all foreign keys defined in the record.
     * This facilitates constructing complex queries that involve related tables without manually specifying each join.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT \{select(Table.class)}
     * FROM \{from(Table.class, true)}
     * }</pre>
     *
     * <p>For convenience, you can also use the shorthand notation. In this case, {@code autoJoin}
     * defaults to {@code false}. The SQL template engine automatically detects that a FROM element is required
     * based on its placement in the query:
     * <pre>{@code
     * SELECT \{Table.class}
     * FROM \{Table.class}
     * }</pre>
     *
     * @param table the {@link Class} object representing the table record.
     * @param autoJoin if {@code true}, automatically join all foreign keys listed in the record.
     * @return an {@link Element} representing the FROM clause for the specified table.
     */
    static Element from(@Nonnull KClass<? extends Record> table, boolean autoJoin) {
        return new From(getORMReflection().getRecordType(table), autoJoin);
    }

    /**
     * Generates a FROM element for the specified table class, with an alias and optional auto-joining of foreign keys.
     *
     * <p>This method creates a {@code FROM} clause for the provided table record, applying the specified alias.
     * If {@code autoJoin} is set to {@code true}, it will automatically include JOIN clauses for all foreign keys
     * defined in the record. This facilitates constructing complex queries that involve related tables without
     * manually specifying each join.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT \{select(Table.class)}
     * FROM \{from(Table.class, "t", true)}
     * }</pre>
     *
     * @param table the {@link Class} object representing the table record.
     * @param alias the alias to use for the table in the query.
     * @param autoJoin if {@code true}, automatically join all foreign keys listed in the record.
     * @return an {@link Element} representing the FROM clause for the specified table.
     */
    static Element from(@Nonnull KClass<? extends Record> table, @Nonnull String alias, boolean autoJoin) {
        return new From(new TableSource(getORMReflection().getRecordType(table)), requireNonNull(alias, "alias"), autoJoin);
    }

    /**
     * Generates a FROM element using a provided SQL string template with an alias.
     *
     * <p>This method allows you to specify a custom {@link StringTemplate} to be used as the source in the {@code FROM}
     * clause, applying the provided alias. This is useful when you need to include subqueries or complex table
     * expressions in your SQL queries.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT \{select(Table.class)}
     * FROM \{from(RAW."SELECT column_a, column_b FROM table", "t")}
     * }</pre>
     *
     * <p>Note that in this context, the alias is mandatory and auto-joining of foreign keys is not applicable.
     *
     * @param template the {@link StringTemplate} representing the custom SQL to be used in the FROM clause.
     * @param alias the alias to assign to the template in the query.
     * @return an {@link Element} representing the FROM clause with the specified template and alias.
     */
    static Element from(@Nonnull StringTemplate template, @Nonnull String alias) {
        return new From(new TemplateSource(template), requireNonNull(alias, "alias"), false);
    }

    /**
     * Generates a FROM element using a provided SQL string template with an alias.
     *
     * <p>This method allows you to specify a custom {@link StringTemplate} to be used as the source in the {@code FROM}
     * clause, applying the provided alias. This is useful when you need to include subqueries or complex table
     * expressions in your SQL queries.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT \{select(Table.class)}
     * FROM \{from(RAW."SELECT column_a, column_b FROM table", "t")}
     * }</pre>
     *
     * <p>Note that in this context, the alias is mandatory and auto-joining of foreign keys is not applicable.
     *
     * @param function used to define the string template to be used in the FROM clause.
     * @param alias the alias to assign to the template in the query.
     * @return an {@link Element} representing the FROM clause with the specified template and alias.
     */
    static Element from(@Nonnull TemplateFunction function, @Nonnull String alias) {
        return from(template(function), alias);
    }

    /**
     * Generates an INSERT element for the specified table class.
     *
     * <p>This method creates an {@code INSERT} clause for the provided table record. It is designed to be used
     * within SQL string templates to dynamically construct INSERT queries based on the table's structure.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * INSERT INTO \{insert(Table.class)}
     * VALUES \{values(entity)}
     * }</pre>
     *
     * <p>For convenience, you can also use the shorthand notation. The SQL template engine automatically detects that
     * an INSERT element is required based on its placement in the query:
     * <pre>{@code
     * INSERT INTO \{Table.class}
     * VALUES \{entity}
     * }</pre>
     *
     * <p>Here, {@code entity} is an instance of the {@code Table} class containing the values to be inserted.
     *
     * @param table the {@link Class} object representing the table record.
     * @return an {@link Element} representing the INSERT clause for the specified table.
     */
    static Element insert(@Nonnull KClass<? extends Record> table) {
        return new Insert(getORMReflection().getRecordType(table));
    }

    /**
     * Generates a VALUES clause for the specified record instance(s).
     *
     * <p>This method creates a {@code VALUES} clause using the provided {@link Record} instance(s).
     * It is intended to be used within SQL string templates to dynamically construct INSERT statements with
     * the given values.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * INSERT INTO \{Table.class}
     * VALUES \{values(entity1, entity2)}
     * }</pre>
     *
     * <p>For convenience, you can also use the shorthand notation. The SQL template engine automatically
     * detects that a VALUES element is required based on its placement in the query:
     * <pre>{@code
     * INSERT INTO \{Table.class}
     * VALUES \{new Record[] {entity1, entity2}}
     * }</pre>
     *
     * <p>Here, {@code entity1}, {@code entity2}, etc., are instances of the {@code Record} class containing
     * the values to be inserted.
     *
     * @param r one or more {@link Record} instances containing the values to be inserted.
     * @return an {@link Element} representing the VALUES clause with the specified records.
     */
    static Element values(@Nonnull Record... r) {
        return new Values(Arrays.asList(r), null);
    }

    /**
     * Generates a VALUES clause for the specified iterable of record instances.
     *
     * <p>This method creates a {@code VALUES} clause using the provided {@link Iterable} of {@link Record} instances.
     * It is intended to be used within SQL string templates to dynamically construct INSERT statements with
     * the given values.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * INSERT INTO \{Table.class}
     * VALUES \{values(records)}
     * }</pre>
     *
     * <p>For convenience, you can also use the shorthand notation. The SQL template engine automatically
     * detects that a VALUES element is required based on its placement in the query:
     * <pre>{@code
     * INSERT INTO \{Table.class}
     * VALUES \{records}
     * }</pre>
     *
     * <p>Here, {@code records} is an {@link Iterable} of {@code Record} instances containing
     * the values to be inserted.
     *
     * @param records an {@link Iterable} of {@link Record} instances containing the values to be inserted.
     * @return an {@link Element} representing the VALUES clause with the specified records.
     */
    static Element values(@Nonnull Iterable<? extends Record> records) {
        return new Values(records, null);
    }

    /**
     * Generates a VALUES clause using the specified {@link BindVars} for batch insertion.
     *
     * <p>This method creates a {@code VALUES} clause that utilizes a {@link BindVars} instance, allowing for batch
     * insertion of records using bind variables. This is particularly useful when performing batch operations where
     * the same query is executed multiple times with different variable values.
     *
     * <p>Example usage in a batch insertion scenario:
     * <pre>{@code
     * var bindVars = orm.createBindVars();
     * try (var query = orm.query(RAW."""
     *         INSERT INTO \{Table.class}
     *         VALUES \{values(bindVars)}""").prepare()) {
     *     records.forEach(query::addBatch);
     *     query.executeBatch();
     * }
     * }</pre>
     *
     * <p>For convenience, you can also use the shorthand notation. The SQL template engine automatically
     * detects that a VALUES element is required based on its placement in the query:
     * <pre>{@code
     * INSERT INTO \{Table.class}
     * VALUES \{bindVars}
     * }</pre>
     *
     * <p>In this example, {@code bindVars} is a {@link BindVars} instance created by the ORM. The {@code records} are
     * iterated over, and each is added to the batch. The query is then executed as a batch operation.
     *
     * @param bindVars the {@link BindVars} instance used for batch insertion.
     * @return an {@link Element} representing the VALUES clause utilizing the specified bind variables.
     */
    static Element values(@Nonnull BindVars bindVars) {
        return new Values(null, requireNonNull(bindVars, "bindVars"));
    }

    /**
     * Generates an UPDATE element for the specified table class.
     *
     * <p>This method creates an {@code UPDATE} clause for the provided table record. It is designed to be used
     * within SQL string templates to dynamically construct UPDATE queries based on the table's structure.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * UPDATE \{update(Table.class)}
     * SET \{set(record)}
     * WHERE \{where(record)}
     * }</pre>
     *
     * <p>For convenience, you can also use the shorthand notation. The SQL template engine automatically detects that
     * an UPDATE element is required based on its placement in the query:
     * <pre>{@code
     * UPDATE \{Table.class}
     * SET \{record}
     * WHERE \{record}
     * }</pre>
     *
     * <p>Here, {@code record} is an instance of the {@code Record} class containing the values to be updated.
     *
     * @param table the {@link Class} object representing the table record.
     * @return an {@link Element} representing the UPDATE clause for the specified table.
     */
    static Element update(@Nonnull KClass<? extends Record> table) {
        return new Elements.Update(getORMReflection().getRecordType(table));
    }

    /**
     * Generates an UPDATE element for the specified table class with an alias.
     *
     * <p>This method creates an {@code UPDATE} clause for the provided table record, applying the specified alias.
     * It is designed to be used within SQL string templates to dynamically construct UPDATE queries based on the
     * table's structure.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * UPDATE \{update(Table.class, "t")}
     * SET \{set(record)}
     * WHERE \{where(record)}
     * }</pre>
     *
     * <p>Here, {@code record} is an instance of the {@code Record} class containing the values to be updated.
     *
     * @param table the {@link Class} object representing the table record.
     * @param alias the alias to use for the table in the query.
     * @return an {@link Element} representing the UPDATE clause for the specified table with alias.
     */
    static Element update(@Nonnull KClass<? extends Record> table, @Nonnull String alias) {
        return new Elements.Update(getORMReflection().getRecordType(table), alias);
    }

    /**
     * Generates a SET clause for the specified record.
     *
     * <p>This method creates a {@code SET} clause using the provided {@link Record} instance. It is intended to be used
     * within SQL string templates to dynamically construct UPDATE statements with the given values.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * UPDATE \{Table.class}
     * SET \{set(record)}
     * WHERE \{where(record)}
     * }</pre>
     *
     * <p>For convenience, you can also use the shorthand notation. The SQL template engine automatically detects
     * that a SET element is required based on its placement in the query:
     * <pre>{@code
     * UPDATE \{Table.class}
     * SET \{record}
     * WHERE \{record}
     * }</pre>
     *
     * <p>Here, {@code record} is an instance of the {@code Record} class containing the values to be set.
     *
     * @param record the {@link Record} instance containing the values to be set.
     * @return an {@link Element} representing the SET clause with the specified record.
     */
    static Element set(@Nonnull Record record) {
        return new Set(requireNonNull(record, "record"), null);
    }

    /**
     * Generates a SET clause using the specified {@link BindVars}.
     *
     * <p>This method creates a {@code SET} clause that utilizes a {@link BindVars} instance, allowing for batch
     * updates using bind variables. This is particularly useful when performing batch operations where the same
     * update query is executed multiple times with different variable values.
     *
     * <p>Example usage in a batch update scenario:
     * <pre>{@code
     * var bindVars = orm.createBindVars();
     * try (var query = orm.query(RAW."""
     *         UPDATE \{Table.class}
     *         SET \{set(bindVars)}
     *         WHERE \{where(bindVars)}""").prepare()) {
     *     records.forEach(query::addBatch);
     *     query.executeBatch();
     * }
     * }</pre>
     *
     * <p>For convenience, you can also use the shorthand notation. The SQL template engine automatically detects that
     * an UPDATE element is required based on its placement in the query:
     * <pre>{@code
     * UPDATE \{Table.class}
     * SET \{record}
     * WHERE \{record}
     * }</pre>
     *
     * <p>In this example, {@code bindVars} is a {@link BindVars} instance created by the ORM. The {@code records} are
     * iterated over, and each is added to the batch. The query is then executed as a batch operation.
     *
     * @param bindVars the {@link BindVars} instance used for batch updates.
     * @return an {@link Element} representing the SET clause utilizing the specified bind variables.
     */
    static Element set(@Nonnull BindVars bindVars) {
        return new Set(null, requireNonNull(bindVars, "bindVars"));
    }

    /**
     * Generates a WHERE clause based on the provided iterable of values or records.
     *
     * <p>This method creates a {@code WHERE} clause that matches the primary key(s) of the root table,
     * a record instance of the root table, or foreign key(s) in the hierarchy against the provided records
     * using the {@code IN} operator. It is useful when you want to select records where the primary key,
     * a specific record, or related foreign keys match any of the values in the iterable.
     *
     * <p>The objects in the iterable can be:
     * <ul>
     *   <li>Primitive values matching the primary key of the root table.</li>
     *   <li>Instances of {@link Record} matching the compound primary key of the root table.</li>
     *   <li>Instances of {@link Record} representing records of related (foreign key) tables in the hierarchy of the
     *   root table.</li>
     * </ul>
     *
     * <p>Example usage with primary key values:
     * <pre>{@code
     * SELECT \{Table.class}
     * FROM \{Table.class}
     * WHERE \{where(listOfIds)}
     * }</pre>
     *
     * <p>Example usage with records:
     * <pre>{@code
     * List<User> users = List.of(user1, user2);
     * SELECT \{Table.class}
     * FROM \{Table.class}
     * WHERE \{where(users)}
     * }</pre>
     *
     * <p>In this example, the query selects all entries in {@code Table} that are linked to any of the users in the
     * list.
     *
     *
     * <p>For convenience, you can also use the shorthand notation. The SQL template engine automatically detects that
     * a WHERE element is required based on its placement in the query:
     * <pre>{@code
     * List<User> users = List.of(user1, user2);
     * SELECT \{Table.class}
     * FROM \{Table.class}
     * WHERE \{users}
     * }</pre>
     *
     * <p>As per the resolution rules:
     * <ul>
     *   <li>If {@code \{users}}, or any other primitive or object, is placed after WHERE the SQL template engine
     *   resolves it into a WHERE element.</li>
     *   <li>If {@code \{users}}, or any other primitive or object, is placed after keywords like VALUES, SET, the SQL
     *   template engine resolves it into the appropriate element (e.g., VALUES element, SET element).</li>
     *   <li>If {@code \{users}} is not in such a placement, it is resolved into a param element.</li>
     * </ul>
     *
     * @param it an {@link Iterable} of values or records to match against the primary key(s) or foreign keys.
     * @return an {@link Element} representing the WHERE clause.
     */
    static Element where(@Nonnull Iterable<?> it) {
        return new Where(new ObjectExpression(it, IN, null), null);
    }

    /**
     * Generates a WHERE clause based on the provided array of values or records.
     *
     * <p>This method creates a {@code WHERE} clause that matches the primary key(s) of the root table,
     * a record instance of the root table, or foreign key(s) in the hierarchy against the provided records
     * using the {@code IN} operator. It is useful when you want to select records where the primary key,
     * a specific record, or related foreign keys match any of the values in the array.
     *
     * <p>The objects can be:
     * <ul>
     *   <li>Primitive values matching the primary key of the root table.</li>
     *   <li>Instances of {@link Record} matching the compound primary key of the root table.</li>
     *   <li>Instances of {@link Record} representing records of related (foreign key) tables in the hierarchy of the
     *   root table.</li>
     * </ul>
     *
     * <p>Example usage with primary key values:
     * <pre>{@code
     * SELECT \{Table.class}
     * FROM \{Table.class}
     * WHERE \{where(id1, id2, id3)}
     * }</pre>
     *
     * <p>Example usage with records:
     * <pre>{@code
     * SELECT \{Table.class}
     * FROM \{Table.class}
     * WHERE \{where(user1, user2)}
     * }</pre>
     *
     * <p>In this example, the query selects all entries in {@code Table} that are linked to {@code user1} or
     * {@code user2}.
     *
     * @param o an array of values or records to match against the primary key(s) or foreign keys.
     * @return an {@link Element} representing the WHERE clause.
     */
    static Element where(@Nonnull Object... o) {
        return new Where(new ObjectExpression(o, IN, null), null);
    }

    /**
     * Generates a WHERE clause based on the provided value or record.
     *
     * <p>This method creates a {@code WHERE} clause that matches the primary key(s) of the root table,
     * a record instance of the root table, or foreign key(s) in the hierarchy against the provided records
     * using the {@code EQUALS} operator. It is useful when you want to select records where the primary key,
     * a specific record, or related foreign keys match the object.
     *
     * <p>The object can be:
     * <ul>
     *   <li>Primitive values matching the primary key of the root table.</li>
     *   <li>Instances of {@link Record} matching the compound primary key of the root table.</li>
     *   <li>Instances of {@link Record} representing records of related (foreign key) tables in the hierarchy of the
     *   root table.</li>
     * </ul>
     *
     * <p>Example usage with a primary key value:
     * <pre>{@code
     * SELECT \{Table.class}
     * FROM \{Table.class}
     * WHERE \{where(id)}
     * }</pre>
     *
     * <p>Example usage with a record:
     * <pre>{@code
     * User user = ...;
     * SELECT \{Table.class}
     * FROM \{Table.class}
     * WHERE \{where(user)}
     * }</pre>
     *
     * <p>In this example, the query selects all entries in {@code Table} that are linked to the specified {@code user}.
     *
     * <p>For convenience, you can also use the shorthand notation. The SQL template engine automatically detects that
     * a WHERE element is required based on its placement in the query:
     * <pre>{@code
     * User user = ...;
     * SELECT \{Table.class}
     * FROM \{Table.class}
     * WHERE \{user}
     * }</pre>
     *
     * <p>As per the resolution rules:
     * <ul>
     *   <li>If {@code \{user}}, or any other primitive or object, is placed after WHERE the SQL template engine
     *   resolves it into a WHERE element.</li>
     *   <li>If {@code \{user}}, or any other primitive or object, is placed after keywords like VALUES, SET, the SQL
     *   template engine resolves it into the appropriate element (e.g., VALUES element, SET element).</li>
     *   <li>If {@code \{user}} is not in such a placement, it is resolved into a param element.</li>
     * </ul>
     *
     * @param o the value or record to match against the primary key or foreign key.
     * @return an {@link Element} representing the WHERE clause.
     */
    static Element where(@Nonnull Object o) {
        return new Where(new ObjectExpression(o), null);
    }

    /**
     * Generates a WHERE clause based on the provided path, operator, and iterable of values or records.
     *
     * <p>This method creates a {@code WHERE} clause for the specified column or path using the given
     * operator and values or records. The {@code path} parameter specifies the column or property to apply the
     * condition on, which can include nested properties using dot notation.
     *
     * <p>The objects in the iterable must match the type of the record component found at the specified path.
     * If the path points to a record, the objects may also match the primary key type of that record.</pz>
     *
     * <p>Example usage with primary keys:
     * <pre>{@code
     * SELECT \{Table.class}
     * FROM \{Table.class}
     * WHERE \{where("user", Operator.IN, listOfUserIds)}
     * }</pre>
     *
     * or:
     * <pre>{@code
     * SELECT \{Table.class}
     * FROM \{Table.class}
     * WHERE \{where("user.id", Operator.IN, listOfUserIds)}
     * }</pre>
     *
     * <p>In this example, {@code listOfUserIds} contains the primary key values of the {@code user} records,
     * and the query selects all entries in {@code Table} linked to those users.</p>
     *
     * <p>Example usage with records:
     * <pre>{@code
     * List<User> users = ...;
     * SELECT \{Table.class}
     * FROM \{Table.class}
     * WHERE \{where("user", Operator.IN, users)}
     * }</pre>
     *
     * <p>In this example, {@code users} is a list of {@code User} records. The query matches entries in
     * {@code Table} linked to any of the users in the list via their foreign keys.</p>
     *
     * @param path the path or column name to apply the condition on.
     * @param operator the {@link Operator} to use in the condition.
     * @param it an {@link Iterable} of values or records for the condition.
     * @return an {@link Element} representing the WHERE clause.
     */
    static Element where(@Nonnull String path, @Nonnull Operator operator, @Nonnull Iterable<?> it) {
        return new Where(new ObjectExpression(it, operator, path), null);
    }

    /**
     * Generates a WHERE clause based on the provided path, operator, and values or records.
     *
     * <p>This method creates a {@code WHERE} clause for the specified column or path using the given
     * operator and values or records. The {@code path} parameter specifies the column or property to apply the
     * condition on, which can include nested properties using dot notation.
     *
     * <p>The objects in the array must match the type of the record component found at the specified path.
     * If the path points to a record, the objects may also match the primary key type of that record.</pz>
     *
     * <p>Example usage with primary keys:
     * <pre>{@code
     * SELECT \{Table.class}
     * FROM \{Table.class}
     * WHERE \{where("user", Operator.BETWEEN, 1, 10)}
     * }</pre>
     *
     * or:
     * <pre>{@code
     * SELECT \{Table.class}
     * FROM \{Table.class}
     * WHERE \{where("user.id", Operator.BETWEEN, 1, 10)}
     * }</pre>
     *
     * <p>Example usage with records:
     * <pre>{@code
     * User user1 = ...;
     * User user2 = ...;
     * SELECT \{Table.class}
     * FROM \{Table.class}
     * WHERE \{where("user", Operator.BETWEEN, user1, user2)}
     * }</pre>
     *
     * <p>In this example, the query selects all entries in {@code Table} where the associated {@code user} falls between {@code user1} and {@code user2} based on the defined ordering.
     *
     * @param path the path or column name to apply the condition on.
     * @param operator the {@link Operator} to use in the condition.
     * @param o the values or records for the condition.
     * @return an {@link Element} representing the WHERE clause.
     */
    static Element where(@Nonnull String path, @Nonnull Operator operator, @Nonnull Object... o) {
        return new Where(new ObjectExpression(o, operator, path), null);
    }

    /**
     * Generates a WHERE clause using the specified {@link BindVars} for batch operations.
     *
     * <p>This method is particularly useful when performing batch operations where the same query is executed
     * multiple times with different parameter values. The {@link BindVars} instance allows for parameterized
     * queries, enhancing performance and security by preventing SQL injection and enabling query plan reuse.
     *
     * <p>Example usage in a batch operation:
     * <pre>{@code
     * var bindVars = orm.createBindVars();
     * try (var query = orm.query(RAW."""
     *         UPDATE \{Table.class}
     *         SET \{bindVars}
     *         WHERE \{where(bindVars)}""").prepare()) {
     *     records.forEach(query::addBatch);
     *     query.executeBatch();
     * }
     * }</pre>
     *
     * <p>In this example, the {@code bindVars} instance is used to bind variables for the WHERE clause in a batch
     * operation. Each record in {@code records} provides the parameter values for a single execution of the query.
     *
     * <p>For convenience, you can also use the shorthand notation. The SQL template engine automatically detects that
     * a WHERE element is required based on its placement in the query:
     * <pre>{@code
     * UPDATE \{Table.class}
     * SET \{bindVars}
     * WHERE \{bindVars}
     * }</pre>
     *
     * @param bindVars the {@link BindVars} instance used for binding variables in the WHERE clause; must not be
     * {@code null}.
     * @return an {@link Element} representing the WHERE clause utilizing the specified bind variables.
     */
    static Element where(@Nonnull BindVars bindVars) {
        return new Where( null, requireNonNull(bindVars, "bindVars"));
    }

    /**
     * Generates a DELETE element for the specified table class.
     *
     * <p>This method creates a {@code DELETE} clause for the provided table record. It is designed to be used
     * within SQL string templates to dynamically construct DELETE queries based on the table's structure.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * DELETE \{delete(Table.class)} FROM \{from(Table.class)}
     * WHERE \{where(record)}
     * }</pre>
     *
     * <p>For convenience, you can also use the shorthand notation. The SQL template engine automatically detects that
     * a DELETE element is required based on its placement in the query:
     * <pre>{@code
     * DELETE \{Table.class} FROM \{Table.class}
     * WHERE \{record}
     * }</pre>
     *
     * <p>Here, {@code record} is an instance of the {@link Record} class containing the criteria for deletion.
     *
     * <p>Note that in most databases, specifying the table in the DELETE clause is not necessary, or even disallowed;
     * the DELETE statement is usually constructed with only a FROM clause:
     * <pre>{@code
     * DELETE FROM \{from(Table.class)}
     * WHERE \{where(record)}
     * }</pre>
     *
     * @param table the {@link Class} object representing the table record.
     * @return an {@link Element} representing the DELETE clause for the specified table.
     */
    static Element delete(@Nonnull KClass<? extends Record> table) {
        return new Delete(getORMReflection().getRecordType(table));
    }

    /**
     * Generates a DELETE element for the specified table class with an alias.
     *
     * <p>This method creates a {@code DELETE} clause for the provided table record, applying the specified alias.
     * It is designed to be used within SQL string templates to dynamically construct DELETE queries based on the
     * table's structure.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * DELETE \{delete(Table.class, "t")} FROM \{from(Table.class, "t")}
     * WHERE \{where(record)}
     * }</pre>
     *
     * <p>Here, {@code record} is an instance of the {@link Record} class containing the criteria for deletion.
     *
     * <p>Note that in most databases, specifying the table in the DELETE clause with an alias is not necessary; the
     * DELETE statement can be constructed with only a FROM clause and an alias:
     * <pre>{@code
     * DELETE FROM \{from(Table.class, "t")}
     * WHERE \{where(record)}
     * }</pre>
     *
     * @param table the {@link Class} object representing the table record.
     * @param alias the alias to use for the table in the query.
     * @return an {@link Element} representing the DELETE clause for the specified table with an alias.
     */
    static Element delete(@Nonnull KClass<? extends Record> table, @Nonnull String alias) {
        return new Delete(getORMReflection().getRecordType(table), alias);
    }

    /**
     * Generates a Table element for the specified table class.
     *
     * <p>This method creates a representation of a database table, which can be used in SQL string templates
     * to refer to the table in queries. It is useful when you need to explicitly specify the table in parts of
     * your query where the SQL template engine does not automatically resolve the table based on context.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT * FROM \{table(Table.class)}
     * }</pre>
     *
     * <p>For convenience, you can also use the shorthand notation. If the SQL template engine cannot resolve
     * {@code \{Table.class}} into a specific element based on its placement in the query (e.g., after SELECT, FROM, etc.),
     * it will default to creating a Table element:
     * <pre>{@code
     * ... \{Table.class} ...
     * }</pre>
     *
     * <p>However, if {@code \{Table.class}} is followed by a dot {@code '.'}, the SQL template engine will resolve it into an
     * alias element, representing the alias of the table in the query:
     * <pre>{@code
     * SELECT \{Table.class}.column_name FROM \{Table.class}
     * }</pre>
     *
     * <p>As per the resolution rules:
     * <ul>
     *   <li>If {@code \{Table.class}} is placed after keywords like SELECT, FROM, INSERT INTO, UPDATE, or DELETE,
     *       the SQL template engine resolves it into the appropriate element (e.g., SELECT element, FROM element).</li>
     *   <li>If {@code \{Table.class}} is not in such a placement and is not followed by a dot {@code '.'},
     *       it is resolved into a table element.</li>
     *   <li>If {@code \{Table.class}} is followed by a dot {@code '.'}, it is resolved into an alias element.</li>
     * </ul>
     *
     * @param table the {@link Class} object representing the table record.
     * @return an {@link Element} representing the table.
     */
    static Element table(@Nonnull KClass<? extends Record> table) {
        return new Table(getORMReflection().getRecordType(table));
    }

    /**
     * Generates a Table element with an alias for the specified table class.
     *
     * <p>This method creates a representation of a database table with the specified alias, which can be used in
     * SQL string templates to refer to the table in queries. This is useful when you need to assign an alias to
     * a table for use in your query.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT * FROM \{table(Table.class, "t")}
     * }</pre>
     *
     * <p>You can refer to the table alias in your query as follows:
     * <pre>{@code
     * SELECT \{alias(Table.class)}.column_name FROM \{table(Table.class, "t")}
     * }</pre>
     *
     * @param table the {@link Class} object representing the table record.
     * @param alias the alias to use for the table in the query.
     * @return an {@link Element} representing the table with an alias.
     */
    static Element table(@Nonnull KClass<? extends Record> table, @Nonnull String alias) {
        return new Table(getORMReflection().getRecordType(table), alias);
    }

    /**
     * Generates an alias element for the specified table class.
     *
     * <p>This method returns the alias of the table as used in the query. It is useful when you need to refer to
     * the table's alias, especially in situations where the SQL template engine cannot automatically determine
     * the appropriate element based on context.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT \{alias(Table.class)}.column_name FROM \{table(Table.class, "t")}
     * }</pre>
     *
     * <p>According to the resolution rules, if {@code \{Table.class}} is followed by a dot {@code '.'}, the SQL template engine
     * automatically resolves it into an alias element:
     * <pre>{@code
     * SELECT \{Table.class}.column_name FROM \{Table.class}
     * }</pre>
     *
     * <p>As per the resolution rules:
     * <ul>
     *   <li>If {@code \{Table.class}} is placed after keywords like SELECT, FROM, INSERT INTO, UPDATE, or DELETE,
     *       the SQL template engine resolves it into the appropriate element (e.g., SELECT element, FROM element).</li>
     *   <li>If {@code \{Table.class}} is not in such a placement and is not followed by a dot {@code '.'},
     *       it is resolved into a table element.</li>
     *   <li>If {@code \{Table.class}} is followed by a dot {@code '.'}, it is resolved into an alias element.</li>
     * </ul>
     *
     * @param table the {@link Class} object representing the table record.
     * @return an {@link Element} representing the table's alias.
     */
    static Element alias(@Nonnull KClass<? extends Record> table) {
        return new Alias(getORMReflection().getRecordType(table), null, CASCADE);
    }

    /**
     * Generates an alias element for a table found at a specific {@code path} within the table's hierarchy as used in
     * the query.
     *
     * <p>This method is particularly useful when the same table class appears multiple times in a query through
     * different paths, and you need to specify which instance you're referring to. The {@code path} parameter uniquely
     * identifies the table by specifying the sequence of field names from the root table to the target table. This
     * helps avoid ambiguity when generating SQL queries that involve multiple relationships to the same table class.
     * </p>
     *
     * <p>The path is constructed by concatenating the names of the fields that lead to the target table from the root
     * table.</p>
     *
     * <p>Example usage in a string template where {@code User} is referenced twice:
     * <pre>{@code
     * // Define a record with two references to User
     * record Table(int id, User child, User parent) {}
     *
     * // In the SQL template
     * SELECT \{alias(User.class, "child")}.column_name FROM \{Table.class}
     * }</pre>
     *
     * <p>In this example, the path "child" specifies that we are referring to the {@code child} field of the
     * {@code Table} record, which is of type {@code User}. This distinguishes it from the {@code parent} field, which
     * is also of type {@code User}.</p>
     *
     * @param table the {@link Class} object representing the table record.
     * @param path an optional path within the table's hierarchy to uniquely identify the table.
     * @return an {@link Element} representing the table's alias with the specified path.
     */
    static Element alias(@Nonnull KClass<? extends Record> table, @Nullable String path) {
        return new Alias(getORMReflection().getRecordType(table), requireNonNull(path, "path"), CASCADE);
    }

    /**
     * Generates an alias element for the specified table class.
     *
     * <p>This method returns the alias of the table as used in the query. It is useful when you need to refer to
     * the table's alias, especially in situations where the SQL template engine cannot automatically determine
     * the appropriate element based on context.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT \{alias(Table.class)}.column_name FROM \{table(Table.class, "t")}
     * }</pre>
     *
     * <p>According to the resolution rules, if {@code \{Table.class}} is followed by a dot {@code '.'}, the SQL template engine
     * automatically resolves it into an alias element:
     * <pre>{@code
     * SELECT \{Table.class}.column_name FROM \{Table.class}
     * }</pre>
     *
     * <p>As per the resolution rules:
     * <ul>
     *   <li>If {@code \{Table.class}} is placed after keywords like SELECT, FROM, INSERT INTO, UPDATE, or DELETE,
     *       the SQL template engine resolves it into the appropriate element (e.g., SELECT element, FROM element).</li>
     *   <li>If {@code \{Table.class}} is not in such a placement and is not followed by a dot {@code '.'},
     *       it is resolved into a table element.</li>
     *   <li>If {@code \{Table.class}} is followed by a dot {@code '.'}, it is resolved into an alias element.</li>
     * </ul>
     *
     * @param table the {@link Class} object representing the table record.
     * @param scope the {@link ResolveScope} to use when resolving the alias. Use CASCADE to include local and outer
     *                   scopes, INNER to include local aliases only, and OUTER to include outer aliases only.
     * @return an {@link Element} representing the table's alias.
     */
    static Element alias(@Nonnull KClass<? extends Record> table, @Nonnull ResolveScope scope) {
        return new Alias(getORMReflection().getRecordType(table), null, scope);
    }

    /**
     * Generates an alias element for a table found at a specific {@code path} within the table's hierarchy as used in
     * the query.
     *
     * <p>This method is particularly useful when the same table class appears multiple times in a query through
     * different paths, and you need to specify which instance you're referring to. The {@code path} parameter uniquely
     * identifies the table by specifying the sequence of field names from the root table to the target table. This
     * helps avoid ambiguity when generating SQL queries that involve multiple relationships to the same table class.
     * </p>
     *
     * <p>The path is constructed by concatenating the names of the fields that lead to the target table from the root
     * table.</p>
     *
     * <p>Example usage in a string template where {@code User} is referenced twice:
     * <pre>{@code
     * // Define a record with two references to User
     * record Table(int id, User child, User parent) {}
     *
     * // In the SQL template
     * SELECT \{alias(User.class, "child")}.column_name FROM \{Table.class}
     * }</pre>
     *
     * <p>In this example, the path "child" specifies that we are referring to the {@code child} field of the
     * {@code Table} record, which is of type {@code User}. This distinguishes it from the {@code parent} field, which
     * is also of type {@code User}.</p>
     *
     * @param table the {@link Class} object representing the table record.
     * @param path an optional path within the table's hierarchy to uniquely identify the table.
     * @param scope the {@link ResolveScope} to use when resolving the alias. Use CASCADE to include local and outer
     *                   scopes, INNER to include local aliases only, and OUTER to include outer aliases only.
     * @return an {@link Element} representing the table's alias with the specified path.
     */
    static Element alias(@Nonnull KClass<? extends Record> table, @Nonnull String path, @Nonnull ResolveScope scope) {
        return new Alias(getORMReflection().getRecordType(table), requireNonNull(path, "path"), scope);
    }

    /**
     * Generates an alias element for a table specified by the given {@code metamodel} in a type safe manner.
     *
     * <p>Example usage in a string template where {@code User} is referenced twice:
     * <pre>{@code
     * // Define a record with two references to User
     * record Table(int id, User child, User parent) {}
     *
     * // In the SQL template
     * SELECT \{alias(Table_.child}.column_name FROM \{Table.class}
     * }</pre>
     *
     * <p>In this example, {@code Table_.child} specifies that we are referring to the {@code child} field of the {@code Table} record,
     * which is of type {@code User}. This distinguishes it from the {@code parent} field, which is also of type {@code User}.
     *
     * @param metamodel specifies the table for which the alias is to be generated.
     * @return an {@link Element} representing the table's alias with the specified path.
     * @since 1.2
     */
    static Element alias(@Nonnull Metamodel<?, ? extends Record> metamodel) {
        return new Alias(metamodel, CASCADE);
    }

    /**
     * Generates an alias element for a table specified by the given {@code metamodel} in a type safe manner.
     *
     * <p>Example usage in a string template where {@code User} is referenced twice:
     * <pre>{@code
     * // Define a record with two references to User
     * record Table(int id, User child, User parent) {}
     *
     * // In the SQL template
     * SELECT \{alias(Table_.child}.column_name FROM \{Table.class}
     * }</pre>
     *
     * <p>In this example, {@code Table_.child} specifies that we are referring to the {@code child} field of the {@code Table} record,
     * which is of type {@code User}. This distinguishes it from the {@code parent} field, which is also of type {@code User}.
     *
     * @param metamodel specifies the table for which the alias is to be generated.
     * @param scope the {@link ResolveScope} to use when resolving the alias. Use STRICT to include local and outer
     *              aliases, LOCAL to include local aliases only, and OUTER to include outer aliases only.
     * @return an {@link Element} representing the table's alias with the specified path.
     * @since 1.2
     */
    static Element alias(@Nonnull Metamodel<?, ? extends Record> metamodel, @Nonnull ResolveScope scope) {
        return new Alias(metamodel, scope);
    }

    static Element column(@Nonnull KClass<? extends Record> table, @Nonnull String componentName, @Nullable String path) {
        return new Column(getORMReflection().getRecordType(table), componentName, path, CASCADE);
    }

    static Element column(@Nonnull KClass<? extends Record> table, @Nonnull String componentName, @Nullable String path, @Nonnull ResolveScope scope) {
        return new Column(getORMReflection().getRecordType(table), componentName, path, scope);
    }

    static Element column(@Nonnull Metamodel<?, ?> metamodel) {
        return new Column(metamodel, CASCADE);
    }

    static Element column(@Nonnull Metamodel<?, ?> metamodel, @Nonnull ResolveScope scope) {
        return new Column(metamodel, scope);
    }

    /**
     * Generates a parameter element for the specified value, to be used in SQL queries.
     *
     * <p>This method creates a positional parameter for use in SQL string templates. The parameter
     * can be of any object type and may be {@code null}. It is intended to be used in places where
     * you need to bind a value to a SQL query parameter.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT *
     * FROM \{Table.class}
     * WHERE status = \{param(1)}
     * }</pre>
     *
     * <p>For convenience, you can also use the shorthand notation, where the SQL template engine
     * automatically detects that a parameter is required:
     * <pre>{@code
     * SELECT *
     * FROM \{Table.class}
     * WHERE status = \{1}
     * }</pre>
     *
     * <p>As per the resolution rules:
     * <ul>
     *   <li>If {@code \{1}}, or any other primitive or object, is placed after keywords like VALUES, SET or WHERE,
     *       the SQL template engine resolves it into the appropriate element (e.g., VALUES element, SET element).</li>
     *   <li>If {@code \{1}} is not in such a placement, it is resolved into a param element.</li>
     * </ul>
     *
     * @param value the value to be used as a parameter in the SQL query; may be {@code null}.
     * @return an {@link Element} representing the parameter.
     */
    static Element param(@Nullable Object value) {
        return new Param(null, value);
    }

    /**
     * Generates a named parameter element for the specified value, to be used in SQL queries.
     *
     * <p>This method creates a named parameter for use in SQL string templates. Named parameters
     * are useful when you want to explicitly specify the parameter's name in the query, improving
     * readability and maintainability, especially in complex queries.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT *
     * FROM \{Table.class}
     * WHERE status = \{param("status", 1)}
     * }</pre>
     *
     * <p>In the query, the parameter will be referred to by its name {@code status}.
     *
     * @param name the name of the parameter; must not be {@code null}.
     * @param value the value to be used as a parameter in the SQL query; may be {@code null}.
     * @return an {@link Element} representing the named parameter.
     */
    static Element param(@Nonnull String name, @Nullable Object value) {
        return new Param(requireNonNull(name, "name"), value);
    }

    /**
     * Generates a parameter element for the specified value with a converter function.
     *
     * <p>This method allows you to provide a custom converter function to transform the value
     * into a database-compatible format. This is useful when the value needs to be converted
     * before being set as a parameter in the SQL query, such as formatting dates or custom objects.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT *
     * FROM \{Table.class}
     * WHERE created_at = \{param(dateValue, date -> new java.sql.Date(date.getTime()))}
     * }</pre>
     *
     * @param <P> the type of the value to be converted.
     * @param value the value to be used as a parameter in the SQL query; may be {@code null}.
     * @param converter a {@link Function} that converts the value to a database-compatible format; must not be
     * {@code null}.
     * @return an {@link Element} representing the parameter with a converter applied.
     */
    static <P> Element param(@Nullable P value, @Nonnull Function<? super P, ?> converter) {
        //noinspection unchecked
        return new Param(null, value, (Function<Object, ?>) requireNonNull(converter, "converter"));
    }

    /**
     * Generates a named parameter element for the specified value with a converter function.
     *
     * <p>This method allows you to provide a custom converter function to transform the value
     * into a database-compatible format and assign a name to the parameter. Named parameters
     * enhance query readability and are particularly useful in complex queries.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT *
     * FROM \{Table.class}
     * WHERE created_at = \{param("createdAt", dateValue, date -> new java.sql.Date(date.getTime()))}
     * }</pre>
     *
     * @param <P> the type of the value to be converted.
     * @param name the name of the parameter; must not be {@code null}.
     * @param value the value to be used as a parameter in the SQL query; may be {@code null}.
     * @param converter a {@link Function} that converts the value to a database-compatible format; must not be
     * {@code null}.
     * @return an {@link Element} representing the named parameter with a converter applied.
     */
    static <P> Element param(@Nonnull String name, @Nullable P value, @Nonnull Function<? super P, ?> converter) {
        //noinspection unchecked
        return new Param(name, value, (Function<Object, ?>) requireNonNull(converter, "converter"));
    }

    /**
     * Generates a parameter element for the specified {@link Date} value with a temporal type.
     *
     * <p>This method creates a positional parameter for a {@link Date} value, converting it to the appropriate
     * SQL type based on the provided {@link TemporalType}. It is useful when you need to specify how the date
     * should be interpreted in the database (as DATE, TIME, or TIMESTAMP).
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT *
     * FROM \{Table.class}
     * WHERE event_date = \{param(dateValue, TemporalType.DATE)}
     * }</pre>
     *
     * @param value the {@link Date} value to be used as a parameter; must not be {@code null}.
     * @param temporalType the {@link TemporalType} specifying how the date should be handled; must not be {@code null}.
     * @return an {@link Element} representing the date parameter with the specified temporal type.
     */
    static Element param(@Nonnull Date value, @Nonnull TemporalType temporalType) {
        return param(value, v -> switch (temporalType) {
            case DATE -> new java.sql.Date(v.getTime());
            case TIME -> new java.sql.Time(v.getTime());
            case TIMESTAMP -> new java.sql.Timestamp(v.getTime());
        });
    }

    /**
     * Generates a named parameter element for the specified {@link Date} value with a temporal type.
     *
     * <p>This method creates a named parameter for a {@link Date} value, converting it to the appropriate
     * SQL type based on the provided {@link TemporalType}. Named parameters improve query readability
     * and are especially useful in complex queries.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT *
     * FROM \{Table.class}
     * WHERE event_date = \{param("eventDate", dateValue, TemporalType.DATE)}
     * }</pre>
     *
     * @param name the name of the parameter; must not be {@code null}.
     * @param value the {@link Date} value to be used as a parameter; must not be {@code null}.
     * @param temporalType the {@link TemporalType} specifying how the date should be handled; must not be {@code null}.
     * @return an {@link Element} representing the named date parameter with the specified temporal type.
     */
    static Element param(@Nonnull String name, @Nonnull Date value, @Nonnull TemporalType temporalType) {
        return param(name, value, v -> switch (temporalType) {
            case DATE -> new java.sql.Date(v.getTime());
            case TIME -> new java.sql.Time(v.getTime());
            case TIMESTAMP -> new java.sql.Timestamp(v.getTime());
        });
    }

    /**
     * Generates a parameter element for the specified {@link Calendar} value with a temporal type.
     *
     * <p>This method creates a positional parameter for a {@link Calendar} value, converting it to the appropriate
     * SQL type based on the provided {@link TemporalType}. It is useful when working with calendar instances
     * that need to be interpreted in the database as specific temporal types.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT *
     * FROM \{Table.class}
     * WHERE event_time = \{param(calendarValue, TemporalType.TIMESTAMP)}
     * }</pre>
     *
     * @param value the {@link Calendar} value to be used as a parameter; must not be {@code null}.
     * @param temporalType the {@link TemporalType} specifying how the calendar should be handled; must not be
     * {@code null}.
     * @return an {@link Element} representing the calendar parameter with the specified temporal type.
     */
    static Element param(@Nonnull Calendar value, @Nonnull TemporalType temporalType) {
        return param(value, v -> switch (temporalType) {
            case DATE -> new java.sql.Date(v.getTimeInMillis());
            case TIME -> new java.sql.Time(v.getTimeInMillis());
            case TIMESTAMP -> new java.sql.Timestamp(v.getTimeInMillis());
        });
    }

    /**
     * Generates a named parameter element for the specified {@link Calendar} value with a temporal type.
     *
     * <p>This method creates a named parameter for a {@link Calendar} value, converting it to the appropriate
     * SQL type based on the provided {@link TemporalType}. Named parameters enhance query readability
     * and are particularly useful in complex queries.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT *
     * FROM \{Table.class}
     * WHERE event_time = \{param("eventTime", calendarValue, TemporalType.TIMESTAMP)}
     * }</pre>
     *
     * @param name the name of the parameter; must not be {@code null}.
     * @param value the {@link Calendar} value to be used as a parameter; must not be {@code null}.
     * @param temporalType the {@link TemporalType} specifying how the calendar should be handled; must not be
     * {@code null}.
     * @return an {@link Element} representing the named calendar parameter with the specified temporal type.
     */
    static Element param(@Nonnull String name, @Nonnull Calendar value, @Nonnull TemporalType temporalType) {
        return param(name, value, v -> switch (temporalType) {
            case DATE -> new java.sql.Date(v.getTimeInMillis());
            case TIME -> new java.sql.Time(v.getTimeInMillis());
            case TIMESTAMP -> new java.sql.Timestamp(v.getTimeInMillis());
        });
    }

    /**
     * Creates a new subquery element using a query builder.
     *
     * @param builder   the query builder used to construct the subquery; must not be null.
     * @param correlate a flag indicating whether the subquery should correlate with the outer query.
     *                  If {@code true}, the subquery can reference elements from the outer query.
     *                  If {@code false}, the subquery is independent and does not access the outer query.
     * @return a new {@code Subquery} element based on the provided query builder and correlation flag.
     * @throws NullPointerException if the {@code builder} is null.
     */
    static Element subquery(@Nonnull KQueryBuilder<?, ?, ?> builder, boolean correlate) {
        return new Subquery(((Subqueryable) builder).getStringTemplate(), correlate);
    }

    /**
     * Creates a new subquery element using a string template.
     *
     * @param template  the string template representing the subquery; must not be null.
     * @param correlate a flag indicating whether the subquery should correlate with the outer query.
     *                  If {@code true}, the subquery can reference elements from the outer query.
     *                  If {@code false}, the subquery is independent and does not access the outer query.
     * @return a new {@code Subquery} element based on the provided template and correlation flag.
     * @throws NullPointerException if the {@code template} is null.
     */
    static Element subquery(@Nonnull StringTemplate template, boolean correlate) {
        return new Subquery(template, correlate);
    }

    /**
     * Injects raw SQL into the query without any processing or sanitization.
     *
     * <p>This method allows you to insert arbitrary SQL code directly into your query. It bypasses any
     * automatic parameter binding or SQL generation provided by the SQL template engine. As a result, it can be
     * potentially unsafe and may expose your application to SQL injection attacks if not used carefully.
     *
     * <p><strong>Warning:</strong> Use this method only when you are certain that the SQL string being injected
     * is safe and originates from a trusted source. Avoid using user-supplied input with this method.
     *
     * <p>Example usage in a string template:
     * <pre>{@code
     * SELECT * FROM \{User.class} WHERE \{unsafe("city = 'Sunnyvale'")}
     * }</pre>
     *
     * <p>In this example, the SQL fragment <code>"city = 'Sunnyvale'"</code> is injected directly into the query.
     *
     * @param sql the raw SQL string to inject into the query.
     * @return an {@link Element} that represents the raw SQL code to be inserted into the query.
     */
    static Element unsafe(@Nonnull String sql) {
        return new Unsafe(sql);
    }
}
