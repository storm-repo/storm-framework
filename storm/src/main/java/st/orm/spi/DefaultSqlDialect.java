/*
 * Copyright 2024 the original author or authors.
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
package st.orm.spi;

import jakarta.annotation.Nonnull;

public class DefaultSqlDialect implements SqlDialect {

    /**
     * Indicates whether the SQL dialect supports delete aliases.
     *
     * <p>Delete aliases allow delete statements to use table aliases in joins,  making it easier to filter rows based
     * on related data.</p>
     *
     * @return {@code true} if delete aliases are supported, {@code false} otherwise.
     */
    @Override
    public boolean supportsDeleteAlias() {
        return false;
    }

    /**
     * Escapes the given database identifier (e.g., table or column name) according to this SQL dialect.
     *
     * @param name the identifier to escape (must not be {@code null})
     * @return the escaped identifier
     */
    @Override
    public String escape(@Nonnull String name) {
        return STR."\"\{name}\"";
    }

    @Override
    public StringTemplate limit(int limit) {
        // Taking the most basic approach that is supported by most database in test (containers).
        // For production use, ensure the right dialect is used.
        return StringTemplate.of(STR."LIMIT \{limit}");
    }

    @Override
    public StringTemplate limit(int limit, int offset) {
        // Taking the most basic approach that is supported by most database in test (containers).
        // For production use, ensure the right dialect is used.
        return StringTemplate.of(STR."LIMIT \{limit} OFFSET \{offset}");
    }
}
