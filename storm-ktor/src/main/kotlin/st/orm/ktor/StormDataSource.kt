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

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import javax.sql.DataSource

/**
 * Creates a [HikariDataSource] from the application's HOCON configuration.
 *
 * Expected configuration in `application.conf`:
 * ```
 * storm {
 *     datasource {
 *         jdbcUrl = "jdbc:h2:mem:test"
 *         driverClassName = "org.h2.Driver"
 *         username = "sa"
 *         password = ""
 *         maximumPoolSize = 10
 *     }
 * }
 * ```
 */
internal fun createDataSourceFromConfig(application: Application, path: String = "storm.datasource"): DataSource {
    val config = application.environment.config
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = config.property("$path.jdbcUrl").getString()
        config.propertyOrNull("$path.driverClassName")?.getString()?.let {
            driverClassName = it
        }
        config.propertyOrNull("$path.username")?.getString()?.let {
            username = it
        }
        config.propertyOrNull("$path.password")?.getString()?.let {
            password = it
        }
        config.propertyOrNull("$path.maximumPoolSize")?.getString()?.toIntOrNull()?.let {
            maximumPoolSize = it
        }
        config.propertyOrNull("$path.connectionTimeout")?.getString()?.toLongOrNull()?.let {
            connectionTimeout = it
        }
        config.propertyOrNull("$path.idleTimeout")?.getString()?.toLongOrNull()?.let {
            idleTimeout = it
        }
        config.propertyOrNull("$path.maxLifetime")?.getString()?.toLongOrNull()?.let {
            maxLifetime = it
        }
        config.propertyOrNull("$path.minimumIdle")?.getString()?.toIntOrNull()?.let {
            minimumIdle = it
        }
    }
    return HikariDataSource(hikariConfig)
}

/**
 * Closes a [DataSource] the plugin created from configuration.
 *
 * Ownership is decided at creation time: only pools the plugin built itself are closed at shutdown, never a
 * user-supplied instance. The close goes through [AutoCloseable], which the created pool type implements; this
 * keeps the shutdown path free of pool-implementation classes, which are provided scope and may be absent at
 * runtime when every data source is user-supplied.
 */
internal fun closeOwnedDataSource(dataSource: DataSource) {
    (dataSource as? AutoCloseable)?.close()
}
