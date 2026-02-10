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

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import st.orm.spring.RepositoryBeanFactoryPostProcessor
import st.orm.spring.SpringTransactionConfiguration
import st.orm.template.ORMTemplate
import javax.sql.DataSource

class StormAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                DataSourceAutoConfiguration::class.java,
                DataSourceTransactionManagerAutoConfiguration::class.java,
                StormAutoConfiguration::class.java,
                StormRepositoryAutoConfiguration::class.java,
                StormTransactionAutoConfiguration::class.java
            )
        )

    @Test
    fun `ormTemplate bean created when DataSource present`() {
        contextRunner
            .withPropertyValues(
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver"
            )
            .run { context ->
                context.getBean(ORMTemplate::class.java) shouldNotBe null
            }
    }

    @Test
    fun `ormTemplate bean not created without DataSource`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(StormAutoConfiguration::class.java))
            .run { context ->
                context.containsBean("ormTemplate") shouldBe false
            }
    }

    @Test
    fun `user-defined ORMTemplate takes precedence`() {
        contextRunner
            .withPropertyValues(
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver"
            )
            .withUserConfiguration(CustomOrmTemplateConfig::class.java)
            .run { context ->
                val ormTemplate = context.getBean(ORMTemplate::class.java)
                ormTemplate shouldBe context.getBean("customOrmTemplate")
            }
    }

    @Test
    fun `repository bean factory post processor auto-configured`() {
        contextRunner
            .withPropertyValues(
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver"
            )
            .run { context ->
                context.getBean(RepositoryBeanFactoryPostProcessor::class.java)
                    .shouldBeInstanceOf<AutoConfiguredRepositoryBeanFactoryPostProcessor>()
            }
    }

    @Test
    fun `user-defined RepositoryBeanFactoryPostProcessor takes precedence`() {
        contextRunner
            .withPropertyValues(
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver"
            )
            .withUserConfiguration(CustomRepositoryPostProcessorConfig::class.java)
            .run { context ->
                context.containsBean("repositoryBeanFactoryPostProcessor") shouldBe true
                context.containsBeanDefinition("autoConfiguredRepositoryBeanFactoryPostProcessor") shouldBe false
            }
    }

    @Test
    fun `transaction auto-configuration activates SpringTransactionConfiguration`() {
        contextRunner
            .withPropertyValues(
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver"
            )
            .run { context ->
                context.getBean(SpringTransactionConfiguration::class.java) shouldNotBe null
            }
    }

    @Configuration
    open class CustomOrmTemplateConfig {
        @Bean
        open fun customOrmTemplate(dataSource: DataSource): ORMTemplate = ORMTemplate.of(dataSource)
    }

    @Configuration
    open class CustomRepositoryPostProcessorConfig {
        companion object {
            @JvmStatic
            @Bean
            fun repositoryBeanFactoryPostProcessor(): RepositoryBeanFactoryPostProcessor =
                object : RepositoryBeanFactoryPostProcessor() {
                    override val repositoryBasePackages: Array<String> get() = arrayOf("com.example")
                }
        }
    }
}
