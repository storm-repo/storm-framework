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
package st.orm.core.spi;

import java.util.Map;

/**
 * JMX MXBean interface for entity cache metrics.
 *
 * <p>Register under {@code st.orm:type=EntityCacheMetrics}. Exposes read-only counters and a {@link #reset()}
 * operation to clear all accumulated data.</p>
 *
 * <p>These metrics aggregate across all transaction-scoped entity caches.</p>
 *
 * @since 1.9
 */
public interface EntityCacheMetricsMXBean {

    /** Total number of {@code get()} calls. */
    long getGets();

    /** Number of {@code get()} calls that returned a cached entity. */
    long getGetHits();

    /** Number of {@code get()} calls where no cached entity was available. */
    long getGetMisses();

    /** Get hit ratio as a percentage (0-100). */
    long getGetHitRatioPercent();

    /** Total number of {@code intern()} calls. */
    long getInterns();

    /** Number of {@code intern()} calls that reused an existing canonical instance. */
    long getInternHits();

    /** Number of {@code intern()} calls that stored a new or updated instance (no reusable entry found). */
    long getInternMisses();

    /** Intern hit ratio as a percentage (0-100). */
    long getInternHitRatioPercent();

    /** Number of cache entries removed due to entity mutations (insert, update, delete). */
    long getRemovals();

    /** Number of full cache clears. */
    long getClears();

    /** Number of cache entries cleaned up after garbage collection. */
    long getEvictions();

    // -- Per-entity configuration --

    /**
     * Returns the effective {@link CacheRetention} per entity type.
     *
     * <p>Each key is the simple class name of the entity type, and the value is the retention mode name
     * (e.g., {@code "DEFAULT"}, {@code "LIGHT"}).</p>
     */
    Map<String, String> getRetentionPerEntity();

    /** Resets all counters to zero. */
    void reset();
}
