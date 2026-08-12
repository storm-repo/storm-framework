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
package st.orm.test.kotlin

import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import st.orm.test.SqlCapture
import kotlin.coroutines.CoroutineContext

/**
 * Executes [block] while capturing all SQL statements it generates.
 *
 * The capture follows the coroutine rather than the thread it happens to run on: it keeps recording across a
 * suspension that resumes elsewhere, and coroutines launched within the block inherit it, whichever dispatcher
 * they land on. Work launched on a context built from scratch, such as an external scope, falls outside the
 * capture, and a capture recording one coroutine is never observed by another:
 *
 * ```
 * capture.recording {
 *     users.insert(User(email = "alice@example.com"))
 *     withContext(Dispatchers.IO) { users.findAll() }
 * }
 * capture.count(Operation.SELECT) shouldBe 1
 * ```
 *
 * Blocking code records through the [SqlCapture.record] and [SqlCapture.execute] members. The suspending entry
 * point carries its own name because Kotlin resolves a call against the member on the lambda's shape alone: a
 * member named `record` would win the call and reject the suspending block, rather than let it reach this
 * extension.
 *
 * @param block the work to record.
 * @return the block's result.
 * @since 1.14
 */
public suspend fun <T> SqlCapture.recording(block: suspend () -> T): T = withContext(SqlCaptureElement(this)) { block() }

/**
 * Binds a capture to the coroutine by attaching it to whichever thread the coroutine resumes on, and detaching
 * it again on suspension, so the capture is only ever observable from the coroutine that opened it.
 */
private class SqlCaptureElement(
    private val capture: SqlCapture,
) : ThreadContextElement<AutoCloseable> {

    /** A key per element, so nested captures stack on the thread the way the blocking entry points nest. */
    private val instanceKey = object : CoroutineContext.Key<SqlCaptureElement> {}

    override val key: CoroutineContext.Key<*> get() = instanceKey

    override fun updateThreadContext(context: CoroutineContext): AutoCloseable = capture.attach()

    override fun restoreThreadContext(context: CoroutineContext, oldState: AutoCloseable) {
        oldState.close()
    }
}
