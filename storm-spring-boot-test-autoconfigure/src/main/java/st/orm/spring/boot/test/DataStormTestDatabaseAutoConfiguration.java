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
package st.orm.spring.boot.test;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.NoneNestedConditions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * Fallback embedded test database for {@link DataStormTest @DataStormTest} slices on Spring Boot 4.
 *
 * <p>Spring Boot 3 replaces the application's {@code DataSource} with an embedded database through its own
 * {@code TestDatabaseAutoConfiguration}, which the slice imports. Spring Boot 4 hosts that behavior in the
 * separate {@code spring-boot-jdbc-test-autoconfigure} artifact; when it is absent, this fallback provides
 * an embedded H2 database so the slice behaves identically on both generations.</p>
 *
 * <p>The fallback stays inert whenever Boot's own replacement is present (either location on the classpath),
 * when H2 is not on the test classpath, or when {@code spring.test.database.replace=none} requests the
 * configured database, such as a Testcontainers-managed one.</p>
 *
 * @since 1.13
 */
@AutoConfiguration(
        beforeName = {
                // Spring Boot 3.x location.
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                // Spring Boot 4.x location.
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        })
@ConditionalOnMissingClass({
        // Boot's own replacement wins by presence; both its locations checked.
        "org.springframework.boot.test.autoconfigure.jdbc.TestDatabaseAutoConfiguration",
        "org.springframework.boot.jdbc.test.autoconfigure.TestDatabaseAutoConfiguration",
})
@ConditionalOnClass(name = "org.h2.Driver")
@Conditional(DataStormTestDatabaseAutoConfiguration.ReplacementRequested.class)
public class DataStormTestDatabaseAutoConfiguration {

    /**
     * Provides the embedded test database, taking precedence over the application's configured data source
     * through the DataSource auto-configuration's own back-off.
     */
    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource dataStormTestDatabase() {
        return new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build();
    }

    /**
     * Matches unless {@code spring.test.database.replace=none} requests the configured database.
     */
    static class ReplacementRequested extends NoneNestedConditions {

        ReplacementRequested() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @ConditionalOnProperty(name = "spring.test.database.replace", havingValue = "none")
        static class ReplaceNone {
        }
    }
}
