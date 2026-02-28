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

import static st.orm.core.spi.Providers.getSqlDialect;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import st.orm.Data;
import st.orm.DbIgnore;
import st.orm.Entity;
import st.orm.GenerationStrategy;
import st.orm.ProjectionQuery;
import st.orm.core.spi.TypeDiscovery;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.core.template.SqlDialect;
import st.orm.core.template.SqlDialect.SequenceDiscoveryStrategy;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.impl.DatabaseSchema.DbColumn;
import st.orm.core.template.impl.SchemaValidationError.ErrorKind;
import st.orm.core.template.impl.TypeCompatibility.Compatibility;
import st.orm.mapping.RecordField;

/**
 * Validates entity and projection definitions against the actual database schema.
 *
 * <p>This validator compares Storm's {@link Model}/{@link Column} metadata (built from entity classes) against the
 * database's {@link java.sql.DatabaseMetaData}. It detects mismatches such as missing tables, missing columns,
 * type incompatibilities, nullability mismatches, primary key mismatches, and missing sequences.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * SchemaValidator validator = SchemaValidator.of(dataSource);
 * validator.validateOrThrow();  // throws SchemaValidationException on mismatch
 * }</pre>
 *
 * @since 1.9
 */
public final class SchemaValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger("st.orm.validation");

    private final DataSource dataSource;
    private final ModelBuilder modelBuilder;
    private final TypeCompatibility typeCompatibility;
    private final SqlDialect sqlDialect;

    private SchemaValidator(
            @Nonnull DataSource dataSource,
            @Nonnull ModelBuilder modelBuilder,
            @Nonnull TypeCompatibility typeCompatibility,
            @Nonnull SqlDialect sqlDialect
    ) {
        this.dataSource = dataSource;
        this.modelBuilder = modelBuilder;
        this.typeCompatibility = typeCompatibility;
        this.sqlDialect = sqlDialect;
    }

    /**
     * Creates a new schema validator with the default model builder.
     *
     * @param dataSource the data source to validate against.
     * @return a new schema validator.
     */
    public static SchemaValidator of(@Nonnull DataSource dataSource) {
        return new SchemaValidator(dataSource, ModelBuilder.newInstance(), TypeCompatibility.defaultCompatibility(),
                getSqlDialect());
    }

    /**
     * Creates a new schema validator with the specified model builder.
     *
     * @param dataSource   the data source to validate against.
     * @param modelBuilder the model builder to use for constructing entity models.
     * @return a new schema validator.
     */
    public static SchemaValidator of(@Nonnull DataSource dataSource, @Nonnull ModelBuilder modelBuilder) {
        return new SchemaValidator(dataSource, modelBuilder, TypeCompatibility.defaultCompatibility(),
                getSqlDialect());
    }

    /**
     * Creates a new schema validator with the specified model builder and SQL dialect.
     *
     * @param dataSource   the data source to validate against.
     * @param modelBuilder the model builder to use for constructing entity models.
     * @param sqlDialect   the SQL dialect to use for database-specific behavior.
     * @return a new schema validator.
     */
    public static SchemaValidator of(@Nonnull DataSource dataSource, @Nonnull ModelBuilder modelBuilder,
                                     @Nonnull SqlDialect sqlDialect) {
        return new SchemaValidator(dataSource, modelBuilder, TypeCompatibility.defaultCompatibility(), sqlDialect);
    }

    /**
     * Validates all entity and projection types discovered on the classpath via {@link TypeDiscovery}.
     *
     * @return the list of validation errors (empty if all entities match the database schema).
     */
    public List<SchemaValidationError> validate() {
        return validate(TypeDiscovery.getDataTypes());
    }

    /**
     * Validates the specified entity and projection types against the database schema.
     *
     * @param types the types to validate.
     * @return the list of validation errors (empty if all types match the database schema).
     */
    public List<SchemaValidationError> validate(@Nonnull Iterable<Class<? extends Data>> types) {
        List<SchemaValidationError> errors = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            String defaultCatalog = connection.getCatalog();
            String defaultSchema = connection.getSchema();
            // Cache DatabaseSchema instances per schema name (case-insensitive) to avoid redundant metadata reads.
            TreeMap<String, DatabaseSchema> schemaCache = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (Class<? extends Data> type : types) {
                validateType(type, connection, defaultCatalog, defaultSchema, schemaCache, errors);
            }
        } catch (SQLException e) {
            throw new st.orm.PersistenceException("Failed to read database schema for validation.", e);
        }
        return errors;
    }

    /**
     * Validates all discovered types and throws if any errors are found.
     *
     * @throws SchemaValidationException if one or more validation errors are detected.
     */
    public void validateOrThrow() throws SchemaValidationException {
        List<SchemaValidationError> errors = validate();
        if (!errors.isEmpty()) {
            throw new SchemaValidationException(errors);
        }
    }

    /**
     * Validates the specified types and throws if any errors are found.
     *
     * @param types the types to validate.
     * @throws SchemaValidationException if one or more validation errors are detected.
     */
    public void validateOrThrow(@Nonnull Iterable<Class<? extends Data>> types) throws SchemaValidationException {
        List<SchemaValidationError> errors = validate(types);
        if (!errors.isEmpty()) {
            throw new SchemaValidationException(errors);
        }
    }

    /**
     * Validates all discovered types, logs each finding, and returns the error messages.
     *
     * <p>In strict mode, all findings (including warnings like type narrowing and nullability mismatches) are treated
     * as errors. In non-strict mode, warnings are logged at WARN level but excluded from the returned error list.</p>
     *
     * @param strict whether to treat warnings as errors.
     * @return the list of error messages (empty on success).
     */
    public List<String> validateAndReport(boolean strict) {
        LOGGER.info("Validating Data types for schema compatibility.");
        List<Class<? extends Data>> types = TypeDiscovery.getDataTypes();
        return reportErrors(validate(types), strict, types.size());
    }

    /**
     * Validates the specified types, logs each finding, and returns the error messages.
     *
     * <p>In strict mode, all findings (including warnings like type narrowing and nullability mismatches) are treated
     * as errors. In non-strict mode, warnings are logged at WARN level but excluded from the returned error list.</p>
     *
     * @param types  the types to validate.
     * @param strict whether to treat warnings as errors.
     * @return the list of error messages (empty on success).
     */
    public List<String> validateAndReport(@Nonnull Iterable<Class<? extends Data>> types, boolean strict) {
        LOGGER.info("Validating Data types for schema compatibility.");
        return reportErrors(validate(types), strict, countTypes(types));
    }

    /**
     * Validates all discovered types and logs mismatches at WARN level.
     *
     * <p>This is a convenience method for "warn" mode: validation issues are logged but never cause an exception.</p>
     */
    public void validateOrWarn() {
        LOGGER.info("Validating Data types for schema compatibility.");
        List<Class<? extends Data>> types = TypeDiscovery.getDataTypes();
        reportErrors(validate(types), false, types.size());
    }

    /**
     * Validates all discovered types, reports errors with logging, and throws if any errors remain.
     *
     * @param strict whether to treat warnings as errors.
     * @throws st.orm.PersistenceException if one or more validation errors are detected after reporting.
     */
    public void validateReportAndThrow(boolean strict) {
        List<String> errors = validateAndReport(strict);
        if (!errors.isEmpty()) {
            throw new st.orm.PersistenceException(formatErrors(errors));
        }
    }

    private static List<String> reportErrors(
            @Nonnull List<SchemaValidationError> validationErrors,
            boolean strict,
            int typeCount
    ) {
        if (validationErrors.isEmpty()) {
            LOGGER.info("Successfully validated {} Data types for schema compatibility.", typeCount);
            return List.of();
        }
        // In strict mode, all findings are treated as errors.
        // In non-strict mode, warnings are logged separately and excluded from the returned list.
        List<String> errors = new ArrayList<>();
        for (SchemaValidationError validationError : validationErrors) {
            String message = validationError.toString();
            if (!strict && validationError.kind().warning()) {
                LOGGER.warn(message);
            } else {
                LOGGER.error(message);
                errors.add(message);
            }
        }
        if (errors.isEmpty()) {
            LOGGER.info("Successfully validated {} Data types for schema compatibility (with warnings).", typeCount);
        } else {
            LOGGER.warn("Schema validation found {} issue(s).", errors.size());
        }
        return errors;
    }

    private static int countTypes(@Nonnull Iterable<?> types) {
        if (types instanceof Collection<?> collection) {
            return collection.size();
        }
        int count = 0;
        for (var ignored : types) {
            count++;
        }
        return count;
    }

    static String formatErrors(@Nonnull List<String> errors) {
        return "Schema validation failed with %d error(s):\n%s".formatted(
                errors.size(),
                String.join("\n", errors.stream().map(e -> "  - " + e).toList()));
    }

    /**
     * Resolves the {@link DatabaseSchema} for the given entity schema, reading from the database if not already cached.
     *
     * <p>On databases that use catalogs as schemas (as indicated by {@link SqlDialect#useCatalogAsSchema()}), the
     * {@code @DbTable(schema = ...)} value represents a database name, which maps to the JDBC catalog. In that case,
     * the entity schema is passed as the catalog parameter instead of the schema pattern.</p>
     */
    private DatabaseSchema resolveSchema(
            @Nonnull Connection connection,
            @Nullable String defaultCatalog,
            @Nullable String defaultSchema,
            @Nonnull String entitySchema,
            @Nonnull TreeMap<String, DatabaseSchema> schemaCache
    ) throws SQLException {
        // Use the entity's schema if specified, otherwise fall back to the connection's default schema.
        String schemaKey = entitySchema.isEmpty()
                ? (defaultSchema != null ? defaultSchema : "")
                : entitySchema;
        DatabaseSchema cached = schemaCache.get(schemaKey);
        if (cached != null) {
            return cached;
        }
        String catalog;
        String schemaPattern;
        if (!entitySchema.isEmpty() && sqlDialect.useCatalogAsSchema()) {
            // Database uses catalogs as schemas (e.g., MySQL, MariaDB). The entity's schema represents a
            // database name, which maps to the JDBC catalog.
            catalog = entitySchema;
            schemaPattern = null;
        } else {
            catalog = defaultCatalog;
            schemaPattern = entitySchema.isEmpty() ? defaultSchema : entitySchema;
        }
        SequenceDiscoveryStrategy strategy = sqlDialect.sequenceDiscoveryStrategy();
        DatabaseSchema databaseSchema = DatabaseSchema.read(connection, catalog, schemaPattern, strategy);
        schemaCache.put(schemaKey, databaseSchema);
        return databaseSchema;
    }

    /**
     * Validates a single type against the database schema.
     */
    private void validateType(
            @Nonnull Class<? extends Data> type,
            @Nonnull Connection connection,
            @Nullable String defaultCatalog,
            @Nullable String defaultSchema,
            @Nonnull TreeMap<String, DatabaseSchema> schemaCache,
            @Nonnull List<SchemaValidationError> errors
    ) {
        // Skip sealed interfaces: their permitted subclasses will be validated individually,
        // and for single-table inheritance the sealed model's columns are the union of all subtypes.
        if (type.isInterface() || (type.isSealed() && !type.isRecord())) {
            return;
        }
        boolean requirePrimaryKey = Entity.class.isAssignableFrom(type);
        Model<?, ?> model;
        try {
            model = modelBuilder.build(type, requirePrimaryKey);
        } catch (SqlTemplateException e) {
            // Type cannot be modeled (e.g., invalid record structure). Skip; RecordValidation handles this.
            return;
        }
        // Skip types annotated with @DbIgnore or @ProjectionQuery.
        if (model.recordType().isAnnotationPresent(DbIgnore.class)
                || model.recordType().isAnnotationPresent(ProjectionQuery.class)) {
            return;
        }
        String tableName = model.name();
        String entitySchema = model.schema();
        DatabaseSchema schema;
        try {
            schema = resolveSchema(connection, defaultCatalog, defaultSchema, entitySchema, schemaCache);
        } catch (SQLException e) {
            throw new st.orm.PersistenceException(
                    "Failed to read database schema '%s' for validation.".formatted(entitySchema), e);
        }
        // Use schema-qualified table name in error messages when a custom schema is specified.
        String qualifiedTableName = entitySchema.isEmpty()
                ? tableName
                : entitySchema + "." + tableName;
        // Collect field names annotated with @DbIgnore.
        Set<String> ignoredComponents = getIgnoredFields(model);
        // 1. Table existence.
        if (!schema.tableExists(tableName)) {
            errors.add(new SchemaValidationError(type, ErrorKind.TABLE_NOT_FOUND,
                    "Table '%s' not found in database.".formatted(qualifiedTableName)));
            return;  // No point checking columns if the table doesn't exist.
        }
        // 2-4. Column existence, type compatibility, nullability.
        for (Column column : model.declaredColumns()) {
            if (isIgnored(column, ignoredComponents)) {
                continue;
            }
            String columnName = column.name();
            Optional<DbColumn> dbColumn = schema.getColumn(tableName, columnName);

            if (dbColumn.isEmpty()) {
                errors.add(new SchemaValidationError(type, ErrorKind.COLUMN_NOT_FOUND,
                        "Column '%s' not found in table '%s'.".formatted(columnName, qualifiedTableName)));
                continue;
            }
            DbColumn dbCol = dbColumn.get();
            // Type compatibility check.
            Compatibility compatibility = typeCompatibility.check(column.type(), dbCol.dataType(), dbCol.typeName());
            if (compatibility == Compatibility.NARROWING) {
                errors.add(new SchemaValidationError(type, ErrorKind.TYPE_NARROWING,
                        "Column '%s' in table '%s': Java type '%s' mapped to SQL type '%s' (%d) may involve precision or range loss."
                                .formatted(columnName, qualifiedTableName, column.type().getSimpleName(),
                                        dbCol.typeName(), dbCol.dataType())));
            } else if (compatibility == Compatibility.INCOMPATIBLE) {
                errors.add(new SchemaValidationError(type, ErrorKind.TYPE_INCOMPATIBLE,
                        "Column '%s' in table '%s': Java type '%s' is not compatible with SQL type '%s' (%d)."
                                .formatted(columnName, qualifiedTableName, column.type().getSimpleName(),
                                        dbCol.typeName(), dbCol.dataType())));
            }
            // Nullability check: entity field is non-nullable but database column allows NULL.
            // We only flag the case where the entity says non-null but the DB says nullable,
            // since the reverse (entity nullable, DB not null) is safe.
            if (!column.nullable() && dbCol.nullable()) {
                errors.add(new SchemaValidationError(type, ErrorKind.NULLABILITY_MISMATCH,
                        "Column '%s' in table '%s': entity field is non-nullable but database column allows NULL."
                                .formatted(columnName, qualifiedTableName)));
            }
        }
        // 5. Primary key match.
        if (requirePrimaryKey) {
            Set<String> entityPkColumns = model.declaredColumns().stream()
                    .filter(Column::primaryKey)
                    .map(column -> column.name().toUpperCase())
                    .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));

            Set<String> dbPkColumns = schema.getPrimaryKeys(tableName).stream()
                    .map(pk -> pk.columnName().toUpperCase())
                    .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));

            if (!dbPkColumns.isEmpty() && !entityPkColumns.equals(dbPkColumns)) {
                errors.add(new SchemaValidationError(type, ErrorKind.PRIMARY_KEY_MISMATCH,
                        "Primary key mismatch for table '%s': entity defines %s, database has %s."
                                .formatted(qualifiedTableName, entityPkColumns, dbPkColumns)));
            }
        }
        // 6. Sequence existence.
        for (Column column : model.declaredColumns()) {
            if (isIgnored(column, ignoredComponents)) {
                continue;
            }
            if (column.generation() == GenerationStrategy.SEQUENCE) {
                String sequenceName = column.sequence();
                if (!sequenceName.isEmpty() && !schema.sequenceExists(sequenceName)) {
                    errors.add(new SchemaValidationError(type, ErrorKind.SEQUENCE_NOT_FOUND,
                            "Sequence '%s' not found in database (referenced by column '%s' in table '%s')."
                                    .formatted(sequenceName, column.name(), qualifiedTableName)));
                }
            }
        }
    }

    /**
     * Returns the names of fields annotated with {@link DbIgnore}.
     */
    private static Set<String> getIgnoredFields(@Nonnull Model<?, ?> model) {
        Set<String> ignored = new HashSet<>();
        for (RecordField field : model.recordType().fields()) {
            if (field.isAnnotationPresent(DbIgnore.class)) {
                ignored.add(field.name());
            }
        }
        return ignored;
    }

    /**
     * Returns whether the given column should be skipped because its source record component is annotated with
     * {@link DbIgnore}.
     *
     * <p>For direct columns, the metamodel's field path matches the component name. For inline records, the field
     * path starts with the component name (e.g., "address.street" for the "address" component).</p>
     */
    private static boolean isIgnored(@Nonnull Column column, @Nonnull Set<String> ignoredComponents) {
        if (ignoredComponents.isEmpty()) {
            return false;
        }
        String fieldPath = column.metamodel().fieldPath();
        for (String component : ignoredComponents) {
            if (fieldPath.equals(component) || fieldPath.startsWith(component + ".")) {
                return true;
            }
        }
        return false;
    }
}
