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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;

import java.util.regex.Pattern;

import static java.util.regex.Pattern.DOTALL;

/**
 * SQL parser for basic SQL processing.
 */
final class SqlParser {

    enum SqlMode {
        SELECT, INSERT, UPDATE, DELETE, UNDEFINED
    }

    private static final Pattern WITH_PATTERN = Pattern.compile("^(?i:WITH)\\W.*", DOTALL);
    private static final java.util.Map<Pattern, SqlMode> SQL_MODES = java.util.Map.of(
            Pattern.compile("^(?i:SELECT)\\W.*", DOTALL), SqlMode.SELECT,
            Pattern.compile("^(?i:INSERT)\\W.*", DOTALL), SqlMode.INSERT,
            Pattern.compile("^(?i:UPDATE)\\W.*", DOTALL), SqlMode.UPDATE,
            Pattern.compile("^(?i:DELETE)\\W.*", DOTALL), SqlMode.DELETE
    );

    private SqlParser() {
    }

    /**
     * Determines the SQL mode for the specified {@code stringTemplate}.
     *
     * @param stringTemplate The string template.
     * @return the SQL mode.
     */
    static SqlMode getSqlMode(@Nonnull StringTemplate stringTemplate) {
        String first = stringTemplate.fragments().getFirst().stripLeading();
        if (first.isEmpty()) {
            first = stringTemplate.values().stream()
                    .findFirst()
                    .filter(Elements.Unsafe.class::isInstance)
                    .map(Elements.Unsafe.class::cast)
                    .map(Elements.Unsafe::sql)
                    .orElse("");
        }
        String input = removeComments(first).stripLeading();
        if (WITH_PATTERN.matcher(input).matches()) {
            input = removeWithClause(removeComments(String.join("", stringTemplate.fragments())));
        }
        String sql = input.stripLeading();
        return SQL_MODES.entrySet().stream()
                .filter(e -> e.getKey().matcher(sql).matches())
                .map(java.util.Map.Entry::getValue)
                .findFirst()
                .orElse(SqlMode.UNDEFINED);
    }

    static String removeWithClause(String sql) {
        assert sql.trim().toUpperCase().startsWith("WITH");
        int depth = 0; // Depth of nested parentheses.
        boolean inSingleQuotes = false; // Track whether inside single quotes.
        boolean inDoubleQuotes = false; // Track whether inside double quotes.
        int startIndex = sql.indexOf('('); // Find the first opening parenthesis.
        // If there's no opening parenthesis after "WITH", return the original string.
        if (startIndex == -1) {
            return sql;
        }
        for (int i = startIndex; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            // Toggle state for single quotes.
            if (ch == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            }
            // Toggle state for double quotes.
            else if (ch == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            }
            // Count parentheses depth if not within quotes.
            if (!inSingleQuotes && !inDoubleQuotes) {
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                    if (depth == 0) {
                        // Found the matching closing parenthesis for the first opening parenthesis.
                        String afterWithClause = sql.substring(i + 1).trim();
                        // Check if it needs to remove a comma right after the closing parenthesis of WITH clause.
                        if (afterWithClause.startsWith(",")) {
                            afterWithClause = afterWithClause.substring(1).trim();
                        }
                        return afterWithClause;
                    }
                }
            }
        }
        // If depth never reaches 0, return the original string as it might be malformed or the logic above didn't correctly parse it.
        return sql;
    }

    /**
     * Removes both single-line and multi-line comments from a SQL string.
     *
     * @param sql The original SQL string.
     * @return The SQL string with comments removed.
     */
    static String removeComments(@Nonnull String sql) {
        // Pattern for multi-line comments.
        String multiLineCommentRegex = "(?s)/\\*.*?\\*/";
        // Pattern for single-line comments (both -- and #).
        String singleLineCommentRegex = "(--|#).*?(\\n|$)";
        // Remove multi-line comments, then single-line comments.
        return sql.replaceAll(multiLineCommentRegex, "")
                .replaceAll(singleLineCommentRegex, "")
                .stripLeading();
    }
}
