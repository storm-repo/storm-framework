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
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import st.orm.EntityCallback
import st.orm.StormConfig
import st.orm.core.spi.ConnectionProvider
import st.orm.core.spi.ExceptionMapper
import st.orm.core.spi.QueryObserver
import st.orm.core.spi.TransactionTemplateProvider
import st.orm.core.template.impl.SchemaValidator
import st.orm.template.ORMTemplate
import javax.sql.DataSource

/**
 * Auto-configuration for the Storm ORM framework.
 *
 * Creates an [ORMTemplate] bean from the available [DataSource] if no `ORMTemplate` bean has been defined by the user.
 * A [StormConfig] is built from the bound [StormProperties] and passed to the `ORMTemplate` factory.
 *
 * @see StormConfig
 */
@AutoConfiguration
@ConditionalOnClass(ORMTemplate::class)
@ConditionalOnBean(DataSource::class)
@EnableConfigurationProperties(StormProperties::class)
open class StormAutoConfiguration {

    private val logger = org.slf4j.LoggerFactory.getLogger(StormAutoConfiguration::class.java)

    /**
     * Creates an [ORMTemplate] bean using the provided [DataSource] and [StormProperties].
     *
     * A [StormConfig] is built from the bound properties. Fields not explicitly configured in `application.yml`
     * fall back to system properties and then to built-in defaults.
     *
     * Integration strategies are consumed from the application context when present: the Spring-aware connection and
     * transaction template providers contributed by [StormTransactionAutoConfiguration] (or user-defined
     * replacements), and optional [ExceptionMapper] and [QueryObserver] beans. Without such beans the template falls
     * back to `ServiceLoader` discovery, matching standalone behavior.
     *
     * This bean backs off if the user has already defined their own `ORMTemplate` bean.
     *
     * @param dataSource the data source to use for database operations.
     * @param properties the Storm configuration properties bound from `storm.*`.
     * @return a new [ORMTemplate] instance.
     */
    @Bean
    @ConditionalOnMissingBean(ORMTemplate::class)
    open fun ormTemplate(
        dataSource: DataSource,
        properties: StormProperties,
        entityCallbacks: List<EntityCallback<*>>,
        connectionProvider: ObjectProvider<ConnectionProvider>,
        transactionTemplateProvider: ObjectProvider<TransactionTemplateProvider>,
        exceptionMapper: ObjectProvider<ExceptionMapper>,
        queryObserver: ObjectProvider<QueryObserver>,
    ): ORMTemplate {
        val builder = ORMTemplate.builder(dataSource).config(toStormConfig(properties))
        connectionProvider.ifAvailable { builder.connectionProvider(it) }
        transactionTemplateProvider.ifAvailable { builder.transactionTemplateProvider(it) }
        exceptionMapper.ifAvailable { builder.exceptionMapper(it) }
        queryObserver.ifAvailable { builder.queryObserver(it) }
        return builder.build().withEntityCallbacks(entityCallbacks)
    }

    /**
     * Runs schema validation after all singleton beans have been fully initialized. This guarantees that migration
     * tools like Flyway and Liquibase (or any bean-based migration mechanism) have completed their work before
     * validation occurs.
     *
     * Validation defaults to `fail`: every entity and projection is validated against the live database schema and
     * mismatches abort startup. Set `storm.validation.schema_mode` to `warn` or `none` to relax or opt out.
     */
    @Bean
    open fun stormSchemaValidator(
        dataSource: DataSource,
        properties: StormProperties,
    ): SmartInitializingSingleton = SmartInitializingSingleton {
        val schemaMode = properties.validation.schemaMode?.trim()?.ifBlank { null } ?: "fail"
        if (schemaMode.equals("none", ignoreCase = true)) {
            return@SmartInitializingSingleton
        }
        val validator = SchemaValidator.of(dataSource)
        when {
            schemaMode.equals("fail", ignoreCase = true) -> {
                validator.validateOrThrow()
                logger.info("Storm schema validation passed (mode=fail).")
            }
            schemaMode.equals("warn", ignoreCase = true) -> validator.validateOrWarn()
            else -> logger.warn("Unknown schema validation mode: '$schemaMode'. Expected 'none', 'warn', or 'fail'.")
        }
    }

    private fun toStormConfig(properties: StormProperties): StormConfig {
        val map = mutableMapOf<String, String>()
        properties.update.defaultMode?.let {
            map[StormConfig.UPDATE_DEFAULT_MODE] = it.trim().uppercase()
        }
        properties.update.dirtyCheck?.let {
            map[StormConfig.UPDATE_DIRTY_CHECK] = it.trim().uppercase()
        }
        properties.update.maxShapes?.let {
            map[StormConfig.UPDATE_MAX_SHAPES] = it.toString()
        }
        properties.entityCache.retention?.let {
            map[StormConfig.ENTITY_CACHE_RETENTION] = it.trim()
        }
        properties.templateCache.size?.let {
            map[StormConfig.TEMPLATE_CACHE_SIZE] = it.toString()
        }
        properties.ansiEscaping?.let {
            map[StormConfig.ANSI_ESCAPING] = it.toString()
        }
        properties.validation.recordMode?.let {
            map[StormConfig.VALIDATION_RECORD_MODE] = it.trim()
        }
        properties.validation.strict?.let {
            map[StormConfig.VALIDATION_STRICT] = it.toString()
        }
        return StormConfig.of(map)
    }
}
