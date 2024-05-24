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

import st.orm.template.impl.SqlTemplateImpl.DefaultJoinType;

/**
 * Represents a join type in a SQL query.
 *
 * There are four default join types that can be accessed via the static methods. Alternatively, custom join types can
 * be created by implementing this interface.
 */
public interface JoinType {

    /** The default inner join type. */
    static JoinType inner() { return DefaultJoinType.INNER; }

    /** The default cross join type. */
    static JoinType cross() { return DefaultJoinType.CROSS; }

    /** The default left join type. */
    static JoinType left() { return DefaultJoinType.LEFT; }

    /** The default right join type. */
    static JoinType right() { return DefaultJoinType.RIGHT; }

    /**
     * The SQL representation of the join type. For example, "INNER JOIN" or "LEFT JOIN".
     *
     * @return the SQL representation of the join type.
     */
    String sql();

    /**
     * Whether the join type will be accompanied by an ON clause.
     *
     * Note that {@code JoinType} implementation is not responsible for generating the ON clause. This method is used
     * to determine whether the ON clause should be included in the generated SQL.
     *
     * @return {@code true} if the join type will be accompanied by an ON clause, {@code false} otherwise.
     */
    default boolean hasOnClause() {
        return true;
    }

    /**
     * Whether the join type is an outer join.
     *
     * Outer joins will be treated differently when generating the SQL. Outer joins will be placed at the end of the
     * join list to prevent undesired side effects.
     *
     * @return {@code true} if the join type is an outer join, {@code false} otherwise.
     */
    default boolean isOuter() {
        return false;
    }
}
