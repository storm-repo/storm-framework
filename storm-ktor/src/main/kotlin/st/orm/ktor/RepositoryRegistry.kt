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
import io.ktor.server.application.log
import st.orm.core.spi.TypeDiscovery
import st.orm.repository.Repository
import st.orm.template.ORMTemplate
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Registry for pre-created Storm repository instances.
 *
 * Repositories are created once during application startup and cached for the lifetime of the application.
 *
 * @since 1.11
 */
class RepositoryRegistry internal constructor(
    private val ormTemplate: ORMTemplate,
    private val application: Application,
) {

    private val repositories = ConcurrentHashMap<KClass<*>, Any>()

    /**
     * Registers a custom repository type. The repository proxy is created immediately and cached.
     *
     * @param type the repository interface to register.
     * @return the created repository instance.
     */
    fun <T : Repository> register(type: KClass<T>): T {
        val repository = ormTemplate.repository(type)
        repositories[type] = repository
        return repository
    }

    /**
     * Registers all repository interfaces from the compile-time type index that belong to the specified packages.
     *
     * The Storm metamodel processor (annotation processor or KSP) generates an index of all [Repository] subtypes
     * at compile time. This method reads that index and registers repositories whose package matches one of the
     * given [packages] (including sub-packages).
     *
     * This requires the `storm-metamodel-processor` (Java) or `storm-metamodel-ksp` (Kotlin) to be configured in
     * the build, which is already the case for metamodel generation.
     *
     * @param packages one or more package names to register repositories from.
     */
    @Suppress("UNCHECKED_CAST")
    fun register(vararg packages: String) {
        val discovered = TypeDiscovery.getRepositoryTypes()
        for (type in discovered) {
            if (packages.isNotEmpty()) {
                val typeName = type.name
                if (packages.none { typeName.startsWith("$it.") }) continue
            }
            val kotlinType = type.kotlin as KClass<out Repository>
            if (!repositories.containsKey(kotlinType)) {
                val repository = ormTemplate.repository(kotlinType)
                repositories[kotlinType] = repository
                application.log.debug("Registered repository: ${type.simpleName}")
            }
        }
    }

    /**
     * Iterates over all registered repository types and their instances.
     *
     * This is useful for integrating with DI frameworks like Koin that need to register each repository
     * as a managed component.
     */
    fun forEach(action: (type: KClass<out Repository>, instance: Repository) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        for ((type, instance) in repositories) {
            action(type as KClass<out Repository>, instance as Repository)
        }
    }

    /**
     * Retrieves a previously registered repository.
     *
     * @param type the repository interface to retrieve.
     * @return the cached repository instance.
     * @throws IllegalStateException if the repository type has not been registered.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Repository> get(type: KClass<T>): T = repositories[type] as? T
        ?: throw IllegalStateException(
            "Repository ${type.simpleName} is not registered. " +
                "Call register(${type.simpleName}::class) or register(\"<package>\") in stormRepositories { }.",
        )

    /**
     * Retrieves a repository, creating and caching it on first access if it has not been registered.
     *
     * Repositories registered at startup (automatically from the compile-time type index, or explicitly via
     * [register]) are returned from the cache. Unknown types are created lazily and cached for subsequent
     * calls. Thread-safe.
     *
     * @param type the repository interface to retrieve or create.
     * @return the cached or newly created repository instance.
     * @since 1.12
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Repository> getOrCreate(type: KClass<T>): T = repositories.computeIfAbsent(type) { ormTemplate.repository(type) } as T
}

/**
 * Configures Storm repositories for this application.
 *
 * Since 1.12, calling this is optional: the [Storm] plugin registers all repositories from the compile-time
 * type index automatically during installation (see [StormPluginConfig.autoRegisterRepositories]), and
 * unregistered types are created lazily on first [repository] access. Use this block when you want explicit
 * control over registration, or to iterate the registered repositories for DI integration via
 * [RepositoryRegistry.forEach].
 *
 * The block operates on the registry created by the [Storm] plugin when one exists, so explicit registrations
 * and auto-registrations share the same cache.
 *
 * ```kotlin
 * fun Application.module() {
 *     install(Storm)
 *
 *     stormRepositories {
 *         // Option A: register individually
 *         register(UserRepository::class)
 *
 *         // Option B: register all repositories in a package
 *         register("com.myapp.repository")
 *
 *         // Option C: register all indexed repositories
 *         register()
 *     }
 * }
 * ```
 *
 * @since 1.11
 */
fun Application.stormRepositories(block: RepositoryRegistry.() -> Unit): RepositoryRegistry {
    val registry = attributes.getOrNull(RepositoryRegistryKey)
        ?: RepositoryRegistry(orm, this).also { attributes.put(RepositoryRegistryKey, it) }
    registry.block()
    return registry
}
