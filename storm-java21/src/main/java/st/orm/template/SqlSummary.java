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
package st.orm.template;

import static java.util.Objects.requireNonNull;
import static st.orm.core.template.StatementOrigin.FETCH;

import jakarta.annotation.Nonnull;
import java.time.Duration;
import java.util.List;
import st.orm.core.template.SqlScope.Summary;

/**
 * What a call cost the database.
 *
 * <p>A summary answers what one unit of work did as a whole: how many statements it took, how long the database
 * spent on them against how long the call took, and which statement carried the weight. A statement run many times
 * cheaply outranks one slow statement when it cost more in total, which is what {@link #byStatement()} orders
 * by.</p>
 *
 * @since 1.13
 */
public final class SqlSummary {

    private final Summary summary;

    SqlSummary(@Nonnull Summary summary) {
        this.summary = requireNonNull(summary, "summary");
    }

    /** Returns what the scope covered. */
    public String name() {
        return summary.name();
    }

    /** Returns the statements the call executed, including any beyond the recording limit. */
    public int statementCount() {
        return summary.statementCount();
    }

    /**
     * Returns how many were fetches: one per reference the call read that no query had already brought along.
     *
     * <p>A fetch the transaction's entity cache served issues no statement, so this counts distinct cache misses
     * rather than {@code fetch()} call sites.</p>
     */
    public int fetchCount() {
        return summary.count(FETCH);
    }

    /**
     * Returns how many reads the transaction's entity cache served without a statement: a reference resolving
     * to an entity the transaction had already read, or an identity lookup at {@code REPEATABLE_READ} and
     * above.
     *
     * <p>The {@link #fetchCount()} reports the cache misses; this is what the cache saved.</p>
     */
    public int cacheHits() {
        return summary.cacheHits();
    }

    /** Returns how long the call took. */
    public Duration duration() {
        return Duration.ofNanos(summary.durationNanos());
    }

    /** Returns the summed statement duration, which under a fan-out exceeds {@link #databaseElapsed()}. */
    public Duration databaseDuration() {
        return Duration.ofNanos(summary.databaseNanos());
    }

    /** Returns the time during which at least one statement was in flight. */
    public Duration databaseElapsed() {
        return Duration.ofNanos(summary.databaseElapsedNanos());
    }

    /** Returns the greatest number of statements in flight at once; above one, the work ran concurrently. */
    public int peakConcurrency() {
        return summary.peakConcurrency();
    }

    /** Returns the recorded statements, up to the scope's limit. */
    public List<SqlStatement> statements() {
        return summary.statements().stream()
                .map(statement -> new SqlStatement(
                        statement.operation().name(),
                        statement.dataType() == null ? "-" : statement.dataType().getSimpleName(),
                        statement.origin() == FETCH,
                        statement.statement(),
                        Duration.ofNanos(statement.durationNanos()),
                        statement.rows(),
                        statement.exactRows()))
                .toList();
    }

    /** Returns one entry per distinct statement, heaviest first, which is where the time went. */
    public List<StatementSummary> byStatement() {
        return summary.byStatement().stream()
                .map(line -> new StatementSummary(line.statement(), line.dataType(), line.fetch(),
                        line.executions(), line.variants(), Duration.ofNanos(line.durationNanos()), line.rows(),
                        line.exactRows(), line.callSite(), line.sites()))
                .toList();
    }

    /** Returns whether the call executed more statements than the scope recorded. */
    public boolean truncated() {
        return summary.truncated();
    }

    /**
     * Renders the summary with the full statement texts appended, one per row in row order, so a row whose
     * elided text is not enough can be matched to the statement it stands for.
     *
     * @return the rendered summary followed by the full statements.
     */
    public String toDetailedString() {
        return summary.toDetailedString();
    }

    @Override
    public String toString() {
        return summary.toString();
    }
}
