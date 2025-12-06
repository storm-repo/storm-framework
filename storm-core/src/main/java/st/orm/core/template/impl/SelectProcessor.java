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
import st.orm.FK;
import st.orm.Metamodel;
import st.orm.Ref;
import st.orm.PK;
import st.orm.SelectMode;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.core.template.SqlDialect;
import st.orm.core.template.SqlTemplate;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.impl.Elements.Select;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;

import java.util.ArrayList;
import java.util.List;

import static java.lang.String.join;
import static java.util.List.copyOf;
import static st.orm.ResolveScope.INNER;
import static st.orm.core.spi.Providers.getORMConverter;
import static st.orm.core.template.impl.RecordReflection.getColumnName;
import static st.orm.core.template.impl.RecordReflection.getForeignKeys;
import static st.orm.core.template.impl.RecordReflection.getRecordType;
import static st.orm.core.template.impl.RecordReflection.isRecord;
import static st.orm.core.template.impl.SqlTemplateImpl.toPathString;

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
        List<RecordField> path = null;
        String pathString = null;
        if (primaryTable != null) {
            List<RecordField> pathList = resolvePath(getRecordType(primaryTable.table()), getRecordType(select.table()));
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
        var columns = getColumnsString(getRecordType(select.table()), select.mode(), path, aliasMapper,
                primaryTable == null ? null : getRecordType(primaryTable.table()), alias, false);
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

    private void checkAlias(@Nonnull String alias, @Nonnull RecordType type) throws SqlTemplateException {
        if (alias.isEmpty()) {
            throw new SqlTemplateException("Table %s not found in table graph.".formatted(type.type().getSimpleName()));
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
    private List<String> getColumnsString(@Nonnull RecordType type,
                                          @Nonnull SelectMode mode,
                                          @Nullable List<RecordField> path,
                                          @Nonnull AliasMapper aliasMapper,
                                          @Nullable RecordType primaryTable,
                                          @Nonnull String alias,
                                          boolean isPk) throws SqlTemplateException {
        var columns = new ArrayList<String>();
        for (var field : type.fields()) {
            boolean pk = isPk || field.isAnnotationPresent(PK.class);
            boolean fk = field.isAnnotationPresent(FK.class);
            boolean ref = Ref.class.isAssignableFrom(field.type());
            var converter = getORMConverter(field).orElse(null);
            if (converter != null) {
                if (mode != SelectMode.PK) {
                    checkAlias(alias, type);
                    converter.getColumns(f -> getColumnName(f, template.columnNameResolver())).forEach(name -> {
                        columns.add(dialectTemplate.process("\0.\0", alias, name));
                        columns.add(", ");
                    });
                }
            } else if (fk && (mode != SelectMode.NESTED || ref)) {   // Use foreign key column if not nested or if ref.
                if (mode != SelectMode.PK) {
                    checkAlias(alias, type);
                    getForeignKeys(field, template.foreignKeyResolver(), template.columnNameResolver()).forEach(name -> {
                        columns.add(dialectTemplate.process("\0.\0", alias, name));
                        columns.add(", ");
                    });
                }
            } else if (isRecord(field.type())) {
                if (mode != SelectMode.PK || pk) {
                    RecordType fieldType = getRecordType(field.type());
                    List<RecordField> newPath;
                    if (path != null) {
                        newPath = new ArrayList<>(path);
                        newPath.add(field);
                        newPath = copyOf(newPath);
                    } else if (primaryTable != null && primaryTable.type() == fieldType.type()) {
                        newPath = List.of();
                    } else {
                        newPath = null;
                    }
                    columns.addAll(getColumnsString(fieldType, mode, newPath, aliasMapper, primaryTable,
                            getAlias(fieldType, newPath, alias, aliasMapper, template.dialect()), pk));
                }
            } else {
                if (ref) {
                    throw new SqlTemplateException("Ref component '%s.%s' is not a foreign key.".formatted(type.type().getSimpleName(), field.name()));
                }
                if (mode != SelectMode.PK || pk) {
                    checkAlias(alias, type);
                    var name = getColumnName(field, template.columnNameResolver());
                    columns.add(dialectTemplate.process("\0.\0", alias, name));
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
    private static String getAlias(@Nonnull RecordType type,
                                   @Nullable List<RecordField> path,
                                   @Nonnull String alias,
                                   @Nonnull AliasMapper aliasMapper,
                                   @Nonnull SqlDialect dialect) throws SqlTemplateException {
        if (!type.isDataType()) {
            return alias;
        }
        if (path == null) {
            var result = aliasMapper.findAlias(type.requireDataType(), null, INNER);
            if (result.isPresent()) {
                return result.get();
            }
            if (alias.isEmpty()) {
                throw new SqlTemplateException("Alias not found for %s".formatted(type.type().getSimpleName()));
            }
            return alias;
        }
        String p = toPathString(path);
        if (!path.isEmpty()) {
            RecordField lastField = path.getLast();
            if (lastField.isAnnotationPresent(FK.class)) {
                // Path is implicit. First try the path, if not found, try without the path.
                var resolvedAlias = aliasMapper.findAlias(type.requireDataType(), p, INNER);
                if (resolvedAlias.isPresent()) {
                    return resolvedAlias.get();
                }
                return aliasMapper.findAlias(type.requireDataType(), null, INNER)
                        .orElseThrow(() -> new SqlTemplateException("Table %s for column not found at %s.".formatted(type.type().getSimpleName(), p)));
            }
            if (lastField.isAnnotationPresent(PK.class)) {
                return alias;
            }
            if (getORMConverter(lastField).isEmpty()
                    && !aliasMapper.exists(type.requireDataType(), INNER)) { // Check needed for records without annotations.
                return alias; // @Inline is implicitly assumed.
            }
        }
        // Fallback for records without annotations.
        return aliasMapper.getAlias(Metamodel.root(type.type()), INNER, dialect, () ->
                new SqlTemplateException("Table %s for column not found.".formatted(type.type().getSimpleName())));
    }

    /**
     * Resolves the path from the root to the target record type.
     *
     * @param root the root record type.
     * @param target the target record type.
     * @return the path from the root to the target record type.
     * @throws SqlTemplateException if the path is ambiguous.
     */
    private static @Nullable List<RecordField> resolvePath(@Nonnull RecordType root,
                                                           @Nonnull RecordType target) throws SqlTemplateException{
        List<RecordField> path = new ArrayList<>();
        List<RecordField> searchPath = new ArrayList<>(); // Temporary path for exploration.
        int pathsFound = resolvePath(root, target, searchPath, path, 0);
        if (pathsFound == 0) {
            return null;
        } else if (pathsFound > 1) {
            throw new SqlTemplateException("Multiple paths to the target %s found in %s.".formatted(target.type().getSimpleName(), root.type().getSimpleName()));
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
    private static int resolvePath(@Nonnull RecordType current,
                                   @Nonnull RecordType target,
                                   @Nonnull List<RecordField> searchPath,
                                   @Nonnull List<RecordField> path,
                                   int pathsFound) {
        if (current.type() == target.type()) {
            if (pathsFound == 0) {
                path.clear();
                path.addAll(searchPath);
            }
            return pathsFound + 1;
        }
        for (RecordField field : current.fields()) {
            if (!isRecord(field.type())) {
                continue;
            }
            RecordType fieldType = getRecordType(field.type());
            if (field.isAnnotationPresent(FK.class)) {
                searchPath.add(field);
                pathsFound = resolvePath(fieldType, target, searchPath, path, pathsFound);
                if (pathsFound > 1) {
                    return pathsFound; // Early return if multiple paths are found.
                }
                searchPath.removeLast();
            } else if (fieldType.type() == target.type()) {
                searchPath.add(field);
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
