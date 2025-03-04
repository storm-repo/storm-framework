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
package st.orm.kotlin.template;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import kotlin.reflect.KClass;
import st.orm.template.SqlDialect;

import java.util.List;
import java.util.SequencedMap;

/**
 * Represents the model of an entity.
 */
public interface KModel<E extends Record, ID> {

    /**
     * Returns the schema, or empty String if the schema is not specified.
     *
     * @return the schema, or empty String if the schema is not specified.
     */
    String schema();

    /**
     * Returns the name of the table or view.
     *
     * @return the name of the table or view.
     */
    String name();

    /**
     * Returns the qualified name of the table or view, including the schema and escape characters where necessary.
     *
     * @return the qualified name of the table or view, including the schema and escape characters where necessary.
     */
    String qualifiedName(@Nonnull SqlDialect dialect);

    /**
     * Returns the type of the entity or projection.
     *
     * @return the type of the entity or projection.
     */
    KClass<E> type();

    /**
     * Returns the type of the primary key.
     *
     * @return the type of the primary key.
     */
    KClass<ID> primaryKeyType();

    /**
     * Returns an immutable list of columns in the entity or projection.
     *
     * @return an immutable list of columns in the entity or projection.
     */
    List<KColumn> columns();

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
    boolean isDefaultPrimaryKey(@Nullable ID pk);

    /**
     * Extracts the value for the specified column from the given record.
     *
     * @param column the column to extract the value for.
     * @param record the record to extract the value from.
     * @return the value for the specified column from the given record.
     * @since 1.2
     */
    Object getValue(@Nonnull KColumn column, @Nonnull E record);

    /**
     * Extracts the values from the given record and maps them to the columns of the entity or projection.
     *
     * @param record the record to extract the values from.
     * @return the values from the given record mapped to the columns of the entity or projection.
     * @since 1.2
     */
    SequencedMap<KColumn, Object> getValues(@Nonnull E record);
}
