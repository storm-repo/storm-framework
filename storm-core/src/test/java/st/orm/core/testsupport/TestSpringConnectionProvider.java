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
package st.orm.core.testsupport;

import java.sql.Connection;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.datasource.DataSourceUtils;
import st.orm.PersistenceException;
import st.orm.core.spi.ConnectionProvider;
import st.orm.core.spi.Orderable.BeforeAny;
import st.orm.core.spi.TransactionContext;

/**
 * Test-only connection provider that binds connections to Spring's transaction management.
 *
 * <p>The storm-core test suite runs under Spring's test framework ({@code @DataJpaTest}), which wraps each test in a
 * transaction that is rolled back afterwards. Templates created with {@code ORMTemplate.of(dataSource)} pick up this
 * provider through the {@code ServiceLoader} fallback, so their statements join the test-managed transaction and the
 * shared database stays clean between tests.</p>
 */
@BeforeAny
public class TestSpringConnectionProvider implements ConnectionProvider {

    @Override
    public Connection getConnection(DataSource dataSource, @Nullable TransactionContext context) {
        try {
            return DataSourceUtils.getConnection(dataSource);
        } catch (Exception e) {
            throw new PersistenceException("Failed to get connection from DataSource.", e);
        }
    }

    @Override
    public void releaseConnection(Connection connection, DataSource dataSource, @Nullable TransactionContext context) {
        DataSourceUtils.releaseConnection(connection, dataSource);
    }
}
