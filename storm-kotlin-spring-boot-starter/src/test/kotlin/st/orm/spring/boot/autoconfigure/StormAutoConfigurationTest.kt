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
import st.orm.Entity
import st.orm.EntityCallback
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
        // StormAutoConfiguration is conditional on a DataSource bean. When a DataSource is available,
        // it should auto-configure an ORMTemplate bean.
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
        // Without a DataSource in the context, StormAutoConfiguration should not create
        // an ORMTemplate bean (the @ConditionalOnBean(DataSource) condition should fail).
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(StormAutoConfiguration::class.java))
            .run { context ->
                context.containsBean("ormTemplate") shouldBe false
            }
    }

    @Test
    fun `user-defined ORMTemplate takes precedence`() {
        // StormAutoConfiguration uses @ConditionalOnMissingBean(ORMTemplate), so a user-defined
        // ORMTemplate bean should take precedence over the auto-configured one.
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
        // StormRepositoryAutoConfiguration should register an AutoConfiguredRepositoryBeanFactoryPostProcessor
        // that scans for repository interfaces and registers them as Spring beans.
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
        // StormRepositoryAutoConfiguration uses @ConditionalOnMissingBean, so a user-defined
        // RepositoryBeanFactoryPostProcessor should prevent the auto-configured one from being created.
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
        // StormTransactionAutoConfiguration should register a SpringTransactionConfiguration bean
        // when a PlatformTransactionManager is present (provided by DataSourceTransactionManagerAutoConfiguration).
        contextRunner
            .withPropertyValues(
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver"
            )
            .run { context ->
                context.getBean(SpringTransactionConfiguration::class.java) shouldNotBe null
            }
    }

    @Test
    fun `storm properties bound from application configuration`() {
        // Storm properties under the "storm.*" prefix should be bound to StormProperties and applied
        // to the ORMTemplate. Each property value set here should be reflected in the bound bean.
        contextRunner
            .withPropertyValues(
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "storm.update.default-mode=ENTITY",
                "storm.update.dirty-check=INSTANCE",
                "storm.update.max-shapes=5",
                "storm.entity-cache.retention=default",
                "storm.template-cache.size=100",
                "storm.ansi-escaping=false",
                "storm.validation.skip=false",
                "storm.validation.warnings-only=true"
            )
            .run { context ->
                context.getBean(ORMTemplate::class.java) shouldNotBe null
                val properties = context.getBean(StormProperties::class.java)
                properties.update.defaultMode shouldBe "ENTITY"
                properties.update.dirtyCheck shouldBe "INSTANCE"
                properties.update.maxShapes shouldBe 5
                properties.entityCache.retention shouldBe "default"
                properties.templateCache.size shouldBe 100
                properties.ansiEscaping shouldBe false
                properties.validation.skip shouldBe false
                properties.validation.warningsOnly shouldBe true
            }
    }

    @Test
    fun `storm properties setters update values`() {
        // StormProperties is a POJO with nested config objects. Setters should correctly update values,
        // which is essential for Spring Boot's property binding mechanism.
        val properties = StormProperties()

        val update = StormProperties.Update()
        update.defaultMode = "FIELD"
        update.dirtyCheck = "FIELD"
        update.maxShapes = 10
        properties.update = update

        val entityCache = StormProperties.EntityCache()
        entityCache.retention = "light"
        properties.entityCache = entityCache

        val templateCache = StormProperties.TemplateCache()
        templateCache.size = 200
        properties.templateCache = templateCache

        val validation = StormProperties.Validation()
        validation.skip = true
        validation.warningsOnly = false
        properties.validation = validation

        properties.ansiEscaping = true

        properties.update.defaultMode shouldBe "FIELD"
        properties.update.dirtyCheck shouldBe "FIELD"
        properties.update.maxShapes shouldBe 10
        properties.entityCache.retention shouldBe "light"
        properties.templateCache.size shouldBe 200
        properties.validation.skip shouldBe true
        properties.validation.warningsOnly shouldBe false
        properties.ansiEscaping shouldBe true
    }

    @Test
    fun `entity callback bean auto-detected`() {
        // When a user defines an EntityCallback bean, StormAutoConfiguration should detect it
        // and wire it into the ORMTemplate for entity lifecycle callbacks.
        contextRunner
            .withPropertyValues(
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver"
            )
            .withUserConfiguration(EntityCallbackConfig::class.java)
            .run { context ->
                context.getBean(ORMTemplate::class.java) shouldNotBe null
                context.containsBean("entityCallback") shouldBe true
            }
    }

    @Configuration
    open class EntityCallbackConfig {
        @Bean
        open fun entityCallback(): EntityCallback<*> = object : EntityCallback<Entity<*>> {}
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
