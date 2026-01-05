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
package st.orm;

import jakarta.annotation.Nonnull;

/**
 * Default implementation of the {@link JoinType} interface used to represent the default join types.
 */
public enum DefaultJoinType implements JoinType {
    INNER("INNER JOIN", true, false),
    CROSS("CROSS JOIN", false, false),
    LEFT("LEFT JOIN", true, true),
    RIGHT("RIGHT JOIN", true, true);

    private final String sql;
    private final boolean on;
    private final boolean outer;

    DefaultJoinType(@Nonnull String sql, boolean on, boolean outer) {
        this.sql = sql;
        this.on = on;
        this.outer = outer;
    }

    /**
     * The SQL representation of the join type. For example, "INNER JOIN" or "LEFT JOIN".
     *
     * @return the SQL representation of the join type.
     */
    @Override
    public String sql() {
        return sql;
    }

    /**
     * Whether the join type will be accompanied by an ON clause.
     *
     * <p><strong>Note:</strong> The {@code JoinType} implementation is not responsible for generating the ON clause.
     * This method is used to determine whether the ON clause should be included in the generated SQL.</p>
     *
     * @return {@code true} if the join type will be accompanied by an ON clause, {@code false} otherwise.
     */
    @Override
    public boolean hasOnClause() {
        return on;
    }

    /**
     * Whether the join type is an outer join.
     *
     * <p>Outer joins will be treated differently when generating the SQL. Outer joins will be placed at the end of the
     * join list to prevent undesired side effects.</p>
     *
     * @return {@code true} if the join type is an outer join, {@code false} otherwise.
     */
    @Override
    public boolean isOuter() {
        return outer;
    }
}
