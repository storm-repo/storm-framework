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
import st.orm.core.template.SqlTemplate;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.impl.Elements.Alias;

/**
 * A processor for an alias element of a template.
 */
final class AliasProcessor implements ElementProcessor<Alias> {

    private final SqlTemplate template;
    private final AliasMapper aliasMapper;

    AliasProcessor(@Nonnull SqlTemplateProcessor templateProcessor) {
        this.template = templateProcessor.template();
        this.aliasMapper = templateProcessor.aliasMapper();
    }

    /**
     * Process an alias element of a template.
     *
     * @param alias the alias element to process.
     * @return the result of processing the element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    @Override
    public ElementResult process(@Nonnull Alias alias) throws SqlTemplateException {
        return new ElementResult(aliasMapper.getAlias(alias.table(), null, alias.scope(), template.dialect()));
    }
}
