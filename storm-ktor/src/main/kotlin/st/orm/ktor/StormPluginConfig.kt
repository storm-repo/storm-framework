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
package st.orm.ktor

import st.orm.EntityCallback
import st.orm.StormConfig
import javax.sql.DataSource

/**
 * Configuration DSL for the Storm Ktor plugin.
 *
 * Example usage:
 * ```kotlin
 * install(Storm) {
 *     dataSource = HikariDataSource(hikariConfig)
 *     schemaValidation = "warn"
 *     entityCallback(AuditCallback())
 * }
 * ```
 */
class StormPluginConfig {

    /**
     * The [DataSource] to use. If not provided, one will be created from the HOCON configuration under
     * `storm.datasource`.
     */
    var dataSource: DataSource? = null

    /**
     * Optional [StormConfig] override. If not provided, configuration is read from the HOCON configuration under
     * `storm`.
     */
    var config: StormConfig? = null

    /**
     * Optional [st.orm.core.spi.ConnectionProvider] override. When not set, the plugin uses the coroutine-aware
     * provider that binds connections to Storm's programmatic transactions.
     *
     * @since 1.13
     */
    var connectionProvider: st.orm.core.spi.ConnectionProvider? = null

    /**
     * Optional [st.orm.core.spi.TransactionTemplateProvider] override. When not set, the plugin uses the JDBC
     * transaction provider that backs Storm's `transaction { }` API. Templates that should share transactions must
     * use the same provider instance.
     *
     * @since 1.13
     */
    var transactionTemplateProvider: st.orm.core.spi.TransactionTemplateProvider? = null

    /**
     * Optional [st.orm.core.spi.ExceptionMapper] that maps failures raised during query execution to the runtime
     * exception thrown to the caller.
     *
     * @since 1.13
     */
    var exceptionMapper: st.orm.core.spi.ExceptionMapper? = null

    /**
     * Optional [st.orm.core.spi.QueryObserver] that is notified of query executions, for metrics and tracing
     * bindings.
     *
     * @since 1.13
     */
    var queryObserver: st.orm.core.spi.QueryObserver? = null

    /**
     * Optional [st.orm.core.spi.SqlCommenter] that appends per-execution comment content to statements, such
     * as the current trace context ([st.orm.micrometer.TraceContextSqlCommenter]). Note that per-execution
     * content defeats prepared statement caching; enable selectively.
     *
     * @since 1.13
     */
    var sqlCommenter: st.orm.core.spi.SqlCommenter? = null

    /**
     * Whether to expose the [st.orm.template.ORMTemplate] and the registered repositories through Ktor's
     * dependency injection (`ktor-server-di`). Each repository is registered under its own interface type, so
     * modules and routes can inject them directly:
     *
     * ```kotlin
     * val visits: VisitRepository by dependencies
     * ```
     *
     * Enabled by default; set to `false` to leave the dependency container untouched.
     *
     * @since 1.13
     */
    var registerDependencies: Boolean = true

    /**
     * Schema validation mode: `"none"`, `"warn"`, or `"fail"`.
     *
     * When not set, the mode is read from the application configuration under
     * `storm.validation.schemaMode` (or `storm.validation.schema_mode`), matching the Spring Boot
     * starter's property. Defaults to `"fail"`: every entity and projection is validated against the
     * live database schema during installation, and mismatches abort startup. Run migrations in the
     * [migration] hook so the schema is up to date before validation, or set this to `"none"` to opt out.
     */
    var schemaValidation: String? = null

    internal var migration: ((DataSource) -> Unit)? = null

    /**
     * Registers a migration hook that runs after the [DataSource] is available but before the
     * ORM template is created and the schema is validated.
     *
     * This is the place to run schema migrations (e.g., Flyway or Liquibase) when the plugin creates
     * the DataSource from configuration, guaranteeing that schema validation sees the migrated schema:
     *
     * ```kotlin
     * install(Storm) {
     *     migration { dataSource ->
     *         Flyway.configure().dataSource(dataSource).load().migrate()
     *     }
     * }
     * ```
     *
     * @since 1.12
     */
    fun migration(block: (DataSource) -> Unit) {
        migration = block
    }

    /**
     * Entity callbacks for lifecycle hooks on insert, update, and delete operations.
     */
    val entityCallbacks: MutableList<EntityCallback<*>> = mutableListOf()

    /**
     * Adds an entity callback.
     */
    fun entityCallback(callback: EntityCallback<*>) {
        entityCallbacks.add(callback)
    }

    /**
     * Whether to automatically register all repository interfaces from the compile-time type index when the
     * plugin is installed. Enabled by default.
     *
     * Auto-registration creates the repository proxies eagerly, so an invalid repository definition fails at
     * startup rather than at first request. Use [repositories] to narrow registration to specific packages, or
     * set this to `false` to skip auto-registration entirely; repositories are then created lazily on first
     * [repository] access, or explicitly via [stormRepositories].
     *
     * @since 1.12
     */
    var autoRegisterRepositories: Boolean = true

    internal val repositoryPackages: MutableList<String> = mutableListOf()

    /**
     * Narrows repository auto-registration to the given packages (including sub-packages).
     *
     * Only repository interfaces from the compile-time type index whose package matches one of the given
     * [packages] are registered at startup. Repositories outside these packages remain available through lazy
     * creation on first [repository] access.
     *
     * @param packages one or more package names to register repositories from.
     * @since 1.12
     */
    fun repositories(vararg packages: String) {
        repositoryPackages.addAll(packages)
    }

    internal val databases = LinkedHashMap<String, StormDatabaseConfig>()

    /**
     * Configures an additional, named database.
     *
     * The block mirrors the primary database's options: provide a [StormDatabaseConfig.dataSource] or let the
     * plugin create one from the HOCON configuration under `storm.databases.<name>.datasource`. The database's
     * template and repositories are exposed under the given name via `orm("name")`, `repository<T>("name")`, and
     * named dependency injection.
     *
     * Declare the packages that belong to this database with [StormDatabaseConfig.repositories]; they partition
     * repository registration and schema validation between the databases:
     *
     * ```kotlin
     * install(Storm) {
     *     // primary database: unchanged, zero-config from storm.datasource
     *
     *     database("analytics") {
     *         repositories("com.myapp.analytics")
     *     }
     * }
     * ```
     *
     * @param name the database name; must be unique and not blank.
     * @param block the configuration for the named database.
     * @since 1.13
     */
    fun database(name: String, block: StormDatabaseConfig.() -> Unit) {
        require(name.isNotBlank()) { "Database name must not be blank." }
        require(name !in databases) { "Database '$name' is already configured." }
        databases[name] = StormDatabaseConfig(name).apply(block)
    }
}
