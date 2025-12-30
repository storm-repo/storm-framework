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
package st.orm.core.template;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.Data;
import st.orm.mapping.RecordType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Represents the model of an entity or projection.
 *
 * @param <E> the type of the entity or projection.
 * @param <ID> the type of the primary key, or {@code Void} in case of a projection without a primary key.
 */
public interface Model<E extends Data, ID> {

    /**
     * Returns the schema, or an empty String if the schema is not specified.
     *
     * @return the schema, or an empty String if the schema is not specified.
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
    Class<E> type();

    /**
     * Returns the type of the primary key.
     *
     * @return the type of the primary key.
     */
    Class<ID> primaryKeyType();

    /**
     * Returns an immutable list of columns in the entity or projection.
     *
     * @return an immutable list of columns in the entity or projection.
     */
    List<Column> columns();

    /**
     * Returns the type of the record.
     *
     * @return the type of the record.
     * @since 1.7
     */
    RecordType recordType();

    /**
     * <p>This method is used to check if the primary key of the entity is a default value. This is useful when
     * determining if the entity is new or has been persisted before.</p>
     *
     * @param pk primary key to check.
     * @return {code true} if the specified primary key represents a default value, {@code false} otherwise.
     * @since 1.2
     */
    boolean isDefaultPrimaryKey(@Nullable ID pk);

    /**
     * Extracts column values from the given record and feeds them to a consumer in model column order.
     *
     * <p>The values produced by this method are the same values that would be presented to the JDBC / data layer.
     * This means conversions have already been applied:</p>
     *
     * <ul>
     *   <li>{@code Ref<T>} is unpacked to its underlying primary-key value.</li>
     *   <li>Foreign-key fields are represented by their primary-key value.</li>
     *   <li>Java time types are converted to their JDBC-compatible counterparts (for example {@code LocalDate} to
     *       {@code java.sql.Date}, {@code LocalDateTime} to {@code java.sql.Timestamp}, etc.).</li>
     * </ul>
     *
     * <p>The consumer is invoked once per mapped column, in a stable order that matches the column order of the
     * underlying model (entity or projection). The extracted value may be {@code null}.</p>
     *
     * <p>This method does not allocate intermediate collections and does not mutate the record. It is intended for
     * efficient value extraction and binding, for example, when preparing SQL statements.</p>
     *
     * @param record the record to extract values from.
     * @param consumer receives each mapped column together with its extracted (JDBC-ready) value.
     * @throws SqlTemplateException if an error occurs during value extraction.
     * @since 1.7
     */
    default void forEachValue(@Nonnull E record, @Nonnull BiConsumer<Column, Object> consumer) throws SqlTemplateException {
        forEachValue(record, column -> true, consumer);
    }

    /**
     * Extracts column values from the given record and feeds them to a consumer in model column order,
     * limited to columns accepted by {@code columnFilter}.
     *
     * <p>See {@link #forEachValue(Data, BiConsumer)} for details about ordering and the produced value types.
     * In short: the produced values are JDBC-ready and already converted (refs and foreign keys unpacked to ids,
     * Java time converted to JDBC time types).</p>
     *
     * @param record the record (entity or projection instance) to extract values from
     * @param filter predicate that decides whether a column should be visited
     * @param consumer receives each visited column together with its extracted (JDBC-ready) value
     * @throws SqlTemplateException if an error occurs during value extraction
     * @since 1.7
     */
    void forEachValue(
            @Nonnull E record,
            @Nonnull Predicate<Column> filter,
            @Nonnull BiConsumer<Column, Object> consumer
    ) throws SqlTemplateException;

    /**
     * Collects extracted values into a map, optionally filtering which columns to include.
     *
     * <p>The returned map preserves iteration order. Its iteration order matches the model's stable column order.</p>
     *
     * <p>Values are the same JDBC-ready values as produced by {@link #forEachValue(Data, BiConsumer)}.</p>
     *
     * @param record the record (entity or projection instance) to extract values from.
     * @param filter predicate that decides whether a column should be included.
     * @return a {@link Map} containing columns and their extracted (JDBC-ready) values in the order of the model.
     * @throws SqlTemplateException if an error occurs during value extraction.
     * @since 1.7
     */
    default Map<Column, Object> values(@Nonnull E record, @Nonnull Predicate<Column> filter)
            throws SqlTemplateException {
        Map<Column, Object> values = new LinkedHashMap<>();
        forEachValue(record, filter, values::put);
        return values;
    }
}
