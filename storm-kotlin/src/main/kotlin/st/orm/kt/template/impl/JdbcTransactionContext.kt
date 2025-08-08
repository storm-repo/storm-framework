package st.orm.kt.template.impl

import kotlinx.coroutines.*
import st.orm.PersistenceException
import st.orm.core.spi.TransactionCallback
import st.orm.core.spi.TransactionContext
import st.orm.core.spi.TransactionStatus
import st.orm.kt.template.TransactionPropagation
import st.orm.kt.template.TransactionPropagation.*
import st.orm.kt.template.TransactionTimedOutException
import java.sql.*
import javax.sql.DataSource

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
     * @property timeoutJob Job for transaction timeout watcher.
     * @property timedOut Indicates if transaction has timed out.
     */
    internal data class TransactionState(
        val propagation: TransactionPropagation = REQUIRED,
        val isolationLevel: Int?                = null,
        val timeoutSeconds: Int?                = null,
        val readOnly: Boolean?                  = null,
        var dataSource: DataSource?             = null,
        var connection: Connection?             = null,
        var ownsConnection: Boolean             = false,
        var originalIsolationLevel: Int?        = null,
        var originalReadOnly: Boolean?          = null,
        var savepoint: Savepoint?               = null,
        var rollbackOnly: Boolean               = false,
        var suspendedConnection: Connection?    = null,
        var suspendedDataSource: DataSource?    = null,
        var suspended: Boolean                  = false,
        var timeoutJob: Job?                    = null,
        var timedOut: Boolean                   = false
    )

    private val stack = mutableListOf<TransactionState>()

    // Coroutine-based scheduler for transaction timeouts.
    private val timeoutScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val currentState: TransactionState
        get() = stack.lastOrNull() ?: throw IllegalStateException("No transaction active.")

    /**
     * Obtains a JDBC Connection for the current transaction.
     *
     * Creates a new connection or reuses existing one based on transaction propagation rules.
     *
     * @param dataSource DataSource to get connection from
     * @return JDBC Connection
     * @throws PersistenceException if connection cannot be obtained
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

    override fun <T : Any> getDecorator(resourceType: Class<T>): TransactionContext.Decorator<T> {
        if (resourceType != PreparedStatement::class.java) {
            return TransactionContext.Decorator<T> { obj -> obj }   // No-op.
        }
        return TransactionContext.Decorator { resource ->
            val statement = resource as PreparedStatement
            val timeout = currentState.timeoutSeconds
            if (timeout != null && timeout > 0) {
                statement.queryTimeout = timeout
            }
            resource
        }
    }

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
        propagation: TransactionPropagation = REQUIRED,
        isolation: Int? = null,
        timeoutSeconds: Int? = null,
        readOnly: Boolean? = null,
        callback: TransactionCallback<T>
    ): T {
        val state = TransactionState(propagation, isolation, timeoutSeconds, readOnly)
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
            rollback(suppressException = true)  // Suppress any rollback exception to ensure handling of e.
            when (e.cause) {
                is SQLTimeoutException -> {
                    // TimeoutJob may not have registered timeout yet.
                    throw TransactionTimedOutException(e.message ?: "Transaction did not complete within timeout.", e)
                }
                else -> throw e
            }
        }
        if (state.rollbackOnly) {
            rollback()
        } else {
            commit()
        }
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
        currentState.rollbackOnly = true
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
                            openConnection(state, dataSource)   // Non-transactional.
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
                        openConnection(state, dataSource)
                    }
                    NEVER -> {
                        if (outer?.connection != null) {
                            throw PersistenceException("Existing transaction found for NEVER propagation.")
                        }
                        openConnection(state, dataSource)   // Non-transactional.
                    }
                    NESTED -> {
                        if (outer?.connection != null) {
                            if (outer.dataSource != dataSource) {
                                throw PersistenceException(
                                    "Incompatible DataSource: expected ${outer.dataSource}, got $dataSource."
                                )
                            }
                            state.connection = outer.connection
                            state.dataSource = dataSource
                            state.savepoint = outer.connection!!.setSavepoint()
                            state.ownsConnection = false
                            state.originalIsolationLevel = outer.connection!!.transactionIsolation
                            state.originalReadOnly = outer.connection!!.isReadOnly
                            state.isolationLevel?.let { outer.connection!!.transactionIsolation = it }
                            state.readOnly?.let { outer.connection!!.isReadOnly = it }
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
        val state = popState()
        // Cancel any pending timeout watcher
        state.timeoutJob?.cancel()
        val connection = state.connection
            ?: return
        try {
            when {
                state.savepoint != null -> {
                    connection.releaseSavepoint(state.savepoint)
                }
                state.ownsConnection -> {
                    connection.commit()
                    connection.autoCommit = true
                    connection.close()
                }
                else -> {
                    // Joined REQUIRED: Commiting joined transaction, which means we're doing nothing yet.
                }
            }
        } catch (e: SQLException) {
            throw PersistenceException("Commit failed.", e)
        }
        if (stack.isEmpty()) clear(state)
    }

    /**
     * Roll back the current transaction frame.
     */
    private fun rollback(suppressException: Boolean = false) {
        val state = popState()
        // Cancel any pending timeout watcher.
        state.timeoutJob?.let { job ->
            when {
                job.isCompleted && !job.isCancelled -> {
                    state.timedOut = true   // Timeout detected.
                }
                job.isActive -> job.cancel()
            }
        }
        val connection = state.connection
            ?: return
        try {
            when {
                state.savepoint != null -> {
                    connection.rollback(state.savepoint)
                    // Restore prior settings.
                    state.originalIsolationLevel?.let { connection.transactionIsolation = it }
                    state.originalReadOnly?.let { connection.isReadOnly = it }
                }
                state.ownsConnection -> {
                    connection.rollback()
                    connection.autoCommit = true
                    connection.close()
                }
                else -> {
                    // Joined REQUIRED: mark outer for rollback and restore prior settings.
                    stack.lastOrNull()?.rollbackOnly = true
                }
            }
        } catch (e: SQLException) {
            if (!suppressException) {
                throw PersistenceException("Rollback failed.", e)
            }
        }
        if (stack.isEmpty()) clear(state)
        if (!suppressException && state.timedOut) {
            throw TransactionTimedOutException("Transaction did not complete within timeout (${state.timeoutSeconds}s).")
        }
    }

    private fun popState(): TransactionState {
        if (stack.isEmpty()) throw IllegalStateException("No transaction in active.")
        return stack.removeAt(stack.lastIndex)
    }

    private fun clear(state: TransactionState) {
        // Restore prior settings.
        state.originalIsolationLevel?.let { state.connection!!.transactionIsolation = it }
        state.originalReadOnly?.let { state.connection!!.isReadOnly = it }
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
            val connection = dataSource.connection
            if (!connection.autoCommit) {
                throw PersistenceException("Connection returned from DataSource must be in auto-commit mode.")
            }
            state.isolationLevel?.let { connection.transactionIsolation = it }
            state.readOnly?.let { connection.isReadOnly = it }
            connection.autoCommit = false
            state.connection = connection
            state.dataSource = dataSource
            state.ownsConnection = true
            // Schedule rollback on timeout if requested.
            state.timeoutSeconds?.let { seconds ->
                state.timeoutJob = timeoutScope.launch {
                    delay(seconds * 1000L)
                    state.timedOut = true
                    state.rollbackOnly = true
                }
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
            val connection = dataSource.connection.apply { autoCommit = true }
            state.connection = connection
            state.dataSource = dataSource
            state.ownsConnection = true
        }
    }

    /**
     * Suspend an outer transaction on this state.
     */
    private fun suspendTransaction(state: TransactionState, outer: TransactionState) {
        state.suspendedConnection = outer.connection
        state.suspendedDataSource = outer.dataSource
        state.suspended = true
    }

    /**
     * Join an existing transaction.
     */
    private fun joinOuterTransaction(state: TransactionState, outer: TransactionState) {
        state.connection = outer.connection
        state.dataSource = outer.dataSource
        state.ownsConnection = false
        state.originalIsolationLevel = outer.connection!!.transactionIsolation
        state.originalReadOnly = outer.connection!!.isReadOnly
        state.isolationLevel?.let { outer.connection!!.transactionIsolation = it }
        state.readOnly?.let { outer.connection!!.isReadOnly = it }
    }
}