package st.orm;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EntityScan
@ComponentScan("st.orm.repository.spring")
@EnableJpaRepositories
@EnableTransactionManagement
public class IntegrationConfig {
}