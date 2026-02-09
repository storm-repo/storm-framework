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
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import st.orm.template.ORMTemplate;

import javax.sql.DataSource;

/**
 * Auto-configuration for the Storm ORM framework.
 *
 * <p>Creates an {@link ORMTemplate} bean from the available {@link DataSource} if no {@code ORMTemplate} bean has been
 * defined by the user.</p>
 */
@AutoConfiguration
@ConditionalOnClass(ORMTemplate.class)
@ConditionalOnBean(DataSource.class)
@EnableConfigurationProperties(StormProperties.class)
public class StormAutoConfiguration {

    /**
     * Creates an {@link ORMTemplate} bean using the provided {@link DataSource}.
     *
     * <p>This bean backs off if the user has already defined their own {@code ORMTemplate} bean.</p>
     *
     * @param dataSource the data source to use for database operations.
     * @return a new {@link ORMTemplate} instance.
     */
    @Bean
    @ConditionalOnMissingBean(ORMTemplate.class)
    public ORMTemplate ormTemplate(DataSource dataSource) {
        return ORMTemplate.of(dataSource);
    }
}
