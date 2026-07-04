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
package st.orm.ktor.koin

import io.ktor.server.application.Application
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import st.orm.ktor.orm
import st.orm.ktor.stormRepositories
import st.orm.template.ORMTemplate
import kotlin.reflect.KClass

/**
 * Builds a Koin [Module] exposing Storm's [ORMTemplate] and every repository registered by the
 * Storm plugin, each bound under its own interface type.
 *
 * Repositories from the compile-time type index are registered automatically when the Storm plugin
 * is installed, so with default settings this module exposes the application's entire repository
 * layer. Because every repository is available under its exact type, services can be declared with
 * Koin's constructor DSL:
 *
 * ```kotlin
 * install(Storm)
 * install(Koin) {
 *     modules(
 *         stormModule(),
 *         module {
 *             singleOf(::TopMoviesService)   // repositories injected by type
 *         },
 *     )
 * }
 * ```
 *
 * Requires the Storm plugin to be installed first.
 *
 * @return a Koin module with the [ORMTemplate] and all registered Storm repositories.
 * @since 1.12
 */
fun Application.stormModule(): Module {
    val ormTemplate = orm
    val registry = stormRepositories { }
    return module {
        single { ormTemplate }
        registry.forEach { type, instance ->
            // Koin indexes definitions by their compile-time type, which is the Repository marker
            // here; the bind makes each instance resolvable by its own interface type, which is
            // the index that matters. The cast only widens the erased generic; the runtime KClass
            // is the repository's own interface.
            @Suppress("UNCHECKED_CAST")
            single { instance } bind (type as KClass<st.orm.repository.Repository>)
        }
    }
}
