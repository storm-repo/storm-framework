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
import st.orm.BindVars;
import st.orm.PersistenceException;
import st.orm.Query;
import st.orm.spi.Provider;
import st.orm.spi.Providers;
import st.orm.spi.QueryFactory;
import st.orm.template.ColumnNameResolver;
import st.orm.template.ForeignKeyResolver;
import st.orm.template.ORMTemplate;
import st.orm.template.PreparedStatementTemplate;
import st.orm.template.Sql;
import st.orm.template.SqlTemplate;
import st.orm.template.SqlTemplate.BatchListener;
import st.orm.template.SqlTemplate.Parameter;
import st.orm.template.SqlTemplateException;
import st.orm.template.TableAliasResolver;
import st.orm.template.TableNameResolver;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static st.orm.template.SqlTemplate.PS;

public final class PreparedStatementTemplateImpl implements PreparedStatementTemplate, QueryFactory {

    @FunctionalInterface
    private interface TemplateProcessor {
        PreparedStatement process(@Nonnull Sql sql,
                                  boolean safe) throws SQLException;
    }

    private final TemplateProcessor templateProcessor;
    private final ModelBuilder modelBuilder;
    private final TableAliasResolver tableAliasResolver;
    private final Predicate<Provider> providerFilter;
    private final RefFactory refFactory;

    public PreparedStatementTemplateImpl(@Nonnull DataSource dataSource) {
        // Note that this logic does not use Spring's DataSourceUtils, so it is not aware of Spring's transaction
        // management.
        templateProcessor = (sql, safe) -> {
            if (!safe) {
                sql.unsafeWarning().ifPresent(warning -> {
                    throw new PersistenceException(STR."\{warning} Use Query.safe() to mark query as safe.");
                });
            }
            var statement = sql.statement();
            var parameters = sql.parameters();
            var bindVariables = sql.bindVariables().orElse(null);
            var generatedKeys = sql.generatedKeys();
            Connection connection = getConnection(dataSource);
            PreparedStatement preparedStatement = null;
            try {
                if (!generatedKeys.isEmpty()) {
                    try {
                        //noinspection SqlSourceToSinkFlow
                        preparedStatement = connection.prepareStatement(statement, generatedKeys.toArray(new String[0]));
                    } catch (SQLFeatureNotSupportedException ignore) {}
                }
                if (preparedStatement == null) {
                    //noinspection SqlSourceToSinkFlow
                    preparedStatement = connection.prepareStatement(statement);
                }
                if (bindVariables == null) {
                    setParameters(preparedStatement, parameters);
                } else {
                    bindVariables.setBatchListener(getBatchListener(preparedStatement, parameters));
                }
            } finally {
                if (preparedStatement == null) {
                    releaseConnection(connection, dataSource);
                }
            }
            return createProxy(preparedStatement, connection, dataSource);
        };
        this.modelBuilder = ModelBuilder.newInstance();
        this.tableAliasResolver = TableAliasResolver.DEFAULT;
        this.providerFilter = null;
        this.refFactory = new RefFactoryImpl(this, modelBuilder, null);
    }

    public PreparedStatementTemplateImpl(@Nonnull Connection connection) {
        templateProcessor = (sql, safe) -> {
            if (!safe) {
                sql.unsafeWarning().ifPresent(warning -> {
                    throw new PersistenceException(STR."\{warning} Use Query.safe() to mark query as safe.");
                });
            }
            var statement = sql.statement();
            var parameters = sql.parameters();
            var bindVariables = sql.bindVariables().orElse(null);
            var generatedKeys = sql.generatedKeys();
            PreparedStatement preparedStatement = null;
            if (!generatedKeys.isEmpty()) {
                try {
                    //noinspection SqlSourceToSinkFlow
                    preparedStatement = connection.prepareStatement(statement, generatedKeys.toArray(new String[0]));
                } catch (SQLFeatureNotSupportedException ignore) {}
            }
            if (preparedStatement == null) {
                //noinspection SqlSourceToSinkFlow
                preparedStatement = connection.prepareStatement(statement);
            }
            if (bindVariables == null) {
                setParameters(preparedStatement, parameters);
            } else {
                bindVariables.setBatchListener(getBatchListener(preparedStatement, parameters));
            }
            return preparedStatement;
        };
        this.modelBuilder = ModelBuilder.newInstance();
        this.tableAliasResolver = TableAliasResolver.DEFAULT;
        this.providerFilter = null;
        this.refFactory = new RefFactoryImpl(this, modelBuilder, null);
    }

    private PreparedStatementTemplateImpl(@Nonnull TemplateProcessor templateProcessor,
                                          @Nonnull ModelBuilder modelBuilder,
                                          @Nonnull TableAliasResolver tableAliasResolver,
                                          @Nullable Predicate<Provider> providerFilter) {
        this.templateProcessor = templateProcessor;
        this.modelBuilder = modelBuilder;
        this.tableAliasResolver = tableAliasResolver;
        this.providerFilter = providerFilter;
        this.refFactory = new RefFactoryImpl(this, modelBuilder, providerFilter);
    }

    /**
     * Returns a new prepared statement template with the specified table name resolver.
     *
     * @param tableNameResolver the table name resolver.
     * @return a new prepared statement template.
     */
    @Override
    public PreparedStatementTemplateImpl withTableNameResolver(@Nullable TableNameResolver tableNameResolver) {
        return new PreparedStatementTemplateImpl(templateProcessor, modelBuilder.tableNameResolver(tableNameResolver), tableAliasResolver, providerFilter);
    }

    /**
     * Returns a new prepared statement template with the specified column name resolver.
     *
     * @param columnNameResolver the column name resolver.
     * @return a new prepared statement template.
     */
    @Override
    public PreparedStatementTemplateImpl withColumnNameResolver(@Nullable ColumnNameResolver columnNameResolver) {
        return new PreparedStatementTemplateImpl(templateProcessor, modelBuilder.columnNameResolver(columnNameResolver), tableAliasResolver, providerFilter);
    }

    /**
     * Returns a new prepared statement template with the specified foreign key resolver.
     *
     * @param foreignKeyResolver the foreign key resolver.
     * @return a new prepared statement template.
     */
    @Override
    public PreparedStatementTemplateImpl withForeignKeyResolver(@Nullable ForeignKeyResolver foreignKeyResolver) {
        return new PreparedStatementTemplateImpl(templateProcessor, modelBuilder.foreignKeyResolver(foreignKeyResolver), tableAliasResolver, providerFilter);
    }

    /**
     * Returns a new prepared statement template with the specified table alias resolver.
     *
     * @param tableAliasResolver the table alias resolver.
     * @return a new prepared statement template.
     */
    @Override
    public PreparedStatementTemplate withTableAliasResolver(@Nonnull TableAliasResolver tableAliasResolver) {
        return new PreparedStatementTemplateImpl(templateProcessor, modelBuilder, tableAliasResolver, providerFilter);
    }

    /**
     * Returns a new prepared statement template with the specified provider filter.
     *
     * @param providerFilter the provider filter.
     * @return a new prepared statement template.
     */
    @Override
    public PreparedStatementTemplateImpl withProviderFilter(@Nullable Predicate<Provider> providerFilter) {
        return new PreparedStatementTemplateImpl(templateProcessor, modelBuilder, tableAliasResolver, providerFilter);
    }

    /**
     * Create a new bind variables instance that can be used to add bind variables to a batch.
     *
     * @return a new bind variables instance.
     */
    @Override
    public BindVars createBindVars() {
        return sqlTemplate().createBindVars();
    }

    private BatchListener getBatchListener(@Nonnull PreparedStatement preparedStatement,
                                           @Nonnull List<Parameter> parameters) {
        return batchParameters -> {
            try {
                setParameters(preparedStatement, parameters);
                setParameters(preparedStatement, batchParameters);
                preparedStatement.addBatch();
            } catch (SQLException e) {
                throw new PersistenceException(e);
            }
        };
    }

    private void setParameters(@Nonnull PreparedStatement preparedStatement,
                               @Nonnull List<? extends Parameter> parameters) throws SQLException {
        for (var parameter : parameters) {
            var dbValue = parameter.dbValue();
            switch (parameter) {
                case SqlTemplate.PositionalParameter p -> {
                    switch (dbValue) {
                        case null      -> preparedStatement.setObject(p.position(), null);
                        case Short s   -> preparedStatement.setShort(p.position(), s);
                        case Integer i -> preparedStatement.setInt(p.position(), i);
                        case Long l    -> preparedStatement.setLong(p.position(), l);
                        case Float f   -> preparedStatement.setFloat(p.position(), f);
                        case Double d  -> preparedStatement.setDouble(p.position(), d);
                        case Byte b    -> preparedStatement.setByte(p.position(), b);
                        case Boolean b -> preparedStatement.setBoolean(p.position(), b);
                        case String s  -> preparedStatement.setString(p.position(), s);
                        case java.sql.Date d -> preparedStatement.setDate(p.position(), d);
                        case java.sql.Time t -> preparedStatement.setTime(p.position(), t);
                        case java.sql.Timestamp t -> preparedStatement.setTimestamp(p.position(), t);
                        case Enum<?> e -> preparedStatement.setString(p.position(), e.name());
                        default -> preparedStatement.setObject(p.position(), dbValue);
                    }
                }
                case SqlTemplate.NamedParameter _ -> throw new SQLException("Named parameters not supported for PreparedStatement.");
            }
        }
    }

    private SqlTemplate sqlTemplate() {
        SqlTemplate template = PS
                .withTableNameResolver(modelBuilder.tableNameResolver())
                .withColumnNameResolver(modelBuilder.columnNameResolver())
                .withForeignKeyResolver(modelBuilder.foreignKeyResolver())
                .withTableAliasResolver(tableAliasResolver);
        if (providerFilter != null) {
            template = template.withDialect(Providers.getSqlDialect(providerFilter));
        }
        return template;
    }

    /**
     * Returns an ORM template that is backed by this prepared statement template.
     *
     * @return the ORM template.
     */
    @Override
    public ORMTemplate toORM() {
        return new ORMTemplateImpl(this, modelBuilder, providerFilter);
    }

    /**
     * Creates a proxy for the PreparedStatement that closes the connection when the PreparedStatement is closed.
     *
     * @param statement the PreparedStatement to create a proxy for.
     * @param connection the connection to close when the PreparedStatement is closed.
     * @return a proxy for the PreparedStatement that closes the connection when the PreparedStatement is closed.
     */
    private static PreparedStatement createProxy(@Nonnull PreparedStatement statement, @Nonnull Connection connection, @Nonnull DataSource dataSource) {
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[] { PreparedStatement.class },
                (_, method, args) -> {
                    // Check if the close method is being called on the PreparedStatement.
                    if (method.getName().equals("close")) {
                        try {
                            statement.close();
                        } finally {
                            releaseConnection(connection, dataSource);
                        }
                        return null;
                    }
                    try {
                        // For other methods, just invoke the method on the actual PreparedStatement.
                        return method.invoke(statement, args);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                }
        );
    }

    /**
     * Create a new query for the specified {@code template}.
     *
     * @param template the template to process.
     * @return a query that can be executed.
     * @throws PersistenceException if the template is invalid.
     */
    @Override
    public Query create(@Nonnull StringTemplate template) {
        try {
            var sql = sqlTemplate().process(template);
            var bindVariables = sql.bindVariables().orElse(null);
            return new QueryImpl(refFactory, safe -> {
                try {
                    return templateProcessor.process(sql, safe);
                } catch (SQLException e) {
                    throw new PersistenceException(e);
                }
            }, bindVariables == null ? null : bindVariables.getHandle(), sql.versionAware());
        } catch (SqlTemplateException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Creates a query for the specified query {@code template}.
     *
     * @param template the query template.
     * @return the query.
     */
    @Override
    public PreparedStatement query(@Nonnull StringTemplate template) throws SQLException {
        var sql = sqlTemplate().process(template);
        return templateProcessor.process(sql, true);    // We allow unsafe queries in direct JDBC mode.
    }

    private static final Method GET_CONNECTION_METHOD = ((Supplier<Method>) () -> {
        try {
            return Class.forName("org.springframework.jdbc.datasource.DataSourceUtils").getMethod("getConnection", DataSource.class);
        } catch (Throwable _) {
        }
        return null;
    }).get();

    private static final Method RELEASE_CONNECTION_METHOD =((Supplier<Method>) () -> {
        try {
            return Class.forName("org.springframework.jdbc.datasource.DataSourceUtils").getMethod("releaseConnection", Connection.class, DataSource.class);
        } catch (Throwable _) {
        }
        return null;
    }).get();

    private static Connection getConnection(@Nonnull DataSource dataSource) throws SQLException {
        if (GET_CONNECTION_METHOD != null) {
            try {
                try {
                    return (Connection) GET_CONNECTION_METHOD.invoke(null, dataSource);
                } catch (InvocationTargetException e) {
                    throw e.getTargetException();
                }
            } catch (SQLException e) {
                throw e;
            } catch (Throwable t) {
                throw new SQLException(t);
            }
        }
        return dataSource.getConnection();
    }

    private static void releaseConnection(@Nonnull Connection connection, @Nonnull DataSource dataSource) throws SQLException {
        if (RELEASE_CONNECTION_METHOD != null) {
            try {
                try {
                    RELEASE_CONNECTION_METHOD.invoke(null, connection, dataSource);
                } catch (InvocationTargetException e) {
                    throw e.getTargetException();
                }
            } catch (SQLException e) {
                throw e;
            } catch (Throwable t) {
                throw new SQLException(t);
            }
        } else {
            connection.close();
        }
    }
}
