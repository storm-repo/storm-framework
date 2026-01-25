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
import st.orm.Data;
import st.orm.DbColumn;
import st.orm.DbColumns;
import st.orm.DbTable;
import st.orm.FK;
import st.orm.GenerationStrategy;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.Version;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.mapping.ColumnNameResolver;
import st.orm.mapping.ForeignKeyResolver;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;
import st.orm.core.template.SqlTemplateException;
import st.orm.mapping.TableNameResolver;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
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

    private RecordReflection() {
    }

    /**
     * Checks whether the specified type is a record type.
     *
     * @param type the type to check.
     * @return {@code true} if the specified type is a record type, {@code false} otherwise.
     */
    public static boolean isRecord(@Nonnull Class<?> type) {
        return REFLECTION.findRecordType(type).isPresent();
    }

    /**
     * Returns the record type for the specified type.
     *
     * @param type the type to obtain the record type for.
     * @return the record type for the specified type.
     * @throws PersistenceException if the specified type is not a record type.
     */
    public static RecordType getRecordType(@Nonnull Class<?> type) {
        return REFLECTION.getRecordType(type);
    }

    /**
     * Returns the record components for the specified record type. The result is cached to avoid repeated expensive
     * reflection lookups.
     *
     * @param recordType the record type to obtain the record components for.
     * @return the record components for the specified record type.
     * @throws PersistenceException if the record type is not a record.
     */
    public static List<RecordField> getRecordFields(@Nonnull Class<?> recordType) {
        return REFLECTION.getRecordType(recordType).fields();
    }

    /**
     * Looks up the record field in the given table, taking the {@code field} path into account.
     */
    public static RecordField getRecordField(@Nonnull Class<?> table,
                                             @Nonnull String path) throws SqlTemplateException {
        if (path.isEmpty()) {
            throw new SqlTemplateException("Empty component path specified.");
        }
        // Split on '.' to handle nested components (e.g., "x.y.z").
        String[] parts = path.split("\\.");
        RecordType type = REFLECTION.getRecordType(table);
        RecordField foundField = null;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            // Get record components for the current record class.
            foundField = null;
            for (RecordField field : type.fields()) {
                if (field.name().equals(part)) {
                    foundField = field;
                    break;
                }
            }
            if (foundField == null) {
                throw new SqlTemplateException("No field named '%s' found in record %s.".formatted(part, type.type().getName()));
            }
            // If there's still a next part to search, update type if possible.
            boolean hasNextPart = (i < parts.length - 1);
            if (hasNextPart) {
                // The type of the found field must be another record type to continue drilling down.
                var fieldType = REFLECTION.findRecordType(foundField.type()).orElse(null);
                if (fieldType != null) {
                    type = fieldType;
                } else {
                    throw new SqlTemplateException("Component '%s' in record %s is not a record, but further components were specified: '%s'.".formatted(part, type.type().getName(), path));
                }
            }
        }
        return foundField;
    }

    /**
     * Returns the primary key field for the specified table. If the table has a compound primary key, represented
     * by a record, the primary key field is that record itself.
     *
     * <p><strong>Note:</strong> PKs must always be present at the top-level of the record. They would not be recognized
     * if they're part of inlined records.</p>
     *
     * @param table the table to obtain the primary key field for.
     * @return the primary key field for the specified table.
     */
    public static Optional<RecordField> findPkField(@Nonnull Class<?> table) {
        return REFLECTION.getRecordType(table).fields().stream()
                .filter(field -> field.isAnnotationPresent(PK.class))
                .findFirst();
    }

    /**
     * Returns the primary key components for the specified table. If the primary key is a foreign key (a record with a
     * primary key), that record component is returned. If the primary key is a compound primary key (an inline record)
     * the primary key components are the record components of that inline record.
     *
     * <p><strong>Note:</strong> PKs must always be present at the top-level of the record. They would not be recognized
     * if they're part of inlined records.</p>
     *
     * @param table the table to obtain the primary key components for.
     */
    static Stream<RecordField> getNestedPkFields(@Nonnull Class<?> table) {
        var pkField = findPkField(table).orElse(null);
        if (pkField == null) {
            return Stream.of();
        }
        if (pkField.isAnnotationPresent(FK.class)) {
            // If the primary key component is also a foreign key, return the component itself.
            return Stream.of(pkField);
        }
        if (!REFLECTION.findRecordType(pkField.type()).isPresent()) {
            return Stream.of(pkField);
        }
        return RecordReflection.getRecordFields(pkField.type()).stream();
    }

    @SuppressWarnings("unchecked")
    static Stream<RecordField> getFkFields(@Nonnull Class<?> table) {
        return REFLECTION.getRecordType(table).fields().stream()
                .flatMap(field -> {
                    if (field.isAnnotationPresent(FK.class)) {
                        return Stream.of(field);
                    }
                    if (REFLECTION.findRecordType(field.type()).isPresent() && getORMConverter(field).isEmpty()) {
                        return getFkFields(field.type());
                    }
                    return Stream.empty();
                });
    }

    /**
     * Returns the version field for the specified table. The version field is a record field that is
     * annotated with the {@link Version} annotation.
     *
     * @param table the table to obtain the version field for.
     * @return optional with the field that specified the Version annotation, or an empty if none found.
     */
    static Optional<RecordField> getVersionField(@Nonnull Class<?> table) {
        for (var field : REFLECTION.getRecordType(table).fields()) {
            if (field.isAnnotationPresent(Version.class)) {
                return Optional.of(field);
            }
            if (REFLECTION.findRecordType(field.type()).isPresent()
                    && !field.isAnnotationPresent(FK.class)
                    && getORMConverter(field).isEmpty()) {
                var versionComponent = getVersionField(field.type());
                if (versionComponent.isPresent()) {
                    return versionComponent;
                }
            }
        }
        return Optional.empty();
    }

    static GenerationStrategy getGenerationStrategy(@Nonnull RecordField field) {
        PK pk = field.getAnnotation(PK.class);
        if (pk != null) {
            if (!REFLECTION.findRecordType(field.type()).isPresent() && !field.isAnnotationPresent(FK.class)) {
                return pk.generation();
            }
        }
        return GenerationStrategy.NONE;
    }

    static String getSequence(@Nonnull RecordField field) {
        PK pk = field.getAnnotation(PK.class);
        if (pk != null) {
            return pk.sequence();
        }
        return "";
    }

    static boolean isTypePresent(@Nonnull Class<?> source,
                                 @Nonnull Class<?> target) throws SqlTemplateException {
        if (target.equals(source)) {
            return true;
        }
        return findRecordField(getRecordFields(source), target).isPresent();
    }

    static Optional<RecordField> findRecordField(@Nonnull List<RecordField> fields,
                                                 @Nonnull Class<?> table) throws SqlTemplateException {
        var recordType = REFLECTION.getRecordType(table);
        for (var field : fields) {
            if (field.type() == recordType.type()
                    || (Ref.class.isAssignableFrom(field.type()) && getRefDataType(field).equals(table))) {
                return Optional.of(field);
            }
        }
        return empty();
    }

    /**
     * Represents a key for a record field.
     */
    record FieldKey(Class<?> declaringType, String name) {
        FieldKey(RecordField field) {
            this(field.declaringType(), field.name());
        }
    }
    private static final java.util.Map<FieldKey, Class<?>> REF_PK_TYPE_CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    static Class<?> getRefPkType(@Nonnull RecordField field) throws SqlTemplateException {
        try {
            return REF_PK_TYPE_CACHE.computeIfAbsent(new FieldKey(field), ignore -> {
                try {
                    var type = field.genericType();
                    if (type instanceof ParameterizedType parameterizedType) {
                        Type supplied = parameterizedType.getActualTypeArguments()[0];
                        if (supplied instanceof Class<?> c && REFLECTION.findRecordType(c).isPresent()) {
                            return RecordReflection.findPkField(c)
                                    .map(RecordField::type)
                                    .orElseThrow(() -> new SqlTemplateException("Primary key not found for entity: %s.".formatted(c.getSimpleName())));
                        }
                    }
                    throw new SqlTemplateException("Ref component must specify an entity: %s.".formatted(field.type().getSimpleName()));
                } catch (SqlTemplateException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            throw (SqlTemplateException) e.getCause();
        }
    }

    private static final Map<FieldKey, Class<? extends Data>> REF_RECORD_TYPE_CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    static Class<? extends Data> getRefDataType(@Nonnull RecordField field) throws SqlTemplateException {
        try {
            return REF_RECORD_TYPE_CACHE.computeIfAbsent(new FieldKey(field), ignore -> {
                try {
                    Class<?> recordType = null;
                    var type = field.genericType();
                    if (type instanceof ParameterizedType parameterizedType) {
                        Type supplied = parameterizedType.getActualTypeArguments()[0];
                        if (supplied instanceof Class<?> c) {
                            recordType = (Class<?>) c;
                        }
                    }
                    if (recordType == null) {
                        throw new SqlTemplateException("Ref must specify a Data type: %s.".formatted(field.type().getSimpleName()));
                    }
                    var finalRecordType = recordType;
                    REFLECTION.findRecordType(recordType)
                            .orElseThrow(() -> new SqlTemplateException("Ref must specify a record type: %s.".formatted(finalRecordType.getSimpleName())));
                    if (!Data.class.isAssignableFrom(recordType)) {
                        throw new SqlTemplateException("Ref must specify a Data type: %s.".formatted(field.type().getSimpleName()));
                    }
                    return (Class<? extends Data>) recordType;
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
    static TableName getTableName(@Nonnull Class<? extends Data> table,
                                  @Nonnull TableNameResolver tableNameResolver) throws SqlTemplateException {
        RecordType type = REFLECTION.getRecordType(table);
        String tableName = null;
        DbTable dbTable = type.getAnnotation(DbTable.class);
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
            tableName = tableNameResolver.resolveTableName(type);
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
     * Returns the column name for the specified record field taking the column name resolver into account,
     * if present.
     *
     * @param field the record field to obtain the column name for.
     * @param columnNameResolver the column name resolver.
     * @return the column name for the specified record field.
     */
    static ColumnName getColumnName(@Nonnull RecordField field,
                                    @Nonnull ColumnNameResolver columnNameResolver) throws SqlTemplateException {
        List<ColumnName> names = getColumnNames(field, COLUMN_ANNOTATIONS);
        if (names.size() == 1) {
            return names.getFirst();
        }
        if (names.size() > 1) {
            throw new SqlTemplateException("Multiple column names found for %s.%s: %s.".formatted(field.type().getSimpleName(), field.name(), names));
        }
        DbColumn dbColumn = field.getAnnotation(DbColumn.class);
        return new ColumnName(columnNameResolver.resolveColumnName(field), dbColumn != null && dbColumn.escape());
    }

    /**
     * Returns the column name(s) for the specified record component using the component's annotations.
     *
     * @param field the record field to obtain the column name(s) for.
     * @param annotationTypes the column name annotations to consider.
     * @return the column name(s) for the specified record component.
     * @throws SqlTemplateException if zero, or multiple names are found for the component.
     */
    private static List<ColumnName> getColumnNames(@Nonnull RecordField field,
                                                   @Nonnull List<Class<? extends Annotation>> annotationTypes)
            throws SqlTemplateException {
        try {
            var columNameLists = annotationTypes.stream()
                    .map(annotationType -> field.getAnnotation(annotationType))
                    .filter(Objects::nonNull)
                    .flatMap(RecordReflection::getColumnNames)
                    .distinct()
                    .toList();
            if (columNameLists.isEmpty()) {
                return List.of();
            }
            if (columNameLists.size() > 1) {
                throw new SqlTemplateException("Multiple column names found for %s.%s: %s."
                        .formatted(field.type().getSimpleName(), field.name(), columNameLists));
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
     * Returns the column name(s) for the specified primary key field.
     *
     * @param field the record field to obtain the primary key column name(s) for.
     * @return the column name for the specified record component(s).
     */
    static List<ColumnName> getPrimaryKeys(@Nonnull RecordField field,
                                           @Nonnull ForeignKeyResolver foreignKeyResolver,
                                           @Nonnull ColumnNameResolver columnNameResolver) throws SqlTemplateException {
        var columnNames = getColumnNames(field, PK_COLUMN_ANNOTATIONS);
        if (!columnNames.isEmpty()) {
            return columnNames;
        }
        if (field.isAnnotationPresent(FK.class)) {
            // If the primary key component is also a foreign key, return the foreign key column names.
            return getForeignKeys(field, foreignKeyResolver, columnNameResolver);
        }
        DbColumn[] dbColumns = field.getAnnotations(DbColumn.class);
        RecordType fieldType = REFLECTION.findRecordType(field.type()).orElse(null);
        if (fieldType != null) {
            columnNames = new ArrayList<>();
            var pkFields = fieldType.fields();
            for (int i = 0; i < pkFields.size(); i++) {
                var pkField = pkFields.get(i);
                DbColumn nestedDbColumn = i < dbColumns.length
                        ? dbColumns[i]
                        : pkField.getAnnotation(DbColumn.class);    // Top level is prioritized over nested.
                String name = columnNameResolver.resolveColumnName(pkField);
                columnNames.add(new ColumnName(name, nestedDbColumn != null && nestedDbColumn.escape()));
            }
        } else {
            DbColumn dbColumn = dbColumns.length > 0 ? dbColumns[0] : null;
            String name = columnNameResolver.resolveColumnName(field);
            columnNames = List.of(new ColumnName(name, dbColumn != null && dbColumn.escape()));
        }
        return columnNames;
    }

    private static final List<Class<? extends Annotation>> FK_COLUMN_ANNOTATIONS = List.of(FK.class, DbColumn.class, DbColumns.class);

    /**
     * Returns the column name(s) for the specified foreign key field taking the column name resolver into account,
     * if present.
     *
     * @param field the record field to obtain the foreign key column name(s) for.
     * @param foreignKeyResolver the foreign key resolver.
     * @return the column name for the specified record component(s).
     */
    @SuppressWarnings("unchecked")
    static List<ColumnName> getForeignKeys(@Nonnull RecordField field,
                                           @Nonnull ForeignKeyResolver foreignKeyResolver,
                                           @Nonnull ColumnNameResolver columnNameResolver) throws SqlTemplateException {
        var columnNames = getColumnNames(field, FK_COLUMN_ANNOTATIONS);
        if (!columnNames.isEmpty()) {
            return columnNames;
        }
        Class<?> fkType = Ref.class.isAssignableFrom(field.type())
                ? getRefDataType(field)
                : field.type();
        DbColumn[] dbColumns = field.getAnnotations(DbColumn.class);
        List<RecordField> pkFields = getNestedPkFields(fkType).toList();
        if (pkFields.size() == 1) {
            // If there is only one PK component, use the column name of the FK component.
            DbColumn dbColumn = dbColumns.length > 0
                    ? dbColumns[0]
                    : pkFields.getFirst().getAnnotation(DbColumn.class);
            String name = foreignKeyResolver.resolveColumnName(field, REFLECTION.getRecordType(fkType));
            return List.of(new ColumnName(name, dbColumn != null && dbColumn.escape()));
        }
        columnNames = new ArrayList<>(pkFields.size());
        for (int i = 0; i < pkFields.size(); i++) {
            var pkComponent = pkFields.get(i);
            DbColumn nestedDbColumn = i < dbColumns.length
                    ? dbColumns[i]
                    : pkComponent.getAnnotation(DbColumn.class); // Top-level prioritized.
            String name = columnNameResolver.resolveColumnName(pkComponent);
            columnNames.add(new ColumnName(name, nestedDbColumn != null && nestedDbColumn.escape()));
        }
        return columnNames;
    }

    static void mapForeignKeys(@Nonnull TableMapper tableMapper,
                               @Nonnull String alias,
                               @Nonnull Class<? extends Data> rootTable,
                               @Nonnull Class<? extends Data> table,
                               @Nullable String path)
            throws SqlTemplateException {
        for (var field : RecordReflection.getRecordFields(table)) {
            if (field.isAnnotationPresent(FK.class)) {
                if (Ref.class.isAssignableFrom(field.type())) {
                    tableMapper.mapForeignKey(table, getRefDataType(field), alias, field, rootTable, path);
                } else {
                    Class<?> recordType = field.type();
                    REFLECTION.findRecordType(recordType)
                            .orElseThrow(() -> new SqlTemplateException("FK annotation is only allowed on record types: %s.".formatted(field.type().getSimpleName())));
                    if (!Data.class.isAssignableFrom(recordType)) {
                        throw new SqlTemplateException("FK annotation is only allowed on Data types: %s.".formatted(field.type().getSimpleName()));
                    }
                    tableMapper.mapForeignKey(table, (Class<? extends Data>) recordType, alias, field, rootTable, path);
                }
            }
        }
    }
}
