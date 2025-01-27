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
import st.orm.template.SqlTemplate;
import st.orm.template.impl.Elements.Unsafe;

import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.DOTALL;

/**
 * SQL parser for basic SQL processing.
 */
final class SqlParser {

    /**
     * Represents the SQL mode.
     *
     * <p>The SQL mode provides context for the {@link SqlTemplate} processor, enabling validation and inference by the
     * SQL template logic. Each mode indicates the type of SQL operation being performed, which allows the framework to
     * adapt behavior accordingly.</p>
     */
    enum SqlMode {

        /**
         * Represents a SELECT operation.
         *
         * <p>This mode is used for queries that retrieve data from the database without modifying its state. The SQL
         * template processor uses this mode to validate that the query conforms to the structure of a SELECT
         * statement.</p>
         */
        SELECT,

        /**
         * Represents an INSERT operation.
         *
         * <p>This mode is used for queries that add new records to the database. The SQL template processor validates
         * that parameters align with the fields being inserted.</p>
         */
        INSERT,

        /**
         * Represents an UPDATE operation.
         *
         * <p>This mode is used for queries that modify existing records in the database. The SQL template processor
         * ensures that the query includes a WHERE clause (if required) to avoid unintentional updates across all
         * rows.</p>
         */
        UPDATE,

        /**
         * Represents a DELETE operation.
         *
         * <p>This mode is used for queries that remove records from the database. The SQL template processor validates
         * the presence of a WHERE clause (if required) to prevent unintended deletion of all rows.</p>
         */
        DELETE,

        /**
         * Represents an undefined operation.
         *
         * <p>This mode is used when the type of SQL operation cannot be determined. The SQL template processor may
         * apply relaxed validation or inference rules when this mode is specified.</p>
         */
        UNDEFINED
    }

    private static final Pattern WITH_PATTERN = Pattern.compile("^(?i:WITH)\\W.*", DOTALL);
    private static final Map<Pattern, SqlMode> SQL_MODES = Map.of(
            Pattern.compile("^(?i:SELECT)\\W.*", DOTALL), SqlMode.SELECT,
            Pattern.compile("^(?i:INSERT)\\W.*", DOTALL), SqlMode.INSERT,
            Pattern.compile("^(?i:UPDATE)\\W.*", DOTALL), SqlMode.UPDATE,
            Pattern.compile("^(?i:DELETE)\\W.*", DOTALL), SqlMode.DELETE
    );
    private static final Pattern WHERE_PATTERN = Pattern.compile(
            "(?i:\\bWHERE\\b)",
            Pattern.DOTALL
    );

    private SqlParser() {
    }

    private static String getSql(@Nonnull StringTemplate template) {
        String first = template.fragments().getFirst().stripLeading();
        if (first.isEmpty()) {
            first = template.values().stream()
                    .findFirst()
                    .filter(Unsafe.class::isInstance)
                    .map(Unsafe.class::cast)
                    .map(Unsafe::sql)
                    .orElse("");
        }
        String input = removeComments(first).stripLeading();
        if (WITH_PATTERN.matcher(input).matches()) {
            input = removeWithClause(removeComments(String.join("", template.fragments())));
        }
        return input.stripLeading();
    }

    /**
     * Determines the SQL mode for the specified {@code stringTemplate}.
     *
     * @param template The string template.
     * @return the SQL mode.
     */
    static SqlMode getSqlMode(@Nonnull StringTemplate template) {
        String sql = getSql(template);
        return SQL_MODES.entrySet().stream()
                .filter(e -> e.getKey().matcher(sql).matches())
                .map(Entry::getValue)
                .findFirst()
                .orElse(SqlMode.UNDEFINED);
    }

    static boolean hasWhereClause(@Nonnull String sql) {
        return WHERE_PATTERN.matcher(removeStringLiterals(removeComments(sql))).find();
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
     * @param sql the original SQL string.
     * @return the SQL string with comments removed.
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

    /**
     * Removes string literals from a SQL string.
     * @param sql the original SQL string.
     * @return the SQL string with string literals removed.
     */
    static String removeStringLiterals(@Nonnull String sql) {
        // Regex for single-quoted string literals, handling both double single quotes and backslash escapes.
        String singleQuotedStringRegex = "'(?:''|\\.|[^'])*'";
        return sql.replaceAll(singleQuotedStringRegex, "")
                .stripLeading();
    }
}
