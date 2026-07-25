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

import static java.util.Objects.requireNonNull;
import static st.orm.core.template.impl.SqlInterceptorManager.intercept;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import st.orm.Data;
import st.orm.PersistenceException;
import st.orm.core.template.Query;
import st.orm.core.template.QueryPlan;
import st.orm.core.template.Sql;
import st.orm.core.template.SqlTemplate.Parameter;
import st.orm.core.template.SqlTemplate.PositionalParameter;

/**
 * Compiled query plan backed by a processed template.
 *
 * <p>The plan holds the processed SQL and an immutable snapshot of the value-independent parameter extractors that
 * template processing registered for the template's bind variables. Binding a record runs the extractors and derives
 * a value-bound {@link Sql} that flows through the regular one-shot execution path. The plan carries no connection or
 * statement state, making it thread-safe and reusable across executions.</p>
 */
final class QueryPlanImpl implements QueryPlan {

    private final Sql sql;
    private final List<Function<Data, List<PositionalParameter>>> parameterExtractors;
    private final List<Function<Object, List<PositionalParameter>>> valueParameterExtractors;
    private final Function<Sql, Query> queryFactory;

    QueryPlanImpl(@Nonnull Sql sql,
                  @Nonnull List<Function<Data, List<PositionalParameter>>> parameterExtractors,
                  @Nonnull List<Function<Object, List<PositionalParameter>>> valueParameterExtractors,
                  @Nonnull Function<Sql, Query> queryFactory) {
        this.sql = requireNonNull(sql, "sql");
        this.parameterExtractors = List.copyOf(parameterExtractors);
        this.valueParameterExtractors = List.copyOf(valueParameterExtractors);
        this.queryFactory = requireNonNull(queryFactory, "queryFactory");
    }

    @Override
    public Query bind(@Nonnull Data record) {
        requireNonNull(record, "record");
        if (parameterExtractors.isEmpty()) {
            throw new PersistenceException("Cannot bind a record against a constant plan: the plan's template has no bind variables. Use query() instead.");
        }
        try {
            var parameters = new ArrayList<Parameter>(sql.parameters().size() + parameterExtractors.size());
            parameters.addAll(sql.parameters());
            for (var extractor : parameterExtractors) {
                parameters.addAll(extractor.apply(record));
            }
            // Interceptors observe every bound statement, matching the observability of per-call processing.
            return queryFactory.apply(intercept(sql.parameters(parameters).bindVariables(null)));
        } catch (UncheckedSqlTemplateException e) {
            throw new PersistenceException(e.getCause());
        }
    }

    @Override
    public Query bindValue(@Nonnull Object id) {
        requireNonNull(id, "id");
        if (parameterExtractors.isEmpty()) {
            throw new PersistenceException("Cannot bind an id against a constant plan: the plan's template has no bind variables. Use query() instead.");
        }
        if (valueParameterExtractors.size() != parameterExtractors.size()) {
            throw new PersistenceException("Cannot bind an id: the plan's bind variables are not purely primary-key based. Use bind() with a record instead.");
        }
        try {
            var parameters = new ArrayList<Parameter>(sql.parameters().size() + valueParameterExtractors.size());
            parameters.addAll(sql.parameters());
            for (var extractor : valueParameterExtractors) {
                parameters.addAll(extractor.apply(id));
            }
            // Interceptors observe every bound statement, matching the observability of per-call processing.
            return queryFactory.apply(intercept(sql.parameters(parameters).bindVariables(null)));
        } catch (UncheckedSqlTemplateException e) {
            throw new PersistenceException(e.getCause());
        }
    }

    @Override
    public Query query() {
        if (!parameterExtractors.isEmpty()) {
            throw new PersistenceException("Cannot execute a plan with bind variables without a record. Use bind() instead.");
        }
        // Interceptors observe every executed statement, matching the observability of per-call processing.
        return queryFactory.apply(intercept(sql));
    }
}
