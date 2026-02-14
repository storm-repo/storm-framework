package st.orm.template.impl

import st.orm.PersistenceException
import st.orm.core.spi.TransactionCallback
import st.orm.core.spi.TransactionContext
import st.orm.core.spi.TransactionTemplate
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

            override fun newContext(suspendMode: Boolean): TransactionContext = // Suspend mode is supported in this implementation.
                JdbcTransactionContext()

            override fun contextHolder(): ThreadLocal<TransactionContext> = CONTEXT_HOLDER

            override fun <T> execute(callback: TransactionCallback<T>, context: TransactionContext): T {
                if (context !is JdbcTransactionContext) {
                    throw PersistenceException("Transaction context must be of type JdbcTransactionContext.")
                }
                return context.execute(propagation, isolation, timeoutSeconds, readOnly, callback)
            }
        }
    }
}
