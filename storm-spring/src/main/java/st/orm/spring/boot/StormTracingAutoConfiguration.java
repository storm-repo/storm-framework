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

import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import st.orm.PersistenceException;
import st.orm.micrometer.TraceContextSqlCommenter;
import st.orm.spi.SqlCommenter;

/**
 * Auto-configuration that appends the current trace context to SQL statements as a sqlcommenter-style
 * comment ({@code /*traceparent='00-…'*&#47;}), correlating database-side diagnostics such as slow query
 * logs with traces. Shared by the Java and Kotlin Spring Boot starters.
 *
 * <p>Opt-in via {@code storm.tracing.sql-comments=true}: a per-execution comment changes the statement text
 * on every call, which defeats driver-side and server-side prepared statement caching. The commenter is
 * handed to the {@code ORMTemplate} created by the starter's auto-configuration; define your own
 * {@link SqlCommenter} bean to append different content.</p>
 *
 * @since 1.13
 */
@AutoConfiguration
@ConditionalOnClass({Tracer.class, TraceContextSqlCommenter.class})
@ConditionalOnBean(Tracer.class)
@ConditionalOnProperty(name = "storm.tracing.sql-comments")
@EnableConfigurationProperties(StormProperties.class)
public class StormTracingAutoConfiguration {

    /**
     * Provides the SQL commenter that appends the current trace context to statements: every statement
     * inside a span with {@code storm.tracing.sql-comments=true}, or only statements of sampled traces
     * with {@code sampled}.
     */
    @Bean
    @ConditionalOnMissingBean(SqlCommenter.class)
    public SqlCommenter stormSqlCommenter(Tracer tracer, StormProperties properties) {
        String mode = properties.getTracing().getSqlComments();
        if ("true".equalsIgnoreCase(mode)) {
            return new TraceContextSqlCommenter(tracer);
        }
        if ("sampled".equalsIgnoreCase(mode)) {
            return new TraceContextSqlCommenter(tracer, true);
        }
        throw new PersistenceException(
                "Unknown storm.tracing.sql-comments value: '%s'. Expected 'true', 'sampled' or 'false'."
                        .formatted(mode));
    }
}
