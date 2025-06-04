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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.BindVars;
import st.orm.template.SqlTemplate;
import st.orm.template.SqlTemplateException;
import st.orm.template.impl.Elements.Set;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.stream.Collectors.joining;

/**
 * A processor for a set element of a template.
 */
final class SetProcessor implements ElementProcessor<Set> {

    private final SqlTemplateProcessor templateProcessor;
    private final SqlTemplate template;
    private final SqlDialectTemplate dialectTemplate;
    private final ModelBuilder modelBuilder;
    private final PrimaryTable primaryTable;
    private final AtomicBoolean versionAware;

    SetProcessor(@Nonnull SqlTemplateProcessor templateProcessor) {
        this.templateProcessor = templateProcessor;
        this.template = templateProcessor.template();
        this.dialectTemplate = templateProcessor.dialectTemplate();
        this.modelBuilder = templateProcessor.modelBuilder();
        this.primaryTable = templateProcessor.primaryTable();
        this.versionAware = templateProcessor.versionAware();
    }

    /**
     * Process a set element of a template.
     *
     * @param set the set element to process.
     * @return the result of processing the element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    @Override
    public ElementResult process(@Nonnull Set set) throws SqlTemplateException {
        if (primaryTable == null) {
            throw new SqlTemplateException("Primary table not found.");
        }
        if (set.record() != null) {
            return getRecordString(set.record());
        }
        if (set.bindVars() != null) {
            return getBindVarsString(set.bindVars());
        }
        throw new SqlTemplateException("No values found for Set.");
    }

    /**
     * Returns the SQL string for the specified record.
     *
     * @param record the record to process.
     * @return the SQL string for the specified record.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    private ElementResult getRecordString(@Nonnull Record record) throws SqlTemplateException {
        if (!primaryTable.table().isInstance(record)) {
            throw new SqlTemplateException(STR."Record \{record.getClass().getSimpleName()} does not match entity \{primaryTable.table().getSimpleName()}.");
        }
        var mapped = ModelMapper.of(modelBuilder.build(record, false))
                .map(record, column -> !column.primaryKey() && column.updatable());
        List<String> args = new ArrayList<>();
        for (var entry : mapped.entrySet()) {
            var column = entry.getKey();
            if (!column.version()) {
                args.add(dialectTemplate."\{primaryTable.alias().isEmpty() ? "" : STR."\{primaryTable.alias()}."}\{column.qualifiedName(template.dialect())} = \{templateProcessor.bindParameter(entry.getValue())}");
                args.add(", ");
            } else {
                var versionString = getVersionString(column.qualifiedName(template.dialect()), column.type(), primaryTable.alias());
                versionAware.setPlain(true);
                args.add(versionString);
                args.add(", ");
            }
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
     * @return the SQL string for the specified bindVars.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    private ElementResult getBindVarsString(@Nonnull BindVars bindVars) throws SqlTemplateException {
        if (bindVars instanceof BindVarsImpl vars) {
            AtomicInteger bindVarsCount = new AtomicInteger();
            String bindVarsString = modelBuilder.build(primaryTable.table(), false)
                    .columns().stream()
                    .filter(column -> !column.primaryKey() && column.updatable())
                    .map(column -> {
                        if (!column.version()) {
                            bindVarsCount.incrementAndGet();
                            return STR."\{primaryTable.alias().isEmpty() ? "" : STR."\{primaryTable.alias()}."}\{column.qualifiedName(template.dialect())} = ?";
                        }
                        versionAware.setPlain(true);
                        return getVersionString(column.qualifiedName(template.dialect()), column.type(), primaryTable.alias());
                    })
                    .collect(joining(", "));
            var parameterFactory = templateProcessor.setBindVars(vars, bindVarsCount.getPlain());
            vars.addParameterExtractor(record -> {
                try {
                    ModelMapper.of(modelBuilder.build(record, false))
                            .map(record, column -> !column.primaryKey() && column.updatable() && !column.version())
                            .values()
                            .forEach(parameterFactory::bind);
                    return parameterFactory.getParameters();
                } catch (SqlTemplateException ex) {
                    throw new UncheckedSqlTemplateException(ex);
                }
            });
            return new ElementResult(bindVarsString);
        }
        throw new SqlTemplateException("Unsupported BindVars type.");
    }

    /**
     * Returns the version string for the version column.
     *
     * @param columnName the column name of the version column.
     * @param type the type of the version column.
     * @param alias the alias of the table.
     * @return the version string for the version column.
     */
    private static String getVersionString(@Nonnull String columnName, @Nonnull Class<?> type, @Nonnull String alias) {
        String value = switch (type) {
            case Class<?> c when
                    Integer.TYPE.isAssignableFrom(c)
                            || Long.TYPE.isAssignableFrom(c)
                            || Integer.class.isAssignableFrom(c)
                            || Long.class.isAssignableFrom(c)
                            || BigInteger.class.isAssignableFrom(c) -> STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{columnName} + 1";
            case Class<?> c when
                    Instant.class.isAssignableFrom(c)
                            || Date.class.isAssignableFrom(c)
                            || Calendar.class.isAssignableFrom(c)
                            || Timestamp.class.isAssignableFrom(c) -> "CURRENT_TIMESTAMP";
            default -> STR."\{columnName}";
        };
        return STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{columnName} = \{value}";
    }
}
