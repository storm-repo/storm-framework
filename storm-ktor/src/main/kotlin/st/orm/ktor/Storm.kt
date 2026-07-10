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

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.log
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies
import io.ktor.util.reflect.TypeInfo
import st.orm.template.ORMTemplate
import st.orm.template.impl.CoroutineAwareConnectionProviderImpl
import st.orm.template.impl.TransactionTemplateProviderImpl
import javax.sql.DataSource
import kotlin.reflect.KClass
import kotlin.reflect.full.starProjectedType

/**
 * Ktor plugin that configures Storm ORM for the application.
 *
 * The plugin creates an [st.orm.template.ORMTemplate] from either a user-provided [javax.sql.DataSource] or one
 * auto-created from the HOCON configuration under `storm.datasource`. The template is stored in the application's
 * attributes and can be accessed via extension properties on [io.ktor.server.application.Application],
 * [io.ktor.server.application.ApplicationCall], and [io.ktor.server.routing.RoutingContext].
 *
 * Repositories from the compile-time type index are registered automatically during installation and are
 * available via `repository<T>()` without further setup. Narrow the registration with `repositories(...)` or
 * disable it with `autoRegisterRepositories = false`.
 *
 * Usage:
 * ```kotlin
 * fun Application.module() {
 *     install(Storm) {
 *         // Option A: auto-configure from application.conf (zero config)
 *
 *         // Option B: provide your own DataSource
 *         dataSource = HikariDataSource(hikariConfig)
 *
 *         // Option C: override Storm config
 *         config = StormConfig.of(mapOf("storm.update.default_mode" to "FIELD"))
 *
 *         // Optional: schema validation
 *         schemaValidation = "warn"
 *
 *         // Optional: entity callbacks
 *         entityCallback(AuditCallback())
 *
 *         // Optional: narrow repository auto-registration
 *         repositories("com.myapp.repository")
 *
 *         // Optional: additional, named databases
 *         database("analytics") {
 *             repositories("com.myapp.analytics")
 *         }
 *     }
 * }
 * ```
 *
 * Additional databases declared with `database("name") { }` get their own template, repositories, schema
 * validation, and lifecycle, exposed under their name: `orm("name")`, `repository<T>("name")`, and named
 * dependency injection. The packages declared inside a database block partition repositories and schema
 * validation between the databases.
 *
 * @since 1.11
 */
val Storm = createApplicationPlugin(name = "Storm", createConfiguration = ::StormPluginConfig) {

    // Packages claimed by the named databases (package name to database name). These partition repository
    // registration and schema validation: types under a claimed package belong to that database only.
    val claimedPackages = mutableMapOf<String, String>()
    for (databaseConfig in pluginConfig.databases.values) {
        for (packageName in databaseConfig.repositoryPackages) {
            val existingOwner = claimedPackages.put(packageName, databaseConfig.name)
            if (existingOwner != null && existingOwner != databaseConfig.name) {
                throw IllegalStateException(
                    "Package '$packageName' is declared by both database '$existingOwner' and " +
                        "database '${databaseConfig.name}'. Each package can belong to only one database.",
                )
            }
        }
    }

    // ---- Primary database ----

    val dataSource = pluginConfig.dataSource ?: createDataSourceFromConfig(application)
    val stormConfig = pluginConfig.config ?: readStormConfig(application)

    // Run the migration hook (e.g., Flyway) before the ORM template is created and the schema is
    // validated, so validation always sees the migrated schema.
    pluginConfig.migration?.invoke(dataSource)

    // Compose the template with explicit, plugin-scoped integration strategies: one provider instance per
    // database, so all repositories of a database share transactions, and the ambient transaction { }
    // API binds to the database's provider when its template executes inside a transaction block.
    val builder = ORMTemplate.builder(dataSource)
        .config(stormConfig)
        .connectionProvider(pluginConfig.connectionProvider ?: CoroutineAwareConnectionProviderImpl())
        .transactionTemplateProvider(pluginConfig.transactionTemplateProvider ?: TransactionTemplateProviderImpl())
    pluginConfig.exceptionMapper?.let { builder.exceptionMapper(it) }
    pluginConfig.queryObserver?.let { builder.queryObserver(it) }
    var ormTemplate = builder.build()
    if (pluginConfig.entityCallbacks.isNotEmpty()) {
        ormTemplate = ormTemplate.withEntityCallbacks(pluginConfig.entityCallbacks)
    }

    application.attributes.put(OrmTemplateKey, ormTemplate)
    application.attributes.put(DataSourceKey, dataSource)

    // Create the repository registry and auto-register repositories from the compile-time type index.
    // Eager registration creates the proxies now, so a broken repository definition fails at startup
    // rather than at first request. Narrow with repositories("com.myapp") or disable with
    // autoRegisterRepositories = false; unregistered types are still created lazily on first access.
    // Types under packages claimed by a named database are excluded here and registered there instead.
    val repositoryRegistry = RepositoryRegistry(ormTemplate, application)
    repositoryRegistry.claimedPackages = claimedPackages
    if (pluginConfig.autoRegisterRepositories) {
        repositoryRegistry.register(*pluginConfig.repositoryPackages.toTypedArray())
    }
    application.attributes.put(RepositoryRegistryKey, repositoryRegistry)

    // ---- Named databases ----

    val namedTemplates = LinkedHashMap<String, ORMTemplate>()
    val namedDataSources = LinkedHashMap<String, DataSource>()
    val namedRegistries = LinkedHashMap<String, RepositoryRegistry>()
    for (databaseConfig in pluginConfig.databases.values) {
        val name = databaseConfig.name
        val databaseDataSource = databaseConfig.dataSource
            ?: createDataSourceFromConfig(application, "storm.databases.$name.datasource")
        val databaseStormConfig = databaseConfig.config
            ?: readStormConfig(application, "storm.databases.$name")
        databaseConfig.migration?.invoke(databaseDataSource)
        val databaseBuilder = ORMTemplate.builder(databaseDataSource)
            .config(databaseStormConfig)
            .connectionProvider(databaseConfig.connectionProvider ?: CoroutineAwareConnectionProviderImpl())
            .transactionTemplateProvider(
                databaseConfig.transactionTemplateProvider ?: TransactionTemplateProviderImpl(),
            )
        databaseConfig.exceptionMapper?.let { databaseBuilder.exceptionMapper(it) }
        databaseConfig.queryObserver?.let { databaseBuilder.queryObserver(it) }
        var databaseTemplate = databaseBuilder.build()
        if (databaseConfig.entityCallbacks.isNotEmpty()) {
            databaseTemplate = databaseTemplate.withEntityCallbacks(databaseConfig.entityCallbacks)
        }
        val databaseRegistry = RepositoryRegistry(databaseTemplate, application)
        databaseRegistry.claimedPackages = claimedPackages.filterValues { it != name }
        if (databaseConfig.repositoryPackages.isNotEmpty()) {
            databaseRegistry.register(*databaseConfig.repositoryPackages.toTypedArray())
        }
        namedTemplates[name] = databaseTemplate
        namedDataSources[name] = databaseDataSource
        namedRegistries[name] = databaseRegistry
    }
    application.attributes.put(NamedOrmTemplatesKey, namedTemplates)
    application.attributes.put(NamedDataSourcesKey, namedDataSources)
    application.attributes.put(NamedRepositoryRegistriesKey, namedRegistries)

    // ---- Dependency injection ----

    // Expose the templates and the registered repositories through Ktor's dependency injection. The primary
    // template is registered unnamed and each named database's template under its name. Repositories are always
    // registered unnamed under their own interface type: package partitioning guarantees each type belongs to
    // exactly one database, so `val visits: VisitRepository by dependencies` works regardless of the database.
    // Repositories created lazily after installation are not added to the container.
    if (pluginConfig.registerDependencies) {
        application.dependencies {
            provide<ORMTemplate> { ormTemplate }
        }
        application.registerRepositories(repositoryRegistry)
        for ((name, template) in namedTemplates) {
            application.dependencies {
                provide<ORMTemplate>(name) { template }
            }
            application.registerRepositories(namedRegistries.getValue(name))
        }
    }

    // ---- Schema validation ----

    // The packages declared by the named databases partition validation: each database validates only the entity
    // and projection types under its packages, and the primary validates everything else.
    application.runSchemaValidation(
        template = ormTemplate,
        configuredMode = pluginConfig.schemaValidation
            ?: application.environment.config.propertyOrNull("storm.validation.schemaMode")?.getString()
            ?: application.environment.config.propertyOrNull("storm.validation.schema_mode")?.getString()
            ?: "fail",
        description = "primary database",
    ) { type -> claimedPackages.keys.none { type.java.name.startsWith("$it.") } }
    for (databaseConfig in pluginConfig.databases.values) {
        val name = databaseConfig.name
        application.runSchemaValidation(
            template = namedTemplates.getValue(name),
            configuredMode = databaseConfig.schemaValidation
                ?: application.environment.config.propertyOrNull("storm.databases.$name.validation.schemaMode")?.getString()
                ?: application.environment.config.propertyOrNull("storm.databases.$name.validation.schema_mode")?.getString()
                ?: "fail",
            description = "database '$name'",
        ) { type -> databaseConfig.repositoryPackages.any { type.java.name.startsWith("$it.") } }
    }

    // Register shutdown hook to close the DataSources that are managed HikariDataSources.
    application.monitor.subscribe(ApplicationStopped) {
        closeDataSourceIfManaged(dataSource)
        namedDataSources.values.forEach { closeDataSourceIfManaged(it) }
    }
}

/**
 * Registers every repository of the given registry in the dependency container, each under its own interface type.
 */
private fun Application.registerRepositories(registry: RepositoryRegistry) {
    registry.forEach { type, instance ->
        dependencies {
            set(DependencyKey(TypeInfo(type, type.starProjectedType))) { instance }
        }
    }
}

/**
 * Runs schema validation for one database's template, limited to the types accepted by [filter].
 */
private fun Application.runSchemaValidation(
    template: ORMTemplate,
    configuredMode: String,
    description: String,
    filter: (KClass<out st.orm.Data>) -> Boolean,
) {
    val schemaMode = configuredMode.trim().lowercase()
    if (schemaMode == "none" || schemaMode.isBlank()) {
        return
    }
    when (schemaMode) {
        "fail" -> {
            template.validateSchemaOrThrow(filter)
            log.info("Storm schema validation passed for $description (mode=fail).")
        }
        "warn" -> template.validateSchema(filter).forEach { log.warn(it) }
        else -> log.warn("Unknown schema validation mode: '$configuredMode'. Expected 'none', 'warn', or 'fail'.")
    }
}
