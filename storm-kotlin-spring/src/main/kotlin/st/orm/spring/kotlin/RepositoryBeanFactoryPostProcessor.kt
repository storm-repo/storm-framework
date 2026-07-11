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
package st.orm.spring.kotlin

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.stereotype.Component
import st.orm.repository.EntityRepository
import st.orm.repository.ProjectionRepository
import st.orm.repository.Repository
import st.orm.spring.AbstractRepositoryBeanFactoryPostProcessor
import st.orm.template.ORMTemplate

/**
 * BeanFactoryPostProcessor that scans base packages for Repository interfaces and registers them as beans.
 *
 * The scanning and registration engine lives in [AbstractRepositoryBeanFactoryPostProcessor]; this class binds
 * it to the Kotlin API's repository types. Subclasses override the engine's methods, e.g.
 * `override fun getRepositoryBasePackages(): Array<String>`.
 */
@Component
open class RepositoryBeanFactoryPostProcessor : AbstractRepositoryBeanFactoryPostProcessor() {

    override fun getRepositoryType(): Class<*> = Repository::class.java

    override fun getExcludedRepositoryTypes(): Set<Class<*>> = setOf(Repository::class.java, EntityRepository::class.java, ProjectionRepository::class.java)

    override fun createRepository(beanFactory: ConfigurableListableBeanFactory, repositoryType: Class<*>): Any {
        val orm = getBeanORMTemplate(beanFactory)
        @Suppress("UNCHECKED_CAST")
        return orm.repository((repositoryType as Class<Repository>).kotlin)
    }

    private fun getBeanORMTemplate(beanFactory: ConfigurableListableBeanFactory): ORMTemplate {
        val beanName = getOrmTemplateBeanName()
        return if (beanName != null) {
            beanFactory.getBean(beanName, ORMTemplate::class.java)
        } else {
            beanFactory.getBean(ORMTemplate::class.java)
        }
    }
}
