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
import st.orm.template.ResolveScope;
import st.orm.template.SqlTemplateException;
import st.orm.template.TableAliasResolver;
import st.orm.template.TableNameResolver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.lang.Character.isUpperCase;
import static java.lang.Character.toLowerCase;
import static java.util.Optional.empty;
import static st.orm.template.ResolveScope.CASCADE;
import static st.orm.template.ResolveScope.INNER;
import static st.orm.template.impl.RecordReflection.getTableName;
import static st.orm.template.impl.SqlTemplateImpl.multiplePathsFoundException;

final class AliasMapper {
    private final Map<Class<? extends Record>, SequencedCollection<TableAlias>> aliasMap;
    private final AliasMapper parent;
    private final TableAliasResolver tableAliasResolver;
    private final TableNameResolver tableNameResolver;

    record TableAlias(Class<? extends Record> table, String path, String alias) {}

    AliasMapper(@Nullable TableAliasResolver tableAliasResolver,
                @Nullable TableNameResolver tableNameResolver,
                @Nullable AliasMapper parent) {
        this.tableAliasResolver = tableAliasResolver;
        this.tableNameResolver = tableNameResolver;
        this.parent = parent;
        this.aliasMap = new HashMap<>();
    }

    /**
     * Retrieves all aliases, including those from the parent. Note that the stream may contain duplicates as
     * parents may contain the same aliases.
     */
    private Stream<String> aliases() {
        // Effectively using CASCADE to include all aliases.
        var local = aliasMap.values().stream().flatMap(it -> it.stream()
                    .map(TableAlias::alias));
        var global = parent == null ? Stream.<String>of() : parent.aliases();
        return Stream.concat(local, global);
    }

    /**
     * Retrieves all aliases for the specified table, including those from the parent. Note that the stream may
     * contain duplicates as parents may contain the same aliases.
     *
     * @param table the table to retrieve the aliases for.
     */
    private Stream<String> aliases(@Nonnull Class<? extends Record> table, @Nonnull ResolveScope scope) {
        var local = switch (scope) {
            case INNER, CASCADE -> aliasMap.getOrDefault(table, List.of()).stream()
                    .map(TableAlias::alias);
            case OUTER -> Stream.<String>of();
        };
        var global = switch (scope) {
            case INNER -> Stream.<String>of();
            case CASCADE, OUTER ->
                    parent == null ? Stream.<String>of() : parent.aliases(table, CASCADE);   // Use STRICT to include parents recursively.
        };
        return Stream.concat(local, global);
    }

    /**
     * Retrieves all aliases for the specified table and path, including those from the parent. Note that the
     * stream may contain duplicates as parents may contain the same aliases. If path is null, all aliases for
     * the table are returned.
     *
     * @param table the table to retrieve the aliases for.
     * @param path  the path to retrieve the aliases for.
     * @param scope CASCADE to include local and outer aliases, LOCAL to include local aliases only, and
     *              OUTER to include outer aliases only.
     */
    private Stream<String> aliases(Class<? extends Record> table, @Nullable String path, @Nonnull ResolveScope scope) {
        var local = switch (scope) {
            case INNER, CASCADE -> aliasMap.getOrDefault(table, List.of()).stream()
                    .filter(a -> path == null || Objects.equals(path, a.path()))
                    .map(TableAlias::alias);
            case OUTER -> Stream.<String>of();
        };
        var global = switch (scope) {
            case INNER -> Stream.<String>of();
            case CASCADE, OUTER ->
                    parent == null ? Stream.<String>of() : parent.aliases(table, path, CASCADE);   // Use STRICT to include parents recursively.
        };
        return Stream.concat(local, global);
    }

    private SqlTemplateException multipleFoundException(@Nonnull Class<? extends Record> table, @Nullable String path, @Nonnull ResolveScope resolveMode) {
        if (resolveMode != INNER) {
            return new SqlTemplateException(STR."Multiple aliases found for: \{table.getSimpleName()} at path: '\{path}'. Use INNER scope to limit alias resolution to the current scope.");
        }
        var paths = aliasMap.get(table).stream()
                .map(TableAlias::path)
                .toList();
        return multiplePathsFoundException(table, paths);
    }

    public String useAlias(@Nonnull Class<? extends Record> table, @Nonnull String alias, @Nonnull ResolveScope scope) throws SqlTemplateException {
        if (getAliases(table, scope).stream().noneMatch(a -> a.equals(alias))) {
            throw new SqlTemplateException(STR."Alias \{alias} for table \{table.getSimpleName()} not found.");
        }
        return alias;
    }

    public List<String> getAliases(@Nonnull Class<? extends Record> table, @Nonnull ResolveScope scope) {
        return aliases(table, scope).toList();
    }

    public boolean exists(@Nonnull Class<? extends Record> table, @Nonnull ResolveScope scope) {
        return !getAliases(table, scope).isEmpty();
    }

    /**
     * Returns the primary alias for the specified table.
     *
     * @param table the table to get the alias for.
     * @return the primary alias.
     */
    public Optional<String> getPrimaryAlias(@Nonnull Class<? extends Record> table) {
        var list = getAliases(table, INNER);
        if (list.isEmpty()) {
            return empty();
        }
        return Optional.of(list.getFirst());
    }

    public String getAlias(@Nonnull Class<? extends Record> table, @Nullable String path, @Nonnull ResolveScope scope) throws SqlTemplateException {
        var result = findAlias(table, path, scope);
        if (result.isPresent()) {
            return result.get();
        }
        if (path != null) {
            throw new SqlTemplateException(STR."Alias for \{table.getSimpleName()} not found at path: '\{path}'.");
        }
        if (exists(table, scope)) {
            // Table is registered, but alias could not be resolved (due to empty registration). Revert to full table name.
            return getTableName(table, tableNameResolver);
        }
        throw new SqlTemplateException(STR."Alias for \{table.getSimpleName()} not found.");
    }

    public Optional<String> findAlias(@Nonnull Class<? extends Record> table, @Nullable String path, @Nonnull ResolveScope scope) throws SqlTemplateException {
        var list = aliases(table, path, scope).toList();
        if (list.isEmpty() && path != null) {
            list = aliases(table, path, scope).toList();
        }
        if (list.isEmpty()) {
            return Optional.empty();
        }
        if (list.size() > 1) {
            throw multipleFoundException(table, path, scope);
        }
        if (path != null) {
            return Optional.of(list.getFirst());
        }
        String alias = list.getFirst();
        return alias.isEmpty() ? Optional.empty() : Optional.of(alias);
    }

    public String generateAlias(@Nonnull Class<? extends Record> table, @Nullable String path) throws SqlTemplateException {
        String alias = generateAlias(table, tableAliasResolver,
                proposedAlias -> aliases().noneMatch(proposedAlias::equals));    // Take all aliases into account to prevent unnecessary shadowing.
        if (alias.isEmpty()) {
            throw new SqlTemplateException(STR."Failed to generate alias for \{table.getSimpleName()}.");
        }
        aliasMap.computeIfAbsent(table, _ -> new LinkedHashSet<>()).add(new TableAlias(table, path, alias));
        return alias;
    }

    public void setAlias(@Nonnull Class<? extends Record> table, @Nonnull String alias, @Nullable String path) throws SqlTemplateException {
        if (!aliasMap.computeIfAbsent(table, _ -> new LinkedHashSet<>()).add(new TableAlias(table, path, alias))) {
            // Only detect duplicated aliases at the same level.
            throw new SqlTemplateException(STR."Alias already exists for table \{table.getSimpleName()}.");
        }
    }

    private String generateAlias(@Nonnull Class<? extends Record> table,
                                 @Nullable TableAliasResolver tableAliasResolver,
                                 @Nonnull Predicate<String> tester) throws SqlTemplateException {
        String alias;
        if (tableAliasResolver == null) {
            // Extract the capitals from the class name to form the base alias.
            String className = table.getSimpleName();
            StringBuilder aliasBuilder = new StringBuilder("_");    // Use underscore as prefix to avoid clashes with client aliases.
            for (char ch : className.toCharArray()) {
                if (isUpperCase(ch)) {
                    aliasBuilder.append(toLowerCase(ch));
                }
            }
            String baseAlias = aliasBuilder.toString();
            alias = baseAlias;
            // Check if the base alias passes the tester predicate. If not, append a number and check again.
            int counter = 1;
            while (!tester.test(alias)) {
                alias = baseAlias + counter;
                counter++;
            }
        } else {
            int counter = 0;
            var aliases = new HashSet<>();
            do {
                alias = tableAliasResolver.resolveTableAlias(table, counter++);
                if (alias.isEmpty()) {
                    break;
                }
                if (!aliases.add(alias)) {
                    throw new SqlTemplateException(STR."Table alias returns the same alias \{alias} multiple times.");
                }
            } while (!tester.test(alias));
        }
        if (alias.isEmpty()) {
            throw new SqlTemplateException(STR."Table alias for \{table.getSimpleName()} is empty.");
        }
        return alias;
    }
}
