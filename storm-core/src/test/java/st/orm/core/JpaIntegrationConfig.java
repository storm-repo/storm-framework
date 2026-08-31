package st.orm.core;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * The context for the tests that exercise JPA interoperability itself; every other test runs on the JPA-free
 * {@link IntegrationConfig}.
 */
@Configuration
@EntityScan
@ComponentScan("st.orm.core.repository.spring")
@EnableJpaRepositories
@EnableTransactionManagement
public class JpaIntegrationConfig {
}
