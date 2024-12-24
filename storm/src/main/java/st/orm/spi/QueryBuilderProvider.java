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
package st.orm.spi;

import jakarta.annotation.Nonnull;
import st.orm.template.QueryBuilder;
import st.orm.template.QueryTemplate;

/**
 * @since 1.1
 */
public interface QueryBuilderProvider extends Provider {

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
    <T extends Record, R, ID> QueryBuilder<T, R, ID> selectFrom(@Nonnull QueryTemplate queryTemplate,
                                                                @Nonnull Class<T> fromType,
                                                                @Nonnull Class<R> selectType,
                                                                @Nonnull StringTemplate template,
                                                                boolean subquery);

    /**
     * Creates a query builder for the specified table to delete from.
     *
     * @param fromType the table to delete from.
     * @return the query builder.
     * @param <T> the table type to delete from.
     */
    <T extends Record, ID> QueryBuilder<T, ?, ID> deleteFrom(@Nonnull QueryTemplate queryTemplate,
                                                             @Nonnull Class<T> fromType);
}