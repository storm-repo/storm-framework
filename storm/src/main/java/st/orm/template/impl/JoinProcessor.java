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
import st.orm.template.SqlTemplate;
import st.orm.template.SqlTemplateException;

import java.util.function.Supplier;

import static st.orm.template.Metamodel.root;
import static st.orm.template.ResolveScope.INNER;
import static st.orm.template.impl.RecordReflection.findComponent;
import static st.orm.template.impl.RecordReflection.getColumnName;
import static st.orm.template.impl.RecordReflection.getFkComponents;
import static st.orm.template.impl.RecordReflection.getForeignKey;
import static st.orm.template.impl.RecordReflection.getPkComponents;
import static st.orm.template.impl.RecordReflection.getTableName;

/**
 * A processor for a join element of a template.
 */
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
        if (join.autoJoin() && join.source() instanceof Elements.TableSource(var table)) {
            return new ElementResult(() -> {
                if (!tableUse.isReferencedTable(table)) {
                    return "";
                }
                return getJoinString(join);
            });
        }
        return new ElementResult(getJoinString(join));
    }

    /**
     * Returns the SQL string for a join element.
     *
     * @param join the join element.
     * @return the SQL string for the join element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    private String getJoinString(Join join) throws SqlTemplateException {
        var columnNameResolver = template.columnNameResolver();
        var foreignKeyResolver = template.foreignKeyResolver();
        var tableNameResolver = template.tableNameResolver();
        var dialect = template.dialect();
        if (join.type().hasOnClause()) {
            String on = switch (join.target()) {
                case Elements.TableTarget(var toTable) when join.source() instanceof Elements.TableSource(var fromTable) -> {
                    var leftComponents = getFkComponents(fromTable).toList();
                    var rightComponents = getFkComponents(toTable).toList();
                    var leftComponent = findComponent(leftComponents, toTable);
                    Supplier<SqlTemplateException> exception = () -> new SqlTemplateException(STR."Failed to join \{fromTable.getSimpleName()} with \{toTable.getSimpleName()}.");
                    if (leftComponent.isPresent()) {
                        // Joins foreign key of left table to primary key of right table.
                        var fk = getForeignKey(leftComponent.get(), foreignKeyResolver);
                        var pk = getColumnName(getPkComponents(toTable).findFirst().orElseThrow(exception), columnNameResolver);
                        yield dialectTemplate."\{aliasMapper.getAlias(root(fromTable), INNER, dialect)}.\{fk} = \{aliasMapper.getAlias(toTable, null, INNER, dialect)}.\{pk}";
                    } else {
                        var rightComponent = findComponent(rightComponents, fromTable);
                        if (rightComponent.isPresent()) {
                            // Joins foreign key of right table to primary key of left table.
                            var fk = getForeignKey(rightComponent.get(), foreignKeyResolver);
                            var pk = getColumnName(getPkComponents(fromTable).findFirst().orElseThrow(exception), columnNameResolver);
                            yield dialectTemplate."\{aliasMapper.getAlias(root(fromTable), INNER, dialect)}.\{pk} = \{aliasMapper.getAlias(toTable, null, INNER, dialect)}.\{fk}";
                        } else {
                            // Joins foreign keys of two compound primary keys.
                            leftComponent = leftComponents.stream()
                                    .filter(f -> rightComponents.stream().anyMatch(r -> r.getType().equals(f.getType())))
                                    .findFirst();
                            rightComponent = rightComponents.stream()
                                    .filter(f -> leftComponents.stream().anyMatch(l -> l.getType().equals(f.getType())))
                                    .findFirst();
                            var fk = getForeignKey(leftComponent.orElseThrow(exception), foreignKeyResolver);
                            var pk = getForeignKey(rightComponent.orElseThrow(exception), foreignKeyResolver);
                            yield dialectTemplate."\{aliasMapper.getAlias(root(fromTable), INNER, dialect)}.\{fk} = \{aliasMapper.getAlias(toTable, null, INNER, dialect)}.\{pk}";
                        }
                    }
                }
                case Elements.TableTarget _ -> throw new SqlTemplateException("Unsupported source type.");   // Should not happen. See Join validation logic.
                case Elements.TemplateTarget ts -> templateProcessor.parse(ts.template(), true);   // On-clause is correlated.
            };
            return switch (join) {
                case Join(Elements.TableSource ts, var alias, _, _, _) ->
                        dialectTemplate."\n\{join.type().sql()} \{getTableName(ts.table(), tableNameResolver)} \{aliasMapper.useAlias(ts.table(), alias, INNER)} ON \{on}";
                case Join(Elements.TemplateSource ts, var alias, _, _, _) -> {
                    var source = templateProcessor.parse(ts.template(), false);   // Source is not correlated.
                    yield dialectTemplate."\n\{join.type().sql()} (\{source}) \{alias} ON \{on}";
                }
            };
        }
        return switch (join) {
            case Join(Elements.TableSource ts, var alias, _, _, _) ->
                    dialectTemplate."\n\{join.type().sql()} \{getTableName(ts.table(), tableNameResolver)}\{alias.isEmpty() ? "" : STR." \{alias}"}";
            case Join(Elements.TemplateSource ts, var alias, _, _, _) -> {
                var source = templateProcessor.parse(ts.template(), false);   // Source is not correlated.
                yield dialectTemplate."\n\{join.type().sql()} (\{source})\{alias.isEmpty() ? "" : STR." \{alias}"}";
            }
        };
    }
}
