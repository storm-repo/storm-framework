/*
 * Copyright 2024 - 2025 the original author or authors.
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
package st.orm.kt.template

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import st.orm.core.spi.Providers.getTransactionTemplate
import st.orm.core.spi.TransactionContext
import st.orm.core.spi.TransactionTemplate
import st.orm.kt.template.TransactionIsolation.*
import java.sql.Connection.*
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Executes the given [block] within a database transaction.
 *
 * By default this uses `REQUIRED` propagation, which means nested calls
 * participate in the same transaction. You can override propagation to
 * `REQUIRES_NEW` (fully isolated sub-transaction) or `NESTED` (JDBC savepoint).
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
 * @return               The result of executing [block].
 * @throws st.orm.PersistenceException if transaction execution fails.
 * @since 1.5
 */
fun <T> transaction(
    propagation: TransactionPropagation = TransactionPropagation.REQUIRED,
    isolation: TransactionIsolation?    = null,
    timeoutSeconds: Int?                = null,
    readOnly: Boolean                   = false,
    block: Transaction.() -> T
): T {
    val transactionTemplate = getTransactionTemplate(
        propagation = propagation,
        isolation = isolation,
        timeoutSeconds = timeoutSeconds,
        readOnly = readOnly
    )
    TransactionContextHolder.current.get()?.let { existingCtx ->
        return execute(transactionTemplate, existingCtx, block)
    }
    val newCtx = transactionTemplate.newContext(false).also {
        TransactionContextHolder.current.set(it)
    }
    try {
        return execute(transactionTemplate, newCtx, block)
    } finally {
        TransactionContextHolder.current.remove()
    }
}

/**
 * Executes the given [block] within a coroutine-friendly database transaction.
 *
 * This variant ensures the transactional logic runs on the specified coroutine [context]
 * (e.g. a dispatcher) while preserving all the usual Spring semantics for propagation,
 * isolation, timeout and read-only settings.
 *
 * ## Propagation behavior matrix
 *
 * | Propagation       | Inner commit                                     | Inner rollback                                                   | Outer commit                                         | Outer rollback                                                      |
 * |-------------------|--------------------------------------------------|------------------------------------------------------------------|------------------------------------------------------|----------------------------------------------------------------------|
 * | `REQUIRED`        | Joins outer tx—no real commit until outer ends   | Marks whole tx rollback-only; rolls back all on outer exit      | Commits entire tx (all work)                         | Rolls back entire tx (all work)                                     |
 * | `REQUIRES_NEW`    | Commits only the new (inner) tx                  | Rolls back only the inner tx; outer stays active                 | Commits outer tx (inner stays committed)             | Rolls back outer tx; inner-committed work remains                  |
 * | `NESTED`          | Releases the JDBC savepoint—inner changes are now visible to the outer transaction  | Rolls back to savepoint—undoes just inner work, outer stays open | Commits entire tx (savepoints dropped, all work kept) | Rolls back entire tx (including inner work, regardless of savepoint) |
 *
 * @param context           The [CoroutineContext] in which to run the transaction.
 *                          For example, `Dispatchers.IO`.
 * @param propagation       Controls how this call participates in an existing transaction:
 *                          - `REQUIRED` (default): join or start if none
 *                          - `REQUIRES_NEW`: suspend outer, start fresh
 *                          - `NESTED`: create JDBC savepoint
 * @param isolation         The isolation level for the transaction. Defaults to [TransactionIsolation.DEFAULT].
 * @param timeoutSeconds    The transaction timeout in seconds. If `null`, uses the provider’s default.
 * @param readOnly          Whether the transaction is read-only. Defaults to `false`.
 * @param block             The transactional logic to execute, with `this` bound to a [Transaction].
 * @return                  The result of executing [block].
 * @throws st.orm.PersistenceException if transaction execution or rollback/commit fails.
 * @since 1.5
 */
suspend fun <T> suspendTransaction(
    context: CoroutineContext = TransactionDispatchers.Default,
    propagation: TransactionPropagation = TransactionPropagation.REQUIRED,
    isolation: TransactionIsolation?    = null,
    timeoutSeconds: Int?                = null,
    readOnly: Boolean                   = false,
    block: suspend Transaction.() -> T
): T {
    val transactionTemplate = getTransactionTemplate(
        propagation = propagation,
        isolation = isolation,
        timeoutSeconds = timeoutSeconds,
        readOnly = readOnly
    )
    // If we're already in a transaction, just re-use it (no dispatcher switch needed).
    currentCoroutineContext()[TransactionKey]?.context?.let {
        return suspendExecute(transactionTemplate, it, block)
    }
    val newContext = transactionTemplate.newContext(true)
    return withContext(context +
            TransactionKey(newContext) +
            TransactionContextHolder.current.asContextElement(newContext)) {
        suspendExecute(transactionTemplate, newContext, block)
    }
}

private object TransactionContextHolder {
    val current = ThreadLocal<TransactionContext?>()
}

private class TransactionKey(val context: TransactionContext) :
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TransactionKey>
}

private fun getTransactionTemplate(
    propagation: TransactionPropagation = TransactionPropagation.REQUIRED,
    isolation: TransactionIsolation?    = null,
    timeoutSeconds: Int?                = null,
    readOnly: Boolean                   = false
) = getTransactionTemplate().apply {
    propagation(propagation.toString())
    isolation?.let {
        isolation(when (it) {
            READ_UNCOMMITTED -> TRANSACTION_READ_UNCOMMITTED
            READ_COMMITTED   -> TRANSACTION_READ_COMMITTED
            REPEATABLE_READ  -> TRANSACTION_REPEATABLE_READ
            SERIALIZABLE     -> TRANSACTION_SERIALIZABLE
        })
    }
    timeoutSeconds?.let { timeout(it) }
    readOnly(readOnly)
}

private fun <T> execute(
    transactionTemplate: TransactionTemplate,
    transactionContext: TransactionContext,
    block: Transaction.() -> T
): T {
    return transactionTemplate.execute({ status ->
        val tx = object : Transaction {
            override val isRollbackOnly: Boolean
                get() = status.isRollbackOnly
            override fun setRollbackOnly() {
                status.setRollbackOnly()
            }
        }
        block(tx)
    }, transactionContext)
}

private fun <T> suspendExecute(
    transactionTemplate: TransactionTemplate,
    transactionContext: TransactionContext,
    block: suspend Transaction.() -> T
): T {
    @Suppress("UNCHECKED_CAST")
    return transactionTemplate.execute({ status ->
        val tx = object : Transaction {
            override val isRollbackOnly: Boolean
                get() = status.isRollbackOnly
            override fun setRollbackOnly() {
                status.setRollbackOnly()
            }
        }
        runBlocking { block(tx) }
    }, transactionContext)
}
