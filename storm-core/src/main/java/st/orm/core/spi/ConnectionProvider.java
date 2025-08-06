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

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * The connection provider is responsible for providing connections to the database.
 *
 * @since 1.5
 */
public interface ConnectionProvider extends Provider {

    /**
     * Gets a connection from the given data source.
     *
     * @param dataSource the data source to get the connection from.
     * @return a connection to the database.
     */
    Connection getConnection(@Nonnull DataSource dataSource);

    /**
     * Releases the given connection back to the data source.
     *
     * @param connection the connection to release.
     * @param dataSource the data source to which the connection belongs.
     */
    void releaseConnection(@Nonnull Connection connection, @Nonnull DataSource dataSource);
}
