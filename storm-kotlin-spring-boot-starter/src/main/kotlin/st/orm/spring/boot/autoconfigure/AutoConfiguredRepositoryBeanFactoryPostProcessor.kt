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
package st.orm.spring.boot.autoconfigure

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import st.orm.spring.RepositoryBeanFactoryPostProcessor

/**
 * A [RepositoryBeanFactoryPostProcessor] that automatically resolves base packages from Spring Boot's
 * [AutoConfigurationPackages].
 *
 * This allows Storm repositories to be discovered without requiring the user to manually specify base packages.
 */
class AutoConfiguredRepositoryBeanFactoryPostProcessor : RepositoryBeanFactoryPostProcessor() {

    private var resolvedPackages: Array<String>? = null

    /**
     * Resolves the auto-configuration base packages from [AutoConfigurationPackages] and then delegates to the
     * superclass to scan and register repository beans.
     *
     * @param beanFactory the bean factory to post-process.
     */
    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        resolvedPackages = try {
            AutoConfigurationPackages.get(beanFactory).toTypedArray()
        } catch (e: IllegalStateException) {
            emptyArray()
        }
        super.postProcessBeanFactory(beanFactory)
    }

    /**
     * Returns the base packages resolved from [AutoConfigurationPackages], or an empty array if no packages were
     * resolved.
     */
    override val repositoryBasePackages: Array<String>
        get() = resolvedPackages ?: emptyArray()
}
