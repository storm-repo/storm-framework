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
package st.orm.spring.boot.autoconfigure

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.transaction.PlatformTransactionManager
import st.orm.core.spi.ConnectionProvider
import st.orm.core.spi.TransactionTemplateProvider
import st.orm.spring.SpringConnectionProvider
import st.orm.spring.SpringTransactionTemplateProvider

/**
 * Auto-configuration that provides Spring-aware [ConnectionProvider] and [TransactionTemplateProvider] beans when a
 * [PlatformTransactionManager] is available.
 *
 * The providers carry the transaction managers of this application context and are handed to the
 * [st.orm.template.ORMTemplate] created by [StormAutoConfiguration]; nothing is registered globally, so multiple
 * application contexts in one JVM each get their own, correctly matched transaction integration. Define your own
 * [ConnectionProvider] or [TransactionTemplateProvider] bean to override the defaults.
 *
 * The ordering hints reference DataSourceTransactionManagerAutoConfiguration by name rather than by
 * class literal: the class moved to the modular spring-boot-jdbc jar in Spring Boot 4, and a class
 * literal to whichever location is absent would fail annotation introspection. Name-based hints are
 * ignored when the class is not on the classpath, so both locations can be listed safely.
 */
@AutoConfiguration(
    afterName = [
        // Spring Boot 3.x location.
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
        // Spring Boot 4.x location.
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
    ],
)
@ConditionalOnBean(PlatformTransactionManager::class)
open class StormTransactionAutoConfiguration {

    /**
     * Provides the connection provider that binds connections to Spring's transaction management.
     */
    @Bean
    @ConditionalOnMissingBean(ConnectionProvider::class)
    open fun stormConnectionProvider(): ConnectionProvider = SpringConnectionProvider()

    /**
     * Provides the transaction template provider that bridges Storm's transaction API into this application
     * context's [PlatformTransactionManager] beans.
     */
    @Bean
    @ConditionalOnMissingBean(TransactionTemplateProvider::class)
    open fun stormTransactionTemplateProvider(
        transactionManagers: ObjectProvider<PlatformTransactionManager>,
    ): TransactionTemplateProvider = SpringTransactionTemplateProvider { transactionManagers.orderedStream().toList() }
}
