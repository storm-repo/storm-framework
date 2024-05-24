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
import st.orm.template.SqlTemplate.AliasResolveStrategy;
import st.orm.template.SqlTemplateException;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Optional;

import static java.util.Optional.empty;

interface AliasMapper {

    String useAlias(@Nonnull Class<? extends Record> table, @Nonnull String alias) throws SqlTemplateException;

    List<String> getAliases(@Nonnull Class<? extends Record> table);

    default Optional<String> resolveAlias(@Nonnull Class<? extends Record> table, @Nonnull AliasResolveStrategy aliasResolveStrategy) {
        var set = getAliases(table);
        if (set.isEmpty()) {
            return empty();
        }
        if (set.size() > 1) {
            if (aliasResolveStrategy != AliasResolveStrategy.FIRST) {
                return empty();
            }
        }
        return Optional.of(set.getFirst());
    }

    String getAlias(@Nonnull Class<? extends Record> table, @Nonnull AliasResolveStrategy aliasResolveStrategy) throws SqlTemplateException;

    String generateAlias(@Nonnull Class<? extends Record> table) throws SqlTemplateException;

    String getAlias(@Nonnull List<RecordComponent> path) throws SqlTemplateException;

    String generateAlias(@Nonnull List<RecordComponent> path) throws SqlTemplateException;
}
