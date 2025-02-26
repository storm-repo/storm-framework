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
package st.orm.spi.postgresql;

import jakarta.annotation.Nonnull;
import st.orm.spi.DefaultSqlDialect;
import st.orm.spi.SqlDialect;
import st.orm.template.SqlTemplateException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import static java.util.stream.Collectors.joining;

public class PostgresqlSqlDialect extends DefaultSqlDialect implements SqlDialect {

    /**
     * PostgreSQL does not support aliasing the target table in DELETE statements.
     */
    @Override
    public boolean supportsDeleteAlias() {
        return false;
    }

    /**
     * PostgreSQL supports multi-value tuples in the IN clause.
     */
    @Override
    public boolean supportsMultiValueTuples() {
        return true;
    }

    /**
     * Escapes the given database identifier using double quotes.
     *
     * @param name the identifier to escape (must not be {@code null})
     * @return the escaped identifier
     */
    @Override
    public String escape(@Nonnull String name) {
        return STR."\"\{name}\"";
    }

    /**
     * Regex for double-quoted identifiers (handling doubled double quotes as escapes).
     */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(
            "\"(?:\"\"|[^\"])*\""
    );

    /**
     * Returns the pattern for identifiers.
     *
     * @return the pattern for identifiers.
     */
    @Override
    public Pattern getIdentifierPattern() {
        return IDENTIFIER_PATTERN;
    }

    /**
     * Regex for single-quoted string literals, handling both doubled single quotes and backslash escapes.
     */
    private static final Pattern QUOTE_LITERAL_PATTERN = Pattern.compile(
            "'(?:''|\\.|[^'])*'"
    );

    /**
     * Returns the pattern for string literals.
     *
     * @return the pattern for string literals.
     */
    @Override
    public Pattern getQuoteLiteralPattern() {
        return QUOTE_LITERAL_PATTERN;
    }

    /**
     * Builds a multi-value IN clause for PostgreSQL.
     *
     * @param values the (multi) values to use in the IN clause.
     * @param parameterConsumer the consumer for the parameters.
     * @return the string representing the multi-value IN clause.
     * @throws SqlTemplateException if the values are incompatible.
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
        StringBuilder in = new StringBuilder("(")
                .append(String.join(", ", columns))
                .append(") IN ((");
        for (Map<String, Object> row : values) {
            if (row.size() != columns.size()) {
                throw new SqlTemplateException("Multi-value IN clause requires all entries to have the same number of columns.");
            }
            if (!columns.containsAll(row.keySet())) {
                throw new SqlTemplateException("Multi-value IN clause requires all entries to have the same columns.");
            }
            in.append(columns.stream()
                    .map(row::get)
                    .map(value -> {
                        parameterConsumer.accept(value);
                        return "?";
                    })
                    .collect(joining(", ")))
              .append("), (");
        }
        in.setLength(in.length() - 3);  // Remove the trailing ", (".
        in.append(")");
        return in.toString();
    }

    /**
     * Returns a PostgreSQL limit clause.
     *
     * @param limit the maximum number of records to return.
     * @return the limit clause.
     */
    @Override
    public String limit(int limit) {
        return STR."LIMIT \{limit}";
    }

    /**
     * Returns a PostgreSQL offset clause.
     *
     * @param offset the offset.
     * @return the offset clause.
     */
    @Override
    public String offset(int offset) {
        return STR."OFFSET \{offset}";
    }

    /**
     * Returns a PostgreSQL limit clause with offset.
     *
     * @param offset the offset.
     * @param limit the maximum number of records to return.
     * @return the limit clause with offset.
     */
    @Override
    public String limit(int offset, int limit) {
        return STR."OFFSET \{offset} LIMIT \{limit}";
    }

    /**
     * Returns the lock hint for a shared reading lock.
     *
     * @return the lock hint for a shared reading lock.
     */
    @Override
    public String forShareLockHint() {
        // We may add configuration flags to use the old PostgreSQL need FOR SHARE instead.
        return "FOR KEY SHARE";
    }

    /**
     * Returns the lock hint for a write lock.
     *
     * @return the lock hint for a write lock.
     */
    @Override
    public String forUpdateLockHint() {
        return "FOR UPDATE";
    }
}