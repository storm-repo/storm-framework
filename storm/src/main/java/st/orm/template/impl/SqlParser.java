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
import st.orm.spi.Providers;
import st.orm.spi.SqlDialect;
import st.orm.template.SqlTemplate;
import st.orm.template.impl.Elements.Unsafe;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.DOTALL;
import static st.orm.template.impl.SqlParser.SqlMode.DELETE;
import static st.orm.template.impl.SqlParser.SqlMode.INSERT;
import static st.orm.template.impl.SqlParser.SqlMode.SELECT;
import static st.orm.template.impl.SqlParser.SqlMode.UNDEFINED;
import static st.orm.template.impl.SqlParser.SqlMode.UPDATE;

/**
 * SQL parser for basic SQL processing.
 */
final class SqlParser {

    private static final SqlDialect SQL_DIALECT = Providers.getSqlDialect();

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

    private static final Pattern WITH_PATTERN = Pattern.compile("^(?i:WITH)\\b.*", DOTALL);
    private static final Map<Pattern, SqlMode> SQL_MODES = Map.of(
            Pattern.compile("^(?i:SELECT)\\b.*", DOTALL), SELECT,
            Pattern.compile("^(?i:INSERT)\\b.*", DOTALL), INSERT,
            Pattern.compile("^(?i:UPDATE)\\b.*", DOTALL), UPDATE,
            Pattern.compile("^(?i:DELETE)\\b.*", DOTALL), DELETE
    );
    private static final Pattern WHERE_PATTERN = Pattern.compile(
            "(?i:\\bWHERE\\b)", DOTALL
    );

    private SqlParser() {
    }

    private static String getRawSql(@Nonnull StringTemplate template) {
        StringBuilder sb = new StringBuilder();
        List<String> fragments = template.fragments();
        List<Object> values = template.values();
        // Go through as many fragments as there are values.
        int size = Math.min(fragments.size(), values.size());
        for (int i = 0; i < size; i++) {
            sb.append(fragments.get(i));
            Object value = values.get(i);
            if (value instanceof Unsafe) {
                sb.append(((Unsafe) value).sql());
            }
        }
        // Append any leftover fragments if there are more fragments than values.
        for (int i = size; i < fragments.size(); i++) {
            sb.append(fragments.get(i));
        }
        return sb.toString();
    }

    /**
     * Determines the SQL mode for the specified {@code sql} string.
     *
     * @param sql the sql to inspect.
     * @return the SQL mode.
     */
    private static SqlMode getSqlMode(@Nonnull String sql) {
        return SQL_MODES.entrySet().stream()
                .filter(e -> e.getKey().matcher(sql).matches())
                .map(Entry::getValue)
                .findFirst()
                .orElse(UNDEFINED);
    }

    /**
     * Determines the SQL mode for the specified {@code template}.
     *
     * @param template the string template.
     * @return the SQL mode.
     */
    static SqlMode getSqlMode(@Nonnull StringTemplate template) {
        String rawSql = getRawSql(template);
        SqlMode mode = getSqlMode(rawSql);  // First try directly.
        if (mode != UNDEFINED) {
            return mode;
        }
        rawSql = removeComments(rawSql).stripLeading();
        mode = getSqlMode(rawSql);  // Remove potential leading comments and retry.
        if (mode != UNDEFINED) {
            return mode;
        }
        if (WITH_PATTERN.matcher(rawSql).matches()) {   // Remove potential WITH clause and retry.
            mode = getSqlMode(removeWithClause(rawSql));
        }
        return mode;
    }

    static boolean hasWhereClause(@Nonnull String sql) {
        return WHERE_PATTERN.matcher(clearStringLiterals(clearQuotedIdentifiers(removeComments(sql)))).find();
    }

    private static String removeWithClause(String sql) {
        sql = clearStringLiterals(clearQuotedIdentifiers(sql));
        assert sql.trim().toUpperCase().startsWith("WITH");
        int depth = 0; // Depth of nested parentheses.
        int startIndex = sql.indexOf('('); // Find the first opening parenthesis.
        // If there's no opening parenthesis after "WITH", return the original string.
        if (startIndex == -1) {
            return sql;
        }
        for (int i = startIndex; i < sql.length(); i++) {
            char ch = sql.charAt(i);
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
        // If depth never reaches 0, return the original string as it might be malformed or the logic above didn't
        // correctly parse it.
        return sql;
    }

    private static String replaceAll(@Nonnull String sql, @Nonnull Pattern pattern, @Nonnull String replacement) {
        return pattern.matcher(sql).replaceAll(replacement);
    }

    /**
     * Removes both single-line and multi-line comments from a SQL string.
     *
     * @param sql the original SQL string.
     * @return the SQL string with comments removed.
     */
    static String removeComments(@Nonnull String sql) {
        // Remove multi-line comments, then single-line comments.
        return replaceAll(
                replaceAll(sql, SQL_DIALECT.getMultiLineCommentPattern(), ""),
                    SQL_DIALECT.getSingleLineCommentPattern(), "");
    }

    /**
     * Replaces all double-quoted identifiers with an empty double-quoted placeholder ("").
     *
     * <p>This is roughly analogous to removing the content of double-quoted identifiers
     * in an ANSI SQL statement while leaving "" as a placeholder.</p>
     *
     * @param sql the original SQL string
     * @return the modified SQL string
     */
    static String clearQuotedIdentifiers(@Nonnull String sql) {
        return replaceAll(sql, SQL_DIALECT.getIdentifierPattern(), SQL_DIALECT.escape(""));
    }

    /**
     * Replaces all single-quoted string literals with an empty string literal ('').
     *
     * @param sql the original SQL string
     * @return the modified SQL string
     */
    static String clearStringLiterals(@Nonnull String sql) {
        return replaceAll(sql, SQL_DIALECT.getQuoteLiteralPattern(), "''");
    }
}
