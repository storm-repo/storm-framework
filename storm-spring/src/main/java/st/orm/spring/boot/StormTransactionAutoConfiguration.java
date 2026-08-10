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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import st.orm.core.spi.ConnectionProvider;
import st.orm.core.spi.TransactionTemplateProvider;
import st.orm.spring.SpringConnectionProvider;
import st.orm.spring.SpringTransactionTemplateProvider;

/**
 * Auto-configuration that provides the Spring-aware transaction integration beans when a
 * {@link PlatformTransactionManager} is available. Shared by the Java and Kotlin Spring Boot starters.
 *
 * <p>The transaction template provider bridges Storm's programmatic transaction API into Spring's transaction
 * managers and exposes a transaction context for Spring-managed ({@code @Transactional}) transactions; the
 * connection provider binds connections through {@code DataSourceUtils}. Both are handed to the
 * {@code ORMTemplate} created by the starter's auto-configuration; nothing is registered globally. Define your
 * own {@link ConnectionProvider} or {@link TransactionTemplateProvider} bean to override.</p>
 *
 * <p>The ordering hints cover both auto-configurations that register a transaction manager: the JDBC one and,
 * for applications with JPA on the class path where the JDBC one backs off, the Hibernate JPA one. They
 * reference the classes by name rather than by class literal: the classes moved to modular jars in Spring
 * Boot 4, and a class literal to whichever location is absent would fail annotation introspection. Name-based
 * hints are ignored when the class is not on the classpath, so both locations can be listed safely.</p>
 *
 * @since 1.13
 */
@AutoConfiguration(
        afterName = {
                // Spring Boot 3.x locations.
                "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
                // Spring Boot 4.x locations.
                "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
                "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        })
@ConditionalOnBean(PlatformTransactionManager.class)
public class StormTransactionAutoConfiguration {

    /**
     * Provides the connection provider that binds connections to Spring's transaction management.
     */
    @Bean
    @ConditionalOnMissingBean(ConnectionProvider.class)
    public ConnectionProvider stormConnectionProvider() {
        return new SpringConnectionProvider();
    }

    /**
     * Provides the transaction template provider that bridges Storm transactions into Spring's transaction
     * managers and exposes Spring-managed transactions to Storm templates.
     */
    @Bean
    @ConditionalOnMissingBean(TransactionTemplateProvider.class)
    public TransactionTemplateProvider stormTransactionTemplateProvider(
            ObjectProvider<PlatformTransactionManager> transactionManagers) {
        return new SpringTransactionTemplateProvider(() -> transactionManagers.orderedStream().toList());
    }
}
