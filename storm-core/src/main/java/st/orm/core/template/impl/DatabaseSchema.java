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
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import st.orm.core.template.SqlDialect.SequenceDiscoveryStrategy;

/**
 * Reads JDBC {@link DatabaseMetaData} into an in-memory representation for schema validation.
 *
 * <p>All name lookups are case-insensitive, since database systems vary in how they handle identifier casing
 * (PostgreSQL lowercases, Oracle uppercases, H2 uppercases by default).</p>
 *
 * @since 1.9
 */
public final class DatabaseSchema {

    /**
     * Represents a column discovered from the database metadata.
     *
     * @param tableName     the table containing this column.
     * @param columnName    the column name.
     * @param dataType      the SQL type from {@link java.sql.Types}.
     * @param typeName      the database-specific type name.
     * @param columnSize    the column size (precision for numeric types, length for character types).
     * @param nullable      whether the column allows NULL values.
     * @param autoIncrement whether the column is auto-incremented.
     */
    public record DbColumn(
            @Nonnull String tableName,
            @Nonnull String columnName,
            int dataType,
            @Nonnull String typeName,
            int columnSize,
            boolean nullable,
            boolean autoIncrement
    ) {}

    /**
     * Represents a primary key column discovered from the database metadata.
     *
     * @param tableName  the table containing this primary key column.
     * @param columnName the column name that is part of the primary key.
     * @param keySeq     the sequence number within the primary key (1-based).
     */
    public record DbPrimaryKey(
            @Nonnull String tableName,
            @Nonnull String columnName,
            int keySeq
    ) {}

    /**
     * Represents a column that is part of a unique index discovered from the database metadata.
     *
     * @param tableName       the table containing this unique index.
     * @param indexName        the name of the unique index.
     * @param columnName      the column name that is part of the unique index.
     * @param ordinalPosition the ordinal position of the column within the index (1-based).
     */
    public record DbUniqueKey(
            @Nonnull String tableName,
            @Nonnull String indexName,
            @Nonnull String columnName,
            int ordinalPosition
    ) {}

    /**
     * Represents a foreign key relationship discovered from the database metadata.
     *
     * @param fkTableName  the table containing the foreign key column.
     * @param fkColumnName the foreign key column name.
     * @param pkTableName  the referenced (primary key) table name.
     * @param pkColumnName the referenced (primary key) column name.
     */
    public record DbForeignKey(
            @Nonnull String fkTableName,
            @Nonnull String fkColumnName,
            @Nonnull String pkTableName,
            @Nonnull String pkColumnName
    ) {}

    // Case-insensitive maps: table name -> columns/PKs/UKs/FKs.
    private final SortedMap<String, List<DbColumn>> columnsByTable;
    private final SortedMap<String, List<DbPrimaryKey>> primaryKeysByTable;
    private final SortedMap<String, List<DbUniqueKey>> uniqueKeysByTable;
    private final SortedMap<String, List<DbForeignKey>> foreignKeysByTable;
    private final SortedMap<String, Boolean> sequences;

    private DatabaseSchema(
            @Nonnull SortedMap<String, List<DbColumn>> columnsByTable,
            @Nonnull SortedMap<String, List<DbPrimaryKey>> primaryKeysByTable,
            @Nonnull SortedMap<String, List<DbUniqueKey>> uniqueKeysByTable,
            @Nonnull SortedMap<String, List<DbForeignKey>> foreignKeysByTable,
            @Nonnull SortedMap<String, Boolean> sequences
    ) {
        this.columnsByTable = columnsByTable;
        this.primaryKeysByTable = primaryKeysByTable;
        this.uniqueKeysByTable = uniqueKeysByTable;
        this.foreignKeysByTable = foreignKeysByTable;
        this.sequences = sequences;
    }

    /**
     * Reads the database schema from the given connection using default catalog and schema.
     *
     * @param connection the JDBC connection.
     * @return the database schema.
     * @throws SQLException if a database access error occurs.
     */
    public static DatabaseSchema read(@Nonnull Connection connection) throws SQLException {
        return read(connection, connection.getCatalog(), connection.getSchema());
    }

    /**
     * Reads the database schema from the given connection.
     *
     * <p>This overload defaults to the {@link SequenceDiscoveryStrategy#INFORMATION_SCHEMA INFORMATION_SCHEMA}
     * strategy for sequence discovery.</p>
     *
     * @param connection    the JDBC connection.
     * @param catalog       the catalog name (may be null).
     * @param schemaPattern the schema pattern (may be null).
     * @return the database schema.
     * @throws SQLException if a database access error occurs.
     */
    public static DatabaseSchema read(
            @Nonnull Connection connection,
            @Nullable String catalog,
            @Nullable String schemaPattern
    ) throws SQLException {
        return read(connection, catalog, schemaPattern, SequenceDiscoveryStrategy.INFORMATION_SCHEMA);
    }

    /**
     * Reads the database schema from the given connection using the specified sequence discovery strategy.
     *
     * @param connection                  the JDBC connection.
     * @param catalog                     the catalog name (may be null).
     * @param schemaPattern               the schema pattern (may be null).
     * @param sequenceDiscoveryStrategy   the strategy for discovering sequences.
     * @return the database schema.
     * @throws SQLException if a database access error occurs.
     */
    public static DatabaseSchema read(
            @Nonnull Connection connection,
            @Nullable String catalog,
            @Nullable String schemaPattern,
            @Nonnull SequenceDiscoveryStrategy sequenceDiscoveryStrategy
    ) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        // Normalize the schema pattern to match the database's identifier casing convention.
        if (schemaPattern != null) {
            if (metadata.storesUpperCaseIdentifiers()) {
                schemaPattern = schemaPattern.toUpperCase();
            } else if (metadata.storesLowerCaseIdentifiers()) {
                schemaPattern = schemaPattern.toLowerCase();
            }
        }
        SortedMap<String, List<DbColumn>> columnsByTable = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        SortedMap<String, List<DbPrimaryKey>> primaryKeysByTable = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        SortedMap<String, List<DbUniqueKey>> uniqueKeysByTable = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        SortedMap<String, List<DbForeignKey>> foreignKeysByTable = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        SortedMap<String, Boolean> sequences = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        // Discover tables and views.
        try (ResultSet tables = metadata.getTables(catalog, schemaPattern, "%", new String[]{"TABLE", "VIEW"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                // Initialize entries so tableExists() works even for tables with no columns.
                columnsByTable.putIfAbsent(tableName, new ArrayList<>());
            }
        }
        // Discover columns for all tables.
        try (ResultSet columns = metadata.getColumns(catalog, schemaPattern, "%", "%")) {
            while (columns.next()) {
                String tableName = columns.getString("TABLE_NAME");
                // Only include columns for tables we discovered.
                if (!columnsByTable.containsKey(tableName)) {
                    continue;
                }
                String columnName = columns.getString("COLUMN_NAME");
                int dataType = columns.getInt("DATA_TYPE");
                String typeName = columns.getString("TYPE_NAME");
                int columnSize = columns.getInt("COLUMN_SIZE");
                String nullableStr = columns.getString("IS_NULLABLE");
                boolean nullable = !"NO".equalsIgnoreCase(nullableStr);
                String autoIncrementStr = columns.getString("IS_AUTOINCREMENT");
                boolean autoIncrement = "YES".equalsIgnoreCase(autoIncrementStr);

                columnsByTable.computeIfAbsent(tableName, k -> new ArrayList<>())
                        .add(new DbColumn(tableName, columnName, dataType, typeName, columnSize, nullable, autoIncrement));
            }
        }
        // Discover primary keys per table.
        for (String tableName : new ArrayList<>(columnsByTable.keySet())) {
            try (ResultSet primaryKeys = metadata.getPrimaryKeys(catalog, schemaPattern, tableName)) {
                while (primaryKeys.next()) {
                    String pkTableName = primaryKeys.getString("TABLE_NAME");
                    String columnName = primaryKeys.getString("COLUMN_NAME");
                    int keySeq = primaryKeys.getShort("KEY_SEQ");
                    primaryKeysByTable.computeIfAbsent(pkTableName, k -> new ArrayList<>())
                            .add(new DbPrimaryKey(pkTableName, columnName, keySeq));
                }
            } catch (SQLException ignored) {
                // Some databases/views may not support getPrimaryKeys; skip gracefully.
            }
        }
        // Discover unique indexes per table.
        for (String tableName : new ArrayList<>(columnsByTable.keySet())) {
            try (ResultSet indexInfo = metadata.getIndexInfo(catalog, schemaPattern, tableName, true, true)) {
                while (indexInfo.next()) {
                    // Skip table statistics entries (TYPE == tableIndexStatistic).
                    if (indexInfo.getShort("TYPE") == 0) {
                        continue;
                    }
                    String indexName = indexInfo.getString("INDEX_NAME");
                    String columnName = indexInfo.getString("COLUMN_NAME");
                    int ordinalPosition = indexInfo.getShort("ORDINAL_POSITION");
                    if (indexName == null || columnName == null) {
                        continue;
                    }
                    uniqueKeysByTable.computeIfAbsent(tableName, k -> new ArrayList<>())
                            .add(new DbUniqueKey(tableName, indexName, columnName, ordinalPosition));
                }
            } catch (SQLException ignored) {
                // Some databases/views may not support getIndexInfo; skip gracefully.
            }
        }
        // Discover foreign keys per table.
        for (String tableName : new ArrayList<>(columnsByTable.keySet())) {
            try (ResultSet importedKeys = metadata.getImportedKeys(catalog, schemaPattern, tableName)) {
                while (importedKeys.next()) {
                    String fkTableName = importedKeys.getString("FKTABLE_NAME");
                    String fkColumnName = importedKeys.getString("FKCOLUMN_NAME");
                    String pkTableName = importedKeys.getString("PKTABLE_NAME");
                    String pkColumnName = importedKeys.getString("PKCOLUMN_NAME");
                    foreignKeysByTable.computeIfAbsent(fkTableName, k -> new ArrayList<>())
                            .add(new DbForeignKey(fkTableName, fkColumnName, pkTableName, pkColumnName));
                }
            } catch (SQLException ignored) {
                // Some databases/views may not support getImportedKeys; skip gracefully.
            }
        }
        // Discover sequences using the dialect-provided strategy.
        readSequences(connection, catalog, schemaPattern, sequences, sequenceDiscoveryStrategy);
        return new DatabaseSchema(columnsByTable, primaryKeysByTable, uniqueKeysByTable, foreignKeysByTable, sequences);
    }

    /**
     * Reads sequences using the specified discovery strategy.
     *
     * <p>If the selected strategy fails, sequence validation is silently skipped.</p>
     */
    private static void readSequences(
            @Nonnull Connection connection,
            @Nullable String catalog,
            @Nullable String schemaPattern,
            @Nonnull SortedMap<String, Boolean> sequences,
            @Nonnull SequenceDiscoveryStrategy strategy
    ) {
        switch (strategy) {
            case INFORMATION_SCHEMA -> readSequencesFromInformationSchema(connection, catalog, schemaPattern, sequences);
            case ALL_SEQUENCES -> readSequencesFromAllSequences(connection, schemaPattern, sequences);
            case NONE -> { }
        }
    }

    /**
     * Attempts to read sequences from INFORMATION_SCHEMA.SEQUENCES.
     */
    private static void readSequencesFromInformationSchema(
            @Nonnull Connection connection,
            @Nullable String catalog,
            @Nullable String schemaPattern,
            @Nonnull SortedMap<String, Boolean> sequences
    ) {
        try {
            StringBuilder sql = new StringBuilder("SELECT SEQUENCE_NAME FROM INFORMATION_SCHEMA.SEQUENCES");
            boolean hasCondition = false;
            if (catalog != null && !catalog.isEmpty()) {
                sql.append(" WHERE SEQUENCE_CATALOG = '").append(catalog.replace("'", "''")).append("'");
                hasCondition = true;
            }
            if (schemaPattern != null && !schemaPattern.isEmpty()) {
                sql.append(hasCondition ? " AND" : " WHERE");
                sql.append(" SEQUENCE_SCHEMA = '").append(schemaPattern.replace("'", "''")).append("'");
            }
            try (var statement = connection.createStatement();
                 var resultSet = statement.executeQuery(sql.toString())) {
                while (resultSet.next()) {
                    sequences.put(resultSet.getString("SEQUENCE_NAME"), Boolean.TRUE);
                }
            }
        } catch (SQLException ignored) {
            // INFORMATION_SCHEMA.SEQUENCES not available; sequence validation will be skipped.
        }
    }

    /**
     * Attempts to read sequences from Oracle's ALL_SEQUENCES dictionary view.
     */
    private static void readSequencesFromAllSequences(
            @Nonnull Connection connection,
            @Nullable String schemaPattern,
            @Nonnull SortedMap<String, Boolean> sequences
    ) {
        try {
            StringBuilder sql = new StringBuilder("SELECT SEQUENCE_NAME FROM ALL_SEQUENCES");
            if (schemaPattern != null && !schemaPattern.isEmpty()) {
                sql.append(" WHERE SEQUENCE_OWNER = '").append(schemaPattern.replace("'", "''")).append("'");
            }
            try (var statement = connection.createStatement();
                 var resultSet = statement.executeQuery(sql.toString())) {
                while (resultSet.next()) {
                    sequences.put(resultSet.getString("SEQUENCE_NAME"), Boolean.TRUE);
                }
            }
        } catch (SQLException ignored) {
            // ALL_SEQUENCES not available; sequence validation will be skipped.
        }
    }

    /**
     * Returns whether a table or view with the given name exists in the schema.
     *
     * @param tableName the table name (case-insensitive).
     * @return {@code true} if the table exists.
     */
    public boolean tableExists(@Nonnull String tableName) {
        return columnsByTable.containsKey(tableName);
    }

    /**
     * Returns the column metadata for the given table and column name.
     *
     * @param tableName  the table name (case-insensitive).
     * @param columnName the column name (case-insensitive).
     * @return the column metadata, or empty if not found.
     */
    public Optional<DbColumn> getColumn(@Nonnull String tableName, @Nonnull String columnName) {
        List<DbColumn> columns = columnsByTable.get(tableName);
        if (columns == null) {
            return Optional.empty();
        }
        return columns.stream()
                .filter(column -> column.columnName().equalsIgnoreCase(columnName))
                .findFirst();
    }

    /**
     * Returns the primary key columns for the given table, ordered by key sequence.
     *
     * @param tableName the table name (case-insensitive).
     * @return the primary key columns, or an empty list if none found.
     */
    public List<DbPrimaryKey> getPrimaryKeys(@Nonnull String tableName) {
        List<DbPrimaryKey> primaryKeys = primaryKeysByTable.get(tableName);
        if (primaryKeys == null) {
            return List.of();
        }
        return List.copyOf(primaryKeys);
    }

    /**
     * Returns the unique key columns for the given table.
     *
     * @param tableName the table name (case-insensitive).
     * @return the unique key columns, or an empty list if none found.
     */
    public List<DbUniqueKey> getUniqueKeys(@Nonnull String tableName) {
        List<DbUniqueKey> uniqueKeys = uniqueKeysByTable.get(tableName);
        if (uniqueKeys == null) {
            return List.of();
        }
        return List.copyOf(uniqueKeys);
    }

    /**
     * Returns the foreign key constraints for the given table.
     *
     * @param tableName the table name (case-insensitive).
     * @return the foreign key constraints, or an empty list if none found.
     */
    public List<DbForeignKey> getForeignKeys(@Nonnull String tableName) {
        List<DbForeignKey> foreignKeys = foreignKeysByTable.get(tableName);
        if (foreignKeys == null) {
            return List.of();
        }
        return List.copyOf(foreignKeys);
    }

    /**
     * Returns whether a sequence with the given name exists in the schema.
     *
     * @param sequenceName the sequence name (case-insensitive).
     * @return {@code true} if the sequence exists.
     */
    public boolean sequenceExists(@Nonnull String sequenceName) {
        return sequences.containsKey(sequenceName);
    }
}
