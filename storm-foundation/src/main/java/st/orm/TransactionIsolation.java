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
package st.orm;

import java.sql.Connection;

/**
 * Transaction isolation levels, mapping to the JDBC isolation constants.
 *
 * <p>There is no {@code DEFAULT} member: leaving the isolation unset means the provider's (typically the
 * database's) default applies.</p>
 *
 * @since 1.13
 */
public enum TransactionIsolation {

    /**
     * Dirty reads, non-repeatable reads, and phantom reads can occur.
     */
    READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED),

    /**
     * Dirty reads are prevented; non-repeatable reads and phantom reads can occur.
     */
    READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),

    /**
     * Dirty reads and non-repeatable reads are prevented; phantom reads can occur.
     */
    REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),

    /**
     * Dirty reads, non-repeatable reads, and phantom reads are prevented.
     */
    SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);

    private final int jdbcLevel;

    TransactionIsolation(int jdbcLevel) {
        this.jdbcLevel = jdbcLevel;
    }

    /**
     * The corresponding {@link Connection} isolation constant.
     */
    public int jdbcLevel() {
        return jdbcLevel;
    }

    /**
     * Returns the isolation level for the given JDBC constant.
     *
     * @param jdbcLevel a {@link Connection} isolation constant.
     * @throws IllegalArgumentException if the constant does not map to an isolation level.
     */
    public static TransactionIsolation fromJdbcLevel(int jdbcLevel) {
        for (TransactionIsolation isolation : values()) {
            if (isolation.jdbcLevel == jdbcLevel) {
                return isolation;
            }
        }
        throw new IllegalArgumentException("Unknown JDBC isolation level: " + jdbcLevel + ".");
    }
}
