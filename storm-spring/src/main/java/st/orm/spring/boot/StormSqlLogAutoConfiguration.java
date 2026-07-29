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

import static st.orm.core.template.SqlLog.HydrationShapes.FULL;
import static st.orm.core.template.SqlLog.HydrationShapes.OFF;
import static st.orm.core.template.SqlLog.HydrationShapes.SHORT;

import jakarta.annotation.Nonnull;
import jakarta.servlet.Filter;
import java.util.Set;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Auto-configuration that reports what each unit of work cost the database.
 *
 * <p>Opt in with {@code storm.sql-log.enabled=true}. Every way work enters the application is a boundary: HTTP
 * requests are wrapped by a servlet filter, and scheduled tasks and message listeners by a proxy around the
 * annotated entry-point method, so a worker without a web layer reports the same way a web application does. For
 * a narrower boundary, such as one service method, open a scope directly with
 * {@link st.orm.template.SqlLog}.</p>
 *
 * @since 1.13
 */
@AutoConfiguration
@ConditionalOnProperty(name = "storm.sql-log.enabled", havingValue = "true")
@EnableConfigurationProperties(StormProperties.class)
public class StormSqlLogAutoConfiguration {

    /**
     * Provides the post-processor that wraps non-request entry points, such as {@code @Scheduled} tasks and
     * message listeners, in a SQL log.
     *
     * <p>Registered as a static bean and bound through the {@link Binder}, since a bean post-processor is
     * created before the configuration-properties machinery. The display settings, which apply to every summary
     * whichever boundary produced it, are applied here as the one unconditional spot of this
     * auto-configuration.</p>
     */
    @Bean
    @ConditionalOnMissingBean(StormSqlLogEntryPointPostProcessor.class)
    static StormSqlLogEntryPointPostProcessor stormSqlLogEntryPointPostProcessor(
            @Nonnull Environment environment) {
        var sqlLog = new Binder(
                ConfigurationPropertySources.get(environment),
                new PropertySourcesPlaceholdersResolver(environment),
                ApplicationConversionService.getSharedInstance())
                .bindOrCreate("storm.sql-log", StormProperties.SqlLog.class);
        applyDisplaySettings(sqlLog);
        return new StormSqlLogEntryPointPostProcessor(Set.copyOf(sqlLog.getEntryPoints()),
                sqlLog.getLimit(),
                sqlLog.isCallSites(),
                sqlLog.getThreshold().getStatements(),
                sqlLog.getThreshold().getDuration());
    }

    /** Applies the settings that shape how every summary renders, whichever boundary produced it. */
    private static void applyDisplaySettings(@Nonnull StormProperties.SqlLog sqlLog) {
        if (!sqlLog.getCallSiteSkip().isEmpty()) {
            st.orm.core.template.SqlLog.ignoreCallSites(sqlLog.getCallSiteSkip().toArray(String[]::new));
        }
        if (sqlLog.getLineWidth() != null) {
            st.orm.core.template.SqlLog.lineWidth(sqlLog.getLineWidth());
        }
        st.orm.core.template.SqlLog.hydrationShapes(switch (sqlLog.getHydration()) {
            case OFF -> OFF;
            case SHORT -> SHORT;
            case FULL -> FULL;
        });
    }

    /**
     * Wraps each HTTP request in a SQL log; the request is the boundary a web application already has.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(Filter.class)
    static class WebConfiguration {

        /**
         * Provides the filter that wraps each request in a SQL log.
         */
        @Bean
        @ConditionalOnMissingBean(StormSqlLogFilter.class)
        StormSqlLogFilter stormSqlLogFilter(@Nonnull StormProperties properties) {
            var sqlLog = properties.getSqlLog();
            return new StormSqlLogFilter(sqlLog.getLimit(),
                    sqlLog.isCallSites(),
                    sqlLog.getThreshold().getStatements(),
                    sqlLog.getThreshold().getDuration());
        }
    }
}
