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
package st.orm.spring.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import st.orm.core.spi.ConnectionProvider;
import st.orm.core.spi.TransactionTemplateProvider;

/**
 * Verifies that the transaction integration activates for every transaction manager a Spring Boot application
 * can end up with: the JDBC manager, and the JPA manager that replaces it when JPA is on the class path. The
 * Storm auto-configuration is listed first so the test relies on the declared ordering hints, not on the
 * listing order, to see the manager bean.
 */
public class StormTransactionAutoConfigurationTest {

    @Test
    void activatesWithJdbcTransactionManager() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        StormTransactionAutoConfiguration.class,
                        DataSourceAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:tx-autoconfig-jdbc;DB_CLOSE_DELAY=-1")
                .run(context -> {
                    assertInstanceOf(JdbcTransactionManager.class, context.getBean(PlatformTransactionManager.class));
                    assertEquals(1, context.getBeansOfType(ConnectionProvider.class).size());
                    assertEquals(1, context.getBeansOfType(TransactionTemplateProvider.class).size());
                });
    }

    @Test
    void activatesWithJpaTransactionManager() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        StormTransactionAutoConfiguration.class,
                        DataSourceAutoConfiguration.class,
                        HibernateJpaAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:tx-autoconfig-jpa;DB_CLOSE_DELAY=-1")
                .run(context -> {
                    assertInstanceOf(JpaTransactionManager.class, context.getBean(PlatformTransactionManager.class));
                    assertEquals(1, context.getBeansOfType(ConnectionProvider.class).size());
                    assertEquals(1, context.getBeansOfType(TransactionTemplateProvider.class).size());
                });
    }

    @Test
    void backsOffWithoutTransactionManager() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        StormTransactionAutoConfiguration.class,
                        DataSourceAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:tx-autoconfig-none;DB_CLOSE_DELAY=-1")
                .run(context -> {
                    assertFalse(context.containsBean("stormConnectionProvider"));
                    assertFalse(context.containsBean("stormTransactionTemplateProvider"));
                });
    }
}
