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
import st.orm.template.SqlTemplate.PositionalParameter;
import st.orm.template.SqlTemplateException;
import st.orm.template.impl.Elements.Var;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A processor for a var element of a template.
 */
final class VarProcessor implements ElementProcessor<Var> {

    private final SqlTemplateProcessor templateProcessor;
    private final AtomicInteger parameterPosition;

    VarProcessor(@Nonnull SqlTemplateProcessor templateProcessor) {
        this.templateProcessor = templateProcessor;
        this.parameterPosition = templateProcessor.parameterPosition();
    }

    /**
     * Process a var element of a template.
     *
     * @param var the var element to process.
     * @return the result of processing the element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    @Override
    public ElementResult process(@Nonnull Var var) throws SqlTemplateException {
        if (var.bindVars() instanceof BindVarsImpl vars) {
            templateProcessor.setBindVars(vars);
            final int position = parameterPosition.getAndIncrement();
            vars.addParameterExtractor(record -> List.of(new PositionalParameter(position, var.extractor().apply(record))));
            return new ElementResult("?");
        }
        throw new SqlTemplateException("Unsupported BindVars type.");
    }
}
