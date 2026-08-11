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

import static java.sql.Connection.TRANSACTION_NONE;
import static java.sql.Connection.TRANSACTION_READ_COMMITTED;
import static java.sql.Connection.TRANSACTION_READ_UNCOMMITTED;
import static java.sql.Connection.TRANSACTION_REPEATABLE_READ;
import static java.sql.Connection.TRANSACTION_SERIALIZABLE;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import st.orm.Entity;
import st.orm.PersistenceException;
import st.orm.TransactionPropagation;
import st.orm.TransactionTimedOutException;
import st.orm.UnexpectedRollbackException;

/**
 * A JDBC transaction context implementation that provides lightweight transaction management based on JDBC.
 * This supports various transaction propagation behaviors.
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Supports all transaction propagation modes</li>
 *   <li>Manages transaction isolation levels and read-only settings</li>
 *   <li>Handles connection lifecycle and savepoint management</li>
 *   <li>Binds all state to the context object rather than a thread, supporting coroutines and virtual
 *   threads</li>
 * </ul>
 *
 * <p>Transaction state management: the context maintains a stack of transaction frames. Each frame tracks the
 * connection, savepoint, and transaction attributes; nested transactions are supported through savepoints; the
 * physical connection is bound lazily, when the first data source touches this context.</p>
 *
 * @see TransactionPropagation for supported transaction propagation modes
 * @since 1.13
 */
public final class JdbcTransactionContext implements TransactionContext {

    private static final Logger LOGGER = LoggerFactory.getLogger("st.orm.transaction");

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    /**
     * The state of one transaction frame.
     *
     * <p>Joined REQUIRED/SUPPORTS/MANDATORY frames share the same entity-cache map instance for identity
     * stability. NESTED also shares the map (same connection), but on savepoint rollback the map is cleared to
     * avoid stale state.</p>
     */
    private static final class TransactionState {
        final TransactionPropagation propagation;
        final @Nullable Integer isolationLevel;
        final @Nullable Integer timeoutSeconds;
        final @Nullable Boolean readOnly;
        final String transactionId = UUID.randomUUID().toString();
        // Guards lazy connection binding: two threads (e.g. a coroutine resumption racing a task) must not
        // assign different connections to the same state. A lock rather than synchronized, so JDBC calls do
        // not pin virtual threads.
        final ReentrantLock bindLock = new ReentrantLock();
        @Nullable DataSource dataSource;
        volatile @Nullable Connection connection;
        boolean ownsConnection;
        @Nullable Integer originalIsolationLevel;
        @Nullable Boolean originalReadOnly;
        @Nullable Savepoint savepoint;
        boolean rollbackOnly;
        boolean rollbackInherited;
        @Nullable Connection suspendedConnection;
        @Nullable DataSource suspendedDataSource;
        boolean suspended;
        @Nullable Long deadlineNanos;
        Map<Class<?>, EntityCache<?, ?>> entityCacheMap = new HashMap<>();

        TransactionState(@Nonnull TransactionPropagation propagation,
                         @Nullable Integer isolationLevel,
                         @Nullable Integer timeoutSeconds,
                         @Nullable Boolean readOnly) {
            this.propagation = propagation;
            this.isolationLevel = isolationLevel;
            this.timeoutSeconds = timeoutSeconds;
            this.readOnly = readOnly;
        }

        String timeoutDescription() {
            return "isolation=" + isolationName(isolationLevel) + ", timeout="
                    + (timeoutSeconds == null ? "<none>" : timeoutSeconds + "s");
        }

        @Nullable
        Integer remainingSeconds() {
            if (deadlineNanos == null) {
                return null;
            }
            long remaining = deadlineNanos - nowNanos();
            if (remaining <= 0L) {
                return 0;
            }
            if (remaining >= (long) Integer.MAX_VALUE * NANOS_PER_SECOND) {
                return Integer.MAX_VALUE;
            }
            return (int) (remaining / NANOS_PER_SECOND);
        }
    }

    private final List<TransactionState> stack = new ArrayList<>();

    private static long nowNanos() {
        return System.nanoTime();
    }

    private static long deadlineFromNow(int timeoutSeconds) {
        return nowNanos() + (long) timeoutSeconds * NANOS_PER_SECOND;
    }

    private static String isolationName(@Nullable Integer isolation) {
        if (isolation == null) {
            return "DEFAULT";
        }
        return switch (isolation) {
            case TRANSACTION_NONE -> "NONE";
            case TRANSACTION_READ_UNCOMMITTED -> "READ_UNCOMMITTED";
            case TRANSACTION_READ_COMMITTED -> "READ_COMMITTED";
            case TRANSACTION_REPEATABLE_READ -> "REPEATABLE_READ";
            case TRANSACTION_SERIALIZABLE -> "SERIALIZABLE";
            default -> "UNKNOWN (" + isolation + ")";
        };
    }

    private TransactionState currentState() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("No transaction active.");
        }
        return stack.getLast();
    }

    @Nullable
    private TransactionState lastOrNull() {
        return stack.isEmpty() ? null : stack.getLast();
    }

    @Override
    public Optional<String> describe() {
        var state = lastOrNull();
        return Optional.ofNullable(state == null ? null : state.timeoutDescription());
    }

    /**
     * Obtains a JDBC connection for the current transaction, creating or reusing one based on the transaction
     * propagation rules.
     *
     * @param dataSource the data source to get the connection from.
     * @return the JDBC connection.
     * @throws PersistenceException if the connection cannot be obtained.
     */
    public Connection getConnection(@Nonnull DataSource dataSource) {
        useDataSource(dataSource);
        return currentState().connection;
    }

    /**
     * Returns the current active connection, or {@code null} when no connection is bound.
     */
    @Nullable
    public Connection currentConnection() {
        var state = lastOrNull();
        return state == null ? null : state.connection;
    }

    /**
     * Returns true if the transaction has repeatable-read semantics: the isolation level is
     * {@code REPEATABLE_READ} or higher. When the isolation level is not explicitly set, the database default is
     * used; since most databases default to {@code READ_COMMITTED}, this returns false so fresh data is fetched
     * on each read.
     */
    @Override
    public boolean isRepeatableRead() {
        var isolationLevel = currentState().isolationLevel;
        if (isolationLevel == null) {
            return false;
        }
        return isolationLevel >= TRANSACTION_REPEATABLE_READ;
    }

    @SuppressWarnings("unchecked")
    @Override
    public EntityCache<? extends Entity<?>, ?> entityCache(@Nonnull Class<? extends Entity<?>> entityType,
                                                           @Nonnull CacheRetention retention) {
        return (EntityCache<? extends Entity<?>, ?>) currentState().entityCacheMap
                .computeIfAbsent(entityType, ignore -> new EntityCacheImpl<>(retention));
    }

    @SuppressWarnings("unchecked")
    @Override
    public EntityCache<? extends Entity<?>, ?> getEntityCache(@Nonnull Class<? extends Entity<?>> entityType) {
        var cache = (EntityCache<? extends Entity<?>, ?>) currentState().entityCacheMap.get(entityType);
        if (cache == null) {
            throw new IllegalStateException("No entity cache exists for " + entityType.getName() + ".");
        }
        return cache;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public EntityCache<? extends Entity<?>, ?> findEntityCache(@Nonnull Class<? extends Entity<?>> entityType) {
        return (EntityCache<? extends Entity<?>, ?>) currentState().entityCacheMap.get(entityType);
    }

    /**
     * Clears all entity caches associated with this transaction context.
     */
    @Override
    public void clearAllEntityCaches() {
        currentState().entityCacheMap.values().forEach(EntityCache::clear);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Decorator<T> getDecorator(@Nonnull Class<T> resourceType) {
        if (resourceType != PreparedStatement.class) {
            return resource -> resource; // No-op.
        }
        return resource -> {
            var preparedStatement = (PreparedStatement) resource;
            // Prefer dynamic remaining time; fall back to static seconds if present.
            var state = currentState();
            Integer remaining = state.remainingSeconds();
            Integer seconds;
            if (remaining != null && remaining > 0) {
                seconds = remaining;
            } else if (remaining != null) {
                seconds = 1; // Already out of time: force a fast timeout.
            } else {
                seconds = state.timeoutSeconds;
            }
            if (seconds != null && seconds > 0) {
                try {
                    preparedStatement.setQueryTimeout(seconds);
                } catch (SQLException e) {
                    throw new PersistenceException("Failed to set query timeout.", e);
                }
            }
            return resource;
        };
    }

    /**
     * Begins a transaction frame with the specified attributes.
     *
     * <p>The physical connection is bound lazily, when the first data source touches this context via
     * {@code useDataSource}. The frame is finished with {@link #complete(boolean)}.</p>
     */
    public void begin(@Nonnull TransactionPropagation propagation,
               @Nullable Integer isolation,
               @Nullable Integer timeoutSeconds,
               @Nullable Boolean readOnly) {
        var state = new TransactionState(propagation, isolation, timeoutSeconds, readOnly);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("""
                    Starting transaction ({}):
                        propagation: {}
                        isolation: {}
                        timeout: {}
                        readOnly: {}""",
                    state.transactionId, propagation, isolationName(isolation),
                    timeoutSeconds == null ? "<no timeout>" : timeoutSeconds + " second(s)", readOnly);
        }
        state.deadlineNanos = timeoutSeconds == null ? null : deadlineFromNow(timeoutSeconds);
        stack.add(state);
    }

    /**
     * Completes the current transaction frame.
     *
     * <p>When {@code rollback} is {@code true}, the frame rolls back and any rollback failure is suppressed so
     * the caller can surface the original error. Otherwise the frame commits; the commit path detects timeouts
     * and rollback-only marks and rolls back instead, throwing accordingly.</p>
     *
     * @param rollback whether the transactional work failed and the frame must be rolled back.
     */
    public void complete(boolean rollback) {
        if (rollback) {
            if (LOGGER.isTraceEnabled()) {
                var state = lastOrNull();
                LOGGER.trace("Transaction failed ({}).", state == null ? null : state.transactionId);
            }
            rollback(true); // Suppress any rollback exception to ensure handling of exception.
        } else {
            // Let commit detect timeout or rollback-only.
            commit();
        }
    }

    /**
     * Whether the current transaction is marked for rollback-only.
     */
    public boolean isRollbackOnly() {
        var state = lastOrNull();
        return state != null && state.rollbackOnly;
    }

    /**
     * Marks the current transaction so that it will roll back on completion.
     */
    public void setRollbackOnly() {
        int lastIndex = stack.size() - 1;
        var currentState = stack.get(lastIndex);
        LOGGER.debug("Marking transaction for rollback ({}).", currentState.transactionId);
        currentState.rollbackOnly = true;
        currentState.rollbackInherited = false; // Reset inherited flag, if applicable.
        // Do NOT propagate from NESTED (savepoint) scopes and do NOT propagate from owners (outermost or
        // REQUIRES_NEW).
        if (currentState.savepoint != null || currentState.ownsConnection) {
            return;
        }
        // Propagate to outer joined frames up to (and including) the owning frame, but stop at a savepoint
        // boundary.
        for (int i = lastIndex - 1; i >= 0; i--) {
            var state = stack.get(i);
            if (state.savepoint != null) {
                break; // Do not cross NESTED boundary.
            }
            state.rollbackOnly = true;
            state.rollbackInherited = true; // Indicates caller-triggered.
            if (state.ownsConnection) {
                break; // Stop at the owner (could be REQUIRES_NEW).
            }
        }
    }

    private void useDataSource(@Nonnull DataSource dataSource) {
        for (int i = 0; i < stack.size(); i++) {
            var state = stack.get(i);
            if (state.connection == null) {
                var outer = i > 0 ? stack.get(i - 1) : null;
                boolean outerBound = outer != null && outer.connection != null;
                switch (state.propagation) {
                    case REQUIRED -> {
                        if (outerBound) {
                            joinOuterTransaction(state, outer);
                        } else {
                            openNewTransaction(state, dataSource);
                        }
                    }
                    case SUPPORTS -> {
                        if (outerBound) {
                            joinOuterTransaction(state, outer);
                        } else {
                            openConnection(state, dataSource); // Non-transactional.
                        }
                    }
                    case MANDATORY -> {
                        if (outerBound) {
                            joinOuterTransaction(state, outer);
                        } else {
                            throw new PersistenceException("No existing transaction for MANDATORY propagation.");
                        }
                    }
                    case REQUIRES_NEW -> {
                        if (outerBound) {
                            suspendTransaction(state, outer);
                        }
                        openNewTransaction(state, dataSource);
                    }
                    case NOT_SUPPORTED -> {
                        if (outerBound) {
                            suspendTransaction(state, outer);
                        }
                        openConnection(state, dataSource); // Non-transactional.
                    }
                    case NEVER -> {
                        if (outerBound) {
                            throw new PersistenceException("Existing transaction found for NEVER propagation.");
                        }
                        openConnection(state, dataSource); // Non-transactional.
                    }
                    case NESTED -> {
                        if (outerBound) {
                            openNestedTransaction(state, outer, dataSource);
                        } else {
                            openNewTransaction(state, dataSource);
                        }
                    }
                }
            } else {
                // Already bound: sanity-check data source.
                if (state.dataSource != dataSource) {
                    throw new PersistenceException(
                            "Incompatible DataSource: " + dataSource + " but already using " + state.dataSource + ".");
                }
            }
        }
    }

    private void openNestedTransaction(@Nonnull TransactionState state,
                                       @Nonnull TransactionState outer,
                                       @Nonnull DataSource dataSource) {
        if (outer.dataSource != dataSource) {
            throw new PersistenceException(
                    "Incompatible DataSource: expected " + outer.dataSource + ", got " + dataSource + ".");
        }
        try {
            var outerConnection = outer.connection;
            var savepoint = outerConnection.setSavepoint();
            LOGGER.debug("Creating nested transaction with savepoint {} on {} ({}).",
                    savepoint, outerConnection, state.transactionId);
            state.connection = outerConnection;
            state.dataSource = dataSource;
            state.savepoint = savepoint;
            state.ownsConnection = false;
            // Share caches since this is the same JDBC connection/transaction. If we roll back to this
            // savepoint, we will clear the shared cache to avoid stale state. The connection settings are not
            // captured: close() only restores settings on frames that own the connection.
            state.entityCacheMap = outer.entityCacheMap;
            Long innerRequested = state.timeoutSeconds == null ? null : deadlineFromNow(state.timeoutSeconds);
            if (outer.deadlineNanos == null && innerRequested == null) {
                state.deadlineNanos = null;
            } else if (outer.deadlineNanos == null) {
                state.deadlineNanos = innerRequested;
            } else if (innerRequested == null) {
                state.deadlineNanos = outer.deadlineNanos;
            } else {
                state.deadlineNanos = Math.min(outer.deadlineNanos, innerRequested);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to create savepoint.", e);
        }
    }

    /**
     * Commits the current transaction block. If nested, releases the savepoint; if outermost (or REQUIRES_NEW),
     * commits and closes; if joined REQUIRED, just restores settings.
     */
    private void commit() {
        var current = currentState();
        boolean expired = current.deadlineNanos != null && nowNanos() >= current.deadlineNanos;
        if (current.rollbackOnly || expired) {
            rollback(false);
            return;
        }
        var state = popState();
        var connection = state.connection;
        // If no connection was ever used, still enforce timeout deterministically.
        if (connection == null) {
            boolean expiredAfter = state.deadlineNanos != null && nowNanos() >= state.deadlineNanos;
            if (expiredAfter) {
                throw new TransactionTimedOutException(
                        "Did not complete within timeout [" + state.timeoutDescription() + "].");
            }
            return;
        }
        try {
            if (state.savepoint != null) {
                LOGGER.debug("Committing nested scope; releasing savepoint {} on {} ({}).",
                        state.savepoint, connection, state.transactionId);
                connection.releaseSavepoint(state.savepoint);
            } else if (state.ownsConnection) {
                LOGGER.debug("Committing transaction on {} ({}).", connection, state.transactionId);
                try {
                    if (!connection.getAutoCommit()) {
                        connection.commit();
                    }
                } finally {
                    // Always return the connection to the pool — even if commit() threw — so the pool does not
                    // retain a connection with autoCommit=false (which would fail the auto-commit precondition
                    // on the next openNewTransaction()).
                    try {
                        close(connection, state);
                    } catch (SQLException closeException) {
                        LOGGER.warn("Failed to close connection after commit ({}).", state.transactionId, closeException);
                    }
                }
            }
            // else: joined REQUIRED: nothing to do yet.
        } catch (SQLException e) {
            throw new PersistenceException("Commit failed.", e);
        }
    }

    /**
     * Rolls back the current transaction frame.
     */
    private void rollback(boolean suppressException) {
        var state = popState();
        boolean expired = state.deadlineNanos != null && nowNanos() >= state.deadlineNanos;
        var connection = state.connection;
        try {
            if (state.savepoint != null && connection != null) {
                LOGGER.debug("Rolling back to savepoint {} on {} ({}).", state.savepoint, connection, state.transactionId);
                connection.rollback(state.savepoint);
                // We shared the cache map with the outer scope. After savepoint rollback, cached entities can
                // be inconsistent with the DB state. Invalidate the cache for the remainder of the outer
                // transaction.
                var outer = lastOrNull();
                if (outer != null) {
                    outer.entityCacheMap.clear();
                }
            } else if (state.ownsConnection && connection != null) {
                LOGGER.debug("Rolling back transaction on {} ({}).", connection, state.transactionId);
                try {
                    if (!connection.getAutoCommit()) {
                        connection.rollback();
                    }
                } finally {
                    // Always return the connection to the pool — even if rollback() threw — so the pool does
                    // not retain a connection with autoCommit=false (which would fail the auto-commit
                    // precondition on the next openNewTransaction()).
                    try {
                        close(connection, state);
                    } catch (SQLException closeException) {
                        LOGGER.warn("Failed to close connection after rollback ({}).", state.transactionId, closeException);
                    }
                }
            } else {
                // Joined REQUIRED or non-transactional scope (no connection):
                LOGGER.debug("Marking transaction for rollback ({}).", state.transactionId);
                // Propagate to outer joined frames up to (and including) the owning frame, but stop at a
                // savepoint boundary.
                for (int i = stack.size() - 1; i >= 0; i--) {
                    var outerState = stack.get(i);
                    if (outerState.savepoint != null) {
                        break; // Do not cross NESTED boundary.
                    }
                    outerState.rollbackOnly = true;
                    outerState.rollbackInherited = true; // Indicates caller-triggered.
                    if (outerState.ownsConnection) {
                        break; // Stop at the owner (could be REQUIRES_NEW).
                    }
                }
            }
        } catch (SQLException e) {
            if (!suppressException) {
                throw new PersistenceException("Rollback failed.", e);
            }
        }
        if (!suppressException && expired) {
            throw new TransactionTimedOutException(
                    "Did not complete within timeout [" + state.timeoutDescription() + "].");
        }
        if (!suppressException && state.rollbackInherited) {
            throw new UnexpectedRollbackException("Transaction was marked rollback-only by a joined scope.");
        }
    }

    private void close(@Nonnull Connection connection, @Nonnull TransactionState state) throws SQLException {
        if (state.originalIsolationLevel != null) {
            connection.setTransactionIsolation(state.originalIsolationLevel);
        }
        if (state.originalReadOnly != null) {
            connection.setReadOnly(state.originalReadOnly);
        }
        connection.setAutoCommit(true);
        connection.close();
    }

    private TransactionState popState() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("No transaction active.");
        }
        return stack.removeLast();
    }

    /**
     * Opens a fresh JDBC connection for REQUIRED (when no outer) or REQUIRES_NEW.
     */
    private void openNewTransaction(@Nonnull TransactionState state, @Nonnull DataSource dataSource) {
        // Lock the TransactionState so that only one thread can initialize its connection. Without this, two
        // threads could race to assign different connections (or tx modes) to the same state. Ensuring a
        // single, consistent connection instance lets downstream logic detect and fail fast on concurrent
        // access within the same transaction.
        if (state.connection != null) {
            return;
        }
        state.bindLock.lock();
        try {
            if (state.connection != null) {
                return;
            }
            LOGGER.trace("Opening new transaction ({}).", state.transactionId);
            var connection = dataSource.getConnection();
            LOGGER.trace("Obtained connection {} ({}).", connection, state.transactionId);
            if (!connection.getAutoCommit()) {
                throw new PersistenceException("""
                        Connection returned from DataSource must be in auto-commit mode, but arrived with \
                        auto-commit disabled. Either the pool is configured for manual-commit connections, or the \
                        connection carries an unfinished transaction. Configure the pool to hand out auto-commit \
                        connections; Storm disables auto-commit for the duration of a transaction and re-enables it \
                        before releasing the connection.""");
            }
            // Only read and change the connection settings when explicitly requested: reading the isolation
            // level can cost a round trip on some drivers, and close() restores (another round trip) only what
            // was captured here.
            if (state.isolationLevel != null) {
                state.originalIsolationLevel = connection.getTransactionIsolation();
                connection.setTransactionIsolation(state.isolationLevel);
            }
            if (state.readOnly != null) {
                state.originalReadOnly = connection.isReadOnly();
                connection.setReadOnly(state.readOnly);
            }
            connection.setAutoCommit(false);
            state.dataSource = dataSource;
            state.ownsConnection = true;
            if (state.deadlineNanos == null && state.timeoutSeconds != null) {
                state.deadlineNanos = deadlineFromNow(state.timeoutSeconds);
            }
            state.connection = connection;
        } catch (SQLException e) {
            throw new PersistenceException("Failed to open transaction.", e);
        } finally {
            state.bindLock.unlock();
        }
    }

    /**
     * Opens a non-transactional connection (auto-commit).
     */
    private void openConnection(@Nonnull TransactionState state, @Nonnull DataSource dataSource) {
        // Lock the TransactionState so that only one thread can initialize its connection; see
        // openNewTransaction for the rationale.
        if (state.connection != null) {
            return;
        }
        state.bindLock.lock();
        try {
            if (state.connection != null) {
                return;
            }
            LOGGER.trace("Opening connection ({}).", state.transactionId);
            var connection = dataSource.getConnection();
            connection.setAutoCommit(true);
            LOGGER.trace("Obtained connection {} ({}).", connection, state.transactionId);
            state.dataSource = dataSource;
            state.ownsConnection = true;
            // Non-transactional: deadline is not meaningful (no commit), but the decorator still uses
            // remainingSeconds().
            state.deadlineNanos = state.timeoutSeconds == null ? null : deadlineFromNow(state.timeoutSeconds);
            state.connection = connection;
        } catch (SQLException e) {
            throw new PersistenceException("Failed to open connection.", e);
        } finally {
            state.bindLock.unlock();
        }
    }

    /**
     * Suspends an outer transaction on this state.
     */
    private void suspendTransaction(@Nonnull TransactionState state, @Nonnull TransactionState outer) {
        LOGGER.debug("Suspending transaction ({}).", state.transactionId);
        state.suspendedConnection = outer.connection;
        state.suspendedDataSource = outer.dataSource;
        state.suspended = true;
        // No deadline needed while suspended (no shared connection).
    }

    /**
     * Joins an existing transaction.
     */
    private void joinOuterTransaction(@Nonnull TransactionState state, @Nonnull TransactionState outer) {
        LOGGER.debug("Joining transaction ({} -> {}).", state.transactionId, outer.transactionId);
        var connection = outer.connection;
        if (connection == null) {
            throw new PersistenceException("No outer connection to join.");
        }
        state.dataSource = outer.dataSource;
        state.ownsConnection = false;
        // Share the entity cache map with the transaction we joined. The connection settings are not
        // captured: close() only restores settings on frames that own the connection.
        state.entityCacheMap = outer.entityCacheMap;
        Long innerRequested = state.timeoutSeconds == null ? null : deadlineFromNow(state.timeoutSeconds);
        if (outer.deadlineNanos == null && innerRequested == null) {
            // Keep any deadline already set when the frame began.
        } else if (outer.deadlineNanos == null) {
            state.deadlineNanos = innerRequested;
        } else if (innerRequested == null) {
            state.deadlineNanos = outer.deadlineNanos;
        } else {
            state.deadlineNanos = Math.min(outer.deadlineNanos, innerRequested);
        }
        state.connection = connection;
    }
}
