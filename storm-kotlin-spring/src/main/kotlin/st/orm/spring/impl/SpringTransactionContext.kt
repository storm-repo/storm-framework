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
package st.orm.spring.impl

import org.slf4j.LoggerFactory
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionDefinition.*
import org.springframework.transaction.TransactionStatus
import st.orm.Entity
import st.orm.PersistenceException
import st.orm.core.spi.EntityCache
import st.orm.core.spi.EntityCacheImpl
import st.orm.core.spi.TransactionCallback
import st.orm.core.spi.TransactionContext
import st.orm.spring.SpringTransactionConfiguration
import st.orm.template.TransactionTimedOutException
import st.orm.template.UnexpectedRollbackException
import java.sql.Connection.*
import java.sql.PreparedStatement
import java.sql.SQLTimeoutException
import javax.sql.DataSource
import kotlin.math.min
import kotlin.reflect.KClass

/**
 * Internal implementation of transaction context management for Spring-managed transactions.
 * This class provides thread-local transaction context tracking and management capabilities.
 *
 * The class maintains a stack of transaction states for nested transaction support and
 * integrates with Spring's transaction management infrastructure.
 *
 * Key features:
 * - Thread-local transaction context tracking
 * - Support for nested transactions
 * - Integration with Spring's PlatformTransactionManager
 * - Transaction state management including rollback flags
 *
 * Example usage:
 * ```
 * val context = SpringTransactionContext()
 * context.execute(definition) { status ->
 *     // Execute transactional operations
 * }
 * ```
 *
 * @property stack Maintains the stack of transaction states for nested transaction support
 * @property currentState Provides access to the current transaction state or throws if no transaction is active
 * 
 * @see TransactionContext
 * @see PlatformTransactionManager
 * @since 1.5
 */
internal class SpringTransactionContext : TransactionContext {
    companion object {
        val logger = LoggerFactory.getLogger("st.orm.transaction")
    }

    /**
     * Internal class representing the state of a single transaction.
     *
     * @property transactionStatus The Spring transaction status.
     * @property transactionManager The platform transaction manager handling this transaction.
     * @property dataSource The data source associated with this transaction.
     * @property transactionDefinition The definition of this transaction.
     * @property rollbackOnly Indicates if the transaction should be marked as rollback-only.
     * @property timeoutSeconds The timeout in seconds for this transaction.
     * @property deadlineNanos The deadline in nanoseconds for this transaction.
     */
    private data class TransactionState(
        var transactionStatus: TransactionStatus? = null,
        var transactionManager: PlatformTransactionManager? = null,
        var dataSource: DataSource? = null,
        var transactionDefinition: TransactionDefinition? = null,
        var rollbackOnly: Boolean = false,
        var timeoutSeconds: Int? = null,
        var deadlineNanos: Long? = null,

        // NOTE:
        // - Joined REQUIRED/SUPPORTS/MANDATORY frames share the same map instance for identity stability.
        // - NESTED shares the outer map too (same physical transaction). On nested rollback, we clear the outer map.
        // - REQUIRES_NEW/NOT_SUPPORTED/NEVER keep their own map (separate physical transaction / non-tx boundary).
        var entityCacheMap: MutableMap<KClass<*>, EntityCache<*, *>> = mutableMapOf()
    )

    private val stack = mutableListOf<TransactionState>()

    private val currentState: TransactionState
        get() = stack.lastOrNull() ?: throw IllegalStateException("No transaction active.")

    private fun nowNanos(): Long = System.nanoTime()

    private fun Int.toDeadlineFromNowNanos(): Long =
        nowNanos() + this.toLong() * 1_000_000_000L

    private fun TransactionState.remainingSeconds(): Int? =
        deadlineNanos?.let { dl ->
            val rem = dl - nowNanos()
            if (rem <= 0L) 0 else (rem / 1_000_000_000L).toInt()
        }

    private fun isBoundary(propagation: Int?): Boolean =
        when (propagation) {
            PROPAGATION_REQUIRES_NEW,
            PROPAGATION_NESTED,
            PROPAGATION_NOT_SUPPORTED,
            PROPAGATION_NEVER -> true
            else -> false
        }

    /**
     * Returns the index of the owner of the current physical transaction range for stack[index].
     * Walks outward until just after a boundary (or to 0).
     */
    private fun ownerIndexFor(index: Int): Int {
        var i = index
        while (i > 0) {
            val prev = stack[i - 1]
            if (isBoundary(prev.transactionDefinition?.propagationBehavior)) break
            i--
        }
        return i
    }

    /**
     * Finds an already-known DataSource in range, ensuring consistency.
     */
    private fun findDataSourceInRange(startIdx: Int, endIdx: Int): DataSource? {
        var dataSource: DataSource? = null
        for (i in startIdx..endIdx) {
            val stackDataSource = stack[i].dataSource
            if (stackDataSource != null) {
                if (dataSource == null) dataSource = stackDataSource
                else if (dataSource !== stackDataSource) {
                    throw IllegalStateException(
                        "Incompatible DataSource detected within the same transaction range: $dataSource vs $stackDataSource"
                    )
                }
            }
        }
        return dataSource
    }

    /**
     * Ensures Spring TransactionStatus exists for frames in range using [dataSource].
     * Applies any pending rollback flags immediately.
     */
    private fun ensureStartedInRange(startIndex: Int, endIndex: Int, dataSource: DataSource) {
        for (index in startIndex..endIndex) {
            val state = stack[index]
            if (state.dataSource == null) {
                state.dataSource = dataSource
            }
            startTransactionIfNecessary(state, dataSource, index)
            if (state.rollbackOnly) {
                state.transactionStatus?.setRollbackOnly()
            }
        }
    }

    /**
     * Returns true if the transaction is marked as read-only, false otherwise.
     *
     * @return true if the transaction is marked as read-only, false otherwise.
     * @since 1.7
     */
    override fun isReadOnly(): Boolean =
        currentState.transactionDefinition?.isReadOnly ?: false

    /**
     * Returns true if the transaction has repeatable-read semantics.
     *
     * This is true when:
     * - The isolation level is `REPEATABLE_READ` or higher, or
     * - The transaction is read-only (can't see changes since you can't make any)
     *
     * When true, cached entities are returned when re-reading the same entity, preserving
     * entity identity. When false, fresh data is fetched from the database.
     *
     * Spring uses ISOLATION_DEFAULT (-1) when no specific isolation level is set; in that case, we assume
     * the database default is sufficient and return true.
     */
    override fun isRepeatableRead(): Boolean {
        // Read-only transactions have repeatable-read semantics
        if (isReadOnly()) return true
        val isolationLevel = currentState.transactionDefinition?.isolationLevel ?: return true
        // Spring uses ISOLATION_DEFAULT (-1) when no specific isolation level is set.
        if (isolationLevel < 0) return true
        return isolationLevel >= TRANSACTION_REPEATABLE_READ
    }

    /**
     * Returns a transaction-local cache for entities of the given type, keyed by primary key.
     *
     * The cache is used for dirty checking and/or identity preservation. Whether cached instances are
     * returned during reads is controlled by [isRepeatableRead].
     */
    override fun entityCache(entityType: Class<out Entity<*>>): EntityCache<out Entity<*>, *> {
        @Suppress("UNCHECKED_CAST")
        return currentState.entityCacheMap.getOrPut(entityType.kotlin) {
            EntityCacheImpl<Entity<Any>, Any>()
        } as EntityCache<Entity<*>, *>
    }

    /**
     * Clears all entity caches associated with this transaction context.
     */
    override fun clearAllEntityCaches() {
        currentState.entityCacheMap.values.forEach { it.clear() }
    }

    /**
     * Gets the decorator for the specified resource type.
     *
     * @param resourceType the resource type.
     * @return the decorator.
     * @param <T> the resource type.
     */
    override fun <T : Any> getDecorator(resourceType: Class<T>): TransactionContext.Decorator<T> {
        if (resourceType != PreparedStatement::class.java) {
            return TransactionContext.Decorator<T> { obj -> obj } // No-op.
        }
        return TransactionContext.Decorator { resource ->
            val preparedStatement = resource as PreparedStatement
            // Dynamic remaining time; fall back to static definition timeout.
            val remaining = currentState.remainingSeconds()
            val seconds = when {
                remaining != null && remaining > 0 -> remaining
                remaining != null && remaining <= 0 -> 1
                else -> currentState.timeoutSeconds
            }
            if (seconds != null && seconds > 0) {
                preparedStatement.queryTimeout = seconds
            }
            resource
        }
    }

    fun <T> execute(
        definition: TransactionDefinition,
        callback: TransactionCallback<T>
    ): T {
        val state = TransactionState(
            transactionDefinition = definition,
            timeoutSeconds = definition.timeout.takeIf { it > 0 }
        ).apply {
            deadlineNanos = timeoutSeconds?.toDeadlineFromNowNanos()
        }
        with(state.transactionDefinition!!) {
            logger.debug(
                """
                    Starting transaction:
                        propagation: ${
                    when (propagationBehavior) {
                        PROPAGATION_REQUIRED -> "REQUIRED"
                        PROPAGATION_REQUIRES_NEW -> "REQUIRES NEW"
                        PROPAGATION_SUPPORTS -> "SUPPORTS"
                        PROPAGATION_MANDATORY -> "MANDATORY"
                        PROPAGATION_NOT_SUPPORTED -> "NOT SUPPORTED"
                        PROPAGATION_NEVER -> "NEVER"
                        PROPAGATION_NESTED -> "NESTED"
                        else -> "UNKNOWN ($propagationBehavior)"
                    }
                }
                        isolation: ${
                    when (isolationLevel) {
                        TRANSACTION_NONE -> "NONE"
                        TRANSACTION_READ_UNCOMMITTED -> "READ_UNCOMMITTED"
                        TRANSACTION_READ_COMMITTED -> "READ_COMMITTED"
                        TRANSACTION_REPEATABLE_READ -> "REPEATABLE_READ"
                        TRANSACTION_SERIALIZABLE -> "SERIALIZABLE"
                        else -> "UNKNOWN (${state.transactionDefinition!!.isolationLevel})"
                    }
                }
                        timeout: ${if (timeout == -1) "<no timeout>" else "$timeout second(s)"}
                        readOnly: ${state.transactionDefinition!!.isReadOnly}
                """.trimIndent()
            )
        }
        stack.add(state)
        val result = try {
            callback.doInTransaction(object : st.orm.core.spi.TransactionStatus {
                override fun isRollbackOnly(): Boolean = this@SpringTransactionContext.isRollbackOnly
                override fun setRollbackOnly() {
                    this@SpringTransactionContext.setRollbackOnly()
                }
            })
        } catch (e: Throwable) {
            // Let Spring roll back if a transaction was actually started for this frame.
            rollback()
            when (e.cause) {
                is SQLTimeoutException ->
                    throw TransactionTimedOutException(e.message ?: "Did not complete within timeout.", e)
                else -> throw e
            }
        }
        commit()
        return result as T
    }

    /**
     * Called by the ConnectionProvider before obtaining a JDBC Connection.
     */
    fun useDataSource(dataSource: DataSource) {
        val index = stack.lastIndex
        if (index < 0) throw IllegalStateException("No transaction active.")
        val startIndex = ownerIndexFor(index)
        val existingDataSource = findDataSourceInRange(startIndex, index)
        if (existingDataSource != null && existingDataSource !== dataSource) {
            throw IllegalStateException(
                "Incompatible DataSource detected: $dataSource but already using $existingDataSource."
            )
        }
        ensureStartedInRange(startIndex, index, dataSource)
    }

    private val isRollbackOnly: Boolean
        get() = (currentState.rollbackOnly || (currentState.transactionStatus?.isRollbackOnly ?: false))

    private fun setRollbackOnly() {
        val index = stack.lastIndex
        if (index < 0) throw IllegalStateException("No transaction active.")
        val startIndex = ownerIndexFor(index)
        val dataSource = findDataSourceInRange(startIndex, index)
        if (dataSource != null) {
            ensureStartedInRange(startIndex, index, dataSource)  // Starts statuses and calls setRollbackOnly() where needed.
        }
        val currentState = stack[index]
        currentState.rollbackOnly = true
        currentState.transactionStatus?.setRollbackOnly()
    }

    private fun resolveTransactionManager(dataSource: DataSource): PlatformTransactionManager =
        SpringTransactionConfiguration.transactionManagers
            .mapNotNull { it as? DataSourceTransactionManager }
            .firstOrNull { it.dataSource === dataSource }
            ?: throw IllegalStateException("No TransactionManager found for DataSource $dataSource.")

    /**
     * Starts Spring TransactionStatus for [state] if not already started.
     *
     * For inner frames, reuses outer's manager where appropriate. We will still ask the transaction manager
     * for a status using this frame's definition to honor propagation semantics.
     *
     * Also enforces entity cache sharing policy:
     * - REQUIRED/SUPPORTS/MANDATORY share outer cache
     * - NESTED shares outer cache (same physical tx)
     * - REQUIRES_NEW/NOT_SUPPORTED/NEVER use their own cache
     */
    private fun startTransactionIfNecessary(state: TransactionState, dataSource: DataSource, level: Int) {
        requireNotNull(state.transactionDefinition) { "TransactionDefinition must not be null." }
        if (state.dataSource != null && state.transactionManager != null && state.transactionStatus != null) {
            if (state.dataSource !== dataSource) {
                throw IllegalStateException(
                    "Incompatible DataSource detected: $dataSource but already using ${state.dataSource}."
                )
            }
            return
        }
        if (level > 0) {
            startTransactionIfNecessary(stack[level - 1], dataSource, level - 1)
            val outer = stack[level - 1]
            state.dataSource = outer.dataSource ?: dataSource
            state.transactionManager = outer.transactionManager ?: resolveTransactionManager(dataSource)
            // Cache sharing policy.
            when (state.transactionDefinition!!.propagationBehavior) {
                PROPAGATION_REQUIRED,
                PROPAGATION_SUPPORTS,
                PROPAGATION_MANDATORY,
                PROPAGATION_NESTED -> {
                    state.entityCacheMap = outer.entityCacheMap
                }
                // REQUIRES_NEW / NOT_SUPPORTED / NEVER keep their own map.
            }
            // Reconcile deadlines: inner deadline = min(outer, requested).
            val requested = state.timeoutSeconds?.toDeadlineFromNowNanos()
            state.deadlineNanos = when {
                outer.deadlineNanos == null && requested == null -> null
                outer.deadlineNanos == null -> requested
                requested == null -> outer.deadlineNanos
                else -> min(outer.deadlineNanos!!, requested)
            }
        } else {
            state.dataSource = dataSource
            state.transactionManager = resolveTransactionManager(dataSource)
            // Root: deadline already set in execute(); keep it.
        }
        val transactionStatus = state.transactionManager!!.getTransaction(state.transactionDefinition)
        state.transactionStatus = transactionStatus
        if (state.rollbackOnly) transactionStatus.setRollbackOnly()
    }

    private fun commit() {
        // If current state is marked rollbackOnly or expired, go to rollback path.
        currentState.let { state ->
            val expired = state.deadlineNanos?.let { nowNanos() >= it } == true
            if (state.rollbackOnly || expired) {
                rollback()
                return
            }
        }
        val state = popState()
        // If this frame never touched a DataSource/started a status, still enforce timeout deterministically.
        if (state.transactionStatus == null) {
            val expiredAfter = state.deadlineNanos?.let { nowNanos() >= it } == true
            if (expiredAfter) {
                throw TransactionTimedOutException(
                    "Transaction did not complete within timeout (${state.timeoutSeconds}s)."
                )
            }
            return
        }
        try {
            state.transactionManager!!.commit(state.transactionStatus!!)
        } catch (e: org.springframework.transaction.TransactionTimedOutException) {
            throw TransactionTimedOutException(e.message ?: "Did not complete within timeout.")
        } catch (e: org.springframework.transaction.UnexpectedRollbackException) {
            // If Spring threw UR because some inner joined frame marked RO, surface a clean message
            throw UnexpectedRollbackException(
                e.message ?: "Transaction was marked rollback-only by a joined scope.",
                e
            )
        } catch (e: Exception) {
            throw PersistenceException(e)
        }
    }

    private fun rollback() {
        val state = popState()
        // If status never started, just check deadline and throw appropriately.
        if (state.transactionStatus == null) {
            val expired = state.deadlineNanos?.let { nowNanos() >= it } == true
            if (expired) {
                throw TransactionTimedOutException(
                    "Did not complete within timeout (${state.timeoutSeconds}s)."
                )
            }
            return
        }
        try {
            state.transactionManager!!.rollback(state.transactionStatus!!)
        } catch (e: org.springframework.transaction.TransactionTimedOutException) {
            throw TransactionTimedOutException(e.message ?: "Did not complete within timeout.")
        } catch (e: Exception) {
            throw PersistenceException(e)
        }
        // If this frame was NESTED, Spring rolled back to a savepoint.
        // We shared the cache map with the outer scope, so it may now be stale relative to DB state.
        if (state.transactionDefinition?.propagationBehavior == PROPAGATION_NESTED) {
            stack.lastOrNull()?.entityCacheMap?.clear()
        }
        val expired = state.deadlineNanos?.let { nowNanos() >= it } == true
        if (expired) {
            throw TransactionTimedOutException(
                "Did not complete within timeout (${state.timeoutSeconds}s)."
            )
        }
    }

    private fun popState(): TransactionState {
        if (stack.isEmpty()) throw IllegalStateException("No transaction in progress to commit/rollback.")
        return stack.removeAt(stack.lastIndex)
    }
}