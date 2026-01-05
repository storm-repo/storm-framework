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
import st.orm.core.template.SqlTemplateException;

import static java.util.Objects.requireNonNull;

/**
 * Represents the SQL result of processing a template element. The result can be either a SQL string or, generation
 * is deferred by means of a {@link SqlGenerator}.
 *
 * @param generator the SQL generator.
 */
record ElementResult(@Nonnull SqlGenerator generator) implements SqlGenerator {
    ElementResult {
        requireNonNull(generator, "generator");
    }

    /**
     * Creates a new instance with the pre-rendered SQL string.
     *
     * @param sql the pre-rendered SQL string.
     */
    ElementResult(@Nonnull String sql) {
        this(() -> sql);
    }

    /**
     * Returns the SQL string.
     *
     * @return the SQL string.
     * @throws SqlTemplateException if an error occurs while generating the SQL string.
     */
    @Override
    public String get() throws SqlTemplateException {
        return generator.get();
    }
}
