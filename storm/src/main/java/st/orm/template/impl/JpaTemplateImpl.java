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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import st.orm.BindVars;
import st.orm.PreparedQuery;
import st.orm.Query;
import st.orm.Ref;
import st.orm.spi.Provider;
import st.orm.spi.Providers;
import st.orm.spi.QueryFactory;
import st.orm.template.ColumnNameResolver;
import st.orm.template.ForeignKeyResolver;
import st.orm.template.JpaTemplate;
import st.orm.template.ORMTemplate;
import st.orm.template.Sql;
import st.orm.template.SqlTemplate;
import st.orm.template.SqlTemplateException;
import st.orm.template.TableAliasResolver;
import st.orm.template.TableNameResolver;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static jakarta.persistence.TemporalType.DATE;
import static jakarta.persistence.TemporalType.TIME;
import static jakarta.persistence.TemporalType.TIMESTAMP;
import static st.orm.template.SqlTemplate.PS;

public final class JpaTemplateImpl implements JpaTemplate, QueryFactory {

    @FunctionalInterface
    private interface TemplateProcessor {
        jakarta.persistence.Query process(@Nonnull Sql sql, @Nullable Class<?> resultClass, boolean safe);
    }

    private final TemplateProcessor templateProcessor;
    private final ModelBuilder modelBuilder;
    private final TableAliasResolver tableAliasResolver;
    private final Predicate<Provider> providerFilter;
    private final RefFactory refFactory;

    public JpaTemplateImpl(@Nonnull EntityManager entityManager) {
        templateProcessor = (sql, resultClass, safe) -> {
            if (!safe) {
                sql.unsafeWarning().ifPresent(warning -> {
                    throw new PersistenceException(STR."\{warning} Use Query.safe() to mark query as safe.");
                });
            }
            //noinspection SqlSourceToSinkFlow
            jakarta.persistence.Query query = resultClass == null
                    ? entityManager.createNativeQuery(sql.statement())
                    : entityManager.createNativeQuery(sql.statement(), resultClass);
            setParameters(query, sql.parameters());
            return query;
        };
        this.modelBuilder = ModelBuilder.newInstance();
        this.tableAliasResolver = TableAliasResolver.DEFAULT;
        this.providerFilter = null;
        this.refFactory = new RefFactoryImpl(this, modelBuilder, null);
    }

    private JpaTemplateImpl(@Nonnull TemplateProcessor templateProcessor, @Nonnull ModelBuilder modelBuilder, @Nonnull TableAliasResolver tableAliasResolver, @Nullable Predicate<Provider> providerFilter) {
        this.templateProcessor = templateProcessor;
        this.modelBuilder = modelBuilder;
        this.tableAliasResolver = tableAliasResolver;
        this.providerFilter = providerFilter;
        this.refFactory = new RefFactoryImpl(this, modelBuilder, providerFilter);
    }

    private void setParameters(@Nonnull jakarta.persistence.Query query, @Nonnull List<SqlTemplate.Parameter> parameters) {
        for (var parameter : parameters) {
            var dbValue = parameter.dbValue();
            switch (parameter) {
                case SqlTemplate.PositionalParameter p -> {
                    switch (dbValue) {
                        case null -> query.setParameter(p.position(), null);
                        case java.sql.Date d -> query.setParameter(p.position(), d, DATE);
                        case java.sql.Time d -> query.setParameter(p.position(), d, TIME);
                        case java.sql.Timestamp d -> query.setParameter(p.position(), d, TIMESTAMP);
                        default -> query.setParameter(p.position(), dbValue);
                    }
                }
                case SqlTemplate.NamedParameter n -> {
                    switch (dbValue) {
                        case null -> query.setParameter(n.name(), null);
                        case java.sql.Date d -> query.setParameter(n.name(), d, DATE);
                        case java.sql.Time d -> query.setParameter(n.name(), d, TIME);
                        case java.sql.Timestamp d -> query.setParameter(n.name(), d, TIMESTAMP);
                        default -> query.setParameter(n.name(), dbValue);
                    }
                }
            }
        }
    }

    /**
     * Creates a query for the specified query {@code template}.
     *
     * @param template the query template.
     * @return the query.
     */
    @Override
    public jakarta.persistence.Query query(@Nonnull StringTemplate template) {
        try {
            var sql = sqlTemplate().process(template);
            return templateProcessor.process(sql, null, true);  // We allow unsafe queries in direct JPA mode.
        } catch (SqlTemplateException e) {
            throw new PersistenceException(e.getMessage(), e);
        }
    }

    private jakarta.persistence.Query query(@Nonnull StringTemplate template, @Nonnull Class<?> resultClass) {
        try {
            var sql = sqlTemplate().process(template);
            return templateProcessor.process(sql, resultClass, true);  // We allow unsafe queries in direct JPA mode.
        } catch (SqlTemplateException e) {
            throw new PersistenceException(e.getMessage(), e);
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
     * Create a new bind variables instance that can be used to add bind variables to a batch.
     *
     * @return a new bind variables instance.
     */
    @Override
    public BindVars createBindVars() {
        throw new PersistenceException("Not supported by JPA.");
    }

    @Override
    public Query create(@Nonnull StringTemplate template) {
        return new JpaPreparedQuery(template, false);
    }

    /**
     * Returns an ORM template for this JPA template.
     */
    @Override
    public ORMTemplate toORM() {
        return new ORMTemplateImpl(this, ModelBuilder.newInstance(), null);
    }

    /**
     * Returns a new JPA template with the specified table name resolver.
     *
     * @param tableNameResolver the table name resolver.
     * @return a new JPA template.
     */
    @Override
    public JpaTemplate withTableNameResolver(@Nullable TableNameResolver tableNameResolver) {
        return new JpaTemplateImpl(templateProcessor, modelBuilder.tableNameResolver(tableNameResolver), tableAliasResolver, providerFilter);
    }

    /**
     * Returns a new jpa statement template with the specified column name resolver.
     *
     * @param columnNameResolver the column name resolver.
     * @return a new jpa statement template.
     */
    @Override
    public JpaTemplate withColumnNameResolver(@Nullable ColumnNameResolver columnNameResolver) {
        return new JpaTemplateImpl(templateProcessor, modelBuilder.columnNameResolver(columnNameResolver), tableAliasResolver, providerFilter);
    }

    /**
     * Returns a new jpa statement template with the specified foreign key resolver.
     *
     * @param foreignKeyResolver the foreign key resolver.
     * @return a new jpa statement template.
     */
    @Override
    public JpaTemplate withForeignKeyResolver(@Nullable ForeignKeyResolver foreignKeyResolver) {
        return new JpaTemplateImpl(templateProcessor, modelBuilder.foreignKeyResolver(foreignKeyResolver), tableAliasResolver, providerFilter);
    }

    /**
     * Returns a new JPA template with the specified table alias resolver.
     *
     * @param tableAliasResolver the table alias resolver.
     * @return a new JPA template.
     */
    @Override
    public JpaTemplate withTableAliasResolver(@Nonnull TableAliasResolver tableAliasResolver) {
        return new JpaTemplateImpl(templateProcessor, modelBuilder, tableAliasResolver, providerFilter);
    }

    /**
     * Returns a new jpa statement template with the specified provider filter.
     *
     * @param providerFilter the provider filter.
     * @return a new jpa statement template.
     */
    @Override
    public JpaTemplate withProviderFilter(@Nullable Predicate<Provider> providerFilter) {
        return new JpaTemplateImpl(templateProcessor, modelBuilder, tableAliasResolver, providerFilter);
    }

    private class JpaPreparedQuery implements PreparedQuery {
        private final StringTemplate template;
        private final boolean safe;

        public JpaPreparedQuery(@Nonnull StringTemplate template, boolean safe) {
            this.template = template;
            this.safe = safe;
        }

        @Override
        public PreparedQuery prepare() {
            return this;
        }

        @Override
        public Query safe() {
            return new JpaPreparedQuery(template, safe);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Stream<Object[]> getResultStream() {
            return JpaTemplateImpl.this.query(template).getResultStream().map(this::convert);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Stream<T> getResultStream(@Nonnull Class<T> type) {
            return query(template, type).getResultStream();
        }

        @Override
        public <T extends Record> Stream<Ref<T>> getRefStream(@Nonnull Class<T> type, @Nonnull Class<?> pkType) {
            return getResultStream(pkType)
                    .map(id -> id == null ? Ref.ofNull() : refFactory.create(type, id));
        }

        @Override
        public boolean isVersionAware() {
            throw new UnsupportedOperationException("Not supported by JPA.");
        }

        @Override
        public int executeUpdate() {
            return JpaTemplateImpl.this.query(template).executeUpdate();
        }

        /**
         * Converts a database row into a list of values.
         *
         * @param row row to convert.
         * @return an array of values.
         */
        private Object[] convert(@Nullable Object row) {
            if (row == null || !row.getClass().isArray()) {
                return new Object[]{row};
            }
            return (Object[]) row;
        }

        @Override
        public void addBatch(@Nonnull Record record) {
            throw new UnsupportedOperationException("Not supported by JPA.");
        }

        @Override
        public int[] executeBatch() {
            throw new UnsupportedOperationException("Not supported by JPA.");
        }

        @Override
        public <ID> Stream<ID> getGeneratedKeys(@Nonnull Class<ID> type) {
            throw new UnsupportedOperationException("Not supported by JPA.");
        }

        @Override
        public void close() {
        }
    }
}
