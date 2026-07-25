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
import java.util.List;
import java.util.SequencedMap;
import java.util.function.Function;
import st.orm.Operator;
import st.orm.core.template.SqlDialect;
import st.orm.core.template.SqlTemplateException;

/**
 * The single source of truth for rendering a column comparison in a WHERE clause. One column renders as the plain
 * {@code column OP (?...)} form; multiple columns render through the dialect's multi-column form, which is a tuple
 * comparison on dialects that support one and an {@code AND} expansion otherwise.
 *
 * <p>Both the value-based WHERE (values known at compile time) and the bind-variables WHERE (values bound per
 * execution) render their comparisons here. Routing them through one decision is what keeps a compiled
 * {@link st.orm.core.template.QueryPlan} from diverging from the per-call statement it replaces: a plan reuses the
 * bind-variables WHERE, so were this single-versus-multi-column decision duplicated across the two paths, they could
 * drift and a plan could emit different SQL, for example {@code (id) = (?)} where the per-call path emits
 * {@code id = ?}. There is nothing to keep in sync because there is one renderer.</p>
 */
final class ColumnComparison {

    private ColumnComparison() {
    }

    /**
     * Renders a comparison over one or more columns.
     *
     * <p>The comparison is rendered from one of two inputs. When {@code rows} is empty the caller supplies the
     * column and its already rendered placeholders directly, and the comparison renders as
     * {@code column OP (placeholders)}. When {@code rows} is non-empty each row maps columns to values: rows that
     * all resolve to a single column render as {@code column OP (?...)}, while rows spanning multiple columns render
     * through the dialect's multi-column form. In both non-empty cases {@code parameterFunction} produces each
     * placeholder; the value-based path passes a function that binds the value and returns a placeholder, the
     * bind-variables path passes one that returns a placeholder without binding.</p>
     *
     * @param operator the comparison operator.
     * @param singleColumn the column for the {@code rows}-empty form, or {@code null} otherwise.
     * @param singleColumnPlaceholders the rendered placeholders for the {@code rows}-empty form.
     * @param rows the column-to-value rows, empty when the column and placeholders are supplied directly.
     * @param parameterFunction renders a parameter for each value in {@code rows}.
     * @param dialect the SQL dialect.
     * @return the rendered comparison.
     * @throws SqlTemplateException if the dialect cannot render the comparison.
     */
    static String render(@Nonnull Operator operator,
                         @Nullable String singleColumn,
                         @Nonnull List<String> singleColumnPlaceholders,
                         @Nonnull List<SequencedMap<String, Object>> rows,
                         @Nonnull Function<Object, String> parameterFunction,
                         @Nonnull SqlDialect dialect) throws SqlTemplateException {
        if (rows.isEmpty()) {
            // The value-based path supplies its single column and already rendered placeholders directly.
            return operator.format(singleColumn, singleColumnPlaceholders.toArray(new String[0]));
        }
        if (rows.stream().allMatch(row -> row.size() == 1)) {
            // One column across the rows renders plain; routing it through the dialect's multi-column form would
            // wrap a single column in a tuple, e.g. "(id) = (?)".
            String column = rows.getFirst().firstEntry().getKey();
            String[] placeholders = rows.stream()
                    .map(row -> parameterFunction.apply(row.firstEntry().getValue()))
                    .toArray(String[]::new);
            return operator.format(column, placeholders);
        }
        return dialect.multiColumnExpression(operator, rows, parameterFunction);
    }
}
