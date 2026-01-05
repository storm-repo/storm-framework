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
package st.orm.core.template;

import jakarta.annotation.Nonnull;
import st.orm.Data;

import static st.orm.SelectMode.FLAT;
import static st.orm.core.template.Templates.select;
import static st.orm.core.template.TemplateString.wrap;

/**
 * The subquery template is used to construct subqueries that can be linked to the outer query.
 *
 * <p>Unlike regular queries, subqueries only select fields from the primary table; fields from nested (foreign key)
 * records are not included. Additionally, subqueries cannot be directly built — they should be passed as
 * a query builder object when constructing the outer query.</p>
 *
 * @since 1.1
 */
public interface SubqueryTemplate {

    /**
     * Create a subquery for the given table.
     *
     * @param fromType the table to create the subquery for.
     * @param <T> the table type to select from.
     * @return the subquery builder.
     */
    default <T extends Data> QueryBuilder<T, ?, ?> subquery(@Nonnull Class<T> fromType) {
        return subquery(fromType, fromType);
    }

    /**
     * Crate a subquery for the given table and select type.
     *
     * @param fromType the table to create the subquery for.
     * @param selectType the type to select.
     * @return the subquery builder.
     * @param <T> the table type to select from.
     * @param <R> the result type.
     */
    default <T extends Data, R extends Data> QueryBuilder<T, ?, ?> subquery(@Nonnull Class<T> fromType,
                                                                            @Nonnull Class<R> selectType) {
        return subquery(fromType, wrap(select(selectType, FLAT)));
    }

    /**
     * Create a subquery for the given table and select type using the given {@code template}.
     *
     * @param fromType the table to create the subquery for.
     * @param template the select clause template.
     * @return the subquery builder.
     * @param <T> the table type to select from.
     */
    <T extends Data> QueryBuilder<T, ?, ?> subquery(@Nonnull Class<T> fromType,
                                                      @Nonnull TemplateString template);
}
