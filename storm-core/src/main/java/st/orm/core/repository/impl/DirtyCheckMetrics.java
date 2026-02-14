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

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton JMX MBean for dirty checking metrics.
 *
 * <p>Registered as {@code st.orm:type=DirtyCheckMetrics} in the platform MBean server. All {@link DirtySupport}
 * instances report to this single metrics collector. If JMX registration fails, metrics are still collected in-memory
 * and accessible via {@link #getInstance()}.</p>
 *
 * @since 1.9
 */
public final class DirtyCheckMetrics implements DirtyCheckMetricsMXBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(DirtyCheckMetrics.class);

    private static final class Holder {
        static final DirtyCheckMetrics INSTANCE = new DirtyCheckMetrics();
    }

    /**
     * Returns the singleton metrics instance.
     */
    public static DirtyCheckMetrics getInstance() {
        return Holder.INSTANCE;
    }

    // Entity-level outcome counters.
    private final AtomicLong checks = new AtomicLong();
    private final AtomicLong clean = new AtomicLong();
    private final AtomicLong dirty = new AtomicLong();
    private final AtomicLong identityMatches = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();

    // Update mode breakdown.
    private final AtomicLong entityModeChecks = new AtomicLong();
    private final AtomicLong fieldModeChecks = new AtomicLong();

    // Dirty check strategy breakdown.
    private final AtomicLong instanceStrategyChecks = new AtomicLong();
    private final AtomicLong valueStrategyChecks = new AtomicLong();

    // Field-level counters.
    private final AtomicLong fieldComparisons = new AtomicLong();
    private final AtomicLong fieldClean = new AtomicLong();
    private final AtomicLong fieldDirty = new AtomicLong();

    // Shape counters.
    private final AtomicLong shapes = new AtomicLong();
    private final ConcurrentMap<String, AtomicLong> shapesPerEntity = new ConcurrentHashMap<>();

    // Per-entity configuration.
    record EntityConfig(String updateMode, String dirtyCheck, int maxShapes) {}
    private final ConcurrentMap<String, EntityConfig> entityConfigs = new ConcurrentHashMap<>();

    private DirtyCheckMetrics() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("st.orm:type=DirtyCheckMetrics");
            if (!server.isRegistered(name)) {
                server.registerMBean(this, name);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to register DirtyCheckMetrics MBean: {}", e.getMessage());
        }
    }

    // -- Registration methods --

    /**
     * Registers the effective dirty check configuration for the given entity type.
     *
     * @param entityType the simple class name of the entity type.
     * @param updateMode the effective update mode name.
     * @param dirtyCheck the effective dirty check strategy name.
     * @param maxShapes the configured maximum number of update shapes.
     */
    public void registerEntity(String entityType, String updateMode, String dirtyCheck, int maxShapes) {
        entityConfigs.put(entityType, new EntityConfig(updateMode, dirtyCheck, maxShapes));
    }

    // -- Recording methods --

    public void recordClean() {
        checks.incrementAndGet();
        clean.incrementAndGet();
    }

    public void recordCleanIdentityMatch() {
        checks.incrementAndGet();
        clean.incrementAndGet();
        identityMatches.incrementAndGet();
    }

    public void recordDirty() {
        checks.incrementAndGet();
        dirty.incrementAndGet();
    }

    public void recordDirtyCacheMiss() {
        checks.incrementAndGet();
        dirty.incrementAndGet();
        cacheMisses.incrementAndGet();
    }

    public void recordEntityModeCheck() {
        entityModeChecks.incrementAndGet();
    }

    public void recordFieldModeCheck() {
        fieldModeChecks.incrementAndGet();
    }

    public void recordInstanceStrategyCheck() {
        instanceStrategyChecks.incrementAndGet();
    }

    public void recordValueStrategyCheck() {
        valueStrategyChecks.incrementAndGet();
    }

    public void recordFieldClean() {
        fieldComparisons.incrementAndGet();
        fieldClean.incrementAndGet();
    }

    public void recordFieldDirty() {
        fieldComparisons.incrementAndGet();
        fieldDirty.incrementAndGet();
    }

    public void recordNewShape(String entityType) {
        shapes.incrementAndGet();
        shapesPerEntity.computeIfAbsent(entityType, k -> new AtomicLong()).incrementAndGet();
    }

    // -- MXBean getters --

    @Override
    public long getChecks() {
        return checks.get();
    }

    @Override
    public long getClean() {
        return clean.get();
    }

    @Override
    public long getDirty() {
        return dirty.get();
    }

    @Override
    public long getCleanRatioPercent() {
        long c = clean.get();
        long d = dirty.get();
        long total = c + d;
        return total == 0 ? 0 : (c * 100 / total);
    }

    @Override
    public long getIdentityMatches() {
        return identityMatches.get();
    }

    @Override
    public long getCacheMisses() {
        return cacheMisses.get();
    }

    @Override
    public long getEntityModeChecks() {
        return entityModeChecks.get();
    }

    @Override
    public long getFieldModeChecks() {
        return fieldModeChecks.get();
    }

    @Override
    public long getInstanceStrategyChecks() {
        return instanceStrategyChecks.get();
    }

    @Override
    public long getValueStrategyChecks() {
        return valueStrategyChecks.get();
    }

    @Override
    public long getFieldComparisons() {
        return fieldComparisons.get();
    }

    @Override
    public long getFieldClean() {
        return fieldClean.get();
    }

    @Override
    public long getFieldDirty() {
        return fieldDirty.get();
    }

    @Override
    public long getEntityTypes() {
        return shapesPerEntity.size();
    }

    @Override
    public long getShapes() {
        return shapes.get();
    }

    @Override
    public Map<String, Long> getShapesPerEntity() {
        return shapesPerEntity.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    @Override
    public Map<String, String> getUpdateModePerEntity() {
        return entityConfigs.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().updateMode()));
    }

    @Override
    public Map<String, String> getDirtyCheckPerEntity() {
        return entityConfigs.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().dirtyCheck()));
    }

    @Override
    public Map<String, Integer> getMaxShapesPerEntity() {
        return entityConfigs.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().maxShapes()));
    }

    @Override
    public void reset() {
        checks.set(0);
        clean.set(0);
        dirty.set(0);
        identityMatches.set(0);
        cacheMisses.set(0);
        entityModeChecks.set(0);
        fieldModeChecks.set(0);
        instanceStrategyChecks.set(0);
        valueStrategyChecks.set(0);
        fieldComparisons.set(0);
        fieldClean.set(0);
        fieldDirty.set(0);
        shapes.set(0);
        shapesPerEntity.clear();
    }
}
