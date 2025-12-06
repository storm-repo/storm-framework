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
import st.orm.Data;
import st.orm.Metamodel;
import st.orm.PersistenceException;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.mapping.RecordField;
import st.orm.core.template.SqlDialect;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.core.template.SqlTemplateException;

import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;

import static java.util.List.copyOf;
import static java.util.Objects.requireNonNull;

/**
 * Represents the model of an entity or projection.
 *
 * @param <E> the type of the entity or projection.
 * @param <ID> the type of the primary key, or {@code Void} in case of a projection without a primary key.
 * @param tableName the name of the table or view.
 * @param type the type of the entity or projection.
 * @param primaryKeyType the type of the primary key.
 * @param columns an immutable list of columns in the entity or projection.
 */
public record ModelImpl<E extends Data, ID>(
        @Nonnull TableName tableName,
        @Nonnull Class<E> type,
        @Nonnull Class<ID> primaryKeyType,
        @Nonnull List<Column> columns,
        @Nonnull List<Metamodel<E, ?>> metamodels,
        @Nonnull Optional<RecordField> primaryKeyField,
        @Nonnull List<RecordField> foreignKeyFields) implements Model<E, ID> {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    public ModelImpl {
        requireNonNull(tableName, "tableName");
        requireNonNull(type, "type");
        requireNonNull(primaryKeyType, "primaryKeyType");
        columns = copyOf(columns); // Defensive copy.
        metamodels = copyOf(metamodels); // Defensive copy.
        foreignKeyFields = copyOf(foreignKeyFields); // Defensive copy.
        assert columns.size() == metamodels.size() : "Columns and metamodels must have the same size";
    }

    /**
     * Returns the schema, or empty String if the schema is not specified.
     *
     * @return the schema, or empty String if the schema is not specified.
     */
    @Override
    public String schema() {
        return tableName.schema();
    }

    /**
     * Returns the name of the table or view.
     *
     * @return the name of the table or view.
     */
    @Override
    public String name() {
        return tableName.name();
    }

    /**
     * Returns the qualified name of the table or view, including the schema and escape characters where necessary.
     *
     * @return the qualified name of the table or view, including the schema and escape characters where necessary.
     */
    @Override
    public String qualifiedName(@Nonnull SqlDialect dialect) {
        return tableName.getQualifiedName(dialect);
    }

    /**
     * Returns {code true} if the specified primary key represents a default value, {@code false} otherwise.
     *
     * <p>This method is used to check if the primary key of the entity is a default value. This is useful when
     * determining if the entity is new or has been persisted before.</p>
     *
     * @param pk primary key to check.
     * @return {code true} if the specified primary key represents a default value, {@code false} otherwise.
     * @since 1.2
     */
    @Override
    public boolean isDefaultPrimaryKey(@Nullable ID pk) {
        return ModelMapper.of(this).isDefaultValue(pk);
    }

    /**
     * Extracts the value for the specified record field from the given record.
     *
     * @param field the record field to extract the value for.
     * @param record the record to extract the value from.
     * @return the value for the specified record field from the given record.
     * @since 1.3
     */
    @Override
    public Object getValue(@Nonnull RecordField field, @Nonnull E record) {
        return REFLECTION.invoke(field, record);
    }

    /**
     * Extracts the values from the given record and maps them to the columns of the entity or projection.
     *
     * @param record the record to extract the values from.
     * @return the values from the given record mapped to the columns of the entity or projection.
     * @throws PersistenceException if an error occurs while extracting the values.
     * @since 1.2
     */
    @Override
    public SequencedMap<Column, Object> getValues(@Nonnull E record) {
        try {
            return ModelMapper.of(this).map(record);
        } catch (SqlTemplateException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Extracts the value for the specified column from the given record.
     *
     * @param column the column to extract the value for.
     * @param record the record to extract the value from.
     * @return the value for the specified column from the given record.
     * @throws PersistenceException if an error occurs while extracting the values.
     * @since 1.2
     */
    @Override
    public Object getValue(@Nonnull Column column, @Nonnull E record) {
        try {
            return ModelMapper.of(this).map(column, record);
        } catch (SqlTemplateException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Returns the metamodel for the column.
     *
     * @return the metamodel for the column.
     * @throws PersistenceException if the column is unknown or does not match the model's columns.
     * @since 1.3
     */
    @Override
    public Metamodel<E, ?> getMetamodel(@Nonnull Column column) {
        int index = column.index();
        if (index < 1 || index > columns.size()) {
            throw new PersistenceException("Unknown column %s.".formatted(column));
        }
        if (!column.equals(columns.get(index - 1))) {
            throw new PersistenceException("Column %s does not match the model's column at index %d.".formatted(column, index));
        }
        return metamodels.get(column.index() - 1);
    }
}
