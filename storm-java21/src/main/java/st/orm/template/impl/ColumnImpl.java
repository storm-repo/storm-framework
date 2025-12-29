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
import st.orm.Data;
import st.orm.GenerationStrategy;
import st.orm.Metamodel;
import st.orm.template.Column;

/**
 * Represents a column in a database table.
 *
 * @param core the underlying column.
 * @param index the 1-based index of the column.
 * @param name the name of the column.
 * @param type the Java type of the column.
 * @param primaryKey whether the column is a primary key.
 * @param generation the generation strategy.
 * @param sequence the sequence name.
 * @param foreignKey whether the column is a foreign key.
 * @param nullable whether the column is nullable.
 * @param insertable whether the column is insertable.
 * @param updatable whether the column is updatable.
 * @param version whether the column is a version column.
 * @param ref whether the column is a lazily fetched record.
 * @param metamodel the metamodel for the column.
 */
public record ColumnImpl(
        @Nonnull st.orm.core.template.Column core,
        int index,
        @Nonnull String name,
        @Nonnull Class<?> type,
        boolean primaryKey,
        @Nonnull GenerationStrategy generation,
        @Nonnull String sequence,
        boolean foreignKey,
        boolean nullable,
        boolean insertable,
        boolean updatable,
        boolean version,
        boolean ref,
        Metamodel<? extends Data, ?> metamodel
) implements Column {

    public ColumnImpl(@Nonnull st.orm.core.template.Column column) {
        this(
                column,
                column.index(),
                column.name(),
                column.type(),
                column.primaryKey(),
                column.generation(),
                column.sequence(),
                column.foreignKey(),
                column.nullable(),
                column.insertable(),
                column.updatable(),
                column.version(),
                column.ref(),
                column.metamodel()
        );
    }
}
