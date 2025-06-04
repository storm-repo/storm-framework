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
import st.orm.template.impl.Elements.Param;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A processor for a param element of a template.
 */
final class ParamProcessor implements ElementProcessor<Param> {

    private final SqlTemplateProcessor templateProcessor;
    private final SqlTemplate template;
    private final AtomicInteger nameIndex;

    ParamProcessor(@Nonnull SqlTemplateProcessor templateProcessor) {
        this.templateProcessor = templateProcessor;
        this.template = templateProcessor.template();
        this.nameIndex = templateProcessor.nameIndex();
    }

    /**
     * Process a param element of a template.
     *
     * @param param the param element to process.
     * @return the result of processing the element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    @Override
    public ElementResult process(@Nonnull Param param) throws SqlTemplateException {
        if (param.name() != null) {
            if (template.positionalOnly()) {
                throw new SqlTemplateException("Named parameters not supported.");
            }
            return new ElementResult(templateProcessor.bindParameter(param.name(), param.dbValue()));
        }
        if (template.positionalOnly()) {
            return new ElementResult(templateProcessor.bindParameter(param.dbValue()));
        }
        String name = STR."_p\{nameIndex.getAndIncrement()}";
        return new ElementResult(templateProcessor.bindParameter(name, param.dbValue()));
    }
}
