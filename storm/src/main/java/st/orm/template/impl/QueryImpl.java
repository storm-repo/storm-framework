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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.PersistenceException;
import st.orm.PreparedQuery;
import st.orm.Query;
import st.orm.Ref;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.util.stream.Stream.generate;
import static st.orm.template.impl.ObjectMapperFactory.getObjectMapper;

class QueryImpl implements Query {
    private final RefFactory refFactory;
    private final Function<Boolean, PreparedStatement> statement;
    private final BindVarsHandle bindVarsHandle;
    private final boolean versionAware;
    private final boolean safe;

    QueryImpl(@Nonnull RefFactory refFactory,
              @Nonnull Function<Boolean, PreparedStatement> statement,
              @Nullable BindVarsHandle bindVarsHandle,
              boolean versionAware) {
        this(refFactory, statement, bindVarsHandle, versionAware, false);
    }

    QueryImpl(@Nonnull RefFactory refFactory,
              @Nonnull Function<Boolean, PreparedStatement> statement,
              @Nullable BindVarsHandle bindVarsHandle,
              boolean versionAware,
              boolean safe) {
        this.refFactory = refFactory;
        this.statement = statement;
        this.bindVarsHandle = bindVarsHandle;
        this.versionAware = versionAware;
        this.safe = safe;
    }

    /**
     * Prepares the query for execution.
     *
     * <p>Queries are normally constructed in a lazy fashion, unlike prepared queries which are constructed eagerly.
     * Prepared queries allow the use of bind variables and enable reading generated keys after row insertion.</p>
     *
     * <p>Note that the prepared query must be closed after usage to prevent resource leaks. As the prepared query is
     * AutoCloseable, it is recommended to use it within a try-with-resources block.</p>
     *
     * @return the prepared query.
     * @throws PersistenceException if the query preparation fails.
     */
    @Override
    public PreparedQuery prepare() {
        return MonitoredResource.wrap(new PreparedQueryImpl(refFactory, statement.apply(safe), bindVarsHandle, versionAware));
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
        return new QueryImpl(refFactory, statement, bindVarsHandle, versionAware, true);
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
     * <p>Note that calling this method does trigger the execution of the underlying query, so it should only be invoked
     * when the query is intended to run. Since the stream holds resources open while in use, it must be closed after
     * usage to prevent resource leaks. As the stream is AutoCloseable, it is recommended to use it within a
     * try-with-resources block.</p>
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
                            generate(() -> readNext(resultSet, columnCount))
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
        } catch (SQLException e) {
            throw new PersistenceException(e);
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
     * <p>Note that calling this method does trigger the execution of the underlying query, so it should only be invoked
     * when the query is intended to run. Since the stream holds resources open while in use, it must be closed after
     * usage to prevent resource leaks. As the stream is AutoCloseable, it is recommended to use it within a
     * try-with-resources block.</p>
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
                        .orElseThrow(() -> new PersistenceException(STR."No suitable constructor found for \{type}."));
                close = false;
                return MonitoredResource.wrap(
                        generate(() -> readNext(resultSet, columnCount, mapper))
                                .takeWhile(Objects::nonNull)
                                .onClose(() -> close(resultSet, statement)));
            } finally {
                if (close && closeStatement()) {
                    statement.close();
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Execute a SELECT query and return the resulting rows as a stream of ref instances.
     *
     * <p>Each element in the stream represents a row in the result, where the columns of the row are mapped to the
     * constructor arguments primary key type.</p>
     *
     * <p>Note that calling this method does trigger the execution of the underlying query, so it should only be invoked
     * when the query is intended to run. Since the stream holds resources open while in use, it must be closed after
     * usage to prevent resource leaks. As the stream is AutoCloseable, it is recommended to use it within a
     * try-with-resources block.</p>
     *
     * @param type the type of the results that are being referenced.
     * @param pkType the primary key type.
     * @return a stream of ref instances.
     * @throws PersistenceException if the query fails.
     * @since 1.3
     */
    @Override
    public <T extends Record> Stream<Ref<T>> getRefStream(@Nonnull Class<T> type, @Nonnull Class<?> pkType) {
        return getResultStream(pkType)
                .map(id -> id == null ? Ref.ofNull() : refFactory.create(type, id));
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
            throw new PersistenceException(e);
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
            throw new PersistenceException(e);
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
            for (int i = 0; i < columnCount; i++) {
                if (Date.class == types[i]) {
                    // Special case because would return a java.sql.Date by default.
                    Timestamp timestamp = resultSet.getTimestamp(i + 1);
                    if (timestamp != null) {
                        args[i] = new Date(timestamp.getTime());
                    } else {
                        args[i] = null;
                    }
                } else {
                    args[i] = switch (types[i]) {
                        case Class<?> c when c == Short.TYPE -> resultSet.getShort(i + 1);
                        case Class<?> c when c == Integer.TYPE -> resultSet.getInt(i + 1);
                        case Class<?> c when c == Long.TYPE -> resultSet.getLong(i + 1);
                        case Class<?> c when c == Float.TYPE -> resultSet.getFloat(i + 1);
                        case Class<?> c when c == Double.TYPE -> resultSet.getDouble(i + 1);
                        case Class<?> c when c == Byte.TYPE -> resultSet.getByte(i + 1);
                        case Class<?> c when c == Boolean.TYPE -> resultSet.getBoolean(i + 1);
                        case Class<?> c when c == String.class -> resultSet.getString(i + 1);
                        case Class<?> c when c.isEnum() -> resultSet.getString(i + 1);  // Enum is read as string and taken care of by object mapper.
                        default -> resultSet.getObject(i + 1, types[i]);
                    };
                    if (resultSet.wasNull()) {
                        args[i] = null;
                    }
                }
            }
            return mapper.newInstance(args);
        } catch (SQLException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public String toString() {
        var statement = getStatement();
        try {
            try {
                return STR."Query@\{toHexString(identityHashCode(this))} wrapping \{statement}";
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
