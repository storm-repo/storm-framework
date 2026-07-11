package st.orm.spring

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import st.orm.spring.repository.VisitRepository
import st.orm.template.ORMTemplate
import javax.sql.DataSource

/**
 * Verifies that [EnableStormRepositories] binds repositories through the Kotlin adapter when
 * storm-kotlin-spring is on the classpath.
 */
class EnableStormRepositoriesTest {

    @Configuration
    open class DatabaseConfiguration {
        @Bean
        open fun dataSource(): DataSource {
            val dataSource = DataSourceBuilder.create()
                .url("jdbc:h2:mem:enablerepositoriestestkt;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build()
            ResourceDatabasePopulator(ClassPathResource("data.sql")).execute(dataSource)
            return dataSource
        }

        @Bean
        open fun ormTemplate(dataSource: DataSource): ORMTemplate = ORMTemplate.of(dataSource)
    }

    @Configuration
    @EnableStormRepositories(basePackages = ["st.orm.spring.repository"], ormTemplateBeanName = "ormTemplate")
    open class RepositoriesConfiguration

    @Test
    fun `annotation registers repositories through the Kotlin adapter`() {
        AnnotationConfigApplicationContext(
            DatabaseConfiguration::class.java,
            RepositoriesConfiguration::class.java,
        ).use { context ->
            // The registrar must pick the Kotlin adapter: repositories bind through the KClass API.
            context.getBean("stormRepositoriesPostProcessor")
                .shouldBeInstanceOf<st.orm.spring.kotlin.RepositoryBeanFactoryPostProcessor>()
            val visitRepository = context.getBean(VisitRepository::class.java)
            visitRepository.count() shouldBe 14
        }
    }
}
