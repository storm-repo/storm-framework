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
import jakarta.annotation.Nullable;

/**
 * Represents a reference to a column qualified by a table alias, with optional SQL expression override.
 *
 * <p>A {@code ColumnExpression} identifies a column as it appears in a SQL query, using a table alias and a column
 * name. It does not represent a column definition, but a reference that can be rendered into SQL.</p>
 *
 * <p>When an {@code expression} is provided, it replaces the default {@code alias.name} rendering. This is used for
 * synthetic columns such as CASE-based discriminator expressions in joined table inheritance without a
 * discriminator column.</p>
 *
 * @param type the type of the column.
 * @param name the column name.
 * @param alias the table alias used to qualify the column.
 * @param index the column index.
 * @param expression optional SQL expression that overrides the default alias.name rendering.
 * @since 1.7
 */
public record ColumnExpression(@Nonnull Class<?> type, @Nonnull String name, @Nonnull String alias, int index,
                                @Nullable String expression) {

    /**
     * Convenience constructor without expression (delegates with null expression).
     */
    public ColumnExpression(@Nonnull Class<?> type, @Nonnull String name, @Nonnull String alias, int index) {
        this(type, name, alias, index, null);
    }

    /**
     * Returns the SQL representation of this column reference.
     *
     * <p>If an expression is set, it is returned directly. Otherwise, the column is rendered as
     * {@code alias.name} (or just {@code name} if the alias is empty).</p>
     *
     * @return the SQL fragment for this column.
     */
    public String toSql() {
        if (expression != null) {
            return expression;
        }
        return alias.isEmpty() ? name : alias + "." + name;
    }
}
