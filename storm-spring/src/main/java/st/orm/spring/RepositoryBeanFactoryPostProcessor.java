package st.orm.spring;

import jakarta.annotation.Nonnull;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateResolver;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Configuration;
import st.orm.kotlin.repository.KRepository;
import st.orm.kotlin.template.KORMRepositoryTemplate;
import st.orm.repository.EntityRepository;
import st.orm.repository.Repository;
import st.orm.template.ORMRepositoryTemplate;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Configuration
public class RepositoryBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    public String getORMRepositoryTemplateBeanName() {
        return null;
    }

    public String getKORMRepositoryTemplateBeanName() {
        return null;
    }

    public String[] getRepositoryBasePackages() {
        return new String[] {"com.beatgridmedia.panelsync.repository"};
    }

    public String getRepositoryPrefix() {
        return "primary";
    }

    //
    // The logic below is required to support Spring autowiring of our repository interfaces. This logic is here
    // primarily for our own convenience. It is not required for the operation of the ORM framework.
    //

    @SuppressWarnings("unchecked")
    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .forPackages(getRepositoryBasePackages())
                .addScanners(new SubTypesScanner(true))
        );
        List<Class<?>> list = Stream.concat(
                reflections.getSubTypesOf(Repository.class).stream(),
                        reflections.getSubTypesOf(KRepository.class).stream())
                .filter(type -> type != EntityRepository.class)
                .filter(type -> !type.isAnnotationPresent(NoRepositoryBean.class))
                .filter(type -> Stream.of(getRepositoryBasePackages()).anyMatch(type.getName()::startsWith))
                .distinct()
                .toList();
        registerRepositories(registry, beanFactory, list.stream().filter(Repository.class::isAssignableFrom).map(type -> (Class<? extends Repository>) type));
        registerKRepositories(registry, beanFactory, list.stream().filter(KRepository.class::isAssignableFrom).map(type -> (Class<? extends KRepository>) type));
        RepositoryAutowireCandidateResolver.register(beanFactory);
    }

    private void registerRepositories(@Nonnull BeanDefinitionRegistry registry, @Nonnull ConfigurableListableBeanFactory beanFactory, @Nonnull Stream<Class<? extends Repository>> repositories) {
        repositories.forEach(type -> {
            //noinspection unchecked
            Class<Repository> repositoryType = (Class<Repository>) type;
            AbstractBeanDefinition proxyBeanDefinition = BeanDefinitionBuilder
                    .genericBeanDefinition(repositoryType, () -> getBeanORMRepositoryTemplate(beanFactory).repositoryProxy(repositoryType))
                    .getBeanDefinition();
            proxyBeanDefinition.setAttribute("qualifier", getRepositoryPrefix());
            String name = getRepositoryPrefix() + type.getSimpleName();
            registry.registerBeanDefinition(name, proxyBeanDefinition);
        });
    }

    private void registerKRepositories(@Nonnull BeanDefinitionRegistry registry, @Nonnull ConfigurableListableBeanFactory beanFactory, @Nonnull Stream<Class<? extends KRepository>> repositories) {
        repositories.forEach(type -> {
            //noinspection unchecked
            Class<KRepository> repositoryType = (Class<KRepository>) type;
            AbstractBeanDefinition proxyBeanDefinition = BeanDefinitionBuilder
                    .genericBeanDefinition(repositoryType, () -> getBeanKORMRepositoryTemplate(beanFactory).repositoryProxy(repositoryType))
                    .getBeanDefinition();
            proxyBeanDefinition.setAttribute("qualifier", getRepositoryPrefix());
            String name = getRepositoryPrefix() + type.getSimpleName();
            registry.registerBeanDefinition(name, proxyBeanDefinition);
        });
    }

    private ORMRepositoryTemplate getBeanORMRepositoryTemplate(@Nonnull ConfigurableListableBeanFactory beanFactory) {
        if (getORMRepositoryTemplateBeanName() != null) {
            return beanFactory.getBean(getORMRepositoryTemplateBeanName(), ORMRepositoryTemplate.class);
        }
        return beanFactory.getBean(ORMRepositoryTemplate.class);
    }

    private KORMRepositoryTemplate getBeanKORMRepositoryTemplate(@Nonnull ConfigurableListableBeanFactory beanFactory) {
        if (getKORMRepositoryTemplateBeanName() != null) {
            return beanFactory.getBean(getKORMRepositoryTemplateBeanName(), KORMRepositoryTemplate.class);
        }
        return beanFactory.getBean(KORMRepositoryTemplate.class);
    }

    /**
     * Adds qualifier support by looking at the attribute "qualifier" of the bean definition. This works hand in hand
     * with the proxyBeanDefinition.setAttribute("qualifier", getRepositoryPrefix()); call above.
     */
    public static class RepositoryAutowireCandidateResolver implements AutowireCandidateResolver {

        static void register(@Nonnull ConfigurableListableBeanFactory beanFactory) {
            ((DefaultListableBeanFactory) beanFactory).setAutowireCandidateResolver(new RepositoryAutowireCandidateResolver(((DefaultListableBeanFactory) beanFactory).getAutowireCandidateResolver()));
        }

        private final AutowireCandidateResolver delegate;

        public RepositoryAutowireCandidateResolver(AutowireCandidateResolver delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isRequired(@Nonnull DependencyDescriptor descriptor) {
            return delegate.isRequired(descriptor);
        }

        @Override
        public boolean hasQualifier(@Nonnull DependencyDescriptor descriptor) {
            return delegate.hasQualifier(descriptor);
        }

        @Override
        public Object getSuggestedValue(@Nonnull DependencyDescriptor descriptor) {
            return delegate.getSuggestedValue(descriptor);
        }

        @Override
        public Object getLazyResolutionProxyIfNecessary(@Nonnull DependencyDescriptor descriptor, String beanName) {
            return delegate.getLazyResolutionProxyIfNecessary(descriptor, beanName);
        }

        @Override
        public Class<?> getLazyResolutionProxyClass(@Nonnull DependencyDescriptor descriptor, String beanName) {
            return delegate.getLazyResolutionProxyClass(descriptor, beanName);
        }

        @Nonnull
        @Override
        public AutowireCandidateResolver cloneIfNecessary() {
            return delegate.cloneIfNecessary();
        }

        @Override
        public boolean isAutowireCandidate(@Nonnull BeanDefinitionHolder bdHolder, @Nonnull DependencyDescriptor descriptor) {
            if (delegate.isAutowireCandidate(bdHolder, descriptor)) {
                return true;
            } else {
                Optional<String> requiredQualifier = getRequiredQualifier(descriptor);
                if (requiredQualifier.isPresent()) {
                    String beanQualifier = (String) bdHolder.getBeanDefinition().getAttribute("qualifier");
                    return requiredQualifier.get().equals(beanQualifier);
                }
            }
            return false;
        }

        private Optional<String> getRequiredQualifier(DependencyDescriptor descriptor) {
            return Arrays.stream(descriptor.getAnnotations())
                    .map(this::getQualifierValue)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();
        }

        private Optional<String> getQualifierValue(Annotation annotation) {
            if (annotation instanceof Qualifier) {
                return Optional.of(((Qualifier) annotation).value());
            } else if (annotation.annotationType().isAnnotationPresent(Qualifier.class)) {
                Qualifier qualifier = annotation.annotationType().getAnnotation(Qualifier.class);
                return Optional.of(qualifier.value());
            }
            return Optional.empty();
        }
    }
}
