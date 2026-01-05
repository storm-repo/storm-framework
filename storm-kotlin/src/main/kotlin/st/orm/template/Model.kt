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
 * Represents the model of an entity or projection.
 *
 * @param <E> the type of the entity or projection.
 * @param <ID> the type of the primary key, or `Void` in case of a projection without a primary key.
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
     * Iterates over all mapped columns of the given record and emits their extracted values
     * in stable model column order.
     *
     * The emitted values are the same values that would be passed to the JDBC / data layer.
     * All conversions have already been applied:
     *
     * - `Ref<T>` values are unpacked to their primary-key value
     * - Foreign-key fields are represented by their primary-key value
     * - Java time types are converted to JDBC-compatible types
     *   (for example `LocalDate` → `java.sql.Date`,
     *   `LocalDateTime` → `java.sql.Timestamp`)
     *
     * The consumer is invoked once per mapped column. Values may be `null`.
     *
     * This function does not allocate intermediate collections and does not mutate the record.
     * It is intended for efficient value extraction and binding, for example when preparing
     * SQL statements.
     *
     * @param record the record (entity or projection) to extract values from
     * @param consumer receives each column together with its extracted JDBC-ready value
     * @throws SqlTemplateException if value extraction fails
     * @since 1.7
     */
    fun forEachValue(
        record: E,
        consumer: (Column, Any?) -> Unit
    ) = forEachValue(record, filter = { true }, consumer)

    /**
     * Iterates over mapped columns of the given record and emits their extracted values
     * in stable model column order, limited to columns accepted by [filter].
     *
     * Value semantics and ordering are identical to
     * [forEachValue(record, consumer)][forEachValue].
     *
     * @param record the record (entity or projection) to extract values from
     * @param filter predicate deciding which columns are visited
     * @param consumer receives each visited column together with its extracted JDBC-ready value
     * @throws SqlTemplateException if value extraction fails
     * @since 1.7
     */
    fun forEachValue(
        record: E,
        filter: (Column) -> Boolean,
        consumer: (Column, Any?) -> Unit
    )

    /**
     * Collects extracted column values into a map, optionally filtering which columns to include.
     *
     * The returned map preserves iteration order. Its iteration order matches the stable
     * column order of the underlying model.
     *
     * Values are the same JDBC-ready values as produced by [forEachValue].
     *
     * @param record the record (entity or projection) to extract values from
     * @param filter predicate deciding which columns are included
     * @return a map of columns to their extracted JDBC-ready values, in model order
     * @throws SqlTemplateException if value extraction fails
     * @since 1.7
     */
    fun values(
        record: E,
        filter: (Column) -> Boolean
    ): Map<Column, Any?>
}
