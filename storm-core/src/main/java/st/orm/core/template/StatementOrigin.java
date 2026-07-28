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

/**
 * Classifies what caused a statement to execute.
 *
 * <p>A statement resolving a reference is shaped exactly like a primary key lookup the application could have
 * written itself, so the statement text does not distinguish the two. The origin does, which is what makes the
 * cost of resolving references visible as its own quantity rather than as a share of all lookups.</p>
 *
 * <p>This property is low-cardinality and suitable as a metric tag.</p>
 *
 * @since 1.13
 */
public enum StatementOrigin {

    /**
     * The statement was asked for directly: a repository call, a query builder, or a template.
     */
    DIRECT,

    /**
     * The statement resolves a reference whose record was not loaded, through {@link st.orm.Ref#fetch()} or
     * {@link st.orm.Ref#fetchOrNull()}.
     *
     * <p>There is one such statement per reference the code read that the selecting query did not resolve and
     * the transaction's entity cache did not serve. Naming the reference in the query's fetch plan brings the
     * referenced record back in the same statement instead.</p>
     */
    FETCH
}
