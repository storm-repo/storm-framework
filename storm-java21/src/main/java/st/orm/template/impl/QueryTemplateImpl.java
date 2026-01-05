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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.Data;
import st.orm.template.Query;
import st.orm.core.template.SqlDialect;
import st.orm.BindVars;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.template.Model;
import st.orm.template.QueryBuilder;
import st.orm.template.QueryTemplate;

import static java.util.Objects.requireNonNull;
import static st.orm.template.impl.StringTemplates.convert;


public class QueryTemplateImpl implements QueryTemplate {
    private final st.orm.core.template.QueryTemplate core;

    public QueryTemplateImpl(@Nonnull st.orm.core.template.QueryTemplate core) {
        this.core = requireNonNull(core, "core");
    }

    @Override
    public SqlDialect dialect() {
        return core.dialect();
    }

    @Override
    public BindVars createBindVars() {
        return core.createBindVars();
    }

    @Override
    public <T extends Data, ID> Ref<T> ref(@Nonnull Class<T> type, @Nonnull ID id) {
        return core.ref(type, id);
    }

    @Override
    public <T extends Data, ID> Ref<T> ref(@Nonnull T record, @Nonnull ID id) {
        return core.ref(record, id);
    }

    @Override
    public <T extends Data, ID> Model<T, ID> model(@Nonnull Class<T> type) {
        //noinspection unchecked
        return new ModelImpl<>((st.orm.core.template.impl.ModelImpl<T, ID>) core.model(type));
    }

    @Override
    public <T extends Data, ID> Model<T, ID> model(@Nonnull Class<T> type, boolean requirePrimaryKey) {
        //noinspection unchecked
        return new ModelImpl<>((st.orm.core.template.impl.ModelImpl<T, ID>) core.model(type, requirePrimaryKey));
    }

    @Override
    public <T extends Data, R> QueryBuilder<T, R, Object> selectFrom(@Nonnull Class<T> fromType, @Nonnull Class<R> selectType, @Nonnull StringTemplate template) {
        return new QueryBuilderImpl<>(core.selectFrom(fromType, selectType, convert(template)));
    }

    @Override
    public <T extends Data> QueryBuilder<T, ?, ?> deleteFrom(@Nonnull Class<T> fromType) {
        return new QueryBuilderImpl<>(core.deleteFrom(fromType));
    }

    @Override
    public Query query(@Nonnull String query) {
        return new QueryImpl(core.query(query));
    }

    @Override
    public Query query(@Nonnull StringTemplate template) throws PersistenceException {
        return new QueryImpl(core.query(convert(template)));
    }

    @Override
    public <T extends Data> QueryBuilder<T, ?, ?> subquery(@Nonnull Class<T> fromType) {
        return new QueryBuilderImpl<>(core.subquery(fromType));
    }

    @Override
    public <T extends Data, R extends Data> QueryBuilder<T, ?, ?> subquery(@Nonnull Class<T> fromType, @Nonnull Class<R> selectType) {
        return new QueryBuilderImpl<>(core.subquery(fromType, selectType));
    }

    @Override
    public <T extends Data> QueryBuilder<T, ?, ?> subquery(@Nonnull Class<T> fromType, @Nonnull StringTemplate template) {
        return new QueryBuilderImpl<>(core.subquery(fromType, convert(template)));
    }
}
