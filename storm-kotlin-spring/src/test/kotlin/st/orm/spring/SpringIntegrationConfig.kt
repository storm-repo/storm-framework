package st.orm.spring

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import st.orm.spring.kotlin.springOrmTemplate
import st.orm.template.ORMTemplate
import javax.sql.DataSource

/**
 * Test configuration whose [ORMTemplate] is wired to Spring's transaction management via [springOrmTemplate].
 *
 * Used by tests that exercise Spring-managed transactions; tests that exercise Storm's plain JDBC transactions use
 * [IntegrationConfig], whose template falls back to the platform-neutral providers.
 */
@Configuration
internal open class SpringIntegrationConfig : IntegrationConfig() {

    @Bean
    override fun ormTemplate(dataSource: DataSource): ORMTemplate = springOrmTemplate(dataSource) { listOf(transactionManager(dataSource)) }
}
