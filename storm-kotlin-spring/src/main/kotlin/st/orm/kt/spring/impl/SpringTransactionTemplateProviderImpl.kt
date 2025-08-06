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
package st.orm.kt.spring.impl

import org.springframework.transaction.TransactionDefinition.*
import org.springframework.transaction.support.DefaultTransactionDefinition
import st.orm.PersistenceException
import st.orm.core.spi.Orderable.BeforeAny
import st.orm.core.spi.TransactionCallback
import st.orm.core.spi.TransactionContext
import st.orm.core.spi.TransactionTemplate
import st.orm.core.spi.TransactionTemplateProvider
import st.orm.kt.spring.SpringTransactionConfiguration
import java.util.*
import java.util.Optional.ofNullable

/**
 * Transaction template integration for the Spring framework.
 *
 * @since 1.5
 */
@BeforeAny
class SpringTransactionTemplateProviderImpl : TransactionTemplateProvider {
    override fun isEnabled(): Boolean =
        SpringTransactionConfiguration.transactionManagers.isNotEmpty()

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

            override fun newContext(suspendMode: Boolean): TransactionContext {
                if (suspendMode) {
                    throw PersistenceException("Suspend mode is not supported when spring-managed transactions are " +
                            "enabled for storm. Remove `@EnableTransactionIntegration` to disable spring-" +
                            "managed transactions for storm.")
                }
                return SpringTransactionContext()
            }

            override fun currentContext(): Optional<TransactionContext> {
                return ofNullable(SpringTransactionContext.current())
            }

            override fun <T> execute(callback: TransactionCallback<T>, context: TransactionContext): T {
                if (context !is SpringTransactionContext) {
                    throw PersistenceException("Transaction context must be of type SpringTransactionContext.")
                }
                return context.execute(definition, callback)
            }
        }
    }
}
