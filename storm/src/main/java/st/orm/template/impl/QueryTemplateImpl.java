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
import st.orm.BindVars;
import st.orm.Lazy;
import st.orm.PersistenceException;
import st.orm.Query;
import st.orm.spi.Provider;
import st.orm.spi.Providers;
import st.orm.spi.QueryFactory;
import st.orm.template.Model;
import st.orm.template.QueryBuilder;
import st.orm.template.QueryTemplate;
import st.orm.template.SqlTemplateException;

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

class QueryTemplateImpl implements QueryTemplate {
    protected final QueryFactory queryFactory;
    protected final ModelBuilder modelBuilder;
    protected final Predicate<? super Provider> providerFilter;
    private final LazyFactory lazyFactory;

    QueryTemplateImpl(@Nonnull QueryFactory queryFactory,
                      @Nonnull ModelBuilder modelBuilder,
                     @Nullable Predicate<? super Provider> providerFilter) {
        this.queryFactory = requireNonNull(queryFactory);
        this.modelBuilder = requireNonNull(modelBuilder);
        this.providerFilter = providerFilter;
        this.lazyFactory = new LazyFactoryImpl(queryFactory, modelBuilder, providerFilter);
    }

    @Override
    public BindVars createBindVars() {
        return queryFactory.createBindVars();
    }

    @Override
    public <E extends Record, ID> Lazy<E, ID> lazy(@Nonnull Class<E> type, @Nullable ID pk) {
        return lazyFactory.create(type, pk);
    }

    @Override
    public <T extends Record, ID> Model<T, ID> model(@Nonnull Class<T> type) {
        try {
            return modelBuilder.build(type, false);
        } catch (SqlTemplateException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public <T extends Record, R> QueryBuilder<T, R, ?> selectFrom(@Nonnull Class<T> fromType, @Nonnull Class<R> selectType, @Nonnull StringTemplate template) {
        return Providers.selectFrom(this, fromType, selectType, template, false, modelBuilder.supplier(fromType, true));
    }

    @Override
    public <T extends Record> QueryBuilder<T, ?, ?> deleteFrom(@Nonnull Class<T> fromType) {
        return Providers.deleteFrom(this, fromType, modelBuilder.supplier(fromType, true));
    }

    @Override
    public <T extends Record, R> QueryBuilder<T, R, ?> subquery(@Nonnull Class<T> fromType, @Nonnull Class<R> selectType, @Nonnull StringTemplate template) {
        return Providers.selectFrom(this, fromType, selectType, template, true, modelBuilder.supplier(fromType, true));
    }

    @Override
    public Query query(@Nonnull StringTemplate template) {
        return queryFactory.create(template);
    }
}
