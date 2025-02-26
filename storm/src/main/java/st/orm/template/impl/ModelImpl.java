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
import st.orm.PersistenceException;
import st.orm.template.Column;
import st.orm.template.Model;
import st.orm.template.SqlTemplateException;

import java.util.List;
import java.util.SequencedMap;

import static java.util.List.copyOf;

/**
 * Represents the model of an entity or projection.
 *
 * @param <E> the type of the entity or projection.
 * @param <ID> the type of the primary key, or {@code Void} in case of a projection without a primary key.
 * @param name the name of the table or view, may include the schema.
 * @param type the Java type of the entity or projection.
 * @param primaryKeyType the Java type of the primary key.
 * @param columns an immutable list of columns in the entity or projection.
 */
public record ModelImpl<E extends Record, ID>(
        @Nonnull String name,
        @Nonnull Class<E> type,
        @Nonnull Class<ID> primaryKeyType,
        @Nonnull List<Column> columns) implements Model<E, ID> {

    public ModelImpl {
        columns = copyOf(columns); // Defensive copy.
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
}
