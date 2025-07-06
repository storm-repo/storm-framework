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
package st.orm.kotlin.template;

import jakarta.annotation.Nonnull;
import kotlin.reflect.KClass;
import st.orm.template.TemplateFunction;

import static java.lang.StringTemplate.RAW;

/**
 * The subquery builder is used to construct subqueries that can be linked to the outer query.
 *
 * <p>Unlike regular queries, subqueries only select fields from the primary table; fields from nested (foreign key)
 * records are not included. Additionally, subqueries cannot be directly built — they should be passed as
 * a query builder object when constructing the outer query.</p>
 *
 * @since 1.1
 */
public interface KSubqueryTemplate {

    /**
     * Create a subquery for the given table.
     *
     * @param fromType the table to create the subquery for.
     * @param <T> the table type.
     * @return the subquery builder.
     */
    default <T extends Record> KQueryBuilder<?, ?, ?> subquery(@Nonnull KClass<T> fromType) {
        return subquery(fromType, fromType);
    }

    /**
     * Crate a subquery for the given table and select type.
     *
     * @param fromType the table to create the subquery for.
     * @param selectType the type to select.
     * @return the subquery builder.
     * @param <T> the table type.
     * @param <R> the select type.
     */
    default <T extends Record, R extends Record> KQueryBuilder<T, ?, ?> subquery(@Nonnull KClass<T> fromType,
                                                                                 @Nonnull KClass<R> selectType) {
        return subquery(fromType, RAW."\{selectType}");
    }

    /**
     * Create a subquery for the given table and select type using the given template.
     *
     * @param fromType the table to create the subquery for.
     * @param template the template to use for the subquery.
     * @return the subquery builder.
     * @param <T> the table type.
     */
    <T extends Record> KQueryBuilder<T, ?, ?> subquery(@Nonnull KClass<T> fromType,
                                                       @Nonnull StringTemplate template);

    /**
     * Create a subquery for the given table and select type using the given template.
     *
     * @param fromType the table to create the subquery for.
     * @param function the template to use for the subquery.
     * @return the subquery builder.
     * @param <T> the table type.
     */
    default <T extends Record> KQueryBuilder<T, ?, ?> subquery(@Nonnull KClass<T> fromType,
                                                               @Nonnull TemplateFunction function) {
        return subquery(fromType, TemplateFunction.template(function));
    }
}
