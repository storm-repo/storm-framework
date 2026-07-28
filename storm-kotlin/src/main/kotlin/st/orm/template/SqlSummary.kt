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
package st.orm.template

import st.orm.core.template.SqlScope
import st.orm.core.template.StatementOrigin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * What a call cost the database.
 *
 * A summary answers what one unit of work did as a whole: how many statements it took, how long the database spent
 * on them against how long the call took, and which statement carried the weight. A statement run many times
 * cheaply outranks one slow statement when it cost more in total, which is what [byStatement] orders by.
 *
 * @since 1.13
 */
class SqlSummary internal constructor(private val summary: SqlScope.Summary) {

    /** What the scope covered. */
    val name: String get() = summary.name()

    /** Statements the call executed, including any beyond the recording limit. */
    val statementCount: Int get() = summary.statementCount()

    /**
     * How many were fetches: one per reference the call read that no query had already brought along. A fetch the
     * transaction's entity cache served issues no statement, so this counts distinct cache misses rather than
     * `fetch()` call sites.
     */
    val fetchCount: Int get() = summary.count(StatementOrigin.FETCH)

    /**
     * How many reads the transaction's entity cache served without a statement: a reference resolving to an
     * entity the transaction had already read, or an identity lookup at `REPEATABLE_READ` and above. The
     * [fetchCount] reports the cache misses; this is what the cache saved.
     */
    val cacheHits: Int get() = summary.cacheHits()

    /** How long the call took. */
    val duration: Duration get() = summary.durationNanos().nanoseconds

    /** The summed statement duration, which under a fan-out exceeds [databaseElapsed]. */
    val databaseDuration: Duration get() = summary.databaseNanos().nanoseconds

    /** The time during which at least one statement was in flight. */
    val databaseElapsed: Duration get() = summary.databaseElapsedNanos().nanoseconds

    /** The greatest number of statements in flight at once; above one, the work ran concurrently. */
    val peakConcurrency: Int get() = summary.peakConcurrency()

    /** The recorded statements, up to the scope's limit. */
    val statements: List<SqlStatement> get() = summary.statements().map {
        SqlStatement(
            it.operation().name,
            it.dataType()?.simpleName ?: "-",
            it.origin() == StatementOrigin.FETCH,
            it.statement(),
            it.durationNanos().nanoseconds,
            it.rows(),
            it.exactRows(),
        )
    }

    /** One entry per distinct statement, heaviest first, which is where the time went. */
    val byStatement: List<StatementSummary> get() = summary.byStatement().map {
        StatementSummary(
            it.statement(),
            it.dataType(),
            it.fetch(),
            it.executions(),
            it.variants(),
            it.durationNanos().nanoseconds,
            it.rows(),
            it.exactRows(),
            it.callSite(),
            it.sites(),
        )
    }

    /** Whether the call executed more statements than the scope recorded. */
    val truncated: Boolean get() = summary.truncated()

    override fun toString(): String = summary.toString()

    /**
     * Renders the summary with the full statement texts appended, one per row in row order, so a row whose
     * elided text is not enough can be matched to the statement it stands for.
     */
    fun toDetailedString(): String = summary.toDetailedString()
}
