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
package st.orm.spring.impl;

import jakarta.annotation.Nonnull;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import st.orm.PersistenceException;
import st.orm.core.spi.ConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;

public class TransactionAwareConnectionProviderImpl implements ConnectionProvider {

    @Override
    public Connection getConnection(@Nonnull DataSource dataSource) {
        try {
            return DataSourceUtils.getConnection(dataSource);
        } catch (CannotGetJdbcConnectionException e) {
            throw new PersistenceException("Failed to get connection from DataSource.", e);
        }
    }

    @Override
    public void releaseConnection(@Nonnull Connection connection, @Nonnull DataSource dataSource) {
        DataSourceUtils.releaseConnection(connection, dataSource);
    }
}
