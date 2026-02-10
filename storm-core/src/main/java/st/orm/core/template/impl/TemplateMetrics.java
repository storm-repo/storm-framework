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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Singleton JMX MBean for SQL template cache metrics.
 *
 * <p>Registered as {@code st.orm:type=TemplateMetrics} in the platform MBean server. All {@code SqlTemplateImpl}
 * instances share this single metrics collector. If JMX registration fails, metrics are still collected in-memory
 * and accessible via {@link #getInstance()}.</p>
 *
 * @since 1.8
 */
public final class TemplateMetrics implements TemplateMetricsMXBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateMetrics.class);

    /**
     * Initialization-on-demand holder for the singleton instance. Uses the same pattern as
     * {@code SqlTemplateImpl.CacheHolder} to avoid class initialization issues.
     */
    private static final class Holder {
        static final TemplateMetrics INSTANCE = new TemplateMetrics();
    }

    /**
     * Returns the singleton metrics instance.
     */
    public static TemplateMetrics getInstance() {
        return Holder.INSTANCE;
    }

    // Request totals.
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong requestNanosTotal = new AtomicLong();
    private final AtomicLong requestNanosMax = new AtomicLong();

    // Hit totals.
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong hitNanosTotal = new AtomicLong();
    private final AtomicLong hitNanosMax = new AtomicLong();

    // Miss totals.
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong missNanosTotal = new AtomicLong();
    private final AtomicLong missNanosMax = new AtomicLong();

    private TemplateMetrics() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("st.orm:type=TemplateMetrics");
            if (!server.isRegistered(name)) {
                server.registerMBean(this, name);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to register TemplateMetrics MBean: {}", e.getMessage());
        }
    }

    /**
     * Start measuring one request. Call {@link Request#hit()} or {@link Request#miss()} once you know,
     * then call {@link Request#close()} in a finally block.
     */
    public Request startRequest() {
        return new Request(this, System.nanoTime());
    }

    private void record(long nanos, Outcome outcome) {
        requests.incrementAndGet();
        requestNanosTotal.addAndGet(nanos);
        requestNanosMax.accumulateAndGet(nanos, Math::max);
        if (outcome == Outcome.HIT) {
            hits.incrementAndGet();
            hitNanosTotal.addAndGet(nanos);
            hitNanosMax.accumulateAndGet(nanos, Math::max);
        } else if (outcome == Outcome.MISS) {
            misses.incrementAndGet();
            missNanosTotal.addAndGet(nanos);
            missNanosMax.accumulateAndGet(nanos, Math::max);
        }
    }

    @Override
    public long getRequests() {
        return requests.get();
    }

    @Override
    public long getHits() {
        return hits.get();
    }

    @Override
    public long getMisses() {
        return misses.get();
    }

    @Override
    public long getHitRatioPercent() {
        long h = hits.get();
        long m = misses.get();
        long total = h + m;
        return total == 0 ? 0 : (h * 100 / total);
    }

    @Override
    public long getAvgRequestMicros() {
        long r = requests.get();
        return r == 0 ? 0 : (requestNanosTotal.get() / r) / 1_000;
    }

    @Override
    public long getMaxRequestMicros() {
        return requestNanosMax.get() / 1_000;
    }

    @Override
    public long getAvgHitMicros() {
        long h = hits.get();
        return h == 0 ? 0 : (hitNanosTotal.get() / h) / 1_000;
    }

    @Override
    public long getMaxHitMicros() {
        return hitNanosMax.get() / 1_000;
    }

    @Override
    public long getAvgMissMicros() {
        long m = misses.get();
        return m == 0 ? 0 : (missNanosTotal.get() / m) / 1_000;
    }

    @Override
    public long getMaxMissMicros() {
        return missNanosMax.get() / 1_000;
    }

    @Override
    public void reset() {
        requests.set(0);
        requestNanosTotal.set(0);
        requestNanosMax.set(0);
        hits.set(0);
        hitNanosTotal.set(0);
        hitNanosMax.set(0);
        misses.set(0);
        missNanosTotal.set(0);
        missNanosMax.set(0);
    }

    private enum Outcome { HIT, MISS }

    public static final class Request implements AutoCloseable {
        private final TemplateMetrics owner;
        private final long startNanos;

        // 0 = unknown, 1 = hit, 2 = miss
        private int outcome = 0;

        private Request(TemplateMetrics owner, long startNanos) {
            this.owner = owner;
            this.startNanos = startNanos;
        }

        public void hit() {
            this.outcome = 1;
        }

        public void miss() {
            this.outcome = 2;
        }

        @Override
        public void close() {
            long nanos = System.nanoTime() - startNanos;
            Outcome o;
            if (outcome == 1) o = Outcome.HIT;
            else if (outcome == 2) o = Outcome.MISS;
            else {
                // If you forget to mark, treat as MISS so it stands out in metrics.
                o = Outcome.MISS;
            }
            owner.record(nanos, o);
        }
    }
}
