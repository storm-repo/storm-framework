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
package st.orm.template.impl

import st.orm.Entity
import st.orm.Projection
import st.orm.core.spi.ORMReflection
import st.orm.core.spi.Providers
import st.orm.repository.EntityRepository
import st.orm.repository.ProjectionRepository
import st.orm.repository.Repository
import st.orm.repository.impl.EntityRepositoryImpl
import st.orm.repository.impl.ProjectionRepositoryImpl
import st.orm.template.ORMTemplate
import java.lang.reflect.*
import kotlin.reflect.KClass

class ORMTemplateImpl(private val core: st.orm.core.template.ORMTemplate) : QueryTemplateImpl(core), ORMTemplate {
    companion object {
        val REFLECTION: ORMReflection = Providers.getORMReflection()
    }

    /**
     * Returns the repository for the given entity type.
     *
     * @param type the entity type.
     * @param <T> the entity type.
     * @param <ID> the type of the entity's primary key.
     * @return the repository for the given entity type.
     */
    override fun <T : Entity<ID>, ID : Any> entity(type: KClass<T>): EntityRepository<T, ID> {
        return EntityRepositoryImpl(core.entity(type.java))
    }

    /**
     * Returns the repository for the given projection type.
     *
     * @param type the projection type.
     * @param <T> the projection type.
     * @param <ID> the type of the projection's primary key, or Void if the projection specifies no primary key.
     * @return the repository for the given projection type.
     */
    override fun <T : Projection<ID>, ID: Any> projection(type: KClass<T>): ProjectionRepository<T, ID> {
        return ProjectionRepositoryImpl(core.projection(type.java))
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R : Repository> repository(type: KClass<R>): R {
        val entityRepository = createEntityRepository(type)
        val projectionRepository = createProjectionRepository(type)
        val repository = createRepository()
        return Proxy.newProxyInstance(
            type.java.classLoader,
            arrayOf(type.java)
        ) { proxy, method, args ->
            val arguments = args ?: emptyArray()
            try {
                when {
                    method.name == "hashCode" && method.parameterCount == 0 ->
                        System.identityHashCode(proxy)
                    method.name == "equals" && method.parameterCount == 1 ->
                        proxy === arguments[0]
                    method.name == "toString" && method.parameterCount == 0 ->
                        "RepositoryProxy(${type.simpleName})"
                    method.declaringClass.isAssignableFrom(Object::class.java) ->
                        method.invoke(proxy, *arguments)
                    method.declaringClass.isAssignableFrom(Repository::class.java) ->
                        method.invoke(repository, *arguments)
                    method.declaringClass.isAssignableFrom(EntityRepository::class.java) -> {
                        requireNotNull(entityRepository)
                        method.invoke(entityRepository, *arguments)
                    }
                    method.declaringClass.isAssignableFrom(ProjectionRepository::class.java) -> {
                        requireNotNull(projectionRepository)
                        method.invoke(projectionRepository, *arguments)
                    }
                    REFLECTION.isDefaultMethod(method) ->
                        REFLECTION.execute(proxy, method, *arguments)
                    else ->
                        throw UnsupportedOperationException("Unsupported method: ${method.name} for ${type.java.name}.")
                }
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        } as R
    }

    private fun createEntityRepository(type: KClass<*>): EntityRepository<*, *>? {
        if (!EntityRepository::class.java.isAssignableFrom(type.java)) return null
        var entityClass: Class<*>? = null
        for (iface in type.java.genericInterfaces) {
            if (iface is ParameterizedType && (iface.rawType as? Class<*>)?.let {
                    EntityRepository::class.java.isAssignableFrom(it)
                } == true
            ) {
                val arg = iface.actualTypeArguments[0]
                if (arg is Class<*>) {
                    entityClass = arg
                    break
                }
            }
        }
        requireNotNull(entityClass) {
            "Could not determine entity class for repository: ${type.simpleName}."
        }
        // Use Java reflection to invoke the generic 'entity' method at runtime.
        val method = this.javaClass.getMethod("entity", KClass::class.java)
        return method.invoke(this, entityClass.kotlin) as EntityRepository<*, *>
    }

    private fun createProjectionRepository(type: KClass<*>): ProjectionRepository<*, *>? {
        if (!ProjectionRepository::class.java.isAssignableFrom(type.java)) return null
        var projectionClass: Class<*>? = null
        for (iface in type.java.genericInterfaces) {
            if (iface is ParameterizedType && (iface.rawType as? Class<*>)?.let {
                    ProjectionRepository::class.java.isAssignableFrom(it)
                } == true
            ) {
                val arg = iface.actualTypeArguments[0]
                if (arg is Class<*>) {
                    projectionClass = arg
                    break
                }
            }
        }
        requireNotNull(projectionClass) {
            "Could not determine projection class for repository: ${type.simpleName}."
        }
        // Use Java reflection to invoke the generic 'projection' method at runtime.
        val method = this.javaClass.getMethod("projection", KClass::class.java)
        return method.invoke(this, projectionClass.kotlin) as ProjectionRepository<*, *>
    }

    private fun createRepository(): Repository {
        return object : Repository {
            override val orm: ORMTemplate
                get() = this@ORMTemplateImpl
        }
    }
}
