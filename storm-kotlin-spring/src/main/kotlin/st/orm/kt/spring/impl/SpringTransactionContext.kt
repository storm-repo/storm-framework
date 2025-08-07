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

    // Coroutine-based scheduler for transaction timeouts.
    private val timeoutScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val currentState: TransactionState
        get() = stack.lastOrNull() ?: throw IllegalStateException("No transaction active.")

    private fun setRollbackOnly() {
        val state = currentState
        state.rollbackOnly = true
        state.transactionStatus?.setRollbackOnly()
    }

    private val isRollbackOnly: Boolean get() =
        currentState.rollbackOnly || currentState.transactionStatus?.isRollbackOnly ?: false

    override fun <T : Any> getDecorator(resourceType: Class<T>): TransactionContext.Decorator<T> {
        if (resourceType != PreparedStatement::class.java) {
            return TransactionContext.Decorator<T> { obj -> obj }   // No-op.
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

    /**
     * Executes the provided callback within a transaction boundary.
     *
     * @param definition The transaction definition to use.
     * @param callback The transactional operation to execute.
     * @return The result of the callback execution.
     * @param T The type of result returned by the callback.
     */
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
            rollback()
            when (e.cause) {
                is SQLTimeoutException -> {
                    // TimeoutJob may not have registered timeout yet.
                    throw TransactionTimedOutException(e.message ?: "Transaction did not complete within timeout.", e)
                }
                else -> throw e
            }
        }
        if (isRollbackOnly) {
            rollback()
        } else {
            commit()
        }
        return result as T
    }

    /**
     * Associates a data source with the current transaction context.
     * This method is typically called by the ConnectionProvider before obtaining a JDBC Connection.
     *
     * @param dataSource The data source to associate with the current transaction
     */
    fun useDataSource(dataSource: DataSource) {
        if (currentState.dataSource == null) {
            for (i in stack.indices) {
                startTransactionIfNecessary(stack[i], dataSource, i)
            }
        } else {
            if (currentState.dataSource !== dataSource) {
                throw IllegalStateException(
                    "Incompatible DataSource detected: $dataSource but already using ${currentState.dataSource}."
                )
            }
        }
    }

    /**
     * Resolves the appropriate transaction manager for the given data source.
     *
     * @param dataSource The data source to find a transaction manager for.
     * @return The resolved PlatformTransactionManager.
     */
    private fun resolveTransactionManager(dataSource: DataSource): PlatformTransactionManager =
        SpringTransactionConfiguration.transactionManagers
            .mapNotNull { it as? DataSourceTransactionManager }
            .firstOrNull { it.dataSource === dataSource }
            ?: throw IllegalStateException("No TransactionManager found for DataSource $dataSource.")

    /**
     * Starts a new transaction if necessary based on the current state and isolation level.
     *
     * @param state The current transaction state.
     * @param dataSource The data source to use.
     * @param level The transaction isolation level.
     */
    private fun startTransactionIfNecessary(state: TransactionState, dataSource: DataSource, level: Int) {
        assert(state.transactionDefinition != null)
        if (state.dataSource != null) {
            assert(state.transactionManager != null)
            assert(state.transactionStatus != null)
            if (state.dataSource != dataSource) {
                throw IllegalStateException(
                    "Incompatible DataSource detected: $dataSource but already using ${state.dataSource}."
                )
            }
            return
        }
        if (level > 0) {
            startTransactionIfNecessary(state, dataSource, level - 1)
            val outerTransaction = stack[level - 1]
            state.dataSource = outerTransaction.dataSource!!
            state.transactionManager = outerTransaction.transactionManager!!
        } else {
            // This is the outermost transaction, so we need to set up the transaction manager.
            state.dataSource = dataSource
            state.transactionManager = resolveTransactionManager(dataSource)
        }
        // Spring will handle transaction definition.
        val transactionStatus = state.transactionManager!!.getTransaction(state.transactionDefinition)
        state.transactionStatus = transactionStatus
        // Schedule rollback on timeout if requested.
        state.transactionDefinition?.timeout?.let { seconds ->
            if (seconds >= 0) {
                state.timeoutJob = timeoutScope.launch {
                    delay(seconds * 1000L)
                    state.timedOut = true
                    state.rollbackOnly = true
                }
            }
        }
    }

    /**
     * Commit the current (top-of-stack) transaction and pop it. All resources are cleared if this was the last
     * transaction.
     */
    private fun commit() {
        val state = popState()
        // Cancel any pending timeout watcher
        state.timeoutJob?.cancel()
        try {
            state.transactionStatus?.let { state.transactionManager!!.commit(it) }
        } catch (e: org.springframework.transaction.TransactionTimedOutException) {
            throw TransactionTimedOutException(e.message ?: "Transaction did not complete within timeout.")
        } catch (e: Exception) {
            throw PersistenceException(e)
        }
    }

    /**
     * Rollback the current (top-of-stack) transaction and pop it. All resources are cleared if this was the last
     * transaction.
     */
    private fun rollback() {
        val state = popState()
        // Cancel any pending timeout watcher
        state.timeoutJob?.let { job ->
            when {
                job.isCompleted && !job.isCancelled -> {
                    state.timedOut = true   // Timeout detected.
                }
                job.isActive -> job.cancel()
            }
        }
        try {
            state.transactionStatus?.let { state.transactionManager!!.rollback(it) }
        } catch (e: org.springframework.transaction.TransactionTimedOutException) {
            throw TransactionTimedOutException(e.message ?: "Transaction did not complete within timeout.")
        } catch (e: Exception) {
            throw PersistenceException(e)
        }
        if (state.timedOut) {
            throw TransactionTimedOutException("Transaction did not complete within timeout (${state.transactionDefinition!!.timeout}s).")
        }
    }

    private fun popState(): TransactionState {
        if (stack.isNotEmpty()) {
            return stack.removeAt(stack.lastIndex)
        } else {
            throw IllegalStateException("No transaction in progress to commit/rollback.")
        }
    }
}