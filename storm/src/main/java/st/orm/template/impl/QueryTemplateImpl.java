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
import st.orm.Ref;
import st.orm.PersistenceException;
import st.orm.PreparedQuery;
import st.orm.Query;
import st.orm.spi.Provider;
import st.orm.spi.Providers;
import st.orm.spi.QueryFactory;
import st.orm.template.SqlDialect;
import st.orm.template.Model;
import st.orm.template.QueryBuilder;
import st.orm.template.QueryTemplate;
import st.orm.template.SqlTemplateException;

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;
import static st.orm.spi.Providers.getSqlDialect;

class QueryTemplateImpl implements QueryTemplate {
    protected final QueryFactory queryFactory;
    protected final ModelBuilder modelBuilder;
    protected final Predicate<? super Provider> providerFilter;
    private final RefFactory refFactory;
    private final SqlDialect dialect;

    QueryTemplateImpl(@Nonnull QueryFactory queryFactory,
                      @Nonnull ModelBuilder modelBuilder,
                      @Nullable Predicate<? super Provider> providerFilter) {
        this.queryFactory = requireNonNull(queryFactory);
        this.modelBuilder = requireNonNull(modelBuilder);
        this.providerFilter = providerFilter;
        this.dialect = getSqlDialect(providerFilter == null ? _ -> true : providerFilter);
        this.refFactory = new RefFactoryImpl(queryFactory, modelBuilder, providerFilter);
    }

    /**
     * Get the SQL dialect for this template.
     *
     * @return the SQL dialect.
     * @since 1.2
     */
    @Override
    public SqlDialect dialect() {
        return dialect;
    }

    /**
     * Create a new bind variables instance that can be used to add bind variables to a batch.
     *
     * @return a new bind variables instance.
     * @see PreparedQuery#addBatch(Record)
     */
    @Override
    public BindVars createBindVars() {
        return queryFactory.createBindVars();
    }

    /**
     * Creates a ref instance for the specified record {@code type} and {@code pk}. This method can be used to generate
     * ref instances for entities, projections and regular records.
     *
     * @param type record type.
     * @param id primary key.
     * @return ref instance.
     * @param <E> table type.
     * @param <ID> primary key type.
     * @since 1.3
     */
    @Override
    public <E extends Record, ID> Ref<E> ref(@Nonnull Class<E> type, @Nonnull ID id) {
        return refFactory.create(type, id);
    }

    /**
     * Creates a ref instance for the specified record {@code type} and {@code pk}. This method can be used to generate
     * ref instances for entities, projections and regular records. The object returned by this method already contains
     * the fetched record.
     *
     * @param id primary key.
     * @return ref instance.
     * @param <E> table type.
     * @param <ID> primary key type.
     * @since 1.3
     */
    @Override
    public <E extends Record, ID> Ref<E> ref(@Nonnull E entity, @Nonnull ID id) {
        return refFactory.create(entity, id);
    }

    /**
     * Get the model for the specified record {@code type}. The model provides information about the type's database
     * name, primary keys and columns.
     *
     * @param type record type.
     * @param requirePrimaryKey whether to require a primary key.
     * @return the model.
     * @param <T> table type.
     * @param <ID> primary key type.
     * @since 1.3
     */
    @Override
    public <T extends Record, ID> Model<T, ID> model(@Nonnull Class<T> type, boolean requirePrimaryKey) {
        try {
            return modelBuilder.build(type, requirePrimaryKey);
        } catch (SqlTemplateException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Creates a query builder for the specified table and select type using the given {@code template}.
     *
     * @param fromType the table to select from.
     * @param selectType the result type of the query.
     * @param template the select clause template.
     * @return the query builder.
     * @param <T> the table type to select from.
     * @param <R> the result type.
     */
    @Override
    public <T extends Record, R> QueryBuilder<T, R, ?> selectFrom(@Nonnull Class<T> fromType, @Nonnull Class<R> selectType, @Nonnull StringTemplate template) {
        return Providers.selectFrom(this, fromType, selectType, template, false, modelBuilder.supplier(fromType, true));
    }

    /**
     * Creates a query builder for the specified table to delete from.
     *
     * @param fromType the table to delete from.
     * @return the query builder.
     * @param <T> the table type to delete from.
     */
    @Override
    public <T extends Record> QueryBuilder<T, ?, ?> deleteFrom(@Nonnull Class<T> fromType) {
        return Providers.deleteFrom(this, fromType, modelBuilder.supplier(fromType, true));
    }

    /**
     * Create a subquery for the given table and select type using the given {@code template}.
     *
     * @param fromType the table to create the subquery for.
     * @param selectType the type to select.
     * @param template the select clause template.
     * @return the subquery builder.
     * @param <T> the table type to select from.
     * @param <R> the result type.
     */
    @Override
    public <T extends Record, R> QueryBuilder<T, R, ?> subquery(@Nonnull Class<T> fromType, @Nonnull Class<R> selectType, @Nonnull StringTemplate template) {
        return Providers.selectFrom(this, fromType, selectType, template, true, modelBuilder.supplier(fromType, true));
    }

    /**
     * Creates a query for the specified query {@code template}.
     *
     * @param template the query template.
     * @return the query.
     */
    @Override
    public Query query(@Nonnull StringTemplate template) {
        return queryFactory.create(template);
    }
}
