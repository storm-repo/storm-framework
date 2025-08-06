package st.orm.kt

import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import st.orm.kt.template.ORMTemplate
import javax.sql.DataSource

@Configuration
@EnableTransactionManagement
open class IntegrationConfig {

    @Bean
    open fun dataSource(): DataSource =
        DataSourceBuilder.create()
            .url("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false")
            .username("sa")
            .password("")
            .driverClassName("org.h2.Driver")
            .build()

    @Bean
    open fun transactionManager(dataSource: DataSource): DataSourceTransactionManager =
        DataSourceTransactionManager(dataSource)

    @Bean
    open fun ormTemplate(dataSource: DataSource): ORMTemplate =
        ORMTemplate.of(dataSource)
}