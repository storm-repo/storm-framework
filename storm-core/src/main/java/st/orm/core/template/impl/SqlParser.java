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

import static java.util.regex.Pattern.DOTALL;
import static st.orm.core.template.SqlOperation.DELETE;
import static st.orm.core.template.SqlOperation.INSERT;
import static st.orm.core.template.SqlOperation.SELECT;
import static st.orm.core.template.SqlOperation.UNDEFINED;
import static st.orm.core.template.SqlOperation.UPDATE;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import st.orm.core.template.SqlDialect;
import st.orm.core.template.SqlOperation;
import st.orm.core.template.TemplateString;
import st.orm.core.template.impl.Elements.Unsafe;

/**
 * SQL parser for basic SQL processing.
 */
final class SqlParser {

    private static final Pattern WITH_PATTERN = Pattern.compile("^(?i:WITH)\\b.*", DOTALL);
    private static final Map<Pattern, SqlOperation> SQL_MODES = Map.of(
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

    private static String getRawSql(@Nonnull TemplateString template) {
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
    private static SqlOperation getSqlOperation(@Nonnull String sql) {
        return SQL_MODES.entrySet().stream()
                .filter(e -> e.getKey().matcher(sql).matches())
                .map(Entry::getValue)
                .findFirst()
                .orElse(UNDEFINED);
    }

    /**
     * Determines the SQL operation for the specified {@code template}.
     *
     * @param template the string template.
     * @return the SQL operation.
     */
    static SqlOperation getSqlOperation(@Nonnull TemplateString template, @Nonnull SqlDialect dialect) {
        String rawSql = getRawSql(template);
        SqlOperation operation = getSqlOperation(rawSql);  // First try directly.
        if (operation != UNDEFINED) {
            return operation;
        }
        rawSql = removeComments(rawSql, dialect).stripLeading();
        operation = getSqlOperation(rawSql);  // Remove potential leading comments and retry.
        if (operation != UNDEFINED) {
            return operation;
        }
        if (WITH_PATTERN.matcher(rawSql).matches()) {   // Remove potential WITH clause and retry.
            operation = getSqlOperation(removeWithClause(rawSql, dialect));
        }
        return operation;
    }

    static boolean hasWhereClause(@Nonnull String sql, @Nonnull SqlDialect dialect) {
        return WHERE_PATTERN.matcher(clearStringLiterals(clearQuotedIdentifiers(removeComments(sql, dialect), dialect), dialect)).find();
    }

    private static String removeWithClause(@Nonnull String sql, @Nonnull SqlDialect dialect) {
        sql = clearStringLiterals(clearQuotedIdentifiers(sql, dialect), dialect);
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
     * @param dialect the SQL dialect.
     * @return the SQL string with comments removed.
     */
    static String removeComments(@Nonnull String sql, @Nonnull SqlDialect dialect) {
        // Remove multi-line comments, then single-line comments.
        return replaceAll(
                replaceAll(sql, dialect.getMultiLineCommentPattern(), ""),
                    dialect.getSingleLineCommentPattern(), "");
    }

    /**
     * Replaces all double-quoted identifiers with an empty double-quoted placeholder ("").
     *
     * <p>This is roughly analogous to removing the content of double-quoted identifiers
     * in an ANSI SQL statement while leaving "" as a placeholder.</p>
     *
     * @param sql the original SQL string.
     * @param dialect the SQL dialect.
     * @return the modified SQL string.
     */
    static String clearQuotedIdentifiers(@Nonnull String sql, @Nonnull SqlDialect dialect) {
        return replaceAll(sql, dialect.getIdentifierPattern(), dialect.escape(""));
    }

    /**
     * Replaces all single-quoted string literals with an empty string literal ('').
     *
     * @param sql the original SQL string.
     * @param dialect the SQL dialect.
     * @return the modified SQL string.
     */
    static String clearStringLiterals(@Nonnull String sql, @Nonnull SqlDialect dialect) {
        return replaceAll(sql, dialect.getQuoteLiteralPattern(), "''");
    }
}
