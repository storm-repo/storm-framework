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
 * distributed under the "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package st.orm.spi.oracle;

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

public class OracleSqlDialect extends DefaultSqlDialect implements SqlDialect {

    @Override
    public boolean supportsDeleteAlias() {
        // Oracle doesn't allow table aliases in DELETE.
        return false;
    }

    @Override
    public boolean supportsMultiValueTuples() {
        // Oracle supports multi-column IN (col1, col2) IN ((v1_1, v1_2), ...).
        return true;
    }

    @Override
    public String escape(@Nonnull String name) {
        // Oracle uses double quotes to escape identifiers.
        return STR."\"\{name.replace("\"", "\"\"")}\"";
    }

    /**
     * Regex for double-quoted identifiers in Oracle (embedded quotes are doubled).
     */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(
        "\"(?:\"\"|[^\"])*\""
    );

    @Override
    public Pattern getIdentifierPattern() {
        return IDENTIFIER_PATTERN;
    }

    /**
     * Regex for single-quoted string literals in Oracle (escaped by doubling the single quote).
     */
    private static final Pattern QUOTE_LITERAL_PATTERN = Pattern.compile("'(?:''|\\.|[^'])*'");

    @Override
    public Pattern getQuoteLiteralPattern() {
        return QUOTE_LITERAL_PATTERN;
    }

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
     * For Oracle 12c+ you can use:
     *   SELECT ... FETCH FIRST n ROWS ONLY
     */
    @Override
    public String limit(int limit) {
        return STR."FETCH FIRST \{limit} ROWS ONLY";
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
        return STR."OFFSET \{offset} ROWS";
    }

    /**
     * Oracle 12c+ offset syntax is:
     *   SELECT ...
     *   OFFSET {offset} ROWS FETCH NEXT {limit} ROWS ONLY
     */
    @Override
    public String limit(int offset, int limit) {
        return STR."OFFSET \{offset} ROWS FETCH NEXT \{limit} ROWS ONLY";
    }

    /**
     * Oracle does not support a shared lock hint for SELECT statements. Return an empty string.
     *
     * @return an empty string.
     */
    @Override
    public String forShareLockHint() {
        // We may add configuration flags to choose between an empty String and a PersistenceException.
        return "";
    }

    /**
     * Returns the lock hint for a write lock in Oracle.
     * Oracle supports "FOR UPDATE" to lock rows for update.
     *
     * @return the lock hint for a write lock.
     */
    @Override
    public String forUpdateLockHint() {
        return "FOR UPDATE";
    }
}