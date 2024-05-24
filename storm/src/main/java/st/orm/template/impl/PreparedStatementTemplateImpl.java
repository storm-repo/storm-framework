/*
 * Copyright 2024 the original author or authors.
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
import jakarta.persistence.PersistenceException;
import st.orm.BindVars;
import st.orm.Query;
import st.orm.spi.Provider;
import st.orm.template.ColumnNameResolver;
import st.orm.template.ForeignKeyResolver;
import st.orm.template.ORMRepositoryTemplate;
import st.orm.template.PreparedStatementTemplate;
import st.orm.template.SqlTemplate;
import st.orm.template.SqlTemplate.AliasResolveStrategy;
import st.orm.template.SqlTemplate.BatchListener;
import st.orm.template.SqlTemplate.BindVariables;
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
        PreparedStatement process(@Nonnull String sql,
                                  @Nonnull List<Parameter> parameters,
                                  @Nullable BindVariables bindVariables,
                                  @Nonnull List<String> generatedKeys) throws SQLException;
    }

    private final TemplateProcessor templateProcessor;
    private final TableNameResolver tableNameResolver;
    private final TableAliasResolver tableAliasResolver;
    private final ColumnNameResolver columnNameResolver;
    private final ForeignKeyResolver foreignKeyResolver;
    private final AliasResolveStrategy aliasResolveStrategy;
    private final Predicate<Provider> providerFilter;

    public PreparedStatementTemplateImpl(@Nonnull DataSource dataSource) {
        // Note that this logic does not use Spring's DataSourceUtils, so it is not aware of Spring's transaction
        // management.
        templateProcessor = (sql, parameters, bindVariables, generatedKeys) -> {
            Connection connection = getConnection(dataSource);
            PreparedStatement preparedStatement = null;
            try {
                if (!generatedKeys.isEmpty()) {
                    try {
                        //noinspection SqlSourceToSinkFlow
                        preparedStatement = connection.prepareStatement(sql, generatedKeys.toArray(new String[0]));
                    } catch (SQLFeatureNotSupportedException ignore) {}
                }
                if (preparedStatement == null) {
                    //noinspection SqlSourceToSinkFlow
                    preparedStatement = connection.prepareStatement(sql);
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
        this.tableNameResolver = null;
        this.tableAliasResolver = null;
        this.columnNameResolver = null;
        this.foreignKeyResolver = null;
        this.aliasResolveStrategy = null;
        this.providerFilter = null;
    }

    public PreparedStatementTemplateImpl(@Nonnull Connection connection) {
        templateProcessor = (sql, parameters, bindVariables, generatedKeys) -> {
            PreparedStatement preparedStatement = null;
            if (!generatedKeys.isEmpty()) {
                try {
                    //noinspection SqlSourceToSinkFlow
                    preparedStatement = connection.prepareStatement(sql, generatedKeys.toArray(new String[0]));
                } catch (SQLFeatureNotSupportedException ignore) {}
            }
            if (preparedStatement == null) {
                //noinspection SqlSourceToSinkFlow
                preparedStatement = connection.prepareStatement(sql);
            }
            if (bindVariables == null) {
                setParameters(preparedStatement, parameters);
            } else {
                bindVariables.setBatchListener(getBatchListener(preparedStatement, parameters));
            }
            return preparedStatement;
        };
        this.tableNameResolver = null;
        this.tableAliasResolver = null;
        this.columnNameResolver = null;
        this.foreignKeyResolver = null;
        this.aliasResolveStrategy = null;
        this.providerFilter = null;
    }

    private PreparedStatementTemplateImpl(@Nonnull TemplateProcessor templateProcessor,
                                          @Nullable TableNameResolver tableNameResolver,
                                          @Nullable TableAliasResolver tableAliasResolver,
                                          @Nullable ColumnNameResolver columnNameResolver,
                                          @Nullable ForeignKeyResolver foreignKeyResolver,
                                          @Nullable AliasResolveStrategy aliasResolveStrategy,
                                          @Nullable Predicate<Provider> providerFilter) {
        this.templateProcessor = templateProcessor;
        this.tableNameResolver = tableNameResolver;
        this.tableAliasResolver = tableAliasResolver;
        this.columnNameResolver = columnNameResolver;
        this.foreignKeyResolver = foreignKeyResolver;
        this.aliasResolveStrategy = aliasResolveStrategy;
        this.providerFilter = providerFilter;
    }

    @Override
    public PreparedStatementTemplateImpl withTableNameResolver(@Nullable TableNameResolver tableNameResolver) {
        return new PreparedStatementTemplateImpl(templateProcessor, tableNameResolver, tableAliasResolver, columnNameResolver, foreignKeyResolver, aliasResolveStrategy, providerFilter);
    }

    @Override
    public PreparedStatementTemplateImpl withColumnNameResolver(@Nullable ColumnNameResolver columnNameResolver) {
        return new PreparedStatementTemplateImpl(templateProcessor, tableNameResolver, tableAliasResolver, columnNameResolver, foreignKeyResolver, aliasResolveStrategy, providerFilter);
    }

    @Override
    public PreparedStatementTemplateImpl withForeignKeyResolver(@Nullable ForeignKeyResolver foreignKeyResolver) {
        return new PreparedStatementTemplateImpl(templateProcessor, tableNameResolver, tableAliasResolver, columnNameResolver, foreignKeyResolver, aliasResolveStrategy, providerFilter);
    }

    @Override
    public PreparedStatementTemplate withAliasResolveStrategy(@Nonnull AliasResolveStrategy aliasResolveStrategy) {
        return new PreparedStatementTemplateImpl(templateProcessor, tableNameResolver, tableAliasResolver, columnNameResolver, foreignKeyResolver, aliasResolveStrategy, providerFilter);
    }

    @Override
    public PreparedStatementTemplateImpl withProviderFilter(@Nullable Predicate<Provider> providerFilter) {
        return new PreparedStatementTemplateImpl(templateProcessor, tableNameResolver, tableAliasResolver, columnNameResolver, foreignKeyResolver, aliasResolveStrategy, providerFilter);
    }

    @Override
    public BindVars createBindVars() {
        return sqlTemplate().createBindVars();
    }

    private BatchListener getBatchListener(@Nonnull PreparedStatement preparedStatement, @Nonnull List<Parameter> parameters) {
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
                        default -> preparedStatement.setObject(p.position(), dbValue);
                    }
                }
                case SqlTemplate.NamedParameter _ -> throw new SQLException("Named parameters not supported for PreparedStatement.");
            }
        }
    }

    private SqlTemplate sqlTemplate() {
        return PS
                .withTableNameResolver(tableNameResolver)
                .withColumnNameResolver(columnNameResolver)
                .withForeignKeyResolver(foreignKeyResolver)
                .withAliasResolveStrategy(aliasResolveStrategy);
    }

    @Override
    public ORMRepositoryTemplate toORM() {
        return new ORMRepositoryTemplateImpl(this, tableNameResolver, columnNameResolver, foreignKeyResolver, providerFilter);
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

    @Override
    public Query create(@Nonnull LazyFactory lazyFactory, @Nonnull StringTemplate template) {
        try {
            var sql = sqlTemplate().process(template);
            var bindVariables = sql.bindVariables().orElse(null);
            return new QueryImpl(lazyFactory, () -> {
                try {
                    return templateProcessor.process(sql.statement(), sql.parameters(), bindVariables, sql.generatedKeys());
                } catch (SQLException e) {
                    throw new PersistenceException(e);
                }
            }, bindVariables == null ? null : bindVariables.getHandle(), sql.versionAware());
        } catch (SqlTemplateException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public PreparedStatement process(StringTemplate template) throws SQLException {
        var sql = sqlTemplate().process(template);
        return templateProcessor.process(sql.statement(), sql.parameters(), sql.bindVariables().orElse(null), sql.generatedKeys());
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
