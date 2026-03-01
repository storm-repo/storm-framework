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
package st.orm.core.repository.impl;

import java.util.Map;

/**
 * JMX MXBean interface for dirty checking metrics.
 *
 * <p>Register under {@code st.orm:type=DirtyCheckMetrics}. Exposes read-only counters and a {@link #reset()}
 * operation to clear all accumulated data.</p>
 *
 * <p>These metrics aggregate across all dirty checks performed by entity repositories.</p>
 *
 * @since 1.9
 */
public interface DirtyCheckMetricsMXBean {

    // -- Entity-level outcome counters --

    /** Total number of dirty checks performed. */
    long getChecks();

    /** Number of checks that found the entity clean (update skipped). */
    long getClean();

    /** Number of checks that found the entity dirty (update triggered). */
    long getDirty();

    /** Clean ratio as a percentage (0-100), indicating how often updates are avoided. */
    long getCleanRatioPercent();

    // -- Resolution path counters --

    /**
     * Number of checks resolved by identity comparison ({@code cached == entity}), the cheapest path.
     */
    long getIdentityMatches();

    /**
     * Number of checks where no cached baseline was available (entity not in cache or cache unavailable), causing a
     * fallback to full-entity update.
     */
    long getCacheMisses();

    // -- Update mode breakdown --

    /** Number of checks that used {@code ENTITY} update mode (full-row UPDATE on any change). */
    long getEntityModeChecks();

    /** Number of checks that used {@code FIELD} update mode (column-level UPDATE). */
    long getFieldModeChecks();

    // -- Dirty check strategy breakdown --

    /** Number of checks that used {@code INSTANCE} strategy (identity-based field comparison). */
    long getInstanceStrategyChecks();

    /** Number of checks that used {@code VALUE} strategy (equality-based field comparison). */
    long getValueStrategyChecks();

    // -- Field-level counters --

    /** Total number of individual field comparisons performed across all dirty checks. */
    long getFieldComparisons();

    /** Number of individual field comparisons where the field was found equal (clean). */
    long getFieldClean();

    /** Number of individual field comparisons where the field was found different (dirty). */
    long getFieldDirty();

    // -- Shape counters --

    /** Number of distinct entity types that have generated UPDATE shapes. */
    long getEntityTypes();

    /** Total number of distinct UPDATE statement shapes created across all entity types. */
    long getShapes();

    /**
     * Returns the number of distinct UPDATE shapes per entity type.
     *
     * <p>Each key is the simple class name of the entity type, and the value is the number of distinct column
     * combinations (shapes) that have been generated for that type. Compare with the configured
     * {@code storm.update.max_shapes} to gauge overflow pressure per entity type.</p>
     */
    Map<String, Long> getShapesPerEntity();

    // -- Per-entity configuration --

    /**
     * Returns the effective {@link st.orm.UpdateMode} per entity type.
     *
     * <p>Each key is the simple class name of the entity type, and the value is the update mode name
     * (e.g., {@code "FIELD"}, {@code "ENTITY"}).</p>
     */
    Map<String, String> getUpdateModePerEntity();

    /**
     * Returns the effective {@link st.orm.DirtyCheck} strategy per entity type.
     *
     * <p>Each key is the simple class name of the entity type, and the value is the dirty check strategy name
     * (e.g., {@code "INSTANCE"}, {@code "VALUE"}).</p>
     */
    Map<String, String> getDirtyCheckPerEntity();

    /**
     * Returns the configured maximum number of update shapes per entity type.
     *
     * <p>Each key is the simple class name of the entity type, and the value is the max shapes limit.</p>
     */
    Map<String, Integer> getMaxShapesPerEntity();

    /** Resets all counters to zero. */
    void reset();
}
