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
import st.orm.core.template.impl.Elements.Unsafe;

/**
 * A processor for an unsafe element of a template.
 */
final class UnsafeProcessor implements ElementProcessor<Unsafe> {

    public UnsafeProcessor(@Nonnull SqlTemplateProcessor templateProcessor) {
    }

    /**
     * Process an unsafe element of a template.
     *
     * @param unsafe the unsafe element to process.
     * @return the result of processing the element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    @Override
    public ElementResult process(@Nonnull Unsafe unsafe) throws SqlTemplateException {
        return new ElementResult(unsafe.sql());
    }
}
