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

import kotlinx.coroutines.asContextElement
import st.orm.core.template.impl.CallSiteCapture
import st.orm.core.template.impl.SqlInterceptorManager
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import st.orm.core.template.SqlLog as CoreSqlLog

/** Statements recorded per scope before recording stops, keeping a runaway call from retaining the lot. */
public const val DEFAULT_SQL_LOG_LIMIT: Int = 200

/**
 * Returns the coroutine context that carries the SQL log open on the calling thread.
 *
 * A coroutine inherits its parent's context, never the parent thread's thread locals, so a scope opened in
 * blocking code reaches a coroutine only when something binds it into that coroutine's context. Storm binds it
 * wherever Storm itself builds a context, such as [transaction] and [withTransactionOptions]. Code that builds
 * its own coroutine passes this alongside:
 *
 * ```
 * fun loadOwners(ids: List<Int>): List<Owner> = runBlocking(sqlLogContext()) {
 *     ids.map { async { owners.getById(it) } }.awaitAll()
 * }
 * ```
 *
 * Every coroutine launched inside then observes the scope, whichever thread it resumes on.
 *
 * When the observing scope records call sites, the context also carries the launch site: at this call the caller
 * is still on the stack, while on the dispatcher thread the work resumes on it no longer is. A statement whose
 * stack is plumbing end to end is then attributed to the frame that launched the work rather than to the
 * plumbing.
 *
 * Returns [EmptyCoroutineContext] when no scope is open, so nothing is carried and no thread local is written on
 * suspension. Opening the scope inside coroutine code with [sqlLog] needs none of this: its children inherit by
 * construction.
 *
 * @return the context carrying the open scope, or an empty context when none is open.
 * @since 1.13
 */
public fun sqlLogContext(): CoroutineContext {
    val holder = SqlInterceptorManager.holder()
    val open = holder.get() ?: return EmptyCoroutineContext
    var context: CoroutineContext = holder.asContextElement(open)
    if (SqlInterceptorManager.hasCallSiteListeners()) {
        CallSiteCapture.captureCallSite()?.let { launchSite ->
            context += CallSiteCapture.callSiteHint().asContextElement(launchSite)
        }
    }
    return context
}

/**
 * Records the statements executed by [block], reporting what the call cost the database.
 *
 * A scope covers whatever runs inside it, whichever repository, query builder or template issued the statement, so
 * it can wrap the handling of a request rather than one repository:
 *
 * ```
 * get("/owners/{id}") {
 *     val view = sqlLog("getOwner") { ownerService.load(call.parameters.getOrFail<Int>("id")) }
 *     call.respond(view)
 * }
 * ```
 *
 * The summary reports through the `st.orm.sql.perf` logger when the block completes, normally or not, and the
 * logger is the only switch: statements are recorded only while it is enabled at INFO, and at TRACE the full
 * statement texts follow the summary. What a scope observed is a report, not an API: production numbers belong to
 * the Micrometer observations, and test assertions to `SqlCapture`.
 *
 * The scope follows the coroutine rather than the thread it happens to run on, so it keeps recording across a
 * suspension that resumes elsewhere, and a scope opened by one coroutine is never observed by another. Code that
 * runs outside coroutines opens the same scope with [sqlLogBlocking].
 *
 * Cost when inactive is zero: a scope registers on the interceptor chain every statement already walks, so a
 * statement executed with no scope open reads a single counter and stops.
 *
 * How summaries render — line width, call-site skips — is a property of the deployment, configured rather
 * than programmed: the `storm.sql_log.performance.line_width` and
 * `storm.sql_log.call_site_skip` system properties on a plain JVM, or the corresponding keys of the Spring and
 * Ktor integrations.
 *
 * @param name what the scope covers, used to label the summary.
 * @param limit the number of statements to record; the summary counts the rest regardless.
 * @param callSites whether to attribute each execution to the application frame that caused it, which costs a
 *   stack walk per execution while the scope records.
 * @param block the work to record.
 * @return the block's result.
 * @since 1.13
 */
@OptIn(InternalStormApi::class)
public suspend fun <T> sqlLog(
    name: String,
    limit: Int = DEFAULT_SQL_LOG_LIMIT,
    callSites: Boolean = false,
    block: suspend () -> T,
): T {
    if (!CoreSqlLog.reporting()) {
        // Nothing consumes the summary, so no scope is opened to build one.
        return block()
    }
    return recordSqlLog(name, limit, callSites, block, CoreSqlLog::report)
}

/**
 * Records the statements executed by [block] from code that runs outside coroutines, reporting what the call cost
 * the database.
 *
 * This is [sqlLog] for blocking code, mirroring how transactions ship as the [transaction] and [transactionBlocking]
 * pair; a Kotlin service running on a request thread, such as a Spring MVC controller, wraps its work the same way:
 *
 * ```
 * fun loadOwners(): List<OwnerView> = sqlLogBlocking("loadOwners") {
 *     ownerService.loadAll()
 * }
 * ```
 *
 * The scope binds to the calling thread and to the contexts Storm builds below it: a [transaction] or
 * [transactionBlocking] block opened inside observes it, whichever thread its coroutines resume on. A coroutine the
 * application builds itself observes it only when launched with [sqlLogContext] alongside. Everything else,
 * including how the summary reports and what it costs while inactive, matches [sqlLog].
 *
 * @param name what the scope covers, used to label the summary.
 * @param limit the number of statements to record; the summary counts the rest regardless.
 * @param callSites whether to attribute each execution to the application frame that caused it, which costs a
 *   stack walk per execution while the scope records.
 * @param block the work to record.
 * @return the block's result.
 * @since 1.14
 */
public fun <T> sqlLogBlocking(
    name: String,
    limit: Int = DEFAULT_SQL_LOG_LIMIT,
    callSites: Boolean = false,
    block: () -> T,
): T {
    if (!CoreSqlLog.reporting()) {
        // Nothing consumes the summary, so no scope is opened to build one.
        return block()
    }
    // A scope times executions, so it listens around them rather than intercepting the statements a call builds.
    val recorder = CoreSqlLog.recorder(limit, callSites)
    val started = System.nanoTime()
    val detach = SqlInterceptorManager.attach(recorder)
    try {
        return block()
    } finally {
        try {
            detach.close()
        } finally {
            // A call that failed is worth summarizing too: the statements leading up to it are the evidence.
            CoreSqlLog.report(CoreSqlLog.summary(name, recorder, System.nanoTime() - started))
        }
    }
}
