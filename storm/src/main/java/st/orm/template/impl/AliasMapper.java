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

import java.util.List;
import java.util.Optional;

import static java.util.Optional.empty;

interface AliasMapper {

    default String useAlias(@Nonnull Class<? extends Record> table, @Nonnull String alias) throws SqlTemplateException {
        if (getAliases(table).stream().noneMatch(a -> a.equals(alias))) {
            throw new SqlTemplateException(STR."Alias \{alias} for table \{table.getSimpleName()} not found.");
        }
        return alias;
    }

    List<String> getAliases(@Nonnull Class<? extends Record> table);

    /**
     * Returns the primary alias for the specified table.
     *
     * @param table the table to get the alias for.
     * @return the primary alias.
     */
    default Optional<String> getPrimaryAlias(@Nonnull Class<? extends Record> table) {
        var list = getAliases(table);
        if (list.isEmpty()) {
            return empty();
        }
        return Optional.of(list.getFirst());
    }

    default String getAlias(@Nonnull Class<? extends Record> table, @Nullable String path) throws SqlTemplateException {
        return getAlias(table, path, true);
    }

    String getAlias(@Nonnull Class<? extends Record> table, @Nullable String path, boolean force) throws SqlTemplateException;

    String generateAlias(@Nonnull Class<? extends Record> table, @Nullable String path) throws SqlTemplateException;

    void setAlias(@Nonnull Class<? extends Record> table, @Nonnull String alias, @Nullable String path) throws SqlTemplateException;
}
