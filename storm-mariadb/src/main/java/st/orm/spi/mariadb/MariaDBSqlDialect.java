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
package st.orm.spi.mariadb;

import jakarta.annotation.Nonnull;
import st.orm.StormConfig;
import st.orm.spi.mysql.MySQLSqlDialect;

public class MariaDBSqlDialect extends MySQLSqlDialect {

    public MariaDBSqlDialect() {
    }

    public MariaDBSqlDialect(@Nonnull StormConfig config) {
        super(config);
    }

    /**
     * Returns the name of the SQL dialect.
     *
     * @return the name of the SQL dialect.
     * @since 1.2
     */
    @Override
    public String name() {
        return "MariaDB";
    }

    /**
     * Returns a fetch size of 1000 to control result batching.
     *
     * <p>Unlike MySQL Connector/J, the MariaDB Connector/J driver does not support {@code Integer.MIN_VALUE}
     * as a fetch size for row-by-row streaming. Instead, a positive fetch size is used to instruct the driver
     * to fetch rows in batches, reducing memory consumption for large result sets while maintaining good
     * throughput.</p>
     *
     * @return {@code 1000}.
     * @since 1.10
     */
    @Override
    public int defaultFetchSize() {
        return 1000;
    }

    /**
     * Returns {@code false} because the positive fetch size is safe to apply universally.
     *
     * <p>Unlike the MySQL Connector/J row-by-row streaming mode, the MariaDB batch fetch approach does not
     * impose connection-level constraints, so it can be applied to both streaming and eager result
     * consumption without a performance penalty.</p>
     *
     * @return {@code false}.
     * @since 1.10
     */
    @Override
    public boolean streamOnlyFetchSize() {
        return false;
    }

    /**
     * Returns the SQL statement for getting the next value of the given sequence.
     *
     * @param sequenceName the name of the sequence.
     * @return the SQL statement for getting the next value of the given sequence.
     * @since 1.6
     */
    @Override
    public String sequenceNextVal(String sequenceName) {
        return "NEXT VALUE FOR " + getSafeIdentifier(sequenceName);
    }

    /**
     * MariaDB supports {@code INSERT ... RETURNING} (since 10.5), so batch {@code insertAndFetchIds} reads the keys
     * from a multi-row {@code VALUES ... RETURNING} result set.
     *
     * @return {@code true}.
     * @since 1.13
     */
    @Override
    public boolean supportsInsertReturning() {
        return true;
    }

    /**
     * Disables the multi-row generated-keys path inherited from {@link MySQLSqlDialect}. Unlike MySQL Connector/J,
     * the MariaDB Connector/J driver returns only a single key from {@code getGeneratedKeys} for a multi-row
     * {@code INSERT}; the {@link #supportsInsertReturning() RETURNING} path is used instead.
     *
     * @return {@code false}.
     * @since 1.13
     */
    @Override
    public boolean supportsMultiRowGeneratedKeys() {
        return false;
    }
}
