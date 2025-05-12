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
import st.orm.FK;
import st.orm.Ref;
import st.orm.PK;
import st.orm.Templates.SelectMode;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.SqlDialect;
import st.orm.template.SqlTemplate;
import st.orm.template.SqlTemplateException;
import st.orm.template.impl.Elements.Select;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.join;
import static java.util.List.copyOf;
import static st.orm.spi.Providers.getORMConverter;
import static st.orm.template.Metamodel.root;
import static st.orm.template.ResolveScope.INNER;
import static st.orm.template.impl.RecordReflection.getColumnName;
import static st.orm.template.impl.RecordReflection.getForeignKeys;
import static st.orm.template.impl.SqlTemplateImpl.toPathString;

/**
 * A processor for a select element of a template.
 */
final class SelectProcessor implements ElementProcessor<Select> {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private final SqlTemplate template;
    private final SqlDialectTemplate dialectTemplate;
    private final AliasMapper aliasMapper;
    private final PrimaryTable primaryTable;

    SelectProcessor(@Nonnull SqlTemplateProcessor templateProcessor) {
        this.template = templateProcessor.template();
        this.dialectTemplate = templateProcessor.dialectTemplate();
        this.aliasMapper = templateProcessor.aliasMapper();
        this.primaryTable = templateProcessor.primaryTable();
    }

    /**
     * Process a select element of a template.
     *
     * @param select the select element to process.
     * @return the result of processing the element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    @Override
    public ElementResult process(@Nonnull Select select) throws SqlTemplateException {
        // Resolve the path of type, starting from the primary table. This will help in resolving the alias in case the
        // same table is used multiple times.
        List<RecordComponent> path = null;
        String pathString = null;
        if (primaryTable != null) {
            List<RecordComponent> pathList = resolvePath(primaryTable.table(), select.table());
            if (pathList != null) {
                path = pathList;
                pathString = toPathString(pathList);
            }
        }
        // Path is implicit. First try the path, if not found, try without the path.
        var resolvedAlias = aliasMapper.findAlias(select.table(), pathString, INNER);
        if (resolvedAlias.isEmpty()) {
            resolvedAlias = aliasMapper.findAlias(select.table(), null, INNER);
        }
        var alias = resolvedAlias.orElse("");
        var columns = getColumnsString(select.table(), select.mode(), path, aliasMapper,
                primaryTable == null ? null : primaryTable.table(), alias, false);
        if (!columns.isEmpty()) {
            columns.removeLast();
        }
        return new ElementResult(join("", columns));
    }

    //
    // This class will utilize a QueryModel to simplify / unify the record mapping logic, similarly to how the Model is
    // used to generate insert and update SQL. This will allow for a more consistent and maintainable code
    // base.
    //

    private void checkAlias(@Nonnull String alias, @Nonnull Class<?> type) throws SqlTemplateException {
        if (alias.isEmpty()) {
            throw new SqlTemplateException(STR."Table \{type.getSimpleName()} not found in table graph.");
        }
    }

    /**
     * Returns the columns of a record type as a SQL string. Each element of the resulting list is a column.
     *
     * @param type the record type.
     * @param mode the mode determines which columns are selected.
     * @param path the path of the record type.
     * @param aliasMapper the alias mapper.
     * @param primaryTable the primary table (optional).
     * @param alias the alias of the table.
     * @param isPk if the method is called recursively in the context of a pk.
     * @return a list of columns as a SQL string.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    private List<String> getColumnsString(@Nonnull Class<? extends Record> type,
                                          @Nonnull SelectMode mode,
                                          @Nullable List<RecordComponent> path,
                                          @Nonnull AliasMapper aliasMapper,
                                          @Nullable Class<? extends Record> primaryTable,
                                          @Nonnull String alias,
                                          boolean isPk) throws SqlTemplateException {
        var columns = new ArrayList<String>();
        for (var component : type.getRecordComponents()) {
            boolean pk = isPk || REFLECTION.isAnnotationPresent(component, PK.class);
            boolean fk = REFLECTION.isAnnotationPresent(component, FK.class);
            boolean ref = Ref.class.isAssignableFrom(component.getType());
            var converter = getORMConverter(component).orElse(null);
            if (converter != null) {
                if (mode != SelectMode.PK) {
                    checkAlias(alias, type);
                    converter.getColumns(c -> getColumnName(c, template.columnNameResolver())).forEach(name -> {
                        columns.add(dialectTemplate."\{alias}.\{name}");
                        columns.add(", ");
                    });
                }
            } else if (fk && (mode != SelectMode.NESTED || ref)) {   // Use foreign key column if not nested or if ref.
                if (mode != SelectMode.PK) {
                    checkAlias(alias, type);
                    getForeignKeys(component, template.foreignKeyResolver(), template.columnNameResolver()).forEach(name -> {
                        columns.add(dialectTemplate."\{alias}.\{name}");
                        columns.add(", ");
                    });
                }
            } else if (component.getType().isRecord()) {
                if (mode != SelectMode.PK || pk) {
                    @SuppressWarnings("unchecked")
                    var recordType = (Class<? extends Record>) component.getType();
                    List<RecordComponent> newPath;
                    if (path != null) {
                        newPath = new ArrayList<>(path);
                        newPath.add(component);
                        newPath = copyOf(newPath);
                    } else if (recordType == primaryTable) {
                        newPath = List.of();
                    } else {
                        newPath = null;
                    }
                    columns.addAll(getColumnsString(recordType, mode, newPath, aliasMapper, primaryTable,
                            getAlias(recordType, newPath, alias, aliasMapper, template.dialect()), pk));
                }
            } else {
                if (ref) {
                    throw new SqlTemplateException(STR."Ref component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is not a foreign key.");
                }
                if (mode != SelectMode.PK || pk) {
                    checkAlias(alias, type);
                    var name = getColumnName(component, template.columnNameResolver());
                    columns.add(dialectTemplate."\{alias}.\{name}");
                    columns.add(", ");
                }
            }
        }
        return columns;
    }

    /**
     * Returns the alias of a record type based on the path and the alias of the table.
     *
     * @param type the record type to get the alias for.
     * @param path the path of the record type (optional).
     * @param alias the alias of the primary table.
     * @param aliasMapper the alias mapper to look up the alias.
     * @param dialect the SQL dialect.
     * @return the alias of the record type for the given path.
     * @throws SqlTemplateException if the alias is not found.
     */
    private static String getAlias(@Nonnull Class<? extends Record> type,
                                   @Nullable List<RecordComponent> path,
                                   @Nonnull String alias,
                                   @Nonnull AliasMapper aliasMapper,
                                   @Nonnull SqlDialect dialect) throws SqlTemplateException {
        if (path == null) {
            var result = aliasMapper.findAlias(type, null, INNER);
            if (result.isPresent()) {
                return result.get();
            }
            if (alias.isEmpty()) {
                throw new SqlTemplateException(STR."Alias not found for \{type.getSimpleName()}");
            }
            return alias;
        }
        String p = toPathString(path);
        if (!path.isEmpty()) {
            RecordComponent lastComponent = path.getLast();
            if (REFLECTION.isAnnotationPresent(lastComponent, FK.class)) {
                // Path is implicit. First try the path, if not found, try without the path.
                var resolvedAlias = aliasMapper.findAlias(type, p, INNER);
                if (resolvedAlias.isPresent()) {
                    return resolvedAlias.get();
                }
                return aliasMapper.findAlias(type, null, INNER)
                        .orElseThrow(() -> new SqlTemplateException(STR."Table \{type.getSimpleName()} for column not found at \{p}."));
            }
            if (REFLECTION.isAnnotationPresent(lastComponent, PK.class)) {
                return alias;
            }
            if (getORMConverter(lastComponent).isEmpty()
                    && !aliasMapper.exists(type, INNER)) { // Check needed for records without annotations.
                return alias; // @Inline is implicitly assumed.
            }
        }
        // Fallback for records without annotations.
        return aliasMapper.getAlias(root(type), INNER, dialect, () ->
                new SqlTemplateException(STR."Table \{type.getSimpleName()} for column not found."));
    }

    /**
     * Resolves the path from the root to the target record type.
     *
     * @param root the root record type.
     * @param target the target record type.
     * @return the path from the root to the target record type.
     * @throws SqlTemplateException if the path is ambiguous.
     */
    private static @Nullable List<RecordComponent> resolvePath(@Nonnull Class<? extends Record> root,
                                                               @Nonnull Class<? extends Record> target) throws SqlTemplateException{
        List<RecordComponent> path = new ArrayList<>();
        List<RecordComponent> searchPath = new ArrayList<>(); // Temporary path for exploration.
        int pathsFound = resolvePath(root, target, searchPath, path, 0);
        if (pathsFound == 0) {
            return null;
        } else if (pathsFound > 1) {
            throw new SqlTemplateException(STR."Multiple paths to the target \{target.getSimpleName()} found in \{root.getSimpleName()}.");
        }
        return path;
    }

    /**
     * Resolves the path from the current record type to the target record type.
     *
     * @param current the current record type.
     * @param target the target record type.
     * @param searchPath the current search path.
     * @param path the resolved path.
     * @param pathsFound the number of paths found.
     * @return the number of paths found.
     */
    private static int resolvePath(@Nonnull Class<? extends Record> current,
                                   @Nonnull Class<? extends Record> target,
                                   @Nonnull List<RecordComponent> searchPath,
                                   @Nonnull List<RecordComponent> path,
                                   int pathsFound) {
        if (current == target) {
            if (pathsFound == 0) {
                path.clear();
                path.addAll(searchPath);
            }
            return pathsFound + 1;
        }
        for (RecordComponent component : current.getRecordComponents()) {
            Class<?> componentType = component.getType();
            if (componentType.isRecord() && REFLECTION.isAnnotationPresent(component, FK.class)) {
                searchPath.add(component);
                //noinspection unchecked
                pathsFound = resolvePath((Class<? extends Record>) componentType, target, searchPath, path, pathsFound);
                if (pathsFound > 1) {
                    return pathsFound; // Early return if multiple paths are found.
                }
                searchPath.removeLast();
            } else if (componentType == target) {
                searchPath.add(component);
                pathsFound++;
                if (pathsFound == 1) {
                    path.clear();
                    path.addAll(searchPath);
                }
                searchPath.removeLast();
                if (pathsFound > 1) {
                    return pathsFound;
                }
            }
        }
        return pathsFound;
    }
}
