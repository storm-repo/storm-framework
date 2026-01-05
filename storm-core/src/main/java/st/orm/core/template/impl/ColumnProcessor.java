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
import st.orm.core.template.SqlTemplate;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.impl.Elements.Column;

/**
 * A processor for a column element of a template.
 */
final class ColumnProcessor implements ElementProcessor<Column> {

    private final SqlTemplate template;
    private final ModelBuilder modelBuilder;
    private final AliasMapper aliasMapper;
    private final PrimaryTable primaryTable;

    ColumnProcessor(@Nonnull SqlTemplateProcessor templateProcessor) {
        this.template = templateProcessor.template();
        this.modelBuilder = templateProcessor.modelBuilder();
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
        var metamodel = column.field();
        var model = modelBuilder.build(metamodel.tableType(), false);
        String alias;
        if (primaryTable != null && primaryTable.table() == metamodel.root() && metamodel.path().isEmpty()) {
            // This check allows the alias of the primary table to be left empty, which can be useful in some cases like
            // update statements.
            alias = primaryTable.alias();
        } else{
            alias = aliasMapper.getAlias(metamodel, column.scope(), template.dialect(),
                    () -> new SqlTemplateException("Table for Column not found at %s.".formatted(metamodel)));
        }
        var columnName = model.getSingleColumn(metamodel).qualifiedName(template.dialect());
        return new ElementResult("%s%s".formatted(alias.isEmpty() ? "" : alias + ".", columnName));
    }
}
