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
import st.orm.DbColumn;
import st.orm.DbColumns;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.Version;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.config.ColumnNameResolver;
import st.orm.config.ForeignKeyResolver;
import st.orm.core.template.SqlTemplateException;
import st.orm.config.TableNameResolver;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.util.Optional.empty;
import static java.util.function.Predicate.not;
import static st.orm.core.spi.Providers.getORMConverter;

/**
 * Helper class for record reflection.
 */
@SuppressWarnings("ALL")
final class RecordReflection {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();
    private static final ConcurrentHashMap<Class<?>, List<RecordComponent>> RECORD_COMPONENT_CACHE
            = new ConcurrentHashMap<>();

    private RecordReflection() {
    }

    /**
     * Returns the record components for the specified record type. The result is cached to avoid repeated expensive
     * reflection lookups.
     *
     * @param recordType the record type to obtain the record components for.
     * @return the record components for the specified record type.
     * @throws IllegalArgumentException if the record type is not a record.
     */
    public static List<RecordComponent> getRecordComponents(@Nonnull Class<?> recordType) {
        return RECORD_COMPONENT_CACHE.computeIfAbsent(recordType, ignore -> {
            if (!recordType.isRecord()) {
                throw new IllegalArgumentException("The specified class %s is not a record type.".formatted(recordType.getName()));
            }
            return List.of(recordType.getRecordComponents());
        });
    }

    /**
     * Looks up the record component in the given table, taking the {@code component} path into account.
     */
    public static RecordComponent getRecordComponent(@Nonnull Class<? extends Record> table,
                                                     @Nonnull String path) throws SqlTemplateException {
        if (path.isEmpty()) {
            throw new SqlTemplateException("Empty component path specified.");
        }
        // Split on '.' to handle nested components (e.g., "x.y.z").
        String[] parts = path.split("\\.");
        Class<? extends Record> currentRecordClass = table;
        RecordComponent foundComponent = null;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            // Get record components for the current record class.
            foundComponent = null;
            for (RecordComponent c : getRecordComponents(currentRecordClass)) {
                if (c.getName().equals(part)) {
                    foundComponent = c;
                    break;
                }
            }
            if (foundComponent == null) {
                throw new SqlTemplateException("No component named '%s' found in record %s.".formatted(part, currentRecordClass.getName()));
            }
            // If there's still a next part to search, update currentRecordClass if possible.
            boolean hasNextPart = (i < parts.length - 1);
            if (hasNextPart) {
                // The type of the found component must be another record to continue drilling down.
                if (Record.class.isAssignableFrom(foundComponent.getType())) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Record> nextRecordClass = (Class<? extends Record>) foundComponent.getType();
                    currentRecordClass = nextRecordClass;
                } else {
                    throw new SqlTemplateException("Component '%s' in record %s is not a record, but further components were specified: '%s'.".formatted(part, currentRecordClass.getName(), path));
                }
            }
        }
        return foundComponent;
    }

    /**
     * Returns the primary key component for the specified table. If the table has a compound primary key, represented
     * by a record, the primary key component is that record itself.
     *
     * <p><strong>Note:</strong> PKs must always be present at the top-level of the record. They would not be recognized
     * if they're part of inlined records.</p>
     *
     * @param table the table to obtain the primary key component for.
     * @return the primary key component for the specified table.
     */
    static Optional<RecordComponent> getPkComponent(@Nonnull Class<? extends Record> table) {
        return RecordReflection.getRecordComponents(table).stream()
                .filter(c -> REFLECTION.isAnnotationPresent(c, PK.class))
                .findFirst();
    }

    /**
     * Returns the primary key components for the specified table. If the table has a compound primary key, represented
     * by a record, the primary key components are the record components of the record.
     *
     * <p><strong>Note:</strong> PKs must always be present at the top-level of the record. They would not be recognized
     * if they're part of inlined records.</p>
     *
     * @param table the table to obtain the primary key components for.
     */
    static Stream<RecordComponent> getNestedPkComponents(@Nonnull Class<? extends Record> table) {
        var pkComponent = getPkComponent(table).orElse(null);
        if (pkComponent == null) {
            return Stream.of();
        }
        if (!pkComponent.getType().isRecord()) {
            return Stream.of(pkComponent);
        }
        return RecordReflection.getRecordComponents(pkComponent.getType()).stream();
    }

    @SuppressWarnings("unchecked")
    static Stream<RecordComponent> getFkComponents(@Nonnull Class<? extends Record> table) {
        return RecordReflection.getRecordComponents(table).stream()
                .flatMap(c -> {
                    if (REFLECTION.isAnnotationPresent(c, FK.class)) {
                        return Stream.of(c);
                    }
                    if (c.getType().isRecord() && getORMConverter(c).isEmpty()) {
                        return getFkComponents((Class<? extends Record>) c.getType());
                    }
                    return Stream.empty();
                });
    }

    /**
     * Returns the version component for the specified table. The version component is a record component that is
     * annotated with the {@link Version} annotation.
     *
     * @param table the table to obtain the version component for.
     * @return optional with the component that specified the Version annotation, or an empty if none found.
     */
    static Optional<RecordComponent> getVersionComponent(@Nonnull Class<? extends Record> table) {
        for (var component : RecordReflection.getRecordComponents(table)) {
            if (REFLECTION.isAnnotationPresent(component, Version.class)) {
                return Optional.of(component);
            }
            if (component.getType().isRecord()
                    && !REFLECTION.isAnnotationPresent(component, FK.class)
                    && getORMConverter(component).isEmpty()) {
                var versionComponent = getVersionComponent((Class<? extends Record>) component.getType());
                if (versionComponent.isPresent()) {
                    return versionComponent;
                }
            }
        }
        return Optional.empty();
    }

    static boolean isAutoGenerated(@Nonnull RecordComponent component) {
        PK pk = REFLECTION.getAnnotation(component, PK.class);
        return pk != null
                && pk.autoGenerated()
                && !component.getType().isRecord()                          // Record PKs are not auto-generated.
                && !REFLECTION.isAnnotationPresent(component, FK.class);    // PKs that are also FKs are not auto-generated.
    }

    static boolean isTypePresent(@Nonnull Class<? extends Record> source,
                                 @Nonnull Class<? extends Record> target) throws SqlTemplateException {
        if (target.equals(source)) {
            return true;
        }
        return findComponent(RecordReflection.getRecordComponents(source), target).isPresent();
    }

    static Optional<RecordComponent> findComponent(@Nonnull List<RecordComponent> components,
                                                   @Nonnull Class<? extends Record> table) throws SqlTemplateException {
        for (var component : components) {
            if (component.getType().equals(table)
                    || (Ref.class.isAssignableFrom(component.getType()) && getRefRecordType(component).equals(table))) {
                return Optional.of(component);
            }
        }
        return empty();
    }

    // Use RecordComponentKey as key as multiple new instances of the same RecordComponent are created, which return
    // false for equals and hashCode.
    record RecordComponentKey(Class<? extends Record> recordType, String name) {
        RecordComponentKey(@Nonnull RecordComponent component) {
            //noinspection unchecked
            this((Class<? extends Record>) component.getDeclaringRecord(), component.getName());
        }
    }
    private static final java.util.Map<RecordComponentKey, Class<?>> REF_PK_TYPE_CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    static Class<?> getRefPkType(@Nonnull RecordComponent component) throws SqlTemplateException {
        try {
            return REF_PK_TYPE_CACHE.computeIfAbsent(new RecordComponentKey(component), ignore -> {
                try {
                    var type = component.getGenericType();
                    if (type instanceof ParameterizedType parameterizedType) {
                        Type supplied = parameterizedType.getActualTypeArguments()[0];
                        if (supplied instanceof Class<?> c && c.isRecord()) {
                            return REFLECTION.findPKType((Class<? extends Record>) c)
                                    .orElseThrow(() -> new SqlTemplateException("Primary key not found for entity: %s.".formatted(c.getSimpleName())));
                        }
                    }
                    throw new SqlTemplateException("Ref component must specify an entity: %s.".formatted(component.getType().getSimpleName()));
                } catch (SqlTemplateException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            throw (SqlTemplateException) e.getCause();
        }
    }

    private static final Map<RecordComponentKey, Class<? extends Record>> REF_RECORD_TYPE_CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    static Class<? extends Record> getRefRecordType(@Nonnull RecordComponent component) throws SqlTemplateException {
        try {
            return REF_RECORD_TYPE_CACHE.computeIfAbsent(new RecordComponentKey(component), ignore -> {
                try {
                    Class<? extends Record> recordType = null;
                    var type = component.getGenericType();
                    if (type instanceof ParameterizedType parameterizedType) {
                        Type supplied = parameterizedType.getActualTypeArguments()[0];
                        if (supplied instanceof Class<?> c && c.isRecord()) {
                            recordType = (Class<? extends Record>) c;
                        }
                    }
                    if (!Entity.class.isAssignableFrom(component.getType()) && recordType == null) {
                        throw new SqlTemplateException("Ref component must specify an entity: %s.".formatted(component.getType().getSimpleName()));
                    }
                    if (recordType == null) {
                        throw new SqlTemplateException("Ref component must be a record: %s.".formatted(component.getType().getSimpleName()));
                    }
                    return recordType;
                } catch (SqlTemplateException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            throw (SqlTemplateException) e.getCause();
        }
    }

    /**
     * Returns the table name for the specified record type taking the table name resolver into account, if present.
     *
     * @param table the record type to obtain the table name for.
     * @param tableNameResolver the table name resolver.
     * @return the table name for the specified record type.
     */
    static TableName getTableName(@Nonnull Class<? extends Record> table,
                                  @Nonnull TableNameResolver tableNameResolver) throws SqlTemplateException {
        String tableName = null;
        DbTable dbTable = REFLECTION.getAnnotation(table, DbTable.class);
        if (dbTable != null) {
            var tableNames = Stream.of(dbTable.name(), dbTable.value())
                    .filter(not(String::isEmpty))
                    .distinct()
                    .toList();
            if (tableNames.size() > 1) {
                throw new PersistenceException("Multiple table names found for %s.".formatted(table.getSimpleName()));
            }
            if (!tableNames.isEmpty()) {
                tableName = tableNames.getFirst();
            }
        }
        if (tableName == null) {
            tableName = tableNameResolver.resolveTableName(table);
        }
        if (dbTable != null) {
            if (!dbTable.schema().isEmpty()) {
                return new TableName(tableName, dbTable.schema(), dbTable.escape());
            }
            return new TableName(tableName, "", dbTable.escape());
        }
        return new TableName(tableName, "", false);
    }

    /**
     * Combines the specified column value and name into a stream of lists. If the value is empty, only the name is
     * returned. If the name is empty, only the value is returned. If both are non-empty, an exception is thrown.
     *
     * @param value the column value.“
     * @param name the column name.
     * @return a stream of lists containing the column value and/or name.
     * @throws IllegalArgumentException if the name is different from the value.
     */
    private static Optional<String> combine(@Nonnull String value, @Nonnull String name) {
        if (!value.isEmpty()) {
            if (!name.isEmpty() && !name.equals(value)) {
                throw new IllegalArgumentException("Column name '%s' cannot be different from the column value '%s'.".formatted(name, value));
            }
            return Optional.of(value);
        }
        if (!name.isEmpty()) {
            return Optional.of(name);
        }
        return empty();
    }

    /**
     * Returns the column name(s) for the specified {@link DbColumn} or {@link DbColumns} annotation.
     *
     * @param dbColumns the {@link DbColumn} or {@link DbColumns} annotation to obtain the column name(s) for.
     * @return the column name(s) for the specified {@link DbColumn} or {@link DbColumns} annotation.
     * @throws IllegalArgumentException if any of the annotations is invalid.
     */
    private static Stream<List<ColumnName>> columnNames(DbColumn[] dbColumns) {
        return Stream.of(Arrays.stream(dbColumns)
                .map(dbColumn -> combine(dbColumn.name(), dbColumn.value())
                        .map(name -> new ColumnName(name, dbColumn.escape()))
                        .orElseThrow(() -> new IllegalArgumentException("Column name cannot be empty."))).toList());
    }

    private static final List<Class<? extends Annotation>> COLUMN_ANNOTATIONS = List.of(PK.class, FK.class, DbColumn.class);

    /**
     * Returns the column name for the specified record component taking the column name resolver into account,
     * if present.
     *
     * @param component the record component to obtain the column name for.
     * @param columnNameResolver the column name resolver.
     * @return the column name for the specified record component.
     */
    static ColumnName getColumnName(@Nonnull RecordComponent component,
                                    @Nonnull ColumnNameResolver columnNameResolver) throws SqlTemplateException {
        List<ColumnName> names = getColumnNames(component, COLUMN_ANNOTATIONS);
        if (names.size() == 1) {
            return names.getFirst();
        }
        if (names.size() > 1) {
            throw new SqlTemplateException("Multiple column names found for %s.%s: %s.".formatted(component.getDeclaringRecord().getSimpleName(), component.getName(), names));
        }
        DbColumn dbColumn = REFLECTION.getAnnotation(component, DbColumn.class);
        return new ColumnName(columnNameResolver.resolveColumnName(component), dbColumn != null && dbColumn.escape());
    }

    /**
     * Returns the column name(s) for the specified record component using the component's annotations.
     *
     * @param component the record component to obtain the column name(s) for.
     * @param annotationTypes the column name annotations to consider.
     * @return the column name(s) for the specified record component.
     * @throws SqlTemplateException if zero, or multiple names are found for the component.
     */
    private static List<ColumnName> getColumnNames(@Nonnull RecordComponent component,
                                                   @Nonnull List<Class<? extends Annotation>> annotationTypes)
            throws SqlTemplateException {
        try {
            var columNameLists = annotationTypes.stream()
                    .map(c -> REFLECTION.getAnnotation(component, c))
                    .filter(Objects::nonNull)
                    .flatMap(RecordReflection::getColumnNames)
                    .distinct()
                    .toList();
            if (columNameLists.isEmpty()) {
                return List.of();
            }
            if (columNameLists.size() > 1) {
                throw new SqlTemplateException("Multiple column names found for %s.%s: %s.".formatted(component.getDeclaringRecord().getSimpleName(), component.getName(), columNameLists));
            }
            return columNameLists.getFirst();
        } catch (IllegalArgumentException e) {
            throw new SqlTemplateException(e);
        }
    }

    /**
     * Returns the column name(s) for as specified by the {@code annotation}.
     *
     * @param annotation annotation to obtain the column name(s) for.
     * @return the column name(s) for as specified by the {@code annotation}.
     * @throws IllegalArgumentException if the annotation is invalid.
     */
    private static Stream<List<ColumnName>> getColumnNames(@Nonnull Annotation annotation) {
        return switch (annotation) {
            case PK pk -> combine(pk.value(), pk.name()).map(ColumnName::new).stream().map(name -> List.of(name));
            case FK fk -> combine(fk.value(), fk.name()).map(ColumnName::new).stream().map(name -> List.of(name));
            case DbColumn dbColumn -> columnNames(new DbColumn[]{dbColumn});
            case DbColumns dbColumns -> columnNames(dbColumns.value());
            default -> throw new IllegalArgumentException("Unsupported annotation: %s.".formatted(annotation));
        };
    }

    private static final List<Class<? extends Annotation>> PK_COLUMN_ANNOTATIONS = List.of(PK.class, DbColumn.class, DbColumns.class);

    /**
     * Returns the column name(s) for the specified primary key component.
     *
     * @param component the record component to obtain the primary key column name(s) for.
     * @return the column name for the specified record component(s).
     */
    static List<ColumnName> getPrimaryKeys(@Nonnull RecordComponent component,
                                           @Nonnull ColumnNameResolver columnNameResolver) throws SqlTemplateException {
        var columnNames = getColumnNames(component, PK_COLUMN_ANNOTATIONS);
        if (!columnNames.isEmpty()) {
            return columnNames;
        }
        DbColumn[] dbColumns = REFLECTION.getAnnotations(component, DbColumn.class);
        if (component.getType().isRecord()) {
            columnNames = new ArrayList<>();
            var pkComponents = RecordReflection.getRecordComponents(component.getType());
            for (int i = 0; i < pkComponents.size(); i++) {
                var pkComponent = pkComponents.get(i);
                DbColumn nestedDbColumn = i < dbColumns.length
                        ? dbColumns[i]
                        : REFLECTION.getAnnotation(pkComponent, DbColumn.class);    // Top level is prioritized over nested.
                String name = columnNameResolver.resolveColumnName(pkComponent);
                columnNames.add(new ColumnName(name, nestedDbColumn != null && nestedDbColumn.escape()));
            }
        } else {
            DbColumn dbColumn = dbColumns.length > 0 ? dbColumns[0] : null;
            String name = columnNameResolver.resolveColumnName(component);
            columnNames = List.of(new ColumnName(name, dbColumn != null && dbColumn.escape()));
        }
        return columnNames;
    }

    private static final List<Class<? extends Annotation>> FK_COLUMN_ANNOTATIONS = List.of(FK.class, DbColumn.class, DbColumns.class);

    /**
     * Returns the column name(s) for the specified foreign key component taking the column name resolver into account,
     * if present.
     *
     * @param component the record component to obtain the foreign key column name(s) for.
     * @param foreignKeyResolver the foreign key resolver.
     * @return the column name for the specified record component(s).
     */
    @SuppressWarnings("unchecked")
    static List<ColumnName> getForeignKeys(@Nonnull RecordComponent component,
                                           @Nonnull ForeignKeyResolver foreignKeyResolver,
                                           @Nonnull ColumnNameResolver columnNameResolver) throws SqlTemplateException {
        var columnNames = getColumnNames(component, FK_COLUMN_ANNOTATIONS);
        if (!columnNames.isEmpty()) {
            return columnNames;
        }
        Class<? extends Record> recordType = Ref.class.isAssignableFrom(component.getType())
                ? getRefRecordType(component)
                : (Class<? extends Record>) component.getType();
        DbColumn[] dbColumns = REFLECTION.getAnnotations(component, DbColumn.class);
        List<RecordComponent> pkComponents = getNestedPkComponents(recordType).toList();
        if (pkComponents.size() == 1) {
            // If there is only one PK component, use the column name of the FK component.
            DbColumn dbColumn = dbColumns.length > 0
                    ? dbColumns[0]
                    : REFLECTION.getAnnotation(pkComponents.getFirst(), DbColumn.class);
            String name = foreignKeyResolver.resolveColumnName(component, recordType);
            return List.of(new ColumnName(name, dbColumn != null && dbColumn.escape()));
        }
        columnNames = new ArrayList<>(pkComponents.size());
        for (int i = 0; i < pkComponents.size(); i++) {
            var pkComponent = pkComponents.get(i);
            DbColumn nestedDbColumn = i < dbColumns.length
                    ? dbColumns[i]
                    : REFLECTION.getAnnotation(pkComponent, DbColumn.class); // Top-level prioritized
            String name = columnNameResolver.resolveColumnName(pkComponent);
            columnNames.add(new ColumnName(name, nestedDbColumn != null && nestedDbColumn.escape()));
        }
        return columnNames;
    }

    static void mapForeignKeys(@Nonnull TableMapper tableMapper,
                               @Nonnull String alias,
                               @Nonnull Class<? extends Record> rootTable,
                               @Nonnull Class<? extends Record> table,
                               @Nullable String path)
            throws SqlTemplateException {
        for (var component : RecordReflection.getRecordComponents(table)) {
            if (REFLECTION.isAnnotationPresent(component, FK.class)) {
                if (Ref.class.isAssignableFrom(component.getType())) {
                    tableMapper.mapForeignKey(table, getRefRecordType(component), alias, component, rootTable, path);
                } else {
                    if (!component.getType().isRecord()) {
                        throw new SqlTemplateException("FK annotation is only allowed on record types: %s.".formatted(component.getType().getSimpleName()));
                    }
                    //noinspection unchecked
                    Class<? extends Record> componentType = (Class<? extends Record>) component.getType();
                    tableMapper.mapForeignKey(table, componentType, alias, component, rootTable, path);
                }
            }
        }
    }
}
