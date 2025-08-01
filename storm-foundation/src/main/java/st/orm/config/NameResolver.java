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
package st.orm.config;

import jakarta.annotation.Nonnull;

import static java.lang.Character.toLowerCase;

/**
 * Helper class to help with name resolution.
 */
final class NameResolver {

    private NameResolver() {
    }

    /**
     * Converts a camel case name to a snake case name.
     *
     * @param name the name to convert.
     * @return the converted name.
     */
    static String camelCaseToSnakeCase(@Nonnull String name) {
        StringBuilder columnName = new StringBuilder();
        columnName.append(toLowerCase(name.charAt(0)));
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                columnName.append("_").append(toLowerCase(c));
            } else {
                columnName.append(c);
            }
        }
        return columnName.toString();
    };
}
