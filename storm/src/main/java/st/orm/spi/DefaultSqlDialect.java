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
import st.orm.template.SqlTemplateException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;
import static st.orm.template.Operator.EQUALS;

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
     * Indicates whether the SQL dialect supports multi-value tuples in the IN clause.
     *
     * @return {@code true} if multi-value tuples are supported, {@code false} otherwise.
     * @since 1.2
     */
    @Override
    public boolean supportsMultiValueTuples() {
        return false;
    }

    // ANSI SQL valid identifier regex pattern.
    private static final Pattern SQL_IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    /**
     * Returns the pattern for valid identifiers.
     *
     * @return the pattern for valid identifiers.
     * @since 1.2
     */
    @Override
    public Pattern getValidIdentifierPattern() {
        return SQL_IDENTIFIER_PATTERN;
    }

    protected static final Set<String> ANSI_KEYWORDS = Set.of(
            "ABSOLUTE", "ACTION", "ADD", "ALL", "ALLOCATE", "ALTER", "AND", "ANY", "ARE",
            "ARRAY", "AS", "ASC", "ASSERTION", "AT", "AUTHORIZATION", "AVG", "BEGIN",
            "BETWEEN", "BIGINT", "BINARY", "BIT", "BLOB", "BOOLEAN", "BOTH", "BY", "CALL",
            "CALLED", "CASCADE", "CASE", "CAST", "CHAR", "CHARACTER", "CHECK", "CLOB",
            "CLOSE", "COALESCE", "COLLATE", "COLUMN", "COMMIT", "CONNECT", "CONNECTION",
            "CONSTRAINT", "CONTINUE", "CONVERT", "CORRESPONDING", "COUNT", "CREATE", "CROSS",
            "CURRENT", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURRENT_USER",
            "CURSOR", "DATE", "DAY", "DEALLOCATE", "DEC", "DECIMAL", "DECLARE", "DEFAULT",
            "DELETE", "DESC", "DESCRIBE", "DISCONNECT", "DISTINCT", "DOMAIN", "DOUBLE",
            "DROP", "ELSE", "END", "ESCAPE", "EXCEPT", "EXCEPTION", "EXEC", "EXECUTE",
            "EXISTS", "EXTERNAL", "EXTRACT", "FALSE", "FETCH", "FILTER", "FLOAT", "FOR",
            "FOREIGN", "FREE", "FROM", "FULL", "FUNCTION", "GET", "GLOBAL", "GRANT",
            "GROUP", "HAVING", "HOUR", "IDENTITY", "IMMEDIATE", "IN", "INDICATOR",
            "INNER", "INOUT", "INPUT", "INSENSITIVE", "INSERT", "INT", "INTEGER",
            "INTERSECT", "INTERVAL", "INTO", "IS", "ITERATE", "JOIN", "LANGUAGE", "LARGE",
            "LAST", "LEADING", "LEFT", "LIKE", "LOCAL", "LOCALTIME", "LOCALTIMESTAMP",
            "LOOP", "LOWER", "MATCH", "MEMBER", "MERGE", "METHOD", "MINUTE", "MOD",
            "MODIFIES", "MODULE", "MONTH", "MULTISET", "NATIONAL", "NATURAL", "NCHAR",
            "NCLOB", "NEW", "NO", "NONE", "NOT", "NULL", "NUMERIC", "OCTET_LENGTH", "OF",
            "OLD", "ON", "ONLY", "OPEN", "OR", "ORDER", "OUT", "OUTER", "OVER", "OVERLAPS",
            "PARAMETER", "PARTITION", "POSITION", "PRECISION", "PREPARE", "PRIMARY",
            "PROCEDURE", "RANGE", "READS", "REAL", "RECURSIVE", "REF", "REFERENCES",
            "REFERENCING", "RELEASE", "REPEAT", "RESIGNAL", "RESULT", "RETURN", "RETURNS",
            "REVOKE", "RIGHT", "ROLLBACK", "ROLLUP", "ROUTINE", "ROW", "ROWS", "SAVEPOINT",
            "SCROLL", "SEARCH", "SECOND", "SELECT", "SENSITIVE", "SESSION_USER", "SET",
            "SIGNAL", "SIMILAR", "SMALLINT", "SOME", "SPECIFIC", "SPECIFICTYPE", "SQL",
            "SQLEXCEPTION", "SQLSTATE", "SQLWARNING", "START", "STATIC", "SUBSTRING",
            "SUM", "SYSTEM", "TABLE", "TEMPORARY", "THEN", "TIME", "TIMESTAMP",
            "TIMEZONE_HOUR", "TIMEZONE_MINUTE", "TO", "TRAILING", "TRANSLATION", "TREAT",
            "TRIGGER", "TRIM", "TRUE", "UNDO", "UNION", "UNIQUE", "UNKNOWN", "UNNEST",
            "UNTIL", "UPDATE", "UPPER", "USAGE", "USER", "USING", "VALUE", "VALUES",
            "VAR_POP", "VAR_SAMP", "VARCHAR", "VARYING", "WHEN", "WHENEVER", "WHERE",
            "WHILE", "WINDOW", "WITH", "WITHIN"
    );

    /**
     * Indicates whether the given name is a keyword in this SQL dialect.
     *
     * @param name the name to check.
     * @return {@code true} if the name is a keyword, {@code false} otherwise.
     * @since 1.2
     */
    @Override
    public boolean isKeyword(@Nonnull String name) {
        return ANSI_KEYWORDS.contains(name.toUpperCase());
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

    private static final Pattern SINGLE_LINE_COMMENT_PATTERN = Pattern.compile("(--|#).*?(\\n|$)");

    /**
     * Returns the pattern for single line comments.
     *
     * @return the pattern for single line comments.
     * @since 1.2
     */
    @Override
    public Pattern getSingleLineCommentPattern() {
        return SINGLE_LINE_COMMENT_PATTERN;
    }

    private static final Pattern MULTI_LINE_COMMENT_PATTERN = Pattern.compile("(?s)/\\*.*?\\*/");

    /**
     * Returns the pattern for multi line comments.
     *
     * @return the pattern for multi line comments.
     * @since 1.2
     */
    @Override
    public Pattern getMultiLineCommentPattern() {
        return MULTI_LINE_COMMENT_PATTERN;
    }

    /**
     * Regex for double-quoted identifiers (including escaped quotes inside). In ANSI SQL, an embedded double quote is
     * escaped by doubling it (""). Look for sequences of "" or any non-quote character, all enclosed between double
     * quotes.
     */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\"(?:\"\"|[^\"])*\"");

    /**
     * Returns the pattern for identifiers.
     *
     * @return the pattern for identifiers.
     * @since 1.2
     */
    @Override
    public Pattern getIdentifierPattern() {
        return IDENTIFIER_PATTERN;
    }

    /**
     * Regex for single-quoted string literals, handling double single quotes.
     */
    private static final Pattern QUOTE_LITERAL_PATTERN = Pattern.compile("'(?:''|[^'])*'");

    /**
     * Returns the pattern for string literals.
     *
     * @return the pattern for string literals.
     * @since 1.2
     */
    @Override
    public Pattern getQuoteLiteralPattern() {
        return QUOTE_LITERAL_PATTERN;
    }

    /**
     * Returns a string for the given column name.
     *
     * @param values the (multi) values to use in the IN clause.
     * @param parameterConsumer the consumer for the parameters.
     * @return the string that represents the multi value IN clause.
     * @throws SqlTemplateException if the values are incompatible.
     * @since 1.2
     */
    @Override
    public String multiValueIn(@Nonnull List<Map<String, Object>> values,
                               @Nonnull Consumer<Object> parameterConsumer) throws SqlTemplateException {
        List<String> args = new ArrayList<>();
        for (var valueMap : values) {
            args.add(STR."(\{valueMap.keySet().stream()
                    .map(k -> EQUALS.format(k, 1))  // We can safely use EQUALS here.
                    .collect(joining(" AND "))})");
            valueMap.values().forEach(parameterConsumer);
            args.add(" OR ");
        }
        if (!args.isEmpty()) {
            args.removeLast();
        }
        return String.join("", args);
    }

    /**
     * Returns {@code true} if the limit should be applied after the SELECT clause, {@code false} to apply the limit at
     * the end of the query.
     *
     * @return {@code true} if the limit should be applied after the SELECT clause, {@code false} to apply the limit at
     * the end of the query.
     * @since 1.2
     */
    @Override
    public boolean applyLimitAfterSelect() {
        return false;
    }

    /**
     * Returns a string template for the given limit.
     *
     * @param limit the maximum number of records to return.
     * @return a string template for the given limit.
     * @since 1.2
     */
    @Override
    public String limit(int limit) {
        // Taking the most basic approach that is supported by most database in test (containers).
        // For production use, ensure the right dialect is used.
        return STR."LIMIT \{limit}";
    }

    /**
     * Returns a string template for the given offset.
     *
     * @param offset the offset.
     * @return a string template for the given offset.
     * @since 1.2
     */
    @Override
    public String offset(int offset) {
        return STR."OFFSET \{offset}";
    }

    /**
     * Returns a string template for the given limit and offset.
     *
     * @param offset the offset.
     * @param limit the maximum number of records to return.
     * @return a string template for the given limit and offset.
     * @since 1.2
     */
    @Override
    public String limit(int limit, int offset) {
        // Taking the most basic approach that is supported by most database in test (containers).
        // For production use, ensure the right dialect is used.
        return STR."LIMIT \{limit} OFFSET \{offset}";
    }

    /**
     * Returns {@code true} if the lock hint should be applied after the FROM clause, {@code false} to apply the lock
     * hint at the end of the query.
     *
     * @return {@code true} if the lock hint should be applied after the FROM clause, {@code false} to apply the lock
     * hint at the end of the query.
     * @since 1.2
     */
    @Override
    public boolean applyLockHintAfterFrom() {
        return false;
    }

    /**
     * Returns the lock hint for a shared reading lock.
     *
     * @return the lock hint for a shared reading lock.
     * @since 1.2
     */
    @Override
    public String forShareLockHint() {
        return "FOR SHARE";
    }

    /**
     * Returns the lock hint for a write lock.
     *
     * @return the lock hint for a write lock.
     * @since 1.2
     */
    @Override
    public String forUpdateLockHint() {
        return "FOR UPDATE";
    }
}
