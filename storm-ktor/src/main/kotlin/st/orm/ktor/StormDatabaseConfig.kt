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
 * Configuration for an additional, named database of the [Storm] plugin.
 *
 * Declared inside `install(Storm) { database("name") { ... } }`; the options mirror the primary database's
 * configuration on [StormPluginConfig]. The database's template and repositories are exposed under the given name:
 * `orm("name")`, `repository<T>("name")`, and named dependency injection (`@Named("name")`).
 *
 * The packages declared with [repositories] partition the application: repository interfaces and entity types under
 * these packages belong to this database. They are registered against this database's template, excluded from the
 * primary database's registration and schema validation, and validated against this database's schema instead.
 *
 * @since 1.13
 */
class StormDatabaseConfig internal constructor(internal val name: String) {

    /**
     * The [DataSource] to use for this database. If not provided, one is created from the HOCON configuration
     * under `storm.databases.<name>.datasource`.
     */
    var dataSource: DataSource? = null

    /**
     * Optional [StormConfig] override for this database. If not provided, configuration is read from the HOCON
     * configuration under `storm.databases.<name>`, falling back to defaults.
     */
    var config: StormConfig? = null

    /**
     * Optional [st.orm.core.spi.ConnectionProvider] override. When not set, this database uses its own
     * coroutine-aware provider instance.
     */
    var connectionProvider: st.orm.core.spi.ConnectionProvider? = null

    /**
     * Optional [st.orm.core.spi.TransactionTemplateProvider] override. When not set, this database uses its own
     * JDBC transaction provider instance. Each database has its own transaction provider, so a `transaction { }`
     * block binds to one database; blocks cannot atomically span databases.
     */
    var transactionTemplateProvider: st.orm.core.spi.TransactionTemplateProvider? = null

    /**
     * Optional [st.orm.core.spi.ExceptionMapper] for this database's template.
     */
    var exceptionMapper: st.orm.core.spi.ExceptionMapper? = null

    /**
     * Optional [st.orm.core.spi.QueryObserver] for this database's template.
     */
    var queryObserver: st.orm.core.spi.QueryObserver? = null

    /**
     * Optional [st.orm.core.spi.SqlCommenter] for this database; inherits the plugin-level commenter when
     * unset.
     *
     * @since 1.13
     */
    var sqlCommenter: st.orm.core.spi.SqlCommenter? = null

    /**
     * Schema validation mode for this database: `"none"`, `"warn"`, or `"fail"`.
     *
     * When not set, the mode is read from the HOCON configuration under
     * `storm.databases.<name>.validation.schemaMode` (or `schema_mode`), defaulting to `"fail"`. Validation covers
     * the entity and projection types under the packages declared with [repositories].
     */
    var schemaValidation: String? = null

    internal var migration: ((DataSource) -> Unit)? = null

    /**
     * Registers a migration hook for this database, running after its [DataSource] is available but before the
     * template is created and the schema is validated.
     */
    fun migration(block: (DataSource) -> Unit) {
        migration = block
    }

    internal val entityCallbacks = mutableListOf<EntityCallback<*>>()

    /**
     * Registers an entity callback on this database's template.
     */
    fun entityCallback(callback: EntityCallback<*>) {
        entityCallbacks += callback
    }

    internal val repositoryPackages = mutableListOf<String>()

    /**
     * Declares the packages that belong to this database.
     *
     * Repository interfaces from the compile-time type index under these packages (including sub-packages) are
     * registered against this database's template, and entity and projection types under these packages are
     * validated against this database's schema. The primary database excludes these packages from its own
     * registration and validation.
     *
     * A database without declared packages exposes its template (`orm("name")`) but registers no repositories
     * automatically.
     */
    fun repositories(vararg packages: String) {
        repositoryPackages += packages
    }
}
