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
package st.orm.template.impl

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import st.orm.core.template.impl.CallSiteCapture
import st.orm.core.template.impl.SqlInterceptorManager
import st.orm.core.template.impl.SqlInterceptorManager.Operator
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import st.orm.core.template.SqlLog as CoreSqlLog

/**
 * Records the statements executed by [block] into a coroutine-aware scope and hands the summary to [onSummary]
 * once the block completes, normally or not.
 *
 * This is the recording machinery behind [st.orm.template.sqlLog] and the Ktor plugin's per-call scope; the
 * summary is internal wiring on its way to the `st.orm.sql.perf` logger, not part of the public API.
 *
 * The scope follows the coroutine rather than the thread it happens to run on, so it keeps recording across a
 * suspension that resumes elsewhere, and a scope opened by one coroutine is never observed by another.
 */
suspend fun <T> recordSqlLog(
    name: String,
    limit: Int,
    callSites: Boolean,
    block: suspend () -> T,
    onSummary: (CoreSqlLog.Summary) -> Unit,
): T {
    // A scope times executions, so it listens around them rather than intercepting the statements a call builds.
    val recorder = CoreSqlLog.recorder(limit, callSites)
    val holder = SqlInterceptorManager.holder()
    val previous: Array<Operator>? = holder.get()
    val operator = Operator({ sql -> sql }, { it }, recorder)
    val installed: Array<Operator> = if (previous == null) arrayOf(operator) else arrayOf(operator, *previous)
    // The scope opens where the caller still is on the stack, so that frame is the launch-site fallback for
    // children whose own stack loses it.
    val hint: CoroutineContext = if (callSites) {
        CallSiteCapture.captureCallSite()?.let { CallSiteCapture.callSiteHint().asContextElement(it) }
            ?: EmptyCoroutineContext
    } else {
        EmptyCoroutineContext
    }
    val started = System.nanoTime()
    // The count is what allows the statement path to skip the holder, so it has to cover the whole scope, on every
    // thread the coroutine resumes on.
    SqlInterceptorManager.scopeInstalled()
    try {
        return withContext(currentCoroutineContext() + holder.asContextElement(installed) + hint) {
            block()
        }
    } finally {
        SqlInterceptorManager.scopeUninstalled()
        onSummary(CoreSqlLog.summary(name, recorder, System.nanoTime() - started))
    }
}
