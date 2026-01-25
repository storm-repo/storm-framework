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

import jakarta.annotation.Nonnull;
import st.orm.Data;
import st.orm.SelectMode;
import st.orm.core.template.Column;
import st.orm.core.template.impl.Elements.Expression;

import java.util.List;

/**
 * Represents the resolved model of a SQL query.
 *
 * <p>A {@code QueryModel} is responsible for translating high-level query constructs such as expressions, entities,
 * references, and templates into SQL fragments and bind values.</p>
 *
 * <p>The model encapsulates table aliasing, column resolution, metamodel lookup, and the separation of SQL
 * compilation and parameter binding. Implementations are expected to be stateful per query instance.</p>
 *
 * @since 1.8
 */
interface QueryModel {

    /**
     * Returns the root table of this query model.
     *
     * <p>The returned table represents the primary table involved in the query and is used as the anchor for alias
     * resolution, column qualification, and model construction.</p>
     *
     * @return the aliased root table of the query.
     */
    AliasedTable getTable();

    /**
     * Returns the columns to be selected for the root table, according to the specified selection mode.
     *
     * <p>The selection mode determines whether only primary key columns, only declared columns, or all nested columns
     * are included.</p>
     *
     * @param mode the selection mode that controls which columns are returned.
     * @return the list of aliased columns for the root table.
     */
    List<AliasedColumn> getColumns(@Nonnull SelectMode mode);

    /**
     * Returns the columns to be selected for the specified table type, according to the given selection mode.
     *
     * <p>If the requested table type differs from the root table, a corresponding model is resolved or built to
     * determine the correct column set.</p>
     *
     * @param table the table type for which columns should be returned.
     * @param mode  the selection mode that controls which columns are included.
     * @return the list of aliased columns for the specified table type.
     */
    List<AliasedColumn> getColumns(@Nonnull Class<? extends Data> table, @Nonnull SelectMode mode);

    /**
     * Compiles the given expression into its SQL representation.
     *
     * <p>This method resolves the expression type and delegates to the appropriate compilation strategy. Any template
     * placeholders or object-based expressions are converted into SQL fragments using the provided compiler.</p>
     *
     * @param expression the expression to compile.
     * @param compiler   the compiler responsible for producing SQL fragments.
     * @return the compiled SQL fragment representing the expression.
     */
    String compileExpression(@Nonnull Expression expression, @Nonnull TemplateCompiler compiler);

    /**
     * Binds all parameters required by the given expression to the provided binder.
     *
     * <p>The binding order is guaranteed to match the order used during compilation of the same expression. Nested
     * expressions and object-based expressions are handled recursively.</p>
     *
     * @param expression the expression whose parameters should be bound.
     * @param binder     the binder responsible for collecting parameter values.
     */
    void bindExpression(@Nonnull Expression expression, @Nonnull TemplateBinder binder);

    /**
     * Converts the specified column into an {@link AliasedColumn} using the current alias resolution rules.
     *
     * <p>The resolved alias depends on the column's metamodel path and the joins that were introduced while building
     * the query model. An exception is thrown if no suitable alias can be found.</p>
     *
     * @param column the column to convert.
     * @return the aliased representation of the column.
     */
    AliasedColumn toAliasedColumn(@Nonnull Column column);
}
