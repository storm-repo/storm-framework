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
import st.orm.core.template.impl.SqlInterceptorManager
import st.orm.template.impl.recordSqlLog
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import st.orm.core.template.SqlLog as CoreSqlLog

/**
 * Declares packages or source files whose frames are skipped when a scope attributes an execution to a call
 * site, so rows name the code that asked for the work rather than the application's own database plumbing.
 *
 * An entry ending in `.kt` or `.java` matches the frame's source file, which is what covers inline functions:
 * their lambdas compile into the caller's class, where a package prefix cannot see them, while the frame keeps
 * the declaring file's name. When every application frame on a stack is declared plumbing, the innermost
 * plumbing frame is reported rather than none. Intended to be called once at startup.
 *
 * @param packagePrefixes the package prefixes or source file names to skip, such as `"com.acme.db"` or
 *   `"DbExtensions.kt"`.
 * @since 1.13
 */
fun ignoreSqlLogCallSites(vararg packagePrefixes: String) {
    CoreSqlLog.ignoreCallSites(*packagePrefixes)
}

/**
 * Sets the width summary rows aim for, such as 120 for narrow viewers or 240 for wide ones; the statement
 * text elides to what the row's other columns leave. A display property of the deployment; intended to be
 * called once at startup.
 *
 * @param width the display width; at least 80.
 * @since 1.13
 */
fun sqlLogLineWidth(width: Int) {
    CoreSqlLog.lineWidth(width)
}

/**
 * Sets how summary rows render the declared hydration shape of their statement's type. Off by default; a
 * display property of the deployment, intended to be called once at startup.
 *
 * @param shapes how shapes render.
 * @since 1.13
 */
fun sqlLogHydrationShapes(shapes: HydrationShapes) {
    CoreSqlLog.hydrationShapes(
        when (shapes) {
            HydrationShapes.OFF -> CoreSqlLog.HydrationShapes.OFF
            HydrationShapes.SHORT -> CoreSqlLog.HydrationShapes.SHORT
            HydrationShapes.FULL -> CoreSqlLog.HydrationShapes.FULL
        },
    )
}

/**
 * How summary rows render the declared hydration shape of their statement's type.
 *
 * @see sqlLogHydrationShapes
 * @since 1.13
 */
enum class HydrationShapes {

    /** No shape renders. The default. */
    OFF,

    /**
     * A row whose type hydrates beyond its own table ends with the numeric shape, `j2 c12 d3`: joins, columns,
     * and graph depth. A flat type shows none.
     */
    SHORT,

    /** Every mapped row ends with the full shape, `joins=2 columns=12 graph=Pet(Owner(City))`. */
    FULL,
}

/** Statements recorded per scope before recording stops, keeping a runaway call from retaining the lot. */
const val DEFAULT_SQL_LOG_LIMIT: Int = 200

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
fun sqlLogContext(): CoroutineContext {
    val holder = SqlInterceptorManager.holder()
    val open = holder.get() ?: return EmptyCoroutineContext
    var context: CoroutineContext = holder.asContextElement(open)
    if (SqlInterceptorManager.hasCallSiteListeners()) {
        CoreSqlLog.captureCallSite()?.let { launchSite ->
            context += CoreSqlLog.callSiteHint().asContextElement(launchSite)
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
 * The summary reports through the `st.orm.sql.summary` logger when the block completes, normally or not, and the
 * logger is the only switch: statements are recorded only while it is enabled at INFO, and at DEBUG the full
 * statement texts follow the summary. What a scope observed is a report, not an API: production numbers belong to
 * the Micrometer observations, and test assertions to `SqlCapture`.
 *
 * The scope follows the coroutine rather than the thread it happens to run on, so it keeps recording across a
 * suspension that resumes elsewhere, and a scope opened by one coroutine is never observed by another.
 *
 * Cost when inactive is zero: a scope registers on the interceptor chain every statement already walks, so a
 * statement executed with no scope open reads a single counter and stops.
 *
 * @param name what the scope covers, used to label the summary.
 * @param limit the number of statements to record; the summary counts the rest regardless.
 * @param callSites whether to attribute each execution to the application frame that caused it, which costs a
 *   stack walk per execution while the scope records.
 * @param block the work to record.
 * @return the block's result.
 * @since 1.13
 */
suspend fun <T> sqlLog(
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
