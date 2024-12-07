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
package st.orm.template;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Represents a comparison operator in a SQL query.
 */
@SuppressWarnings({"SwitchStatementWithTooFewBranches", "unused"})
public interface Operator {
    Operator IN = (column, size) -> switch (size) {
        case 0 -> "1 <> 1";
        default -> STR."\{column} IN (\{"?, ".repeat(size - 1)}?)";
    };
    Operator NOT_IN = (column, size) -> switch (size) {
        case 0 -> "1 = 1";
        default -> STR."\{column} NOT IN (\{"?, ".repeat(size - 1)}?)";
    };
    Operator EQUALS = (column, size) -> format("Equals", 1, size, STR."\{column} = ?");
    Operator NOT_EQUALS = (column, size) -> format("Not equals", 1, size, STR."\{column} <> ?");
    Operator LIKE = (column, size) -> format("Like", 1, size, STR."\{column} LIKE ?");
    Operator NOT_LIKE = (column, size) -> format("Not like", 1, size, STR."\{column} NOT LIKE ?");
    Operator GREATER_THAN = (column, size) -> format("Greater than", 1 ,size, STR."\{column} > ?");
    Operator GREATER_THAN_OR_EQUAL = (column, size) -> format("Greater than or equal", 1, size, STR."\{column} >= ?");
    Operator LESS_THAN = (column, size) -> format("Less than", 1, size, STR."\{column} < ?");
    Operator LESS_THAN_OR_EQUAL= (column, size) -> format("Less than or equal", 1, size, STR."\{column} <= ?");
    Operator BETWEEN = (column, size) -> format("Between", 2, size, STR."\{column} BETWEEN ? AND ?");
    Operator IS_TRUE = (column, size) -> format("Is true", 0, size, STR."\{column} IS TRUE");
    Operator IS_FALSE = (column, size) -> format("Is false", 0, size, STR."\{column} IS FALSE");
    Operator IS_NULL = (column, size) -> format("Is null", 0, size, STR."\{column} IS NULL");
    Operator IS_NOT_NULL = (column, size) -> format("Is not null", 0, size, STR."\{column} IS NOT NULL");

    /**
     * Formats the operator with bind variables matching the specified size.
     *
     * @param column the column to compare.
     * @param size the number of bind variables to format.
     * @return the formatted operator.
     * @throws IllegalArgumentException if the specified size is not supported by the operator.
     */
    String format(@Nullable String column, int size);

    private static String format(@Nullable String name, int requiredSize, int actualSize, @Nonnull String operator) {
        if (name == null) {
            throw new IllegalArgumentException("Column name cannot be null.");
        }
        if (requiredSize != actualSize) {
            throw new IllegalArgumentException(STR."\{name} operator requires \{requiredSize} value(s). Found \{actualSize} value(s).");
        }
        return operator;
    }
}