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
class StormConfiguration {

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
     * Schema validation mode: `"none"` (default), `"warn"`, or `"fail"`.
     */
    var schemaValidation: String = "none"

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
}
