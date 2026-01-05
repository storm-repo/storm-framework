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
package st.orm.template

import st.orm.Data

/**
 * A builder for constructing join clause of the query using custom join conditions.
 *
 * @param <T> the type of the table being queried.
 * @param <R> the type of the result.
 * @param <ID> the type of the primary key.
 */
interface JoinBuilder<T : Data, R, ID> {
    /**
     * Specifies the join condition using a custom expression.
     *
     * @param template the condition to join on.
     * @return the query builder.
     */
    fun on(template: TemplateBuilder): QueryBuilder<T, R, ID> {
        return on(template.build())
    }

    /**
     * Specifies the join condition using a custom expression.
     *
     * @param template the condition to join on.
     * @return the query builder.
     */
    fun on(template: TemplateString): QueryBuilder<T, R, ID>
}
