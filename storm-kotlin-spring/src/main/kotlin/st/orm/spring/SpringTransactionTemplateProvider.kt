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
package st.orm.spring

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition.*
import org.springframework.transaction.support.DefaultTransactionDefinition
import st.orm.PersistenceException
import st.orm.core.spi.TransactionContext
import st.orm.core.spi.TransactionStatus
import st.orm.core.spi.TransactionTemplate
import st.orm.core.spi.TransactionTemplate.TransactionHandle
import st.orm.core.spi.TransactionTemplateProvider
import st.orm.spring.impl.SpringTransactionContext

/**
 * Transaction template provider that bridges Storm's transaction API into Spring's
 * `PlatformTransactionManager`.
 *
 * The provider is constructed with the transaction managers of the owning application context; the matching
 * `DataSourceTransactionManager` is resolved lazily, when the first data source touches the transaction. Templates
 * that should share transactions must be configured with the *same provider instance*, so integrations expose one
 * provider per application context; see [springOrmTemplate].
 *
 * @param transactionManagers supplies the transaction managers of the owning application context; resolved lazily
 * on first use.
 * @since 1.13
 */
class SpringTransactionTemplateProvider(
    private val transactionManagers: () -> List<PlatformTransactionManager>,
) : TransactionTemplateProvider {

    /**
     * Creates a provider for an eagerly resolved list of transaction managers.
     */
    constructor(transactionManagers: List<PlatformTransactionManager>) : this({ transactionManagers })

    private val contextHolder = ThreadLocal<TransactionContext>()

    /**
     * Obtains a new transaction template instance.
     *
     * @return a new transaction template instance.
     */
    override fun getTransactionTemplate(): TransactionTemplate {
        return object : TransactionTemplate {
            private val definition = DefaultTransactionDefinition()

            override fun propagation(propagation: String): TransactionTemplate {
                definition.propagationBehavior = when (propagation) {
                    "REQUIRED" -> PROPAGATION_REQUIRED
                    "SUPPORTS" -> PROPAGATION_SUPPORTS
                    "MANDATORY" -> PROPAGATION_MANDATORY
                    "REQUIRES_NEW" -> PROPAGATION_REQUIRES_NEW
                    "NOT_SUPPORTED" -> PROPAGATION_NOT_SUPPORTED
                    "NEVER" -> PROPAGATION_NEVER
                    "NESTED" -> PROPAGATION_NESTED
                    else -> throw IllegalArgumentException("Unknown propagation type: $propagation")
                }
                return this
            }

            override fun isolation(isolation: Int): TransactionTemplate {
                definition.isolationLevel = isolation
                return this
            }

            override fun timeout(timeout: Int): TransactionTemplate {
                definition.timeout = timeout
                return this
            }

            override fun readOnly(readOnly: Boolean): TransactionTemplate {
                definition.isReadOnly = readOnly
                return this
            }

            override fun open(existing: TransactionContext?, suspendMode: Boolean): TransactionHandle {
                if (suspendMode) {
                    throw PersistenceException(
                        "Suspend mode is not supported with Spring-managed transactions. Use " +
                            "transactionBlocking { } instead, or configure the template without the Spring " +
                            "transaction template provider.",
                    )
                }
                val context = when (existing) {
                    null -> SpringTransactionContext(transactionManagers)
                    is SpringTransactionContext -> existing
                    else -> throw PersistenceException("Transaction context must be of type SpringTransactionContext.")
                }
                context.begin(definition)
                return object : TransactionHandle {
                    override fun context(): TransactionContext = context

                    override fun status(): TransactionStatus = object : TransactionStatus {
                        override fun setRollbackOnly() = context.setRollbackOnly()

                        override fun isRollbackOnly(): Boolean = context.isRollbackOnly
                    }

                    override fun complete(rollback: Boolean) = context.complete(rollback)
                }
            }

            override fun contextHolder(): ThreadLocal<TransactionContext> = contextHolder
        }
    }
}
