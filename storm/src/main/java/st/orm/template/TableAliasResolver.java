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
package st.orm.template;

import jakarta.annotation.Nonnull;

import static java.lang.Character.isUpperCase;
import static java.lang.Character.toLowerCase;

/**
 * Resolves the alias for a table.
 */
@FunctionalInterface
public interface TableAliasResolver {

    TableAliasResolver DEFAULT = camelCaseAbbreviation();

    static TableAliasResolver camelCaseAbbreviation() {
        return (type, counter) -> {
            // Extract the capitals from the class name to form the base alias.
            String name = type.getSimpleName();
            StringBuilder aliasBuilder = new StringBuilder("_");    // Use underscore as prefix to avoid clashes with client aliases.
            for (char ch : name.toCharArray()) {
                if (isUpperCase(ch)) {
                    aliasBuilder.append(toLowerCase(ch));
                }
            }
            return STR."\{aliasBuilder}\{counter == 0 ? "" : STR."_\{counter}"}";
        };
    }
    /**
     * Resolves the alias for a table.
     *
     * @param type the record type.
     * @param counter counter used track the number of attempts.
     * @return the alias.
     */
    String resolveTableAlias(@Nonnull Class<? extends Record> type, int counter);
}
