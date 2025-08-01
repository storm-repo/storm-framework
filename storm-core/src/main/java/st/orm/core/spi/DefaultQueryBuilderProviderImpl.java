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
package st.orm.core.spi;

import jakarta.annotation.Nonnull;
import st.orm.Ref;
import st.orm.core.template.Model;
import st.orm.core.spi.Orderable.AfterAny;
import st.orm.core.template.QueryBuilder;
import st.orm.core.template.QueryTemplate;
import st.orm.core.template.TemplateString;
import st.orm.core.template.impl.DeleteBuilderImpl;
import st.orm.core.template.impl.SelectBuilderImpl;

import java.util.function.Supplier;

/**
 * Default implementation of {@link QueryBuilderProvider} that are based on {@code SelectBuilderImpl} and
 * {@code DeleteBuilderImpl}.
 */
@AfterAny
public class DefaultQueryBuilderProviderImpl implements QueryBuilderProvider {

    /**
     * Creates a query builder for the specified table and select type using the given {@code template}.
     *
     * @param queryTemplate provides the template logic for the query builder.
     * @param fromType the table to select from.
     * @param selectType the result type of the query.
     * @param template the select clause template.
     * @param subquery whether the query is a subquery.
     * @param modelSupplier a supplier that creates the model when needed.
     * @return the query builder.
     * @param <T> the table type to select from.
     * @param <R> the result type.
     */
    @Override
    public <T extends Record, R, ID> QueryBuilder<T, R, ID> selectFrom(@Nonnull QueryTemplate queryTemplate,
                                                                       @Nonnull Class<T> fromType,
                                                                       @Nonnull Class<R> selectType,
                                                                       @Nonnull TemplateString template,
                                                                       boolean subquery,
                                                                       @Nonnull Supplier<Model<T, ID>> modelSupplier) {
        return new SelectBuilderImpl<>(queryTemplate, fromType, selectType, template, subquery, modelSupplier);
    }

    /**
     * Creates a query builder for the specified table and select type using the given {@code template}.
     *
     * @param queryTemplate provides the template logic for the query builder.
     * @param fromType the table to select from.
     * @param refType the type that is selected as ref.
     * @param pkType the primary key type of the table.
     * @param modelSupplier a supplier that creates the model when needed.
     * @return the query builder.
     * @param <T> the table type to select from.
     * @param <ID> the primary key type.
     */
    @Override
    public <T extends Record, R extends Record, ID> QueryBuilder<T, Ref<R>, ID> selectRefFrom(@Nonnull QueryTemplate queryTemplate,
                                                                                              @Nonnull Class<T> fromType,
                                                                                              @Nonnull Class<R> refType,
                                                                                              @Nonnull Class<?> pkType,
                                                                                              @Nonnull Supplier<Model<T, ID>> modelSupplier) {
        return new SelectBuilderImpl<>(queryTemplate, fromType, refType, pkType, modelSupplier);
    }

    /**
     * Creates a query builder for the specified table to delete from.
     *
     * @param queryTemplate provides the template logic for the query builder.
     * @param fromType the table to delete from.
     * @param modelSupplier a supplier that creates the model when needed.
     * @return the query builder.
     * @param <T> the table type to delete from.
     */
    @Override
    public <T extends Record, ID> QueryBuilder<T, ?, ID> deleteFrom(@Nonnull QueryTemplate queryTemplate,
                                                                    @Nonnull Class<T> fromType,
                                                                    @Nonnull Supplier<Model<T, ID>> modelSupplier) {
        return new DeleteBuilderImpl<>(queryTemplate, fromType, modelSupplier);
    }
}
