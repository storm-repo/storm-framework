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
import st.orm.core.template.impl.Elements.Update;

import static st.orm.core.template.impl.RecordReflection.getTableName;

/**
 * A processor for an update element of a template.
 */
final class UpdateProcessor implements ElementProcessor<Update> {

    private final SqlTemplate template;
    private final SqlDialectTemplate dialectTemplate;

    UpdateProcessor(@Nonnull SqlTemplateProcessor templateProcessor) {
        this.template = templateProcessor.template();
        this.dialectTemplate = templateProcessor.dialectTemplate();
    }

    /**
     * Process an update element of a template.
     *
     * @param update the update element to process.
     * @return the result of processing the element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    @Override
    public ElementResult process(@Nonnull Update update) throws SqlTemplateException {
        return new ElementResult(dialectTemplate.process("\0\0",
                getTableName(update.table(), template.tableNameResolver()),
                update.alias().isEmpty() ? "" : " " + update.alias()));
    }
}
