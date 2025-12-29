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
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.impl.TableName;
import st.orm.template.Column;
import st.orm.template.Model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static java.util.List.copyOf;

/**
 * Represents the model of an entity.
 */
public record ModelImpl<E extends Data, ID>(
        @Nonnull st.orm.core.template.impl.ModelImpl<E, ID> core,
        @Nonnull TableName tableName,
        @Nonnull Class<E> type,
        @Nonnull Class<ID> primaryKeyType,
        @Nonnull List<Column> columns) implements Model<E, ID> {

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
                        .map(ColumnImpl::new)
                        .map(Column.class::cast)
                        .toList()
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
     * Extracts values from the given record and passes them to the provided consumer.
     *
     * <p>This method iterates over all columns mapped by the entity or projection and invokes the consumer once per
     * column with the corresponding extracted value.</p>
     *
     * <p>The consumer is invoked in a stable order that matches the column order of the underlying model.</p>
     *
     * <p>The extracted value may be {@code null} if the corresponding field value is {@code null}.</p>
     *
     * <p>This method does not allocate intermediate collections and does not mutate the record. It is intended for
     * efficient value extraction and binding, for example, when preparing SQL statements.</p>
     *
     * @param record the record to extract values from
     * @param consumer receives each column together with its extracted value
     * @since 1.7
     */
    @Override
    public void forEachValue(@Nonnull E record, @Nonnull Predicate<Column> filter, @Nonnull BiConsumer<Column, Object> consumer) throws SqlTemplateException {
        core.forEachValue(record,
                column -> filter.test(new ColumnImpl(column)),
                (column, value) -> consumer.accept(new ColumnImpl(column), value));
    }

    /**
     * Collects extracted values into a map, optionally filtering which columns to include.
     *
     * <p>The returned map preserves iteration order. Its iteration order matches the model's stable column order.</p>
     *
     * <p>Values are the same JDBC-ready values as produced by {@link #forEachValue(E, BiConsumer)}.</p>
     *
     * @param record the record (entity or projection instance) to extract values from.
     * @param filter predicate that decides whether a column should be included.
     * @return a {@link Map} containing columns and their extracted (JDBC-ready) values in the order of the model.
     * @throws SqlTemplateException if an error occurs during value extraction.
     * @since 1.7
     */
    @Override
    public Map<Column, Object> values(@Nonnull E record, @Nonnull Predicate<Column> filter) throws SqlTemplateException {
        Map<Column, Object> map = new LinkedHashMap<>();
        core.forEachValue(record,
                column -> filter.test(new ColumnImpl(column)),
                (column, value) -> map.put(new ColumnImpl(column), value));
        return map;
    }
}
