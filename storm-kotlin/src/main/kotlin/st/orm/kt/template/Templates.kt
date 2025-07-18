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
package st.orm.kt.template

import jakarta.persistence.EntityManager
import st.orm.*
import st.orm.core.template.impl.Element
import st.orm.core.template.impl.Elements
import st.orm.core.template.impl.Elements.ObjectExpression
import st.orm.core.template.impl.Elements.TemplateSource
import st.orm.core.template.impl.Subqueryable
import st.orm.kt.template.Templates.table
import java.sql.Connection
import java.sql.Time
import java.sql.Timestamp
import java.util.*
import java.util.function.Function
import javax.sql.DataSource
import kotlin.reflect.KClass

/**
 * The `Templates` interface provides a collection of static methods for constructing SQL query elements
 * and creating ORM repository templates. It serves as a central point for building SQL queries or getting access to
 * repositories that can interact with databases in a type-safe and manner, supporting both JPA and JDBC.
 *
 *
 * This interface includes methods for generating SQL clauses such as `SELECT`, `FROM`, `WHERE`,
 * `INSERT`, `UPDATE`, and `DELETE`, as well as utility methods for working with parameters,
 * tables, aliases, and more.
 *
 *
 * Additionally, the `Templates` interface provides methods to create [ORMTemplate]
 * instances for use with different data sources like JPA's [EntityManager], JDBC's [DataSource], or
 * [Connection].
 *
 * <h2>Using Templates</h2>
 *
 *
 * Define the records to use them to construct the query using templates:
 *
 * <pre>`record City(int id, String name, long population) {};
 *
 * record User(int id, String name, int age, LocalDate birthDate,
 * String street, String postalCode, int cityId) {};
`</pre> *
 *
 * <h3>Create</h3>
 *
 *
 * Insert a user into the database. The template engine also supports insertion of multiple entries by passing an
 * array (or list) of objects or primary keys. Alternatively, insertion can also be executed in batch mode using
 * [st.orm.BindVars].
 * <pre>`User user = ...;
 * ORM(dataSource).query(RAW."""
 * INSERT INTO \{User.class}
 * VALUES \{user}""");
`</pre> *
 *
 * <h3>Read</h3>
 *
 *
 * Select all users from the database that are linked to City with primary key 1. The value is passed to the
 * underling JDBC or JPA system as variable. The results can also be retrieved as a stream of objects by using
 * [Query.getResultStream].
 * <pre>`List<User> users = ORM(dataSource).query(RAW."""
 * SELECT \{User.class}
 * FROM \{User.class}
 * WHERE \{User.class}.city_id = \{1}""")
 * .getResultList(User.class);
`</pre> *
 *
 * <h3>Update</h3>
 *
 *
 * Update a user in the database. The template engine also supports updates for multiple entries by passing an
 * array or list of objects. Alternatively, updates can be executed in batch mode by using [st.orm.BindVars].
 * <pre>`User user = ...;
 * ORM(dataSource).query(RAW."""
 * UPDATE \{User.class}
 * SET \{user}
 * WHERE \{user}""");
`</pre> *
 *
 * <h3>Delete</h3>
 *
 *
 * Delete user in the database. The template engine also supports updates for multiple entries by passing an
 * array (or list) of objects or primary keys. Alternatively, deletion can be executed in batch mode by using
 * [st.orm.BindVars].
 * <pre>`User user = ...;
 * ORM(dataSource).query(RAW."""
 * DELETE FROM \{User.class}
 * WHERE \{user}""");
`</pre> *
 *
 * <h2>Howto start</h2>
 *
 *
 * The `Templates` interface provides static methods to create [ORMTemplate] instances based on your data source:
 *
 * <h3>Using EntityManager (JPA)</h3>
 * <pre>`EntityManager entityManager = ...;
 * ORMTemplate orm = Templates.ORM(entityManager);
`</pre> *
 *
 * <h3>Using DataSource (JDBC)</h3>
 * <pre>`DataSource dataSource = ...;
 * ORMTemplate orm = Templates.ORM(dataSource);
`</pre> *
 *
 * <h3>Using Connection (JDBC)</h3>
 *
 *
 * **Note:** The caller is responsible for closing the connection after usage.
 *
 * <pre>`Connection connection = ...;
 * ORMTemplate orm = Templates.ORM(connection);
`</pre> *
 *
 * @see st.orm.repository.EntityRepository
 *
 * @see st.orm.repository.ProjectionRepository
 */
object Templates {

    /**
     * Generates a SELECT element for the specified table class.
     *
     *
     * This method creates a `SELECT` clause for the provided table record, including all of its
     * columns as well as columns from any foreign key relationships defined within the record. It is
     * designed to be used within SQL string templates to dynamically construct queries based on the
     * table's structure.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT \{select(MyTable.class, NESTED)}
     * FROM \{from(MyTable.class)}
    `</pre> *
     *
     *
     * For convenience, you can also use the shorthand notation. The SQL template engine
     * automatically detects that a SELECT element is required based on its placement in the query:
     * <pre>`SELECT \{MyTable.class}
     * FROM \{MyTable.class}
    `</pre> *
     *
     * @param table the [Class] object representing the table record.
     * @param mode NESTED to include the full object hierarchy, FLAT to include only the main table's fields, PK to
     * include only the primary key fields.
     * @return an [Element] representing the SELECT clause for the specified table.
     */
    fun select(table: KClass<out Record>): Element {
        return Elements.Select(table.java, SelectMode.NESTED)
    }
    /**
     * Generates a SELECT element for the specified table class.
     *
     *
     * This method creates a `SELECT` clause for the provided table record, including all of its
     * columns as well as columns from any foreign key relationships defined within the record. It is
     * designed to be used within SQL string templates to dynamically construct queries based on the
     * table's structure.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT \{select(MyTable.class)}
     * FROM \{from(MyTable.class)}
    `</pre> *
     *
     *
     * For convenience, you can also use the shorthand notation. The SQL template engine
     * automatically detects that a SELECT element is required based on its placement in the query:
     * <pre>`SELECT \{MyTable.class}
     * FROM \{MyTable.class}
    `</pre> *
     *
     * @param table the [Class] object representing the table record.
     * @return an [Element] representing the SELECT clause for the specified table.
     */
    fun select(table: KClass<out Record>, mode: SelectMode = SelectMode.NESTED): Element {
        return Elements.Select(table.java, mode)
    }

    /**
     * Generates a FROM element for the specified table class without an alias and optional auto-joining of foreign
     * keys.
     *
     *
     * This method creates a `FROM` clause for the provided table record. If `autoJoin` is set
     * to `true`, it will automatically include JOIN clauses for all foreign keys defined in the record.
     * This facilitates constructing complex queries that involve related tables without manually specifying each join.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT \{select(MyTable.class)}
     * FROM \{from(MyTable.class, true)}
    `</pre> *
     *
     *
     * For convenience, you can also use the shorthand notation. In this case, `autoJoin`
     * defaults to `false`. The SQL template engine automatically detects that a FROM element is required
     * based on its placement in the query:
     * <pre>`SELECT \{MyTable.class}
     * FROM \{MyTable.class}
    `</pre> *
     *
     * @param table the [Class] object representing the table record.
     * @param autoJoin if `true`, automatically join all foreign keys listed in the record.
     * @return an [Element] representing the FROM clause for the specified table.
     */
    fun from(table: KClass<out Record>, autoJoin: Boolean): Element {
        return Elements.From(table.java, autoJoin)
    }

    /**
     * Generates a FROM element for the specified table class, with an alias and optional auto-joining of foreign keys.
     *
     *
     * This method creates a `FROM` clause for the provided table record, applying the specified alias.
     * If `autoJoin` is set to `true`, it will automatically include JOIN clauses for all foreign keys
     * defined in the record. This facilitates constructing complex queries that involve related tables without
     * manually specifying each join.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT \{select(Table.class)}
     * FROM \{from(MyTable.class, "t", true)}
    `</pre> *
     *
     * @param table the [Class] object representing the table record.
     * @param alias the alias to use for the table in the query. The alias must not require escaping.
     * @param autoJoin if `true`, automatically join all foreign keys listed in the record.
     * @return an [Element] representing the FROM clause for the specified table.
     */
    fun from(table: KClass<out Record>, alias: String, autoJoin: Boolean): Element {
        return Elements.From(Elements.TableSource(table.java), Objects.requireNonNull(alias, "alias"), autoJoin)
    }

    /**
     * Generates a FROM element using a provided SQL string template with an alias.
     *
     *
     * This method allows you to specify a custom [StringTemplate] to be used as the source in the `FROM`
     * clause, applying the provided alias. This is useful when you need to include subqueries or complex table
     * expressions in your SQL queries.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT \{select(Table.class)}
     * FROM \{from(RAW."SELECT column_a, column_b FROM table", "t")}
    `</pre> *
     *
     *  in this context, the alias is mandatory and auto-joining of foreign keys is not applicable.
     *
     * @param builder the [StringTemplate] representing the custom SQL to be used in the FROM clause.
     * @param alias the alias to assign to the frame clause in the query. The alias must not require escaping.
     * @return an [Element] representing the FROM clause with the specified template and alias.
     */
    fun from(builder: TemplateBuilder, alias: String): Element {
        return from(builder.build(), alias)
    }
    /**
     * Generates a FROM element using a provided SQL string template with an alias.
     *
     *
     * This method allows you to specify a custom [StringTemplate] to be used as the source in the `FROM`
     * clause, applying the provided alias. This is useful when you need to include subqueries or complex table
     * expressions in your SQL queries.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT \{select(Table.class)}
     * FROM \{from(RAW."SELECT column_a, column_b FROM table", "t")}
    `</pre> *
     *
     *  in this context, the alias is mandatory and auto-joining of foreign keys is not applicable.
     *
     * @param template the [StringTemplate] representing the custom SQL to be used in the FROM clause.
     * @param alias the alias to assign to the frame clause in the query. The alias must not require escaping.
     * @return an [Element] representing the FROM clause with the specified template and alias.
     */
    fun from(template: TemplateString, alias: String): Element {
        return Elements.From(
            TemplateSource(template.unwrap),
            Objects.requireNonNull(alias, "alias"),
            false
        )
    }

    /**
     * Generates an INSERT element for the specified table class.
     *
     *
     * This method creates an `INSERT` clause for the provided table record. It is designed to be used
     * within SQL string templates to dynamically construct INSERT queries based on the table's structure.
     *
     *
     * Example usage in a string template:
     * <pre>`INSERT INTO \{insert(MyTable.class)}
     * VALUES \{values(entity)}
    `</pre> *
     *
     *
     * For convenience, you can also use the shorthand notation. The SQL template engine automatically detects that
     * an INSERT element is required based on its placement in the query:
     * <pre>`INSERT INTO \{MyTable.class}
     * VALUES \{entity}
    `</pre> *
     *
     *
     * Here, `entity` is an instance of the `Table` class containing the values to be inserted.
     *
     * @param table the [Class] object representing the table record.
     * @return an [Element] representing the INSERT clause for the specified table.
     */
    fun insert(table: KClass<out Record>): Element {
        return Elements.Insert(table.java)
    }

    /**
     * Generates an INSERT element for the specified table class.
     *
     *
     * This method creates an `INSERT` clause for the provided table record. It is designed to be used
     * within SQL string templates to dynamically construct INSERT queries based on the table's structure.
     *
     *
     * Example usage in a string template:
     * <pre>`INSERT INTO \{insert(MyTable.class)}
     * VALUES \{values(entity)}
    `</pre> *
     *
     *
     * For convenience, you can also use the shorthand notation. The SQL template engine automatically detects that
     * an INSERT element is required based on its placement in the query:
     * <pre>`INSERT INTO \{MyTable.class}
     * VALUES \{entity}
    `</pre> *
     *
     *
     * Here, `entity` is an instance of the `Table` class containing the values to be inserted.
     *
     * @param table the [Class] object representing the table record.
     * @param ignoreAutoGenerate true to ignore the auto-generate flag on the primary key and explicitly insert the
     * provided primary key value. Use this flag only when intentionally providing the primary
     * key value (e.g., migrations, data exports).
     * @return an [Element] representing the INSERT clause for the specified table.
     */
    fun insert(table: KClass<out Record>, ignoreAutoGenerate: Boolean): Element {
        return Elements.Insert(table.java, ignoreAutoGenerate)
    }

    /**
     * Generates a VALUES clause for the specified record instance(s).
     *
     *
     * This method creates a `VALUES` clause using the provided [Record] instance(s).
     * It is intended to be used within SQL string templates to dynamically construct INSERT statements with
     * the given values.
     *
     *
     * Example usage in a string template:
     * <pre>`INSERT INTO \{MyTable.class}
     * VALUES \{values(entity1, entity2)}
    `</pre> *
     *
     *
     * For convenience, you can also use the shorthand notation. The SQL template engine automatically
     * detects that a VALUES element is required based on its placement in the query:
     * <pre>`INSERT INTO \{MyTable.class}
     * VALUES \{new Record[] {entity1, entity2}}
    `</pre> *
     *
     *
     * Here, `entity1`, `entity2`, etc., are instances of the `Record` class containing
     * the values to be inserted.
     *
     * @param r one or more [Record] instances containing the values to be inserted.
     * @param ignoreAutoGenerate true to ignore the auto-generate flag on the primary key and explicitly insert the
     * provided primary key value. Use this flag only when intentionally providing the primary
     * key value (e.g., migrations, data exports).
     * @return an [Element] representing the VALUES clause with the specified records.
     */
    fun values(r: Record, ignoreAutoGenerate: Boolean): Element {
        return Elements.Values(listOf(r), null, ignoreAutoGenerate)
    }

    /**
     * Generates a VALUES clause for the specified record instance(s).
     *
     *
     * This method creates a `VALUES` clause using the provided [Record] instance(s).
     * It is intended to be used within SQL string templates to dynamically construct INSERT statements with
     * the given values.
     *
     *
     * Example usage in a string template:
     * <pre>`INSERT INTO \{MyTable.class}
     * VALUES \{values(entity1, entity2)}
    `</pre> *
     *
     *
     * For convenience, you can also use the shorthand notation. The SQL template engine automatically
     * detects that a VALUES element is required based on its placement in the query:
     * <pre>`INSERT INTO \{MyTable.class}
     * VALUES \{new Record[] {entity1, entity2}}
    `</pre> *
     *
     *
     * Here, `entity1`, `entity2`, etc., are instances of the `Record` class containing
     * the values to be inserted.
     *
     * @param r one or more [Record] instances containing the values to be inserted.
     * @return an [Element] representing the VALUES clause with the specified records.
     */
    fun values(vararg r: Record): Element {
        return Elements.Values(listOf(*r), null)
    }

    /**
     * Generates a VALUES clause for the specified iterable of record instances.
     *
     *
     * This method creates a `VALUES` clause using the provided [Iterable] of [Record] instances.
     * It is intended to be used within SQL string templates to dynamically construct INSERT statements with
     * the given values.
     *
     *
     * Example usage in a string template:
     * <pre>`INSERT INTO \{MyTable.class}
     * VALUES \{values(records)}
    `</pre> *
     *
     *
     * For convenience, you can also use the shorthand notation. The SQL template engine automatically
     * detects that a VALUES element is required based on its placement in the query:
     * <pre>`INSERT INTO \{MyTable.class}
     * VALUES \{records}
    `</pre> *
     *
     *
     * Here, `records` is an [Iterable] of `Record` instances containing
     * the values to be inserted.
     *
     * @param records an [Iterable] of [Record] instances containing the values to be inserted.
     * @return an [Element] representing the VALUES clause with the specified records.
     */
    fun values(records: Iterable<Record>): Element {
        return Elements.Values(records, null)
    }

    /**
     * Generates a VALUES clause for the specified iterable of record instances.
     *
     *
     * This method creates a `VALUES` clause using the provided [Iterable] of [Record] instances.
     * It is intended to be used within SQL string templates to dynamically construct INSERT statements with
     * the given values.
     *
     *
     * Example usage in a string template:
     * <pre>`INSERT INTO \{MyTable.class}
     * VALUES \{values(records)}
    `</pre> *
     *
     *
     * For convenience, you can also use the shorthand notation. The SQL template engine automatically
     * detects that a VALUES element is required based on its placement in the query:
     * <pre>`INSERT INTO \{MyTable.class}
     * VALUES \{records}
    `</pre> *
     *
     *
     * Here, `records` is an [Iterable] of `Record` instances containing
     * the values to be inserted.
     *
     * @param records an [Iterable] of [Record] instances containing the values to be inserted.
     * @param ignoreAutoGenerate true to ignore the auto-generate flag on the primary key and explicitly insert the
     * provided primary key value. Use this flag only when intentionally providing the primary
     * key value (e.g., migrations, data exports).
     * @return an [Element] representing the VALUES clause with the specified records.
     */
    fun values(records: Iterable<Record>, ignoreAutoGenerate: Boolean): Element {
        return Elements.Values(records, null, ignoreAutoGenerate)
    }

    /**
     * Generates a VALUES clause using the specified [st.orm.BindVars] for batch insertion.
     *
     *
     * This method creates a `VALUES` clause that utilizes a [st.orm.BindVars] instance, allowing for batch
     * insertion of records using bind variables. This is particularly useful when performing batch operations where
     * the same query is executed multiple times with different variable values.
     *
     *
     * Example usage in a batch insertion scenario:
     * <pre>`var bindVars = orm.createBindVars();
     * try (var query = orm.query(RAW."""
     * INSERT INTO \{MyTable.class}
     * VALUES \{values(bindVars)}""").prepare()) {
     * records.forEach(query::addBatch);
     * query.executeBatch();
     * }
    `</pre> *
     *
     *
     * For convenience, you can also use the shorthand notation. The SQL template engine automatically
     * detects that a VALUES element is required based on its placement in the query:
     * <pre>`INSERT INTO \{MyTable.class}
     * VALUES \{bindVars}
    `</pre> *
     *
     *
     * In this example, `bindVars` is a [st.orm.BindVars] instance created by the ORM. The `records` are
     * iterated over, and each is added to the batch. The query is then executed as a batch operation.
     *
     * @param bindVars the [st.orm.BindVars] instance used for batch insertion.
     * @return an [Element] representing the VALUES clause utilizing the specified bind variables.
     */
    fun values(bindVars: BindVars): Element {
        return Elements.Values(null, Objects.requireNonNull(bindVars, "bindVars"))
    }

    /**
     * Generates a VALUES clause using the specified [BindVars] for batch insertion.
     *
     *
     * This method creates a `VALUES` clause that utilizes a [BindVars] instance, allowing for batch
     * insertion of records using bind variables. This is particularly useful when performing batch operations where
     * the same query is executed multiple times with different variable values.
     *
     *
     * Example usage in a batch insertion scenario:
     * <pre>`var bindVars = orm.createBindVars();
     * try (var query = orm.query(RAW."""
     * INSERT INTO \{MyTable.class}
     * VALUES \{values(bindVars)}""").prepare()) {
     * records.forEach(query::addBatch);
     * query.executeBatch();
     * }
    `</pre> *
     *
     *
     * For convenience, you can also use the shorthand notation. The SQL template engine automatically
     * detects that a VALUES element is required based on its placement in the query:
     * <pre>`INSERT INTO \{MyTable.class}
     * VALUES \{bindVars}
    `</pre> *
     *
     *
     * In this example, `bindVars` is a [BindVars] instance created by the ORM. The `records` are
     * iterated over, and each is added to the batch. The query is then executed as a batch operation.
     *
     * @param bindVars the [BindVars] instance used for batch insertion.
     * @param ignoreAutoGenerate true to ignore the auto-generate flag on the primary key and explicitly insert the
     * provided primary key value. Use this flag only when intentionally providing the primary
     * key value (e.g., migrations, data exports).
     * @return an [Element] representing the VALUES clause utilizing the specified bind variables.
     */
    fun values(bindVars: BindVars, ignoreAutoGenerate: Boolean): Element {
        return Elements.Values(null, Objects.requireNonNull(bindVars, "bindVars"), ignoreAutoGenerate)
    }

    /**
     * Generates an UPDATE element for the specified table class.
     *
     *
     * This method creates an `UPDATE` clause for the provided table record. It is designed to be used
     * within SQL string templates to dynamically construct UPDATE queries based on the table's structure.
     *
     *
     * Example usage in a string template:
     * <pre>`UPDATE \{update(MyTable.class)}
     * SET \{set(record)}
     * WHERE \{where(record)}
    `</pre> *
     *
     *
     * For convenience, you can also use the shorthand notation. The SQL template engine automatically detects that
     * an UPDATE element is required based on its placement in the query:
     * <pre>`UPDATE \{MyTable.class}
     * SET \{record}
     * WHERE \{record}
    `</pre> *
     *
     *
     * Here, `record` is an instance of the `Record` class containing the values to be updated.
     *
     * @param table the [Class] object representing the table record.
     * @return an [Element] representing the UPDATE clause for the specified table.
     */
    fun update(table: KClass<out Record>): Element {
        return Elements.Update(table.java)
    }

    /**
     * Generates an UPDATE element for the specified table class with an alias.
     *
     *
     * This method creates an `UPDATE` clause for the provided table record, applying the specified alias.
     * It is designed to be used within SQL string templates to dynamically construct UPDATE queries based on the
     * table's structure.
     *
     *
     * Example usage in a string template:
     * <pre>`UPDATE \{update(MyTable.class, "t")}
     * SET \{set(record)}
     * WHERE \{where(record)}
    `</pre> *
     *
     *
     * Here, `record` is an instance of the `Record` class containing the values to be updated.
     *
     * @param table the [Class] object representing the table record.
     * @param alias the alias to use for the table in the query. The alias must not require escaping.
     * @return an [Element] representing the UPDATE clause for the specified table with alias.
     */
    fun update(table: KClass<out Record>, alias: String): Element {
        return Elements.Update(table.java, alias)
    }

    /**
     * Generates a SET clause for the specified record.
     *
     *
     * This method creates a `SET` clause using the provided [Record] instance. It is intended to be used
     * within SQL string templates to dynamically construct UPDATE statements with the given values.
     *
     *
     * Example usage in a string template:
     * <pre>`UPDATE \{MyTable.class}
     * SET \{set(record)}
     * WHERE \{where(record)}
    `</pre> *
     *
     *
     * For convenience, you can also use the shorthand notation. The SQL template engine automatically detects
     * that a SET element is required based on its placement in the query:
     * <pre>`UPDATE \{MyTable.class}
     * SET \{record}
     * WHERE \{record}
    `</pre> *
     *
     *
     * Here, `record` is an instance of the `Record` class containing the values to be set.
     *
     * @param record the [Record] instance containing the values to be set.
     * @return an [Element] representing the SET clause with the specified record.
     */
    fun set(record: Record): Element {
        return Elements.Set(Objects.requireNonNull(record, "record"), null)
    }

    /**
     * Generates a SET clause using the specified [BindVars].
     *
     *
     * This method creates a `SET` clause that utilizes a [BindVars] instance, allowing for batch
     * updates using bind variables. This is particularly useful when performing batch operations where the same
     * update query is executed multiple times with different variable values.
     *
     *
     * Example usage in a batch update scenario:
     * <pre>`var bindVars = orm.createBindVars();
     * try (var query = orm.query(RAW."""
     * UPDATE \{MyTable.class}
     * SET \{set(bindVars)}
     * WHERE \{where(bindVars)}""").prepare()) {
     * records.forEach(query::addBatch);
     * query.executeBatch();
     * }
    `</pre> *
     *
     *
     * For convenience, you can also use the shorthand notation. The SQL template engine automatically detects that
     * an UPDATE element is required based on its placement in the query:
     * <pre>`UPDATE \{MyTable.class}
     * SET \{record}
     * WHERE \{record}
    `</pre> *
     *
     *
     * In this example, `bindVars` is a [BindVars] instance created by the ORM. The `records` are
     * iterated over, and each is added to the batch. The query is then executed as a batch operation.
     *
     * @param bindVars the [BindVars] instance used for batch updates.
     * @return an [Element] representing the SET clause utilizing the specified bind variables.
     */
    fun set(bindVars: BindVars): Element {
        return Elements.Set(null, Objects.requireNonNull(bindVars, "bindVars"))
    }

    /**
     * Generates a WHERE clause based on the provided iterable of values or records.
     *
     *
     * This method creates a `WHERE` clause that matches the primary key(s) of the root table,
     * a record instance of the root table, or foreign key(s) in the hierarchy against the provided records
     * using the `IN` operator. It is useful when you want to select records where the primary key,
     * a specific record, or related foreign keys match any of the values in the iterable.
     *
     *
     * The objects in the iterable can be:
     *
     *  * Primitive values matching the primary key of the root table.
     *  * Instances of [Record] matching the compound primary key of the root table.
     *  * Instances of [Record] representing records of related (foreign key) tables in the hierarchy of the
     * root table.
     *
     *
     *
     * Example usage with primary key values:
     * <pre>`SELECT \{MyTable.class}
     * FROM \{MyTable.class}
     * WHERE \{where(listOfIds)}
    `</pre> *
     *
     *
     * Example usage with records:
     * <pre>`List<Table> entities = List.of(entity1, entity2);
     * SELECT \{MyTable.class}
     * FROM \{MyTable.class}
     * WHERE \{where(entities)}
    `</pre> *
     *
     *
     * In this example, the query selects all entries in `Table` that are linked to any of the otherTables in the
     * list.
     *
     *
     *
     * For convenience, you can also use the shorthand notation. The SQL template engine automatically detects that
     * a WHERE element is required based on its placement in the query:
     * <pre>`List<Table> entities = List.of(entity1, entity2);
     * SELECT \{MyTable.class}
     * FROM \{MyTable.class}
     * WHERE \{entities}
    `</pre> *
     *
     *
     * As per the resolution rules:
     *
     *  * If `\{entities}`, or any other primitive or object, is placed after WHERE the SQL template engine
     * resolves it into a WHERE element.
     *  * If `\{entities}`, or any other primitive or object, is placed after keywords like VALUES, SET, the SQL
     * template engine resolves it into the appropriate element (e.g., VALUES element, SET element).
     *  * If `\{entities}` is not in such a placement, it is resolved into a param element.
     *
     *
     * @param it an [Iterable] of values or records to match against the primary key(s) or foreign keys.
     * @return an [Element] representing the WHERE clause.
     */
    fun where(it: Iterable<*>): Element {
        return Elements.Where(ObjectExpression(Operator.IN, it), null)
    }

    /**
     * Generates a WHERE clause based on the provided value or record.
     *
     *
     * This method creates a `WHERE` clause that matches the primary key(s) of the root table,
     * a record instance of the root table, or foreign key(s) in the hierarchy against the provided records
     * using the `EQUALS` operator. It is useful when you want to select records where the primary key,
     * a specific record, or related foreign keys match the object.
     *
     *
     * The object can be:
     *
     *  * Primitive values matching the primary key of the root table.
     *  * Instances of [Record] matching the compound primary key of the root table.
     *  * Instances of [Record] representing records of related (foreign key) tables in the hierarchy of the
     * root table.
     *
     *
     *
     * Example usage with a primary key value:
     * <pre>`SELECT \{MyTable.class}
     * FROM \{MyTable.class}
     * WHERE \{where(id)}
    `</pre> *
     *
     *
     * Example usage with a record:
     * <pre>`Table entity = ...;
     * SELECT \{MyTable.class}
     * FROM \{MyTable.class}
     * WHERE \{where(otherTable)}
    `</pre> *
     *
     *
     * In this example, the query selects all entries in `Table` that are linked to the specified `otherTable`.
     *
     *
     * For convenience, you can also use the shorthand notation. The SQL template engine automatically detects that
     * a WHERE element is required based on its placement in the query:
     * <pre>`Table entity = ...;
     * SELECT \{MyTable.class}
     * FROM \{MyTable.class}
     * WHERE \{entity}
    `</pre> *
     *
     *
     * As per the resolution rules:
     *
     *  * If `\{entity}`, or any other primitive or object, is placed after WHERE the SQL template engine
     * resolves it into a WHERE element.
     *  * If `\{entity}`, or any other primitive or object, is placed after keywords like VALUES, SET, the SQL
     * template engine resolves it into the appropriate element (e.g., VALUES element, SET element).
     *  * If `\{entity}` is not in such a placement, it is resolved into a param element.
     *
     *
     * @param o the value or record to match against the primary key or foreign key.
     * @return an [Element] representing the WHERE clause.
     */
    fun where(o: Any): Element {
        return Elements.Where(ObjectExpression(o), null)
    }

    /**
     * Generates a WHERE clause based on the provided path, operator, and iterable of values or records.
     *
     *
     * This method creates a `WHERE` clause for the specified column or path using the given
     * operator and values or records. The `path` parameter specifies the column or property to apply the
     * condition on, which can include nested properties using dot notation.
     *
     *
     * The objects in the iterable must match the type of the record component found at the specified path.
     * If the path points to a record, the objects may also match the primary key type of that record.
     *
     *
     * Example usage with primary keys:
     * <pre>`SELECT \{MyTable.class}
     * FROM \{MyTable.class}
     * WHERE \{where(MyTable_.otherTable.id, Operator.IN, listOfIds)}
    `</pre> *
     *
     *
     * In this example, `listOfIds` contains the primary key values of the `MyTable` records,
     * and the query selects all entries in `Table` linked to that MyTable.
     *
     *
     * Example usage with records:
     * <pre>`List<MyTable> entities = ...;
     * SELECT \{MyTable.class}
     * FROM \{MyTable.class}
     * WHERE \{where(MyTable_.otherTable, Operator.IN, entities)}
    `</pre> *
     *
     *
     * In this example, `entities` is a list of `MyTable` records. The query matches entries in
     * `Table` linked to any of the records in the list via their foreign keys.
     *
     * @param path the path or column name to apply the condition on.
     * @param operator the [Operator] to use in the condition.
     * @param it an [Iterable] of values or records for the condition.
     * @return an [Element] representing the WHERE clause.
     */
    fun <V> where(
        path: Metamodel<*, V>,
        operator: Operator,
        it: Iterable<V>
    ): Element {
        return Elements.Where(ObjectExpression(path, operator, it), null)
    }

    /**
     * Generates a WHERE clause based on the provided path, operator, and values or records.
     *
     *
     * This method creates a `WHERE` clause for the specified column or path using the given
     * operator and values or records. The `path` parameter specifies the column or property to apply the
     * condition on, which can include nested properties using dot notation.
     *
     *
     * The objects in the array must match the type of the record component found at the specified path.
     * If the path points to a record, the objects may also match the primary key type of that record.
     *
     *
     * Example usage with primary keys:
     * <pre>`SELECT \{MyTable.class}
     * FROM \{MyTable.class}
     * WHERE \{where(MyTable_.otherTable.id, Operator.BETWEEN, 1, 10)}
    `</pre> *
     *
     *
     * In this example, the query selects all entries in `Table` where the associated `MyTable`
     * records have primary keys between `1` and `10`.
     *
     * @param path the path or column name to apply the condition on.
     * @param operator the [Operator] to use in the condition.
     * @param o the values or records for the condition.
     * @return an [Element] representing the WHERE clause.
     */
    fun <V> where(path: Metamodel<*, V>, operator: Operator, vararg o: V): Element {
        return Elements.Where(ObjectExpression(path, operator, o), null)
    }

    /**
     * Generates a WHERE clause using the specified [BindVars] for batch operations.
     *
     *
     * This method is particularly useful when performing batch operations where the same query is executed
     * multiple times with different parameter values. The [BindVars] instance allows for parameterized
     * queries, enhancing performance and security by preventing SQL injection and enabling query plan reuse.
     *
     *
     * Example usage in a batch operation:
     * <pre>`var bindVars = orm.createBindVars();
     * try (var query = orm.query(RAW."""
     * UPDATE \{MyTable.class}
     * SET \{bindVars}
     * WHERE \{where(bindVars)}""").prepare()) {
     * records.forEach(query::addBatch);
     * query.executeBatch();
     * }
    `</pre> *
     *
     *
     * In this example, the `bindVars` instance is used to bind variables for the WHERE clause in a batch
     * operation. Each record in `records` provides the parameter values for a single execution of the query.
     *
     *
     * For convenience, you can also use the shorthand notation. The SQL template engine automatically detects that
     * a WHERE element is required based on its placement in the query:
     * <pre>`UPDATE \{MyTable.class}
     * SET \{bindVars}
     * WHERE \{bindVars}
    `</pre> *
     *
     * @param bindVars the [BindVars] instance used for binding variables in the WHERE clause; must not be
     * `null`.
     * @return an [Element] representing the WHERE clause utilizing the specified bind variables.
     */
    fun where(bindVars: BindVars): Element {
        return Elements.Where(null, Objects.requireNonNull(bindVars, "bindVars"))
    }

    /**
     * Generates a DELETE element for the specified table class.
     *
     *
     * This method creates a `DELETE` clause for the provided table record. It is designed to be used
     * within SQL string templates to dynamically construct DELETE queries based on the table's structure.
     *
     *
     * Example usage in a string template:
     * <pre>`DELETE \{delete(MyTable.class)} FROM \{from(MyTable.class)}
     * WHERE \{where(record)}
    `</pre> *
     *
     *
     * For convenience, you can also use the shorthand notation. The SQL template engine automatically detects that
     * a DELETE element is required based on its placement in the query:
     * <pre>`DELETE \{MyTable.class} FROM \{MyTable.class}
     * WHERE \{record}
    `</pre> *
     *
     *
     * Here, `record` is an instance of the [Record] class containing the criteria for deletion.
     *
     *
     * **Note:** In most databases, specifying the table in the DELETE clause is not
     * necessary, or even disallowed; the DELETE statement is usually constructed with only a FROM clause:
     *
     * <pre>`DELETE FROM \{from(MyTable.class)}
     * WHERE \{where(record)}
    `</pre> *
     *
     * @param table the [Class] object representing the table record.
     * @return an [Element] representing the DELETE clause for the specified table.
     */
    fun delete(table: KClass<out Record>): Element {
        return Elements.Delete(table.java)
    }

    /**
     * Generates a DELETE element for the specified table class with an alias.
     *
     *
     * This method creates a `DELETE` clause for the provided table record, applying the specified alias.
     * It is designed to be used within SQL string templates to dynamically construct DELETE queries based on the
     * table's structure.
     *
     *
     * Example usage in a string template:
     * <pre>`DELETE \{delete(MyTable.class, "t")} FROM \{from(MyTable.class, "t")}
     * WHERE \{where(record)}
    `</pre> *
     *
     *
     * Here, `record` is an instance of the [Record] class containing the criteria for deletion.
     *
     *
     * **Note:** In most databases, specifying the table in the DELETE clause with an alias
     * is not necessary; the DELETE statement can be constructed with only a FROM clause and an alias:
     *
     * <pre>`DELETE FROM \{from(MyTable.class, "t")}
     * WHERE \{where(record)}
    `</pre> *
     *
     * @param table the [Class] object representing the table record.
     * @param alias the alias to use for the table in the query. The alias must not require escaping.
     * @return an [Element] representing the DELETE clause for the specified table with an alias.
     */
    fun delete(table: KClass<out Record>, alias: String): Element {
        return Elements.Delete(table.java, alias)
    }

    /**
     * Generates a Table element for the specified table class.
     *
     *
     * This method creates a representation of a database table, which can be used in SQL string templates
     * to refer to the table in queries. It is useful when you need to explicitly specify the table in parts of
     * your query where the SQL template engine does not automatically resolve the table based on context.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT * FROM \{table(MyTable.class)}
    `</pre> *
     *
     *
     * For convenience, you can also use the shorthand notation. If the SQL template engine cannot resolve
     * `\{MyTable.class}` into a specific element based on its placement in the query (e.g., after SELECT, FROM, etc.),
     * it will default to creating a Table element:
     * <pre>`... \{MyTable.class} ...
    `</pre> *
     *
     *
     * However, if `\{MyTable.class}` is followed by a dot `'.'`, the SQL template engine will resolve it into an
     * alias element, representing the alias of the table in the query:
     * <pre>`SELECT \{MyTable.class}.column_name FROM \{MyTable.class}
    `</pre> *
     *
     *
     * As per the resolution rules:
     *
     *  * If `\{MyTable.class}` is placed after keywords like SELECT, FROM, INSERT INTO, UPDATE, or DELETE,
     * the SQL template engine resolves it into the appropriate element (e.g., SELECT element, FROM element).
     *  * If `\{MyTable.class}` is not in such a placement and is not followed by a dot `'.'`,
     * it is resolved into a table element.
     *  * If `\{MyTable.class}` is followed by a dot `'.'`, it is resolved into an alias element.
     *
     *
     * @param table the [Class] object representing the table record.
     * @return an [Element] representing the table.
     */
    fun table(table: KClass<out Record>): Element {
        return Elements.Table(table.java)
    }

    /**
     * Generates a Table element with an alias for the specified table class.
     *
     *
     * This method creates a representation of a database table with the specified alias, which can be used in
     * SQL string templates to refer to the table in queries. This is useful when you need to assign an alias to
     * a table for use in your query.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT * FROM \{table(MyTable.class, "t")}
    `</pre> *
     *
     *
     * You can refer to the table alias in your query as follows:
     * <pre>`SELECT \{alias(MyTable.class)}.column_name FROM \{table(MyTable.class, "t")}
    `</pre> *
     *
     * @param table the [Class] object representing the table record.
     * @param alias the alias to use for the table in the query. The alias must not require escaping.
     * @return an [Element] representing the table with an alias.
     */
    fun table(table: KClass<out Record>, alias: String): Element {
        return Elements.Table(table.java, alias)
    }

    /**
     * Generates an alias element for the specified table class.
     *
     *
     * This method returns the alias of the table as used in the query. It is useful when you need to refer to
     * the table's alias, especially in situations where the SQL template engine cannot automatically determine
     * the appropriate element based on context.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT \{alias(MyTable.class)}.column_name FROM \{table(MyTable.class, "t")}
    `</pre> *
     *
     *
     * According to the resolution rules, if `\{MyTable.class}` is followed by a dot `'.'`, the SQL template engine
     * automatically resolves it into an alias element:
     * <pre>`SELECT \{MyTable.class}.column_name FROM \{MyTable.class}
    `</pre> *
     *
     *
     * As per the resolution rules:
     *
     *  * If `\{MyTable.class}` is placed after keywords like SELECT, FROM, INSERT INTO, UPDATE, or DELETE,
     * the SQL template engine resolves it into the appropriate element (e.g., SELECT element, FROM element).
     *  * If `\{MyTable.class}` is not in such a placement and is not followed by a dot `'.'`,
     * it is resolved into a table element.
     *  * If `\{MyTable.class}` is followed by a dot `'.'`, it is resolved into an alias element.
     *
     *
     * @param table the [Class] object representing the table record.
     * @return an [Element] representing the table's alias.
     */
    fun alias(table: KClass<out Record>): Element {
        return Elements.Alias(table.java, ResolveScope.CASCADE)
    }

    /**
     * Generates an alias element for the specified table class.
     *
     *
     * This method returns the alias of the table as used in the query. It is useful when you need to refer to
     * the table's alias, especially in situations where the SQL template engine cannot automatically determine
     * the appropriate element based on context.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT \{alias(MyTable.class)}.column_name FROM \{table(MyTable.class, "t")}
    `</pre> *
     *
     *
     * According to the resolution rules, if `\{MyTable.class}` is followed by a dot `'.'`, the SQL template engine
     * automatically resolves it into an alias element:
     * <pre>`SELECT \{MyTable.class}.column_name FROM \{MyTable.class}
    `</pre> *
     *
     *
     * As per the resolution rules:
     *
     *  * If `\{MyTable.class}` is placed after keywords like SELECT, FROM, INSERT INTO, UPDATE, or DELETE,
     * the SQL template engine resolves it into the appropriate element (e.g., SELECT element, FROM element).
     *  * If `\{MyTable.class}` is not in such a placement and is not followed by a dot `'.'`,
     * it is resolved into a table element.
     *  * If `\{MyTable.class}` is followed by a dot `'.'`, it is resolved into an alias element.
     *
     *
     * @param table the [Class] object representing the table record.
     * @param scope the [ResolveScope] to use when resolving the alias. Use CASCADE to include local and outer
     * aliases, LOCAL to include local aliases only, and OUTER to include outer aliases only.
     * @return an [Element] representing the table's alias.
     */
    fun alias(table: KClass<out Record>, scope: ResolveScope): Element {
        return Elements.Alias(table.java, scope)
    }

    /**
     * Generates a column element for a column specified by the given `metamodel` in a type safe manner.
     *
     *
     * The path is constructed by concatenating the names of the fields that lead to the target table from the root
     * table. The componentName is the name of the record component that is mapped to the database column. If a record
     * uses inline records, the componentName is also constructed by concatenating the fields leading to the record
     * component.
     *
     *
     * Example usage in a string template where `MyTable` is referenced twice:
     * <pre>`// Define a record with two references to MyTable
     * record Table(int id, MyTable child, MyTable parent) {}
     *
     * // In the SQL template
     * SELECT \{column(MyTable_.child.name)} FROM \{MyTable.class}
    `</pre> *
     *
     *
     * In this example, the path "child" specifies that we are referring to the `child` field of the
     * `Table` record, which is of type `MyTable`. This distinguishes it from the `parent` field, which
     * is also of type `MyTable`. The "name" componentName refers to the name record component of `MyTable`.
     *
     * @param path specifies the database column for which the column is to be generated.
     * @return an [Element] representing the table's column with the specified path.
     * @since 1.2
     */
    fun column(path: Metamodel<*, *>): Element {
        return Elements.Column(path, ResolveScope.CASCADE)
    }

    /**
     * Generates a column element for a column specified by the given `metamodel` in a type safe manner.
     *
     *
     * The path is constructed by concatenating the names of the fields that lead to the target table from the root
     * table. The componentName is the name of the record component that is mapped to the database column. If a record
     * uses inline records, the componentName is also constructed by concatenating the fields leading to the record
     * component.
     *
     *
     * Example usage in a string template where `MyTable` is referenced twice:
     * <pre>`// Define a record with two references to MyTable
     * record Table(int id, MyTable child, MyTable parent) {}
     *
     * // In the SQL template
     * SELECT \{column(MyTable_.child.name)} FROM \{MyTable.class}
    `</pre> *
     *
     *
     * In this example, the path "child" specifies that we are referring to the `child` field of the
     * `Table` record, which is of type `MyTable`. This distinguishes it from the `parent` field, which
     * is also of type `MyTable`. The "name" componentName refers to the name record component of `MyTable`.
     *
     * @param path specifies the database column for which the column is to be generated.
     * @param scope the [ResolveScope] to use when resolving the alias. Use CASCADE to include local and outer
     * aliases, LOCAL to include local aliases only, and OUTER to include outer aliases only.
     * @return an [Element] representing the table's column with the specified path.
     * @since 1.2
     */
    fun column(path: Metamodel<*, *>, scope: ResolveScope): Element {
        return Elements.Column(path, scope)
    }

    /**
     * Generates a parameter element for the specified value, to be used in SQL queries.
     *
     *
     * This method creates a positional parameter for use in SQL string templates. The parameter
     * can be of any object type and may be `null`. It is intended to be used in places where
     * you need to bind a value to a SQL query parameter.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT *
     * FROM \{MyTable.class}
     * WHERE status = \{param(1)}
    `</pre> *
     *
     *
     * For convenience, you can also use the shorthand notation, where the SQL template engine
     * automatically detects that a parameter is required:
     * <pre>`SELECT *
     * FROM \{MyTable.class}
     * WHERE status = \{1}
    `</pre> *
     *
     *
     * As per the resolution rules:
     *
     *  * If `\{1}`, or any other primitive or object, is placed after keywords like VALUES, SET or WHERE,
     * the SQL template engine resolves it into the appropriate element (e.g., VALUES element, SET element).
     *  * If `\{1}` is not in such a placement, it is resolved into a param element.
     *
     *
     * @param value the value to be used as a parameter in the SQL query; may be `null`.
     * @return an [Element] representing the parameter.
     */
    fun param(value: Any?): Element {
        return Elements.Param(null, value)
    }

    /**
     * Generates a named parameter element for the specified value, to be used in SQL queries.
     *
     *
     * This method creates a named parameter for use in SQL string templates. Named parameters
     * are useful when you want to explicitly specify the parameter's name in the query, improving
     * readability and maintainability, especially in complex queries.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT *
     * FROM \{MyTable.class}
     * WHERE status = \{param("status", 1)}
    `</pre> *
     *
     *
     * In the query, the parameter will be referred to by its name `status`.
     *
     * @param name the name of the parameter; must not be `null`.
     * @param value the value to be used as a parameter in the SQL query; may be `null`.
     * @return an [Element] representing the named parameter.
     */
    fun param(name: String, value: Any?): Element {
        return Elements.Param(name, value)
    }

    /**
     * Generates a parameter element for the specified value with a converter function.
     *
     *
     * This method allows you to provide a custom converter function to transform the value
     * into a database-compatible format. This is useful when the value needs to be converted
     * before being set as a parameter in the SQL query, such as formatting dates or custom objects.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT *
     * FROM \{MyTable.class}
     * WHERE created_at = \{param(dateValue, date -> new java.sql.Date(date.getTime()))}
    `</pre> *
     *
     * @param <P> the type of the value to be converted.
     * @param value the value to be used as a parameter in the SQL query; may be `null`.
     * @param converter a [Function] that converts the value to a database-compatible format; must not be
     * `null`.
     * @return an [Element] representing the parameter with a converter applied.
    </P> */
    fun <P> param(value: P, converter: (P) -> Any?): Element {
        return Elements.Param(null, value) {
            @Suppress("UNCHECKED_CAST")
            converter(it as P)
        }
    }

    /**
     * Generates a named parameter element for the specified value with a converter function.
     *
     *
     * This method allows you to provide a custom converter function to transform the value
     * into a database-compatible format and assign a name to the parameter. Named parameters
     * enhance query readability and are particularly useful in complex queries.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT *
     * FROM \{MyTable.class}
     * WHERE created_at = \{param("createdAt", dateValue, date -> new java.sql.Date(date.getTime()))}
    `</pre> *
     *
     * @param <P> the type of the value to be converted.
     * @param name the name of the parameter; must not be `null`.
     * @param value the value to be used as a parameter in the SQL query; may be `null`.
     * @param converter a [Function] that converts the value to a database-compatible format; must not be
     * `null`.
     * @return an [Element] representing the named parameter with a converter applied.
    </P> */
    fun <P> param(name: String, value: P, converter: (P) -> Any?): Element {
        return Elements.Param(name, value) {
            @Suppress("UNCHECKED_CAST")
            converter(it as P)
        }
    }

    /**
     * Generates a parameter element for the specified [Date] value with a temporal type.
     *
     *
     * This method creates a positional parameter for a [Date] value, converting it to the appropriate
     * SQL type based on the provided [st.orm.TemporalType]. It is useful when you need to specify how the date
     * should be interpreted in the database (as DATE, TIME, or TIMESTAMP).
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT *
     * FROM \{MyTable.class}
     * WHERE event_date = \{param(dateValue, TemporalType.DATE)}
    `</pre> *
     *
     * @param value the [Date] value to be used as a parameter; must not be `null`.
     * @param temporalType the [st.orm.TemporalType] specifying how the date should be handled; must not be `null`.
     * @return an [Element] representing the date parameter with the specified temporal type.
     */
    fun param(value: Date, temporalType: TemporalType): Element {
        return param(value) {
            when (temporalType) {
                TemporalType.DATE -> java.sql.Date(it.time)
                TemporalType.TIME -> Time(it.time)
                TemporalType.TIMESTAMP -> Timestamp(it.time)
            }
        }
    }

    /**
     * Generates a named parameter element for the specified [Date] value with a temporal type.
     *
     *
     * This method creates a named parameter for a [Date] value, converting it to the appropriate
     * SQL type based on the provided [TemporalType]. Named parameters improve query readability
     * and are especially useful in complex queries.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT *
     * FROM \{MyTable.class}
     * WHERE event_date = \{param("eventDate", dateValue, TemporalType.DATE)}
    `</pre> *
     *
     * @param name the name of the parameter; must not be `null`.
     * @param value the [Date] value to be used as a parameter; must not be `null`.
     * @param temporalType the [TemporalType] specifying how the date should be handled; must not be `null`.
     * @return an [Element] representing the named date parameter with the specified temporal type.
     */
    fun param(name: String, value: Date, temporalType: TemporalType): Element {
        return param(name, value) {
            when (temporalType) {
                TemporalType.DATE -> java.sql.Date(it.time)
                TemporalType.TIME -> Time(it.time)
                TemporalType.TIMESTAMP -> Timestamp(it.time)
            }
        }
    }

    /**
     * Generates a parameter element for the specified [Calendar] value with a temporal type.
     *
     *
     * This method creates a positional parameter for a [Calendar] value, converting it to the appropriate
     * SQL type based on the provided [TemporalType]. It is useful when working with calendar instances
     * that need to be interpreted in the database as specific temporal types.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT *
     * FROM \{MyTable.class}
     * WHERE event_time = \{param(calendarValue, TemporalType.TIMESTAMP)}
    `</pre> *
     *
     * @param value the [Calendar] value to be used as a parameter; must not be `null`.
     * @param temporalType the [TemporalType] specifying how the calendar should be handled; must not be
     * `null`.
     * @return an [Element] representing the calendar parameter with the specified temporal type.
     */
    fun param(value: Calendar, temporalType: TemporalType): Element {
        return param(value) {
            when (temporalType) {
                TemporalType.DATE -> java.sql.Date(it.timeInMillis)
                TemporalType.TIME -> Time(it.timeInMillis)
                TemporalType.TIMESTAMP -> Timestamp(it.timeInMillis)
            }
        }
    }

    /**
     * Generates a named parameter element for the specified [Calendar] value with a temporal type.
     *
     *
     * This method creates a named parameter for a [Calendar] value, converting it to the appropriate
     * SQL type based on the provided [TemporalType]. Named parameters enhance query readability
     * and are particularly useful in complex queries.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT *
     * FROM \{MyTable.class}
     * WHERE event_time = \{param("eventTime", calendarValue, TemporalType.TIMESTAMP)}
    `</pre> *
     *
     * @param name the name of the parameter; must not be `null`.
     * @param value the [Calendar] value to be used as a parameter; must not be `null`.
     * @param temporalType the [TemporalType] specifying how the calendar should be handled; must not be
     * `null`.
     * @return an [Element] representing the named calendar parameter with the specified temporal type.
     */
    fun param(name: String, value: Calendar, temporalType: TemporalType): Element {
        return param(name, value) {
            when (temporalType) {
                TemporalType.DATE -> java.sql.Date(it.timeInMillis)
                TemporalType.TIME -> Time(it.timeInMillis)
                TemporalType.TIMESTAMP -> Timestamp(it.timeInMillis)
            }
        }
    }

    /**
     * Creates a new var element that can be used to specify individual bind variables in the query.
     *
     * @param bindVars the bind variables instance used for parameter binding.
     * @param extractor the function used to extract the value from the record for the bind variable.
     * @return a new [Element] representing the bind variable.
     */
    fun bindVar(bindVars: BindVars, extractor: (Record) -> Any): Element {
        return Elements.BindVar(bindVars, extractor)
    }

    /**
     * Creates a new subquery element using a query builder.
     *
     * @param builder   the query builder used to construct the subquery; must not be null.
     * @param correlate a flag indicating whether the subquery should correlate with the outer query.
     * If `true`, the subquery can reference elements from the outer query.
     * If `false`, the subquery is independent and does not access the outer query.
     * @return a new `Subquery` element based on the provided query builder and correlation flag.
     */
    fun subquery(builder: QueryBuilder<*, *, *>, correlate: Boolean): Element {
        return Elements.Subquery((builder as Subqueryable).subquery, correlate)
    }

    /**
     * Creates a new subquery element using a string template.
     *
     * @param builder  the string template representing the subquery; must not be null.
     * @param correlate a flag indicating whether the subquery should correlate with the outer query.
     * If `true`, the subquery can reference elements from the outer query.
     * If `false`, the subquery is independent and does not access the outer query.
     * @return a new `Subquery` element based on the provided template and correlation flag.
     */
    fun subquery(builder: TemplateBuilder, correlate: Boolean): Element {
        return subquery(builder.build(), correlate)
    }

    /**
     * Creates a new subquery element using a string template.
     *
     * @param template  the string template representing the subquery; must not be null.
     * @param correlate a flag indicating whether the subquery should correlate with the outer query.
     * If `true`, the subquery can reference elements from the outer query.
     * If `false`, the subquery is independent and does not access the outer query.
     * @return a new `Subquery` element based on the provided template and correlation flag.
     */
    fun subquery(template: TemplateString, correlate: Boolean): Element {
        return Elements.Subquery(template.unwrap, correlate)
    }

    /**
     * Injects raw SQL into the query without any processing or sanitization.
     *
     *
     * This method allows you to insert arbitrary SQL code directly into your query. It bypasses any
     * automatic parameter binding or SQL generation provided by the SQL template engine. As a result, it can be
     * potentially unsafe and may expose your application to SQL injection attacks if not used carefully.
     *
     *
     * **Warning:** Use this method only when you are certain that the SQL string being injected
     * is safe and originates from a trusted source. Avoid using user-supplied input with this method.
     *
     *
     * Example usage in a string template:
     * <pre>`SELECT * FROM \{User.class} WHERE \{unsafe("city = 'Sunnyvale'")}
    `</pre> *
     *
     *
     * In this example, the SQL fragment `"city = 'Sunnyvale'"` is injected directly into the query.
     *
     * @param sql the raw SQL string to inject into the query.
     * @return an [Element] that represents the raw SQL code to be inserted into the query.
     */
    fun unsafe(sql: String): Element {
        return Elements.Unsafe(sql)
    }
}
