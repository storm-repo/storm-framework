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
package st.orm.core.template;

import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Represents a specific SQL dialect with methods to determine feature support and handle identifier escaping.
 *
 * @since 1.1
 */
public interface SqlDialect {

    /**
     * Returns the name of the SQL dialect.
     *
     * @return the name of the SQL dialect.
     * @since 1.2
     */
    String name();

    /**
     * Indicates whether the SQL dialect supports delete aliases.
     *
     * <p>Delete aliases allow delete statements to use table aliases in joins,  making it easier to filter rows based
     * on related data.</p>
     *
     * @return {@code true} if delete aliases are supported, {@code false} otherwise.
     */
    boolean supportsDeleteAlias();

    /**
     * Indicates whether the SQL dialect supports multi-value tuples in the IN clause.
     *
     * @return {@code true} if multi-value tuples are supported, {@code false} otherwise.
     * @since 1.2
     */
    boolean supportsMultiValueTuples();

    /**
     * Returns the pattern for valid identifiers.
     *
     * @return the pattern for valid identifiers.
     * @since 1.2
     */
    Pattern getValidIdentifierPattern();

    /**
     * Indicates whether the given name is a keyword in this SQL dialect.
     *
     * @param name the name to check.
     * @return {@code true} if the name is a keyword, {@code false} otherwise.
     * @since 1.2
     */
    boolean isKeyword(@Nonnull String name);

    /**
     * Escapes the given database identifier (e.g., table or column name) according to this SQL dialect.
     *
     * @param name the identifier to escape (must not be {@code null})
     * @return the escaped identifier
     */
    String escape(@Nonnull String name);

    /**
     * Returns a safe identifier for the given name, possibly escaping it if it is a keyword or contains invalid
     * characters.
     *
     * @param name the name to check.
     * @return a safe identifier for the given name.
     * @since 1.2
     */
    default String getSafeIdentifier(String name) {
        if (isKeyword(name) || !getValidIdentifierPattern().matcher(name).matches()) {
            return escape(name);
        }
        return name;
    }

    /**
     * Returns the pattern for single line comments.
     *
     * @return the pattern for single line comments.
     * @since 1.2
     */
    Pattern getSingleLineCommentPattern();

    /**
     * Returns the pattern for multi line comments.
     *
     * @return the pattern for multi line comments.
     * @since 1.2
     */
    Pattern getMultiLineCommentPattern();

    /**
     * Returns the pattern for identifiers.
     *
     * @return the pattern for identifiers.
     * @since 1.2
     */
    Pattern getIdentifierPattern();

    /**
     * Returns the pattern for string literals.
     *
     * @return the pattern for string literals.
     * @since 1.2
     */
    Pattern getQuoteLiteralPattern();

    /**
     * Returns a string for the given column name.
     *
     * @param values the (multi) values to use in the IN clause.
     * @param parameterFunction the function responsible for binding the parameters to the SQL template and returning
     *                          the string representation of the parameter, which is either a '?' placeholder or a
     *                          literal value.
     * @return the string that represents the multi value IN clause.
     * @throws SqlTemplateException if the values are incompatible.
     * @since 1.2
     */
    String multiValueIn(@Nonnull List<Map<String, Object>> values,
                        @Nonnull Function<Object, String> parameterFunction) throws SqlTemplateException;

    /**
     * Returns {@code true} if the limit should be applied after the SELECT clause, {@code false} to apply the limit at
     * the end of the query.
     *
     * @return {@code true} if the limit should be applied after the SELECT clause, {@code false} to apply the limit at
     * the end of the query.
     * @since 1.2
     */
    boolean applyLimitAfterSelect();

    /**
     * Returns a string template for the given limit.
     *
     * @param limit the maximum number of records to return.
     * @return a string template for the given limit.
     * @since 1.2
     */
    String limit(int limit);

    /**
     * Returns a string template for the given offset.
     *
     * @param offset the offset.
     * @return a string template for the given offset.
     * @since 1.2
     */
    String offset(int offset);

    /**
     * Returns a string template for the given limit and offset.
     *
     * @param offset the offset.
     * @param limit the maximum number of records to return.
     * @return a string template for the given limit and offset.
     * @since 1.2
     */
    String limit(int offset, int limit);

    /**
     * Returns {@code true} if the lock hint should be applied after the FROM clause, {@code false} to apply the lock
     * hint at the end of the query.
     *
     * @return {@code true} if the lock hint should be applied after the FROM clause, {@code false} to apply the lock
     * hint at the end of the query.
     * @since 1.2
     */
    boolean applyLockHintAfterFrom();

    /**
     * Returns the lock hint for a shared reading lock.
     *
     * @return the lock hint for a shared reading lock.
     * @since 1.2
     */
    String forShareLockHint();

    /**
     * Returns the lock hint for a write lock.
     *
     * @return the lock hint for a write lock.
     * @since 1.2
     */
    String forUpdateLockHint();

    /**
     * Returns the SQL statement for getting the next value of the given sequence.
     *
     * @param sequenceName the name of the sequence.
     * @return the SQL statement for getting the next value of the given sequence.
     * @since 1.6
     */
    String sequenceNextVal(String sequenceName);
}