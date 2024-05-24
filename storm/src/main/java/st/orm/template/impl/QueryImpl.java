package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.PersistenceException;
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

    @Override
    public PreparedQuery prepare() {
        return new PreparedQueryImpl(lazyFactory, statement.get(), bindVarsHandle, versionAware);
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
     * @return the result stream.
     * @throws PersistenceException if the query fails.
     */
    @Override
    public Stream<Object[]> getResultStream() {
        try {
            boolean close = true;
            PreparedStatement statement = getStatement();
            try {
                ResultSet resultSet = getStatement().executeQuery();
                try {
                    int columnCount = resultSet.getMetaData().getColumnCount();
                    // The result set will be closed when the stream is closed, but also when readNext returns null.
                    Stream<Object[]> stream = generate(() -> readNext(resultSet, columnCount))
                            .takeWhile(Objects::nonNull)
                            .onClose(() -> {
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
                            });
                    close = false;
                    return AutoClosingStreamProxy.wrap(stream);
                } finally {
                    if (close) {
                        resultSet.close();
                    }
                }
            } finally {
                if (close) {
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
     * @param type the type of the result.
     * @return the result stream.
     * @throws PersistenceException if the query fails.
     */
    @Override
    public <T> Stream<T> getResultStream(@Nonnull Class<T> type) {
        try {
            boolean close = true;
            PreparedStatement statement = getStatement();
            try {
                ResultSet resultSet = statement.executeQuery();
                try {
                    int columnCount = resultSet.getMetaData().getColumnCount();
                    var mapper = getObjectMapper(columnCount, type, lazyFactory)
                            .orElseThrow(() -> new PersistenceException(STR."No suitable constructor found for \{type}."));
                    // The result set will be closed when the stream is closed, but also when readNext returns null.
                    Stream<T> stream = generate(() -> readNext(resultSet, columnCount, mapper))
                            .takeWhile(Objects::nonNull)
                            .onClose(() -> {
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
                            });
                    close = false;
                    return AutoClosingStreamProxy.wrap(stream);
                } finally {
                    if (close) {
                        resultSet.close();
                    }
                }
            } finally {
                if (close) {
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
                resultSet.close(); // Close as soon as we can as method is idempotent.
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
                resultSet.close(); // Close as soon as we can as method is idempotent.
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
