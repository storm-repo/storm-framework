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

import static java.lang.System.identityHashCode;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import st.orm.PersistenceException;

/**
 * The default connection provider, binding connections to the active {@link JdbcTransactionContext}.
 *
 * <p>This provider is platform-neutral: outside a programmatic transaction, connections are acquired and
 * closed directly on the data source. Integrations that bind connections to an external transaction subsystem
 * supply their own {@link ConnectionProvider} via the template builder.</p>
 *
 * @since 1.13
 */
public final class JdbcConnectionProviderImpl implements ConnectionProvider {

    @Override
    public Connection getConnection(@Nonnull DataSource dataSource, @Nullable TransactionContext context) {
        if (context != null) {
            if (!(context instanceof JdbcTransactionContext jdbcContext)) {
                throw new IllegalArgumentException("Transaction context must be of type JdbcTransactionContext.");
            }
            var connection = jdbcContext.getConnection(dataSource);
            ConcurrencyDetector.beforeAccess(connection, context);
            return connection;
        }
        // If no programmatic transaction is active, obtain a new connection from the data source.
        return getRegularConnection(dataSource);
    }

    @Override
    public void releaseConnection(@Nonnull Connection connection, @Nonnull DataSource dataSource,
                                  @Nullable TransactionContext context) {
        if (context != null) {
            if (!(context instanceof JdbcTransactionContext jdbcContext)) {
                throw new IllegalArgumentException("Transaction context must be of type JdbcTransactionContext.");
            }
            if (jdbcContext.currentConnection() == connection) {
                // If this connection is the current transaction connection, do not close it. It will be closed
                // when the outermost transaction ends.
                ConcurrencyDetector.afterAccess(connection, context);
                return;
            }
        }
        releaseRegularConnection(connection);
    }

    private Connection getRegularConnection(@Nonnull DataSource dataSource) {
        try {
            return dataSource.getConnection();
        } catch (Throwable t) {
            throw new PersistenceException("Failed to get connection from DataSource.", t);
        }
    }

    private void releaseRegularConnection(@Nonnull Connection connection) {
        try {
            connection.close();
        } catch (Throwable t) {
            throw new PersistenceException("Failed to release connection.", t);
        }
    }

    /**
     * Detects concurrent access to transaction-scoped connections.
     *
     * <p>Ownership is tracked by transaction context identity rather than thread identity, because coroutines
     * may resume on a different virtual thread after suspension (especially with OpenTelemetry or other
     * javaagent instrumentation that wraps dispatched tasks).</p>
     *
     * <p>The same context can access the same connection multiple times (re-entrancy).</p>
     */
    public static final class ConcurrencyDetector {

        private static final class ConnectionIdentity extends WeakReference<Connection> {
            private final int id;

            ConnectionIdentity(Connection connection, ReferenceQueue<Connection> queue) {
                super(connection, queue);
                this.id = identityHashCode(connection);
            }

            @Override
            public int hashCode() {
                return id;
            }

            @Override
            public boolean equals(Object other) {
                return other instanceof ConnectionIdentity otherIdentity
                        && this.get() == otherIdentity.get()
                        && this.get() != null;
            }
        }

        private static final class Owner {
            @Nullable TransactionContext context;
            int depth;
        }

        private static final ReferenceQueue<Connection> QUEUE = new ReferenceQueue<>();
        private static final Map<ConnectionIdentity, Owner> OWNERS = new ConcurrentHashMap<>();

        private ConcurrencyDetector() {
        }

        private static void reap() {
            while (true) {
                var ref = QUEUE.poll();
                if (!(ref instanceof ConnectionIdentity identity)) {
                    break;
                }
                OWNERS.remove(identity);
            }
        }

        public static void beforeAccess(@Nonnull Connection connection, @Nonnull TransactionContext context) {
            reap();
            var key = new ConnectionIdentity(connection, QUEUE);
            var owner = OWNERS.computeIfAbsent(key, ignore -> new Owner());
            synchronized (owner) {
                if (owner.context == null) {
                    owner.context = context;
                    owner.depth = 1;
                } else if (owner.context == context) {
                    owner.depth++;
                } else {
                    throw new PersistenceException("Concurrent access on " + connection + ".");
                }
            }
        }

        public static void afterAccess(@Nonnull Connection connection, @Nonnull TransactionContext context) {
            reap();
            var key = new ConnectionIdentity(connection, QUEUE);
            var owner = OWNERS.get(key);
            if (owner == null) {
                return;
            }
            boolean clear = false;
            synchronized (owner) {
                if (owner.context != context) {
                    return;
                }
                if (--owner.depth == 0) {
                    owner.context = null;
                    clear = true;
                }
            }
            if (clear) {
                OWNERS.remove(key, owner);
            }
        }
    }
}
