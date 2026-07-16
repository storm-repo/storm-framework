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
package st.orm.core.template.impl;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.util.Optional.ofNullable;
import static st.orm.core.template.impl.LazySupplier.lazy;
import static st.orm.core.template.impl.ObjectMapperFactory.getObjectMapper;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import st.orm.Data;
import st.orm.Entity;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.core.spi.QueryContext;
import st.orm.core.spi.QueryContext.ExecutionKind;
import st.orm.core.spi.QueryObserver;
import st.orm.core.spi.QueryObserver.Observation;
import st.orm.core.spi.RefFactory;
import st.orm.core.spi.TransactionScope;
import st.orm.core.spi.TransactionTemplateProvider;
import st.orm.core.spi.WeakInterner;
import st.orm.core.template.PreparedQuery;
import st.orm.core.template.Query;
import st.orm.core.template.SqlOperation;
import st.orm.core.template.SqlTemplateException;

@SuppressWarnings("ALL")
class QueryImpl implements Query {

    /**
     * The template-scoped services and statement metadata shared by a query and the prepared queries derived from it.
     */
    record Environment(@Nonnull RefFactory refFactory,
                       @Nonnull TransactionTemplateProvider transactionTemplateProvider,
                       @Nonnull QueryObserver queryObserver,
                       @Nonnull Function<Throwable, RuntimeException> exceptionTransformer,
                       @Nonnull SqlOperation operation,
                       @Nullable Class<? extends Data> dataType,
                       @Nullable String statementText) {
    }

    private final Environment environment;
    private final RefFactory refFactory;
    private final Function<Boolean, PreparedStatement> statement;
    private final BindVarsHandle bindVarsHandle;
    private final boolean versionAware;
    private final Class<? extends Data> affectedType;
    private final boolean unsafe;
    private final boolean managed;
    private final int defaultFetchSize;
    private final boolean streamOnlyFetchSize;
    private final boolean streamingRequiresTransaction;
    private final Function<Throwable, RuntimeException> exceptionTransformer;

    QueryImpl(@Nonnull Environment environment,
              @Nonnull Function<Boolean, PreparedStatement> statement,
              @Nullable BindVarsHandle bindVarsHandle,
              @Nullable Class<? extends Data> affectedType,
              boolean versionAware,
              boolean managed,
              boolean unsafe,
              int defaultFetchSize,
              boolean streamOnlyFetchSize,
              boolean streamingRequiresTransaction) {
        this.environment = environment;
        this.refFactory = environment.refFactory();
        this.statement = statement;
        this.bindVarsHandle = bindVarsHandle;
        this.versionAware = versionAware;
        this.affectedType = affectedType;
        this.managed = managed;
        this.unsafe = unsafe;
        this.defaultFetchSize = defaultFetchSize;
        this.streamOnlyFetchSize = streamOnlyFetchSize;
        this.streamingRequiresTransaction = streamingRequiresTransaction;
        this.exceptionTransformer = environment.exceptionTransformer();
    }

    /**
     * Prepares the query for execution.
     *
     * <p>Queries are normally constructed in a lazy fashion, unlike prepared queries which are constructed eagerly.
     * Prepared queries allow the use of bind variables and enable reading generated keys after row insertion.</p>
     *
     * <p><strong>Note:</strong> The prepared query must be closed after usage to prevent resource leaks. As the prepared
     * query is {@code AutoCloseable}, it is recommended to use it within a {@code try-with-resources} block.</p>
     *
     * @return the prepared query.
     * @throws PersistenceException if the query preparation fails.
     */
    @Override
    public PreparedQuery prepare() {
        return MonitoredResource.wrap(new PreparedQueryImpl(environment, statement.apply(unsafe), bindVarsHandle, affectedType, versionAware, managed, defaultFetchSize, streamOnlyFetchSize, streamingRequiresTransaction));
    }

    /**
     * Returns a new query that is marked as managed. This indicates that the query is managed by a repository.
     *
     * @return a new query that is marked as managed.
     * @since 1.8
     */
    @Override
    public Query managed() {
        return new QueryImpl(environment, statement, bindVarsHandle, affectedType, versionAware, true, unsafe, defaultFetchSize, streamOnlyFetchSize, streamingRequiresTransaction);
    }

    /**
     * Returns a new query that allows dangerous operations, such as DELETE and UPDATE without a WHERE clause.
     *
     * @return a new query that allows dangerous operations.
     * @since 1.2
     */
    @Override
    public Query unsafe() {
        return new QueryImpl(environment, statement, bindVarsHandle, affectedType, versionAware, managed, true, defaultFetchSize, streamOnlyFetchSize, streamingRequiresTransaction);
    }

    @Override
    public QueryImpl withoutFetchSize() {
        if (defaultFetchSize == 0) {
            return this;
        }
        return new QueryImpl(environment, statement, bindVarsHandle, affectedType, versionAware, managed, unsafe, 0, false, false);
    }

    private PreparedStatement getStatement() {
        return statement.apply(unsafe);
    }

    private void applyFetchSize(@Nonnull PreparedStatement statement) throws SQLException {
        if (defaultFetchSize != 0) {
            statement.setFetchSize(defaultFetchSize);
        }
    }

    /**
     * Configures the connection for cursor-based streaming when the dialect requires an active transaction.
     *
     * <p>If the connection is in auto-commit mode and the dialect indicates that streaming requires a transaction,
     * auto-commit is disabled to enable cursor-based result batching. The returned {@code Runnable} restores the
     * connection to its original state when the stream is closed.</p>
     *
     * @param statement the prepared statement whose connection to configure.
     * @return a cleanup action that restores auto-commit, or {@code null} if no configuration was needed.
     */
    private @Nullable Runnable configureStreamingTransaction(@Nonnull PreparedStatement statement) {
        if (streamingRequiresTransaction && defaultFetchSize != 0) {
            try {
                var connection = statement.getConnection();
                if (connection.getAutoCommit()) {
                    connection.setAutoCommit(false);
                    return () -> {
                        try {
                            connection.commit();
                            connection.setAutoCommit(true);
                        } catch (SQLException e) {
                            throw new PersistenceException(e);
                        }
                    };
                }
            } catch (SQLException ignore) {
                // Unable to determine or change auto-commit state; proceed without cursor-based streaming.
            }
        }
        return null;
    }

    protected boolean closeStatement() {
        return true;
    }

    /**
     * Notifies the query observer of a starting execution. Observer failures never affect query execution.
     */
    private Observation observe(@Nonnull ExecutionKind kind) {
        try {
            var queryObserver = environment.queryObserver();
            if (queryObserver == QueryObserver.NOOP) {
                // Fast path: skip context creation when nothing is observing.
                return Observation.NOOP;
            }
            return queryObserver.onExecute(
                    new QueryContextImpl(environment.operation(), environment.dataType(), kind, environment.statementText()));
        } catch (Throwable ignore) {
            return Observation.NOOP;
        }
    }

    private static void observationError(@Nonnull Observation observation, @Nonnull Throwable throwable) {
        try {
            observation.error(throwable);
        } catch (Throwable ignore) {
            // Observer failures never affect query execution.
        }
    }

    private static void closeObservation(@Nonnull Observation observation) {
        try {
            observation.close();
        } catch (Throwable ignore) {
            // Observer failures never affect query execution.
        }
    }

    /**
     * Execute a SELECT query and return the resulting rows as a stream of row instances.
     *
     * <p>Each element in the stream represents a row in the result, where the columns of the row corresponds to the
     * order of values in the row array.</p>
     *
     * <p>The resulting stream is lazily loaded, meaning that the records are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of records.</p>
     *
     * <p><strong>Note:</strong> Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must be
     * closed after usage to prevent resource leaks. As the stream is {@code AutoCloseable}, it is recommended to use it
     * within a {@code try-with-resources} block.</p>
     *
     * @return a stream of results.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    @Override
    public Stream<Object[]> getResultStream() {
        var observation = observe(ExecutionKind.QUERY);
        boolean handedOff = false;
        try {
            PreparedStatement statement = getStatement();
            boolean close = true;
            try {
                applyFetchSize(statement);
                Runnable streamingCleanup = configureStreamingTransaction(statement);
                ResultSet resultSet = statement.executeQuery();
                try {
                    int columnCount = resultSet.getMetaData().getColumnCount();
                    close = false;
                    handedOff = true;
                    return MonitoredResource.wrap(
                            StreamSupport.stream(rawRowSpliterator(resultSet, columnCount), false)
                                    .onClose(() -> {
                                        try {
                                            close(resultSet, statement, streamingCleanup);
                                        } finally {
                                            closeObservation(observation);
                                        }
                                    }));
                } finally {
                    if (close) {
                        resultSet.close();
                    }
                }
            } finally {
                if (close && closeStatement()) {
                    statement.close();
                }
            }
        } catch (Exception e) {
            if (!handedOff) {
                observationError(observation, e);
                closeObservation(observation);
            }
            throw exceptionTransformer.apply(e);
        }
    }

    /**
     * Execute a SELECT query and return the resulting rows as a stream of row instances.
     *
     * <p>Each element in the stream represents a row in the result, where the columns of the row are mapped to the
     * constructor arguments of the specified {@code type}.</p>
     *
     * <p>The resulting stream is lazily loaded, meaning that the records are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of records.</p>
     *
     * <p><strong>Note:</strong> Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must be
     * closed after usage to prevent resource leaks. As the stream is {@code AutoCloseable}, it is recommended to use it
     * within a {@code try-with-resources} block.</p>
     *
     * @return a stream of results.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    @Override
    public <T> Stream<T> getResultStream(@Nonnull Class<T> type) {
        var observation = observe(ExecutionKind.QUERY);
        boolean handedOff = false;
        PreparedStatement statement = null;
        try {
            statement = getStatement();
            boolean close = true;
            try {
                applyFetchSize(statement);
                Runnable streamingCleanup = configureStreamingTransaction(statement);
                ResultSet resultSet = statement.executeQuery();
                int columnCount = resultSet.getMetaData().getColumnCount();
                var mapper = getObjectMapper(columnCount, type, refFactory)
                        .orElseThrow(() -> new SqlTemplateException("No suitable constructor found for %s.".formatted(type.getName())));
                close = false;
                handedOff = true;
                var closeableStatement = statement;
                return MonitoredResource.wrap(
                        StreamSupport.stream(rowSpliterator(resultSet, columnCount, mapper), false)
                                .onClose(() -> {
                                    try {
                                        close(resultSet, closeableStatement, streamingCleanup);
                                    } finally {
                                        closeObservation(observation);
                                    }
                                }));
            } finally {
                if (close && closeStatement()) {
                    statement.close();
                }
            }
        } catch (Exception e) {
            if (!handedOff) {
                observationError(observation, e);
                closeObservation(observation);
            }
            throw exceptionTransformer.apply(e);
        }
    }

    /**
     * Execute a SELECT query and return the resulting rows as a stream of ref instances.
     *
     * <p>Each element in the stream represents a row in the result, where the columns of the row are mapped to the
     * constructor arguments primary key type.</p>
     *
     * <p><strong>Note:</strong> Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must be
     * closed after usage to prevent resource leaks. As the stream is {@code AutoCloseable}, it is recommended to use it
     * within a {@code try-with-resources} block.</p>
     *
     * @param type the type of the results that are being referenced.
     * @param pkType the primary key type.
     * @return a stream of ref instances.
     * @throws PersistenceException if the query fails.
     * @since 1.3
     */
    @Override
    public <T extends Data> Stream<Ref<T>> getRefStream(@Nonnull Class<T> type, @Nonnull Class<?> pkType) {
        var interner = new WeakInterner();
        return getResultStream(pkType)
                .map(pk -> {
                    if (pk == null) {
                        throw new PersistenceException(
                            "Primary key for %s is NULL. This usually indicates an invalid query result or incorrect mapping."
                                .formatted(type.getName())
                        );
                    }
                    return interner.intern(refFactory.create(type, pk));
                });
    }

    @Override
    public Object[] getSingleResult() {
        return streamOnlyFetchSize && defaultFetchSize != 0
                ? withoutFetchSize().getSingleResult()
                : Query.super.getSingleResult();
    }

    @Override
    public <T> T getSingleResult(@Nonnull Class<T> type) {
        return streamOnlyFetchSize && defaultFetchSize != 0
                ? withoutFetchSize().getSingleResult(type)
                : Query.super.getSingleResult(type);
    }

    @Override
    public Optional<Object[]> getOptionalResult() {
        return streamOnlyFetchSize && defaultFetchSize != 0
                ? withoutFetchSize().getOptionalResult()
                : Query.super.getOptionalResult();
    }

    @Override
    public <T> Optional<T> getOptionalResult(@Nonnull Class<T> type) {
        return streamOnlyFetchSize && defaultFetchSize != 0
                ? withoutFetchSize().getOptionalResult(type)
                : Query.super.getOptionalResult(type);
    }

    @Override
    public List<Object[]> getResultList() {
        return streamOnlyFetchSize && defaultFetchSize != 0
                ? withoutFetchSize().getResultList()
                : Query.super.getResultList();
    }

    @Override
    public <T> List<T> getResultList(@Nonnull Class<T> type) {
        return streamOnlyFetchSize && defaultFetchSize != 0
                ? withoutFetchSize().getResultList(type)
                : Query.super.getResultList(type);
    }

    @Override
    public <T extends Data> List<Ref<T>> getRefList(@Nonnull Class<T> type, @Nonnull Class<?> pkType) {
        return streamOnlyFetchSize && defaultFetchSize != 0
                ? withoutFetchSize().getRefList(type, pkType)
                : Query.super.getRefList(type, pkType);
    }

    @Override
    public long getResultCount() {
        return streamOnlyFetchSize && defaultFetchSize != 0
                ? withoutFetchSize().getResultCount()
                : Query.super.getResultCount();
    }

    protected void close(@Nonnull ResultSet resultSet, @Nonnull PreparedStatement statement) {
        close(resultSet, statement, null);
    }

    protected void close(@Nonnull ResultSet resultSet, @Nonnull PreparedStatement statement,
                          @Nullable Runnable streamingCleanup) {
        try {
            try {
                try {
                    resultSet.close();
                } finally {
                    if (streamingCleanup != null) {
                        streamingCleanup.run();
                    }
                }
            } finally {
                if (closeStatement()) {
                    statement.close();
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Returns true if the query is version aware, false otherwise.
     *
     * @return true if the query is version aware, false otherwise.
     */
    @Override
    public boolean isVersionAware() {
        return versionAware;
    }

    /**
     * Execute a command, such as an INSERT, UPDATE, or DELETE statement.
     *
     * @return the number of rows impacted as result of the statement.
     * @throws PersistenceException if the statement fails.
     */
    @Override
    public int executeUpdate() {
        var observation = observe(ExecutionKind.UPDATE);
        try {
            PreparedStatement statement = getStatement();
            try {
                try {
                    int result = statement.executeUpdate();
                    invalidateAffectedEntityCaches();
                    return result;
                } finally {
                    if (closeStatement()) {
                        statement.close();
                    }
                }
            } catch (SQLException e) {
                throw exceptionTransformer.apply(e);
            }
        } catch (Throwable t) {
            observationError(observation, t);
            throw t;
        } finally {
            closeObservation(observation);
        }
    }

    /**
     * Invalidates the entity cache for the type affected by this INSERT, UPDATE, or DELETE operation.
     *
     * <p>If the affected type is known, only the cache for that type is cleared. If the affected type is unknown
     * (e.g., for raw SQL mutations), all entity caches are cleared to ensure dirty checking does not rely on stale
     * observed state.</p>
     */
    @SuppressWarnings("unchecked")
    private void invalidateAffectedEntityCaches() {
        if (managed) {
            return;  // Caller is managing cache.
        }
        var context = TransactionScope.peekContext(environment.transactionTemplateProvider());
        if (context == null) {
            return;
        }
        if (affectedType == null) {
            // Unknown affected type: clear all caches to avoid stale observed state.
            context.clearAllEntityCaches();
        } else if (Entity.class.isAssignableFrom(affectedType)) {
            var cache = context.findEntityCache((Class<? extends Entity<?>>) affectedType);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    /**
     * Execute a batch of commands.
     *
     * @return an array of update counts containing one element for each command in the batch. The elements of the
     * array are ordered according to the order in which commands were added to the batch, following
     * {@code Statement.executeBatch} semantics.
     * @throws PersistenceException if the batch fails.
     */
    @Override
    public int[] executeBatch() {
        var observation = observe(ExecutionKind.BATCH);
        try {
            PreparedStatement statement = getStatement();
            try {
                try {
                    int[] result = statement.executeBatch();
                    invalidateAffectedEntityCaches();
                    return result;
                } finally {
                    if (closeStatement()) {
                        statement.close();
                    }
                }
            } catch (SQLException e) {
                throw exceptionTransformer.apply(e);
            }
        } catch (Throwable t) {
            observationError(observation, t);
            throw t;
        } finally {
            closeObservation(observation);
        }
    }

    /**
     * Returns a spliterator that yields raw {@code Object[]} rows from the ResultSet. End-of-stream is signaled by
     * {@code tryAdvance} returning {@code false}, which allows individual row values to be {@code null} without
     * prematurely terminating the stream.
     */
    private Spliterator<Object[]> rawRowSpliterator(@Nonnull ResultSet resultSet, int columnCount) {
        return new Spliterators.AbstractSpliterator<>(Long.MAX_VALUE, Spliterator.ORDERED) {
            @Override
            public boolean tryAdvance(@Nonnull Consumer<? super Object[]> action) {
                try {
                    if (!resultSet.next()) {
                        return false;
                    }
                    Object[] row = new Object[columnCount];
                    for (int i = 0; i < columnCount; i++) {
                        row[i] = resultSet.getObject(i + 1);
                    }
                    action.accept(row);
                    return true;
                } catch (SQLException e) {
                    throw exceptionTransformer.apply(new PersistenceException(e));
                } catch (Exception e) {
                    throw exceptionTransformer.apply(e);
                }
            }
        };
    }

    /**
     * Returns a spliterator that yields mapped rows from the ResultSet. End-of-stream is signaled by {@code tryAdvance}
     * returning {@code false}, which allows the mapper to legitimately return {@code null} (e.g. a value-type
     * pass-through for a column whose value is SQL NULL) without prematurely terminating the stream.
     */
    protected <T> Spliterator<T> rowSpliterator(@Nonnull ResultSet resultSet, int columnCount, @Nonnull ObjectMapper<T> mapper) {
        Class<?>[] types;
        try {
            types = mapper.getParameterTypes();
        } catch (SqlTemplateException e) {
            throw new PersistenceException(e);
        }
        var columnSkipper = mapper.columnSkipper();
        var calendarSupplier = lazy(() -> Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC)));
        // Resolve the per-column reader once per query rather than re-evaluating the target-type dispatch for every
        // column of every row. The column types are fixed for the lifetime of the result set.
        ColumnReader[] readers = new ColumnReader[columnCount];
        for (int i = 0; i < columnCount; i++) {
            readers[i] = columnReaderFor(types[i]);
        }
        return new Spliterators.AbstractSpliterator<>(Long.MAX_VALUE, Spliterator.ORDERED) {
            @Override
            public boolean tryAdvance(@Nonnull Consumer<? super T> action) {
                try {
                    if (!resultSet.next()) {
                        return false;
                    }
                    Object[] args = new Object[columnCount];
                    if (columnSkipper != null) {
                        // Skip decoding non-key columns of entities that are already cached.
                        columnSkipper.readRow(args, index ->
                                readers[index].read(resultSet, index + 1, calendarSupplier));
                    } else {
                        for (int i = 0; i < columnCount; i++) {
                            args[i] = readers[i].read(resultSet, i + 1, calendarSupplier);
                        }
                    }
                    action.accept(mapper.newInstance(args));
                    return true;
                } catch (SQLException e) {
                    throw exceptionTransformer.apply(new PersistenceException(e));
                } catch (Exception e) {
                    throw exceptionTransformer.apply(e);
                }
            }
        };
    }

    /**
     * Reads a single column value from a result set, decoding it to the resolved target type. One reader is resolved
     * per column (see {@link #columnReaderFor}) and reused for every row, so the target-type dispatch is not repeated
     * for every column of every row.
     */
    @FunctionalInterface
    private interface ColumnReader {
        Object read(@Nonnull ResultSet rs, int columnIndex, @Nonnull Supplier<Calendar> calendarSupplier)
                throws SQLException;
    }

    /**
     * Cache of resolved column readers, keyed by target type. Readers are stateless (the calendar is supplied per
     * call), so a single instance per type is shared across every query for the lifetime of the VM. The value is
     * computed at most once per type and read on a fast, lock-free path.
     */
    private static final ClassValue<ColumnReader> COLUMN_READERS = new ClassValue<>() {
        @Override
        protected ColumnReader computeValue(@Nonnull Class<?> type) {
            return buildColumnReader(type);
        }
    };

    /**
     * Resolves the column reader for the given target type, computing it at most once per type for the lifetime of
     * the VM.
     */
    private static ColumnReader columnReaderFor(@Nonnull Class<?> targetType) {
        return COLUMN_READERS.get(targetType);
    }

    /**
     * Builds the column reader for the given target type. Primitive-returning JDBC getters honour
     * {@link ResultSet#wasNull()} so that a SQL NULL maps to {@code null} rather than the getter's zero value;
     * reference-returning getters already yield {@code null} for SQL NULL, so no {@code wasNull} probe is needed.
     */
    private static ColumnReader buildColumnReader(@Nonnull Class<?> targetType) {
        return switch (targetType) {
            // Ordered most-common-first. Resolution is paid once per column per query, so this mainly trims the
            // setup path, but keeping the frequent types (identifiers, names, flags) at the front is free.
            case Class<?> c when c == String.class -> (rs, i, cal) -> rs.getString(i);
            case Class<?> c when c == Integer.TYPE || c == Integer.class ->
                    (rs, i, cal) -> { int v = rs.getInt(i); return rs.wasNull() ? null : v; };
            case Class<?> c when c == Long.TYPE || c == Long.class ->
                    (rs, i, cal) -> { long v = rs.getLong(i); return rs.wasNull() ? null : v; };
            case Class<?> c when c == Boolean.TYPE || c == Boolean.class ->
                    (rs, i, cal) -> { boolean v = rs.getBoolean(i); return rs.wasNull() ? null : v; };
            case Class<?> c when c == Double.TYPE || c == Double.class ->
                    (rs, i, cal) -> { double v = rs.getDouble(i); return rs.wasNull() ? null : v; };
            // Remaining numeric primitives. wasNull distinguishes a genuine zero from a SQL NULL.
            case Class<?> c when c == Short.TYPE || c == Short.class ->
                    (rs, i, cal) -> { short v = rs.getShort(i); return rs.wasNull() ? null : v; };
            case Class<?> c when c == Float.TYPE || c == Float.class ->
                    (rs, i, cal) -> { float v = rs.getFloat(i); return rs.wasNull() ? null : v; };
            case Class<?> c when c == Byte.TYPE || c == Byte.class ->
                    (rs, i, cal) -> { byte v = rs.getByte(i); return rs.wasNull() ? null : v; };
            case Class<?> c when c == BigDecimal.class -> (rs, i, cal) -> rs.getBigDecimal(i);
            case Class<?> c when c == ByteBuffer.class -> (rs, i, cal) -> {
                byte[] bytes = rs.getBytes(i);
                return bytes != null ? ByteBuffer.wrap(bytes).asReadOnlyBuffer() : null;
            };
            case Class<?> c when c == UUID.class -> (rs, i, cal) -> {
                Object obj = rs.getObject(i);
                return obj instanceof UUID u ? u : obj != null ? UUID.fromString(obj.toString()) : null;
            };
            case Class<?> c when c.isEnum() -> (rs, i, cal) -> rs.getString(i); // Enum handled by mapper.
            case Class<?> c when c == java.util.Date.class -> (rs, i, cal) -> {
                Timestamp ts = rs.getTimestamp(i, cal.get());
                return ts != null ? new java.util.Date(ts.getTime()) : null;
            };
            case Class<?> c when c == Calendar.class -> (rs, i, cal) -> {
                Timestamp ts = rs.getTimestamp(i, cal.get());
                if (ts == null) return null;
                Calendar out = (Calendar) cal.get().clone();
                out.setTimeInMillis(ts.getTime());
                return out;
            };
            case Class<?> c when c == Timestamp.class     -> (rs, i, cal) -> rs.getTimestamp(i, cal.get());
            case Class<?> c when c == java.sql.Date.class -> (rs, i, cal) -> rs.getDate(i);
            case Class<?> c when c == Time.class          -> (rs, i, cal) -> rs.getTime(i);
            // java.time using vendor-safe approach.
            case Class<?> c when c == LocalDateTime.class -> (rs, i, cal) -> {
                Timestamp ts = rs.getTimestamp(i);
                return ts != null ? ts.toLocalDateTime() : null;
            };
            case Class<?> c when c == LocalDate.class -> (rs, i, cal) -> {
                java.sql.Date d = rs.getDate(i);
                return d != null ? d.toLocalDate() : null;
            };
            case Class<?> c when c == LocalTime.class -> (rs, i, cal) -> {
                Time t = rs.getTime(i);
                return t != null ? t.toLocalTime() : null;
            };
            case Class<?> c when c == Instant.class -> (rs, i, cal) -> {
                Timestamp ts = rs.getTimestamp(i, cal.get());
                return ts != null ? ts.toInstant() : null;
            };
            case Class<?> c when c == OffsetDateTime.class -> (rs, i, cal) -> {
                Timestamp ts = rs.getTimestamp(i, cal.get());
                return ts != null ? OffsetDateTime.ofInstant(ts.toInstant(), ZoneOffset.UTC) : null;
            };
            case Class<?> c when c == ZonedDateTime.class -> (rs, i, cal) -> {
                Timestamp ts = rs.getTimestamp(i, cal.get());
                return ts != null ? ZonedDateTime.ofInstant(ts.toInstant(), ZoneOffset.UTC) : null;
            };
            default -> (rs, i, cal) -> {
                Object value = rs.getObject(i);
                return rs.wasNull() ? null : value;
            };
        };
    }

    @Override
    public String toString() {
        var statement = getStatement();
        try {
            try {
                return "Query@%s wrapping %s".formatted(toHexString(identityHashCode(this)), statement);
            } finally {
                if (closeStatement()) {
                    statement.close();
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Describes a statement execution for the query observer.
     */
    private record QueryContextImpl(@Nonnull SqlOperation operation,
                                    @Nullable Class<? extends Data> type,
                                    @Nonnull ExecutionKind kind,
                                    @Nullable String statementText) implements QueryContext {
        @Override
        public Optional<Class<? extends Data>> dataType() {
            return ofNullable(type);
        }

        @Override
        public OptionalInt batchSize() {
            return OptionalInt.empty();
        }

        @Override
        public Optional<String> statement() {
            return ofNullable(statementText);
        }
    }
}
