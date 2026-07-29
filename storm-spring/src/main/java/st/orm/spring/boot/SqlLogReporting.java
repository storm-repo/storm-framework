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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.Duration;
import org.slf4j.Logger;
import st.orm.core.template.SqlLog;

/**
 * How a scope summary reports, shared by every Spring entry point so the request filter and the entry-point
 * interceptor cannot drift apart. Without thresholds every unit of work that touched the database is reported, at
 * INFO; with a threshold set, only the ones that exceed it are, at WARN. At DEBUG the full statement texts follow
 * the summary.
 */
final class SqlLogReporting {

    private SqlLogReporting() {
    }

    /**
     * Returns whether a summary would reach the logger, so a caller can skip opening a scope whose summary
     * nothing consumes.
     */
    static boolean consumes(@Nonnull Logger logger, boolean thresholded) {
        return thresholded ? logger.isWarnEnabled() : logger.isInfoEnabled();
    }

    /**
     * Reports the summary. A unit of work that touched no database says nothing worth a line.
     */
    static void report(@Nonnull Logger logger,
                       @Nonnull SqlLog.Summary summary,
                       @Nullable Integer statementThreshold,
                       @Nullable Duration durationThreshold) {
        if (summary.statementCount() == 0) {
            return;
        }
        // At DEBUG the full statement texts follow the summary, so an elided row can be matched to its
        // statement.
        Object rendered = logger.isDebugEnabled() ? summary.toDetailedString() : summary;
        if (statementThreshold == null && durationThreshold == null) {
            logger.info("{}", rendered);
            return;
        }
        boolean exceeded = (statementThreshold != null && summary.statementCount() >= statementThreshold)
                || (durationThreshold != null && summary.durationNanos() >= durationThreshold.toNanos());
        if (exceeded) {
            logger.warn("{}", rendered);
        }
    }
}
