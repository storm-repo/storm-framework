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
package st.orm.spring.boot.autoconfigure;

import jakarta.annotation.Nonnull;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import st.orm.spring.RepositoryBeanFactoryPostProcessor;

/**
 * A {@link RepositoryBeanFactoryPostProcessor} that automatically resolves base packages from Spring Boot's
 * {@link AutoConfigurationPackages}.
 *
 * <p>This allows Storm repositories to be discovered without requiring the user to manually specify base packages.</p>
 */
public class AutoConfiguredRepositoryBeanFactoryPostProcessor extends RepositoryBeanFactoryPostProcessor {

    private String[] resolvedPackages;

    /**
     * Resolves the auto-configuration base packages from {@link AutoConfigurationPackages} and then delegates to the
     * superclass to scan and register repository beans.
     *
     * @param beanFactory the bean factory to post-process.
     */
    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory beanFactory) {
        try {
            resolvedPackages = AutoConfigurationPackages.get(beanFactory).toArray(String[]::new);
        } catch (IllegalStateException e) {
            resolvedPackages = new String[0];
        }
        super.postProcessBeanFactory(beanFactory);
    }

    /**
     * Returns the base packages resolved from {@link AutoConfigurationPackages}.
     *
     * @return the base packages to scan for repository interfaces, or an empty array if no packages were resolved.
     */
    @Override
    public String[] getRepositoryBasePackages() {
        return resolvedPackages != null ? resolvedPackages : new String[0];
    }
}
