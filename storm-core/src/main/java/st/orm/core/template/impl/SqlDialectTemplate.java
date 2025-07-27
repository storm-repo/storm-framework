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
import st.orm.core.spi.Name;
import st.orm.core.template.SqlDialect;
import st.orm.core.template.TemplateString;

import static java.util.Objects.requireNonNull;

/**
 * SQL template processor that converts a {@link StringTemplate} into a SQL string using a {@link SqlDialect}.
 *
 * <p><strong>Note:</strong> Only {@code Name} values will be processed by the dialect, regular strings will be
 * appended as is and other types will be converted to strings using their {@code toString()} method.</p>
 */
final class SqlDialectTemplate {

    private final SqlDialect dialect;

    public SqlDialectTemplate(@Nonnull SqlDialect dialect) {
        this.dialect = requireNonNull(dialect);
    }

    /**
     * Process the given template string and return the resulting SQL string.
     *
     * @param template the template string to process.
     * @param values   the values to substitute in the template.
     * @return the processed SQL string.
     */
    public String process(@Nonnull String template, @Nonnull Object... values) {
        return process(TemplateString.raw(template, values));
    }

    /**
     * Process the given template string and return the resulting SQL string.
     *
     * @param template the template string to process.
     * @return the processed SQL string.
     */
    public String process(@Nonnull TemplateString template) {
        StringBuilder builder = new StringBuilder();
        int valuesSize = template.values().size();
        for (int i = 0; i < valuesSize; i++) {
            builder.append(template.fragments().get(i));
            var element = template.values().get(i);
            builder.append(switch (element) {
                case Name n -> n.getQualifiedName(dialect);
                default -> element.toString();
            });
        }
        builder.append(template.fragments().get(valuesSize));
        return builder.toString();
    }
}
