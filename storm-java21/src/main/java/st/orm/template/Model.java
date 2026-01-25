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
package st.orm.template;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.Data;
import st.orm.core.template.SqlTemplateException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.function.BiConsumer;

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
     * Returns all columns of this model, including columns of expanded relationships.
     *
     * <p>The returned list is deterministic and stable. Declared columns are processed in declaration order.
     * Foreign relationships are expanded depth-first at the position of the foreign-key column.</p>
     *
     * <p>Expanded columns always correspond to physical columns of the parent record. Join keys come from the
     * parent row, not the referenced row.</p>
     *
     * @return all columns of this model, including expanded relations.
     * @since 1.7
     */
    List<Column> columns();

    /**
     * Returns the columns declared directly on this model.
     *
     * <p>Relationship expansion is not applied. The returned list preserves declared order.</p>
     *
     * <p><strong>Index semantics:</strong> {@link st.orm.core.template.Column#index()} refers to the index in {@link #columns()},
     * not in this list.</p>
     *
     * @return the declared columns of this model.
     * @since 1.8
     */
    List<Column> declaredColumns();

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
     * Iterates over the values of the given columns for the supplied record.
     *
     * <p>Values are JDBC-ready. Conversions have already been applied.</p>
     *
     * <p><strong>Ordering requirement:</strong> {@code columns} must be ordered according to the model's
     * column order (usually {@link #columns()} or {@link #declaredColumns()}).</p>
     *
     * @param columns the columns to extract values for, ordered in model column order.
     * @param record the record to extract values from.
     * @param consumer receives each column and its extracted value.
     * @throws SqlTemplateException if extraction fails.
     * @since 1.8
     */
    void forEachValue(@Nonnull List<Column> columns,
                      @Nonnull E record,
                      @Nonnull BiConsumer<Column, Object> consumer)
            throws SqlTemplateException;

    /**
     * Collects column values into an ordered map.
     *
     * <p><strong>Ordering requirement:</strong> {@code columns} must be ordered according to the model's
     * column order (usually {@link #columns()} or {@link #declaredColumns()}).</p>
     *
     * @param columns the columns to extract values for.
     * @param record the record to extract values from.
     * @return a map of columns to extracted values.
     * @throws SqlTemplateException if extraction fails.
     * @since 1.8
     */
    default SequencedMap<Column, Object> values(@Nonnull List<Column> columns,
                                                @Nonnull E record)
            throws SqlTemplateException {
        var values = new LinkedHashMap<Column, Object>();
        forEachValue(columns, record, values::put);
        return values;
    }

    /**
     * Collects all column values into an ordered map.
     *
     * <p>This method is equivalent to {@link #values(List, Data)} with {@link #columns()}.</p>
     *
     * @param record the record to extract values from.
     * @return a map of columns to extracted values.
     * @throws SqlTemplateException if extraction fails.
     * @since 1.8
     */
    default SequencedMap<Column, Object> values(@Nonnull E record) throws SqlTemplateException {
        return values(columns(), record);
    }
}
