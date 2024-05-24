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
package st.orm.repository;

import jakarta.annotation.Nonnull;

import java.util.List;

import static java.util.List.copyOf;

/**
 * Represents the model of an entity.
 *
 * @param <E> the type of the entity.
 * @param <ID> the type of the entity's primary key.
 * @param tableName the name of the table, may include the schema.
 * @param type the Java type of the entity.
 * @param primaryKeyType the Java type of the entity's primary key.
 * @param columns an immutable list of columns in the entity.
 */
public record EntityModel<E, ID>(
        @Nonnull String tableName,
        @Nonnull Class<E> type,
        @Nonnull Class<ID> primaryKeyType,
        @Nonnull List<Column> columns) {

    public EntityModel {
        columns = copyOf(columns); // Defensive copy.
    }
}
