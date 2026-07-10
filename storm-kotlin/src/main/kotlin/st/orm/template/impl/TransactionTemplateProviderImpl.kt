package st.orm.template.impl

import st.orm.PersistenceException
import st.orm.core.spi.TransactionContext
import st.orm.core.spi.TransactionStatus
import st.orm.core.spi.TransactionTemplate
import st.orm.core.spi.TransactionTemplate.TransactionHandle
import st.orm.core.spi.TransactionTemplateProvider
import st.orm.template.TransactionPropagation.*

class TransactionTemplateProviderImpl : TransactionTemplateProvider {
    companion object {
        private val CONTEXT_HOLDER = ThreadLocal<TransactionContext>()
    }

    override fun getTransactionTemplate(): TransactionTemplate {
        return object : TransactionTemplate {
            private var propagation = REQUIRED
            private var isolation: Int? = null
            private var timeoutSeconds: Int? = null
            private var readOnly = false

            override fun propagation(propagation: String): TransactionTemplate {
                this.propagation = when (propagation) {
                    "REQUIRED" -> REQUIRED
                    "SUPPORTS" -> SUPPORTS
                    "MANDATORY" -> MANDATORY
                    "REQUIRES_NEW" -> REQUIRES_NEW
                    "NOT_SUPPORTED" -> NOT_SUPPORTED
                    "NEVER" -> NEVER
                    "NESTED" -> NESTED
                    else -> throw IllegalArgumentException("Unknown propagation: $propagation.")
                }
                return this
            }

            override fun isolation(isolation: Int): TransactionTemplate {
                this.isolation = isolation
                return this
            }

            override fun readOnly(readOnly: Boolean): TransactionTemplate {
                this.readOnly = readOnly
                return this
            }

            override fun timeout(timeoutSeconds: Int): TransactionTemplate {
                this.timeoutSeconds = timeoutSeconds
                return this
            }

            override fun open(existing: TransactionContext?, suspendMode: Boolean): TransactionHandle {
                // Suspend mode is supported by this implementation; the JDBC context binds state to the context
                // object rather than the thread.
                val context = when (existing) {
                    null -> JdbcTransactionContext()
                    is JdbcTransactionContext -> existing
                    else -> throw PersistenceException("Transaction context must be of type JdbcTransactionContext.")
                }
                context.begin(propagation, isolation, timeoutSeconds, readOnly)
                return object : TransactionHandle {
                    override fun context(): TransactionContext = context

                    override fun status(): TransactionStatus = object : TransactionStatus {
                        override fun setRollbackOnly() = context.setRollbackOnly()

                        override fun isRollbackOnly(): Boolean = context.isRollbackOnly
                    }

                    override fun complete(rollback: Boolean) = context.complete(rollback)
                }
            }

            override fun contextHolder(): ThreadLocal<TransactionContext> = CONTEXT_HOLDER
        }
    }
}
