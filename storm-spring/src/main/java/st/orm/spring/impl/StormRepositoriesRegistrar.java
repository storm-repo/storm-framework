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
package st.orm.spring.impl;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import st.orm.spring.AbstractRepositoryBeanFactoryPostProcessor;
import st.orm.spring.EnableStormRepositories;
import st.orm.spring.RepositoryBeanFactoryPostProcessor;

/**
 * Registers the repository scanning post-processor for {@link EnableStormRepositories}.
 *
 * <p>The repositories are bound through the language stack present on the classpath: the Kotlin adapter
 * when storm-kotlin-spring is available, the Java adapter otherwise (exactly one language stack exists per
 * application, as the stacks share class names).</p>
 *
 * @since 1.13
 */
public class StormRepositoriesRegistrar implements ImportBeanDefinitionRegistrar {

    static final String BEAN_NAME = "stormRepositoriesPostProcessor";

    private static final String KOTLIN_ADAPTER = "st.orm.spring.kotlin.RepositoryBeanFactoryPostProcessor";

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) {
        var attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EnableStormRepositories.class.getName()));
        if (attributes == null) {
            return;
        }
        Set<String> basePackages = new LinkedHashSet<>();
        for (String basePackage : attributes.getStringArray("basePackages")) {
            if (!basePackage.isBlank()) {
                basePackages.add(basePackage);
            }
        }
        for (Class<?> basePackageClass : attributes.getClassArray("basePackageClasses")) {
            basePackages.add(ClassUtils.getPackageName(basePackageClass));
        }
        if (basePackages.isEmpty()) {
            basePackages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
        }
        String ormTemplateBeanName = attributes.getString("ormTemplateBeanName");
        String repositoryPrefix = attributes.getString("repositoryPrefix");
        var beanDefinition = BeanDefinitionBuilder.genericBeanDefinition(resolvePostProcessorType())
                .addConstructorArgValue(basePackages.toArray(String[]::new))
                .addConstructorArgValue(ormTemplateBeanName.isEmpty() ? null : ormTemplateBeanName)
                .addConstructorArgValue(repositoryPrefix)
                .getBeanDefinition();
        beanDefinition.setRole(org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE);
        registry.registerBeanDefinition(BEAN_NAME, beanDefinition);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends AbstractRepositoryBeanFactoryPostProcessor> resolvePostProcessorType() {
        ClassLoader classLoader = StormRepositoriesRegistrar.class.getClassLoader();
        if (ClassUtils.isPresent(KOTLIN_ADAPTER, classLoader)) {
            try {
                return (Class<? extends AbstractRepositoryBeanFactoryPostProcessor>)
                        ClassUtils.forName(KOTLIN_ADAPTER, classLoader);
            } catch (ClassNotFoundException ignored) {
                // Fall through to the Java adapter.
            }
        }
        return RepositoryBeanFactoryPostProcessor.class;
    }
}
