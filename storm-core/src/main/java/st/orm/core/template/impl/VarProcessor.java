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
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.impl.Elements.BindVar;

/**
 * A processor for a var element of a template.
 */
final class VarProcessor implements ElementProcessor<BindVar> {

    private final SqlTemplateProcessor templateProcessor;

    VarProcessor(@Nonnull SqlTemplateProcessor templateProcessor) {
        this.templateProcessor = templateProcessor;
    }

    /**
     * Process a var element of a template.
     *
     * @param bindVar the var element to process.
     * @return the result of processing the element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    @Override
    public ElementResult process(@Nonnull BindVar bindVar) throws SqlTemplateException {
        if (bindVar.bindVars() instanceof BindVarsImpl vars) {
            var parameterFactory = templateProcessor.setBindVars(vars, 1);
            vars.addParameterExtractor(record -> {
                parameterFactory.bind(bindVar.extractor().apply(record));
                try {
                    return parameterFactory.getParameters();
                } catch (SqlTemplateException e) {
                    throw new UncheckedSqlTemplateException(e);
                }
            });
            return new ElementResult("?");
        }
        throw new SqlTemplateException("Unsupported BindVars type.");
    }
}
