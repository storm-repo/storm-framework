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
package st.orm.spring.boot.autoconfigure

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment

/**
 * An [EnvironmentPostProcessor] that bridges Spring `storm.*` properties to JVM system properties before Storm's
 * static initializers run.
 *
 * This post-processor runs at [Ordered.HIGHEST_PRECEDENCE] + 10 to ensure it executes before any Storm class is
 * loaded. Existing JVM `-D` flags are never overwritten, so explicit flags always take precedence over Spring
 * property values.
 *
 * Registered via `META-INF/spring.factories`.
 *
 * @see StormProperties
 */
class StormEnvironmentPostProcessor : EnvironmentPostProcessor, Ordered {

    companion object {
        /**
         * Mapping from Spring relaxed property keys to the exact JVM system property keys expected by Storm.
         */
        private val PROPERTY_MAPPINGS = arrayOf(
            arrayOf("storm.update.default-mode",     "storm.update.defaultMode"),
            arrayOf("storm.update.dirty-check",      "storm.update.dirtyCheck"),
            arrayOf("storm.update.max-shapes",       "storm.update.maxShapes"),
            arrayOf("storm.entity-cache.retention",  "storm.entityCache.retention"),
            arrayOf("storm.template-cache.size",     "storm.templateCache.size"),
            arrayOf("storm.metrics.level",           "storm.metrics.level"),
            arrayOf("storm.metrics.initial-log-at",  "storm.metrics.initialLogAt"),
            arrayOf("storm.metrics.max-log-gap",     "storm.metrics.maxLogGap"),
            arrayOf("storm.ansi-escaping",           "storm.ansiEscaping"),
            arrayOf("storm.validation.skip",         "storm.validation.skip"),
            arrayOf("storm.validation.warnings-only","storm.validation.warningsOnly"),
        )
    }

    /**
     * Reads Storm-related properties from the Spring [ConfigurableEnvironment] and sets them as JVM system properties
     * if not already present.
     *
     * @param environment the Spring environment containing resolved properties.
     * @param application the Spring application (may be `null` in tests).
     */
    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication?) {
        for ((springKey, systemKey) in PROPERTY_MAPPINGS) {
            if (System.getProperty(systemKey) != null) {
                // Explicit JVM flag takes precedence.
                continue
            }
            environment.getProperty(springKey)?.let { value ->
                System.setProperty(systemKey, value)
            }
        }
    }

    /** Returns the order of this post-processor ([Ordered.HIGHEST_PRECEDENCE] + 10). */
    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 10
}
