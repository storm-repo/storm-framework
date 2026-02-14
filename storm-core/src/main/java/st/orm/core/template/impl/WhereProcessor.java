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
import static st.orm.core.template.impl.ElementRouter.getElementProcessor;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.function.Function;
import st.orm.BindVars;
import st.orm.Data;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.TemplateString;
import st.orm.core.template.impl.Elements.Where;

final class WhereProcessor implements ElementProcessor<Where> {
    record WhereBindHint(@Nonnull List<Column> columns) implements BindHint {}

    /**
     * Returns a key that represents the compiled shape of the given element.
     *
     * <p>The compilation key is used for caching compiled results. It must include all fields that can affect the
     * compilation output (SQL text, emitted fragments, placeholder shape, etc.). The key is compared using
     * value-based equality, so it should be immutable and implement stable {@code equals}/{@code hashCode}.</p>
     *
     * <p>If this method returns {@code null} for any element in a template, the compiled result is considered
     * non-cacheable and the template must be recompiled each time it is requested.</p>
     *
     * @param where the element to compute a key for.
     * @return an immutable key for caching, or {@code null} if the element (or its compilation) cannot be cached.
     * @throws SqlTemplateException if the key generation fails.
     */
    @Override
    public Object getCompilationKey(@Nonnull Where where, @Nonnull Function<TemplateString, Object> keyGenerator)
            throws SqlTemplateException {
        if (where.expression() != null) {
            var cacheable = new Cacheable(where.expression());
            return getElementProcessor(cacheable).getCompilationKey(cacheable, keyGenerator);
        }
        if (where.bindVars() != null) {
            return new Where(null, null);
        }
        throw new SqlTemplateException("Invalid where element: %s.".formatted(where));
    }

    /**
     * Compiles the given element into an {@link CompiledElement}.
     *
     * <p>This method is responsible for producing the compile-time representation of the element. It must not perform
     * runtime binding. Any binding should be deferred to {@link #bind(Where, TemplateBinder, BindHint)}.</p>
     *
     * @param where the element to compile.
     * @param compiler the active compiler context.
     * @return the compiled result for this element.
     * @throws SqlTemplateException if compilation fails.
     */
    @Override
    public CompiledElement compile(@Nonnull Where where, @Nonnull TemplateCompiler compiler) throws SqlTemplateException {
        if (where.expression() != null) {
            return new CompiledElement(compiler.getQueryModel().compileExpression(where.expression(), compiler),
                    new WhereBindHint(List.of()));
        }
        if (where.bindVars() != null) {
            return compileWhereBindVars(where.bindVars(), compiler);
        }
        throw new SqlTemplateException("No expression or bindVars found for Where.");
    }

    /**
     * Performs post-processing after compilation, typically binding runtime values for the element.
     *
     * <p>This method is called after the element has been compiled. Typical responsibilities include binding
     * parameters, registering bind variables, or applying runtime-only adjustments that must not affect the compiled
     * SQL shape.</p>
     *
     * @param where the element that was compiled.
     * @param binder the binder used to bind runtime values.
     */
    @Override
    public void bind(@Nonnull Where where, @Nonnull TemplateBinder binder, @Nonnull BindHint bindHint) {
        if (bindHint instanceof WhereBindHint(var columns)) {
            if (where.expression() != null) {
                assert columns.isEmpty();
                binder.getQueryModel().bindExpression(where.expression(), binder);
            }
            if (where.bindVars() instanceof BindVarsImpl vars) {
                var table = binder.getQueryModel().getTable();
                //noinspection unchecked
                var model = (Model<Data, ?>) binder.getModel(table.type());
                var parameterFactory = binder.setBindVars(where.bindVars());
                vars.addParameterExtractor(record -> {
                    try {
                        model
                                .forEachValue(columns, record,
                                        (column, value) -> parameterFactory.bind(value));
                        return parameterFactory.getParameters();
                    } catch (SqlTemplateException ex) {
                        throw new UncheckedSqlTemplateException(ex);
                    }
                });
            }
        } else {
            throw new IllegalStateException("Unexpected bind hint: %s.".formatted(bindHint.getClass().getSimpleName()));
        }
    }

    private List<Column> getIdentifyingColumns(@Nonnull TemplateCompiler compiler)
            throws SqlTemplateException {
        var table = compiler.getQueryModel().getTable();
        var columns = compiler.getModel(table.type()).declaredColumns();
        boolean hasPk = columns.stream().anyMatch(Column::primaryKey);
        if (!hasPk) {
            throw new SqlTemplateException("No primary key found for table: %s.".formatted(table.type().getSimpleName()));
        }
        return columns.stream()
                .filter(c -> c.primaryKey() || (compiler.isVersionAware() && c.version()))
                .toList();
    }

    private CompiledElement compileWhereBindVars(@Nonnull BindVars bindVars,
                                                 @Nonnull TemplateCompiler compiler) throws SqlTemplateException {
        if (bindVars instanceof BindVarsImpl) {
            var queryModel = compiler.getQueryModel();
            var columns = getIdentifyingColumns(compiler);
            compiler.mapBindVars(columns.size());
            return new CompiledElement(columns.stream()
                    .map(queryModel::toAliasedColumn)
                    .map(column -> "%s%s = ?".formatted(column.alias().isEmpty() ? "" : column.alias() + ".", column.name()))
                    .collect(joining(" AND ")), new WhereBindHint(columns));
        }
        throw new SqlTemplateException("Unsupported BindVars type.");
    }
}
