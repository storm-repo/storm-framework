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

import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import st.orm.EntityCallback;
import st.orm.StormConfig;
import st.orm.core.spi.ConnectionProvider;
import st.orm.core.spi.ExceptionMapper;
import st.orm.core.spi.QueryObserver;
import st.orm.core.spi.TransactionTemplateProvider;
import st.orm.spring.boot.StormProperties;
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
@ConditionalOnSingleCandidate(DataSource.class)
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
        var builder = ORMTemplate.builder(dataSource).config(properties.toStormConfig());
        connectionProvider.ifAvailable(builder::connectionProvider);
        transactionTemplateProvider.ifAvailable(builder::transactionTemplateProvider);
        exceptionMapper.ifAvailable(builder::exceptionMapper);
        queryObserver.ifAvailable(builder::queryObserver);
        return builder.build().withEntityCallbacks(entityCallbacks);
    }

}
