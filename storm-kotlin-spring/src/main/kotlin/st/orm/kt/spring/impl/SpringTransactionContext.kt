package st.orm.kt.spring.impl

import kotlinx.coroutines.*
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import st.orm.PersistenceException
import st.orm.core.spi.TransactionCallback
import st.orm.core.spi.TransactionContext
import st.orm.kt.spring.SpringTransactionConfiguration
import st.orm.kt.template.TransactionTimedOutException
import st.orm.kt.template.UnexpectedRollbackException
import java.sql.PreparedStatement
import java.sql.SQLTimeoutException
import javax.sql.DataSource

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
    /**
     * Internal class representing the state of a single transaction.
     *
     * @property transactionStatus The Spring transaction status
     * @property transactionManager The platform transaction manager handling this transaction
     * @property dataSource The data source associated with this transaction
     * @property transactionDefinition The definition of this transaction
     */
    private data class TransactionState(
        var transactionStatus: TransactionStatus?            = null,
        var transactionManager: PlatformTransactionManager?  = null,
        var dataSource: DataSource?                          = null,
        var transactionDefinition: TransactionDefinition?    = null,
        var rollbackOnly: Boolean                            = false,
        var timeoutJob: Job?                                 = null,
        var timedOut: Boolean                                = false
    )

    private val stack = mutableListOf<TransactionState>()

    // Coroutine-based scheduler for timeouts.
    private val timeoutScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val currentState: TransactionState
        get() = stack.lastOrNull() ?: throw IllegalStateException("No transaction active.")


    /**
     * Returns true if propagation is a boundary.
     */
    private fun isBoundary(propagation: Int?): Boolean =
        when (propagation) {
            TransactionDefinition.PROPAGATION_REQUIRES_NEW,
            TransactionDefinition.PROPAGATION_NESTED,
            TransactionDefinition.PROPAGATION_NOT_SUPPORTED,
            TransactionDefinition.PROPAGATION_NEVER -> true
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

    override fun <T : Any> getDecorator(resourceType: Class<T>): TransactionContext.Decorator<T> {
        if (resourceType != PreparedStatement::class.java) {
            return TransactionContext.Decorator<T> { obj -> obj } // No-op.
        }
        return TransactionContext.Decorator { resource ->
            val statement = resource as PreparedStatement
            val timeout = currentState.transactionDefinition!!.timeout
            if (timeout > 0) {
                statement.queryTimeout = timeout
            }
            resource
        }
    }

    fun <T> execute(
        definition: TransactionDefinition,
        callback: TransactionCallback<T>
    ): T {
        val state = TransactionState(transactionDefinition = definition)
        stack.add(state)
        val result = try {
            callback.doInTransaction(object : st.orm.core.spi.TransactionStatus {
                override fun isRollbackOnly(): Boolean =
                    this@SpringTransactionContext.isRollbackOnly

                override fun setRollbackOnly() {
                    this@SpringTransactionContext.setRollbackOnly()
                }
            })
        } catch (e: Throwable) {
            // Let Spring roll back if a transaction was actually started for this frame.
            rollback()
            when (e.cause) {
                is SQLTimeoutException ->
                    throw TransactionTimedOutException(e.message ?: "Transaction did not complete within timeout.", e)
                else -> throw e
            }
        }
        // Always delegate to commit() so Spring can detect/throw on global rollback-only.
        commit()
        return result as T
    }

    /**
     * Called by the ConnectionProvider before obtaining a JDBC Connection.
     * Attaches [dataSource] to the current range (owner..current), starts Spring statuses,
     * and applies any pending flags.
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
            // Ensure outer is initialized first for consistent manager usage.
            startTransactionIfNecessary(stack[level - 1], dataSource, level - 1)
            val outer = stack[level - 1]
            state.dataSource = outer.dataSource ?: dataSource
            state.transactionManager = outer.transactionManager ?: resolveTransactionManager(dataSource)
        } else {
            state.dataSource = dataSource
            state.transactionManager = resolveTransactionManager(dataSource)
        }
        val transactionStatus = state.transactionManager!!.getTransaction(state.transactionDefinition)
        state.transactionStatus = transactionStatus
        // Apply pending flags immediately.
        if (state.rollbackOnly) transactionStatus.setRollbackOnly()
        // Schedule timeout, marking intent but not forcing DS init elsewhere.
        state.transactionDefinition?.timeout?.let { seconds ->
            if (seconds >= 0) {
                state.timeoutJob = timeoutScope.launch {
                    delay(seconds * 1000L)
                    state.timedOut = true
                    state.rollbackOnly = true
                    // If a status already exists (this frame was started), reflect now.
                    state.transactionStatus?.setRollbackOnly()
                }
            }
        }
    }

    private fun commit() {
        currentState.let { state ->
            // Cancel any pending timeout watcher.
            state.timeoutJob?.let { job ->
                when {
                    job.isCompleted && !job.isCancelled -> state.timedOut = true
                    job.isActive -> job.cancel()
                }
            }
            if (state.rollbackOnly || state.timedOut) {
                rollback()
                return
            }
        }
        val state = popState()
        try {
            state.transactionStatus?.let { st ->
                state.transactionManager!!.commit(st)
            }
        } catch (e: org.springframework.transaction.TransactionTimedOutException) {
            throw TransactionTimedOutException(e.message ?: "Transaction did not complete within timeout.")
        } catch (e: org.springframework.transaction.UnexpectedRollbackException) {
            if (state.timedOut) {
                throw TransactionTimedOutException(
                    "Transaction did not complete within timeout (${state.transactionDefinition?.timeout}s)."
                )
            }
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
        // Cancel any pending timeout watcher.
        state.timeoutJob?.let { job ->
            when {
                job.isCompleted && !job.isCancelled -> state.timedOut = true
                job.isActive -> job.cancel()
            }
        }
        try {
            state.transactionStatus?.let { st ->
                state.transactionManager!!.rollback(st)
            }
        } catch (e: org.springframework.transaction.TransactionTimedOutException) {
            throw TransactionTimedOutException(e.message ?: "Transaction did not complete within timeout.")
        } catch (e: Exception) {
            throw PersistenceException(e)
        }
        if (state.timedOut) {
            throw TransactionTimedOutException(
                "Transaction did not complete within timeout (${state.transactionDefinition!!.timeout}s)."
            )
        }
    }

    private fun popState(): TransactionState {
        if (stack.isEmpty()) throw IllegalStateException("No transaction in progress to commit/rollback.")
        return stack.removeAt(stack.lastIndex)
    }
}