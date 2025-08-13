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

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.BeanDefinitionHolder
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.config.DependencyDescriptor
import org.springframework.beans.factory.support.AutowireCandidateResolver
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.ResourceLoaderAware
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.env.Environment
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ResourceLoader
import org.springframework.core.type.classreading.MetadataReader
import org.springframework.core.type.filter.AssignableTypeFilter
import org.springframework.stereotype.Component
import org.springframework.util.ClassUtils
import st.orm.repository.EntityRepository
import st.orm.repository.ProjectionRepository
import st.orm.repository.Repository
import st.orm.template.ORMTemplate
import java.lang.annotation.Annotation
import java.util.*
import java.util.stream.Stream


/**
 * A [BeanFactoryPostProcessor] that scans the specified base packages for repository interfaces and registers
 * them as beans in the bean factory. This allows repository interfaces to be autowired by the ORM framework.
 */
@Component
open class RepositoryBeanFactoryPostProcessor :
    BeanFactoryPostProcessor,
    ResourceLoaderAware {

    companion object {
        val logger = LoggerFactory.getLogger("st.orm.spring.repository")
    }

    private var resourceLoader: ResourceLoader? = null
    private var environment: Environment = StandardEnvironment()

    open val ormTemplateBeanName: String?
        get() = null

    open val repositoryBasePackages: Array<String>
        get() = emptyArray()

    open val repositoryPrefix: String
        get() = ""

    override fun setResourceLoader(resourceLoader: ResourceLoader) {
        this.resourceLoader = resourceLoader
    }

    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        if (repositoryBasePackages.isEmpty()) return
        val registry = beanFactory as BeanDefinitionRegistry
        val scanner = object : ClassPathScanningCandidateComponentProvider(false, environment) {
            override fun isCandidateComponent(metadataReader: MetadataReader): Boolean {
                val metadata = metadataReader.classMetadata
                return metadata.isIndependent && metadata.isInterface
            }
            override fun isCandidateComponent(beanDefinition: AnnotatedBeanDefinition): Boolean {
                val metadata = beanDefinition.metadata
                return metadata.isIndependent && metadata.isInterface
            }
        }.apply {
            addIncludeFilter(AssignableTypeFilter(Repository::class.java))
            resourceLoader.let { resourceLoader = it }
        }
        val repositoryClasses: List<Class<out Repository>> =
            repositoryBasePackages
                .asSequence()
                .flatMap { base ->
                    scanner.findCandidateComponents(base).asSequence()
                }
                .mapNotNull { bd ->
                    val className = bd.beanClassName ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    ClassUtils.forName(className, defaultClassLoader()) as Class<out Repository>
                }
                .filter { it != Repository::class.java }
                .filter { it != EntityRepository::class.java }
                .filter { it != ProjectionRepository::class.java }
                .filter { !hasNoRepositoryBeanAnnotation(it) }
                .distinct()
                .toList()
        registerRepositories(
            registry,
            beanFactory,
            repositoryClasses.stream()
        )
        RepositoryAutowireCandidateResolver.register(beanFactory)
    }

    private fun registerRepositories(
        registry: BeanDefinitionRegistry,
        beanFactory: ConfigurableListableBeanFactory,
        repositories: Stream<Class<out Repository>>
    ) {
        repositories.forEach { type: Class<out Repository> ->
            val repositoryType = type as Class<Repository>
            val proxyBeanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(repositoryType) {
                    getBeanORMTemplate(beanFactory).repository(repositoryType.kotlin)
                }
                .beanDefinition
            val prefix = repositoryPrefix
            if (prefix.isNotEmpty()) {
                // Set the qualifier attribute to support autowiring by qualifier.
                proxyBeanDefinition.setAttribute("qualifier", prefix)
                logger.debug("Registering repository: ${type.name} with qualifier: $prefix.")
            } else {
                logger.debug("Registering repository: ${type.name}.")
            }
            val name = prefix + type.simpleName
            registry.registerBeanDefinition(name, proxyBeanDefinition)
        }
    }

    private fun getBeanORMTemplate(beanFactory: ConfigurableListableBeanFactory): ORMTemplate {
        val beanName = ormTemplateBeanName
        return if (beanName != null) {
            beanFactory.getBean(beanName, ORMTemplate::class.java)
        } else {
            beanFactory.getBean(ORMTemplate::class.java)
        }
    }

    /**
     * Reflectively check for any @NoRepositoryBean.
     * Works with either your own annotation or Spring Data’s (org.springframework.data.repository.NoRepositoryBean).
     */
    private fun hasNoRepositoryBeanAnnotation(type: Class<*>): Boolean {
        if (type.annotations.any { it.annotationClass.simpleName == "NoRepositoryBean" }) return true
        val candidates = listOf(
            "org.springframework.data.repository.NoRepositoryBean",
            "st.orm.spring.NoRepositoryBean"
        )
        for (fullyQualifiedName in candidates) {
            val loaded: Class<*> = runCatching { Class.forName(fullyQualifiedName, false, type.classLoader) }
                .getOrNull() ?: continue
            if (Annotation::class.java.isAssignableFrom(loaded)) {
                val annotationType = loaded as Class<out kotlin.Annotation>
                if (type.isAnnotationPresent(annotationType)) {
                    return true
                }
            }
        }
        return false
    }

    private fun defaultClassLoader(): ClassLoader =
        resourceLoader?.classLoader ?: ClassUtils.getDefaultClassLoader() ?: ClassLoader.getSystemClassLoader()

    /**
     * Adds qualifier support by looking at the attribute "qualifier" of the bean definition. This works hand in hand
     * with the proxyBeanDefinition.setAttribute("qualifier", repositoryPrefix) call above.
     */
    class RepositoryAutowireCandidateResolver(
        private val delegate: AutowireCandidateResolver
    ) : AutowireCandidateResolver {

        override fun isRequired(descriptor: DependencyDescriptor): Boolean =
            delegate.isRequired(descriptor)

        override fun hasQualifier(descriptor: DependencyDescriptor): Boolean =
            delegate.hasQualifier(descriptor)

        override fun getSuggestedValue(descriptor: DependencyDescriptor): Any? =
            delegate.getSuggestedValue(descriptor)

        override fun getLazyResolutionProxyIfNecessary(
            descriptor: DependencyDescriptor,
            beanName: String?
        ): Any? = delegate.getLazyResolutionProxyIfNecessary(descriptor, beanName)

        override fun getLazyResolutionProxyClass(
            descriptor: DependencyDescriptor,
            beanName: String?
        ): Class<*>? = delegate.getLazyResolutionProxyClass(descriptor, beanName)

        override fun cloneIfNecessary(): AutowireCandidateResolver =
            delegate.cloneIfNecessary()

        override fun isAutowireCandidate(
            holder: BeanDefinitionHolder,
            descriptor: DependencyDescriptor
        ): Boolean {
            if (delegate.isAutowireCandidate(holder, descriptor)) return true
            val requiredQualifier = getRequiredQualifier(descriptor)
            if (requiredQualifier != null) {
                return holder.beanDefinition.getAttribute("qualifier") == requiredQualifier
            }
            return false
        }

        private fun getRequiredQualifier(descriptor: DependencyDescriptor): String? =
            Arrays.stream(descriptor.annotations)
                .map { annotation -> getQualifierValue(annotation) }
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null)

        private fun getQualifierValue(annotation: kotlin.Annotation): String? {
            if (annotation is Qualifier) return annotation.value
            val metadata = annotation.annotationClass.java.getAnnotation(Qualifier::class.java)
            return metadata?.value
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