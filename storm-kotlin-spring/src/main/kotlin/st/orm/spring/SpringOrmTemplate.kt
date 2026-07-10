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
package st.orm.spring

import org.springframework.transaction.PlatformTransactionManager
import st.orm.EntityCallback
import st.orm.StormConfig
import st.orm.template.ORMTemplate
import javax.sql.DataSource

/**
 * Creates an [ORMTemplate] wired to Spring's transaction management.
 *
 * This is the canonical composition for plain Spring (non-Boot) applications; the Spring Boot starter performs the
 * equivalent wiring automatically. The returned template participates in Spring-managed (`@Transactional`)
 * transactions and bridges Storm's `transaction { }` API into the given transaction managers.
 *
 * Example:
 * ```kotlin
 * @Configuration
 * @EnableTransactionManagement
 * open class AppConfig {
 *     @Bean
 *     open fun ormTemplate(
 *         dataSource: DataSource,
 *         transactionManagers: ObjectProvider<PlatformTransactionManager>,
 *     ): ORMTemplate = springOrmTemplate(dataSource) { transactionManagers.orderedStream().toList() }
 * }
 * ```
 *
 * @param dataSource the data source to use for database operations.
 * @param config the Storm configuration to apply.
 * @param entityCallbacks the entity callbacks to register on the template.
 * @param transactionManagers supplies the transaction managers of the owning application context; resolved lazily
 * on first use.
 * @return an ORM template wired to Spring's transaction management.
 * @since 1.13
 */
fun springOrmTemplate(
    dataSource: DataSource,
    config: StormConfig = StormConfig.defaults(),
    entityCallbacks: List<EntityCallback<*>> = emptyList(),
    transactionManagers: () -> List<PlatformTransactionManager>,
): ORMTemplate = ORMTemplate.builder(dataSource)
    .config(config)
    .connectionProvider(SpringConnectionProvider())
    .transactionTemplateProvider(SpringTransactionTemplateProvider(transactionManagers))
    .build()
    .withEntityCallbacks(entityCallbacks)
