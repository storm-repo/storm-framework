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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import st.orm.Data;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.impl.Elements.Values;

final class ValuesProcessor implements ElementProcessor<Values> {
    record ValuesBindHint(@Nonnull List<Column> columns) implements BindHint {}

    private static final List<Data> EMPTY_DATA = List.of();

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
     * @param values the element to compute a key for.
     * @return an immutable key for caching, or {@code null} if the element (or its compilation) cannot be cached.
     */
    @Override
    public Object getCompilationKey(@Nonnull Values values) {
        if (values.records() != null) {
            if (hasAtMostOneElement(values.records())) {
                // Only cache when record-count is 1.
                return new Values(EMPTY_DATA, null, values.ignoreAutoGenerate());
            }
            return null;
        }
        return new Values(null, null, values.ignoreAutoGenerate());
    }

    /**
     * Compiles the given element into an {@link CompiledElement}.
     *
     * <p>This method is responsible for producing the compile-time representation of the element. It must not perform
     * runtime binding. Any binding should be deferred to {@link #bind(Values, TemplateBinder, BindHint)}.</p>
     *
     * @param values the element to compile.
     * @param compiler the active compiler context.
     * @return the compiled result for this element.
     * @throws SqlTemplateException if compilation fails.
     */
    @Override
    public CompiledElement compile(@Nonnull Values values, @Nonnull TemplateCompiler compiler) throws SqlTemplateException {
        if (values.records() != null) {
            return compileValues(values, compiler);
        }
        if (values.bindVars() != null) {
            return compileValuesBindVars(values, compiler);
        }
        throw new SqlTemplateException("No values found for Values.");
    }

    /**
     * Performs post-processing after compilation, typically binding runtime values for the element.
     *
     * <p>This method is called after the element has been compiled. Typical responsibilities include binding
     * parameters, registering bind variables, or applying runtime-only adjustments that must not affect the compiled
     * SQL shape.</p>
     *
     * @param values the element that was compiled.
     * @param binder the binder used to bind runtime values.
     * @param bindHint the bind hint for the element, providing additional context for binding.
     */
    @Override
    public void bind(@Nonnull Values values, @Nonnull TemplateBinder binder, @Nonnull BindHint bindHint) throws SqlTemplateException {
        if (bindHint instanceof ValuesBindHint(List<Column> columns)) {
            var queryModel = binder.getQueryModel();
            var table = queryModel.getTable();
            //noinspection unchecked
            var model = (Model<Data, ?>) binder.getModel(table.type());
            if (values.records() != null) {
                values.records().forEach(record -> {
                    try {
                        model.forEachValue(columns, record, (column, value) -> {
                            switch (column.generation()) {
                                case NONE -> binder.bindParameter(value);
                                case IDENTITY, SEQUENCE -> {
                                    if (values.ignoreAutoGenerate()) {
                                        binder.bindParameter(value);
                                    }
                                }
                            }
                        });
                    } catch (SqlTemplateException e) {
                        throw new UncheckedSqlTemplateException(e);
                    }
                });
            }
            if (values.bindVars() instanceof BindVarsImpl vars) {
                var parameterFactory = binder.setBindVars(vars);
                vars.addParameterExtractor(record -> {
                    try {
                        model.forEachValue(columns, record, (column, value) -> {
                            switch (column.generation()) {
                                case NONE -> parameterFactory.bind(value);
                                case IDENTITY, SEQUENCE -> {
                                    if (values.ignoreAutoGenerate()) {
                                        parameterFactory.bind(value);
                                    }
                                }
                            }
                        });
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

    private static boolean hasAtMostOneElement(Iterable<?> iterable) {
        Iterator<?> it = iterable.iterator();
        if (!it.hasNext()) {
            return true; // 0.
        }
        it.next();
        return !it.hasNext(); // True if exactly 1.
    }

    private CompiledElement compileValues(@Nonnull Values values, @Nonnull TemplateCompiler compiler) throws SqlTemplateException {
        assert values.records() != null;
        var queryModel = compiler.getQueryModel();
        var table = queryModel.getTable();
        //noinspection unchecked
        var model = (Model<Data, ?>) compiler.getModel(table.type());
        var columns = model.declaredColumns().stream()
                .filter(Column::insertable)
                .toList();
        List<String> args = new ArrayList<>();
        for (var record : values.records()) {
            if (record == null) {
                throw new SqlTemplateException("Record is null.");
            }
            if (!table.type().isInstance(record)) {
                throw new SqlTemplateException("Record %s does not match entity %s.".formatted(record.getClass().getSimpleName(), table.type().getSimpleName()));
            }
            List<String> placeholders = new ArrayList<>();
            model.forEachValue(columns, record, (column, value) -> {
                switch (column.generation()) {
                    case NONE -> placeholders.add(compiler.mapParameter(value));
                    case IDENTITY -> {
                        if (values.ignoreAutoGenerate()) {
                            placeholders.add(compiler.mapParameter(value));
                        }
                    }
                    case SEQUENCE -> {
                        if (values.ignoreAutoGenerate()) {
                            placeholders.add(compiler.mapParameter(value));
                        } else {
                            String sequenceName = column.sequence();
                            if (!sequenceName.isEmpty()) {
                                // Do NOT bind a value; emit sequence retrieval instead.
                                placeholders.add(compiler.dialect().sequenceNextVal(sequenceName));
                            }
                        }
                    }
                }
            });
            args.add("(%s)".formatted(String.join(", ", placeholders)));
            args.add(", ");
        }
        if (!args.isEmpty()) {
            args.removeLast();
        }
        return new CompiledElement(String.join("", args), new ValuesBindHint(columns));
    }

    private CompiledElement compileValuesBindVars(@Nonnull Values values, @Nonnull TemplateCompiler compiler)
            throws SqlTemplateException {
        assert values.bindVars() != null;
        if (values.bindVars() instanceof BindVarsImpl) {
            var queryModel = compiler.getQueryModel();
            var table = queryModel.getTable();
            //noinspection unchecked
            var model = (Model<Data, ?>) compiler.getModel(table.type());
            var columns = model.declaredColumns().stream()
                    .filter(Column::insertable)
                    .toList();
            var bindsVarCount = (int) model.declaredColumns().stream()
                    .filter(Column::insertable)
                    .filter(column -> switch (column.generation()) {
                        case NONE -> true;
                        case IDENTITY, SEQUENCE -> values.ignoreAutoGenerate();
                    })
                    .count();
            StringBuilder bindVarsString = new StringBuilder();
            for (var column : columns) {
                switch (column.generation()) {
                    case NONE -> bindVarsString.append("?, ");
                    case IDENTITY -> {
                        if (values.ignoreAutoGenerate()) {
                            bindVarsString.append("?, ");
                        }
                    }
                    case SEQUENCE -> {
                        if (values.ignoreAutoGenerate()) {
                            bindVarsString.append("?, ");
                        } else {
                            String sequenceName = column.sequence();
                            if (!sequenceName.isEmpty()) {
                                bindVarsString.append(compiler.dialect().sequenceNextVal(sequenceName)).append(", ");
                            }
                        }
                    }
                }
            }
            compiler.mapBindVars(bindsVarCount);
            if (!bindVarsString.isEmpty()) {
                bindVarsString.delete(bindVarsString.length() - ", ".length(), bindVarsString.length());
            }
            return new CompiledElement("(%s)".formatted(bindVarsString), new ValuesBindHint(columns));
        }
        throw new SqlTemplateException("Unsupported BindVars type.");
    }
}
