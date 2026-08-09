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
package st.orm.template

import st.orm.Data
import st.orm.EntityCallback
import st.orm.StormConfig
import st.orm.mapping.TemplateDecorator
import st.orm.repository.RepositoryLookup
import st.orm.template.impl.ORMTemplateImpl
import java.sql.Connection
import javax.sql.DataSource
import kotlin.reflect.KClass

/**
 * The primary entry point for Storm's ORM functionality in Kotlin, combining SQL template query construction with
 * repository access.
 *
 * `ORMTemplate` extends both [QueryTemplate] (for constructing and executing SQL queries) and
 * [RepositoryLookup] (for obtaining type-safe [st.orm.repository.EntityRepository] and
 * [st.orm.repository.ProjectionRepository] instances). It is the central interface from which all database
 * operations originate.
 *
 * Instances can be created using the companion object factory methods [of], or via the Kotlin extension
 * properties [DataSource.orm] and [Connection.orm]. For JPA-based usage, see `JpaTemplate`.
 *
 * ## Example
 * ```kotlin
 * val orm = dataSource.orm
 *
 * // Repository-based access
 * val users = orm.entity(User::class)
 * val user = users.findById(42)
 *
 * // Template-based query
 * val result = orm.query("SELECT ${User::class} FROM ${User::class} WHERE ${User_.name} = ${"Alice"}")
 *     .getResultList(User::class)
 * ```
 *
 * @see st.orm.repository.EntityRepository
 * @see st.orm.repository.ProjectionRepository
 */
public interface ORMTemplate :
    QueryTemplate,
    RepositoryLookup {

    /**
     * Returns a new [ORMTemplate] with the specified entity callback added.
     *
     * The returned template shares the same underlying connection and configuration, but applies the given
     * callback to entity lifecycle operations (insert, update, delete) performed through its repositories. The
     * callback is only invoked for entities matching its type parameter. Multiple callbacks can be registered by
     * chaining calls to this method.
     *
     * @param callback the entity callback to add.
     * @return a new [ORMTemplate] with the callback added.
     * @since 1.9
     */
    public fun withEntityCallback(callback: EntityCallback<*>): ORMTemplate

    /**
     * Returns a new [ORMTemplate] with the specified entity callbacks added.
     *
     * The returned template shares the same underlying connection and configuration, but applies the given
     * callbacks to entity lifecycle operations (insert, update, delete) performed through its repositories. Each
     * callback is only invoked for entities matching its type parameter.
     *
     * @param callbacks the entity callbacks to add.
     * @return a new [ORMTemplate] with the callbacks added.
     * @since 1.9
     */
    public fun withEntityCallbacks(callbacks: List<EntityCallback<*>>): ORMTemplate

    /**
     * Validates all discovered entity and projection types against the database schema.
     *
     * Logs each validation error and returns the list of error messages. On success, logs a
     * confirmation message and returns an empty list.
     *
     * This method requires a DataSource-backed template. Templates created from a raw
     * [java.sql.Connection] or `EntityManager` do not support schema validation.
     *
     * @return the list of validation error messages (empty on success).
     * @throws st.orm.PersistenceException if the template does not support schema validation.
     * @since 1.9
     */
    public fun validateSchema(): List<String>

    /**
     * Validates discovered types matching the filter against the database schema.
     *
     * Discovers all entity and projection types via the classpath index, applies the given filter, and validates
     * the matching types. This is useful when a single application connects to multiple datasources and only a
     * subset of types is reachable from each connection.
     *
     * Logs each validation error and returns the list of error messages. On success, logs a
     * confirmation message and returns an empty list.
     *
     * This method requires a DataSource-backed template. Templates created from a raw
     * [java.sql.Connection] or `EntityManager` do not support schema validation.
     *
     * @param filter predicate to select which discovered types to validate.
     * @return the list of validation error messages (empty on success).
     * @throws st.orm.PersistenceException if the template does not support schema validation.
     * @since 1.11
     */
    public fun validateSchema(filter: (KClass<out Data>) -> Boolean): List<String>

    /**
     * Validates the specified types against the database schema.
     *
     * Logs each validation error and returns the list of error messages. On success, logs a
     * confirmation message and returns an empty list.
     *
     * This method requires a DataSource-backed template. Templates created from a raw
     * [java.sql.Connection] or `EntityManager` do not support schema validation.
     *
     * @param types the entity and projection types to validate.
     * @return the list of validation error messages (empty on success).
     * @throws st.orm.PersistenceException if the template does not support schema validation.
     * @since 1.9
     */
    public fun validateSchema(vararg types: KClass<out Data>): List<String>

    /**
     * Validates all discovered types and throws if any errors are found.
     *
     * This method requires a DataSource-backed template. Templates created from a raw
     * [java.sql.Connection] or `EntityManager` do not support schema validation.
     *
     * @throws st.orm.PersistenceException if validation fails or the template does not support schema validation.
     * @since 1.9
     */
    public fun validateSchemaOrThrow()

    /**
     * Validates discovered types matching the filter and throws if any errors are found.
     *
     * Discovers all entity and projection types via the classpath index, applies the given filter, and validates
     * the matching types. This is useful when a single application connects to multiple datasources and only a
     * subset of types is reachable from each connection.
     *
     * This method requires a DataSource-backed template. Templates created from a raw
     * [java.sql.Connection] or `EntityManager` do not support schema validation.
     *
     * @param filter predicate to select which discovered types to validate.
     * @throws st.orm.PersistenceException if validation fails or the template does not support schema validation.
     * @since 1.11
     */
    public fun validateSchemaOrThrow(filter: (KClass<out Data>) -> Boolean)

    /**
     * Validates the specified types and throws if any errors are found.
     *
     * This method requires a DataSource-backed template. Templates created from a raw
     * [java.sql.Connection] or `EntityManager` do not support schema validation.
     *
     * @param types the entity and projection types to validate.
     * @throws st.orm.PersistenceException if validation fails or the template does not support schema validation.
     * @since 1.9
     */
    public fun validateSchemaOrThrow(vararg types: KClass<out Data>)

    public companion object {
        /**
         * Returns an [ORMTemplate] for use with JDBC.
         *
         * This method creates an ORM repository template using the provided [DataSource].
         * It allows you to perform database operations using JDBC in a type-safe manner.
         *
         * Example usage:
         * ```
         * DataSource dataSource = ...;
         * ORMTemplate orm = ORMTemplate.of(dataSource);
         * List<MyTable> otherTables = orm.query(RAW."""
         * SELECT \{MyTable.class}
         * FROM \{MyTable.class}
         * WHERE \{MyTable_.name} = \{"ABC"}""")
         * .getResultList(MyTable.class);
         * ```
         *
         * @param dataSource the [DataSource] to use for database operations; must not be `null`.
         * @return an [ORMTemplate] configured for use with JDBC.
         */
        public fun of(dataSource: DataSource): ORMTemplate = ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(dataSource))

        /**
         * Returns an [ORMTemplate] for use with JDBC.
         *
         * This method creates an ORM repository template using the provided [Connection].
         * It allows you to perform database operations using JDBC in a type-safe manner.
         *
         * **Note:** The caller is responsible for closing the connection after usage.
         *
         * Example usage:
         * ```
         * try (Connection connection = ...) {
         * ORMTemplate orm = ORMTemplate.of(connection);
         * List<MyTable> otherTables = orm.query(RAW."""
         * SELECT \{MyTable.class}
         * FROM \{MyTable.class}
         * WHERE \{MyTable_.name} = \{"ABC"}""")
         * .getResultList(MyTable.class)
         * }
         * ```
         *
         * @param connection the [Connection] to use for database operations; must not be `null`.
         * @return an [ORMTemplate] configured for use with JDBC.
         */
        public fun of(connection: Connection): ORMTemplate = ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(connection))

        /**
         * Returns an [ORMTemplate] for use with JDBC, with a custom template decorator.
         *
         * This method creates an ORM repository template using the provided [DataSource] and applies
         * the specified decorator to customize template processing behavior.
         *
         * @param dataSource the [DataSource] to use for database operations.
         * @param decorator a function that transforms the [TemplateDecorator] to customize template processing.
         * @return an [ORMTemplate] configured for use with JDBC.
         */
        public fun of(
            dataSource: DataSource,
            decorator: (TemplateDecorator) -> TemplateDecorator,
        ): ORMTemplate = ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(dataSource, decorator))

        /**
         * Returns an [ORMTemplate] for use with JDBC, with a custom template decorator.
         *
         * This method creates an ORM repository template using the provided [Connection] and applies
         * the specified decorator to customize template processing behavior.
         *
         * **Note:** The caller is responsible for closing the connection after usage.
         *
         * @param connection the [Connection] to use for database operations.
         * @param decorator a function that transforms the [TemplateDecorator] to customize template processing.
         * @return an [ORMTemplate] configured for use with JDBC.
         */
        public fun of(
            connection: Connection,
            decorator: (TemplateDecorator) -> TemplateDecorator,
        ): ORMTemplate = ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(connection, decorator))

        /**
         * Returns an [ORMTemplate] for use with JDBC, configured with the provided [StormConfig].
         *
         * @param dataSource the [DataSource] to use for database operations.
         * @param config the Storm configuration to apply.
         * @return an [ORMTemplate] configured for use with JDBC.
         */
        public fun of(dataSource: DataSource, config: StormConfig): ORMTemplate = ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(dataSource, config))

        /**
         * Returns an [ORMTemplate] for use with JDBC, configured with the provided [StormConfig] and a custom
         * template decorator.
         *
         * @param dataSource the [DataSource] to use for database operations.
         * @param config the Storm configuration to apply.
         * @param decorator a function that transforms the [TemplateDecorator] to customize template processing.
         * @return an [ORMTemplate] configured for use with JDBC.
         */
        public fun of(
            dataSource: DataSource,
            config: StormConfig,
            decorator: (TemplateDecorator) -> TemplateDecorator,
        ): ORMTemplate = ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(dataSource, config, decorator))

        /**
         * Returns an [ORMTemplate] for use with JDBC, configured with the provided [StormConfig].
         *
         * **Note:** The caller is responsible for closing the connection after usage.
         *
         * @param connection the [Connection] to use for database operations.
         * @param config the Storm configuration to apply.
         * @return an [ORMTemplate] configured for use with JDBC.
         */
        public fun of(connection: Connection, config: StormConfig): ORMTemplate = ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(connection, config))

        /**
         * Returns an [ORMTemplate] for use with JDBC, configured with the provided [StormConfig] and a custom
         * template decorator.
         *
         * **Note:** The caller is responsible for closing the connection after usage.
         *
         * @param connection the [Connection] to use for database operations.
         * @param config the Storm configuration to apply.
         * @param decorator a function that transforms the [TemplateDecorator] to customize template processing.
         * @return an [ORMTemplate] configured for use with JDBC.
         */
        public fun of(
            connection: Connection,
            config: StormConfig,
            decorator: (TemplateDecorator) -> TemplateDecorator,
        ): ORMTemplate = ORMTemplateImpl(st.orm.core.template.ORMTemplate.of(connection, config, decorator))

        /**
         * Returns a builder for constructing an [ORMTemplate] with instance-scoped integration strategies.
         *
         * The builder is the injection point for platform services: integrations hand their connection provider,
         * transaction template provider, exception mapper and query observer to the template they construct, instead
         * of relying on JVM-global discovery.
         *
         * @param dataSource the [DataSource] to use for database operations.
         * @return a builder for constructing the ORM template.
         * @since 1.13
         */
        public fun builder(dataSource: DataSource): Builder = Builder(st.orm.core.template.ORMTemplate.builder(dataSource))

        /**
         * Returns a builder for constructing an [ORMTemplate] backed by a single connection, with instance-scoped
         * integration strategies.
         *
         * **Note:** The caller is responsible for closing the connection after usage.
         *
         * @param connection the [Connection] to use for database operations.
         * @return a builder for constructing the ORM template.
         * @since 1.13
         */
        public fun builder(connection: Connection): Builder = Builder(st.orm.core.template.ORMTemplate.builder(connection))
    }

    /**
     * Builder for constructing an [ORMTemplate] with instance-scoped integration strategies.
     *
     * @since 1.13
     */
    public class Builder internal constructor(private val core: st.orm.core.template.ORMTemplate.Builder) {

        /**
         * Sets the Storm configuration to apply to the template instance.
         */
        public fun config(config: StormConfig): Builder = apply { core.config(config) }

        /**
         * Sets a function that transforms the [TemplateDecorator] to customize template processing.
         */
        public fun decorator(decorator: (TemplateDecorator) -> TemplateDecorator): Builder = apply { core.decorator(decorator) }

        /**
         * Sets the connection provider used by the template to acquire and release connections.
         *
         * Only valid for data source backed templates; [build] fails fast otherwise.
         */
        public fun connectionProvider(connectionProvider: st.orm.core.spi.ConnectionProvider): Builder = apply { core.connectionProvider(connectionProvider) }

        /**
         * Sets the transaction template provider used by the template to participate in transactions.
         *
         * Templates that should share transactions must be configured with the *same provider instance*.
         */
        public fun transactionTemplateProvider(transactionTemplateProvider: st.orm.core.spi.TransactionTemplateProvider): Builder = apply { core.transactionTemplateProvider(transactionTemplateProvider) }

        /**
         * Sets the exception mapper that maps failures raised during query execution to the runtime exception thrown
         * to the caller.
         */
        public fun exceptionMapper(exceptionMapper: st.orm.core.spi.ExceptionMapper): Builder = apply { core.exceptionMapper(exceptionMapper) }

        /**
         * Sets the query observer that is notified of query executions performed by the template.
         */
        public fun queryObserver(queryObserver: st.orm.core.spi.QueryObserver): Builder = apply { core.queryObserver(queryObserver) }

        /**
         * Sets the SQL commenter that appends per-execution comment content to statements, such as the
         * current trace context. Note that per-execution content defeats prepared statement caching.
         *
         * @since 1.13
         */
        public fun sqlCommenter(sqlCommenter: st.orm.core.spi.SqlCommenter): Builder = apply { core.sqlCommenter(sqlCommenter) }

        /**
         * Builds the ORM template.
         */
        public fun build(): ORMTemplate = ORMTemplateImpl(core.build())
    }
}

/**
 * Creates an [ORMTemplate] from this [DataSource] with default configuration.
 *
 * ```kotlin
 * val orm = dataSource.orm
 * ```
 *
 * Requires `import st.orm.template.orm`.
 */
public val DataSource.orm: ORMTemplate
    get() = ORMTemplate.of(this)

/**
 * Creates an [ORMTemplate] from this [Connection] with default configuration.
 *
 * Requires `import st.orm.template.orm`.
 */
public val Connection.orm: ORMTemplate
    get() = ORMTemplate.of(this)

/**
 * Creates an [ORMTemplate] from this [DataSource] with a custom [TemplateDecorator].
 *
 * The decorator wraps the template pipeline, allowing cross-cutting concerns such as logging or SQL rewriting.
 *
 * Requires `import st.orm.template.orm`.
 */
public fun DataSource.orm(decorator: (TemplateDecorator) -> TemplateDecorator): ORMTemplate = ORMTemplate.of(this, decorator)

/**
 * Creates an [ORMTemplate] from this [Connection] with a custom [TemplateDecorator].
 *
 * Requires `import st.orm.template.orm`.
 */
public fun Connection.orm(decorator: (TemplateDecorator) -> TemplateDecorator): ORMTemplate = ORMTemplate.of(this, decorator)

/**
 * Creates an [ORMTemplate] from this [DataSource] with a custom [StormConfig].
 *
 * Requires `import st.orm.template.orm`.
 */
public fun DataSource.orm(config: StormConfig): ORMTemplate = ORMTemplate.of(this, config)

/**
 * Creates an [ORMTemplate] from this [Connection] with a custom [StormConfig].
 *
 * Requires `import st.orm.template.orm`.
 */
public fun Connection.orm(config: StormConfig): ORMTemplate = ORMTemplate.of(this, config)

/**
 * Creates an [ORMTemplate] from this [DataSource] with a custom [StormConfig] and [TemplateDecorator].
 *
 * Requires `import st.orm.template.orm`.
 */
public fun DataSource.orm(config: StormConfig, decorator: (TemplateDecorator) -> TemplateDecorator): ORMTemplate = ORMTemplate.of(this, config, decorator)

/**
 * Creates an [ORMTemplate] from this [Connection] with a custom [StormConfig] and [TemplateDecorator].
 *
 * Requires `import st.orm.template.orm`.
 */
public fun Connection.orm(config: StormConfig, decorator: (TemplateDecorator) -> TemplateDecorator): ORMTemplate = ORMTemplate.of(this, config, decorator)
