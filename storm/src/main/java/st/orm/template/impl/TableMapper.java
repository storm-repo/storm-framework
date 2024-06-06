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
import jakarta.annotation.Nullable;
import st.orm.template.SqlTemplateException;

import java.lang.reflect.RecordComponent;
import java.util.List;

import static java.util.List.copyOf;

public interface TableMapper {
    record Mapping(@Nonnull String alias, @Nonnull List<RecordComponent> components, boolean primaryKey, @Nullable String path) {
        public Mapping {
            components = copyOf(components); // Defensive copy.
        }
    }

    Mapping getMapping(@Nonnull Class<? extends Record> table, @Nullable String path) throws SqlTemplateException;

    void mapPrimaryKey(@Nonnull Class<? extends Record> table, @Nonnull String alias, @Nonnull List<RecordComponent> components, @Nullable String path);

    void mapForeignKey(@Nonnull Class<? extends Record> table, @Nonnull String alias, @Nonnull RecordComponent component, @Nullable String path);
}
