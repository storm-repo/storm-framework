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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;

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
    private final Set<Class<? extends Record>> referencedTables;
    private final Map<Class<? extends Record>, Set<Class<? extends Record>>> precedingTables;

    TableUse() {
        this.referencedTables = new HashSet<>();
        this.precedingTables = new HashMap<>();
    }

    public void addReferencedTable(@Nonnull Class<? extends Record> table) {
        referencedTables.add(table);
        referencedTables.addAll(precedingTables.getOrDefault(table, Set.of()));
    }

    public void addAutoJoinTable(@Nonnull Class<? extends Record> joinTable, @Nonnull Class<? extends Record> precedingTable) {
        precedingTables.computeIfAbsent(joinTable, _ -> new HashSet<>()).add(precedingTable);
    }

    public boolean isReferencedTable(@Nonnull Class<? extends Record> table) {
        return referencedTables.contains(table);
    }
}
