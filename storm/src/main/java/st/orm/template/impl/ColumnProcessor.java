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
import st.orm.FK;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.SqlTemplate;
import st.orm.template.SqlTemplateException;
import st.orm.template.impl.Elements.Column;

import java.lang.reflect.RecordComponent;

import static st.orm.template.impl.RecordReflection.getColumnName;
import static st.orm.template.impl.RecordReflection.getForeignKey;
import static st.orm.template.impl.RecordReflection.getRecordComponent;

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
        boolean isNested = metamodel.table().componentType() != metamodel.root();
        if (isNested) {
            if (primaryTable == null) {
                throw new SqlTemplateException(STR."Nested metamodel \{metamodel} is not supported when not using a primary table.");
            }
            if (primaryTable.table() != metamodel.root()) {
                throw new SqlTemplateException(STR."Nested metamodel \{metamodel} is not the primary table \{primaryTable.table()}.");
            }
        }
        String alias = aliasMapper.getAlias(column.metamodel(), column.scope(), template.dialect(),
                () -> new SqlTemplateException(STR."Table for Column not found at \{metamodel}."));
        RecordComponent component = getRecordComponent(metamodel.root(), column.metamodel().componentPath());
        ColumnName columnName;
        if (REFLECTION.isAnnotationPresent(component, FK.class)) {
            columnName = getForeignKey(component, template.foreignKeyResolver());
        } else {
            columnName = getColumnName(component, template.columnNameResolver());
        }
        return new ElementResult(dialectTemplate."\{alias}.\{columnName}");
    }
}
