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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.Element;
import st.orm.core.template.SqlTemplate;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.impl.Elements.Delete;
import st.orm.core.template.impl.Elements.From;
import st.orm.core.template.impl.Elements.Insert;
import st.orm.core.template.impl.Elements.Select;
import st.orm.core.template.impl.Elements.TableSource;
import st.orm.core.template.impl.Elements.Update;
import st.orm.core.template.impl.SqlTemplateImpl.Wrapped;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Optional.empty;
import static st.orm.core.template.impl.RecordReflection.getTableName;
import static st.orm.core.template.impl.RecordValidation.validateDataType;
import static st.orm.core.template.impl.RecordValidation.validateWhere;

class QueryModelFactory {

    private final SqlTemplate template;
    private final ModelBuilder modelBuilder;

    public QueryModelFactory(@Nonnull SqlTemplate template, @Nonnull ModelBuilder modelBuilder) {
        this.template = template;
        this.modelBuilder = modelBuilder;
    }

    public Optional<QueryModel> getQueryModel(@Nonnull List<Element> elements,
                                              @Nonnull TableMapper tableMapper,
                                              @Nonnull AliasMapper aliasMapper) throws SqlTemplateException{
        var mutableElements = new ArrayList<>(elements);
        elements = List.copyOf(mutableElements);
        var primaryTable = getPrimaryTable(elements, aliasMapper).orElse(null);
        if (primaryTable == null) {
            return empty();
        }
        var aliasedTable = new AliasedTable(
                primaryTable.table(),
                getTableName(primaryTable.table(), template.tableNameResolver()).qualified(template.dialect()),
                primaryTable.alias().isEmpty() ? "" : template.dialect().getSafeIdentifier(primaryTable.alias()));
        validateDataType(aliasedTable.type());
        validateWhere(elements);
        return Optional.of(new QueryModelImpl(template, modelBuilder, aliasedTable, tableMapper, aliasMapper));
    }

    /**
     * Returns the primary table and its alias in the sql statement, such as the table in the FROM clause for a SELECT
     * or DELETE, or the table in the INSERT or UPDATE clause.
     *
     * @param elements all elements in the sql statement.
     * @param aliasMapper a mapper of table classes to their aliases.
     * @return the primary table for the sql statement.
     * @throws SqlTemplateException if no primary table is found or if multiple primary tables are found.
     */
    private Optional<PrimaryTable> getPrimaryTable(@Nonnull List<Element> elements,
                                                   @Nonnull AliasMapper aliasMapper) throws SqlTemplateException {
        assert elements.stream().noneMatch(Wrapped.class::isInstance);
        PrimaryTable primaryTable = elements.stream()
                .filter(From.class::isInstance)
                .map(From.class::cast)
                .map(f -> {
                    if (f.source() instanceof TableSource(var t)) {
                        return new PrimaryTable(t, f.alias());
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .findAny()
                .orElse(null);
        if (primaryTable != null) {
            return Optional.of(primaryTable);
        }
        primaryTable = elements.stream()
                .map(element -> switch(element) {
                    case Insert it -> new PrimaryTable(it.table(), "");
                    case Update it -> new PrimaryTable(it.table(), it.alias());
                    case Delete it -> new PrimaryTable(it.table(), "");
                    default -> null;
                })
                .filter(Objects::nonNull)
                .findAny()
                .orElse(null);
        if (primaryTable != null) {
            return Optional.of(primaryTable);
        }
        var select = elements.stream()
                .filter(Select.class::isInstance)
                .map(Select.class::cast)
                .findAny();
        if (select.isPresent()) {
            return Optional.of(new PrimaryTable(select.get().table(),
                    aliasMapper.getPrimaryAlias(select.get().table()).orElse("")));
        }
        return empty();
    }
}
