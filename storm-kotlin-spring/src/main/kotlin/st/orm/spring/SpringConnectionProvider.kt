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

import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.jdbc.datasource.DataSourceUtils
import st.orm.PersistenceException
import st.orm.core.spi.ConnectionProvider
import st.orm.core.spi.TransactionContext
import st.orm.spring.impl.SpringTransactionContext
import java.sql.Connection
import javax.sql.DataSource

/**
 * Connection provider that binds connections to Spring's transaction management.
 *
 * Connections are acquired through [DataSourceUtils], so statements executed by the template participate in
 * Spring-managed (`@Transactional`) transactions via thread-bound connections. When Storm's own `transaction { }`
 * API drives the transaction, the active [SpringTransactionContext] is bound to the data source before the
 * connection is acquired, which starts the Spring transaction through the matching `PlatformTransactionManager`.
 *
 * Configure this provider on the template that belongs to the owning application context, together with
 * [SpringTransactionTemplateProvider]; see [springOrmTemplate].
 *
 * @since 1.13
 */
class SpringConnectionProvider : ConnectionProvider {

    override fun getConnection(dataSource: DataSource, context: TransactionContext?): Connection {
        if (context != null) {
            require(context is SpringTransactionContext) { "Transaction context must be of type SpringTransactionContext." }
            context.useDataSource(dataSource)
        }
        try {
            return DataSourceUtils.getConnection(dataSource)
        } catch (e: CannotGetJdbcConnectionException) {
            throw PersistenceException("Failed to get connection from DataSource.", e)
        }
    }

    override fun releaseConnection(connection: Connection, dataSource: DataSource, context: TransactionContext?) {
        if (context != null) {
            require(context is SpringTransactionContext) { "Transaction context must be of type SpringTransactionContext." }
        }
        DataSourceUtils.releaseConnection(connection, dataSource)
    }
}
