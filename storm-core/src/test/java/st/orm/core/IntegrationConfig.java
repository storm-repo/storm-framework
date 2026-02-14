package st.orm.core;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EntityScan
@ComponentScan("st.orm.core.repository.spring")
@EnableJpaRepositories
@EnableTransactionManagement
public class IntegrationConfig {
}
