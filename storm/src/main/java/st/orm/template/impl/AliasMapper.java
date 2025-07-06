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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.template.SqlDialect;
import st.orm.template.Metamodel;
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
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static st.orm.template.ResolveScope.CASCADE;
import static st.orm.template.ResolveScope.INNER;
import static st.orm.template.impl.RecordReflection.getTableName;
import static st.orm.template.impl.SqlTemplateImpl.multiplePathsFoundException;

final class AliasMapper {

    private final TableUse tableUse;
    private final Map<Class<? extends Record>, SequencedCollection<TableAlias>> aliasMap;
    private final AliasMapper parent;
    private final TableAliasResolver tableAliasResolver;
    private final TableNameResolver tableNameResolver;

    record TableAlias(Class<? extends Record> table, String path, String alias) {}

    /**
     * Alias entry with its nesting level: 0=current, 1=parent, etc.
     */
    private record AliasEntry(String alias, int level) {}

    AliasMapper(@Nonnull TableUse tableUse,
                @Nonnull TableAliasResolver tableAliasResolver,
                @Nonnull TableNameResolver tableNameResolver,
                @Nullable AliasMapper parent) {
        this.tableUse = requireNonNull(tableUse);
        this.tableAliasResolver = requireNonNull(tableAliasResolver);
        this.tableNameResolver = requireNonNull(tableNameResolver);
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

//    /**
//     * Retrieves all aliases for the specified table, including those from the parent. Note that the stream may
//     * contain duplicates as parents may contain the same aliases.
//     *
//     * @param table the table to retrieve the aliases for.
//     * @param scope INNER to include local and outer aliases, OUTER to include outer aliases only and CASCADE to
//     *              include all aliases.
//     * @param precedingTable the table that precedes {@code table} in the join chain.
//     */
//    private Stream<String> aliases(@Nonnull Class<? extends Record> table,
//                                   @Nonnull ResolveScope scope,
//                                   @Nullable Class<? extends Record> precedingTable) {
//        if (precedingTable != null) {
//            tableUse.addAutoJoinTable(table, precedingTable);
//        } else {
//            tableUse.addReferencedTable(table);
//        }
//        var local = switch (scope) {
//            case INNER, CASCADE -> aliasMap.getOrDefault(table, List.of()).stream()
//                    .map(TableAlias::alias);
//            case OUTER -> Stream.<String>of();
//        };
//        var global = switch (scope) {
//            case INNER -> Stream.<String>of();
//            case CASCADE, OUTER ->
//                    parent == null ? Stream.<String>of() : parent.aliases(table, CASCADE, precedingTable);   // Use CASCADE to include parents recursively.
//        };
//        return Stream.concat(local, global);
//    }

    /**
     * Recursively collects all alias entries for a given table and optional component path,
     * tagging each with its nesting depth (0 = current level, 1 = parent, etc.).
     *
     * <p>At each level it will:</p>
     * <ol>
     *   <li>Include any locally registered aliases whose path matches (or all if {@code path} is null)</li>
     *   <li>Recurse into the parent mapper (incrementing {@code level} by 1)</li>
     * </ol></p>
     *
     * <p>The resulting stream emits <code>AliasEntry</code> instances with both the alias string and its {@code level},
     * so callers can apply their own filtering (e.g. only outer or only inner levels).</p>
     *
     * @param table         the record type whose aliases are being collected
     * @param path          optional component path; if <code>null</code>, matches all aliases
     * @param level         the current nesting depth: 0=current, 1=parent, etc.
     */
    private Stream<AliasEntry> collectAliasEntries(@Nonnull Class<? extends Record> table,
                                                   @Nullable String path,
                                                   int level) {
        // Current level matches.
        var local = aliasMap.getOrDefault(table, List.of()).stream()
                .filter(ta -> path == null || Objects.equals(path, ta.path()))
                .map(ta -> new AliasEntry(ta.alias(), level));
        // Recurse to parent if present.
        var parentStream = parent == null
                ? Stream.<AliasEntry>empty()
                : parent.collectAliasEntries(table, path, level + 1);
        return Stream.concat(local, parentStream);
    }

    private SqlTemplateException multipleFoundException(@Nonnull Class<? extends Record> table,
                                                        @Nullable String path,
                                                        @Nonnull ResolveScope resolveMode) {
        if (resolveMode != INNER) {
            if (path != null) {
                return new SqlTemplateException(STR."Multiple aliases found for: \{table.getSimpleName()} at path: '\{path}'. Use INNER scope to limit alias resolution to the current scope.");
            }
            return new SqlTemplateException(STR."Multiple aliases found for: \{table.getSimpleName()}.");
        }
        var paths = aliasMap.get(table).stream()
                .map(TableAlias::path)
                .toList();
        return multiplePathsFoundException(table, paths);
    }

    public String useAlias(@Nonnull Class<? extends Record> table,
                           @Nonnull String alias) throws SqlTemplateException {
        if (collectAliasEntries(table, null, 0)
                .filter(e -> e.alias().equals(alias))
                .findAny()
                .isEmpty()) {
            throw new SqlTemplateException(STR."Alias \{alias} for table \{table.getSimpleName()} not found.");
        }
        return alias;
    }

    public boolean exists(@Nonnull Class<? extends Record> table, @Nonnull ResolveScope scope) throws SqlTemplateException {
        return findAlias(table, null, scope).isPresent();
    }

    /**
     * Returns the primary alias for the specified table.
     *
     * @param table the table to get the alias for.
     * @return the primary alias.
     */
    public Optional<String> getPrimaryAlias(@Nonnull Class<? extends Record> table) throws SqlTemplateException {
        return findAlias(table, null, INNER);
    }

    /**
     * Returns the alias for the table at the specified path.
     *
     * <p><strong>Note:</strong> The alias returned is safe for use in SQL and does not require escaping.</p>
     *
     * @param metamodel the metamodel of the table to get the alias for.
     * @param scope the scope to resolve the alias in.
     * @param dialect the SQL dialect to use in case the alias is based on table name and potentially requires escaping.
     * @return the alias for the table at the specified path.
     * @throws SqlTemplateException if the alias could not be resolved.
     */
    public String getAlias(@Nonnull Metamodel<?, ?> metamodel,
                           @Nonnull ResolveScope scope,
                           @Nonnull SqlDialect dialect) throws SqlTemplateException {
        return getAlias(metamodel, scope, dialect,
                () -> new SqlTemplateException(STR."Alias for table not found at \{metamodel}."));
    }

    /**
     * Returns the alias for the table at the specified path.
     *
     * <p><strong>Note:</strong> The alias returned is safe for use in SQL and does not require escaping.</p>
     *
     * @param metamodel the metamodel of the table to get the alias for.
     * @param scope the scope to resolve the alias in.
     * @param dialect the SQL dialect to use in case the alias is based on table name and potentially requires escaping.
     * @param exceptionSupplier the exception supplier to use in case the alias could not be resolved.
     * @return the alias for the table at the specified path.
     * @throws SqlTemplateException if the alias could not be resolved.
     */
    public String getAlias(@Nonnull Metamodel<?, ?> metamodel,
                           @Nonnull ResolveScope scope,
                           @Nonnull SqlDialect dialect,
                           @Nonnull Supplier<SqlTemplateException> exceptionSupplier) throws SqlTemplateException {
        var table = metamodel.table();
        String path = table.componentPath();
        return getAlias(table.componentType(), path.isEmpty() ? null : path, scope, null, dialect, exceptionSupplier);
    }

    /**
     * Returns the alias for the table at the specified path.
     *
     * <p><strong>Note:</strong> The alias returned is safe for use in SQL and does not require escaping.</p>
     *
     * @param table the table to get the alias for.
     * @param path the path of the table (optional).
     * @param scope the scope to resolve the alias in.
     * @param dialect the SQL dialect to use in case the alias is based on table name and potentially requires escaping.
     * @return the alias for the table at the specified path.
     * @throws SqlTemplateException if the alias could not be resolved.
     */
    public String getAlias(@Nonnull Class<? extends Record> table,
                           @Nullable String path,
                           @Nonnull ResolveScope scope,
                           @Nonnull SqlDialect dialect) throws SqlTemplateException {
        return getAlias(table, path, scope, null, dialect);
    }

    /**
     * Returns the alias for the table at the specified path.
     *
     * <p><strong>Note:</strong> The alias returned is safe for use in SQL and does not require escaping.</p>
     *
     * @param table the table to get the alias for.
     * @param path the path of the table (optional).
     * @param scope the scope to resolve the alias in.
     * @param dialect the SQL dialect to use in case the alias is based on table name and potentially requires escaping.
     * @param exceptionSupplier the exception supplier to use in case the alias could not be resolved.
     * @return the alias for the table at the specified path.
     * @throws SqlTemplateException if the alias could not be resolved.
     */
    public String getAlias(@Nonnull Class<? extends Record> table,
                           @Nullable String path,
                           @Nonnull ResolveScope scope,
                           @Nonnull SqlDialect dialect,
                           @Nonnull Supplier<SqlTemplateException> exceptionSupplier) throws SqlTemplateException {
        return getAlias(table, path, scope, null, dialect, exceptionSupplier);
    }

    /**
     * Returns the alias for the table at the specified path.
     *
     * <p><strong>Note:</strong> The alias returned is safe for use in SQL and does not require escaping.</p>
     *
     * @param table the table to get the alias for.
     * @param path the path of the table (optional).
     * @param scope the scope to resolve the alias in.
     * @param autoJoinTable the table that is auto-joined to the specified table (optional).
     * @param dialect the SQL dialect to use in case the alias is based on table name and potentially requires escaping.
     * @return the alias for the table at the specified path.
     * @throws SqlTemplateException if the alias could not be resolved.
     */
    public String getAlias(@Nonnull Class<? extends Record> table,
                           @Nullable String path,
                           @Nonnull ResolveScope scope,
                           @Nullable Class<? extends Record> autoJoinTable,
                           @Nonnull SqlDialect dialect) throws SqlTemplateException {
        return getAlias(table, path, scope, autoJoinTable, dialect,
                () -> path != null
                        ? new SqlTemplateException(STR."Alias for \{table.getSimpleName()} not found at path: '\{path}'.")
                        : new SqlTemplateException(STR."Alias for \{table.getSimpleName()} not found."));
    }

    /**
     * Returns the alias for the table at the specified path.
     *
     * <p><strong>Note:</strong> The alias returned is safe for use in SQL and does not require escaping.</p>
     *
     * @param table the table to get the alias for.
     * @param path the path of the table (optional).
     * @param scope the scope to resolve the alias in.
     * @param autoJoinTable the table that is auto-joined to the specified table (optional).
     * @param dialect the SQL dialect to use in case the alias is based on table name and potentially requires escaping.
     * @param exceptionSupplier the exception supplier to use in case the alias could not be resolved.
     * @return the alias for the table at the specified path.
     * @throws SqlTemplateException if the alias could not be resolved.
     */
    public String getAlias(@Nonnull Class<? extends Record> table,
                           @Nullable String path,
                           @Nonnull ResolveScope scope,
                           @Nullable Class<? extends Record> autoJoinTable,
                           @Nonnull SqlDialect dialect,
                           @Nonnull Supplier<SqlTemplateException> exceptionSupplier) throws SqlTemplateException {
        var alias = findAlias(table, path, scope, autoJoinTable).orElse("");
        if (!alias.isEmpty()) {
            return alias;
        }
        if (path != null) {
            throw exceptionSupplier.get();
        }
        if (exists(table, scope)) {
            // Table is registered, but alias could not be resolved (due to empty registration). Revert to full table name.
            return getTableName(table, tableNameResolver).getQualifiedName(dialect);
        }
        throw exceptionSupplier.get();
    }

    public Optional<String> findAlias(@Nonnull Class<? extends Record> table,
                                      @Nullable String path,
                                      @Nonnull ResolveScope scope) throws SqlTemplateException {
        return findAlias(table, path, scope, null);
    }

    private Optional<String> findAlias(@Nonnull Class<? extends Record> table,
                                       @Nullable String path,
                                       @Nonnull ResolveScope scope,
                                       @Nullable Class<? extends Record> autoJoinTable) throws SqlTemplateException {
        var entries = collectAliasEntries(table, path, 0).toList();
        var filtered = entries.stream()
                .filter(e -> switch (scope) {
                    case INNER   -> e.level() == 0;
                    case OUTER   -> e.level() >  0;
                    case CASCADE -> true;
                })
                .toList();
        if (filtered.isEmpty()) {
            return empty();
        }
        var entry = filtered.getFirst();
        if (filtered.size() > 1) {
            if (filtered.get(1).level() == entry.level()) {
                // Multiple aliases found at the same level.
                throw multipleFoundException(table, path, scope == INNER ? CASCADE : scope);
            }
        }
        if (entry.level() == 0) {
            // Only register usage in the current scope.
            if (autoJoinTable != null) {
                tableUse.addAutoJoinTable(autoJoinTable, table);
            } else {
                tableUse.addReferencedTable(table);
            }
        }
        return Optional.of(entry.alias());
    }

    public String generateAlias(@Nonnull Class<? extends Record> table,
                                @Nullable String path,
                                @Nonnull SqlDialect dialect) throws SqlTemplateException {
        return generateAlias(table, path, null, dialect);
    }

    public String generateAlias(@Nonnull Class<? extends Record> table,
                                @Nullable String path,
                                @Nullable Class<? extends Record> autoJoinTable,
                                @Nonnull SqlDialect dialect) throws SqlTemplateException {
        if (autoJoinTable != null) {
            tableUse.addAutoJoinTable(table, autoJoinTable);
        } else {
            tableUse.addReferencedTable(table);
        }
        String alias = generateAlias(table,
                proposedAlias -> aliases().noneMatch(proposedAlias::equals), dialect);    // Take all aliases into account to prevent unnecessary shadowing.
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
                                 @Nonnull Predicate<String> tester,
                                 @Nonnull SqlDialect dialect) throws SqlTemplateException {
        String alias;
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
        if (alias.isEmpty()) {
            throw new SqlTemplateException(STR."Table alias for \{table.getSimpleName()} is empty.");
        }
        return dialect.getSafeIdentifier(alias);
    }
}
