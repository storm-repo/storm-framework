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
import jakarta.annotation.Nullable;
import st.orm.Data;
import st.orm.core.template.SqlTemplateException;
import st.orm.mapping.RecordField;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.groupingBy;
import static st.orm.core.template.impl.SqlTemplateImpl.multiplePathsFoundException;

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

    private final TableUse tableUse;
    private final Map<Class<? extends Data>, List<Mapping>> mappings;

    TableMapper(@Nonnull TableUse tableUse) {
        this.tableUse = tableUse;
        this.mappings = new HashMap<>();
    }

    public Mapping getMapping(@Nonnull Class<? extends Data> table, @Nullable Class<? extends Data> rootTable, @Nullable String path) throws SqlTemplateException {
        // While it might seem appropriate to return the mapping at the root level when the path is null or empty,
        // doing so can lead to unexpected results if a field is added at the root level in the future.
        // Such an addition would cause the search to switch to that root element, altering the semantics of the query.
        // To avoid this ambiguity, it is better to raise an exception to indicate the ambiguity.
        var tableMappings = mappings.getOrDefault(table, List.of());
        var matches = findMappings(tableMappings, rootTable, path);
        if (matches.size() == 1) {
            var match = matches.getFirst();
            tableUse.addReferencedTable(match.source(), match.alias());
            return match;
        }
        var paths = tableMappings.stream()
                .map(Mapping::pkPath)
                .filter(Objects::nonNull)
                .sorted(comparingInt(TableMapper::countLevels))  // Sort by number of levels (dots).
                .toList();
        if (matches.size() > 1) {
            throw multiplePathsFoundException(table, paths);
        } else {
            throw notFoundException(table, path, paths);
        }
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

    /**
     * Counts the number of levels (dots) in the path.
     *
     * @param path the path to count the levels for.
     * @return the number of levels in the path.
     */
    private static int countLevels(String path) {
        int count = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '.') {
                count++;
            }
        }
        return count;
    }

    private static List<Mapping> findMappings(@Nonnull List<Mapping> mappings, @Nullable Class<? extends Data> rootTable, @Nullable String path) {
        var mapped = mappings.stream()
                .filter(m -> m.pkPath() != null)    // Only include singular pk mappings.
                .filter(m -> rootTable == null || rootTable == m.rootTable())  // Only include mappings if they originate from the same root table to properly use manually added join tables.
                .collect(groupingBy(Mapping::pkPath));
        if (path != null) {
            return mapped.getOrDefault(path, List.of());
        }
        return Stream.concat(
                mappings.stream().filter(m -> m.pkPath() == null),
                mapped.values().stream().flatMap(List::stream)
        ).toList();
    }

    private SqlTemplateException notFoundException(@Nonnull Class<? extends Data> table, @Nullable String path, @Nonnull List<String> paths) {
        if (paths.isEmpty()) {
            return new SqlTemplateException("%s not found %s.".formatted(table.getSimpleName(), path == null ? "in table graph" : "at path: '%s'".formatted(path)));
        }
        paths = paths.stream().map("'%s'"::formatted).toList();
        if (path == null) {
            if (paths.size() == 1) {
                return new SqlTemplateException("%s not found. Specify path '%s' to identify the table.".formatted(table.getSimpleName(), paths.getFirst()));
            }
            return new SqlTemplateException("%s not found. Specify one of the following paths to identify the table: %s.".formatted(table.getSimpleName(), String.join(", ", paths)));
        }
        if (paths.size() == 1) {
            return new SqlTemplateException("%s not found at path: '%s'. Specify path '%s' to identify the table.".formatted(table.getSimpleName(), path, paths.getFirst()));
        }
        return new SqlTemplateException("%s not found at path: '%s'. Specify one of the following paths to identify the table: %s.".formatted(table.getSimpleName(), path, String.join(", ", paths)));
    }
}
