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
package st.orm.spring.kotlin

import org.springframework.transaction.PlatformTransactionManager
import st.orm.EntityCallback
import st.orm.StormConfig
import st.orm.spring.SpringConnectionProvider
import st.orm.spring.SpringExceptionMapper
import st.orm.spring.SpringTransactionTemplateProvider
import st.orm.template.ORMTemplate
import javax.sql.DataSource

/**
 * The canonical plain-Spring (non-Boot) composition of an [ORMTemplate] for Kotlin applications: the template
 * participates in Spring-managed transactions, and Storm's `transaction { }` API runs through Spring's
 * transaction managers.
 *
 * ```kotlin
 * val orm = springOrmTemplate(dataSource) { listOf(transactionManager) }
 * ```
 *
 * Templates that should share transactions must use the same provider instances, so compose one template per
 * application context (typically as a bean).
 *
 * @param dataSource the data source backing the template.
 * @param config the Storm configuration.
 * @param entityCallbacks the entity callbacks to apply.
 * @param transactionManagers supplies the transaction managers of the owning application context.
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
    .transactionTemplateProvider(SpringTransactionTemplateProvider { transactionManagers() })
    .exceptionMapper(SpringExceptionMapper(dataSource))
    .build()
    .withEntityCallbacks(entityCallbacks)
