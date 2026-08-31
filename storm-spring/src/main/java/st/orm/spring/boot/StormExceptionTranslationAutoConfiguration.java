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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;
import st.orm.spi.ExceptionMapper;
import st.orm.spring.SpringExceptionMapper;

/**
 * Auto-configuration that translates SQL failures raised by Storm to Spring's {@code DataAccessException}
 * hierarchy. Shared by the Java and Kotlin Spring Boot starters.
 *
 * <p>The exception mapper is handed to the {@code ORMTemplate} created by the starter's auto-configuration.
 * Disable with {@code storm.exception-translation.enabled=false}, or define your own {@link ExceptionMapper}
 * bean to translate differently.</p>
 *
 * @since 1.13
 */
@AutoConfiguration
@ConditionalOnSingleCandidate(DataSource.class)
@ConditionalOnProperty(name = "storm.exception-translation.enabled", havingValue = "true", matchIfMissing = true)
public class StormExceptionTranslationAutoConfiguration {

    /**
     * Provides the exception mapper that translates SQL failures to Spring's {@code DataAccessException}
     * hierarchy, using vendor error codes for the database product of the application's data source.
     */
    @Bean
    @ConditionalOnMissingBean(ExceptionMapper.class)
    public ExceptionMapper stormExceptionMapper(DataSource dataSource) {
        return new SpringExceptionMapper(dataSource);
    }
}
