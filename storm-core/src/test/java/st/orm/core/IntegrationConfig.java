package st.orm.core;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@ComponentScan("st.orm.core.repository.spring")
@EnableTransactionManagement
public class IntegrationConfig {
}
