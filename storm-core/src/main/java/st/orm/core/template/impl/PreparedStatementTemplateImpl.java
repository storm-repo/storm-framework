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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.BindVars;
import st.orm.PersistenceException;
import st.orm.core.spi.RefFactory;
import st.orm.core.spi.RefFactoryImpl;
import st.orm.core.spi.TransactionContext;
import st.orm.core.spi.TransactionTemplate;
import st.orm.core.template.Query;
import st.orm.core.spi.Provider;
import st.orm.core.spi.Providers;
import st.orm.core.spi.QueryFactory;
import st.orm.mapping.ColumnNameResolver;
import st.orm.mapping.ForeignKeyResolver;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.PreparedStatementTemplate;
import st.orm.core.template.Sql;
import st.orm.core.template.SqlTemplate;
import st.orm.core.template.SqlTemplate.BatchListener;
import st.orm.core.template.SqlTemplate.NamedParameter;
import st.orm.core.template.SqlTemplate.Parameter;
import st.orm.core.template.SqlTemplate.PositionalParameter;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.TableAliasResolver;
import st.orm.mapping.TableNameResolver;
import st.orm.core.template.TemplateString;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
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
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.time.temporal.ChronoUnit.*;
import static st.orm.core.spi.Providers.getConnection;
import static st.orm.core.spi.Providers.releaseConnection;
import static st.orm.core.template.SqlTemplate.PS;
import static st.orm.core.template.impl.ExceptionHelper.getExceptionTransformer;
import static st.orm.core.template.impl.LazySupplier.lazy;
import static st.orm.core.template.impl.RecordValidation.validate;

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
    private final TransactionTemplate transactionTemplate;
    private final SqlTemplate sqlTemplate;

    public PreparedStatementTemplateImpl(@Nonnull DataSource dataSource) {
        validate();
        // Note that this logic does not use Spring's DataSourceUtils, so it is not aware of Spring's transaction
        // management.
        transactionTemplate = Providers.getTransactionTemplate();
        templateProcessor = (sql, safe) -> {
            if (!safe) {
                sql.unsafeWarning().ifPresent(warning -> {
                    throw new PersistenceException("%s Use Query.safe() to mark query as safe.".formatted(warning));
                });
            }
            var statement = sql.statement();
            var parameters = sql.parameters();
            var bindVariables = sql.bindVariables().orElse(null);
            var generatedKeys = sql.generatedKeys();
            var transactionContext = transactionTemplate.currentContext().orElse(null);
            Connection connection = getConnection(dataSource, transactionContext);
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
                if (transactionContext != null) {
                    preparedStatement = transactionContext.getDecorator(PreparedStatement.class)
                            .decorate(preparedStatement);
                }
                if (bindVariables == null) {
                    setParameters(preparedStatement, parameters);
                } else {
                    bindVariables.setBatchListener(getBatchListener(preparedStatement, parameters));
                }
            } finally {
                if (preparedStatement == null) {
                    releaseConnection(connection, dataSource, transactionContext);
                }
            }
            return createProxy(preparedStatement, connection, dataSource, transactionContext);
        };
        this.modelBuilder = ModelBuilder.newInstance();
        this.tableAliasResolver = TableAliasResolver.DEFAULT;
        this.providerFilter = null;
        this.refFactory = new RefFactoryImpl(this, modelBuilder, providerFilter);
        this.sqlTemplate = createSqlTemplate();
    }

    public PreparedStatementTemplateImpl(@Nonnull Connection connection) {
        validate();
        transactionTemplate = Providers.getTransactionTemplate();
        templateProcessor = (sql, safe) -> {
            if (!safe) {
                sql.unsafeWarning().ifPresent(warning -> {
                    throw new PersistenceException("%s Use Query.safe() to mark query as safe.".formatted(warning));
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
            var transactionContext = transactionTemplate.currentContext().orElse(null);
            if (transactionContext != null) {
                preparedStatement = transactionContext.getDecorator(PreparedStatement.class)
                        .decorate(preparedStatement);
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
        this.refFactory = new RefFactoryImpl(this, modelBuilder, providerFilter);
        this.sqlTemplate = createSqlTemplate();
    }

    private PreparedStatementTemplateImpl(@Nonnull TemplateProcessor templateProcessor,
                                          @Nonnull ModelBuilder modelBuilder,
                                          @Nonnull TableAliasResolver tableAliasResolver,
                                          @Nullable Predicate<Provider> providerFilter,
                                          @Nonnull TransactionTemplate transactionTemplate) {
        this.templateProcessor = templateProcessor;
        this.modelBuilder = modelBuilder;
        this.tableAliasResolver = tableAliasResolver;
        this.providerFilter = providerFilter;
        this.refFactory = new RefFactoryImpl(this, modelBuilder, providerFilter);
        this.transactionTemplate = transactionTemplate;
        this.sqlTemplate = createSqlTemplate();
    }

    private SqlTemplate createSqlTemplate() {
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
     * Returns a new prepared statement template with the specified table name resolver.
     *
     * @param tableNameResolver the table name resolver.
     * @return a new prepared statement template.
     */
    @Override
    public PreparedStatementTemplateImpl withTableNameResolver(@Nullable TableNameResolver tableNameResolver) {
        return new PreparedStatementTemplateImpl(templateProcessor, modelBuilder.tableNameResolver(tableNameResolver), tableAliasResolver, providerFilter, transactionTemplate);
    }

    /**
     * Returns a new prepared statement template with the specified column name resolver.
     *
     * @param columnNameResolver the column name resolver.
     * @return a new prepared statement template.
     */
    @Override
    public PreparedStatementTemplateImpl withColumnNameResolver(@Nullable ColumnNameResolver columnNameResolver) {
        return new PreparedStatementTemplateImpl(templateProcessor, modelBuilder.columnNameResolver(columnNameResolver), tableAliasResolver, providerFilter, transactionTemplate);
    }

    /**
     * Returns a new prepared statement template with the specified foreign key resolver.
     *
     * @param foreignKeyResolver the foreign key resolver.
     * @return a new prepared statement template.
     */
    @Override
    public PreparedStatementTemplateImpl withForeignKeyResolver(@Nullable ForeignKeyResolver foreignKeyResolver) {
        return new PreparedStatementTemplateImpl(templateProcessor, modelBuilder.foreignKeyResolver(foreignKeyResolver), tableAliasResolver, providerFilter, transactionTemplate);
    }

    /**
     * Returns a new prepared statement template with the specified table alias resolver.
     *
     * @param tableAliasResolver the table alias resolver.
     * @return a new prepared statement template.
     */
    @Override
    public PreparedStatementTemplate withTableAliasResolver(@Nonnull TableAliasResolver tableAliasResolver) {
        return new PreparedStatementTemplateImpl(templateProcessor, modelBuilder, tableAliasResolver, providerFilter, transactionTemplate);
    }

    /**
     * Returns a new prepared statement template with the specified provider filter.
     *
     * @param providerFilter the provider filter.
     * @return a new prepared statement template.
     */
    @Override
    public PreparedStatementTemplateImpl withProviderFilter(@Nullable Predicate<Provider> providerFilter) {
        return new PreparedStatementTemplateImpl(templateProcessor, modelBuilder, tableAliasResolver, providerFilter, transactionTemplate);
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
        var calendarSupplier = lazy(() -> Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC)));
        return batchParameters -> {
            try {
                setParameters(preparedStatement, parameters, calendarSupplier);
                setParameters(preparedStatement, batchParameters, calendarSupplier);
                preparedStatement.addBatch();
            } catch (SQLException e) {
                throw new PersistenceException(e);
            }
        };
    }

    private static void setParameters(@Nonnull PreparedStatement preparedStatement,
                                      @Nonnull List<? extends Parameter> parameters) throws SQLException {
        var calendarSupplier = lazy(() -> Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC)));
        setParameters(preparedStatement, parameters, calendarSupplier);
    }

    private static void setParameters(@Nonnull PreparedStatement preparedStatement,
                                      @Nonnull List<? extends Parameter> parameters,
                                      @Nonnull Supplier<Calendar> calendarSupplier) throws SQLException {
        for (var parameter : parameters) {
            switch (parameter) {
                case PositionalParameter p -> {
                    final int idx = p.position();
                    final Object v = p.dbValue();
                    switch (v) {
                        case null              -> preparedStatement.setObject(idx, null);
                        case Short s           -> preparedStatement.setShort(idx, s);
                        case Integer i         -> preparedStatement.setInt(idx, i);
                        case Long l            -> preparedStatement.setLong(idx, l);
                        case Float f           -> preparedStatement.setFloat(idx, f);
                        case Double d          -> preparedStatement.setDouble(idx, d);
                        case Byte b            -> preparedStatement.setByte(idx, b);
                        case Boolean b         -> preparedStatement.setBoolean(idx, b);
                        case String s          -> preparedStatement.setString(idx, s);
                        case BigDecimal bd     -> preparedStatement.setBigDecimal(idx, bd);
                        case byte[] bytes      -> preparedStatement.setBytes(idx, bytes);
                        case java.sql.Date d   -> preparedStatement.setDate(idx, d);
                        case Time t            -> preparedStatement.setTime(idx, t);
                        case Timestamp ts      -> preparedStatement.setTimestamp(idx, ts, calendarSupplier.get());
                        case Enum<?> e         -> preparedStatement.setString(idx, e.name());   // Enum handled by ORM layer.
                        // java.time using vendor-safe approach.
                        case LocalDate ld      -> preparedStatement.setDate(idx, java.sql.Date.valueOf(ld));
                        case LocalTime lt      -> preparedStatement.setTime(idx, java.sql.Time.valueOf(lt));
                        case LocalDateTime ldt -> preparedStatement.setTimestamp(idx, Timestamp.valueOf(ldt));
                        case OffsetDateTime odt-> preparedStatement.setTimestamp(idx, Timestamp.from(odt.toInstant()), calendarSupplier.get());
                        case ZonedDateTime zdt -> preparedStatement.setTimestamp(idx, Timestamp.from(zdt.toInstant()), calendarSupplier.get());
                        case Instant inst      -> preparedStatement.setTimestamp(idx, Timestamp.from(inst), calendarSupplier.get());
                        default                -> preparedStatement.setObject(idx, v);
                    }
                }
                case NamedParameter ignored ->
                        throw new SQLException("Named parameters not supported for PreparedStatement.");
            }
        }
    }

    @FunctionalInterface
    interface SqlRunnable { void run() throws SQLException; }

    private static void setObjectOr(PreparedStatement ps,
                                    AtomicBoolean supportsSetObject,
                                    SqlRunnable typedSetter,
                                    SqlRunnable legacyFallback) throws SQLException {
        if (supportsSetObject.get()) {
            try {
                typedSetter.run();
                return;
            } catch (SQLFeatureNotSupportedException e) {
                supportsSetObject.set(false);
            }
        }
        legacyFallback.run();
    }

    /**
     * Get the SQL template used by this factory.
     *
     * <p>Query factory implementations must ensure that the SQL Template returned by this method is processed by any
     * registered {@code SqlInterceptor} instances before being returned. As a result, this method is expected to
     * return a new instance of the SQL template each time it is called, ensuring that any modifications made by
     * interceptors are applied correctly.</p>
     *
     * @return the SQL template.
     * @since 1.3
     */
    @Override
    public SqlTemplate sqlTemplate() {
        return SqlInterceptorManager.customize(sqlTemplate);
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
    private static PreparedStatement createProxy(@Nonnull PreparedStatement statement, @Nonnull Connection connection, @Nonnull DataSource dataSource, @Nullable TransactionContext context) {
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[] { PreparedStatement.class },
                (ignore, method, args) -> {
                    // Check if the close method is being called on the PreparedStatement.
                    if (method.getName().equals("close")) {
                        try {
                            statement.close();
                        } finally {
                            releaseConnection(connection, dataSource, context);
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
    public Query create(@Nonnull TemplateString template) {
        try {
            var sql = sqlTemplate().process(template);
            var bindVariables = sql.bindVariables().orElse(null);
            return new QueryImpl(refFactory, safe -> {
                try {
                    return templateProcessor.process(sql, safe);
                } catch (SQLException e) {
                    throw new PersistenceException(e);
                }
            }, bindVariables == null ? null : bindVariables.getHandle(), sql.versionAware(), getExceptionTransformer(sql));
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
    public PreparedStatement query(@Nonnull TemplateString template) throws SQLException {
        var sql = sqlTemplate().process(template);
        return templateProcessor.process(sql, true);    // We allow unsafe queries in direct JDBC mode.
    }
}
