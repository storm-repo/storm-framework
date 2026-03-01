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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import st.orm.Data;
import st.orm.mapping.RecordField;

/**
 * The table mapper keeps track of all tables in the table graph and manually added joins.
 */
final class TableMapper {
    record Mapping(
            @Nonnull Class<? extends Data> source,
            @Nonnull String alias,
            @Nonnull RecordField field,
            boolean primaryKey,
            @Nullable Class<? extends Data> rootTable,
            @Nullable String pkPath
    ) {}

    private final Map<Class<? extends Data>, List<Mapping>> mappings;

    TableMapper() {
        this.mappings = new HashMap<>();
    }

    public boolean isUnique(@Nonnull Class<? extends Data> table) {
        return mappings.getOrDefault(table, List.of()).size() < 2;
    }

    public void mapPrimaryKey(
            @Nonnull Class<? extends Data> source,
            @Nonnull Class<? extends Data> target,
            @Nonnull String alias,
            @Nonnull RecordField field,
            @Nonnull Class<? extends Data> rootTable,
            @Nullable String path) {
        mappings.computeIfAbsent(target, ignore -> new ArrayList<>())
                .add(new Mapping(source, alias, field, true, rootTable, getPath(field, path)));
    }

    public void mapForeignKey(
            @Nonnull Class<? extends Data> source,
            @Nonnull Class<? extends Data> target,
            @Nonnull String alias,
            @Nonnull RecordField field,
            @Nonnull Class<? extends Data> rootTable,
            @Nullable String path) {
        mappings.computeIfAbsent(target, ignore -> new ArrayList<>())
                .add(new Mapping(source, alias, field, false, rootTable, getPath(field, path)));
    }

    private static String getPath(@Nonnull RecordField field, @Nullable String path) {
        if (path == null) {
            return null;
        }
        if (path.isEmpty()) {
            return field.name();
        }
        return "%s.%s".formatted(path, field.name());
    }
}
