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
package st.orm.core.template.impl;

/**
 * JMX MXBean interface for template cache metrics.
 *
 * <p>Register under {@code st.orm:type=TemplateMetrics}. Exposes read-only counters and a {@link #reset()} operation
 * to clear all accumulated data.</p>
 *
 * @since 1.9
 */
public interface TemplateMetricsMXBean {

    /** Total number of template requests. */
    long getRequests();

    /** Number of cache hits. */
    long getHits();

    /** Number of cache misses. */
    long getMisses();

    /** Hit ratio as a percentage (0-100). */
    long getHitRatioPercent();

    /** Average request duration in microseconds. */
    long getAvgRequestMicros();

    /** Maximum request duration in microseconds. */
    long getMaxRequestMicros();

    /** Average cache hit duration in microseconds. */
    long getAvgHitMicros();

    /** Maximum cache hit duration in microseconds. */
    long getMaxHitMicros();

    /** Average cache miss duration in microseconds. */
    long getAvgMissMicros();

    /** Maximum cache miss duration in microseconds. */
    long getMaxMissMicros();

    // -- Configuration --

    /** Returns the configured template cache size, or the last registered value if multiple instances exist. */
    int getTemplateCacheSize();

    /** Resets all counters to zero. */
    void reset();
}
