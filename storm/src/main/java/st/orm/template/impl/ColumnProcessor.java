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
import st.orm.Lazy;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.SqlTemplate;
import st.orm.template.SqlTemplateException;
import st.orm.template.impl.Elements.Column;

import java.lang.reflect.RecordComponent;

import static st.orm.template.impl.RecordReflection.getColumnName;
import static st.orm.template.impl.RecordReflection.getForeignKey;
import static st.orm.template.impl.RecordReflection.getLazyRecordType;
import static st.orm.template.impl.RecordReflection.getRecordComponent;

/**
 * A processor for a column element of a template.
 */
final class ColumnProcessor implements ElementProcessor<Column> {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private final SqlTemplate template;
    private final SqlDialectTemplate dialectTemplate;
    private final AliasMapper aliasMapper;

    ColumnProcessor(@Nonnull SqlTemplateProcessor templateProcessor) {
        this.template = templateProcessor.template();
        this.dialectTemplate = templateProcessor.dialectTemplate();
        this.aliasMapper = templateProcessor.aliasMapper();
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
        RecordComponent component = getRecordComponent(column.metamodel().root(), column.metamodel().componentPath());
        String alias;
        ColumnName columnName;
        if (REFLECTION.isAnnotationPresent(component, FK.class)) {
            Class<?> table = component.getDeclaringRecord();
            if (Lazy.class.isAssignableFrom(table)) {
                table = getLazyRecordType(component);
            }
            //noinspection unchecked
            alias = aliasMapper.getAlias((Class<? extends Record>) table, column.metamodel().table().path(),
                    column.scope(), template.dialect());
            columnName = getForeignKey(component, template.foreignKeyResolver());
        } else {
            alias = aliasMapper.getAlias(column.metamodel(), column.scope(), template.dialect());
            columnName = getColumnName(component, template.columnNameResolver());
        }
        return new ElementResult(dialectTemplate."\{alias}.\{columnName}");
    }
}
