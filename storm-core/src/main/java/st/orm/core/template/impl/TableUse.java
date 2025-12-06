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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.Data;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utility class to keep track of the tables that are used in a query to optimize the auto join generation.
 *
 * @since 1.1
 */
class TableUse {
    private final Set<TableAlias> referencedTables;
    private final Map<TableAlias, Set<TableAlias>> precedingTables;

    private record TableAlias(Class<?> table, String alias) {}

    TableUse() {
        this.referencedTables = new HashSet<>();
        this.precedingTables = new HashMap<>();
    }

    public void addReferencedTable(@Nonnull Class<?> table, @Nonnull String alias) {
        markAsReferencedWithAncestors(new TableAlias(table, alias));
    }

    public void addAutoJoinTable(@Nonnull Class<? extends Data> joinTable, @Nonnull String joinAlias,
                                 @Nonnull Class<? extends Data> precedingTable, @Nonnull String precedingAlias) {
        var child  = new TableAlias(joinTable, joinAlias);
        var parent = new TableAlias(precedingTable, precedingAlias);
        precedingTables.computeIfAbsent(child, ignore -> new HashSet<>()).add(parent);
        if (referencedTables.contains(child)) {
            markAsReferencedWithAncestors(parent);
        }
    }

    public boolean isReferenced(@Nonnull Class<? extends Data> table, @Nonnull String alias) {
        return referencedTables.contains(new TableAlias(table, alias));
    }

    private void markAsReferencedWithAncestors(@Nonnull TableAlias start) {
        Deque<TableAlias> stack = new ArrayDeque<>();
        Set<TableAlias> visited = new HashSet<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            var current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }
            referencedTables.add(current);
            for (var parent : precedingTables.getOrDefault(current, Set.of())) {
                if (!visited.contains(parent)) stack.push(parent);
            }
        }
    }
}
