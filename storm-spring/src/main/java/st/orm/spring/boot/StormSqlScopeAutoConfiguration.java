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

import static st.orm.core.template.SqlScope.HydrationShapes.FULL;
import static st.orm.core.template.SqlScope.HydrationShapes.OFF;
import static st.orm.core.template.SqlScope.HydrationShapes.SHORT;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that reports what each request cost the database.
 *
 * <p>Opt in with {@code storm.sql-scope.enabled=true}. The request is the boundary, so nothing has to be
 * annotated and no bean is proxied; see {@link StormSqlScopeFilter} for what the summary contains. For a
 * narrower boundary, such as one service method, open a scope directly with
 * {@link st.orm.template.SqlScope}.</p>
 *
 * @since 1.13
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({Filter.class, StormSqlScopeFilter.class})
@ConditionalOnProperty(name = "storm.sql-scope.enabled", havingValue = "true")
@EnableConfigurationProperties(StormProperties.class)
public class StormSqlScopeAutoConfiguration {

    /**
     * Provides the filter that wraps each request in a SQL scope.
     */
    @Bean
    @ConditionalOnMissingBean(StormSqlScopeFilter.class)
    public StormSqlScopeFilter stormSqlScopeFilter(StormProperties properties) {
        var sqlScope = properties.getSqlScope();
        if (!sqlScope.getCallSiteSkip().isEmpty()) {
            st.orm.core.template.SqlScope.ignoreCallSites(sqlScope.getCallSiteSkip().toArray(String[]::new));
        }
        if (sqlScope.getLineWidth() != null) {
            st.orm.core.template.SqlScope.lineWidth(sqlScope.getLineWidth());
        }
        st.orm.core.template.SqlScope.hydrationShapes(switch (sqlScope.getHydration()) {
            case OFF -> OFF;
            case SHORT -> SHORT;
            case FULL -> FULL;
        });
        return new StormSqlScopeFilter(sqlScope.getLimit(),
                sqlScope.isCallSites(),
                sqlScope.getThreshold().getStatements(),
                sqlScope.getThreshold().getDuration());
    }
}
