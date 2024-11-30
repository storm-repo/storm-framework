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
package st.orm.kotlin.template;

import jakarta.annotation.Nonnull;
import kotlin.reflect.KClass;

import static java.lang.StringTemplate.RAW;

/**
 * The subquery builder is used to construct subqueries that may be linked to the outer query.
 *
 * @since 1.1
 */
public interface KSubqueryBuilder {

    /**
     * Create a subquery for the given table.
     *
     * @param fromType the table to create the subquery for.
     * @param <T> the table type.
     * @return the subquery builder.
     */
    default <T extends Record> KQueryBuilder<T, T, ?> subquery(@Nonnull KClass<T> fromType) {
        return subquery(fromType, fromType);
    }

    default <T extends Record, R extends Record> KQueryBuilder<T, R, ?> subquery(@Nonnull KClass<T> fromType, @Nonnull KClass<R> selectType) {
        return subquery(fromType, selectType, RAW."\{selectType}");
    }

    <T extends Record, R> KQueryBuilder<T, R, ?> subquery(@Nonnull KClass<T> fromType, @Nonnull KClass<R> selectType, @Nonnull StringTemplate template);
}
