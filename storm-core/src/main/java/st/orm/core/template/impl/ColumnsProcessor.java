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

import static java.util.stream.Collectors.joining;

import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.impl.Elements.Columns;

/**
 * Renders a {@link Columns} element: every column the metamodel resolves to, comma-separated.
 *
 * <p>Resolution mirrors {@link ColumnProcessor}, but where a {@code Column} requires the metamodel to resolve to
 * exactly one column, this processor accepts multi-column paths (a foreign key to a table with a compound primary
 * key, or an inline record) and expands them in model column order. This is the expansion used by the
 * {@code groupBy} and {@code orderBy} metamodel overloads, keeping their column resolution identical to predicate
 * resolution.</p>
 *
 * @since 1.13
 */
final class ColumnsProcessor implements ElementProcessor<Columns> {

    /**
     * Returns a key that represents the compiled shape of the given element.
     *
     * <p>The compilation key is used for caching compiled results. It must include all fields that can affect the
     * compilation output (SQL text, emitted fragments, placeholder shape, etc.). The key is compared using
     * value-based equality, so it should be immutable and implement stable {@code equals}/{@code hashCode}.</p>
     *
     * @param columns the element to compute a key for.
     * @return an immutable key for caching, or {@code null} if the element (or its compilation) cannot be cached.
     */
    @Override
    public Object getCompilationKey(Columns columns) {
        return columns;
    }

    /**
     * Compiles the given element into an {@link CompiledElement}.
     *
     * <p>This method is responsible for producing the compile-time representation of the element. It must not perform
     * runtime binding. Any binding should be deferred to {@link #bind(Columns, TemplateBinder, BindHint)}.</p>
     *
     * @param columns the element to compile.
     * @param compiler the active compiler context.
     * @return the compiled result for this element.
     * @throws SqlTemplateException if compilation fails.
     */
    @Override
    public CompiledElement compile(Columns columns, TemplateCompiler compiler)
            throws SqlTemplateException {
        var metamodel = MetamodelFactory.canonical(columns.field());
        var model = compiler.getModel(metamodel.tableType());
        String alias = compiler.findQueryModel()
                .map(QueryModel::getTable)
                .filter(table -> table.type() == metamodel.root() && metamodel.path().isEmpty())
                .map(AliasedTable::alias)
                .orElseGet(() -> compiler.getAlias(metamodel, columns.scope()));
        var resolved = model.getColumns(metamodel);
        if (resolved.isEmpty()) {
            throw new SqlTemplateException("No columns found for metamodel: %s.%s.%s"
                    .formatted(metamodel.fieldType(), metamodel.path(), metamodel.field()));
        }
        String prefix = alias.isEmpty() ? "" : alias + ".";
        String suffix = columns.descending() ? " DESC" : "";
        String sql = resolved.stream()
                .map(column -> prefix + column.qualifiedName(compiler.dialect()) + suffix)
                .collect(joining(", "));
        return new CompiledElement(sql);
    }

    /**
     * Performs post-processing after compilation, typically binding runtime values for the element.
     *
     * <p>This method is called after the element has been compiled. Typical responsibilities include binding
     * parameters, registering bind variables, or applying runtime-only adjustments that must not affect the compiled
     * SQL shape.</p>
     *
     * @param columns the element that was compiled.
     * @param binder the binder used to bind runtime values.
     * @param bindHint the bind hint for the element, providing additional context for binding.
     */
    @Override
    public void bind(Columns columns, TemplateBinder binder, BindHint bindHint) {
    }
}
