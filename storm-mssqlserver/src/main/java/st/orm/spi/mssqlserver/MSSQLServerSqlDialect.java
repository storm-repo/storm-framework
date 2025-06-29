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
package st.orm.spi.mssqlserver;

import jakarta.annotation.Nonnull;
import st.orm.spi.DefaultSqlDialect;
import st.orm.template.SqlDialect;
import st.orm.template.SqlTemplateException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

public class MSSQLServerSqlDialect extends DefaultSqlDialect implements SqlDialect {

    /**
     * Returns the name of the SQL dialect.
     *
     * @return the name of the SQL dialect.
     * @since 1.2
     */
    @Override
    public String name() {
        return "MS SQL Server";
    }

    /**
     * Indicates whether the SQL dialect supports delete aliases.
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
        // SQL Server does not support multi-value tuple IN clauses.
        return false;
    }

    private static final Pattern MSSQL_IDENTIFIER = Pattern.compile("^[_A-Za-z#][_A-Za-z0-9]*$");

    /**
     * Returns the pattern for valid identifiers.
     *
     * @return the pattern for valid identifiers.
     * @since 1.2
     */
    @Override
    public Pattern getValidIdentifierPattern() {
        return MSSQL_IDENTIFIER;
    }

    private static final Set<String> MSSQL_RESERVED = Stream.concat(ANSI_KEYWORDS.stream(), Stream.of(
            "CLOSE", "COMPUTE", "CONTAINS", "CONTAINSTABLE", "FREETEXT", "FREETEXTTABLE",
            "LINENO", "MERGE", "PIVOT", "RAISERROR", "READTEXT", "REPLICATION", "ROWCOUNT",
            "ROWGUIDCOL", "SEQUENCE", "TRY_CONVERT", "TSEQUAL", "UNPIVOT", "UPDATETEXT",
            "WRITETEXT")).collect(toSet());

    /**
     * Indicates whether the given name is a keyword in this SQL dialect.
     *
     * @param name the name to check.
     * @return {@code true} if the name is a keyword, {@code false} otherwise.
     * @since 1.2
     */
    @Override
    public boolean isKeyword(@Nonnull String name) {
        return MSSQL_RESERVED.contains(name.toUpperCase());
    }

    /**
     * Escapes the given database identifier (e.g., table or column name) according to SQL Server.
     *
     * @param name the identifier to escape (must not be {@code null})
     * @return the escaped identifier
     */
    @Override
    public String escape(@Nonnull String name) {
        // Escape identifier for SQL Server by wrapping it in square brackets and doubling any closing brackets.
        return STR."[\{name.replace("]", "]]")}]";
    }

    /**
     * Regex for identifiers. Supports SQL Server's square bracket quoting as well as double quotes.
     */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(
            "\\[(?:]]|[^]])+]|\"(?:\"\"|[^\"])+\""
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
     * Returns a string for the given multi-value IN clause.
     *
     * @param values the (multi) values to use in the IN clause.
     * @param parameterFunction the function responsible for binding the parameters to the SQL template and returning
     *                          the string representation of the parameter, which is either a '?' placeholder or a
     *                          literal value.
     * @return the string that represents the multi value IN clause.
     * @throws SqlTemplateException if the values are incompatible.
     * @since 1.2
     */
    @Override
    public String multiValueIn(@Nonnull List<Map<String, Object>> values,
                               @Nonnull Function<Object, String> parameterFunction) throws SqlTemplateException {
        if (values.isEmpty()) {
            throw new SqlTemplateException("Multi-value IN clause requires at least one value.");
        }
        // For SQL Server, multi-value tuple IN clauses are not supported. Fall back to the default implementation.
        return super.multiValueIn(values, parameterFunction);
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
        return true;
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
        // For SQL Server, use the TOP clause. Note: TOP must appear immediately after SELECT.
        return STR."TOP \{limit}";
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
        // Note: An ORDER BY clause is required for OFFSET to work correctly.
        return STR."OFFSET \{offset} ROWS";
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
    public String limit(int offset, int limit) {
        // For SQL Server 2012 and later, use the OFFSET-FETCH clause.
        // Note: An ORDER BY clause is required for OFFSET-FETCH to work correctly.
        return STR."OFFSET \{offset} ROWS FETCH NEXT \{limit} ROWS ONLY";
    }

    /**
     * Returns {@code true} if the lock hint should be applied after the FROM clause.
     *
     * @return {@code true} if the lock hint should be applied after the FROM clause.
     * @since 1.2
     */
    @Override
    public boolean applyLockHintAfterFrom() {
        // In SQL Server, table hints are applied immediately after the table name in the FROM clause.
        return true;
    }

    /**
     * Returns the lock hint for a shared reading lock.
     *
     * @return the lock hint for a shared reading lock.
     * @since 1.2
     */
    @Override
    public String forShareLockHint() {
        return "WITH (HOLDLOCK)";
    }

    /**
     * Returns the lock hint for a write lock.
     *
     * @return the lock hint for a write lock.
     * @since 1.2
     */
    @Override
    public String forUpdateLockHint() {
        return "WITH (UPDLOCK)";
    }
}