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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * An {@link EnvironmentPostProcessor} that bridges Spring {@code storm.*} properties to JVM system properties before
 * Storm's static initializers run.
 *
 * <p>This post-processor runs at {@link Ordered#HIGHEST_PRECEDENCE} + 10 to ensure it executes before any Storm class
 * is loaded. Existing JVM {@code -D} flags are never overwritten, so explicit flags always take precedence over Spring
 * property values.</p>
 *
 * <p>Registered via {@code META-INF/spring.factories}.</p>
 *
 * @see StormProperties
 */
public class StormEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * Mapping from Spring relaxed property keys to the exact JVM system property keys expected by Storm.
     */
    private static final String[][] PROPERTY_MAPPINGS = {
            { "storm.update.default-mode",     "storm.update.defaultMode" },
            { "storm.update.dirty-check",      "storm.update.dirtyCheck" },
            { "storm.update.max-shapes",       "storm.update.maxShapes" },
            { "storm.entity-cache.retention",  "storm.entityCache.retention" },
            { "storm.template-cache.size",     "storm.templateCache.size" },
            { "storm.metrics.level",           "storm.metrics.level" },
            { "storm.metrics.initial-log-at",  "storm.metrics.initialLogAt" },
            { "storm.metrics.max-log-gap",     "storm.metrics.maxLogGap" },
            { "storm.ansi-escaping",           "storm.ansiEscaping" },
            { "storm.validation.skip",         "storm.validation.skip" },
            { "storm.validation.warnings-only","storm.validation.warningsOnly" },
    };

    /**
     * Reads Storm-related properties from the Spring {@link ConfigurableEnvironment} and sets them as JVM system
     * properties if not already present.
     *
     * @param environment the Spring environment containing resolved properties.
     * @param application the Spring application (may be {@code null} in tests).
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        for (String[] mapping : PROPERTY_MAPPINGS) {
            String springKey = mapping[0];
            String systemKey = mapping[1];
            if (System.getProperty(systemKey) != null) {
                // Explicit JVM flag takes precedence.
                continue;
            }
            String value = environment.getProperty(springKey);
            if (value != null) {
                System.setProperty(systemKey, value);
            }
        }
    }

    /** Returns the order of this post-processor ({@link Ordered#HIGHEST_PRECEDENCE} + 10). */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
