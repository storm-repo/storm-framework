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
import jakarta.annotation.Nullable;
import st.orm.Data;
import st.orm.Metamodel;
import st.orm.mapping.ColumnNameResolver;
import st.orm.mapping.ForeignKeyResolver;
import st.orm.mapping.RecordField;
import st.orm.core.template.SqlDialect;
import st.orm.core.template.SqlTemplate;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.impl.Elements.TableSource;
import st.orm.core.template.impl.Elements.TableTarget;
import st.orm.core.template.impl.Elements.TemplateSource;
import st.orm.core.template.impl.Elements.TemplateTarget;

import static st.orm.ResolveScope.INNER;
import static st.orm.core.template.impl.RecordReflection.findRecordField;
import static st.orm.core.template.impl.RecordReflection.getFkFields;
import static st.orm.core.template.impl.RecordReflection.getForeignKeys;
import static st.orm.core.template.impl.RecordReflection.findPkField;
import static st.orm.core.template.impl.RecordReflection.getPrimaryKeys;
import static st.orm.core.template.impl.RecordReflection.getTableName;
import static st.orm.core.template.impl.RecordValidation.validateDataType;

final class JoinProcessor implements ElementProcessor<Join> {

    private final SqlTemplateProcessor templateProcessor;
    private final SqlTemplate template;
    private final SqlDialectTemplate dialectTemplate;
    private final TableUse tableUse;
    private final AliasMapper aliasMapper;

    JoinProcessor(@Nonnull SqlTemplateProcessor templateProcessor) {
        this.templateProcessor = templateProcessor;
        this.template = templateProcessor.template();
        this.dialectTemplate = templateProcessor.dialectTemplate();
        this.tableUse = templateProcessor.tableUse();
        this.aliasMapper = templateProcessor.aliasMapper();
    }

    /**
     * Process a join element of a template.
     *
     * @param join the join element to process.
     * @return the result of processing the element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    @Override
    public ElementResult process(@Nonnull Join join) throws SqlTemplateException {
        if (join.autoJoin() && join.source() instanceof TableSource(var table)) {
            // Prune the join if the table is not referenced in the template, for instance, in case of a SelectMode.FLAT.
            return new ElementResult(() -> tableUse.isReferenced(table, join.sourceAlias()) ? getJoinString(join) : "");
        }
        return new ElementResult(getJoinString(join));
    }

    private String getJoinString(@Nonnull Join join) throws SqlTemplateException {
        var dialect = template.dialect();
        var columnNameResolver = template.columnNameResolver();
        var foreignKeyResolver = template.foreignKeyResolver();
        var tableNameResolver = template.tableNameResolver();
        String joinType = join.type().sql();
        String onClause = join.type().hasOnClause() ? switch (join.target()) {
            case TableTarget(var toTable) when join.source() instanceof TableSource(var fromTable) ->
                    buildJoinCondition(fromTable, join.sourceAlias(), toTable, join.targetAlias(),
                            columnNameResolver, foreignKeyResolver, dialect);
            case TemplateTarget ts -> templateProcessor.parse(ts.template(), true);
            default -> throw new SqlTemplateException("Unsupported join target.");
        } : "";
        return switch (join.source()) {
            case TableSource ts -> {
                var table = getTableName(ts.table(), tableNameResolver);
                var alias = aliasMapper.useAlias(ts.table(), join.sourceAlias());
                yield dialectTemplate.process("\n\0 \0 \0\0", joinType, table, alias, onClause.isEmpty() ? "" : " ON " + onClause);
            }
            case TemplateSource ts -> {
                var source = templateProcessor.parse(ts.template(), false);
                var alias = join.sourceAlias();
                yield dialectTemplate.process("\n\0 (\0) \0\0", joinType, source, alias, onClause.isEmpty() ? "" : " ON " + onClause);
            }
        };
    }

    private String buildJoinCondition(
            @Nonnull Class<? extends Data> fromTable,
            @Nonnull String alias,
            @Nonnull Class<? extends Data> toTable,
            @Nullable String toAlias,
            @Nonnull ColumnNameResolver columnNameResolver,
            @Nonnull ForeignKeyResolver foreignKeyResolver,
            @Nonnull SqlDialect dialect
    ) throws SqlTemplateException {
        var rightComponent = findRecordField(getFkFields(toTable).toList(), fromTable);
        if (rightComponent.isPresent()) {
            validateDataType(fromTable, true);
            // Joins foreign key of right table to the primary key of left table.
            return buildJoinCondition(fromTable, alias, toTable, toAlias, rightComponent.get(),
                    findPkField(fromTable).orElseThrow(), columnNameResolver, foreignKeyResolver, dialect);
        }
        var leftComponent = findRecordField(getFkFields(fromTable).toList(), toTable);
        if (leftComponent.isPresent()) {
            validateDataType(toTable, true);
            // Joins foreign key of left table to the primary key of right table.
            return buildJoinCondition(toTable, toAlias, fromTable, alias, leftComponent.get(),
                    findPkField(toTable).orElseThrow(), columnNameResolver, foreignKeyResolver, dialect);
        }
        throw new SqlTemplateException(
                "Failed to join %s with %s. No matching foreign key found.".formatted(fromTable.getSimpleName(), toTable.getSimpleName()));
    }

    @SuppressWarnings("DuplicatedCode")
    private String buildJoinCondition(
            @Nonnull Class<? extends Data> fromTable,
            @Nullable String fromAlias,
            @Nonnull Class<? extends Data> toTable,
            @Nullable String toAlias,
            @Nonnull RecordField left,
            @Nonnull RecordField right,
            @Nonnull ColumnNameResolver columnNameResolver,
            @Nonnull ForeignKeyResolver foreignKeyResolver,
            @Nonnull SqlDialect dialect
    ) throws SqlTemplateException {
        fromAlias = fromAlias == null ? aliasMapper.getAlias(Metamodel.root(fromTable), INNER, dialect) : fromAlias;
        toAlias = toAlias == null ? aliasMapper.getAlias(Metamodel.root(toTable), INNER, dialect,
                () -> new SqlTemplateException("Table alias missing for: %s".formatted(toTable.getSimpleName()))) : toAlias;
        var fkColumns = getForeignKeys(left, foreignKeyResolver, columnNameResolver);
        var pkColumns = getPrimaryKeys(right, foreignKeyResolver, columnNameResolver);
        if (fkColumns.size() != pkColumns.size()) {
            throw new SqlTemplateException("Mismatch in PK/FK columns between tables.");
        }
        StringBuilder joinCondition = new StringBuilder();
        for (int i = 0; i < fkColumns.size(); i++) {
            if (i > 0) {
                joinCondition.append(" AND ");
            }
            joinCondition.append(dialectTemplate.process("\0.\0 = \0.\0", toAlias, fkColumns.get(i), fromAlias, pkColumns.get(i)));
        }
        return joinCondition.toString();
    }
}