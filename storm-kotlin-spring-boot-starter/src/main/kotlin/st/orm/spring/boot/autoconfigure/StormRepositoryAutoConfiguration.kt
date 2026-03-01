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

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import st.orm.spring.RepositoryBeanFactoryPostProcessor
import st.orm.template.ORMTemplate

/**
 * Auto-configuration that registers a [RepositoryBeanFactoryPostProcessor] to scan for Storm repository interfaces.
 * The base packages are automatically resolved from Spring Boot's auto-configuration packages.
 *
 * If the user defines their own `RepositoryBeanFactoryPostProcessor` bean, this auto-configured one backs off.
 */
@AutoConfiguration
@ConditionalOnClass(ORMTemplate::class)
open class StormRepositoryAutoConfiguration {

    companion object {
        /**
         * Creates an [AutoConfiguredRepositoryBeanFactoryPostProcessor] that scans for Storm repository interfaces
         * in the auto-configuration base packages.
         *
         * This bean backs off if the user has already defined their own [RepositoryBeanFactoryPostProcessor] bean.
         *
         * @return a new [AutoConfiguredRepositoryBeanFactoryPostProcessor] instance.
         */
        @JvmStatic
        @Bean
        @ConditionalOnMissingBean(RepositoryBeanFactoryPostProcessor::class)
        fun repositoryBeanFactoryPostProcessor(): AutoConfiguredRepositoryBeanFactoryPostProcessor = AutoConfiguredRepositoryBeanFactoryPostProcessor()
    }
}
