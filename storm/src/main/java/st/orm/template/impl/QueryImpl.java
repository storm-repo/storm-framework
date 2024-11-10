package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.PersistenceException;
import st.orm.PreparedQuery;
import st.orm.Query;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.util.Optional.ofNullable;
import static java.util.stream.Stream.generate;
import static st.orm.template.impl.ObjectMapperFactory.getObjectMapper;

class QueryImpl implements Query {
    private final LazyFactory lazyFactory;
    private final Supplier<PreparedStatement> statement;
    private final BindVarsHandle bindVarsHandle;
    private final boolean versionAware;

    QueryImpl(@Nonnull LazyFactory lazyFactory,
              @Nonnull Supplier<PreparedStatement> statement,
              @Nullable BindVarsHandle bindVarsHandle,
              boolean versionAware) {
        this.lazyFactory = lazyFactory;
        this.statement = statement;
        this.bindVarsHandle = bindVarsHandle;
        this.versionAware = versionAware;
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
        return MonitoredResource.wrap(new PreparedQueryImpl(lazyFactory, statement.get(), bindVarsHandle, versionAware));
    }

    private PreparedStatement getStatement() {
        return statement.get();
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
                                    .onClose(() -> close(resultSet)));
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
                var mapper = getObjectMapper(columnCount, type, lazyFactory)
                        .orElseThrow(() -> new PersistenceException(STR."No suitable constructor found for \{type}."));
                close = false;
                return MonitoredResource.wrap(
                        generate(() -> readNext(resultSet, columnCount, mapper))
                                .takeWhile(Objects::nonNull)
                                .onClose(() -> close(resultSet)));
            } finally {
                if (close && closeStatement()) {
                    statement.close();
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(e);
        }
    }

    protected void close(@Nonnull ResultSet resultSet) {
        try {
            try {
                resultSet.close();
            } finally {
                if (closeStatement()) {
                    getStatement().close();
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
    @SuppressWarnings({"rawtypes", "unchecked"})
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
                        case Class<?> c when c.isEnum() -> ofNullable(resultSet.getString(i + 1))
                                .map(s -> {
                                    try {
                                        return Enum.valueOf((Class<? extends Enum>) c, s);
                                    } catch (IllegalArgumentException e) {
                                        throw new PersistenceException(STR."No enum constant \{c.getName()} for value '\{s}'.");
                                    }
                                })
                                .orElse(null);
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
