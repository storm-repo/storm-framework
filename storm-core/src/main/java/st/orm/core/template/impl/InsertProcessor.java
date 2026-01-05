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
import st.orm.core.template.Column;
import st.orm.core.template.SqlTemplate;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.impl.Elements.Insert;

import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.joining;
import static st.orm.core.template.impl.RecordReflection.getTableName;

/**
 * A processor for an insert element of a template.
 */
final class InsertProcessor implements ElementProcessor<Insert> {

    private final SqlTemplate template;
    private final SqlDialectTemplate dialectTemplate;
    private final ModelBuilder modelBuilder;
    private final List<String> generatedKeys;

    InsertProcessor(@Nonnull SqlTemplateProcessor templateProcessor) {
        this.template = templateProcessor.template();
        this.dialectTemplate = templateProcessor.dialectTemplate();
        this.modelBuilder = templateProcessor.modelBuilder();
        this.generatedKeys = templateProcessor.generatedKeys();
    }

    /**
     * Process an insert element of a template.
     *
     * @param insert the insert element to process.
     * @return the result of processing the element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    @Override
    public ElementResult process(@Nonnull Insert insert) throws SqlTemplateException {
        var model = modelBuilder.build(insert.table(), false);
        String columns = model.columns().stream()
                .filter(Column::insertable)
                .map(column -> {
                    if (column.primaryKey() && !insert.ignoreAutoGenerate()) {
                        return switch (column.generation()) {
                            case NONE -> column.qualifiedName(template.dialect());
                            case IDENTITY -> {
                                generatedKeys.add(column.qualifiedName(template.dialect()));
                                yield null;
                            }
                            case SEQUENCE -> {
                                if (!column.sequence().isEmpty()) {
                                    yield column.qualifiedName(template.dialect());
                                }
                                yield null;
                            }
                        };
                    }
                    return column.qualifiedName(template.dialect());
                })
                .filter(Objects::nonNull)
                .collect(joining(", "));
        return new ElementResult(dialectTemplate.process("\0 (\0)", getTableName(insert.table(), template.tableNameResolver()), columns));
    }
}
