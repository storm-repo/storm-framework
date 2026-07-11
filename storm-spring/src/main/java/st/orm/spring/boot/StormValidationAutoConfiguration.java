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

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import st.orm.core.template.impl.SchemaValidator;

/**
 * Auto-configuration that validates the entity definitions against the live database schema at startup.
 * Shared by the Java and Kotlin Spring Boot starters.
 *
 * <p>Validation runs after all singleton beans have been fully initialized, guaranteeing that migration tools
 * like Flyway and Liquibase (or any bean-based migration mechanism) have completed their work before
 * validation occurs. The mode is read from {@code storm.validation.schema-mode}: {@code fail} (default),
 * {@code warn}, or {@code none}.</p>
 *
 * @since 1.13
 */
@AutoConfiguration
@ConditionalOnSingleCandidate(DataSource.class)
@EnableConfigurationProperties(StormProperties.class)
public class StormValidationAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(StormValidationAutoConfiguration.class);

    /**
     * Runs schema validation after all singleton beans have been fully initialized.
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
                LOGGER.info("Storm schema validation passed (mode=fail).");
            } else if ("warn".equalsIgnoreCase(schemaMode)) {
                validator.validateOrWarn();
            } else {
                LOGGER.warn("Unknown schema validation mode: '{}'. Expected 'none', 'warn', or 'fail'.", schemaMode);
            }
        };
    }
}
