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

import org.slf4j.LoggerFactory
import st.orm.Entity
import st.orm.PersistenceException
import st.orm.core.spi.*
import st.orm.template.TransactionPropagation
import st.orm.template.TransactionPropagation.*
import st.orm.template.TransactionTimedOutException
import st.orm.template.UnexpectedRollbackException
import java.sql.*
import java.sql.Connection.*
import java.util.*
import javax.sql.DataSource
import kotlin.reflect.KClass

/**
 * Minimum transaction isolation level required for entity caching to be enabled.
 *
 * Transactions with an isolation level below this threshold will not use entity caching, which means dirty checking
 * will treat all entities as dirty (resulting in full-row updates). This prevents the entity cache from masking
 * changes that the application expects to see at lower isolation levels.
 *
 * The default value is [TRANSACTION_READ_COMMITTED], meaning entity caching is disabled only for
 * `READ_UNCOMMITTED` transactions. This can be overridden using the system property
 * `storm.entityCache.minIsolationLevel`.
 *
 * Valid values: `NONE`, `READ_UNCOMMITTED`, `READ_COMMITTED`, `REPEATABLE_READ`, `SERIALIZABLE`, or the
 * corresponding JDBC integer constants (0, 1, 2, 4, 8).
 */
private val MIN_ISOLATION_LEVEL_FOR_CACHE: Int = run {
    val value = System.getProperty("storm.entityCache.minIsolationLevel")?.trim()?.uppercase()
    when {
        value.isNullOrBlank() -> TRANSACTION_READ_COMMITTED
        value == "NONE" || value == "0" -> TRANSACTION_NONE
        value == "READ_UNCOMMITTED" || value == "1" -> TRANSACTION_READ_UNCOMMITTED
        value == "READ_COMMITTED" || value == "2" -> TRANSACTION_READ_COMMITTED
        value == "REPEATABLE_READ" || value == "4" -> TRANSACTION_REPEATABLE_READ
        value == "SERIALIZABLE" || value == "8" -> TRANSACTION_SERIALIZABLE
        else -> value.toIntOrNull()
            ?: throw PersistenceException("Invalid value for storm.entityCache.minIsolationLevel: '$value'.")
    }
}

/**
 * A JDBC transaction context implementation that provides lightweight transaction management based on JDBC.
 * This supports various transaction propagation behaviors.
 *
 * Key features:
 * - Supports all transaction propagation modes
 * - Manages transaction isolation levels and read-only settings
 * - Handles connection lifecycle and savepoint management
 * - Provides thread-safe transaction context tracking supporting coroutines
 *
 * Transaction State Management:
 * - Maintains a stack of transaction states per thread
 * - Each state tracks connection, savepoint, and transaction attributes
 * - Supports nested transactions through savepoints
 * - Handles connection suspension and resumption
 *
 * Usage example:
 * ```
 * val txContext = JdbcTransactionContext()
 * txContext.execute(propagation = REQUIRED) {
 *     // Transaction block
 *     // Operations performed here will be in a transaction
 * }
 * ```
 *
 * @property stack Maintains the transaction state stack for the current thread
 * @see TransactionPropagation for supported transaction propagation modes
 * @see TransactionState for details on tracked transaction attributes
 * @since 1.5
 */
internal class JdbcTransactionContext : TransactionContext {
    companion object {
        val logger = LoggerFactory.getLogger("st.orm.transaction")!!
    }

    /**
     * Internal data class representing the state of a transaction frame.
     * Each transaction operation creates a new state that tracks various
     * transaction attributes and resources.
     *
     * @property propagation Transaction propagation behavior (defaults to REQUIRED).
     * @property isolationLevel Transaction isolation level (nullable).
     * @property readOnly Transaction read-only flag (nullable).
     * @property originalIsolationLevel Original connection isolation level.
     * @property originalReadOnly Original connection read-only setting.
     * @property connection Active JDBC connection.
     * @property savepoint Active savepoint for nested transactions.
     * @property dataSource Associated DataSource.
     * @property ownsConnection Indicates if this state owns the connection.
     * @property rollbackOnly Marks transaction for rollback only.
     * @property suspendedConnection Stored connection when transaction is suspended.
     * @property suspendedDataSource Stored DataSource when transaction is suspended.
     * @property suspended Indicates if transaction is currently suspended.
     */
    internal data class TransactionState(
        val propagation: TransactionPropagation = REQUIRED,
        val isolationLevel: Int? = null,
        val timeoutSeconds: Int? = null,
        val readOnly: Boolean? = null,
        var dataSource: DataSource? = null,
        var connection: Connection? = null,
        var ownsConnection: Boolean = false,
        var originalIsolationLevel: Int? = null,
        var originalReadOnly: Boolean? = null,
        var savepoint: Savepoint? = null,
        var rollbackOnly: Boolean = false,
        var rollbackInherited: Boolean = false,
        var suspendedConnection: Connection? = null,
        var suspendedDataSource: DataSource? = null,
        var suspended: Boolean = false,
        var deadlineNanos: Long? = null,
        val transactionId: String = UUID.randomUUID().toString(),
        // NOTE:
        // - Joined REQUIRED/SUPPORTS/MANDATORY frames share the same map instance for identity stability.
        // - NESTED also shares the map (same connection), but on savepoint rollback we clear it to avoid stale state.
        var entityCacheMap: MutableMap<KClass<*>, EntityCache<*, *>> = mutableMapOf()
    )

    private fun nowNanos(): Long = System.nanoTime()

    private fun TransactionState.remainingSeconds(): Int? =
        deadlineNanos?.let { deadline ->
            val remaining = deadline - nowNanos()
            when {
                remaining <= 0L -> 0
                remaining >= (Int.MAX_VALUE.toLong() * 1_000_000_000L) -> Int.MAX_VALUE
                else -> (remaining / 1_000_000_000L).toInt()
            }
        }

    private val stack = mutableListOf<TransactionState>()

    private val currentState: TransactionState
        get() = stack.lastOrNull() ?: throw IllegalStateException("No transaction active.")

    /**
     * Obtains a JDBC Connection for the current transaction.
     *
     * Creates a new connection or reuses existing one based on transaction propagation rules.
     *
     * @param dataSource DataSource to get connection from.
     * @return JDBC Connection.
     * @throws PersistenceException if connection cannot be obtained.
     */
    fun getConnection(dataSource: DataSource): Connection {
        useDataSource(dataSource)
        return currentState.connection!!
    }

    /**
     * Returns the current active Connection if a transaction exists.
     *
     * @return Current Connection or null if no transaction is active
     */
    fun currentConnection(): Connection? = stack.lastOrNull()?.connection

    /**
     * Returns true if the transaction is marked as read-only, false otherwise.
     *
     * @return true if the transaction is marked as read-only, false otherwise.
     * @since 1.7
     */
    override fun isReadOnly(): Boolean =
        currentState.readOnly ?: false

    /**
     * Returns a transaction-local cache for entities of the given type, keyed by primary key.
     *
     * Returns `null` if the transaction's isolation level is below the configured minimum for entity caching
     * (see [MIN_ISOLATION_LEVEL_FOR_CACHE]). At low isolation levels, entity caching is disabled to prevent
     * the cache from masking changes that the application expects to see.
     */
    override fun entityCache(entityType: Class<out Entity<*>>): EntityCache<out Entity<*>, *>? {
        val isolationLevel = currentState.isolationLevel
        if (isolationLevel != null && isolationLevel < MIN_ISOLATION_LEVEL_FOR_CACHE) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return currentState.entityCacheMap.getOrPut(entityType.kotlin) {
            EntityCacheImpl<Entity<Any>, Any>()
        } as EntityCache<Entity<*>, *>
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
            // Prefer dynamic remaining time; fall back to static seconds if present.
            val remaining = currentState.remainingSeconds()
            val seconds = when {
                remaining != null && remaining > 0 -> remaining
                remaining != null && remaining <= 0 -> 1 // Already out of time: force a fast timeout.
                else -> currentState.timeoutSeconds
            }
            if (seconds != null && seconds > 0) {
                preparedStatement.queryTimeout = seconds
            }
            resource
        }
    }

    private fun Int.toDeadlineFromNowNanos(): Long = System.nanoTime() + this.toLong() * 1_000_000_000L

    /**
     * Executes a transaction block with specified attributes.
     *
     * @param propagation Transaction propagation behavior
     * @param isolation Transaction isolation level
     * @param readOnly Transaction read-only setting
     * @param callback Transaction operation to execute
     * @return Result of the transaction operation
     * @throws PersistenceException on transaction or database errors
     */
    fun <T> execute(
        propagation: TransactionPropagation,
        isolation: Int?,
        timeoutSeconds: Int?,
        readOnly: Boolean?,
        callback: TransactionCallback<T>
    ): T {
        val state = TransactionState(propagation, isolation, timeoutSeconds, readOnly)
        logger.debug(
            """
                Starting transaction (${state.transactionId}):
                    propagation: $propagation
                    isolation: ${
                when (isolation) {
                    TRANSACTION_NONE -> "NONE"
                    TRANSACTION_READ_UNCOMMITTED -> "READ_UNCOMMITTED"
                    TRANSACTION_READ_COMMITTED -> "READ_COMMITTED"
                    TRANSACTION_REPEATABLE_READ -> "REPEATABLE_READ"
                    TRANSACTION_SERIALIZABLE -> "SERIALIZABLE"
                    null -> "DEFAULT"
                    else -> "UNKNOWN ($isolation)"
                }
            }
                    timeout: ${if (timeoutSeconds == null) "<no timeout>" else "$timeoutSeconds second(s)"}
                    readOnly: $readOnly
            """.trimIndent()
        )
        state.deadlineNanos = timeoutSeconds?.toDeadlineFromNowNanos()
        stack.add(state)
        val result = try {
            callback.doInTransaction(object : TransactionStatus {
                override fun isRollbackOnly(): Boolean =
                    this@JdbcTransactionContext.isRollbackOnly

                override fun setRollbackOnly() {
                    this@JdbcTransactionContext.setRollbackOnly()
                }
            })
        } catch (e: Throwable) {
            logger.trace("Transaction failed (${state.transactionId}).", e)
            rollback(suppressException = true) // Suppress any rollback exception to ensure handling of exception.
            when (e.cause) {
                is SQLTimeoutException -> {
                    // TimeoutJob may not have registered timeout yet.
                    throw TransactionTimedOutException(
                        e.message ?: "Did not complete within timeout (${state.timeoutSeconds}s).",
                        e
                    )
                }
                else -> throw e
            }
        }
        // Let commit detect timeout or rollback-only.
        commit()
        return result as T
    }

    /**
     * Check if the current transaction is marked for rollback-only.
     */
    private val isRollbackOnly: Boolean
        get() = currentState.rollbackOnly

    /**
     * Mark the current transaction so that it will roll back on completion.
     */
    private fun setRollbackOnly() {
        val lastIndex = stack.lastIndex
        val currentState = stack[lastIndex]
        logger.debug("Marking transaction for rollback (${currentState.transactionId}).")
        currentState.rollbackOnly = true
        currentState.rollbackInherited = false // Reset inherited flag, if applicable.
        // Do NOT propagate from NESTED (savepoint) scopes and do NOT propagate from owners (outermost or REQUIRES_NEW).
        if (currentState.savepoint != null || currentState.ownsConnection) return
        // Propagate to outer joined frames up to (and including) the owning frame, but stop at a savepoint boundary.
        for (i in lastIndex - 1 downTo 0) {
            val state = stack[i]
            if (state.savepoint != null) break // Do not cross NESTED boundary.
            state.rollbackOnly = true
            state.rollbackInherited = true // Indicates caller-triggered.
            if (state.ownsConnection) break // Stop at the owner (could be REQUIRES_NEW).
        }
    }

    private fun useDataSource(dataSource: DataSource) {
        for (i in stack.indices) {
            val state = stack[i]
            if (state.connection == null) {
                val outer = stack.getOrNull(i - 1)
                when (state.propagation) {
                    REQUIRED -> {
                        if (outer?.connection != null) {
                            joinOuterTransaction(state, outer)
                        } else {
                            openNewTransaction(state, dataSource)
                        }
                    }
                    SUPPORTS -> {
                        if (outer?.connection != null) {
                            joinOuterTransaction(state, outer)
                        } else {
                            openConnection(state, dataSource) // Non-transactional.
                        }
                    }
                    MANDATORY -> {
                        if (outer?.connection != null) {
                            joinOuterTransaction(state, outer)
                        } else {
                            throw PersistenceException("No existing transaction for MANDATORY propagation.")
                        }
                    }
                    REQUIRES_NEW -> {
                        if (outer?.connection != null) {
                            suspendTransaction(state, outer)
                        }
                        openNewTransaction(state, dataSource)
                    }
                    NOT_SUPPORTED -> {
                        if (outer?.connection != null) {
                            suspendTransaction(state, outer)
                        }
                        openConnection(state, dataSource) // Non-transactional.
                    }
                    NEVER -> {
                        if (outer?.connection != null) {
                            throw PersistenceException("Existing transaction found for NEVER propagation.")
                        }
                        openConnection(state, dataSource) // Non-transactional.
                    }
                    NESTED -> {
                        if (outer?.connection != null) {
                            if (outer.dataSource != dataSource) {
                                throw PersistenceException(
                                    "Incompatible DataSource: expected ${outer.dataSource}, got $dataSource."
                                )
                            }
                            val savepoint = outer.connection!!.setSavepoint()
                            logger.debug(
                                "Creating nested transaction with savepoint {} on {} ({}).",
                                savepoint,
                                outer.connection!!,
                                state.transactionId
                            )
                            state.connection = outer.connection
                            state.dataSource = dataSource
                            state.savepoint = savepoint
                            state.ownsConnection = false
                            // Share caches since this is the same JDBC connection/transaction.
                            // If we roll back to this savepoint, we will clear the shared cache to avoid stale state.
                            state.entityCacheMap = outer.entityCacheMap
                            state.originalIsolationLevel = outer.connection!!.transactionIsolation
                            state.originalReadOnly = outer.connection!!.isReadOnly
                            val innerRequested = state.timeoutSeconds?.let { nowNanos() + it * 1_000_000_000L }
                            state.deadlineNanos = when {
                                outer.deadlineNanos == null && innerRequested == null -> null
                                outer.deadlineNanos == null -> innerRequested
                                innerRequested == null -> outer.deadlineNanos
                                else -> minOf(outer.deadlineNanos!!, innerRequested)
                            }
                        } else {
                            openNewTransaction(state, dataSource)
                        }
                    }
                }
            } else {
                // Already bound: sanity-check data source.
                if (state.dataSource !== dataSource) {
                    throw PersistenceException(
                        "Incompatible DataSource: $dataSource but already using ${state.dataSource}."
                    )
                }
            }
        }
    }

    /**
     * Commit the current transaction block. If nested, release savepoint; if outermost (or REQUIRES_NEW), commit and
     * close; if joined REQUIRED, just restore settings.
     */
    private fun commit() {
        currentState.let { state ->
            val expired = state.deadlineNanos?.let { nowNanos() >= it } == true
            if (state.rollbackOnly || expired) {
                rollback()
                return
            }
        }
        val state = popState()
        val connection = state.connection
        // If no connection was ever used, still enforce timeout deterministically.
        if (connection == null) {
            val expiredAfter = state.deadlineNanos?.let { nowNanos() >= it } == true
            if (expiredAfter) {
                throw TransactionTimedOutException(
                    "Did not complete within timeout (${state.timeoutSeconds}s)."
                )
            }
            return
        }
        try {
            when {
                state.savepoint != null -> {
                    logger.debug(
                        "Committing nested scope; releasing savepoint {} on {} ({}).",
                        state.savepoint,
                        connection,
                        state.transactionId
                    )
                    connection.releaseSavepoint(state.savepoint)
                }
                state.ownsConnection -> {
                    logger.debug("Committing transaction on {} ({}).", connection, state.transactionId)
                    if (!connection.autoCommit) connection.commit()
                    close(connection, state)
                }
                else -> {
                    // joined REQUIRED: nothing to do yet.
                }
            }
        } catch (e: SQLException) {
            throw PersistenceException("Commit failed.", e)
        }
    }

    /**
     * Roll back the current transaction frame.
     */
    private fun rollback(suppressException: Boolean = false) {
        val state = popState()
        val expired = state.deadlineNanos?.let { nowNanos() >= it } == true
        val connection = state.connection
        try {
            when {
                state.savepoint != null && connection != null -> {
                    logger.debug(
                        "Rolling back to savepoint {} on {} ({}).",
                        state.savepoint,
                        connection,
                        state.transactionId
                    )
                    connection.rollback(state.savepoint)
                    // We shared the cache map with the outer scope. After savepoint rollback, cached entities can be
                    // inconsistent with the DB state. Invalidate the cache for the remainder of the outer transaction.
                    stack.lastOrNull()?.entityCacheMap?.clear()
                }
                state.ownsConnection && connection != null -> {
                    logger.debug("Rolling back transaction on {} ({}).", connection, state.transactionId)
                    if (!connection.autoCommit) connection.rollback()
                    close(connection, state)
                }
                else -> {
                    // Joined REQUIRED or non-transactional scope (no connection):
                    logger.debug("Marking transaction for rollback (${state.transactionId}).")
                    // Propagate to outer joined frames up to (and including) the owning frame, but stop at a savepoint boundary.
                    val lastIndex = stack.lastIndex
                    for (i in lastIndex downTo 0) {
                        val st = stack[i]
                        if (st.savepoint != null) break // Do not cross NESTED boundary.
                        st.rollbackOnly = true
                        st.rollbackInherited = true // Indicates caller-triggered.
                        if (st.ownsConnection) break // Stop at the owner (could be REQUIRES_NEW).
                    }
                }
            }
        } catch (e: SQLException) {
            if (!suppressException) throw PersistenceException("Rollback failed.", e)
        }
        if (!suppressException && expired) {
            throw TransactionTimedOutException(
                "Did not complete within timeout (${state.timeoutSeconds}s)."
            )
        }
        if (!suppressException && state.rollbackInherited) {
            throw UnexpectedRollbackException("Transaction was marked rollback-only by a joined scope.")
        }
    }

    private fun close(connection: Connection, state: TransactionState) {
        state.originalIsolationLevel?.let { connection.transactionIsolation = it }
        state.originalReadOnly?.let { connection.isReadOnly = it }
        connection.autoCommit = true
        connection.close()
    }

    private fun popState(): TransactionState {
        if (stack.isEmpty()) throw IllegalStateException("No transaction in active.")
        return stack.removeAt(stack.lastIndex)
    }

    /**
     * Open a fresh JDBC Connection for REQUIRED (when no outer) or REQUIRES_NEW.
     */
    private fun openNewTransaction(state: TransactionState, dataSource: DataSource) {
        // Synchronize on the TransactionState so that only one thread can initialize its connection.
        // Without this, two threads could race to assign different connections (or tx modes) to the
        // same state. Ensuring a single, consistent connection instance lets downstream logic
        // detect and fail fast on concurrent access within the same transaction.
        if (state.connection != null) return
        synchronized(state) {
            logger.trace("Opening new transaction (${state.transactionId}).")
            val connection = dataSource.connection
            logger.trace("Obtained connection {} ({}).", connection, state.transactionId)
            if (!connection.autoCommit) {
                throw PersistenceException("Connection returned from DataSource must be in auto-commit mode.")
            }
            state.originalIsolationLevel = connection.transactionIsolation
            state.originalReadOnly = connection.isReadOnly
            state.isolationLevel?.let { connection.transactionIsolation = it }
            state.readOnly?.let { connection.isReadOnly = it }
            connection.autoCommit = false
            state.connection = connection
            state.dataSource = dataSource
            state.ownsConnection = true
            if (state.deadlineNanos == null) {
                state.deadlineNanos = state.timeoutSeconds?.toDeadlineFromNowNanos()
            }
        }
    }

    /**
     * Open a non-transactional connection (auto-commit).
     */
    private fun openConnection(state: TransactionState, dataSource: DataSource) {
        // Synchronize on the TransactionState so that only one thread can initialize its connection.
        // Without this, two threads could race to assign different connections (or tx modes) to the
        // same state. Ensuring a single, consistent connection instance lets downstream logic
        // detect and fail fast on concurrent access within the same transaction.
        if (state.connection != null) return
        synchronized(state) {
            logger.trace("Opening connection (${state.transactionId}).")
            val connection = dataSource.connection.apply { autoCommit = true }
            logger.trace("Obtained connection {} ({}).", connection, state.transactionId)
            state.connection = connection
            state.dataSource = dataSource
            state.ownsConnection = true
            // Non-transactional: deadline is not meaningful (no commit), but decorator still uses remainingSeconds().
            state.deadlineNanos = state.timeoutSeconds?.let { nowNanos() + it * 1_000_000_000L }
        }
    }

    /**
     * Suspend an outer transaction on this state.
     */
    private fun suspendTransaction(state: TransactionState, outer: TransactionState) {
        logger.debug("Suspending transaction (${state.transactionId}).")
        state.suspendedConnection = outer.connection
        state.suspendedDataSource = outer.dataSource
        state.suspended = true
        // No deadline needed while suspended (no shared connection).
    }

    /**
     * Join an existing transaction.
     */
    private fun joinOuterTransaction(state: TransactionState, outer: TransactionState) {
        logger.debug("Joining transaction (${state.transactionId} -> ${outer.transactionId}).")
        val connection = outer.connection ?: throw PersistenceException("No outer connection to join.")
        state.connection = connection
        state.dataSource = outer.dataSource
        state.ownsConnection = false
        // Share the entity cache map with the transaction we joined.
        state.entityCacheMap = outer.entityCacheMap
        state.originalIsolationLevel = connection.transactionIsolation
        state.originalReadOnly = connection.isReadOnly
        val innerRequested = state.timeoutSeconds?.toDeadlineFromNowNanos()
        state.deadlineNanos = when {
            outer.deadlineNanos == null && innerRequested == null -> state.deadlineNanos // Could already be set by execute().
            outer.deadlineNanos == null -> innerRequested
            innerRequested == null -> outer.deadlineNanos
            else -> minOf(outer.deadlineNanos!!, innerRequested)
        }
    }
}