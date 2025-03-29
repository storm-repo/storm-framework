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
import st.orm.template.SqlTemplate;
import st.orm.template.SqlTemplateException;
import st.orm.template.impl.Elements.Table;

import static st.orm.template.impl.RecordReflection.getTableName;

/**
 * A processor for a table element of a template.
 */
final class TableProcessor implements ElementProcessor<Table> {

    private final SqlTemplate template;
    private final SqlDialectTemplate dialectTemplate;

    TableProcessor(@Nonnull SqlTemplateProcessor templateProcessor) {
        this.template = templateProcessor.template();
        this.dialectTemplate = templateProcessor.dialectTemplate();
    }

    /**
     * Process a table element of a template.
     *
     * @param table the table element to process.
     * @return the result of processing the element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    @Override
    public ElementResult process(@Nonnull Table table) throws SqlTemplateException {
        TableName tableName = getTableName(table.table(), template.tableNameResolver());
        String alias = table.alias();
        if (alias.isEmpty()) {
            return new ElementResult(dialectTemplate."\{tableName}");
        }
        return new ElementResult(dialectTemplate."\{tableName} \{alias}");
    }
}
