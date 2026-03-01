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
package st.orm.spring;

import jakarta.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.*;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import st.orm.repository.EntityRepository;
import st.orm.repository.ProjectionRepository;
import st.orm.repository.Repository;
import st.orm.template.ORMTemplate;

/**
 * BeanFactoryPostProcessor that scans base packages for Repository interfaces and registers them as beans.
 */
@SuppressWarnings("ALL")
@Component
public class RepositoryBeanFactoryPostProcessor
        implements BeanFactoryPostProcessor, ResourceLoaderAware {

    private static final Logger LOGGER = LoggerFactory.getLogger("st.orm.spring.repository");

    private final Environment environment = new StandardEnvironment();
    private ResourceLoader resourceLoader;

    /** Override to point to a specific ORMTemplate bean. Null = primary/default. */
    public String getOrmTemplateBeanName() { return null; }

    /** Override with base packages to scan. Empty = no scan. */
    public String[] getRepositoryBasePackages() { return new String[0]; }

    /** Optional qualifier prefix for registered repositories. */
    protected String getRepositoryPrefix() { return ""; }

    @Override
    public void setResourceLoader(@Nonnull ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory beanFactory) {
        String[] bases = getRepositoryBasePackages();
        if (bases == null || bases.length == 0) return;
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false, environment) {
                    @Override
                    protected boolean isCandidateComponent(MetadataReader metadataReader) {
                        var md = metadataReader.getClassMetadata();
                        return md.isIndependent() && md.isInterface();
                    }
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        var md = beanDefinition.getMetadata();
                        return md.isIndependent() && md.isInterface();
                    }
                };
        scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));
        if (resourceLoader != null) scanner.setResourceLoader(resourceLoader);
        List<Class<? extends Repository>> repositoryTypes = Arrays.stream(bases)
                .flatMap(base -> scanner.findCandidateComponents(base).stream())
                .map(definition -> {
                    String name = definition.getBeanClassName();
                    if (name == null) return null;
                    try {
                        @SuppressWarnings("unchecked")
                        Class<? extends Repository> clazz =
                                (Class<? extends Repository>) ClassUtils.forName(name, defaultClassLoader());
                        return clazz;
                    } catch (Throwable ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(t -> t != Repository.class)
                .filter(t -> t != EntityRepository.class)
                .filter(t -> t != ProjectionRepository.class)
                .filter(t -> !hasNoRepositoryBeanAnnotation(t))
                .distinct()
                .collect(Collectors.toList());
        registerRepositories(registry, beanFactory, repositoryTypes.stream());
        RepositoryAutowireCandidateResolver.register(beanFactory);
    }

    private void registerRepositories(
            BeanDefinitionRegistry registry,
            ConfigurableListableBeanFactory beanFactory,
            Stream<Class<? extends Repository>> repositories
    ) {
        repositories.forEach(type -> {
            @SuppressWarnings("unchecked")
            Class<Repository> repositoryType = (Class<Repository>) type;
            Supplier<Repository> supplier = () ->
                    getBeanORMTemplate(beanFactory).repository(repositoryType);
            var definition = BeanDefinitionBuilder.genericBeanDefinition(repositoryType, supplier).getBeanDefinition();
            definition.setAttribute("qualifier", getRepositoryPrefix());
            String prefix = getRepositoryPrefix();
            if (prefix != null && !prefix.isEmpty()) {
                // Set the qualifier attribute to support autowiring by qualifier.
                definition.setAttribute("qualifier", prefix);
                LOGGER.debug("Registering repository {} with qualifier {}.", type.getName(), prefix);
            } else {
                LOGGER.debug("Registering repository {}.", type.getName());
            }
            String name = getRepositoryPrefix() + type.getSimpleName();
            registry.registerBeanDefinition(name, definition);
        });
    }

    private ORMTemplate getBeanORMTemplate(ConfigurableListableBeanFactory beanFactory) {
        String beanName = getOrmTemplateBeanName();
        return (beanName != null)
                ? beanFactory.getBean(beanName, ORMTemplate.class)
                : beanFactory.getBean(ORMTemplate.class);
    }

    /**
     * Reflectively check for @NoRepositoryBean.
     */
    private boolean hasNoRepositoryBeanAnnotation(Class<?> type) {
        String fullyQualifiedName = "org.springframework.data.repository.NoRepositoryBean";
        Class<?> loaded;
        try {
            loaded = Class.forName(fullyQualifiedName, false, type.getClassLoader());
        } catch (ClassNotFoundException e) {
            return false;
        }
        if (Annotation.class.isAssignableFrom(loaded)) {
            var annotationType = (Class<? extends Annotation>) loaded;
            if (type.isAnnotationPresent(annotationType)) {
                return true;
            }
        }
        return false;
    }

    private ClassLoader defaultClassLoader() {
        if (resourceLoader != null && resourceLoader.getClassLoader() != null) {
            return resourceLoader.getClassLoader();
        }
        ClassLoader cl = ClassUtils.getDefaultClassLoader();
        return (cl != null ? cl : ClassLoader.getSystemClassLoader());
    }

    /**
     * Qualifier-aware AutowireCandidateResolver that reads "qualifier" attribute
     * (set on the repository bean definitions we register).
     */
    public static class RepositoryAutowireCandidateResolver implements AutowireCandidateResolver {
        private final AutowireCandidateResolver delegate;

        public RepositoryAutowireCandidateResolver(AutowireCandidateResolver delegate) {
            this.delegate = delegate;
        }

        @Override public boolean isRequired(@Nonnull DependencyDescriptor descriptor) {
            return delegate.isRequired(descriptor);
        }

        @Override public boolean hasQualifier(@Nonnull DependencyDescriptor descriptor) {
            return delegate.hasQualifier(descriptor);
        }

        @Override public Object getSuggestedValue(@Nonnull DependencyDescriptor descriptor) {
            return delegate.getSuggestedValue(descriptor);
        }

        @Override public Object getLazyResolutionProxyIfNecessary(@Nonnull DependencyDescriptor descriptor, String beanName) {
            return delegate.getLazyResolutionProxyIfNecessary(descriptor, beanName);
        }

        @Override public Class<?> getLazyResolutionProxyClass(@Nonnull DependencyDescriptor descriptor, String beanName) {
            return delegate.getLazyResolutionProxyClass(descriptor, beanName);
        }

        @Override public AutowireCandidateResolver cloneIfNecessary() {
            return delegate.cloneIfNecessary();
        }

        @Override
        public boolean isAutowireCandidate(@Nonnull BeanDefinitionHolder holder, @Nonnull DependencyDescriptor descriptor) {
            if (delegate.isAutowireCandidate(holder, descriptor)) {
                return true;
            }
            String requiredQualifier = getRequiredQualifier(descriptor);
            if (requiredQualifier != null) {
                Object attr = holder.getBeanDefinition().getAttribute("qualifier");
                return requiredQualifier.equals(attr);
            }
            return false;
        }

        private String getRequiredQualifier(DependencyDescriptor descriptor) {
            for (Annotation ann : descriptor.getAnnotations()) {
                String v = getQualifierValue(ann);
                if (v != null) return v;
            }
            return null;
        }

        private String getQualifierValue(Annotation annotation) {
            if (annotation instanceof Qualifier q) return q.value();
            Qualifier metadata = annotation.annotationType().getAnnotation(Qualifier.class);
            return (metadata != null ? metadata.value() : null);
        }

        public static void register(ConfigurableListableBeanFactory beanFactory) {
            DefaultListableBeanFactory dlbf = (DefaultListableBeanFactory) beanFactory;
            dlbf.setAutowireCandidateResolver(
                    new RepositoryAutowireCandidateResolver(dlbf.getAutowireCandidateResolver()));
        }
    }
}
