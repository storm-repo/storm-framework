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

import static java.util.Optional.empty;
import static java.util.function.Predicate.not;
import static st.orm.core.spi.Providers.getORMConverter;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import st.orm.Data;
import st.orm.DbColumn;
import st.orm.DbColumns;
import st.orm.DbTable;
import st.orm.Discriminator;
import st.orm.Discriminator.DiscriminatorType;
import st.orm.Entity;
import st.orm.FK;
import st.orm.GenerationStrategy;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.Polymorphic;
import st.orm.Ref;
import st.orm.Version;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.core.template.SqlTemplateException;
import st.orm.mapping.ColumnNameResolver;
import st.orm.mapping.ForeignKeyResolver;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;
import st.orm.mapping.TableNameResolver;

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
        // For sealed entity interfaces, delegate to the first permitted subclass.
        if (table.isSealed() && isSealedEntity(table)) {
            Class<?>[] permitted = table.getPermittedSubclasses();
            if (permitted != null && permitted.length > 0) {
                return findPkField(permitted[0]);
            }
            return Optional.empty();
        }
        // Polymorphic FK interfaces (sealed Data, not Entity) have no PK of their own.
        if (table.isSealed() && isPolymorphicData(table)) {
            return Optional.empty();
        }
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
        if (table.isSealed()) {
            return Stream.empty();  // Sealed interfaces have no own FK fields.
        }
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
        if (table.isSealed()) {
            return Optional.empty();  // Sealed interfaces have no own version fields.
        }
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
        if (source.isSealed()) {
            return false;  // Sealed interfaces have no own fields; auto-join not applicable.
        }
        return findRecordField(getRecordFields(source), target).isPresent();
    }

    static Optional<RecordField> findRecordField(@Nonnull List<RecordField> fields,
                                                 @Nonnull Class<?> table) throws SqlTemplateException {
        for (var field : fields) {
            if (field.type() == table
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
                        if (supplied instanceof Class<?> c) {
                            // For sealed types, resolve PK from the first permitted subclass.
                            if (c.isSealed() && Data.class.isAssignableFrom(c)) {
                                Class<?>[] permitted = c.getPermittedSubclasses();
                                if (permitted != null && permitted.length > 0) {
                                    return RecordReflection.findPkField(permitted[0])
                                            .map(RecordField::type)
                                            .orElseThrow(() -> new SqlTemplateException(
                                                    "Primary key not found for permitted subclass: %s."
                                                            .formatted(permitted[0].getSimpleName())));
                                }
                            }
                            if (REFLECTION.findRecordType(c).isPresent()) {
                                return RecordReflection.findPkField(c)
                                        .map(RecordField::type)
                                        .orElseThrow(() -> new SqlTemplateException("Primary key not found for entity: %s.".formatted(c.getSimpleName())));
                            }
                        }
                    }
                    throw new SqlTemplateException("Ref component must specify a Data type: %s. The generic type parameter of Ref<T> must be a type that implements the Data interface (Entity, Projection, or Inline record).".formatted(field.type().getSimpleName()));
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
                        throw new SqlTemplateException("Ref must specify a Data type: %s. The generic type parameter of Ref<T> must be a type that implements the Data interface (Entity, Projection, or Inline record).".formatted(field.type().getSimpleName()));
                    }
                    if (!Data.class.isAssignableFrom(recordType)) {
                        throw new SqlTemplateException("Ref must specify a Data type: %s. The generic type parameter of Ref<T> must be a type that implements the Data interface (Entity, Projection, or Inline record).".formatted(field.type().getSimpleName()));
                    }
                    // Accept sealed interfaces (they are not records themselves but their subtypes are).
                    var finalRecordType = recordType;
                    if (recordType.isSealed() && detectSealedPattern(recordType).isPresent()) {
                        // Sealed hierarchy type is valid even though the interface itself is not a record.
                        return (Class<? extends Data>) recordType;
                    }
                    REFLECTION.findRecordType(recordType)
                            .orElseThrow(() -> new SqlTemplateException("Ref must specify a record type: %s.".formatted(finalRecordType.getSimpleName())));
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
        // For sealed entity interfaces, resolve table name from the @DbTable annotation on the
        // interface itself, or fall back to camelCase-to-snake_case conversion (sealed interfaces
        // aren't records and don't have a RecordType).
        if (table.isSealed() && isSealedEntity(table)) {
            DbTable dbTable = table.getAnnotation(DbTable.class);
            if (dbTable != null) {
                var tableNames = Stream.of(dbTable.name(), dbTable.value())
                        .filter(not(String::isEmpty))
                        .distinct()
                        .toList();
                if (tableNames.size() > 1) {
                    throw new PersistenceException("Multiple table names found for %s.".formatted(table.getSimpleName()));
                }
                String tableName = tableNames.isEmpty()
                        ? camelCaseToSnakeCase(table.getSimpleName())
                        : tableNames.getFirst();
                if (!dbTable.schema().isEmpty()) {
                    return new TableName(tableName, dbTable.schema(), dbTable.escape());
                }
                return new TableName(tableName, "", dbTable.escape());
            }
            // No @DbTable: use camelCase-to-snake_case conversion.
            return new TableName(camelCaseToSnakeCase(table.getSimpleName()), "", false);
        }
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
        // Handle Polymorphic FK: sealed Data interface with separate entity subtypes.
        if (isPolymorphicData(fkType)) {
            // Emit two columns: discriminator + FK value.
            String discriminatorCol = getPolymorphicDiscriminatorColumn(field);
            String fkCol = field.name() + "_id";
            return List.of(
                    new ColumnName(discriminatorCol, false),
                    new ColumnName(fkCol, false)
            );
        }
        // For sealed entity types (Single-Table/Joined), resolve PK from first permitted subclass.
        if (fkType.isSealed() && isSealedEntity(fkType)) {
            Class<?>[] permitted = fkType.getPermittedSubclasses();
            if (permitted != null && permitted.length > 0) {
                fkType = permitted[0];
            }
        }
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

    // ---- Sealed type hierarchy support ----

    /**
     * Enumeration of the polymorphic patterns supported for sealed type hierarchies.
     */
    enum SealedPattern {
        /** All subtypes in one table with discriminator column. Sealed interface is an Entity with @DbTable,
         *  subtypes do NOT have @DbTable. */
        SINGLE_TABLE,
        /** Each subtype in its own extension table (common fields + discriminator in base table).
         *  Sealed interface is an Entity with @DbTable, subtypes also have @DbTable. */
        JOINED,
        /** Sealed interface is a Data marker (not Entity). Each subtype independently has @DbTable and
         *  implements Entity. FK produces discriminator + value columns. */
        POLYMORPHIC_FK
    }

    /**
     * Cache for sealed pattern detection results.
     */
    private static final Map<Class<?>, Optional<SealedPattern>> SEALED_PATTERN_CACHE = new ConcurrentHashMap<>();

    /**
     * Detects the polymorphic pattern for the given sealed type, if any.
     *
     * @param type the type to inspect.
     * @return an Optional containing the detected SealedPattern, or empty if the type is not a sealed hierarchy.
     */
    static Optional<SealedPattern> detectSealedPattern(@Nonnull Class<?> type) {
        return SEALED_PATTERN_CACHE.computeIfAbsent(type, t -> {
            if (!t.isSealed()) {
                return Optional.empty();
            }
            Class<?>[] permitted = t.getPermittedSubclasses();
            if (permitted == null || permitted.length == 0) {
                return Optional.empty();
            }
            boolean isEntity = Entity.class.isAssignableFrom(t);
            if (isEntity) {
                // Sealed Entity: use @Polymorphic to determine strategy.
                // Default is SINGLE_TABLE; @Polymorphic(JOINED) opts into Joined Table.
                // @Polymorphic(SINGLE_TABLE) is also accepted for self-documenting code.
                Polymorphic polymorphic = t.getAnnotation(Polymorphic.class);
                if (polymorphic != null && polymorphic.value() == Polymorphic.Strategy.JOINED) {
                    return Optional.of(SealedPattern.JOINED);
                }
                return Optional.of(SealedPattern.SINGLE_TABLE);
            }
            if (Data.class.isAssignableFrom(t)) {
                // Polymorphic FK: sealed Data interface, subtypes are independent entities.
                // Subtypes must implement Entity; @DbTable is optional (table name resolver
                // is used when not present).
                boolean allSubtypesAreEntities = true;
                for (Class<?> sub : permitted) {
                    if (!Entity.class.isAssignableFrom(sub)) {
                        allSubtypesAreEntities = false;
                        break;
                    }
                }
                if (allSubtypesAreEntities) {
                    return Optional.of(SealedPattern.POLYMORPHIC_FK);
                }
            }
            return Optional.empty();
        });
    }

    /**
     * Returns true if the given type is a sealed entity (Single-Table or Joined pattern).
     */
    static boolean isSealedEntity(@Nonnull Class<?> type) {
        return detectSealedPattern(type)
                .map(p -> p == SealedPattern.SINGLE_TABLE || p == SealedPattern.JOINED)
                .orElse(false);
    }

    /**
     * Returns true if the given type uses single-table inheritance.
     */
    static boolean isSingleTableEntity(@Nonnull Class<?> type) {
        return detectSealedPattern(type)
                .map(p -> p == SealedPattern.SINGLE_TABLE)
                .orElse(false);
    }

    /**
     * Returns true if the given type uses joined table inheritance.
     */
    static boolean isJoinedEntity(@Nonnull Class<?> type) {
        return detectSealedPattern(type)
                .map(p -> p == SealedPattern.JOINED)
                .orElse(false);
    }

    /**
     * Returns true if the given type is a polymorphic Data interface (Polymorphic FK).
     */
    static boolean isPolymorphicData(@Nonnull Class<?> type) {
        return detectSealedPattern(type)
                .map(p -> p == SealedPattern.POLYMORPHIC_FK)
                .orElse(false);
    }

    /**
     * Returns true if the given sealed type has a discriminator column.
     *
     * <p>For SINGLE_TABLE inheritance, a discriminator is always required. For JOINED inheritance,
     * the {@code @Discriminator} annotation is optional; when absent, type resolution is performed
     * via a CASE expression that checks which extension table has a matching row.</p>
     *
     * @param sealedType the sealed type to check.
     * @return {@code true} if the type has a discriminator column.
     */
    static boolean hasDiscriminator(@Nonnull Class<?> sealedType) {
        var pattern = detectSealedPattern(sealedType).orElse(null);
        if (pattern == SealedPattern.SINGLE_TABLE) {
            return true;
        }
        if (pattern == SealedPattern.JOINED) {
            return sealedType.isAnnotationPresent(Discriminator.class);
        }
        return false;
    }

    /**
     * Returns the discriminator column name for a sealed type hierarchy.
     *
     * <p>If the sealed interface is annotated with {@link Discriminator @Discriminator}, the configured column name
     * is returned (defaulting to {@code "dtype"} if no column is specified). For JOINED inheritance without
     * {@code @Discriminator}, the default {@code "dtype"} is returned as an internal key (it is never projected
     * as a column in this case).</p>
     *
     * @param sealedType the sealed interface.
     * @return the discriminator column name.
     * @throws SqlTemplateException if a SINGLE_TABLE entity lacks {@code @Discriminator}.
     */
    static String getDiscriminatorColumn(@Nonnull Class<?> sealedType) throws SqlTemplateException {
        Discriminator discriminator = sealedType.getAnnotation(Discriminator.class);
        if (discriminator == null) {
            // For JOINED without @Discriminator, return default internal key.
            if (isJoinedEntity(sealedType)) {
                return "dtype";
            }
            throw new SqlTemplateException(
                    "Sealed type %s must be annotated with @Discriminator to specify the discriminator column. Add @Discriminator(column = \"...\") to the sealed type to specify which column distinguishes between subtypes."
                            .formatted(sealedType.getSimpleName()));
        }
        if (!discriminator.column().isEmpty()) {
            return discriminator.column();
        }
        return "dtype";
    }

    /**
     * Returns the discriminator column name for a polymorphic FK field.
     *
     * @param field the FK field targeting a sealed Data type.
     * @return the discriminator column name.
     */
    static String getPolymorphicDiscriminatorColumn(@Nonnull RecordField field) {
        Discriminator discriminator = field.getAnnotation(Discriminator.class);
        if (discriminator != null && !discriminator.column().isEmpty()) {
            return discriminator.column();
        }
        return field.name() + "_type";
    }

    /**
     * Converts a camelCase name to snake_case. Matches the default name resolver behavior.
     */
    private static String camelCaseToSnakeCase(@Nonnull String name) {
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toLowerCase(name.charAt(0)));
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else if (Character.isDigit(c)
                    && i >= 2
                    && Character.isLowerCase(name.charAt(i - 1))
                    && Character.isLowerCase(name.charAt(i - 2))) {
                sb.append('_').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Returns the discriminator type for a sealed type hierarchy.
     *
     * <p>The type is read from the {@code @Discriminator} annotation on the sealed interface.
     * Defaults to {@link DiscriminatorType#STRING} if not specified.</p>
     *
     * @param sealedType the sealed interface.
     * @return the discriminator type.
     */
    static DiscriminatorType getDiscriminatorType(@Nonnull Class<?> sealedType) {
        Discriminator discriminator = sealedType.getAnnotation(Discriminator.class);
        if (discriminator != null) {
            return discriminator.type();
        }
        return DiscriminatorType.STRING;
    }

    /**
     * Returns the Java type corresponding to the discriminator column for a sealed type hierarchy.
     *
     * @param sealedType the sealed interface.
     * @return {@code String.class}, {@code Integer.class}, or {@code Character.class}.
     */
    static Class<?> getDiscriminatorColumnJavaType(@Nonnull Class<?> sealedType) {
        return switch (getDiscriminatorType(sealedType)) {
            case STRING -> String.class;
            case INTEGER -> Integer.class;
            case CHAR -> Character.class;
        };
    }

    /**
     * Returns the discriminator value for a concrete subtype within a sealed hierarchy.
     *
     * <p>The return type depends on the discriminator type of the sealed interface:
     * {@code String} for STRING, {@code Integer} for INTEGER, {@code Character} for CHAR.</p>
     *
     * @param concreteType the concrete subtype (a permitted subclass).
     * @param sealedType the sealed interface.
     * @return the discriminator value.
     */
    static Object getDiscriminatorValue(@Nonnull Class<?> concreteType, @Nonnull Class<?> sealedType) {
        Discriminator discriminator = concreteType.getAnnotation(Discriminator.class);
        String rawValue;
        if (discriminator != null && !discriminator.value().isEmpty()) {
            rawValue = discriminator.value();
        } else if (isPolymorphicData(sealedType)) {
            // For POLYMORPHIC_FK, use @DbTable name if present, otherwise use the default table name
            // resolver to derive the discriminator value from the class name.
            DbTable dbTable = concreteType.getAnnotation(DbTable.class);
            if (dbTable != null) {
                String name = dbTable.value().isEmpty() ? dbTable.name() : dbTable.value();
                if (!name.isEmpty()) {
                    rawValue = name;
                } else {
                    rawValue = camelCaseToSnakeCase(concreteType.getSimpleName());
                }
            } else {
                // Use camelCase to snake_case conversion (matches default table name resolver).
                rawValue = camelCaseToSnakeCase(concreteType.getSimpleName());
            }
        } else {
            // Default: simple class name.
            rawValue = concreteType.getSimpleName();
        }
        return convertDiscriminatorValue(rawValue, getDiscriminatorType(sealedType));
    }

    /**
     * Converts a raw string discriminator value to the appropriate Java type based on the discriminator type.
     */
    private static Object convertDiscriminatorValue(@Nonnull String rawValue, @Nonnull DiscriminatorType type) {
        return switch (type) {
            case STRING -> rawValue;
            case INTEGER -> Integer.parseInt(rawValue);
            case CHAR -> rawValue.charAt(0);
        };
    }

    /**
     * Cache for discriminator value to concrete type mappings.
     */
    private static final Map<Class<?>, Map<Object, Class<?>>> DISCRIMINATOR_MAP_CACHE = new ConcurrentHashMap<>();

    /**
     * Resolves a discriminator value to a concrete subtype for the given sealed type.
     *
     * @param sealedType the sealed interface.
     * @param discriminatorValue the discriminator value from the database.
     * @return the concrete subtype class.
     * @throws SqlTemplateException if the discriminator value does not match any permitted subtype.
     */
    static Class<?> resolveConcreteType(@Nonnull Class<?> sealedType,
                                        @Nonnull Object discriminatorValue) throws SqlTemplateException {
        Map<Object, Class<?>> map = DISCRIMINATOR_MAP_CACHE.computeIfAbsent(sealedType, t -> {
            Map<Object, Class<?>> m = new ConcurrentHashMap<>();
            Class<?>[] permitted = t.getPermittedSubclasses();
            if (permitted != null) {
                for (Class<?> sub : permitted) {
                    Object value = getDiscriminatorValue(sub, t);
                    m.put(value, sub);
                }
            }
            return m;
        });
        Class<?> resolved = map.get(discriminatorValue);
        if (resolved == null) {
            throw new SqlTemplateException("Unknown discriminator value '%s' for sealed type %s. Known values: %s."
                    .formatted(discriminatorValue, sealedType.getSimpleName(), map.keySet()));
        }
        return resolved;
    }

    /**
     * Normalizes a raw discriminator value from the database to the appropriate Java type
     * based on the discriminator type of the sealed interface.
     *
     * @param raw the raw value from JDBC.
     * @param discriminatorType the discriminator type of the sealed hierarchy.
     * @return the normalized value (String, Integer, or Character).
     */
    static Object normalizeDiscriminatorValue(@Nonnull Object raw, @Nonnull DiscriminatorType discriminatorType) {
        return switch (discriminatorType) {
            case STRING -> raw.toString();
            case INTEGER -> raw instanceof Number number ? number.intValue() : Integer.parseInt(raw.toString());
            case CHAR -> raw instanceof String string && string.length() == 1
                    ? string.charAt(0)
                    : (raw instanceof Character ? raw : raw.toString().charAt(0));
        };
    }

    /**
     * Returns the fields that are common to ALL permitted subclasses of a joined sealed entity.
     * These fields belong to the base table. PK is always a base field.
     *
     * <p>A field is "common" if it appears in every permitted subclass with the same name AND type.</p>
     *
     * @param sealedType the sealed entity interface.
     * @return the list of field names that belong to the base table.
     */
    static List<String> getBaseFieldNames(@Nonnull Class<?> sealedType) {
        Class<?>[] permitted = sealedType.getPermittedSubclasses();
        if (permitted == null || permitted.length == 0) {
            return List.of();
        }
        // Start with all fields from the first subtype.
        RecordType firstType = REFLECTION.getRecordType(permitted[0]);
        List<String> candidates = new ArrayList<>();
        for (RecordField field : firstType.fields()) {
            candidates.add(field.name());
        }
        // Intersect with fields from all other subtypes.
        for (int i = 1; i < permitted.length; i++) {
            RecordType subType = REFLECTION.getRecordType(permitted[i]);
            List<String> subNames = subType.fields().stream().map(RecordField::name).toList();
            // Also check that types match.
            candidates.removeIf(name -> {
                if (!subNames.contains(name)) {
                    return true;
                }
                // Verify type matches.
                RecordField firstField = firstType.fields().stream()
                        .filter(f -> f.name().equals(name)).findFirst().orElse(null);
                RecordField subField = subType.fields().stream()
                        .filter(f -> f.name().equals(name)).findFirst().orElse(null);
                return firstField == null || subField == null || !firstField.type().equals(subField.type());
            });
        }
        return List.copyOf(candidates);
    }

    /**
     * Returns the field names unique to a specific concrete subtype (extension table fields).
     * These are fields NOT in the base field set.
     *
     * @param concreteType the concrete subtype.
     * @param sealedType the sealed entity interface.
     * @return the list of extension field names.
     */
    static List<String> getExtensionFieldNames(@Nonnull Class<?> concreteType,
                                               @Nonnull Class<?> sealedType) {
        List<String> baseFields = getBaseFieldNames(sealedType);
        RecordType type = REFLECTION.getRecordType(concreteType);
        List<String> extensionFields = new ArrayList<>();
        for (RecordField field : type.fields()) {
            if (!baseFields.contains(field.name())) {
                extensionFields.add(field.name());
            }
        }
        return List.copyOf(extensionFields);
    }

    /**
     * Validates a sealed entity hierarchy. Should be called during startup validation.
     *
     * @param sealedType the sealed interface to validate.
     * @return an error message, or empty string if valid.
     */
    static String validateSealedHierarchy(@Nonnull Class<?> sealedType) {
        Optional<SealedPattern> patternOpt = detectSealedPattern(sealedType);
        if (patternOpt.isEmpty()) {
            return "";
        }
        SealedPattern pattern = patternOpt.get();
        Class<?>[] permitted = sealedType.getPermittedSubclasses();
        if (permitted == null || permitted.length == 0) {
            return "Sealed type %s has no permitted subclasses.".formatted(sealedType.getSimpleName());
        }
        // All permitted subclasses must be records.
        for (Class<?> sub : permitted) {
            if (REFLECTION.findRecordType(sub).isEmpty()) {
                return "Permitted subclass %s of sealed type %s must be a record."
                        .formatted(sub.getSimpleName(), sealedType.getSimpleName());
            }
        }
        if (pattern == SealedPattern.SINGLE_TABLE || pattern == SealedPattern.JOINED) {
            // @Discriminator is required for SINGLE_TABLE, optional for JOINED.
            if (pattern == SealedPattern.SINGLE_TABLE && !sealedType.isAnnotationPresent(Discriminator.class)) {
                return "Sealed entity %s must be annotated with @Discriminator to specify the discriminator column."
                        .formatted(sealedType.getSimpleName());
            }
            // Check for misused @Discriminator value attribute on sealed interface.
            Discriminator sealedDiscriminator = sealedType.getAnnotation(Discriminator.class);
            if (sealedDiscriminator != null && !sealedDiscriminator.value().isEmpty()) {
                return "@Discriminator on sealed entity %s specifies a value attribute '%s'. "
                        .formatted(sealedType.getSimpleName(), sealedDiscriminator.value())
                        + "Use the column attribute to set the discriminator column name, or omit it for the default column name 'dtype'.";
            }
            // All subtypes must have the same @PK type and generation strategy.
            Class<?> firstPkType = null;
            GenerationStrategy firstGenStrategy = null;
            for (Class<?> sub : permitted) {
                Optional<RecordField> pkField = findPkField(sub);
                if (pkField.isEmpty()) {
                    return "Permitted subclass %s of sealed entity %s must have a @PK field."
                            .formatted(sub.getSimpleName(), sealedType.getSimpleName());
                }
                Class<?> pkType = pkField.get().type();
                GenerationStrategy genStrategy = getGenerationStrategy(pkField.get());
                if (firstPkType == null) {
                    firstPkType = pkType;
                    firstGenStrategy = genStrategy;
                } else {
                    if (!firstPkType.equals(pkType)) {
                        return "All permitted subclasses of sealed entity %s must have the same @PK type. Found %s and %s."
                                .formatted(sealedType.getSimpleName(), firstPkType.getSimpleName(), pkType.getSimpleName());
                    }
                    if (firstGenStrategy != genStrategy) {
                        return "All permitted subclasses of sealed entity %s must have the same @PK generation strategy. Found %s and %s."
                                .formatted(sealedType.getSimpleName(), firstGenStrategy, genStrategy);
                    }
                }
            }
        }
        if (pattern == SealedPattern.JOINED) {
            // Shared fields must match in name and type.
            List<String> baseFields = getBaseFieldNames(sealedType);
            if (baseFields.isEmpty()) {
                return "Joined sealed entity %s has no common fields across subtypes."
                        .formatted(sealedType.getSimpleName());
            }
        }
        // Check for near-miss fields: same name but different types across subtypes.
        if (pattern == SealedPattern.SINGLE_TABLE || pattern == SealedPattern.JOINED) {
            for (int i = 0; i < permitted.length; i++) {
                RecordType subType1 = REFLECTION.getRecordType(permitted[i]);
                for (RecordField field1 : subType1.fields()) {
                    for (int j = i + 1; j < permitted.length; j++) {
                        RecordType subType2 = REFLECTION.getRecordType(permitted[j]);
                        for (RecordField field2 : subType2.fields()) {
                            if (field1.name().equals(field2.name()) && !field1.type().equals(field2.type())) {
                                return "Field '%s' has different types in subtypes %s (%s) and %s (%s) of sealed type %s. "
                                        .formatted(field1.name(),
                                                permitted[i].getSimpleName(), field1.type().getSimpleName(),
                                                permitted[j].getSimpleName(), field2.type().getSimpleName(),
                                                sealedType.getSimpleName())
                                        + "Fields shared across subtypes must have the same type.";
                            }
                        }
                    }
                }
            }
        }
        if (pattern == SealedPattern.POLYMORPHIC_FK) {
            // Sealed interface must NOT have @DbTable.
            if (sealedType.isAnnotationPresent(DbTable.class)) {
                return "Polymorphic data type %s must not have @DbTable.".formatted(sealedType.getSimpleName());
            }
            // Sealed interface must NOT have @Discriminator (discriminator is on the FK field).
            if (sealedType.isAnnotationPresent(Discriminator.class)) {
                return "Polymorphic data type %s must not have @Discriminator. "
                        .formatted(sealedType.getSimpleName())
                        + "The discriminator column is specified on the @FK field that references this type.";
            }
            // Sealed interface must NOT have @Polymorphic (it's for sealed Entity types only).
            if (sealedType.isAnnotationPresent(Polymorphic.class)) {
                return "Polymorphic data type %s must not have @Polymorphic. "
                        .formatted(sealedType.getSimpleName())
                        + "@Polymorphic is only used on sealed Entity interfaces.";
            }
            // All subtypes must implement Entity.
            Class<?> firstPkType = null;
            GenerationStrategy firstGenStrategy = null;
            for (Class<?> sub : permitted) {
                if (!Entity.class.isAssignableFrom(sub)) {
                    return "Permitted subclass %s of polymorphic data type %s must implement Entity."
                            .formatted(sub.getSimpleName(), sealedType.getSimpleName());
                }
                Optional<RecordField> pkField = findPkField(sub);
                if (pkField.isEmpty()) {
                    return "Permitted subclass %s of polymorphic data type %s must have a @PK field."
                            .formatted(sub.getSimpleName(), sealedType.getSimpleName());
                }
                Class<?> pkType = pkField.get().type();
                GenerationStrategy genStrategy = getGenerationStrategy(pkField.get());
                if (firstPkType == null) {
                    firstPkType = pkType;
                    firstGenStrategy = genStrategy;
                } else {
                    if (!firstPkType.equals(pkType)) {
                        return "All permitted subclasses of polymorphic data type %s must have the same @PK column type. Found %s and %s."
                                .formatted(sealedType.getSimpleName(), firstPkType.getSimpleName(), pkType.getSimpleName());
                    }
                    if (firstGenStrategy != genStrategy) {
                        return "All permitted subclasses of polymorphic data type %s must have the same @PK generation strategy. Found %s and %s."
                                .formatted(sealedType.getSimpleName(), firstGenStrategy, genStrategy);
                    }
                }
            }
        }
        // Validate discriminator value uniqueness.
        Set<Object> seenValues = new HashSet<>();
        for (Class<?> sub : permitted) {
            Object value = getDiscriminatorValue(sub, sealedType);
            if (!seenValues.add(value)) {
                return "Duplicate discriminator value '%s' in sealed type %s."
                        .formatted(value, sealedType.getSimpleName());
            }
        }
        // Check for misplaced @Discriminator(column) on subtypes.
        for (Class<?> sub : permitted) {
            Discriminator subDiscriminator = sub.getAnnotation(Discriminator.class);
            if (subDiscriminator != null && !subDiscriminator.column().isEmpty()) {
                return "@Discriminator on permitted subclass %s of sealed type %s specifies a column attribute. "
                        .formatted(sub.getSimpleName(), sealedType.getSimpleName())
                        + "The discriminator column is specified on the sealed interface, not on subtypes.";
            }
        }
        // Check for @DbTable on single-table subtypes.
        if (pattern == SealedPattern.SINGLE_TABLE) {
            for (Class<?> sub : permitted) {
                if (sub.isAnnotationPresent(DbTable.class)) {
                    return "Permitted subclass %s of single-table sealed entity %s must not have @DbTable. "
                            .formatted(sub.getSimpleName(), sealedType.getSimpleName())
                            + "All subtypes share the sealed interface's table.";
                }
            }
        }
        return "";
    }

    /**
     * Returns the joined sealed parent of the given type, if any.
     *
     * <p>This method checks whether the given concrete type is a permitted subclass of a sealed entity
     * that uses joined table inheritance. If so, it returns the sealed parent interface.</p>
     *
     * @param type the type to inspect.
     * @return an Optional containing the joined sealed parent, or empty if the type is not a joined subtype.
     */
    static Optional<Class<?>> findJoinedSealedParent(@Nonnull Class<?> type) {
        if (type.isSealed() || type.isInterface()) {
            return Optional.empty();
        }
        for (Class<?> iface : type.getInterfaces()) {
            if (isJoinedEntity(iface)) {
                return Optional.of(iface);
            }
        }
        return Optional.empty();
    }

    // ---- End sealed type hierarchy support ----

    static void mapForeignKeys(@Nonnull TableMapper tableMapper,
                               @Nonnull String alias,
                               @Nonnull Class<? extends Data> rootTable,
                               @Nonnull Class<? extends Data> table,
                               @Nullable String path)
            throws SqlTemplateException {
        if (table.isSealed() && isSealedEntity(table)) {
            return; // Sealed entity interfaces have no FK fields; subtypes are handled separately.
        }
        for (var field : RecordReflection.getRecordFields(table)) {
            if (field.isAnnotationPresent(FK.class)) {
                if (Ref.class.isAssignableFrom(field.type())) {
                    tableMapper.mapForeignKey(table, getRefDataType(field), alias, field, rootTable, path);
                } else {
                    Class<?> recordType = field.type();
                    REFLECTION.findRecordType(recordType)
                            .orElseThrow(() -> new SqlTemplateException("FK annotation is only allowed on record types: %s.".formatted(field.type().getSimpleName())));
                    if (!Data.class.isAssignableFrom(recordType)) {
                        throw new SqlTemplateException("@FK annotation is only allowed on Data types: %s. Foreign key fields must reference types that implement the Data interface (Entity or Inline record). Remove the @FK annotation or change the field type.".formatted(field.type().getSimpleName()));
                    }
                    tableMapper.mapForeignKey(table, (Class<? extends Data>) recordType, alias, field, rootTable, path);
                }
            }
        }
    }
}
