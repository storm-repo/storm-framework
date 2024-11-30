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
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class TableMapperImpl implements TableMapper {
    private final Map<Class<? extends Record>, List<Mapping>> mappings;

    TableMapperImpl() {
        this.mappings = new HashMap<>();
    }

    private String getPath(Mapping mapping) {
        if (mapping.path() == null) {
            return null;
        }
        assert mapping.components().size() == 1;
        if (mapping.path().isEmpty()) {
            return mapping.components().getFirst().getName();
        }
        return STR."\{mapping.path()}.\{mapping.components().getFirst().getName()}";
    }

    private List<Mapping> getMappings(@Nonnull Class<? extends Record> table, @Nullable String path) {
        var list = mappings.getOrDefault(table, List.of()).stream()
                .filter(m -> m.components().size() == 1)
                .toList();
        if (path != null) {
            return list.stream().filter(m -> path.equals(getPath(m))).toList();
        }
        return list;
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

    private SqlTemplateException multipleFoundException(@Nonnull Class<? extends Record> table, @Nonnull List<String> paths) {
        if (paths.isEmpty()) {
            return new SqlTemplateException(STR."Multiple occurences of \{table.getSimpleName()} found in table graph.");
        }
        paths = paths.stream().filter(Objects::nonNull).map(p -> STR."'\{p}'").toList();
        return SqlTemplateImpl.multiplePathsFoundException(table, paths);
    }

    @Override
    public Mapping getMapping(@Nonnull Class<? extends Record> table, @Nullable String path) throws SqlTemplateException {
        var mappings = getMappings(table, path).stream().toList();
        var paths = mappings.stream().map(this::getPath).distinct().toList();
        if (path != null) {
            // Look for mapping at specified path.
            if (mappings.isEmpty()) {
                throw notFoundException(table, path, paths);
            }
            if (mappings.size() > 1) {
                throw new SqlTemplateException(STR."Multiple occurences of \{table.getSimpleName()} found at path: '\{path}'.");
            }
            return mappings.getFirst();
        }
        if (mappings.size() == 1) {
            return mappings.getFirst();
        } else if (mappings.size() > 1) {
            throw multipleFoundException(table, paths);
        } else {
            throw notFoundException(table, null, paths);
        }
    }

    @Override
    public void mapPrimaryKey(@Nonnull Class<? extends Record> table,
                              @Nonnull String alias,
                              @Nonnull List<RecordComponent> components,
                              @Nullable String path) {
        mappings.computeIfAbsent(table, _ -> new ArrayList<>())
                .add(new Mapping(alias, components, true, path));
    }

    @Override
    public void mapForeignKey(@Nonnull Class<? extends Record> table,
                              @Nonnull String alias,
                              @Nonnull RecordComponent component,
                              @Nullable String path) {
        mappings.computeIfAbsent(table, _ -> new ArrayList<>())
                .add(new Mapping(alias, List.of(component), false, path));
    }
}
