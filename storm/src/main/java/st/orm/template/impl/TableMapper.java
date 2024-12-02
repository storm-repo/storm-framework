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
import jakarta.annotation.Nullable;
import st.orm.template.SqlTemplateException;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Comparator.comparingInt;
import static java.util.List.copyOf;
import static java.util.stream.Collectors.groupingBy;
import static st.orm.template.impl.SqlTemplateImpl.multiplePathsFoundException;

final class TableMapper {
    record Mapping(
            @Nonnull String alias,
            @Nonnull List<RecordComponent> components,
            boolean primaryKey,
            @Nullable String path,
            @Nullable String pkPath
    ) {
        Mapping {
            components = copyOf(components); // Defensive copy.
        }
        Mapping(@Nonnull String alias,
                       @Nonnull List<RecordComponent> components,
                       boolean primaryKey,
                       @Nullable String path) {
            this(alias, components, primaryKey, null, getPath(components, path));
        }
    }

    private final Map<Class<? extends Record>, List<Mapping>> mappings;

    TableMapper() {
        this.mappings = new HashMap<>();
    }

    public Mapping getMapping(@Nonnull Class<? extends Record> table, @Nullable String path) throws SqlTemplateException {
        // While it might seem appropriate to return the mapping at the root level when the path is null or empty,
        // doing so can lead to unexpected results if a field is added at the root level in the future.
        // Such an addition would cause the search to switch to that root element, altering the semantics of the query.
        // To avoid this ambiguity, it is better to raise an exception to indicate the ambiguity.
        var tableMappings = mappings.getOrDefault(table, List.of());
        var matches = findMappings(tableMappings, path);
        if (matches.size() == 1) {
            return matches.getFirst();
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

    public void mapPrimaryKey(@Nonnull Class<? extends Record> table,
                              @Nonnull String alias,
                              @Nonnull List<RecordComponent> components,
                              @Nullable String path) {
        mappings.computeIfAbsent(table, _ -> new ArrayList<>())
                .add(new Mapping(alias, components, true, path));
    }

    public void mapForeignKey(@Nonnull Class<? extends Record> table,
                              @Nonnull String alias,
                              @Nonnull RecordComponent component,
                              @Nullable String path) {
        mappings.computeIfAbsent(table, _ -> new ArrayList<>())
                .add(new Mapping(alias, List.of(component), false, path));
    }

    /**
     * Returns only the shortest unique paths from a stream of dot-separated paths. A path is considered "shortest" if
     * it is not a prefix (with a dot separator) of any other path in the stream.
     *
     * @param paths the stream of dot-separated paths to process.
     * @return a sequenced set of shortest unique paths.
     */
    private static SequencedSet<String> getShortestPaths(@Nonnull Stream<String> paths, @Nullable String path) {
        var shortestPaths = new LinkedHashSet<String>();
        Predicate<String> filter = pkPath -> {
            assert pkPath != null;
            if (path == null) {
                return true;
            }
            return pkPath.startsWith(path) &&   // Include exact matches.
                    (path.isEmpty() || pkPath.length() == path.length() || pkPath.charAt(path.length()) == '.');  // And exact sub-paths.
        };
        paths
                .filter(filter)
                .sorted(comparingInt(TableMapper::countLevels))  // Sort by number of levels (dots).
                .forEach(p -> {
                    boolean isSubPath = shortestPaths.stream().anyMatch(existing -> p.startsWith(existing + "."));
                    if (!isSubPath) {
                        shortestPaths.add(p);
                    }
                });
        return shortestPaths;
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

    private static String getPath(@Nonnull List<RecordComponent> components, @Nullable String path) {
        if (path == null) {
            return null;
        }
        if (components.size() != 1) {
            return null;
        }
        if (path.isEmpty()) {
            return components.getFirst().getName();
        }
        return STR."\{path}.\{components.getFirst().getName()}";
    }

    private static List<Mapping> findMappings(@Nonnull List<Mapping> mappings, @Nullable String path) {
        var mapped = mappings.stream()
                .filter(m -> m.pkPath() != null)    // Only include singular pk mappings.
                .collect(groupingBy(Mapping::pkPath));
        var shortestPath = getShortestPaths(mapped.keySet().stream(), path);
        if (path != null) {
            // Return all mappings that match the specified path.
            // Example: with mappings "a.b", "a.b.c", "a.c", "b" and path "a", the result would be "a.b" and "a.c".
            return shortestPath.stream()
                    .flatMap(p -> mapped.get(p).stream())
                    .toList();
        }
        // Return all mappings for which the path is unknown, but hides the mappings that are more specific.
        // Example: with mappings null, null, "a.b", "a.b.c", "a.c", "b" and no path, the result would be
        // null, null, "a.b", "a.c" and "b".
        return Stream.concat(
                mappings.stream().filter(m -> m.pkPath() == null),
                shortestPath.stream().flatMap(p -> mapped.get(p).stream())
        ).toList();
    }

    private SqlTemplateException notFoundException(@Nonnull Class<? extends Record> table, @Nullable String path, @Nonnull List<String> paths) {
        if (paths.isEmpty()) {
            return new SqlTemplateException(STR."\{table.getSimpleName()} not found \{path == null ? "in table graph" : STR."at path: '\{path}'"}.");
        }
        paths = paths.stream().map(p -> STR."'\{p}'").toList();
        if (path == null) {
            if (paths.size() == 1) {
                return new SqlTemplateException(STR."\{table.getSimpleName()} not found. Specify path '\{paths.getFirst()}' to identify the table.");
            }
            return new SqlTemplateException(STR."\{table.getSimpleName()} not found. Specify one of the following paths to identify the table: \{String.join(", ", paths)}.");
        }
        if (paths.size() == 1) {
            return new SqlTemplateException(STR."\{table.getSimpleName()} not found at path: '\{path}'. Specify path '\{paths.getFirst()}' to identify the table.");
        }
        return new SqlTemplateException(STR."\{table.getSimpleName()} not found at path: '\{path}'. Specify one of the following paths to identify the table: \{String.join(", ", paths)}.");
    }
}
