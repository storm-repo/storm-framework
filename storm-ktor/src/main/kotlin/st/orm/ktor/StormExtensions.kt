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
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.RoutingContext
import st.orm.Entity
import st.orm.Projection
import st.orm.repository.EntityRepository
import st.orm.repository.ProjectionRepository
import st.orm.repository.Repository
import st.orm.repository.entity
import st.orm.repository.projection
import st.orm.template.ORMTemplate
import javax.sql.DataSource

/**
 * Returns the Storm [ORMTemplate] configured for this application.
 *
 * Requires the [Storm] plugin to be installed.
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.11
 */
val Application.orm: ORMTemplate
    get() = attributes.getOrNull(OrmTemplateKey)
        ?: throw IllegalStateException(
            "Storm plugin is not installed. Call install(Storm) in your application module.",
        )

/**
 * Returns the [DataSource] configured for this application.
 *
 * Requires the [Storm] plugin to be installed.
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.11
 */
val Application.stormDataSource: DataSource
    get() = attributes.getOrNull(DataSourceKey)
        ?: throw IllegalStateException(
            "Storm plugin is not installed. Call install(Storm) in your application module.",
        )

/**
 * Returns the Storm [ORMTemplate] configured for this application.
 *
 * Convenience extension for use in route handlers.
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.11
 */
val ApplicationCall.orm: ORMTemplate
    get() = application.orm

/**
 * Returns the Storm [ORMTemplate] configured for this application.
 *
 * Convenience extension for use in route handlers.
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.11
 */
val RoutingContext.orm: ORMTemplate
    get() = call.application.orm

/**
 * Retrieves a Storm repository.
 *
 * Repositories from the compile-time type index are registered automatically when the [Storm] plugin is
 * installed (see [StormPluginConfig.autoRegisterRepositories]); other types are created lazily on first
 * access. Either way, the instance is cached for the lifetime of the application.
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.11
 */
inline fun <reified T : Repository> Application.repository(): T {
    val registry = attributes.getOrNull(RepositoryRegistryKey)
        ?: throw IllegalStateException(
            "Storm plugin is not installed. Call install(Storm) in your application module.",
        )
    return registry.getOrCreate(T::class)
}

/**
 * Retrieves a Storm repository.
 *
 * Convenience extension for use in route handlers. See [Application.repository] for registration and caching
 * semantics.
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.11
 */
inline fun <reified T : Repository> ApplicationCall.repository(): T = application.repository()

/**
 * Retrieves a Storm repository.
 *
 * Convenience extension for use in route handlers. See [Application.repository] for registration and caching
 * semantics.
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.11
 */
inline fun <reified T : Repository> RoutingContext.repository(): T = call.application.repository()

/**
 * Returns the repository for entity type [T] with primary key type [ID].
 *
 * The primary key type can be inferred with the underscore operator: `entity<User, _>()`.
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.12
 */
@JvmName("entityTyped")
inline fun <reified T : Entity<ID>, ID : Any> Application.entity(): EntityRepository<T, ID> = orm.entity<T, ID>()

/**
 * Returns the repository for entity type [T] without binding the primary key type.
 *
 * Use `entity<T, _>()` when ID-based operations such as `findById` are needed.
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.12
 */
inline fun <reified T : Entity<*>> Application.entity(): EntityRepository<T, *> = orm.entity<T>()

/**
 * Returns the repository for entity type [T] with primary key type [ID].
 *
 * Convenience extension for use in route handlers. See [Application.entity].
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.12
 */
@JvmName("entityTyped")
inline fun <reified T : Entity<ID>, ID : Any> ApplicationCall.entity(): EntityRepository<T, ID> = application.entity<T, ID>()

/**
 * Returns the repository for entity type [T] without binding the primary key type.
 *
 * Convenience extension for use in route handlers. See [Application.entity].
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.12
 */
inline fun <reified T : Entity<*>> ApplicationCall.entity(): EntityRepository<T, *> = application.entity<T>()

/**
 * Returns the repository for entity type [T] with primary key type [ID].
 *
 * Convenience extension for use in route handlers. See [Application.entity].
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.12
 */
@JvmName("entityTyped")
inline fun <reified T : Entity<ID>, ID : Any> RoutingContext.entity(): EntityRepository<T, ID> = call.application.entity<T, ID>()

/**
 * Returns the repository for entity type [T] without binding the primary key type.
 *
 * Convenience extension for use in route handlers. See [Application.entity].
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.12
 */
inline fun <reified T : Entity<*>> RoutingContext.entity(): EntityRepository<T, *> = call.application.entity<T>()

/**
 * Returns the repository for projection type [T] with primary key type [ID].
 *
 * The primary key type can be inferred with the underscore operator: `projection<OwnerView, _>()`.
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.12
 */
@JvmName("projectionTyped")
inline fun <reified T : Projection<ID>, ID : Any> Application.projection(): ProjectionRepository<T, ID> = orm.projection<T, ID>()

/**
 * Returns the repository for projection type [T] without binding the primary key type.
 *
 * Use `projection<T, _>()` when ID-based operations such as `findById` are needed.
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.12
 */
inline fun <reified T : Projection<*>> Application.projection(): ProjectionRepository<T, *> = orm.projection<T>()

/**
 * Returns the repository for projection type [T] with primary key type [ID].
 *
 * Convenience extension for use in route handlers. See [Application.projection].
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.12
 */
@JvmName("projectionTyped")
inline fun <reified T : Projection<ID>, ID : Any> ApplicationCall.projection(): ProjectionRepository<T, ID> = application.projection<T, ID>()

/**
 * Returns the repository for projection type [T] without binding the primary key type.
 *
 * Convenience extension for use in route handlers. See [Application.projection].
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.12
 */
inline fun <reified T : Projection<*>> ApplicationCall.projection(): ProjectionRepository<T, *> = application.projection<T>()

/**
 * Returns the repository for projection type [T] with primary key type [ID].
 *
 * Convenience extension for use in route handlers. See [Application.projection].
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.12
 */
@JvmName("projectionTyped")
inline fun <reified T : Projection<ID>, ID : Any> RoutingContext.projection(): ProjectionRepository<T, ID> = call.application.projection<T, ID>()

/**
 * Returns the repository for projection type [T] without binding the primary key type.
 *
 * Convenience extension for use in route handlers. See [Application.projection].
 *
 * @throws IllegalStateException if the Storm plugin is not installed.
 * @since 1.12
 */
inline fun <reified T : Projection<*>> RoutingContext.projection(): ProjectionRepository<T, *> = call.application.projection<T>()
