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
package st.orm;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Represents a comparison operator in a SQL query.
 */
@SuppressWarnings({"SwitchStatementWithTooFewBranches", "unused"})
public interface Operator {

    /**
     * The {@code IN} operator.
     */
    Operator IN = (column, placeholders) -> switch (placeholders.length) {
        case 0 -> "1 <> 1";
        default -> "%s IN (%s)".formatted(column, String.join(", ", placeholders));
    };

    /**
     * The {@code NOT IN} operator.
     */
    Operator NOT_IN = (column, placeholders) -> switch (placeholders.length) {
        case 0 -> "1 = 1";
        default -> "%s NOT IN (%s)".formatted(column, String.join(", ", placeholders));
    };

    /**
     * The {@code EXISTS} operator.
     */
    Operator EQUALS = (column, placeholders) -> format("Equals", 1, placeholders.length, "%s = %s".formatted(column, get(placeholders)));

    /**
     * The {@code NOT EXISTS} operator.
     */
    Operator NOT_EQUALS = (column, placeholders) -> format("Not equals", 1, placeholders.length, "%s <> %s".formatted(column, Operator.get(placeholders)));

    /**
     * The {@code LIKE} operator.
     */
    Operator LIKE = (column, placeholders) -> format("Like", 1, placeholders.length, "%s LIKE %s".formatted(column, get(placeholders)));

    /**
     * The {@code NOT LIKE} operator.
     */
    Operator NOT_LIKE = (column, placeholders) -> format("Not like", 1, placeholders.length, "%s NOT LIKE %s".formatted(column, get(placeholders)));

    /**
     * The {@code >} operator.
     */
    Operator GREATER_THAN = (column, placeholders) -> format("Greater than", 1 , placeholders.length, "%s > %s".formatted(column, get(placeholders)));

    /**
     * The {@code >=} operator.
     */
    Operator GREATER_THAN_OR_EQUAL = (column, placeholders) -> format("Greater than or equal", 1, placeholders.length, "%s >= %s".formatted(column, get(placeholders)));

    /**
     * The {@code <} operator.
     */
    Operator LESS_THAN = (column, placeholders) -> format("Less than", 1, placeholders.length, "%s < %s".formatted(column, get(placeholders)));

    /**
     * The {@code <=} operator.
     */
    Operator LESS_THAN_OR_EQUAL= (column, placeholders) -> format("Less than or equal", 1, placeholders.length, "%s <= %s".formatted(column, get(placeholders)));

    /**
     * The {@code BETWEEN} operator.
     */
    Operator BETWEEN = (column, placeholders) -> format("Between", 2, placeholders.length, "%s BETWEEN %s AND %s".formatted(column, get(placeholders), get(1, placeholders)));

    /**
     * The {@code IS TRUE} operator.
     */
    Operator IS_TRUE = (column, placeholders) -> format("Is true", 0, placeholders.length, "%s IS TRUE".formatted(column));

    /**
     * The {@code IS FALSE} operator.
     */
    Operator IS_FALSE = (column, placeholders) -> format("Is false", 0, placeholders.length, "%s IS FALSE".formatted(column));

    /**
     * The {@code IS NULL} operator.
     */
    Operator IS_NULL = (column, placeholders) -> format("Is null", 0, placeholders.length, "%s IS NULL".formatted(column));

    /**
     * The {@code IS NOT NULL} operator.
     */
    Operator IS_NOT_NULL = (column, placeholders) -> format("Is not null", 0, placeholders.length, "%s IS NOT NULL".formatted(column));

    /**
     * Formats the operator with bind variables matching the specified size.
     *
     * @param column the column to compare.
     * @param placeholders the placeholders to use in the template.
     * @return the formatted operator.
     * @throws IllegalArgumentException if the specified size is not supported by the operator.
     */
    String format(@Nullable String column, String... placeholders);

    private static String format(@Nullable String name, int requiredSize, int actualSize, @Nonnull String operator) {
        if (name == null) {
            throw new IllegalArgumentException("Column name cannot be null.");
        }
        if (requiredSize != actualSize) {
            throw new IllegalArgumentException("%s operator requires %s value(s). Found %s value(s).".formatted(name, requiredSize, actualSize));
        }
        return operator;
    }

    private static String get(String... placeholders) {
        return get(0, placeholders);
    }

    private static String get(int index, String... placeholders) {
        if (index < 0 || index >= placeholders.length) {
            throw new IllegalArgumentException("Unexpected number of placeholders. Expected at least %s but found %d.".formatted(index + 1, placeholders.length));
        }
        return placeholders[index];
    }
}