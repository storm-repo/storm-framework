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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.Data;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.core.spi.RefFactory;
import st.orm.core.template.PreparedQuery;
import st.orm.core.template.Query;
import st.orm.core.template.SqlTemplateException;

import java.math.BigDecimal;
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
import java.util.Objects;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.util.stream.Stream.generate;
import static st.orm.core.template.impl.LazySupplier.lazy;
import static st.orm.core.template.impl.ObjectMapperFactory.getObjectMapper;

@SuppressWarnings("ALL")
class QueryImpl implements Query {
    private final RefFactory refFactory;
    private final Function<Boolean, PreparedStatement> statement;
    private final BindVarsHandle bindVarsHandle;
    private final boolean versionAware;
    private final boolean safe;
    private final Function<Throwable, PersistenceException> exceptionTransformer;

    QueryImpl(@Nonnull RefFactory refFactory,
              @Nonnull Function<Boolean, PreparedStatement> statement,
              @Nullable BindVarsHandle bindVarsHandle,
              boolean versionAware,
              @Nonnull Function<Throwable, PersistenceException> exceptionTransformer) {
        this(refFactory, statement, bindVarsHandle, versionAware, false, exceptionTransformer);
    }

    QueryImpl(@Nonnull RefFactory refFactory,
              @Nonnull Function<Boolean, PreparedStatement> statement,
              @Nullable BindVarsHandle bindVarsHandle,
              boolean versionAware,
              boolean safe,
              @Nonnull Function<Throwable, PersistenceException> exceptionTransformer) {
        this.refFactory = refFactory;
        this.statement = statement;
        this.bindVarsHandle = bindVarsHandle;
        this.versionAware = versionAware;
        this.safe = safe;
        this.exceptionTransformer = exceptionTransformer;
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
        return MonitoredResource.wrap(new PreparedQueryImpl(refFactory, statement.apply(safe), bindVarsHandle, versionAware, exceptionTransformer));
    }

    /**
     * Returns a new query that is marked as safe. This means that dangerous operations, such as DELETE and UPDATE
     * without a WHERE clause, will be allowed.
     *
     * @return a new query that is marked as safe.
     * @since 1.2
     */
    @Override
    public Query safe() {
        return new QueryImpl(refFactory, statement, bindVarsHandle, versionAware, true, exceptionTransformer);
    }

    private PreparedStatement getStatement() {
        return statement.apply(safe);
    }

    protected boolean closeStatement() {
        return true;
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
        try {
            PreparedStatement statement = getStatement();
            boolean close = true;
            try {
                ResultSet resultSet = statement.executeQuery();
                try {
                    int columnCount = resultSet.getMetaData().getColumnCount();
                    close = false;
                    return MonitoredResource.wrap(
                            generate(() -> {
                                try {
                                    return readNext(resultSet, columnCount);
                                } catch (Exception e) {
                                    throw exceptionTransformer.apply(e);
                                }
                            })
                                    .takeWhile(Objects::nonNull)
                                    .onClose(() -> close(resultSet, statement)));
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
        PreparedStatement statement = getStatement();
        boolean close = true;
        try {
            try {
                ResultSet resultSet = statement.executeQuery();
                int columnCount = resultSet.getMetaData().getColumnCount();
                var mapper = getObjectMapper(columnCount, type, refFactory)
                        .orElseThrow(() -> new SqlTemplateException("No suitable constructor found for %s.".formatted(type.getName())));
                close = false;
                return MonitoredResource.wrap(
                        generate(() -> {
                            try {
                                return readNext(resultSet, columnCount, mapper);
                            } catch (Exception e) {
                                throw exceptionTransformer.apply(e);
                            }
                        })
                                .takeWhile(Objects::nonNull)
                                .onClose(() -> close(resultSet, statement)));
            } finally {
                if (close && closeStatement()) {
                    statement.close();
                }
            }
        } catch (Exception e) {
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
        return getResultStream(pkType)
                .map(pk -> pk == null ? null : refFactory.create(type, pk));
    }

    protected void close(@Nonnull ResultSet resultSet, @Nonnull PreparedStatement statement) {
        try {
            try {
                resultSet.close();
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
     * Execute a command, such as an INSERT, UPDATE or DELETE statement.
     *
     * @return the number of rows impacted as result of the statement.
     * @throws PersistenceException if the statement fails.
     */
    @Override
    public int executeUpdate() {
        PreparedStatement statement = getStatement();
        try {
            try {
                return statement.executeUpdate();
            } finally {
                if (closeStatement()) {
                    statement.close();
                }
            }
        } catch (SQLException e) {
            throw exceptionTransformer.apply(e);
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
        PreparedStatement statement = getStatement();
        try {
            try {
                return statement.executeBatch();
            } finally {
                if (closeStatement()) {
                    statement.close();
                }
            }
        } catch (SQLException e) {
            throw exceptionTransformer.apply(e);
        }
    }

    /**
     * Reads the next row from the ResultSet or returns null if no more rows are available.
     *
     * @param resultSet   result set to read from.
     * @param columnCount number of columns in ResultSet.
     * @return the next row from the ResultSet or null if no more rows are available.
     */
    private Object[] readNext(@Nonnull ResultSet resultSet, int columnCount) {
        try {
            if (!resultSet.next()) {
                return null;
            }
            Object[] row = new Object[columnCount];
            for (int i = 0; i < columnCount; i++) {
                row[i] = resultSet.getObject(i + 1);
            }
            return row;
        } catch (SQLException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Reads the next row from the ResultSet or returns null if no more rows are available.
     *
     * @param resultSet   result set to read from.
     * @param columnCount number of columns in ResultSet.
     * @param mapper      mapper to use for creating instances.
     * @return the next row from the ResultSet or null if no more rows are available.
     */
    protected <T> T readNext(@Nonnull ResultSet resultSet, int columnCount, @Nonnull ObjectMapper<T> mapper) {
        try {
            if (!resultSet.next()) {
                return null;
            }
            Object[] args = new Object[columnCount];
            Class<?>[] types = mapper.getParameterTypes();
            var calendarSupplier = lazy(() -> Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC)));
            for (int i = 0; i < columnCount; i++) {
                args[i] = readColumnValue(resultSet, i + 1, types[i], calendarSupplier);
            }
            return mapper.newInstance(args);
        } catch (SQLException e) {
            throw new PersistenceException(e);
        }
    }

    private static Object readColumnValue(
            @Nonnull ResultSet rs,
            int columnIndex,
            @Nonnull Class<?> targetType,
            @Nonnull Supplier<Calendar> calendarSupplier
    ) throws SQLException {
        Object value = switch (targetType) {
            // Primitives & basics.
            case Class<?> c when c == Short.TYPE || c == Short.class     -> rs.getShort(columnIndex);
            case Class<?> c when c == Integer.TYPE || c == Integer.class -> rs.getInt(columnIndex);
            case Class<?> c when c == Long.TYPE || c == Long.class       -> rs.getLong(columnIndex);
            case Class<?> c when c == Float.TYPE || c == Float.class     -> rs.getFloat(columnIndex);
            case Class<?> c when c == Double.TYPE || c == Double.class   -> rs.getDouble(columnIndex);
            case Class<?> c when c == Byte.TYPE || c == Byte.class       -> rs.getByte(columnIndex);
            case Class<?> c when c == Boolean.TYPE || c == Boolean.class -> rs.getBoolean(columnIndex);
            case Class<?> c when c == String.class                       -> rs.getString(columnIndex);
            case Class<?> c when c == BigDecimal.class                   -> rs.getBigDecimal(columnIndex);
            case Class<?> c when c == byte[].class                       -> rs.getBytes(columnIndex);
            case Class<?> c when c.isEnum()                              -> rs.getString(columnIndex); // Enum handled by mapper.
            case Class<?> c when c == java.util.Date.class -> {
                Timestamp ts = rs.getTimestamp(columnIndex, calendarSupplier.get());
                yield ts != null ? new java.util.Date(ts.getTime()) : null;
            }
            case Class<?> c when c == Calendar.class -> {
                Timestamp ts = rs.getTimestamp(columnIndex, calendarSupplier.get());
                if (ts == null) yield null;
                Calendar out = (Calendar) calendarSupplier.get().clone();
                out.setTimeInMillis(ts.getTime());
                yield out;
            }
            case Class<?> c when c == Timestamp.class     -> rs.getTimestamp(columnIndex, calendarSupplier.get());
            case Class<?> c when c == java.sql.Date.class -> rs.getDate(columnIndex);
            case Class<?> c when c == Time.class          -> rs.getTime(columnIndex);
            // java.time using vendor-safe approach.
            case Class<?> c when c == LocalDateTime.class -> {
                Timestamp ts = rs.getTimestamp(columnIndex);
                yield ts != null ? ts.toLocalDateTime() : null;
            }
            case Class<?> c when c == LocalDate.class -> {
                java.sql.Date d = rs.getDate(columnIndex);
                yield d != null ? d.toLocalDate() : null;
            }
            case Class<?> c when c == LocalTime.class -> {
                Time t = rs.getTime(columnIndex);
                yield t != null ? t.toLocalTime() : null;
            }
            case Class<?> c when c == Instant.class -> {
                Timestamp ts = rs.getTimestamp(columnIndex, calendarSupplier.get());
                yield ts != null ? ts.toInstant() : null;
            }
            case Class<?> c when c == OffsetDateTime.class -> {
                Timestamp ts = rs.getTimestamp(columnIndex, calendarSupplier.get());
                yield ts != null ? OffsetDateTime.ofInstant(ts.toInstant(), ZoneOffset.UTC) : null;
            }
            case Class<?> c when c == ZonedDateTime.class -> {
                Timestamp ts = rs.getTimestamp(columnIndex, calendarSupplier.get());
                yield ts != null ? ZonedDateTime.ofInstant(ts.toInstant(), ZoneOffset.UTC) : null;
            }
            default -> rs.getObject(columnIndex);
        };
        if (rs.wasNull()) return null;
        return value;
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
}
