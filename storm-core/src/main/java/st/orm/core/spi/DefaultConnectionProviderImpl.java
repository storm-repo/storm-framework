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
package st.orm.core.spi;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.PersistenceException;
import st.orm.core.spi.Orderable.AfterAny;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;

@AfterAny
public class DefaultConnectionProviderImpl implements ConnectionProvider {

    private static final Method GET_CONNECTION_METHOD;
    private static final Method RELEASE_CONNECTION_METHOD;

    static {
        Method getConnection;
        Method releaseConnection;
        try {
            Class<?> utilsClass = Class.forName("org.springframework.jdbc.datasource.DataSourceUtils");
            getConnection = utilsClass.getMethod("getConnection", DataSource.class);
            releaseConnection = utilsClass.getMethod("releaseConnection", Connection.class, DataSource.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            getConnection = null;
            releaseConnection = null;
        }
        GET_CONNECTION_METHOD = getConnection;
        RELEASE_CONNECTION_METHOD = releaseConnection;
    }

    @Override
    public Connection getConnection(@Nonnull DataSource dataSource, @Nullable TransactionContext context) {
        try {
            if (GET_CONNECTION_METHOD != null) {
                try {
                    return (Connection) GET_CONNECTION_METHOD.invoke(null, dataSource);
                } catch (InvocationTargetException e) {
                    throw e.getTargetException();
                }
            } else {
                return dataSource.getConnection();
            }
        } catch (Throwable t) {
            throw new PersistenceException("Failed to get connection from DataSource.", t);
        }
    }

    @Override
    public void releaseConnection(@Nonnull Connection connection, @Nonnull DataSource dataSource, @Nullable TransactionContext context) {
        try {
            if (RELEASE_CONNECTION_METHOD != null) {
                try {
                    RELEASE_CONNECTION_METHOD.invoke(null, connection, dataSource);
                } catch (InvocationTargetException e) {
                    throw e.getTargetException();
                }
            } else {
                connection.close();
            }
        } catch (Throwable t) {
            throw new PersistenceException("Failed to release connection.", t);
        }
    }
}

