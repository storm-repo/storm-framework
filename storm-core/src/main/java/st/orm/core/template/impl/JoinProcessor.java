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
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.impl.Elements.TableSource;
import st.orm.core.template.impl.Elements.TableTarget;
import st.orm.core.template.impl.Elements.TemplateSource;
import st.orm.core.template.impl.Elements.TemplateTarget;
import st.orm.mapping.RecordField;

import static st.orm.ResolveScope.INNER;
import static st.orm.core.template.impl.RecordReflection.findPkField;
import static st.orm.core.template.impl.RecordReflection.findRecordField;
import static st.orm.core.template.impl.RecordReflection.getFkFields;
import static st.orm.core.template.impl.RecordReflection.getForeignKeys;
import static st.orm.core.template.impl.RecordReflection.getPrimaryKeys;
import static st.orm.core.template.impl.RecordReflection.getTableName;
import static st.orm.core.template.impl.RecordValidation.validateDataType;

final class JoinProcessor implements ElementProcessor<Join> {

    /**
     * Returns a key that represents the compiled shape of the given element.
     *
     * <p>The compilation key is used for caching compiled results. It must include all fields that can affect the
     * compilation output (SQL text, emitted fragments, placeholder shape, etc.). The key is compared using
     * value-based equality, so it should be immutable and implement stable {@code equals}/{@code hashCode}.</p>
     *
     * <p>If this method returns {@code null} for any element in a template, the compiled result is considered
     * non-cacheable and the template must be recompiled each time it is requested.</p>
     *
     * @param join the element to compute a key for.
     * @return an immutable key for caching, or {@code null} if the element (or its compilation) cannot be cached.
     */
    @Override
    public Object getCompilationKey(@Nonnull Join join) {
        if (join.source() instanceof TemplateSource || join.target() instanceof TemplateTarget) {
            return null;
        }
        return join;
    }

    /**
     * Compiles the given element into an {@link CompiledElement}.
     *
     * <p>This method is responsible for producing the compile-time representation of the element. It must not perform
     * runtime binding. Any binding should be deferred to {@link #bind(Join, TemplateBinder, BindHint)}.</p>
     *
     * @param join the element to compile.
     * @param compiler the active compiler context.
     * @return the compiled result for this element.
     * @throws SqlTemplateException if compilation fails.
     */
    @Override
    public CompiledElement compile(@Nonnull Join join, @Nonnull TemplateCompiler compiler)
            throws SqlTemplateException{
        if (join.autoJoin() && join.source() instanceof TableSource(var table)) {
            // Prune the join if the table is not referenced in the template, for instance, in case of a SelectMode.DECLARED.
            return new CompiledElement(
                    () -> compiler.isReferenced(table, join.sourceAlias()) ? compileJoin(join, compiler) : "");
        }
        return new CompiledElement(compileJoin(join, compiler));
    }

    /**
     * Performs post-processing after compilation, typically binding runtime values for the element.
     *
     * <p>This method is called after the element has been compiled. Typical responsibilities include binding
     * parameters, registering bind variables, or applying runtime-only adjustments that must not affect the compiled
     * SQL shape.</p>
     *
     * @param join the element that was compiled.
     * @param binder the binder used to bind runtime values.
     * @param bindHint the bind hint for the element, providing additional context for binding.
     */
    @Override
    public void bind(@Nonnull Join join, @Nonnull TemplateBinder binder, @Nonnull BindHint bindHint) {
        if (join.target() instanceof TemplateTarget(var template)) {
            binder.bind(template, true);
        }
        if (join.source() instanceof TemplateSource(var template)) {
            binder.bind(template, false);
        }
    }

    private String compileJoin(@Nonnull Join join, @Nonnull TemplateCompiler compiler)
            throws SqlTemplateException {
        String joinType = join.type().sql();
        String onClause = join.type().hasOnClause() ? switch (join.target()) {
            case TableTarget(var toTable) when join.source() instanceof TableSource(var fromTable) ->
                    compileJoinCondition(fromTable, join.sourceAlias(), toTable, join.targetAlias(), compiler);
            case TemplateTarget ts -> compiler.compile(ts.template(), true);
            default -> throw new SqlTemplateException("Unsupported join target.");
        } : "";
        final String clause = onClause.isEmpty() ? "" : " ON " + onClause;
        return switch (join.source()) {
            case TableSource ts -> {
                var table = getTableName(ts.table(), compiler.template().tableNameResolver());
                var alias = compiler.useAlias(ts.table(), join.sourceAlias());
                yield compiler.dialectTemplate().process("\n\0 \0 \0\0", joinType, table, alias, clause);
            }
            case TemplateSource ts -> {
                var source = compiler.compile(ts.template(), false);
                var alias = join.sourceAlias();
                yield compiler.dialectTemplate().process("\n\0 (\0) \0\0", joinType, source, alias, clause);
            }
        };
    }

    private String compileJoinCondition(
            @Nonnull Class<? extends Data> fromTable,
            @Nonnull String alias,
            @Nonnull Class<? extends Data> toTable,
            @Nullable String toAlias,
            @Nonnull TemplateCompiler compiler
    ) throws SqlTemplateException {
        var rightComponent = findRecordField(getFkFields(toTable).toList(), fromTable);
        if (rightComponent.isPresent()) {
            validateDataType(fromTable, true);
            // Joins foreign key of right table to the primary key of left table.
            return compileJoinCondition(fromTable, alias, toTable, toAlias, rightComponent.get(),
                    findPkField(fromTable).orElseThrow(), compiler);
        }
        var leftComponent = findRecordField(getFkFields(fromTable).toList(), toTable);
        if (leftComponent.isPresent()) {
            validateDataType(toTable, true);
            // Joins foreign key of left table to the primary key of right table.
            return compileJoinCondition(toTable, toAlias, fromTable, alias, leftComponent.get(),
                    findPkField(toTable).orElseThrow(), compiler);
        }
        throw new SqlTemplateException(
                "Failed to join %s with %s. No matching foreign key found.".formatted(fromTable.getSimpleName(), toTable.getSimpleName()));
    }

    @SuppressWarnings("DuplicatedCode")
    private String compileJoinCondition(
            @Nonnull Class<? extends Data> fromTable,
            @Nullable String fromAlias,
            @Nonnull Class<? extends Data> toTable,
            @Nullable String toAlias,
            @Nonnull RecordField left,
            @Nonnull RecordField right,
            @Nonnull TemplateCompiler compiler
    ) throws SqlTemplateException {
        fromAlias = fromAlias == null ? compiler.getAlias(Metamodel.root(fromTable), INNER) : fromAlias;
        toAlias = toAlias == null ? compiler.getAlias(Metamodel.root(toTable), INNER) : toAlias;
        var foreignKeyResolver = compiler.template().foreignKeyResolver();
        var columnNameResolver = compiler.template().columnNameResolver();
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
            joinCondition.append(compiler.dialectTemplate()
                    .process("\0.\0 = \0.\0", toAlias, fkColumns.get(i), fromAlias, pkColumns.get(i)));
        }
        return joinCondition.toString();
    }
}