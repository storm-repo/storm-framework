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
import st.orm.FK;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.core.template.SqlTemplate;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.impl.Elements.Column;
import st.orm.mapping.RecordField;

import static st.orm.core.template.impl.RecordReflection.getColumnName;
import static st.orm.core.template.impl.RecordReflection.getForeignKeys;
import static st.orm.core.template.impl.RecordReflection.getRecordField;

/**
 * A processor for a column element of a template.
 */
final class ColumnProcessor implements ElementProcessor<Column> {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private final SqlTemplate template;
    private final SqlDialectTemplate dialectTemplate;
    private final AliasMapper aliasMapper;
    private final PrimaryTable primaryTable;

    ColumnProcessor(@Nonnull SqlTemplateProcessor templateProcessor) {
        this.template = templateProcessor.template();
        this.dialectTemplate = templateProcessor.dialectTemplate();
        this.aliasMapper = templateProcessor.aliasMapper();
        this.primaryTable = templateProcessor.primaryTable();
    }

    /**
     * Process a column element of a template.
     *
     * @param column the column element to process.
     * @return the result of processing the element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    @Override
    public ElementResult process(@Nonnull Column column) throws SqlTemplateException {
        var metamodel = column.metamodel();
        boolean isNested = metamodel.table().fieldType() != metamodel.root();
        if (isNested) {
            if (primaryTable == null) {
                throw new SqlTemplateException("Nested metamodel %s is not supported when not using a primary table.".formatted(metamodel));
            }
            if (primaryTable.table() != metamodel.root()) {
                throw new SqlTemplateException("Nested metamodel %s is not the primary table %s.".formatted(metamodel, primaryTable.table()));
            }
        }
        String alias;
        if (primaryTable != null && primaryTable.table() == metamodel.root() && metamodel.path().isEmpty()) {
            // This check allows the alias of the primary table to be left empty, which can be useful in some cases like
            // update statements.
            alias = primaryTable.alias();
        } else{
            alias = aliasMapper.getAlias(column.metamodel(), column.scope(), template.dialect(),
                    () -> new SqlTemplateException("Table for Column not found at %s.".formatted(metamodel)));
        }
        RecordField field = getRecordField(metamodel.root(), column.metamodel().fieldPath());
        ColumnName columnName;
        if (field.isAnnotationPresent(FK.class)) {
            var columnNames = getForeignKeys(field, template.foreignKeyResolver(), template.columnNameResolver());
            if (columnNames.size() != 1) {
                throw new SqlTemplateException("Column %s is not a single foreign key.".formatted(field));
            }
            columnName = columnNames.getFirst();
        } else {
            columnName = getColumnName(field, template.columnNameResolver());
        }
        return new ElementResult(dialectTemplate.process("\0\0", alias.isEmpty() ? "" : alias + ".", columnName));
    }
}
