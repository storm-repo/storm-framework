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
package st.orm.core.spi;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import st.orm.PersistenceException;
import st.orm.core.spi.Orderable.AfterAny;

/**
 * Default connection provider that acquires and closes connections directly on the data source.
 *
 * <p>This provider is platform-neutral: it never participates in externally managed transactions. Integrations that
 * bind connections to a transaction subsystem supply their own {@link ConnectionProvider} via the template
 * builder.</p>
 */
@AfterAny
public class DefaultConnectionProviderImpl implements ConnectionProvider {

    @Override
    public Connection getConnection(@Nonnull DataSource dataSource, @Nullable TransactionContext context) {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to get connection from DataSource.", e);
        }
    }

    @Override
    public void releaseConnection(@Nonnull Connection connection, @Nonnull DataSource dataSource, @Nullable TransactionContext context) {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to release connection.", e);
        }
    }
}
