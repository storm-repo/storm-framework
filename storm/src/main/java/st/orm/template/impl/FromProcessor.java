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
import st.orm.template.impl.Elements.From;
import st.orm.template.impl.Elements.TableSource;
import st.orm.template.impl.Elements.TemplateSource;

import static st.orm.template.impl.RecordReflection.getTableName;

/**
 * A processor for a from element of a template.
 */
final class FromProcessor implements ElementProcessor<From> {

    private final SqlTemplateProcessor templateProcessor;
    private final SqlTemplate template;
    private final SqlDialectTemplate dialectTemplate;

    FromProcessor(@Nonnull SqlTemplateProcessor templateProcessor) {
        this.templateProcessor = templateProcessor;
        this.template = templateProcessor.template();
        this.dialectTemplate = templateProcessor.dialectTemplate();
    }

    /**
     * Process a from element of a template.
     *
     * @param from the from element to process.
     * @return the result of processing the element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    @Override
    public ElementResult process(@Nonnull From from) throws SqlTemplateException {
        return new ElementResult(switch (from) {
            case From(TableSource ts, _, _) ->
                    dialectTemplate."\{getTableName(ts.table(), template.tableNameResolver())}\{from.alias().isEmpty() ? "" : STR." \{from.alias()}"}";
            case From(TemplateSource ts, _, _) ->
                    STR."(\{templateProcessor.parse(ts.template(), false)})\{from.alias().isEmpty() ? "" : STR." \{from.alias()}"}";    // From-clause is not correlated.
        });
    }
}
