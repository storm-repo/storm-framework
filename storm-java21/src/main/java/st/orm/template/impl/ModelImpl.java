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
import st.orm.Data;
import st.orm.core.template.impl.TableName;
import st.orm.PersistenceException;
import st.orm.mapping.RecordField;
import st.orm.template.Column;
import st.orm.template.Model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;

import static java.util.List.copyOf;

/**
 * Represents the model of an entity.
 */
public record ModelImpl<E extends Data, ID>(
        @Nonnull st.orm.core.template.impl.ModelImpl<E, ID> core,
        @Nonnull TableName tableName,
        @Nonnull Class<E> type,
        @Nonnull Class<ID> primaryKeyType,
        @Nonnull List<Column> columns,
        @Nonnull Optional<RecordField> primaryKeyField,
        @Nonnull List<RecordField> foreignKeyFields) implements Model<E, ID> {

    public ModelImpl {
        columns = copyOf(columns); // Defensive copy.
    }

    public ModelImpl(@Nonnull st.orm.core.template.impl.ModelImpl<E, ID> model) {
        this(
                model,
                model.tableName(),
                model.type(),
                model.primaryKeyType(),
                model.columns().stream()
                        .map(c -> new ColumnImpl((st.orm.core.template.impl.ColumnImpl) c))
                        .map(Column.class::cast)
                        .toList(),
                model.primaryKeyField(),
                model.foreignKeyFields()
        );
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
        return core.isDefaultPrimaryKey(pk);
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
        return core.getValue(field, record);
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
        var map = new LinkedHashMap<Column, Object>();
        core.getValues(record).forEach((c, v) -> map.put(new ColumnImpl((st.orm.core.template.impl.ColumnImpl) c), v));
        return map;
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
        return core.getValue(((ColumnImpl) column).core(), record);
    }
}
