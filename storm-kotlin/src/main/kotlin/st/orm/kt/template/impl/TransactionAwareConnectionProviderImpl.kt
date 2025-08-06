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
package st.orm.kt.template.impl

import st.orm.PersistenceException
import st.orm.core.spi.ConnectionProvider
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.sql.Connection
import javax.sql.DataSource

/**
 * @since 1.5
 */
class TransactionAwareConnectionProviderImpl : ConnectionProvider {

    override fun getConnection(dataSource: DataSource): Connection {
        val context = JdbcTransactionContext.current()
        if (context != null) {
            validateState()
            return context.getConnection(dataSource)
        }
        // If no programmatic transaction is active, obtain a new connection from the data source.
        return getRegularConnection(dataSource)
    }

    override fun releaseConnection(connection: Connection, dataSource: DataSource) {
        val context = JdbcTransactionContext.current()
        if (context != null) {
            if (context.currentConnection() == connection) {
                // If this connection is the current transaction connection, do not close it. It will be closed when the
                // outermost transaction ends.
                return
            }
        }
        releaseRegularConnection(connection, dataSource)
    }

    private fun validateState() {
        try {
            if (IS_ACTUAL_TRANSACTION_ACTIVE != null) {
                try {
                    if (IS_ACTUAL_TRANSACTION_ACTIVE.invoke(null) as Boolean) {
                        throw PersistenceException(
                            "Programmatic transactions and Spring managed transactions cannot be mixed when spring-managed transactions are disabled for storm. " +
                                    "Use `@EnableTransactionIntegration` to enable spring-managed transactions for storm."
                        )
                    }
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                }
            }
        } catch (e: PersistenceException) {
            throw e
        } catch (t: Throwable) {
            throw PersistenceException("Failed to validate connection.", t)
        }
    }

    private fun getRegularConnection(dataSource: DataSource): Connection {
        try {
            return if (GET_CONNECTION_METHOD != null) {
                try {
                    GET_CONNECTION_METHOD.invoke(null, dataSource) as Connection
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                }
            } else {
                dataSource.connection
            }
        } catch (t: Throwable) {
            throw PersistenceException("Failed to get connection from DataSource.", t)
        }
    }

    private fun releaseRegularConnection(connection: Connection, dataSource: DataSource) {
        try {
            if (RELEASE_CONNECTION_METHOD != null) {
                try {
                    RELEASE_CONNECTION_METHOD.invoke(null, connection, dataSource)
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                }
            } else {
                connection.close()
            }
        } catch (t: Throwable) {
            throw PersistenceException("Failed to release connection.", t)
        }
    }

    companion object {
        private val GET_CONNECTION_METHOD: Method?
        private val RELEASE_CONNECTION_METHOD: Method?
        private val IS_ACTUAL_TRANSACTION_ACTIVE: Method?

        init {
            var getConnection: Method?
            var releaseConnection: Method?
            var isActualTransactionActive: Method?
            try {
                val utilsClass = Class.forName("org.springframework.jdbc.datasource.DataSourceUtils")
                val transactionSynchonizationManagerClass =
                    Class.forName("org.springframework.transaction.support.TransactionSynchronizationManager")
                getConnection = utilsClass.getMethod("getConnection", DataSource::class.java)
                releaseConnection =
                    utilsClass.getMethod("releaseConnection", Connection::class.java, DataSource::class.java)
                isActualTransactionActive = transactionSynchonizationManagerClass.getMethod("isActualTransactionActive")
            } catch (_: ClassNotFoundException) {
                getConnection = null
                releaseConnection = null
                isActualTransactionActive = null
            } catch (_: NoSuchMethodException) {
                getConnection = null
                releaseConnection = null
                isActualTransactionActive = null
            }
            GET_CONNECTION_METHOD = getConnection
            RELEASE_CONNECTION_METHOD = releaseConnection
            IS_ACTUAL_TRANSACTION_ACTIVE = isActualTransactionActive
        }
    }
}
