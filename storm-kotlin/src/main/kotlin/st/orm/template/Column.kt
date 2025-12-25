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
package st.orm.template

import st.orm.Data
import st.orm.GenerationStrategy
import st.orm.Metamodel
import kotlin.reflect.KClass

/**
 * Represents a column in a database table.
 */
interface Column {
    /**
     * Gets the 1-based index of the column.
     *
     * @return the column index.
     */
    val index: Int

    /**
     * Gets the name of the column.
     *
     * @return the column name.
     */
    val name: String

    /**
     * Gets the type of the column.
     *
     * @return the type of the column.
     */
    val type: KClass<*>

    /**
     * Determines if the column is a primary key.
     *
     * @return true if it is a primary key, false otherwise.
     */
    val primaryKey: Boolean

    /**
     * Gets the generation strategy for the primary key.
     *
     * @return the generation strategy for the primary key.
     */
    val generation: GenerationStrategy

    /**
     * Gets the name of the sequence to use for generating values for the primary key, or null if the column is not a
     * primary key or does not require a sequence.
     */
    val sequence: String?

    /**
     * Determines if the column is a foreign key.
     *
     * @return true if it is a foreign key, false otherwise.
     */
    val foreignKey: Boolean

    /**
     * Determines if the column is nullable.
     *
     * @return true if the column can be null, false otherwise.
     */
    val nullable: Boolean

    /**
     * Determines if the column is insertable.
     *
     * @return true if the column can be inserted, false otherwise.
     */
    val insertable: Boolean

    /**
     * Determines if the column is updatable.
     *
     * @return true if the column can be updated, false otherwise.
     */
    val updatable: Boolean

    /**
     * Determines if the column is used for versioning.
     *
     * @return true if it is a version column, false otherwise.
     */
    val version: Boolean

    /**
     * Determines if the column is a ref column.
     *
     * @return if the column is a ref column, false otherwise.
     */
    val ref: Boolean

    /**
     * Gets the meta model of the column.
     *
     * @return the meta model of the column.
     * @since 1.7
     */
    val metaModel: Metamodel<out Data, *>
}