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
package st.orm.spi.mssqlserver;

import jakarta.annotation.Nonnull;
import st.orm.spi.DefaultSqlDialect;
import st.orm.spi.SqlDialect;
import st.orm.template.SqlTemplateException;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class MSSQLServerSqlDialect extends DefaultSqlDialect implements SqlDialect {

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
        // For SQL Server, multi-value tuple IN clauses are not supported. Fall back to the default implementation.
        return super.multiValueIn(values, parameterConsumer);
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