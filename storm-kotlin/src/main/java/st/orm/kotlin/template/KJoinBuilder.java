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
import st.orm.template.TemplateFunction;

/**
 * A builder for constructing join clause of the query using custom join conditions.
 *
 * @param <T>  the type of the table being queried.
 * @param <R>  the type of the result.
 * @param <ID> the type of the primary key.
 */
public interface KJoinBuilder<T extends Record, R, ID> {

    /**
     * Specifies the join condition using a custom expression.
     *
     * @param template the condition to join on.
     * @return the query builder.
     */
    KQueryBuilder<T, R, ID> on(@Nonnull StringTemplate template);

    /**
     * Specifies the join condition using a custom expression.
     *
     * @param function used to define the condition to join on.
     * @return the query builder.
     */
    default KQueryBuilder<T, R, ID> on(@Nonnull TemplateFunction function) {
        return on(TemplateFunction.template(function));
    }
}
