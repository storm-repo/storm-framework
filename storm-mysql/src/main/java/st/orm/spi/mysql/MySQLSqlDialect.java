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
package st.orm.spi.mysql;

import jakarta.annotation.Nonnull;
import st.orm.spi.DefaultSqlDialect;
import st.orm.template.SqlDialect;
import st.orm.template.SqlTemplateException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

public class MySQLSqlDialect extends DefaultSqlDialect implements SqlDialect {

    /**
     * Returns the name of the SQL dialect.
     *
     * @return the name of the SQL dialect.
     * @since 1.2
     */
    @Override
    public String name() {
        return "MySQL";
    }

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
        return true;
    }

    /**
     * Indicates whether the SQL dialect supports multi-value tuples in the IN clause.
     *
     * @return {@code true} if multi-value tuples are supported, {@code false} otherwise.
     * @since 1.2
     */
    @Override
    public boolean supportsMultiValueTuples() {
        // Note that tuple IN is only supported as of MySQL 8.0.19. We will account for this in the future.
        return true;
    }

    private static final Pattern MYSQL_IDENTIFIER = Pattern.compile("^[_A-Za-z][_A-Za-z0-9]*$");

    /**
     * Returns the pattern for valid identifiers.
     *
     * @return the pattern for valid identifiers.
     * @since 1.2
     */
    @Override
    public Pattern getValidIdentifierPattern() {
        return MYSQL_IDENTIFIER;
    }

    private static final Set<String> MYSQL_KEYWORDS = Stream.concat(ANSI_KEYWORDS.stream(), Stream.of(
            "ACCESSIBLE", "CHANGE", "DATABASE", "DAY_HOUR", "DAY_MINUTE", "DAY_SECOND",
            "DELAYED", "DISTINCTROW", "DIV", "ENCLOSED", "ESCAPED", "EXPLAIN", "FULLTEXT",
            "HIGH_PRIORITY", "HOUR_MICROSECOND", "HOUR_SECOND", "IGNORE", "INFILE",
            "INT1", "INT2", "INT3", "INT4", "INT8", "KEYS", "LINES", "LOAD", "LOW_PRIORITY",
            "MATCH", "MEDIUMINT", "MIDDLEINT", "MODIFIES", "OPTION", "OPTIONALLY",
            "OUTFILE", "PARTITION", "PRIVILEGES", "REQUIRE", "RESIGNAL", "SCHEMAS", "SHOW",
            "SQL_BIG_RESULT", "SQL_CALC_FOUND_ROWS", "SQL_SMALL_RESULT", "STRAIGHT_JOIN",
            "TERMINATED", "TINYINT", "UNSIGNED", "UTC_DATE", "UTC_TIME", "UTC_TIMESTAMP",
            "XOR", "ZEROFILL")).collect(toSet());

    /**
     * Indicates whether the given name is a keyword in this SQL dialect.
     *
     * @param name the name to check.
     * @return {@code true} if the name is a keyword, {@code false} otherwise.
     * @since 1.2
     */
    @Override
    public boolean isKeyword(@Nonnull String name) {
        return MYSQL_KEYWORDS.contains(name.toUpperCase());
    }

    /**
     * Escapes the given database identifier (e.g., table or column name) according to this SQL dialect.
     *
     * @param name the identifier to escape (must not be {@code null})
     * @return the escaped identifier
     */
    @Override
    public String escape(@Nonnull String name) {
        return STR."`\{name.replace("`", "``")}`";
    }

    /**
     * Regex for double-quoted identifiers (including escaped quotes inside). In ANSI SQL, an embedded double quote is
     * escaped by doubling it (""). Look for sequences of "" or any non-quote character, all enclosed between double
     * quotes.
     */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(
            "\"(?:\"\"|[^\"])*\""   // Either: double‐quoted identifier (with "" as escape)
            + "|" +
            "`(?:``|[^`])*`"    // Or: backtick‐quoted identifier (with `` as escape)
    );

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
     * Regex for single-quoted string literals, handling both double single quotes and backslash escapes.
     */
    private static final Pattern QUOTE_LITERAL_PATTERN = Pattern.compile("'(?:''|\\.|[^'])*'");

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
        if (values.isEmpty()) {
            throw new SqlTemplateException("Multi-value IN clause requires at least one value.");
        }
        Set<String> columns = new LinkedHashSet<>(values.getFirst().keySet());
        if (columns.size() < 2) {
            throw new SqlTemplateException("Multi-value IN clause requires at least two columns.");
        }
        if (!supportsMultiValueTuples()) {
            return super.multiValueIn(values, parameterConsumer);
        }
        StringBuilder in = new StringBuilder("(").append(String.join(", ", columns)).append(") IN ((");
        for (var row : values) {
            if (row.size() != columns.size()) {
                throw new SqlTemplateException("Multi-value IN clause requires all entries to have the same number of columns.");
            }
            if (!columns.containsAll(row.keySet())) {
                throw new SqlTemplateException("Multi-value IN clause requires all entries to have the same columns.");
            }
            in.append(columns.stream().map(row::get).map(value -> {
                parameterConsumer.accept(value);
                return "?";
            }).collect(joining(", "))).append("), (");
        }
        in.setLength(in.length() - 3);  // Remove the last ", (".
        in.append(")");
        return in.toString();
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
        return STR."LIMIT 18446744073709551615 OFFSET \{offset}";
    }

    /**
     * Returns a string template for the given limit and offset.
     *
     * @param limit the maximum number of records to return.
     * @param offset the offset.
     * @return a string template for the given limit and offset.
     * @since 1.2
     */
    @Override
    public String limit(int offset, int limit) {
        // Taking the most basic approach that is supported by most database in test (containers).
        // For production use, ensure the right dialect is used.
        return STR."LIMIT \{limit} OFFSET \{offset}";
    }
}
