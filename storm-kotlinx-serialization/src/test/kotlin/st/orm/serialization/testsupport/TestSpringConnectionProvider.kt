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
package st.orm.serialization.testsupport

import org.springframework.jdbc.datasource.DataSourceUtils
import st.orm.PersistenceException
import st.orm.core.spi.ConnectionProvider
import st.orm.core.spi.Orderable.BeforeAny
import st.orm.core.spi.TransactionContext
import java.sql.Connection
import javax.sql.DataSource

/**
 * Test-only connection provider that binds connections to Spring's transaction management, so the test suite's
 * transaction-per-test isolation applies to statements executed by Storm templates.
 */
@BeforeAny
internal class TestSpringConnectionProvider : ConnectionProvider {

    override fun getConnection(dataSource: DataSource, context: TransactionContext?): Connection {
        try {
            return DataSourceUtils.getConnection(dataSource)
        } catch (e: Exception) {
            throw PersistenceException("Failed to get connection from DataSource.", e)
        }
    }

    override fun releaseConnection(connection: Connection, dataSource: DataSource, context: TransactionContext?) {
        DataSourceUtils.releaseConnection(connection, dataSource)
    }
}
