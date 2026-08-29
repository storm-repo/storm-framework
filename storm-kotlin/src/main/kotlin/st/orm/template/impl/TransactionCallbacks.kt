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

import kotlinx.coroutines.runBlocking
import st.orm.TransactionCallbackException
import st.orm.core.spi.TransactionRunner
import java.util.function.Consumer

/**
 * Collects and executes transaction lifecycle callbacks for the suspend transaction flow.
 *
 * Callbacks registered via [addOnCommit], [addOnRollback] and [addOnCompletion] are stored in a single list, so
 * they run in registration order regardless of which kind they are; the ones that do not apply to the outcome are
 * skipped. When the transaction settles, the applicable callbacks are executed sequentially in
 * the enclosing coroutine context. If any callback throws, remaining callbacks still execute and the failures are
 * reported as a [TransactionCallbackException] whose cause is the first one, with subsequent ones added to it as
 * suppressed.
 *
 * Implements the language-neutral [st.orm.core.spi.TransactionCallbacks] registration contract, so blocking
 * blocks nested inside a suspend transaction defer their callbacks to this holder.
 *
 * @since 1.11
 */
internal class TransactionCallbacks : st.orm.core.spi.TransactionCallbacks {
    private val callbacks = mutableListOf<Entry>()

    /**
     * A registered callback and the outcomes it applies to, so that callbacks of every kind share a single
     * registration order.
     */
    private class Entry(
        private val onCommit: Boolean,
        private val onRollback: Boolean,
        val action: suspend (Boolean) -> Unit,
    ) {
        fun applies(committed: Boolean): Boolean = if (committed) onCommit else onRollback
    }

    fun addOnCommit(callback: suspend () -> Unit) {
        callbacks += Entry(onCommit = true, onRollback = false) { callback() }
    }

    fun addOnRollback(callback: suspend () -> Unit) {
        callbacks += Entry(onCommit = false, onRollback = true) { callback() }
    }

    fun addOnCompletion(callback: suspend (Boolean) -> Unit) {
        callbacks += Entry(onCommit = true, onRollback = true, action = callback)
    }

    override fun addOnCommit(callback: Runnable) {
        callbacks += Entry(onCommit = true, onRollback = false) { callback.run() }
    }

    override fun addOnRollback(callback: Runnable) {
        callbacks += Entry(onCommit = false, onRollback = true) { callback.run() }
    }

    override fun addOnCompletion(callback: Consumer<Boolean>) {
        callbacks += Entry(onCommit = true, onRollback = true) { committed -> callback.accept(committed) }
    }

    /**
     * Settles the callbacks for the given outcome, honoring external ownership of the physical transaction:
     * the suspend-flow counterpart of the blocking flow's settlement in [TransactionRunner].
     *
     * When [mayDeferToExternal] and an external transaction is detected, the callbacks are handed over to fire
     * on that transaction's outcome, and [markExternalRollbackOnly] pushes the block's rollback demand onto it.
     * The external manager completes on its own thread without a coroutine context, so the transferred
     * callbacks are bridged via `runBlocking`, matching the blocking flow's documented callback semantics.
     * Otherwise the callbacks fire here for the given outcome.
     */
    suspend fun settle(mayDeferToExternal: Boolean, markExternalRollbackOnly: Boolean, committed: Boolean) {
        if (mayDeferToExternal && (markExternalRollbackOnly || callbacks.isNotEmpty())) {
            val external = TransactionRunner.externalTransaction()
            if (external != null) {
                if (markExternalRollbackOnly) external.setRollbackOnly()
                if (callbacks.isNotEmpty()) {
                    external.onCompletion { externalOutcome -> runBlocking { fire(externalOutcome) } }
                }
                return
            }
        }
        fire(committed)
    }

    private suspend fun fire(committed: Boolean) {
        var first: Throwable? = null
        // Iterate a snapshot so that a callback registering another callback is a no-op for this run rather than
        // a concurrent modification.
        for (entry in callbacks.toList()) {
            if (!entry.applies(committed)) continue

            try {
                entry.action(committed)
            } catch (e: Throwable) {
                if (first == null) first = e else first.addSuppressed(e)
            }
        }
        first?.let {
            throw TransactionCallbackException(
                if (committed) {
                    "Transaction committed, but a completion callback failed."
                } else {
                    "Transaction rolled back, and a completion callback failed."
                },
                it,
                committed,
            )
        }
    }
}
