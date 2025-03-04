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
import st.orm.spi.Name;
import st.orm.template.SqlDialect;
import st.orm.template.Column;

/**
 * Represents a column in a database table.
 *
 * @param columnName the name of the column.
 * @param index the 1-based index of the column.
 * @param type the type of the column.
 * @param primaryKey whether the column is a primary key.
 * @param autoGenerated whether the column is auto-generated, in case of a primary key.
 * @param foreignKey whether the column is a foreign key.
 * @param nullable whether the column is nullable.
 * @param insertable whether the column is insertable.
 * @param updatable whether the column is updatable.
 * @param version whether the column is a version column.
 * @param lazy whether the column is a lazily fetched record.
 */
public record ColumnImpl(
        @Nonnull Name columnName,
        int index,
        @Nonnull Class<?> type,
        boolean primaryKey,
        boolean autoGenerated,
        boolean foreignKey,
        boolean nullable,
        boolean insertable,
        boolean updatable,
        boolean version,
        boolean lazy
) implements Column {

    /**
     * Gets the name of the column.
     *
     * @return the column name.
     */
    @Override
    public String name() {
        return columnName.name();
    }

    /**
     * Gets the qualified name of the column including escape characters where necessary.
     *
     * @param dialect the SQL dialect.
     * @return the qualified column name.
     */
    @Override
    public String qualifiedName(@Nonnull SqlDialect dialect) {
        return columnName.getQualifiedName(dialect);
    }
}
