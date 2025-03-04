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
package st.orm.template;

import jakarta.annotation.Nonnull;

/**
 * Represents a column in a database table.
 */
public interface Column {
    /**
     * Gets the 1-based index of the column.
     *
     * @return the column index.
     */
    int index();

    /**
     * Gets the name of the column.
     *
     * @return the column name.
     */
    String name();

    /**
     * Gets the qualified name of the column including escape characters where necessary.
     *
     * @param dialect the SQL dialect.
     * @return the qualified column name.
     */
    String qualifiedName(@Nonnull SqlDialect dialect);

    /**
     * Gets the type of the column.
     *
     * @return the type of the column.
     */
    Class<?> type();

    /**
     * Determines if the column is a primary key.
     *
     * @return true if it is a primary key, false otherwise.
     */
    boolean primaryKey();

    /**
     * Determines if the column is auto-generated, which is typical for primary keys.
     *
     * @return true if auto-generated, false otherwise.
     */
    boolean autoGenerated();

    /**
     * Determines if the column is a foreign key.
     *
     * @return true if it is a foreign key, false otherwise.
     */
    boolean foreignKey();

    /**
     * Determines if the column is nullable.
     *
     * @return true if the column can be null, false otherwise.
     */
    boolean nullable();

    /**
     * Determines if the column is insertable.
     *
     * @return true if the column can be inserted, false otherwise.
     */
    boolean insertable();

    /**
     * Determines if the column is updatable.
     *
     * @return true if the column can be updated, false otherwise.
     */
    boolean updatable();

    /**
     * Determines if the column is used for versioning.
     *
     * @return true if it is a version column, false otherwise.
     */
    boolean version();

    /**
     * Determines if the column is lazily fetched.
     *
     * @return true if it is lazily loaded, false otherwise.
     */
    boolean lazy();
}