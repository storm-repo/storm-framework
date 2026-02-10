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
import st.orm.StormConfig;
import st.orm.template.ORMTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

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
@ConditionalOnBean(DataSource.class)
@EnableConfigurationProperties(StormProperties.class)
public class StormAutoConfiguration {

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
    public ORMTemplate ormTemplate(DataSource dataSource, StormProperties properties) {
        return ORMTemplate.of(dataSource, toStormConfig(properties));
    }

    private static StormConfig toStormConfig(StormProperties properties) {
        Map<String, String> map = new HashMap<>();
        var update = properties.getUpdate();
        if (update.getDefaultMode() != null) {
            map.put("storm.update.default_mode", update.getDefaultMode().trim().toUpperCase());
        }
        if (update.getDirtyCheck() != null) {
            map.put("storm.update.dirty_check", update.getDirtyCheck().trim().toUpperCase());
        }
        if (update.getMaxShapes() != null) {
            map.put("storm.update.max_shapes", update.getMaxShapes().toString());
        }
        var entityCache = properties.getEntityCache();
        if (entityCache.getRetention() != null) {
            map.put("storm.entity_cache.retention", entityCache.getRetention().trim());
        }
        var templateCache = properties.getTemplateCache();
        if (templateCache.getSize() != null) {
            map.put("storm.template_cache.size", templateCache.getSize().toString());
        }
        if (properties.getAnsiEscaping() != null) {
            map.put("storm.ansi_escaping", properties.getAnsiEscaping().toString());
        }
        var validation = properties.getValidation();
        if (validation.getSkip() != null) {
            map.put("storm.validation.skip", validation.getSkip().toString());
        }
        if (validation.getWarningsOnly() != null) {
            map.put("storm.validation.warnings_only", validation.getWarningsOnly().toString());
        }
        return StormConfig.of(map);
    }
}
