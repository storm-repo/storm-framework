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
package st.orm.spring.boot;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import st.orm.core.template.SqlLog;

/**
 * The performance log: what one unit of work cost the database, reported as one summary under
 * {@code st.orm.sql.perf}.
 *
 * <p>Shared by every Spring boundary so the request filter and the entry-point interceptor cannot drift apart.
 * Without thresholds every unit of work that touched the database is reported, at INFO; with a threshold set,
 * only the ones that exceed it are, at WARN. At TRACE the full statement texts follow the summary.</p>
 */
final class PerformanceLog {

    /** The logger every boundary reports under. */
    static final Logger LOGGER = LoggerFactory.getLogger("st.orm.sql.perf");

    private PerformanceLog() {
    }

    /**
     * What a boundary records and when it reports it.
     *
     * @param limit statements recorded per scope; the summary counts the rest regardless.
     * @param callSites whether each execution is attributed to the frame that caused it.
     * @param statementThreshold statements above which the work is reported, or {@code null}.
     * @param durationThreshold duration above which the work is reported, or {@code null}.
     */
    record Settings(int limit,
                    boolean callSites,
                    @Nullable Integer statementThreshold,
                    @Nullable Duration durationThreshold) {

        /** Returns whether a threshold decides what is reported, which is also what decides the level. */
        boolean thresholded() {
            return statementThreshold != null || durationThreshold != null;
        }
    }

    /**
     * A boundary whose settings can be read and replaced while the application runs.
     *
     * <p>What a request costs the database is a question that arises about a deployment that is misbehaving now,
     * which is the same reason the slow statement threshold is live. A boundary reads its settings per unit of
     * work, so a replacement takes effect on the next one; whether the boundary exists at all stays the startup
     * decision {@code storm.sql-log.performance.enabled} makes, since a filter and a bean proxy cannot be installed into a
     * running context.</p>
     */
    interface Boundary {

        /** Returns the settings the boundary reports with. */
        Settings settings();

        /** Replaces the settings the boundary reports with, from the next unit of work on. */
        void settings(Settings settings);

        /** Names the boundary in what the endpoint reports. */
        String boundaryName();
    }

    /**
     * Returns whether a summary would reach the logger, so a caller can skip opening a scope whose summary
     * nothing consumes.
     */
    static boolean consumes(Settings settings) {
        return settings.thresholded() ? LOGGER.isWarnEnabled() : LOGGER.isInfoEnabled();
    }

    /**
     * Reports the summary. A unit of work that touched no database says nothing worth a line.
     */
    static void report(SqlLog.Summary summary, Settings settings) {
        if (summary.statementCount() == 0) {
            return;
        }
        var statementThreshold = settings.statementThreshold();
        var durationThreshold = settings.durationThreshold();
        // At TRACE the full statement texts follow the summary, so an elided row can be matched to its
        // statement. TRACE rather than DEBUG because this logger is a child of st.orm.sql: raising that to DEBUG
        // for per-statement logging would otherwise repeat every statement the statement logger already wrote.
        Object rendered = LOGGER.isTraceEnabled() ? summary.toDetailedString() : summary;
        if (statementThreshold == null && durationThreshold == null) {
            LOGGER.info("{}", rendered);
            return;
        }
        boolean exceeded = (statementThreshold != null && summary.statementCount() >= statementThreshold)
                || (durationThreshold != null && summary.durationNanos() >= durationThreshold.toNanos());
        if (exceeded) {
            LOGGER.warn("{}", rendered);
        }
    }
}
