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

import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import st.orm.EntityCallback;
import st.orm.StormConfig;
import st.orm.core.spi.ConnectionProvider;
import st.orm.core.spi.ExceptionMapper;
import st.orm.core.spi.QueryObserver;
import st.orm.core.spi.SqlCommenter;
import st.orm.core.spi.TransactionTemplateProvider;
import st.orm.micrometer.MicrometerQueryObserver;
import st.orm.micrometer.StormQueryObservationContext;
import st.orm.micrometer.StormTransactionObservationContext;
import st.orm.spring.SpringExceptionMapper;
import st.orm.spring.boot.StormProperties;
import st.orm.spring.boot.StormQueryObservers;
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
    @ConditionalOnSingleCandidate(DataSource.class)
    public ORMTemplate ormTemplate(DataSource dataSource, StormProperties properties,
                                   List<EntityCallback<?>> entityCallbacks,
                                   ObjectProvider<ConnectionProvider> connectionProvider,
                                   ObjectProvider<TransactionTemplateProvider> transactionTemplateProvider,
                                   ObjectProvider<ExceptionMapper> exceptionMapper,
                                   ObjectProvider<QueryObserver> queryObserver,
                                   ObjectProvider<SqlCommenter> sqlCommenter) {
        var builder = ORMTemplate.builder(dataSource).config(properties.toStormConfig());
        connectionProvider.ifAvailable(builder::connectionProvider);
        transactionTemplateProvider.ifAvailable(builder::transactionTemplateProvider);
        exceptionMapper.ifAvailable(builder::exceptionMapper);
        queryObserver.ifAvailable(builder::queryObserver);
        sqlCommenter.ifAvailable(builder::sqlCommenter);
        return builder.build().withEntityCallbacks(entityCallbacks);
    }

    /**
     * Creates the {@link OrmTemplateFactory} that composes fully integrated templates for the applications that
     * define their own template beans, where the single auto-configured template does not apply: several data
     * sources, or a single one whose composition the application customizes.
     *
     * <p>The factory consumes the same integration beans the auto-configured template does. SQL failure
     * translation follows {@code storm.exception-translation.enabled}, with the mapper created per data source
     * when no {@link ExceptionMapper} bean is defined, so every template translates against the database it runs
     * on. Observations are composed per data source through {@link StormQueryObservers} when an
     * {@link ObservationRegistry} is available; a {@link QueryObserver} bean is not consumed here, since it
     * carries no database identity. A template that needs a custom observer sets it in the factory's customize
     * consumer.</p>
     *
     * @param properties the Storm configuration properties bound from {@code storm.*}.
     * @param entityCallbacks the entity callbacks applied to every created template.
     * @param connectionProvider the Spring-aware connection provider, when transaction integration is active.
     * @param transactionTemplateProvider the Spring-aware transaction template provider.
     * @param exceptionMapper user-defined exception mapper overriding the per-data-source default.
     * @param sqlCommenter the SQL commenter contributed by the tracing auto-configuration.
     * @param observationSupport the observation composition, present when Micrometer observations are active.
     * @return the template factory.
     */
    @Bean
    @ConditionalOnMissingBean(OrmTemplateFactory.class)
    public OrmTemplateFactory ormTemplateFactory(StormProperties properties,
                                                 List<EntityCallback<?>> entityCallbacks,
                                                 ObjectProvider<ConnectionProvider> connectionProvider,
                                                 ObjectProvider<TransactionTemplateProvider> transactionTemplateProvider,
                                                 ObjectProvider<ExceptionMapper> exceptionMapper,
                                                 ObjectProvider<SqlCommenter> sqlCommenter,
                                                 ObjectProvider<OrmTemplateObservationSupport> observationSupport) {
        return (dataSource, database, customize) -> {
            var builder = ORMTemplate.builder(dataSource).config(properties.toStormConfig());
            connectionProvider.ifAvailable(builder::connectionProvider);
            transactionTemplateProvider.ifAvailable(builder::transactionTemplateProvider);
            var mapper = exceptionMapper.getIfAvailable();
            if (mapper == null && !Boolean.FALSE.equals(properties.getExceptionTranslation().getEnabled())) {
                mapper = new SpringExceptionMapper(dataSource);
            }
            if (mapper != null) {
                builder.exceptionMapper(mapper);
            }
            observationSupport.ifAvailable(support ->
                    builder.queryObserver(support.observerFor(dataSource, database)));
            sqlCommenter.ifAvailable(builder::sqlCommenter);
            customize.accept(builder);
            return builder.build().withEntityCallbacks(entityCallbacks);
        };
    }

    /**
     * Observation composition for factory-created templates, present when Micrometer observations are on the
     * class path and an {@link ObservationRegistry} bean is available. Each observer resolves the semantic
     * conventions against its own data source and carries the template's database name, so the observation
     * identity follows the template rather than the application.
     */
    public static final class OrmTemplateObservationSupport {

        private final ObservationRegistry observationRegistry;
        private final StormProperties properties;
        private final @Nullable ObservationConvention<StormQueryObservationContext> queryConvention;
        private final @Nullable ObservationConvention<StormTransactionObservationContext> transactionConvention;

        OrmTemplateObservationSupport(
                ObservationRegistry observationRegistry,
                StormProperties properties,
                @Nullable ObservationConvention<StormQueryObservationContext> queryConvention,
                @Nullable ObservationConvention<StormTransactionObservationContext> transactionConvention) {
            this.observationRegistry = observationRegistry;
            this.properties = properties;
            this.queryConvention = queryConvention;
            this.transactionConvention = transactionConvention;
        }

        /** Composes the observer for a template on the data source, reporting the name as {@code storm.database}. */
        public QueryObserver observerFor(DataSource dataSource, @Nullable String database) {
            return StormQueryObservers.create(
                    observationRegistry, properties, dataSource, database, queryConvention, transactionConvention);
        }
    }

    /** Contributes the factory's observation composition when Micrometer observations are active. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({ObservationRegistry.class, MicrometerQueryObserver.class})
    @ConditionalOnBean(ObservationRegistry.class)
    public static class ObservationSupportConfiguration {

        @Bean
        @ConditionalOnMissingBean(OrmTemplateObservationSupport.class)
        public OrmTemplateObservationSupport ormTemplateObservationSupport(
                ObservationRegistry observationRegistry,
                StormProperties properties,
                ObjectProvider<ObservationConvention<StormQueryObservationContext>> queryConvention,
                ObjectProvider<ObservationConvention<StormTransactionObservationContext>> transactionConvention) {
            return new OrmTemplateObservationSupport(
                    observationRegistry,
                    properties,
                    queryConvention.getIfAvailable(),
                    transactionConvention.getIfAvailable());
        }
    }
}
