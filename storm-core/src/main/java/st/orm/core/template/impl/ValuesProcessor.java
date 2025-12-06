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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.BindVars;
import st.orm.Data;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.core.template.SqlDialect;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.impl.Elements.Values;

import java.util.ArrayList;
import java.util.List;

/**
 * A processor for a values element of a template.
 */
final class ValuesProcessor implements ElementProcessor<Values> {

    private final SqlTemplateProcessor templateProcessor;
    private final SqlDialect dialect;
    private final ModelBuilder modelBuilder;
    private final PrimaryTable primaryTable;

    ValuesProcessor(@Nonnull SqlTemplateProcessor templateProcessor) {
        this.templateProcessor = templateProcessor;
        this.dialect = templateProcessor.template().dialect();
        this.modelBuilder = templateProcessor.modelBuilder();
        this.primaryTable = templateProcessor.primaryTable();
    }

    /**
     * Process a values element of a template.
     *
     * @param values the values element to process.
     * @return the result of processing the element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    @Override
    public ElementResult process(@Nonnull Values values) throws SqlTemplateException {
        if (primaryTable == null) {
            throw new SqlTemplateException("Primary entity not found.");
        }
        if (values.records() != null) {
            return getRecordsString(values.records(), values.ignoreAutoGenerate());
        }
        if (values.bindVars() != null) {
            return getBindVarsString(values.bindVars(), values.ignoreAutoGenerate());
        }
        throw new SqlTemplateException("No values found for Values.");
    }

    /**
     * Returns the SQL string for the specified records.
     *
     * @param records the records to process.
     * @param ignoreAutoGenerate whether to ignore the auto-generated flag.
     * @return the SQL string for the specified record.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    private ElementResult getRecordsString(@Nonnull Iterable<? extends Data> records, boolean ignoreAutoGenerate) throws SqlTemplateException {
        var table = primaryTable.table();
        List<String> args = new ArrayList<>();
        for (var record : records) {
            if (record == null) {
                throw new SqlTemplateException("Record is null.");
            }
            if (!table.isInstance(record)) {
                throw new SqlTemplateException("Record %s does not match entity %s.".formatted(record.getClass().getSimpleName(), table.getSimpleName()));
            }
            var model = modelBuilder.build(record, false);
            var values = ModelMapper.of(model).map(record, Column::insertable);
            if (values.isEmpty()) {
                throw new SqlTemplateException("No values found for Insert.");
            }
            List<String> placeholders = new ArrayList<>(values.size());
            for (var column : model.columns()) {
                if (!column.insertable()) {
                    continue;
                }
                switch (column.generation()) {
                    case NONE -> {
                        var value = values.get(column);
                        placeholders.add(templateProcessor.bindParameter(value));
                    }
                    case IDENTITY -> {
                        if (ignoreAutoGenerate) {
                            var value = values.get(column);
                            placeholders.add(templateProcessor.bindParameter(value));
                        }
                    }
                    case SEQUENCE -> {
                        if (ignoreAutoGenerate) {
                            var value = values.get(column);
                            placeholders.add(templateProcessor.bindParameter(value));
                        } else {
                            String sequenceName = column.sequence();
                            if (!sequenceName.isEmpty()) {
                                // Do NOT bind a value; emit sequence retrieval instead.
                                placeholders.add(dialect.sequenceNextVal(sequenceName));
                            }
                        }
                    }
                }
            }
            args.add("(%s)".formatted(String.join(", ", placeholders)));
            args.add(", ");
        }
        if (!args.isEmpty()) {
            args.removeLast();
        }
        return new ElementResult(String.join("", args));
    }

    /**
     * Returns the SQL string for the specified bindVars.
     *
     * @param bindVars the bindVars to process.
     * @param ignoreAutoGenerate whether to ignore the auto-generated flag.
     * @return the SQL string for the specified bindVars.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    private ElementResult getBindVarsString(@Nonnull BindVars bindVars, boolean ignoreAutoGenerate) throws SqlTemplateException {
        if (bindVars instanceof BindVarsImpl vars) {
            @SuppressWarnings("unchecked")
            Model<Data, ?> model = (Model<Data, ?>) modelBuilder.build(primaryTable.table(), false);
            var bindsVarCount = (int) model.columns().stream()
                    .filter(Column::insertable)
                    .filter(column -> switch (column.generation()) {
                        case NONE -> true;
                        case IDENTITY -> ignoreAutoGenerate;
                        case SEQUENCE -> ignoreAutoGenerate;
                    })
                    .count();
            var parameterFactory = templateProcessor.setBindVars(vars, bindsVarCount);
            vars.addParameterExtractor(record -> {
                try {
                    var values = ModelMapper.of(model).map(record, Column::insertable);
                    for (var column : model.columns()) {
                        if (!column.insertable()) {
                            continue;
                        }
                        switch (column.generation()) {
                            case NONE -> {
                                var value = values.get(column);
                                parameterFactory.bind(value);
                            }
                            case IDENTITY -> {
                                if (ignoreAutoGenerate) {
                                    var value = values.get(column);
                                    parameterFactory.bind(value);
                                }
                            }
                            case SEQUENCE -> {
                                if (ignoreAutoGenerate) {
                                    var value = values.get(column);
                                    parameterFactory.bind(value);
                                }
                                // Do nothing.
                            }
                        }
                    }
                    return parameterFactory.getParameters();
                } catch (SqlTemplateException ex) {
                    throw new UncheckedSqlTemplateException(ex);
                }
            });
            StringBuilder bindVarsString = new StringBuilder();
            for (var column : model.columns()) {
                if (!column.insertable()) {
                    continue;
                }
                switch (column.generation()) {
                    case NONE -> bindVarsString.append("?, ");
                    case IDENTITY -> {
                        if (ignoreAutoGenerate) {
                            bindVarsString.append("?, ");
                        }
                    }
                    case SEQUENCE -> {
                        if (ignoreAutoGenerate) {
                            bindVarsString.append("?, ");
                        } else {
                            String sequenceName = column.sequence();
                            if (!sequenceName.isEmpty()) {
                                bindVarsString.append(dialect.sequenceNextVal(sequenceName)).append(", ");
                            }
                        }
                    }
                }
            }
            if (!bindVarsString.isEmpty()) {
                bindVarsString.delete(bindVarsString.length() - ", ".length(), bindVarsString.length());
            }
            return new ElementResult("(%s)".formatted(bindVarsString));
        }
        throw new SqlTemplateException("Unsupported BindVars type.");
    }
}
