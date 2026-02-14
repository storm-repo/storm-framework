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

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton JMX MBean for entity cache metrics.
 *
 * <p>Registered as {@code st.orm:type=EntityCacheMetrics} in the platform MBean server. All transaction-scoped
 * {@link EntityCacheImpl} instances report to this single metrics collector. If JMX registration fails, metrics are
 * still collected in-memory and accessible via {@link #getInstance()}.</p>
 *
 * @since 1.9
 */
public final class EntityCacheMetrics implements EntityCacheMetricsMXBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityCacheMetrics.class);

    private static final class Holder {
        static final EntityCacheMetrics INSTANCE = new EntityCacheMetrics();
    }

    /**
     * Returns the singleton metrics instance.
     */
    public static EntityCacheMetrics getInstance() {
        return Holder.INSTANCE;
    }

    // Get counters.
    private final AtomicLong gets = new AtomicLong();
    private final AtomicLong getHits = new AtomicLong();
    private final AtomicLong getMisses = new AtomicLong();

    // Intern counters.
    private final AtomicLong interns = new AtomicLong();
    private final AtomicLong internHits = new AtomicLong();
    private final AtomicLong internMisses = new AtomicLong();

    // Mutation counters.
    private final AtomicLong removals = new AtomicLong();
    private final AtomicLong clears = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    // Per-entity configuration.
    private final ConcurrentMap<String, String> retentionPerEntity = new ConcurrentHashMap<>();

    private EntityCacheMetrics() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("st.orm:type=EntityCacheMetrics");
            if (!server.isRegistered(name)) {
                server.registerMBean(this, name);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to register EntityCacheMetrics MBean: {}", e.getMessage());
        }
    }

    /**
     * Registers the effective cache retention for the given entity type.
     *
     * @param entityType the simple class name of the entity type.
     * @param retention the effective cache retention mode name.
     */
    public void registerEntity(String entityType, String retention) {
        retentionPerEntity.put(entityType, retention);
    }

    public void recordGetHit() {
        gets.incrementAndGet();
        getHits.incrementAndGet();
    }

    public void recordGetMiss() {
        gets.incrementAndGet();
        getMisses.incrementAndGet();
    }

    public void recordInternHit() {
        interns.incrementAndGet();
        internHits.incrementAndGet();
    }

    public void recordInternMiss() {
        interns.incrementAndGet();
        internMisses.incrementAndGet();
    }

    public void recordRemoval() {
        removals.incrementAndGet();
    }

    public void recordClear() {
        clears.incrementAndGet();
    }

    public void recordEviction() {
        evictions.incrementAndGet();
    }

    @Override
    public long getGets() {
        return gets.get();
    }

    @Override
    public long getGetHits() {
        return getHits.get();
    }

    @Override
    public long getGetMisses() {
        return getMisses.get();
    }

    @Override
    public long getGetHitRatioPercent() {
        long h = getHits.get();
        long m = getMisses.get();
        long total = h + m;
        return total == 0 ? 0 : (h * 100 / total);
    }

    @Override
    public long getInterns() {
        return interns.get();
    }

    @Override
    public long getInternHits() {
        return internHits.get();
    }

    @Override
    public long getInternMisses() {
        return internMisses.get();
    }

    @Override
    public long getInternHitRatioPercent() {
        long h = internHits.get();
        long m = internMisses.get();
        long total = h + m;
        return total == 0 ? 0 : (h * 100 / total);
    }

    @Override
    public long getRemovals() {
        return removals.get();
    }

    @Override
    public long getClears() {
        return clears.get();
    }

    @Override
    public long getEvictions() {
        return evictions.get();
    }

    @Override
    public Map<String, String> getRetentionPerEntity() {
        return Map.copyOf(retentionPerEntity);
    }

    @Override
    public void reset() {
        gets.set(0);
        getHits.set(0);
        getMisses.set(0);
        interns.set(0);
        internHits.set(0);
        internMisses.set(0);
        removals.set(0);
        clears.set(0);
        evictions.set(0);
    }
}
