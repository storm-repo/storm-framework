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

import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import st.orm.micrometer.MicrometerQueryObserver;
import st.orm.micrometer.StormQueryObservationContext;
import st.orm.micrometer.StormTransactionObservationContext;
import st.orm.spi.QueryObserver;

/**
 * Auto-configuration that reports Storm query executions as Micrometer Observations when an
 * {@link ObservationRegistry} is available. Shared by the Java and Kotlin Spring Boot starters.
 *
 * <p>Each query executed by the auto-configured {@code ORMTemplate} is observed under the name
 * {@code storm.query}, with low-cardinality key values for the operation, execution kind and data type,
 * and the SQL statement as a high-cardinality value for trace handlers; parameter values are never
 * reported. Physical transactions are observed under the name {@code storm.transaction}. Contribute an
 * {@link ObservationConvention} bean for {@link StormQueryObservationContext} or
 * {@link StormTransactionObservationContext} to override the naming and key values, define your own
 * {@link QueryObserver} bean to replace the binding, or disable the observations at the registry level with
 * {@code management.observations.enable.storm.query=false} and
 * {@code management.observations.enable.storm.transaction=false}.</p>
 *
 * <p>The ordering hints reference the observation auto-configuration by name for both its Spring Boot 3
 * and Spring Boot 4 locations; name-based hints are ignored when the class is not on the classpath.</p>
 *
 * @since 1.13
 */
@AutoConfiguration(
        afterName = {
                // Spring Boot 3.x location.
                "org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration",
                // Spring Boot 4.x location.
                "org.springframework.boot.observation.autoconfigure.ObservationAutoConfiguration",
        })
@ConditionalOnClass({ObservationRegistry.class, MicrometerQueryObserver.class})
@ConditionalOnBean(ObservationRegistry.class)
@EnableConfigurationProperties(StormProperties.class)
public class StormObservationAutoConfiguration {

    /**
     * Provides the query observer that reports query executions and transactions to the application's
     * observation registry.
     *
     * <p>A custom {@link ObservationConvention} bean for {@link StormQueryObservationContext} or
     * {@link StormTransactionObservationContext}, when present, overrides the default naming and key values of
     * the query and transaction observations respectively.</p>
     */
    @Bean
    @ConditionalOnMissingBean(QueryObserver.class)
    public QueryObserver stormQueryObserver(
            ObservationRegistry observationRegistry,
            ObjectProvider<ObservationConvention<StormQueryObservationContext>> convention,
            ObjectProvider<ObservationConvention<StormTransactionObservationContext>> transactionConvention,
            StormProperties properties,
            ObjectProvider<DataSource> dataSource) {
        return StormQueryObservers.create(
                observationRegistry,
                properties,
                dataSource.getIfUnique(),
                null,
                convention.getIfAvailable(),
                transactionConvention.getIfAvailable());
    }
}
