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
package st.orm.spring.boot.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import st.orm.core.spi.TransactionTemplateProvider;
import st.orm.spring.SpringTransactionTemplateProvider;

/**
 * Auto-configuration that provides a Spring-aware {@link TransactionTemplateProvider} bean when a
 * {@link PlatformTransactionManager} is available.
 *
 * <p>The provider exposes a transaction context bound to Spring's {@code TransactionSynchronizationManager}, giving
 * templates transaction-scoped entity caching for the duration of Spring-managed transactions. It is handed to the
 * {@code ORMTemplate} created by {@link StormAutoConfiguration}; nothing is registered globally. Define your own
 * {@link TransactionTemplateProvider} bean to override.</p>
 *
 * <p>The ordering hints reference DataSourceTransactionManagerAutoConfiguration by name rather than by class
 * literal: the class moved to the modular spring-boot-jdbc jar in Spring Boot 4, and a class literal to whichever
 * location is absent would fail annotation introspection. Name-based hints are ignored when the class is not on the
 * classpath, so both locations can be listed safely.</p>
 */
@AutoConfiguration(
        afterName = {
                // Spring Boot 3.x location.
                "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
                // Spring Boot 4.x location.
                "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
        })
@ConditionalOnBean(PlatformTransactionManager.class)
public class StormTransactionAutoConfiguration {

    /**
     * Provides the transaction template provider that exposes Spring-managed transactions to Storm templates.
     */
    @Bean
    @ConditionalOnMissingBean(TransactionTemplateProvider.class)
    public TransactionTemplateProvider stormTransactionTemplateProvider() {
        return new SpringTransactionTemplateProvider();
    }
}
