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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import st.orm.EntityCallback;
import st.orm.StormConfig;
import st.orm.core.spi.ConnectionProvider;
import st.orm.core.spi.ExceptionMapper;
import st.orm.core.spi.QueryObserver;
import st.orm.core.spi.TransactionTemplateProvider;
import st.orm.core.template.impl.SchemaValidator;
import st.orm.spring.SpringConnectionProvider;
import st.orm.template.ORMTemplate;

/**
 * Auto-configuration for the Storm ORM framework.
 *
 * <p>Creates an {@link ORMTemplate} bean from the available {@link DataSource} if no {@code ORMTemplate} bean has been
 * defined by the user. A {@link StormConfig} is built from the bound {@link StormProperties} and passed to the
 * {@code ORMTemplate} factory.</p>
 *
 * @see StormConfig
 */
@AutoConfiguration
@ConditionalOnClass(ORMTemplate.class)
@ConditionalOnBean(DataSource.class)
@EnableConfigurationProperties(StormProperties.class)
public class StormAutoConfiguration {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(StormAutoConfiguration.class);

    /**
     * Creates an {@link ORMTemplate} bean using the provided {@link DataSource} and {@link StormProperties}.
     *
     * <p>A {@link StormConfig} is built from the bound properties. Fields not explicitly configured in
     * {@code application.yml} fall back to system properties and then to built-in defaults.</p>
     *
     * <p>This bean backs off if the user has already defined their own {@code ORMTemplate} bean.</p>
     *
     * @param dataSource the data source to use for database operations.
     * @param properties the Storm configuration properties bound from {@code storm.*}.
     * @return a new {@link ORMTemplate} instance.
     */
    @Bean
    @ConditionalOnMissingBean(ORMTemplate.class)
    public ORMTemplate ormTemplate(DataSource dataSource, StormProperties properties,
                                   List<EntityCallback<?>> entityCallbacks,
                                   ObjectProvider<ConnectionProvider> connectionProvider,
                                   ObjectProvider<TransactionTemplateProvider> transactionTemplateProvider,
                                   ObjectProvider<ExceptionMapper> exceptionMapper,
                                   ObjectProvider<QueryObserver> queryObserver) {
        var builder = ORMTemplate.builder(dataSource).config(toStormConfig(properties));
        connectionProvider.ifAvailable(builder::connectionProvider);
        transactionTemplateProvider.ifAvailable(builder::transactionTemplateProvider);
        exceptionMapper.ifAvailable(builder::exceptionMapper);
        queryObserver.ifAvailable(builder::queryObserver);
        return builder.build().withEntityCallbacks(entityCallbacks);
    }

    /**
     * Provides the connection provider that binds connections to Spring's transaction management.
     *
     * <p>Connections are acquired through Spring's {@code DataSourceUtils}, so statements executed by the template
     * participate in Spring-managed ({@code @Transactional}) transactions and degrade gracefully to plain
     * connections when no transaction is active. Define your own {@link ConnectionProvider} bean to override.</p>
     */
    @Bean
    @ConditionalOnMissingBean(ConnectionProvider.class)
    public ConnectionProvider stormConnectionProvider() {
        return new SpringConnectionProvider();
    }

    /**
     * Runs schema validation after all singleton beans have been fully initialized. This guarantees that migration
     * tools like Flyway and Liquibase (or any bean-based migration mechanism) have completed their work before
     * validation occurs.
     *
     * <p>Validation defaults to {@code fail}: every entity and projection is validated against the live database
     * schema and mismatches abort startup. Set {@code storm.validation.schema_mode} to {@code warn} or {@code none}
     * to relax or opt out.</p>
     */
    @Bean
    SmartInitializingSingleton stormSchemaValidator(DataSource dataSource, StormProperties properties) {
        return () -> {
            String configured = properties.getValidation().getSchemaMode();
            String schemaMode = configured == null || configured.isBlank() ? "fail" : configured.trim();
            if ("none".equalsIgnoreCase(schemaMode)) {
                return;
            }
            SchemaValidator validator = SchemaValidator.of(dataSource);
            if ("fail".equalsIgnoreCase(schemaMode)) {
                validator.validateOrThrow();
                logger.info("Storm schema validation passed (mode=fail).");
            } else if ("warn".equalsIgnoreCase(schemaMode)) {
                validator.validateOrWarn();
            } else {
                logger.warn("Unknown schema validation mode: '{}'. Expected 'none', 'warn', or 'fail'.", schemaMode);
            }
        };
    }

    private static StormConfig toStormConfig(StormProperties properties) {
        Map<String, String> map = new HashMap<>();
        var update = properties.getUpdate();
        if (update.getDefaultMode() != null) {
            map.put(StormConfig.UPDATE_DEFAULT_MODE, update.getDefaultMode().trim().toUpperCase());
        }
        if (update.getDirtyCheck() != null) {
            map.put(StormConfig.UPDATE_DIRTY_CHECK, update.getDirtyCheck().trim().toUpperCase());
        }
        if (update.getMaxShapes() != null) {
            map.put(StormConfig.UPDATE_MAX_SHAPES, update.getMaxShapes().toString());
        }
        var entityCache = properties.getEntityCache();
        if (entityCache.getRetention() != null) {
            map.put(StormConfig.ENTITY_CACHE_RETENTION, entityCache.getRetention().trim());
        }
        var templateCache = properties.getTemplateCache();
        if (templateCache.getSize() != null) {
            map.put(StormConfig.TEMPLATE_CACHE_SIZE, templateCache.getSize().toString());
        }
        if (properties.getAnsiEscaping() != null) {
            map.put(StormConfig.ANSI_ESCAPING, properties.getAnsiEscaping().toString());
        }
        var validation = properties.getValidation();
        if (validation.getRecordMode() != null) {
            map.put(StormConfig.VALIDATION_RECORD_MODE, validation.getRecordMode().trim());
        }
        if (validation.getStrict() != null) {
            map.put(StormConfig.VALIDATION_STRICT, validation.getStrict().toString());
        }
        return StormConfig.of(map);
    }
}
