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
import st.orm.DbColumn;
import st.orm.DbTable;
import st.orm.FK;
import st.orm.Lazy;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.repository.Entity;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.ColumnNameResolver;
import st.orm.template.ForeignKeyResolver;
import st.orm.template.SqlTemplateException;
import st.orm.template.TableNameResolver;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.util.Optional.empty;
import static java.util.function.Predicate.not;
import static st.orm.spi.Providers.getORMConverter;

/**
 * Helper class for record reflection.
 */
final class RecordReflection {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private RecordReflection() {
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
            RecordComponent[] components = currentRecordClass.getRecordComponents();
            foundComponent = null;
            for (RecordComponent c : components) {
                if (c.getName().equals(part)) {
                    foundComponent = c;
                    break;
                }
            }
            if (foundComponent == null) {
                throw new SqlTemplateException(STR."No component named '\{part}' found in record \{currentRecordClass.getName()}.");
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
                    throw new SqlTemplateException(STR."Component '\{part}' in record \{currentRecordClass.getName()} is not a record, but further components were specified: '\{path}'.");
                }
            }
        }
        return foundComponent;
    }

    @SuppressWarnings("unchecked")
    static Stream<RecordComponent> getPkComponents(@Nonnull Class<? extends Record> table) {
        return Stream.of(table.getRecordComponents())
                .flatMap(c -> {
                    if (REFLECTION.isAnnotationPresent(c, PK.class)) {
                        return Stream.of(c);
                    }
                    if (c.getType().isRecord() && getORMConverter(c).isEmpty()
                            && !REFLECTION.isAnnotationPresent(c, FK.class)) {
                        return getPkComponents((Class<? extends Record>) c.getType());
                    }
                    return Stream.empty();
                });
    }

    @SuppressWarnings("unchecked")
    static Stream<RecordComponent> getFkComponents(@Nonnull Class<? extends Record> table) {
        return Stream.of(table.getRecordComponents())
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
        return findComponent(List.of(source.getRecordComponents()), target).isPresent();
    }

    static Optional<RecordComponent> findComponent(@Nonnull List<RecordComponent> components,
                                                   @Nonnull Class<? extends Record> table) throws SqlTemplateException {
        for (var component : components) {
            if (component.getType().equals(table)
                    || (Lazy.class.isAssignableFrom(component.getType()) && getLazyRecordType(component).equals(table))) {
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
    private static final java.util.Map<RecordComponentKey, Class<?>> LAZY_PK_TYPE_CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    static Class<?> getLazyPkType(@Nonnull RecordComponent component) throws SqlTemplateException {
        try {
            return LAZY_PK_TYPE_CACHE.computeIfAbsent(new RecordComponentKey(component), _ -> {
                try {
                    var type = component.getGenericType();
                    if (type instanceof ParameterizedType parameterizedType) {
                        Type supplied = parameterizedType.getActualTypeArguments()[0];
                        if (supplied instanceof Class<?> c && c.isRecord()) {
                            return REFLECTION.findPKType((Class<? extends Record>) c)
                                    .orElseThrow(() -> new SqlTemplateException(STR."Primary key not found for entity: \{c.getSimpleName()}."));
                        }
                    }
                    throw new SqlTemplateException(STR."Lazy component must specify an entity: \{component.getType().getSimpleName()}.");
                } catch (SqlTemplateException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            throw (SqlTemplateException) e.getCause();
        }
    }

    private static final Map<RecordComponentKey, Class<? extends Record>> LAZY_RECORD_TYPE_CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    static Class<? extends Record> getLazyRecordType(@Nonnull RecordComponent component) throws SqlTemplateException {
        try {
            return LAZY_RECORD_TYPE_CACHE.computeIfAbsent(new RecordComponentKey(component), _ -> {
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
                        throw new SqlTemplateException(STR."Lazy component must specify an entity: \{component.getType().getSimpleName()}.");
                    }
                    if (recordType == null) {
                        throw new SqlTemplateException(STR."Lazy component must be a record: \{component.getType().getSimpleName()}.");
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
                throw new PersistenceException(STR."Multiple table names found for \{table.getSimpleName()}.");
            }
            if (!tableNames.isEmpty()) {
                tableName = tableNames.getFirst();
            }
        }
        if (tableName == null) {
            try {
                tableName = tableNameResolver.resolveTableName(table);
            } catch (SqlTemplateException e) {
                throw new PersistenceException(e);
            }
        }
        if (dbTable != null) {
            if (!dbTable.schema().isEmpty()) {
                return new TableName(tableName, dbTable.schema(), dbTable.escape());
            }
            return new TableName(tableName, "", dbTable.escape());
        }
        return new TableName(tableName, "", false);
    }

    private static Stream<String> getColumnNames(@Nonnull Annotation annotation) {
        return switch (annotation) {
            case PK pk -> Stream.of(pk.name(), pk.value());
            case FK fk -> Stream.of(fk.name(), fk.value());
            case DbColumn dbColumn -> Stream.of(dbColumn.name(), dbColumn.value());
            default -> throw new IllegalArgumentException(STR."Unsupported annotation: \{annotation}.");
        };
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
    static ColumnName getColumnName(@Nonnull RecordComponent component, @Nonnull ColumnNameResolver columnNameResolver)
            throws SqlTemplateException {
        DbColumn dbColumn = REFLECTION.getAnnotation(component, DbColumn.class);
        String name = getPureColumnName(component, COLUMN_ANNOTATIONS)
                .orElse(columnNameResolver.resolveColumnName(component));
        return new ColumnName(name, dbColumn != null && dbColumn.escape());
    }

    /**
     * Returns the column name for the specified record component taking the column name resolver into account,
     * if present.
     *
     * @param component the record component to obtain the column name for.
     * @param annotationTypes the column name annotations to consider.
     * @return the column name for the specified record component.
     */
    static Optional<ColumnName> getColumnName(@Nonnull RecordComponent component,
                                              @Nonnull List<Class<? extends Annotation>> annotationTypes)
            throws SqlTemplateException {
        DbColumn dbColumn = REFLECTION.getAnnotation(component, DbColumn.class);
        return getPureColumnName(component, annotationTypes)
                .map(name -> new ColumnName(name, dbColumn != null && dbColumn.escape()));
    }

    /**
     * Returns the column name for the specified record component taking the column name resolver into account,
     * if present. The column name is not escaped.
     *
     * @param component the record component to obtain the column name for.
     * @return the column name for the specified record component.
     */
    private static Optional<String> getPureColumnName(@Nonnull RecordComponent component,
                                                      @Nonnull List<Class<? extends Annotation>> annotationTypes)
            throws SqlTemplateException {
        var columNames = annotationTypes.stream()
                .map(c -> REFLECTION.getAnnotation(component, c))
                .filter(Objects::nonNull)
                .flatMap(RecordReflection::getColumnNames)
                .filter(not(String::isEmpty))
                .distinct()
                .toList();
        if (columNames.isEmpty()) {
            return Optional.empty();
        }
        if (columNames.size() > 1) {
            throw new SqlTemplateException(STR."Multiple column names found for \{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}: \{columNames}.");
        }
        return Optional.of(columNames.getFirst());
    }

    private static final List<Class<? extends Annotation>> FK_COLUMN_ANNOTATIONS = List.of(FK.class, DbColumn.class);

    /**
     * Returns the column name for the specified record component taking the column name resolver into account,
     * if present.
     *
     * @param component the record component to obtain the foreign key column name for.
     * @param foreignKeyResolver the foreign key resolver.
     * @return the column name for the specified record component.
     */
    @SuppressWarnings("unchecked")
    static ColumnName getForeignKey(@Nonnull RecordComponent component,
                                    @Nonnull ForeignKeyResolver foreignKeyResolver) throws SqlTemplateException {
        var columnName = getColumnName(component, FK_COLUMN_ANNOTATIONS);
        if (columnName.isPresent()) {
            return columnName.get();
        }
        Class<? extends Record> recordType = Lazy.class.isAssignableFrom(component.getType())
                ? getLazyRecordType(component)
                : (Class<? extends Record>) component.getType();
        DbColumn dbColumn = REFLECTION.getAnnotation(component, DbColumn.class);
        String name = foreignKeyResolver.resolveColumnName(component, recordType);
        return new ColumnName(name, dbColumn != null && dbColumn.escape());
    }

    static void mapForeignKeys(@Nonnull TableMapper tableMapper,
                               @Nonnull String alias,
                               @Nonnull Class<? extends Record> rootTable,
                               @Nonnull Class<? extends Record> table,
                               @Nullable String path)
            throws SqlTemplateException {
        for (var component : table.getRecordComponents()) {
            if (REFLECTION.isAnnotationPresent(component, FK.class)) {
                if (Lazy.class.isAssignableFrom(component.getType())) {
                    tableMapper.mapForeignKey(table, getLazyRecordType(component), alias, component, rootTable, path);
                } else {
                    if (!component.getType().isRecord()) {
                        throw new SqlTemplateException(STR."FK annotation is only allowed on record types: \{component.getType().getSimpleName()}.");
                    }
                    //noinspection unchecked
                    Class<? extends Record> componentType = (Class<? extends Record>) component.getType();
                    tableMapper.mapForeignKey(table, componentType, alias, component, rootTable, path);
                }
            }
        }
    }
}
