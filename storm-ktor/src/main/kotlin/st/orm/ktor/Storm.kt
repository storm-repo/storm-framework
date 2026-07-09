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

import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.log
import st.orm.core.template.impl.SchemaValidator
import st.orm.template.impl.CoroutineAwareConnectionProviderImpl
import st.orm.template.impl.TransactionTemplateProviderImpl

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
 *     }
 * }
 * ```
 *
 * @since 1.11
 */
val Storm = createApplicationPlugin(name = "Storm", createConfiguration = ::StormConfiguration) {

    val dataSource = pluginConfig.dataSource ?: createDataSourceFromConfig(application)
    val stormConfig = pluginConfig.config ?: readStormConfig(application)

    // Run the migration hook (e.g., Flyway) before the ORM template is created and the schema is
    // validated, so validation always sees the migrated schema.
    pluginConfig.migration?.invoke(dataSource)

    // Compose the template with explicit, plugin-scoped integration strategies: one provider instance per
    // install, so all repositories of this application share transactions, and the ambient transaction { }
    // API binds to this template's provider when it executes inside a transaction block.
    val builder = st.orm.template.ORMTemplate.builder(dataSource)
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
    val repositoryRegistry = RepositoryRegistry(ormTemplate, application)
    if (pluginConfig.autoRegisterRepositories) {
        repositoryRegistry.register(*pluginConfig.repositoryPackages.toTypedArray())
    }
    application.attributes.put(RepositoryRegistryKey, repositoryRegistry)

    // Run schema validation. The mode comes from the plugin configuration, falling back to the
    // application configuration (storm.validation.schemaMode), matching the Spring Boot starter.
    val configuredSchemaMode = pluginConfig.schemaValidation
        ?: application.environment.config.propertyOrNull("storm.validation.schemaMode")?.getString()
        ?: application.environment.config.propertyOrNull("storm.validation.schema_mode")?.getString()
        ?: "fail"
    val schemaMode = configuredSchemaMode.trim().lowercase()
    if (schemaMode != "none" && schemaMode.isNotBlank()) {
        val validator = SchemaValidator.of(dataSource)
        when (schemaMode) {
            "fail" -> {
                validator.validateOrThrow()
                application.log.info("Storm schema validation passed (mode=fail).")
            }
            "warn" -> validator.validateOrWarn()
            else -> application.log.warn("Unknown schema validation mode: '$configuredSchemaMode'. Expected 'none', 'warn', or 'fail'.")
        }
    }

    // Register shutdown hook to close the DataSource if it is a managed HikariDataSource.
    application.monitor.subscribe(ApplicationStopped) {
        closeDataSourceIfManaged(dataSource)
    }
}
