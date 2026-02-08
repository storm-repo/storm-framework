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
package st.orm.template

import st.orm.Data
import st.orm.core.template.SqlTemplateException
import kotlin.reflect.KClass

/**
 * Provides metadata about an entity or projection type, including its table name, primary key type, and column
 * definitions.
 *
 * The `Model` is obtained via [QueryTemplate.model] or from a repository's `model` property. It can be used to
 * introspect the database mapping of a data class type, access column metadata, check primary key defaults, and
 * extract column values from record instances.
 *
 * ## Example
 * ```kotlin
 * val model = orm.model(User::class)
 * val tableName = model.name
 * val columns = model.columns
 * ```
 *
 * @param E the type of the entity or projection.
 * @param ID the type of the primary key, or `Void` in case of a projection without a primary key.
 * @see Column
 * @see QueryTemplate.model
 */
interface Model<E : Data, ID : Any> {
    /**
     * Returns the schema, or an empty String if the schema is not specified.
     *
     * @return the schema, or an empty String if the schema is not specified.
     */
    val schema: String

    /**
     * Returns the name of the table or view.
     *
     * @return the name of the table or view.
     */
    val name: String

    /**
     * Returns the type of the entity or projection.
     *
     * @return the type of the entity or projection.
     */
    val type: KClass<E>

    /**
     * Returns the type of the primary key.
     *
     * @return the type of the primary key.
     */
    val primaryKeyType: KClass<ID>

    /**
     * Returns an immutable list of columns in the entity or projection.
     *
     * @return an immutable list of columns in the entity or projection.
     */
    val columns: List<Column>

    /**
     * Returns the columns declared directly on this model.
     *
     * <p>Relationship expansion is not applied. The returned list preserves declared order.</p>
     *
     * <p><strong>Index semantics:</strong> {@link Column#index()} refers to the index in {@link #columns()},
     * not in this list.</p>
     *
     * @return the declared columns of this model.
     * @since 1.8
     */
    val declaredColumns: List<Column>

    /**
     *
     * This method is used to check if the primary key of the entity is a default value. This is useful when
     * determining if the entity is new or has been persisted before.
     *
     * @param pk primary key to check.
     * @return {code true} if the specified primary key represents a default value, `false` otherwise.
     * @since 1.2
     */
    fun isDefaultPrimaryKey(pk: ID?): Boolean

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
    fun forEachValue(
        columns: List<Column>,
        record: E,
        consumer: (Column, Any?) -> Unit
    )

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
    fun values(
        columns: List<Column>,
        record: E
    ): Map<Column, Any?> {
        val values = mutableMapOf<Column, Any?>()
        forEachValue(columns, record) { column, value ->
            values[column] = value
        }
        return values
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
    fun values(
        record: E
    ): Map<Column, Any?> = values(columns, record)
}
