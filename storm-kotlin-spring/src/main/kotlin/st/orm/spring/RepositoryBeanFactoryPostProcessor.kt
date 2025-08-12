/*
 * Copyright 2024 - 2025 the original author or authors.
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
package st.orm.spring

import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ConfigurationBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.BeanDefinitionHolder
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.config.DependencyDescriptor
import org.springframework.beans.factory.support.AutowireCandidateResolver
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.stereotype.Component
import st.orm.repository.EntityRepository
import st.orm.repository.ProjectionRepository
import st.orm.repository.Repository
import st.orm.template.ORMTemplate
import java.util.*
import java.util.stream.Stream

/**
 * A [BeanFactoryPostProcessor] that scans the specified base packages for repository interfaces and registers
 * them as beans in the bean factory. This allows repository interfaces to be autowired by the ORM framework.
 */
@Component
open class RepositoryBeanFactoryPostProcessor : BeanFactoryPostProcessor {
    open val ormTemplateBeanName: String?
        /**
         * The name of the ORM template bean to use for repository autowiring. If not specified, the default ORM template
         * bean will be used.
         *
         * @return the name of the ORM template bean.
         */
        get() = null

    open val repositoryBasePackages: Array<String>
        /**
         * The base packages to scan for repository interfaces. This is used to find all repository interfaces that should
         * be autowired by the ORM framework. If not specified, no repositories will be autowired.
         *
         * @return the base packages to scan for repository interfaces.
         */
        get() = emptyArray<String>()

    open val repositoryPrefix: String
        /**
         * The qualifier prefix to use for the repository beans. This is used to distinguish between multiple repository
         * beans of the same type. The default is an empty string.
         *
         * @return the qualifier prefix to use for the repository beans.
         */
        get() = ""

    //
    // The logic below is required to support Spring autowiring of our repository interfaces. This logic is here
    // primarily for our own convenience. It is not required for the operation of the ORM framework.
    //

    /**
     * Scans the specified base packages for repository interfaces and registers them as beans in the bean factory.
     *
     * @param beanFactory the bean factory to register the repository beans with.
     * @throws org.springframework.beans.BeansException if an error occurs while registering the repository beans.
     */
    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        val registry = beanFactory as BeanDefinitionRegistry
        val reflections = Reflections(
            ConfigurationBuilder()
                .forPackages(*this.repositoryBasePackages)
                .addScanners(SubTypesScanner(true))
        )
        val list =
            reflections.getSubTypesOf<Repository>(Repository::class.java).stream()
                .filter { type -> type != EntityRepository::class.java }
                .filter { type -> type != ProjectionRepository::class.java }
                .filter { type -> !type.isAnnotationPresent(NoRepositoryBean::class.java) }
                .filter { type ->
                    Stream.of<String>(*this.repositoryBasePackages)
                        .anyMatch { prefix -> type.getName().startsWith(prefix) }
                }
                .distinct()
                .toList()
        registerRepositories(
            registry,
            beanFactory,
            list.stream().filter { cls -> Repository::class.java.isAssignableFrom(cls) })
        RepositoryAutowireCandidateResolver.Companion.register(beanFactory)
    }

    private fun registerRepositories(
        registry: BeanDefinitionRegistry,
        beanFactory: ConfigurableListableBeanFactory,
        repositories: Stream<Class<out Repository>>
    ) {
        repositories.forEach { type: Class<out Repository> ->
            val repositoryType = type as Class<Repository>
            val proxyBeanDefinition = BeanDefinitionBuilder.genericBeanDefinition(repositoryType) {
                getBeanORMTemplate(beanFactory).repository(repositoryType.kotlin)
            }
                .beanDefinition
            proxyBeanDefinition.setAttribute("qualifier", this.repositoryPrefix)
            val name = this.repositoryPrefix + type.getSimpleName()
            registry.registerBeanDefinition(name, proxyBeanDefinition)
        }
    }

    private fun getBeanORMTemplate(beanFactory: ConfigurableListableBeanFactory): ORMTemplate {
        val beanName = ormTemplateBeanName
        if (beanName != null) {
            return beanFactory.getBean(beanName, ORMTemplate::class.java)
        }
        return beanFactory.getBean(ORMTemplate::class.java)
    }

    /**
     * Adds qualifier support by looking at the attribute "qualifier" of the bean definition. This works hand in hand
     * with the proxyBeanDefinition.setAttribute("qualifier", getRepositoryPrefix()); call above.
     */
    class RepositoryAutowireCandidateResolver
    /**
     * Creates a new [RepositoryAutowireCandidateResolver] instance.
     *
     * @param delegate the delegate autowire candidate resolver.
     */(private val delegate: AutowireCandidateResolver) : AutowireCandidateResolver {
        override fun isRequired(descriptor: DependencyDescriptor): Boolean {
            return delegate.isRequired(descriptor)
        }

        override fun hasQualifier(descriptor: DependencyDescriptor): Boolean {
            return delegate.hasQualifier(descriptor)
        }

        override fun getSuggestedValue(descriptor: DependencyDescriptor): Any? {
            return delegate.getSuggestedValue(descriptor)
        }

        override fun getLazyResolutionProxyIfNecessary(
            descriptor: DependencyDescriptor,
            beanName: String?
        ): Any? {
            return delegate.getLazyResolutionProxyIfNecessary(descriptor, beanName)
        }

        override fun getLazyResolutionProxyClass(
            descriptor: DependencyDescriptor,
            beanName: String?
        ): Class<*>? {
            return delegate.getLazyResolutionProxyClass(descriptor, beanName)
        }

        override fun cloneIfNecessary(): AutowireCandidateResolver {
            return delegate.cloneIfNecessary()
        }

        override fun isAutowireCandidate(
            bdHolder: BeanDefinitionHolder,
            descriptor: DependencyDescriptor
        ): Boolean {
            if (delegate.isAutowireCandidate(bdHolder, descriptor)) {
                return true
            } else {
                val requiredQualifier = getRequiredQualifier(descriptor)
                if (requiredQualifier != null) {
                    return bdHolder.beanDefinition.getAttribute("qualifier") == requiredQualifier
                }
            }
            return false
        }

        private fun getRequiredQualifier(descriptor: DependencyDescriptor): String? {
            return Arrays.stream(descriptor.annotations)
                .map { annotation -> this.getQualifierValue(annotation) }
                .filter { obj -> obj != null }
                .findFirst()
                .orElse(null)
        }

        private fun getQualifierValue(annotation: Annotation): String? {
            if (annotation is Qualifier) {
                return annotation.value
            }
            val qualifier = annotation.annotationClass.java.getAnnotation(Qualifier::class.java)
            if (qualifier != null) {
                return qualifier.value
            }
            return null
        }

        companion object {
            @JvmStatic
            fun register(beanFactory: ConfigurableListableBeanFactory) {
                (beanFactory as DefaultListableBeanFactory).autowireCandidateResolver =
                    RepositoryAutowireCandidateResolver(beanFactory.autowireCandidateResolver)
            }
        }
    }
}