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
import org.slf4j.event.Level;

import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Long.getLong;
import static java.util.Objects.requireNonNull;
import static org.slf4j.event.Level.valueOf;

/**
 * Metrics for SQL template processing.
 *
 * @since 1.8
 */
public final class TemplateMetrics {
    private static final long DEFAULT_INITIAL_LOG_AT = getLong("storm.metrics.initialLogAt", 64L);
    private static final long DEFAULT_MAX_LOG_GAP = getLong("storm.metrics.maxLogGap", 0L);
    private static final Level DEFAULT_LEVEL = valueOf(System.getProperty("storm.metrics.level", Level.DEBUG.name()));

    private final Logger logger;
    private final Level level;

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

    // Logging schedule.
    private final AtomicLong nextLogAt;
    private final AtomicLong logGap;
    private final long maxLogGap; // <= 0 means "no max"

    public TemplateMetrics(Logger logger) {
        this(logger, DEFAULT_LEVEL, DEFAULT_INITIAL_LOG_AT, DEFAULT_MAX_LOG_GAP, true);
    }

    /**
     * @param logger logger to output to.
     * @param initialLogAt first request count at which to log (e.g. 64).
     * @param logOnShutdown if true, registers a shutdown hook that logs a final snapshot.
     */
    public TemplateMetrics(Logger logger, Level level, long initialLogAt, boolean logOnShutdown) {
        this(logger, level, initialLogAt, 0, logOnShutdown);
    }

    /**
     * @param logger logger to output to.
     * @param level log level.
     * @param initialLogAt first request count at which to log (e.g. 64).
     * @param maxLogGap max delta between log lines (e.g. 4096). Use {@code 0} (or negative) for unlimited.
     * @param logOnShutdown if true, registers a shutdown hook that logs a final snapshot.
     */
    public TemplateMetrics(Logger logger, Level level, long initialLogAt, long maxLogGap, boolean logOnShutdown) {
        this.logger = requireNonNull(logger, "logger");
        this.level = requireNonNull(level, "level");
        if (initialLogAt <= 0) {
            throw new IllegalArgumentException("initialLogAt must be > 0");
        }
        if (maxLogGap > 0 && maxLogGap < initialLogAt) {
            throw new IllegalArgumentException("maxLogGap must be >= initialLogAt (or <= 0 for unlimited)");
        }
        this.maxLogGap = maxLogGap;
        this.nextLogAt = new AtomicLong(initialLogAt);
        this.logGap = new AtomicLong(initialLogAt);
        if (logOnShutdown) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    logStats(requests.get());
                } catch (Throwable ignored) {
                    // Never fail shutdown.
                }
            }, "template-metrics-shutdown"));
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
        maybeLog();
    }

    private void maybeLog() {
        if (!logger.isEnabledForLevel(level)) {
            return;
        }
        long count = requests.get();
        long logAt = nextLogAt.get();
        if (count != logAt) {
            return;
        }
        // Update schedule first (CAS) so only one thread logs.
        long currentGap = logGap.get();
        long nextGap;
        if (maxLogGap > 0) {
            // clamp the doubling
            long doubled = currentGap > (Long.MAX_VALUE >> 1) ? Long.MAX_VALUE : (currentGap << 1);
            nextGap = Math.min(doubled, maxLogGap);
        } else {
            nextGap = currentGap > (Long.MAX_VALUE >> 1) ? Long.MAX_VALUE : (currentGap << 1);
        }
        long nextAt;
        if (logAt > Long.MAX_VALUE - currentGap) {
            nextAt = Long.MAX_VALUE;
        } else {
            nextAt = logAt + currentGap;
        }
        if (nextLogAt.compareAndSet(logAt, nextAt)) {
            // keep gap in sync (best-effort, no need to be perfect)
            logGap.set(nextGap);
            logStats(count);
        }
    }

    private void logStats(long count) {
        long h = hits.get();
        long m = misses.get();
        long total = h + m;
        long req = requests.get();
        long avgReqUs = req == 0 ? 0 : (requestNanosTotal.get() / req) / 1_000;
        long maxReqUs = requestNanosMax.get() / 1_000;
        long avgHitUs = h == 0 ? 0 : (hitNanosTotal.get() / h) / 1_000;
        long maxHitUs = hitNanosMax.get() / 1_000;
        long avgMissUs = m == 0 ? 0 : (missNanosTotal.get() / m) / 1_000;
        long maxMissUs = missNanosMax.get() / 1_000;
        long hitRatioPct = total == 0 ? 0 : (h * 100 / total);
        logger.atLevel(level).log(
                "Template metrics after {} requests: hits={}, misses={}, hitRatio={}%, " +
                        "avgRequest={}µs (max={}µs), avgHit={}µs (max={}µs), avgMiss={}µs (max={}µs)",
                count,
                h,
                m,
                hitRatioPct,
                avgReqUs,
                maxReqUs,
                avgHitUs,
                maxHitUs,
                avgMissUs,
                maxMissUs
        );
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