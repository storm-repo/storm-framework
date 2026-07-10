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

import kotlinx.coroutines.*
import st.orm.core.spi.TransactionScope
import st.orm.template.TransactionIsolation.*
import st.orm.template.TransactionPropagation.*
import st.orm.template.impl.TransactionCallbacks
import java.sql.Connection.*
import java.sql.SQLTimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Executes the given [block] within a database transaction.
 *
 * The transaction binds to the first ORM template that executes inside the block: opening the block only records the
 * requested options, and the template's transaction provider opens the actual transaction on first use. A block that
 * never touches a template completes as a no-op.
 *
 * ## Propagation behavior matrix
 *
 * | Propagation       | Inner commit                                     | Inner rollback                                                   | Outer commit                                         | Outer rollback                                                      |
 * |-------------------|--------------------------------------------------|------------------------------------------------------------------|------------------------------------------------------|----------------------------------------------------------------------|
 * | `REQUIRED`        | Joins outer tx—no actual commit until outer ends | Marks whole tx rollback-only; everything rolls back at end       | Commits entire tx (all work)                         | Rolls back entire tx (all work)                                     |
 * | `REQUIRES_NEW`    | Commits only the new (inner) tx                  | Rolls back only the inner tx; outer stays active                 | Commits the outer tx (inner work stays committed)    | Rolls back the outer tx; inner-committed work remains               |
 * | `NESTED`          | Releases the JDBC savepoint—inner changes are now visible to the outer transaction  | Rolls back to savepoint—undoes just inner work, outer stays open | Commits entire tx (savepoints dropped, all work kept) | Rolls back entire tx (including inner work, regardless of savepoint) |
 *
 * @param propagation    Controls how this call participates in an existing transaction:
 *                       - `REQUIRED` (default): join or start if none
 *                       - `REQUIRES_NEW`: suspend outer, start fresh
 *                       - `NESTED`: create JDBC savepoint
 * @param isolation      The isolation level for the transaction. Defaults to [TransactionIsolation.DEFAULT].
 * @param timeoutSeconds The transaction timeout in seconds. If `null`, uses provider default.
 * @param readOnly       Whether the transaction is read-only. Defaults to `false`.
 * @param block          The transactional logic to execute.
 * @return The result of executing [block].
 * @throws st.orm.PersistenceException if transaction execution fails.
 * @since 1.5
 */
fun <T> transactionBlocking(
    propagation: TransactionPropagation? = null,
    isolation: TransactionIsolation? = null,
    timeoutSeconds: Int? = null,
    readOnly: Boolean? = null,
    block: Transaction.() -> T,
): T {
    val options = localTransactionOptions.get() ?: globalTransactionOptions.get()
    val resolvedPropagation = propagation ?: options.propagation
    val scopeOptions = scopeOptions(
        propagation = resolvedPropagation,
        isolation = isolation ?: options.isolation,
        timeoutSeconds = timeoutSeconds ?: options.timeoutSeconds,
        readOnly = readOnly ?: options.readOnly,
        suspendMode = false,
    )
    val scope = TransactionScope.open(scopeOptions)
    val parentCallbacks = currentBlockingCallbacks.get()
    if (resolvedPropagation.isJoining && scope.parent() != null && parentCallbacks != null) {
        // Joining an outer transaction block: delegate callbacks to the outer holder; do not fire here.
        val result = try {
            block(scopeTransaction(scope, parentCallbacks))
        } catch (e: Throwable) {
            try {
                completeAfterFailure(scope, e)
            } finally {
                scope.close()
            }
        }
        try {
            scope.complete(false)
            afterSuccessfulCompletion(scope)
        } finally {
            scope.close()
        }
        return result
    }
    // Owner of the callback lifecycle: outermost block, REQUIRES_NEW / NOT_SUPPORTED, or no parent callbacks.
    val callbacks = TransactionCallbacks()
    val previousCallbacks = currentBlockingCallbacks.get()
    currentBlockingCallbacks.set(callbacks)
    val outcome = try {
        val value = block(scopeTransaction(scope, callbacks))
        TransactionOutcome(value, scope.isRollbackOnly)
    } catch (e: Throwable) {
        // Restore and uninstall the scope before firing so callbacks see a clean state.
        if (previousCallbacks == null) currentBlockingCallbacks.remove() else currentBlockingCallbacks.set(previousCallbacks)
        val wrapped = try {
            completeAfterFailure(scope, e)
        } catch (completionException: Throwable) {
            completionException
        } finally {
            scope.close()
        }
        try {
            callbacks.fireRollbackBlocking()
        } catch (callbackException: Throwable) {
            wrapped.addSuppressed(callbackException)
        }
        throw wrapped
    }
    // Restore and uninstall the scope before firing so callbacks see a clean state.
    if (previousCallbacks == null) currentBlockingCallbacks.remove() else currentBlockingCallbacks.set(previousCallbacks)
    try {
        try {
            scope.complete(false)
            afterSuccessfulCompletion(scope)
        } finally {
            scope.close()
        }
    } catch (completionException: Throwable) {
        try {
            callbacks.fireRollbackBlocking()
        } catch (callbackException: Throwable) {
            completionException.addSuppressed(callbackException)
        }
        throw completionException
    }
    if (outcome.rollbackOnly) callbacks.fireRollbackBlocking() else callbacks.fireCommitBlocking()
    return outcome.value
}

/**
 * Executes the given [block] within a coroutine-friendly database transaction.
 *
 * This variant ensures the transactional logic runs on the specified coroutine [dispatcher]
 * (e.g. a dispatcher) while preserving all the usual Spring semantics for propagation,
 * isolation, timeout and read-only settings.
 *
 * The transaction binds to the first ORM template that executes inside the block: opening the block only records the
 * requested options, and the template's transaction provider opens the actual transaction on first use. A block that
 * never touches a template completes as a no-op.
 *
 * ## Propagation behavior matrix
 *
 * | Propagation       | Inner commit                                     | Inner rollback                                                   | Outer commit                                         | Outer rollback                                                      |
 * |-------------------|--------------------------------------------------|------------------------------------------------------------------|------------------------------------------------------|----------------------------------------------------------------------|
 * | `REQUIRED`        | Joins outer tx—no real commit until outer ends   | Marks whole tx rollback-only; rolls back all on outer exit      | Commits entire tx (all work)                         | Rolls back entire tx (all work)                                     |
 * | `REQUIRES_NEW`    | Commits only the new (inner) tx                  | Rolls back only the inner tx; outer stays active                 | Commits outer tx (inner stays committed)             | Rolls back outer tx; inner-committed work remains                  |
 * | `NESTED`          | Releases the JDBC savepoint—inner changes are now visible to the outer transaction  | Rolls back to savepoint—undoes just inner work, outer stays open | Commits entire tx (savepoints dropped, all work kept) | Rolls back entire tx (including inner work, regardless of savepoint) |
 *
 * @param dispatcher        The [CoroutineDispatcher] in which to run the transaction.
 *                          For example, `Dispatchers.IO`.
 * @param propagation       Controls how this call participates in an existing transaction:
 *                          - `REQUIRED` (default): join or start if none
 *                          - `REQUIRES_NEW`: suspend outer, start fresh
 *                          - `NESTED`: create JDBC savepoint
 * @param isolation         The isolation level for the transaction. Defaults to [TransactionIsolation.DEFAULT].
 * @param timeoutSeconds    The transaction timeout in seconds. If `null`, uses the provider's default.
 * @param readOnly          Whether the transaction is read-only. Defaults to `false`.
 * @param block             The transactional logic to execute, with `this` bound to a [Transaction].
 * @return The result of executing [block].
 * @throws st.orm.PersistenceException if transaction execution or rollback/commit fails.
 * @since 1.5
 */
suspend fun <T> transaction(
    dispatcher: CoroutineDispatcher = TransactionDispatchers.Default,
    propagation: TransactionPropagation? = null,
    isolation: TransactionIsolation? = null,
    timeoutSeconds: Int? = null,
    readOnly: Boolean? = null,
    block: suspend Transaction.() -> T,
): T {
    val currentContext = currentCoroutineContext()
    val options = currentContext[Scoped]?.options ?: globalTransactionOptions.get()
    val resolvedPropagation = propagation ?: options.propagation
    val scopeOptions = scopeOptions(
        propagation = resolvedPropagation,
        isolation = isolation ?: options.isolation,
        timeoutSeconds = timeoutSeconds ?: options.timeoutSeconds,
        readOnly = readOnly ?: options.readOnly,
        suspendMode = true,
    )
    val parentScope = TransactionScope.current()
    val scope = TransactionScope.create(scopeOptions, parentScope)
    val parentCallbacks = currentContext[CallbacksKey]?.callbacks
    if (resolvedPropagation.isJoining && parentScope != null && parentCallbacks != null) {
        // Joining an outer transaction block: delegate callbacks to the outer holder; do not fire here.
        val elements = CallbacksKey(parentCallbacks) +
            TransactionScope.holder().asContextElement(scope) +
            currentBlockingCallbacks.asContextElement(parentCallbacks) +
            localTransactionOptions.asContextElement(options)
        val result = try {
            withContext(currentContext + elements) {
                block(scopeTransaction(scope, parentCallbacks))
            }
        } catch (e: Throwable) {
            completeAfterFailure(scope, e)
        }
        scope.complete(false)
        afterSuccessfulCompletion(scope)
        return result
    }
    // Owner of the callback lifecycle: outermost block, REQUIRES_NEW / NOT_SUPPORTED, or no parent callbacks.
    val callbacks = TransactionCallbacks()
    val elements = CallbacksKey(callbacks) +
        TransactionScope.holder().asContextElement(scope) + // Make the scope available via the ThreadLocal.
        currentBlockingCallbacks.asContextElement(callbacks) +
        localTransactionOptions.asContextElement(options) // Make the options available via the ThreadLocal in case the blocking variant is invoked from suspend context.
    // The dispatcher only applies to outermost transactions; nested blocks stay on the caller's dispatcher.
    val context = if (parentScope == null) currentContext + dispatcher + elements else currentContext + elements
    // Fire callbacks AFTER withContext returns, so CallbacksKey and ThreadLocals are restored.
    val outcome = try {
        withContext(context) {
            val value = block(scopeTransaction(scope, callbacks))
            TransactionOutcome(value, scope.isRollbackOnly)
        }
    } catch (e: Throwable) {
        val wrapped = try {
            completeAfterFailure(scope, e)
        } catch (completionException: Throwable) {
            completionException
        }
        try {
            callbacks.fireRollback()
        } catch (callbackException: Throwable) {
            wrapped.addSuppressed(callbackException)
        }
        throw wrapped
    }
    try {
        scope.complete(false)
        afterSuccessfulCompletion(scope)
    } catch (completionException: Throwable) {
        try {
            callbacks.fireRollback()
        } catch (callbackException: Throwable) {
            completionException.addSuppressed(callbackException)
        }
        throw completionException
    }
    if (outcome.rollbackOnly) callbacks.fireRollback() else callbacks.fireCommit()
    return outcome.value
}

/**
 * Coroutine context element that carries the [TransactionCallbacks] holder for the current physical transaction.
 */
private class CallbacksKey(val callbacks: TransactionCallbacks) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CallbacksKey>
}

/**
 * Thread-local that carries the [TransactionCallbacks] holder for the current physical transaction (blocking path).
 */
private val currentBlockingCallbacks: ThreadLocal<TransactionCallbacks?> = ThreadLocal.withInitial { null }

/**
 * Returns `true` when this propagation joins an existing physical transaction rather than starting a new one.
 */
private val TransactionPropagation.isJoining: Boolean
    get() = when (this) {
        REQUIRED, SUPPORTS, MANDATORY, NESTED -> true
        REQUIRES_NEW, NOT_SUPPORTED, NEVER -> false
    }

/**
 * Captures the result and rollback-only status of a transaction execution.
 */
private class TransactionOutcome<T>(val value: T, val rollbackOnly: Boolean)

/**
 * Maps the resolved transaction options onto the provider-agnostic scope options.
 */
private fun scopeOptions(
    propagation: TransactionPropagation,
    isolation: TransactionIsolation?,
    timeoutSeconds: Int?,
    readOnly: Boolean,
    suspendMode: Boolean,
): TransactionScope.Options = TransactionScope.Options(
    propagation.toString(),
    isolation?.let {
        when (it) {
            READ_UNCOMMITTED -> TRANSACTION_READ_UNCOMMITTED
            READ_COMMITTED -> TRANSACTION_READ_COMMITTED
            REPEATABLE_READ -> TRANSACTION_REPEATABLE_READ
            SERIALIZABLE -> TRANSACTION_SERIALIZABLE
        }
    },
    timeoutSeconds,
    readOnly,
    suspendMode,
)

/**
 * Returns the [Transaction] receiver for the given scope, delegating callback registration to [callbacks].
 */
private fun scopeTransaction(scope: TransactionScope, callbacks: TransactionCallbacks): Transaction = object : Transaction {
    override val isRollbackOnly: Boolean
        get() = scope.isRollbackOnly

    override fun setRollbackOnly() {
        scope.setRollbackOnly()
    }

    override fun onCommit(callback: suspend () -> Unit) {
        callbacks.addOnCommit(callback)
    }

    override fun onRollback(callback: suspend () -> Unit) {
        callbacks.addOnRollback(callback)
    }
}

/**
 * Post-completion checks that mirror the semantics of eagerly entered transaction frames.
 *
 * A scope that never touched a template still fails deterministically when its deadline has passed, marking a joined
 * outer scope rollback-only. A scope whose rollback-only mark was inherited from a joined inner scope raises an
 * [UnexpectedRollbackException] when its block attempts to commit; materialized frames raise this from the provider's
 * commit path, this check covers marks that were propagated at the scope level.
 */
private fun afterSuccessfulCompletion(scope: TransactionScope) {
    if (!scope.isMaterialized && scope.isDeadlineExpired) {
        val propagation = scope.options().propagation()
        val joining = propagation == null || propagation == "REQUIRED" || propagation == "SUPPORTS" || propagation == "MANDATORY"
        if (joining) {
            scope.parent()?.setRollbackOnly()
        }
        throw TransactionTimedOutException("Did not complete within timeout.")
    }
    if (scope.isRollbackInherited) {
        throw UnexpectedRollbackException("Transaction was marked rollback-only by a joined scope.")
    }
}

/**
 * Completes the scope after a failed block and returns the exception to throw: the original failure, or a
 * [TransactionTimedOutException] when the failure was caused by a statement timeout. Completion failures are
 * suppressed onto the original failure.
 */
private fun completeAfterFailure(scope: TransactionScope, e: Throwable): Nothing {
    val description = try {
        scope.materializedContext()?.describe()?.orElse(null)
    } catch (ignore: Throwable) {
        null
    }
    try {
        scope.complete(true) // Suppresses rollback exceptions internally to surface the original error.
    } catch (completionException: Throwable) {
        e.addSuppressed(completionException)
    }
    if (e.cause is SQLTimeoutException) {
        val base = e.message ?: "Did not complete within timeout."
        throw TransactionTimedOutException(
            if (description != null) "$base [$description]" else base,
            e,
        )
    }
    throw e
}

/**
 * Global transaction options that are applied to all transactions by default.
 */
private val globalTransactionOptions = AtomicReference(TransactionOptions())

/**
 * Coroutine context element for transaction options that are applied to all transactions started in the current
 * coroutine context.
 */
private class Scoped(val options: TransactionOptions) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<Scoped>
}

/**
 * Thread-local transaction options that are applied to all transactions started in the current thread.
 */
private val localTransactionOptions: ThreadLocal<TransactionOptions?> = ThreadLocal.withInitial { null }

/**
 * Sets the global transaction options.
 *
 * This affects *new* transactions that do not override options locally.
 *
 * Typical usage: call once during application startup to configure default options that apply to all transactions.
 *
 * @param propagation The transaction propagation behavior.
 * @param isolation The transaction isolation level.
 * @param timeoutSeconds The transaction timeout in seconds.
 * @param readOnly Whether the transaction is read-only.
 * @since 1.6
 */
fun setGlobalTransactionOptions(
    propagation: TransactionPropagation? = null,
    isolation: TransactionIsolation? = null,
    timeoutSeconds: Int? = null,
    readOnly: Boolean? = null,
) {
    val defaults = TransactionOptions()
    globalTransactionOptions.set(
        defaults.copy(
            propagation = propagation ?: defaults.propagation,
            isolation = isolation ?: defaults.isolation,
            timeoutSeconds = timeoutSeconds ?: defaults.timeoutSeconds,
            readOnly = readOnly ?: defaults.readOnly,
        ),
    )
}

/**
 * Set the default transaction options for the current coroutine context.
 *
 * This function is intended to be used in combination with [transaction].
 *
 * @param propagation The transaction propagation behavior.
 * @param isolation The transaction isolation level.
 * @param timeoutSeconds The transaction timeout in seconds.
 * @param readOnly Whether the transaction is read-only.
 * @param block The coroutine code to execute.
 * @return The result of executing [block].
 * @since 1.6
 */
suspend fun <T> withTransactionOptions(
    propagation: TransactionPropagation? = null,
    isolation: TransactionIsolation? = null,
    timeoutSeconds: Int? = null,
    readOnly: Boolean? = null,
    block: suspend () -> T,
): T {
    val currentContext = currentCoroutineContext()
    val current = currentContext[Scoped]?.options ?: globalTransactionOptions.get()
    val scoped = TransactionOptions().copy(
        propagation = propagation ?: current.propagation,
        isolation = isolation ?: current.isolation,
        timeoutSeconds = timeoutSeconds ?: current.timeoutSeconds,
        readOnly = readOnly ?: current.readOnly,
    )
    return withContext(
        Scoped(scoped) +
            localTransactionOptions.asContextElement(scoped), // Make the defaults available via the ThreadLocal in case the blocking variant is invoked from suspend context.
    ) { block() }
}

/**
 * Set the default transaction options for the current thread.
 *
 * This function is intended to be used in combination with [transactionBlocking].
 *
 * @param propagation The transaction propagation behavior.
 * @param isolation The transaction isolation level.
 * @param timeoutSeconds The transaction timeout in seconds.
 * @param readOnly Whether the transaction is read-only.
 * @param block The code to execute.
 * @return The result of executing [block].
 * @since 1.6
 */
fun <T> withTransactionOptionsBlocking(
    propagation: TransactionPropagation? = null,
    isolation: TransactionIsolation? = null,
    timeoutSeconds: Int? = null,
    readOnly: Boolean? = null,
    block: () -> T,
): T {
    val previous = localTransactionOptions.get()
    val current = previous ?: globalTransactionOptions.get()
    localTransactionOptions.set(
        TransactionOptions(
            propagation = propagation ?: current.propagation,
            isolation = isolation ?: current.isolation,
            timeoutSeconds = timeoutSeconds ?: current.timeoutSeconds,
            readOnly = readOnly ?: current.readOnly,
        ),
    )
    return try {
        block()
    } finally {
        if (previous == null) localTransactionOptions.remove() else localTransactionOptions.set(previous)
    }
}
