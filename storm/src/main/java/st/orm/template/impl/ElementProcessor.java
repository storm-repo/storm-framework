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
import jakarta.persistence.PersistenceException;
import st.orm.FK;
import st.orm.Inline;
import st.orm.Lazy;
import st.orm.PK;
import st.orm.Persist;
import st.orm.Version;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.ColumnNameResolver;
import st.orm.template.ForeignKeyResolver;
import st.orm.template.SqlTemplate.AliasResolveStrategy;
import st.orm.template.SqlTemplate.BindVariables;
import st.orm.template.SqlTemplate.NamedParameter;
import st.orm.template.SqlTemplate.Parameter;
import st.orm.template.SqlTemplate.PositionalParameter;
import st.orm.template.SqlTemplateException;
import st.orm.template.impl.Elements.Alias;
import st.orm.template.impl.Elements.Delete;
import st.orm.template.impl.Elements.Expression;
import st.orm.template.impl.Elements.From;
import st.orm.template.impl.Elements.Insert;
import st.orm.template.impl.Elements.ObjectExpression;
import st.orm.template.impl.Elements.Param;
import st.orm.template.impl.Elements.Select;
import st.orm.template.impl.Elements.Set;
import st.orm.template.impl.Elements.Table;
import st.orm.template.impl.Elements.TableSource;
import st.orm.template.impl.Elements.TemplateExpression;
import st.orm.template.impl.Elements.TemplateSource;
import st.orm.template.impl.Elements.Unsafe;
import st.orm.template.impl.Elements.Update;
import st.orm.template.impl.Elements.Values;
import st.orm.template.impl.Elements.Where;
import st.orm.template.impl.SqlTemplateImpl.BindVarsImpl;
import st.orm.template.impl.SqlTemplateImpl.Eval;
import st.orm.template.impl.SqlTemplateImpl.Join;
import st.orm.template.impl.SqlTemplateImpl.On;
import st.orm.template.impl.SqlTemplateImpl.ResolvedEval;
import st.orm.template.impl.SqlTemplateImpl.Wrapped;

import java.lang.reflect.RecordComponent;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.List.copyOf;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.Spliterators.iterator;
import static java.util.stream.Collectors.joining;
import static st.orm.spi.Providers.getORMConverter;
import static st.orm.template.SqlTemplate.AliasResolveStrategy.FAIL;
import static st.orm.template.SqlTemplate.AliasResolveStrategy.FIRST;
import static st.orm.template.impl.SqlTemplateImpl.findComponent;
import static st.orm.template.impl.SqlTemplateImpl.getColumnName;
import static st.orm.template.impl.SqlTemplateImpl.getFkComponents;
import static st.orm.template.impl.SqlTemplateImpl.getForeignKey;
import static st.orm.template.impl.SqlTemplateImpl.getPkComponents;
import static st.orm.template.impl.SqlTemplateImpl.getTableName;

/**
 *
 */
record ElementProcessor(
        @Nonnull SqlTemplateImpl sqlTemplate,
        @Nonnull Element element,
        @Nonnull List<Parameter> parameters,
        @Nonnull AtomicInteger parameterPosition,
        @Nonnull AtomicInteger nameIndex,
        @Nonnull AliasMapper aliasMapper,
        @Nonnull TableMapper tableMapper,
        @Nonnull AtomicReference<BindVariables> bindVariables,
        @Nonnull List<String> generatedKeys,
        @Nonnull AtomicBoolean versionAware,
        @Nullable Table primaryTable
) {
    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    record ElementResult(@Nonnull String sql, @Nonnull List<String> args) {
        public ElementResult {
            requireNonNull(sql, "sql");
            requireNonNull(args, "args");
        }
        public ElementResult(@Nonnull String sql) {
            this(sql, List.of());
        }
    }

    Optional<ElementResult> process() throws SqlTemplateException {
        boolean resolved;
        Element fromResolved;
        if (element instanceof ResolvedEval r) {
            resolved = true;
            fromResolved = r.element();
        } else {
            resolved = false;
            fromResolved = element;
        }
        StringBuilder sql = new StringBuilder();
        StringBuilder evalString = new StringBuilder();
        List<String> args = new ArrayList<>();
        for (Element fromWrapped : fromResolved instanceof Wrapped w ? w.elements() : List.of(fromResolved)) {
            ElementResult result = switch (fromWrapped) {
                case Wrapped _, Eval _, ResolvedEval _ -> {
                    assert false;
                    yield null;
                }
                case Select it -> select(it);
                case Insert it -> insert(it);
                case Update it -> update(it);
                case Delete it -> delete(it);
                case From it -> from(it);
                case Table it -> table(it);
                case Alias it -> alias(it);
                case Set it -> set(it);
                case Where it -> where(it);
                case Values it -> values(it);
                case Param it -> param(it);
                case Unsafe it -> unsafe(it);
                case Join it -> join(it);
            };
            if (resolved) {
                evalString.append(result.sql().formatted(result.args().toArray()));
            } else {
                sql.append(result.sql());
                args.addAll(result.args());
            }
        }
        if (!evalString.isEmpty()) {
            args.add(evalString.toString());
        }
        if (!sql.isEmpty() || !args.isEmpty()) {
            return Optional.of(new ElementResult(sql.toString(), args));
        }
        return empty();
    }

    ElementResult select(Select select) throws SqlTemplateException {
        return new ElementResult(getColumnsStringForSelect(select.table(),
                aliasMapper,
                sqlTemplate.aliasResolveStrategy(),
                primaryTable == null ? null : primaryTable.table(),
                sqlTemplate.columnNameResolver(),
                sqlTemplate.foreignKeyResolver()));
    }

    ElementResult insert(Insert it) throws SqlTemplateException {
        return new ElementResult(STR."\{getTableName(it.table(), sqlTemplate.tableNameResolver())} (\{getColumnsStringForInsert(it.table(), sqlTemplate.columnNameResolver(), sqlTemplate.foreignKeyResolver(), generatedKeys)})");
    }

    ElementResult update(Update it) {
        return new ElementResult(STR."\{getTableName(it.table(), sqlTemplate.tableNameResolver())}\{it.alias().isEmpty() ? "" : STR." \{it.alias()}"}");
    }

    ElementResult delete(Delete it) {
        return new ElementResult(STR."\{it.alias().isEmpty() ? "" : STR."\{it.alias()}"}");
    }

    ElementResult from(From it) throws SqlTemplateException {
        return switch (it) {
            case From(TableSource ts, _) -> new ElementResult(STR."\{getTableName(ts.table(), sqlTemplate.tableNameResolver())}\{it.alias().isEmpty() ? "" : STR." \{it.alias()}"}");
            case From(TemplateSource ts, _) -> new ElementResult(STR."(\{getSourceString(ts)})\{it.alias().isEmpty() ? "" : STR." \{it.alias()}"}");
        };
    }

    ElementResult join(Join join) throws SqlTemplateException {
        if (join.type().hasOnClause()) {
            StringTemplate stringTemplate = join.on();
            var fragments = stringTemplate.fragments();
            var values = stringTemplate.values();
            assert fragments.size() == values.size() + 1;
            List<String> evalStrings = new ArrayList<>();
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < fragments.size(); i++) {
                String fragment = fragments.get(i);
                parts.add(fragment);
                if (i < values.size()) {
                    Object value = values.get(i);
                    List<String> target = parts;
                    if (value instanceof Eval e) {
                        value = e.object();
                        target = evalStrings;
                    }
                    switch (value) {
                        case Unsafe u -> target.add(u.sql());
                        case Table t -> target.add(STR."\{getTableName(t.table(), sqlTemplate.tableNameResolver())}\{t.alias().isEmpty() ? "" : STR." \{t.alias()}"}");
                        case Alias a -> target.add(aliasMapper.getAlias(a.table(), sqlTemplate.aliasResolveStrategy()));
                        case On o -> {
                            var leftComponents = getFkComponents(o.fromTable()).toList();
                            var rightComponents = getFkComponents(o.toTable()).toList();
                            var leftComponent = findComponent(leftComponents, o.toTable());
                            Supplier<SqlTemplateException> exception = () -> new SqlTemplateException(STR."Failed to join \{o.fromTable().getSimpleName()} with \{o.toTable().getSimpleName()}.");
                            if (leftComponent.isPresent()) {
                                // Joins foreign key of left table to primary key of right table.
                                var fk = getForeignKey(leftComponent.get(), sqlTemplate.foreignKeyResolver());
                                var pk = getColumnName(getPkComponents(o.toTable()).findFirst().orElseThrow(exception), sqlTemplate.columnNameResolver());
                                target.add(STR."\{aliasMapper.getAlias(o.fromTable(), sqlTemplate.aliasResolveStrategy())}.\{fk} = \{aliasMapper.getAlias(o.toTable(), sqlTemplate.aliasResolveStrategy())}.\{pk}");
                            } else {
                                var rightComponent = findComponent(rightComponents, o.fromTable());
                                if (rightComponent.isPresent()) {
                                    // Joins foreign key of right table to primary key of left table.
                                    var fk = getForeignKey(rightComponent.get(), sqlTemplate.foreignKeyResolver());
                                    var pk = getColumnName(getPkComponents(o.fromTable()).findFirst().orElseThrow(exception), sqlTemplate.columnNameResolver());
                                    target.add(STR."\{aliasMapper.getAlias(o.fromTable(), sqlTemplate.aliasResolveStrategy())}.\{pk} = \{aliasMapper.getAlias(o.toTable(), sqlTemplate.aliasResolveStrategy())}.\{fk}");
                                } else {
                                    // Joins foreign keys of two compound primary keys.
                                    leftComponent = leftComponents.stream()
                                            .filter(f -> rightComponents.stream().anyMatch(r -> r.getType().equals(f.getType())))
                                            .findFirst();
                                    rightComponent = rightComponents.stream()
                                            .filter(f -> leftComponents.stream().anyMatch(l -> l.getType().equals(f.getType())))
                                            .findFirst();
                                    var fk = getForeignKey(leftComponent.orElseThrow(exception), sqlTemplate.foreignKeyResolver());
                                    var pk = getForeignKey(rightComponent.orElseThrow(exception), sqlTemplate.foreignKeyResolver());
                                    target.add(STR."\{aliasMapper.getAlias(o.fromTable(), sqlTemplate.aliasResolveStrategy())}.\{fk} = \{aliasMapper.getAlias(o.toTable(), sqlTemplate.aliasResolveStrategy())}.\{pk}");
                                }
                            }
                        }
                        case Class<?> c when c.isRecord() -> //noinspection unchecked
                                target.add(aliasMapper.getAlias((Class<? extends Record>) c, sqlTemplate.aliasResolveStrategy()));
                        case Object k when REFLECTION.isSupportedType(k) -> target.add(aliasMapper.getAlias(REFLECTION.getRecordType(k), sqlTemplate.aliasResolveStrategy()));
                        case Element e -> throw new SqlTemplateException(STR."Unsupported element type: \{e.getClass().getSimpleName()}.");
                        default -> target.add(registerParam(value));
                    }
                }
            }
            String on = String.join("", parts);
            return switch (join) {
                case Join(TableSource ts, var alias, _, _) ->
                        new ElementResult(STR."\n\{join.type().sql()} \{getTableName(ts.table(), sqlTemplate.tableNameResolver())} \{aliasMapper.useAlias(ts.table(), alias)} ON \{on}", evalStrings);
                case Join(TemplateSource ts, var alias, _, _) ->
                        new ElementResult(STR."\n\{join.type().sql()} (\{getSourceString(ts)}) \{alias} ON \{on}", evalStrings);
            };
        }
        return switch (join) {
            case Join(TableSource ts, var alias, _, _) -> new ElementResult(STR."\n\{join.type().sql()} \{getTableName(ts.table(), sqlTemplate.tableNameResolver())}\{alias.isEmpty() ? "" : STR." \{alias}"}");
            case Join(TemplateSource ts, var alias, _, _) -> new ElementResult(STR."\n\{join.type().sql()} (\{getSourceString(ts)})\{alias.isEmpty() ? "" : STR." \{alias}"}");
        };
    }

    private String getSourceString(TemplateSource templateSource) throws SqlTemplateException {
        var sql = sqlTemplate.process(templateSource.template(), true);
        for (var parameter : sql.parameters()) {
            switch (parameter) {
                case PositionalParameter p -> registerParam(p.dbValue());
                case NamedParameter n -> registerParam(n.name(), n.dbValue());
            }
        }
        return sql.statement();
    }

    ElementResult table(Table it) {
        StringBuilder s = new StringBuilder();
        s.append(getTableName(it.table(), sqlTemplate.tableNameResolver()));
        if (!it.alias().isEmpty()) {
            s.append(STR." \{it.alias()}");
        }
        return new ElementResult(s.toString());
    }

    ElementResult alias(Alias it) throws SqlTemplateException {
        return new ElementResult(aliasMapper.getAlias(it.table(), sqlTemplate.aliasResolveStrategy()));
    }

    ElementResult set(Set it) throws SqlTemplateException {
        if (primaryTable == null) {
            throw new SqlTemplateException("Primary entity not found.");
        }
        if (it.record() != null) {
            if (!primaryTable.table().isInstance(it.record())) {
                throw new SqlTemplateException(STR."Record \{it.record().getClass().getSimpleName()} does not match entity \{primaryTable.table().getSimpleName()}.");
            }
            var valueMap = getValuesForSet(it.record(), primaryTable.alias(), sqlTemplate.columnNameResolver(), sqlTemplate.foreignKeyResolver(), versionAware);
            List<String> args = new ArrayList<>();
            for (var entry : valueMap.entrySet()) {
                args.add(STR."\{entry.getKey()} = ?");
                parameters.add(new PositionalParameter(parameterPosition.getAndIncrement(), entry.getValue()));
                args.add(", ");
            }
            if (versionAware.getPlain()) {
                for (RecordComponent component : it.record().getClass().getRecordComponents()) {
                    if (component.isAnnotationPresent(Version.class)) {
                        String columnName = getColumnName(component, sqlTemplate.columnNameResolver());
                        String updateExpression = switch (component.getType()) {
                            case Class<?> c when Integer.TYPE.isAssignableFrom(c)
                                    || Long.TYPE.isAssignableFrom(c)
                                    || Integer.class.isAssignableFrom(c)
                                    || Long.class.isAssignableFrom(c)
                                    || BigInteger.class.isAssignableFrom(c) -> STR."\{columnName} + 1";
                            case Class<?> c when Instant.class.isAssignableFrom(c)
                                    || Date.class.isAssignableFrom(c)
                                    || Calendar.class.isAssignableFrom(c)
                                    || Timestamp.class.isAssignableFrom(c) -> "CURRENT_TIMESTAMP";
                            default ->
                                    throw new SqlTemplateException(STR."Unsupported version type: \{component.getType().getSimpleName()}.");
                        };
                        String alias = primaryTable.alias();
                        args.add(STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{columnName} = \{updateExpression}");
                        args.add(", ");
                        break;
                    }
                }
            }
            if (!args.isEmpty()) {
                args.removeLast();
            }
            return new ElementResult(String.join("", args));
        } else if (it.bindVars() != null) {
            if (it.bindVars() instanceof BindVarsImpl vars) {
                String bindVarsStr = getBindVarsStringForSet(primaryTable.table(), primaryTable.alias(), sqlTemplate.columnNameResolver(), sqlTemplate.foreignKeyResolver());
                bindVariables.set(vars);
                final int fixedParameterPosition = parameterPosition.get();
                vars.addParameterExtractor(record -> {
                    try {
                        AtomicInteger position = new AtomicInteger(fixedParameterPosition);
                        return getValuesForSet(record, primaryTable.alias(), sqlTemplate.columnNameResolver(), sqlTemplate.foreignKeyResolver(), versionAware)
                                .values().stream()
                                .map(o -> new PositionalParameter(position.getAndIncrement(), o))
                                .toList();
                    } catch (SqlTemplateException ex) {
                        // BindVars works at the abstraction level of the ORM, so we throw a PersistenceException here.
                        throw new PersistenceException(ex);
                    }
                });
                parameterPosition.set(parameterPosition.get() + (int) bindVarsStr.chars().filter(ch -> ch == '?').count()); // We can find a better way to increase the parameterPosition.
                return new ElementResult(bindVarsStr);
            }
            throw new SqlTemplateException("Unsupported BindVars type.");
        }
        throw new SqlTemplateException("No values found for Set.");
    }

    private ElementResult expression(Expression expression) throws SqlTemplateException {
        return switch(expression) {
            case TemplateExpression it -> {
                var fragments = it.template().fragments();
                var values = it.template().values();
                List<String> evalStrings = new ArrayList<>();
                List<String> parts = new ArrayList<>();
                for (int i = 0; i < fragments.size(); i++) {
                    String fragment = fragments.get(i);
                    parts.add(fragment);
                    if (i < values.size()) {
                        Object value = values.get(i);
                        List<String> target = parts;
                        if (value instanceof Eval e) {
                            value = e.object();
                            target = evalStrings;
                        }
                        switch (value) {
                            case Expression exp -> {
                                ElementResult result = expression(exp);
                                parts.add(result.sql());
                                evalStrings.addAll(result.args());
                            }
                            case Unsafe u -> target.add(u.sql());
                            case Table t -> target.add(STR."\{getTableName(t.table(), sqlTemplate.tableNameResolver())}\{t.alias().isEmpty() ? "" : STR." \{t.alias()}"}");
                            case Alias a -> target.add(aliasMapper.getAlias(a.table(), sqlTemplate.aliasResolveStrategy()));
                            case Stream<?> s -> target.add(getObjectString(s));
                            case Param p when p.name() != null -> target.add(registerParam(p.name(), p.dbValue()));
                            case Param p -> target.add(registerParam(p.dbValue()));
                            case Record r -> target.add(getObjectString(r));
                            case Element e -> throw new SqlTemplateException(STR."Unsupported element type: \{e.getClass().getSimpleName()}.");
                            case Class<?> c when c.isRecord() -> //noinspection unchecked
                                    target.add(aliasMapper.getAlias((Class<? extends Record>) c, sqlTemplate.aliasResolveStrategy()));
                            case Object k when REFLECTION.isSupportedType(k) -> target.add(aliasMapper.getAlias(REFLECTION.getRecordType(k), sqlTemplate.aliasResolveStrategy()));
                            default -> target.add(registerParam(value));
                        }
                    }
                }
                String sql = String.join("", parts);
                yield new ElementResult(sql, evalStrings);
            }
            case ObjectExpression it -> new ElementResult(getObjectString(it.object()));
        };
    }

    private String getObjectString(@Nonnull Object object) throws SqlTemplateException {
        if (primaryTable == null) {
            throw new SqlTemplateException("Primary entity unknown.");
        }
        Iterable<?> iterable = switch (object) {
            case Object[] a -> List.of(a);
            case Iterable<?> i -> i;
            case Stream<?> s -> () -> iterator(s.spliterator());
            default -> List.of(object);
        };
        Class<?> pkType = REFLECTION.findPKType(primaryTable.table()).orElse(null);
        Object first = null;
        String column = null;
        int size = 0;
        List<String> args = new ArrayList<>();
        StringBuilder s = new StringBuilder();
        for (var o : iterable) {
            if (first == null) {
                first = o;
            }
            Class<?> elementType = o.getClass();
            Map<String, Object> valueMap = null;
            if (pkType != null && (pkType == elementType || (pkType.isPrimitive() && isPrimitiveCompatible(o, pkType)))) {
                valueMap = getValuesForCondition(o, primaryTable.table(), primaryTable.alias(), sqlTemplate.columnNameResolver());
            } else if (elementType.isRecord()) {
                valueMap = getValuesForCondition((Record) o, primaryTable.table(), primaryTable.alias(), tableMapper, sqlTemplate.aliasResolveStrategy(), sqlTemplate.columnNameResolver(), sqlTemplate.foreignKeyResolver(), versionAware.getPlain());
            }
            if (valueMap == null || valueMap.isEmpty()) {
                throw new SqlTemplateException(STR."Element \{o.getClass().getSimpleName()} does not match entity or component of entity \{primaryTable.table().getSimpleName()}.");
            }
            if (valueMap.size() == 1) {
                var entry = valueMap.entrySet().iterator().next();
                parameters.add(new PositionalParameter(parameterPosition.getAndIncrement(), entry.getValue()));
                var k = entry.getKey();
                if (column != null) {
                    if (!column.equals(k)) {
                        throw new SqlTemplateException(STR."Where elements resolve to different columns: \{column} and \{k}.");
                    }
                }
                column = k;
                size++;
            } else {
                if (column != null) {
                    throw new SqlTemplateException("Where elements resolve to different columns.");
                }
                args.add(STR."(\{valueMap.keySet().stream()
                        .map(k -> STR."\{k} = ?")
                        .collect(joining(" AND "))})");
                args.add(" OR ");
                parameters.addAll(valueMap.values().stream()
                        .map(v -> new PositionalParameter(parameterPosition.getAndIncrement(), v))
                        .toList());
            }
        }
        if (first == null) {
            // Returns FALSE for empty iterables.
            return "FALSE";
        }
        if (!args.isEmpty()) {
            args.removeLast();
            s.append(String.join("", args));
        }
        if (column != null) {
            if (size == 1) {
                s.append(STR."\{column} = ?");
            } else if (size > 1) {
                s.append(STR."\{column} IN (\{"?, ".repeat(size - 1)}?)");
            } else {
                throw new SqlTemplateException(STR."Unsupported Where element type: \{first.getClass().getSimpleName()}.");
            }
        }
        return s.toString();
    }

    ElementResult where(Where it) throws SqlTemplateException {
        if (primaryTable == null) {
            throw new SqlTemplateException("Primary entity not found.");
        }
        Expression expression = it.expression();
        if (expression != null) {
            return expression(expression);
        } else if (it.bindVars() != null) {
            String bindVarsStr = getBindVarsStringForWhere(primaryTable.table(), primaryTable.alias(), sqlTemplate.columnNameResolver());
            if (it.bindVars() instanceof BindVarsImpl vars) {
                bindVariables.set(vars);
                final int fixedParameterPosition = parameterPosition.get();
                vars.addParameterExtractor(record -> {
                    try {
                        AtomicInteger position = new AtomicInteger(fixedParameterPosition);
                        return getValuesForCondition(record, primaryTable.table(), primaryTable.alias(), tableMapper, sqlTemplate.aliasResolveStrategy(), sqlTemplate.columnNameResolver(), sqlTemplate.foreignKeyResolver(), versionAware.getPlain())
                                .values().stream()
                                .map(o -> new PositionalParameter(position.getAndIncrement(), o))
                                .toList();
                    } catch (SqlTemplateException ex) {
                        // BindVars works at the abstraction level of the ORM, so we throw a PersistenceException here.
                        throw new PersistenceException(ex);
                    }
                });
                parameterPosition.set(parameterPosition.get() + (int) bindVarsStr.chars().filter(ch -> ch == '?').count()); // We can find a better way to increase the parameterPosition.
                return new ElementResult(bindVarsStr);
            }
            throw new SqlTemplateException("Unsupported BindVars type.");
        }
        throw new SqlTemplateException("No values found for Where.");
    }

    ElementResult values(Values it) throws SqlTemplateException {
        if (primaryTable == null) {
            throw new SqlTemplateException("Primary entity not found.");
        }
        if (it.records() != null) {
            List<String> args = new ArrayList<>();
            for (var record : it.records().toList()) {
                if (record == null) {
                    throw new SqlTemplateException("Record is null.");
                }
                if (!primaryTable.table().isInstance(record)) {
                    throw new SqlTemplateException(STR."Record \{record.getClass().getSimpleName()} does not match entity \{primaryTable.table().getSimpleName()}.");
                }
                List<?> valueList = getValuesForInsert(record);
                if (valueList.isEmpty()) {
                    throw new SqlTemplateException("No values found for Insert.");
                }
                args.add(STR."(\{"?, ".repeat(valueList.size() - 1)}?)");
                for (var o : valueList) {
                    parameters().add(new PositionalParameter(parameterPosition.getAndIncrement(), o));
                }
                args.add(", ");
            }
            if (!args.isEmpty()) {
                args.removeLast();
            }
            return new ElementResult(String.join("", args));
        } else if (it.bindVars() != null) {
            if (it.bindVars() instanceof BindVarsImpl vars) {
                String bindVarsStr = getBindVarsStringForInsert(primaryTable.table(), sqlTemplate.columnNameResolver(), sqlTemplate.foreignKeyResolver(), generatedKeys);
                bindVariables.set(vars);
                final int fixedParameterPosition = parameterPosition.get();
                vars.addParameterExtractor(record -> {
                    try {
                        AtomicInteger position = new AtomicInteger(fixedParameterPosition);
                        return getValuesForInsert(record).stream()
                                .map(o -> new PositionalParameter(position.getAndIncrement(), o))
                                .toList();
                    } catch (SqlTemplateException ex) {
                        // BindVars works at the abstraction level of the ORM, so we throw a PersistenceException here.
                        throw new PersistenceException(ex);
                    }
                });
                parameterPosition.set(parameterPosition.get() + (int) bindVarsStr.chars().filter(ch -> ch == '?').count()); // We can find a better way to increase the parameterPosition.
                return new ElementResult(STR."(\{bindVarsStr})");
            }
            throw new SqlTemplateException("Unsupported BindVars type.");
        }
        throw new SqlTemplateException("No values found for Values.");
    }

    private String registerParam(@Nullable Object value) throws SqlTemplateException {
        switch (value) {
            case Object[] _ ->
                    throw new SqlTemplateException("Array parameters not supported.");   // Max compatibility with JPA.
            case Iterable<?> it when sqlTemplate.expandCollection() -> {
                List<String> args = new ArrayList<>();
                for (var v : it) {
                    args.add("?");
                    args.add(", ");
                    parameters.add(new PositionalParameter(parameterPosition.getAndIncrement(), v));
                }
                if (!args.isEmpty()) {
                    args.removeLast();    // Remove last ", " element.
                }
                return String.join("", args);
            }
            case null, default -> {
                parameters.add(new PositionalParameter(parameterPosition.getAndIncrement(), value));
                return "?";
            }
        }
    }

    private String registerParam(@Nonnull String name, @Nullable Object value) throws SqlTemplateException {
        if (sqlTemplate.positionalOnly()) {
            throw new SqlTemplateException("Named parameters not supported.");
        }
        parameters.add(new NamedParameter(name, value));
        return STR.":\{name}";
    }

    ElementResult param(Param p) throws SqlTemplateException {
        if (p.name() != null) {
            if (sqlTemplate.positionalOnly()) {
                throw new SqlTemplateException("Named parameters not supported.");
            }
            return new ElementResult(registerParam(p.name(), p.dbValue()));
        } else {
            if (sqlTemplate.positionalOnly()) {
                return new ElementResult(registerParam(p.dbValue()));
            }
            String name = STR."_p\{nameIndex.getAndIncrement()}";
            return new ElementResult(registerParam(name, p.dbValue()));
        }
    }

    ElementResult unsafe(Unsafe it) {
        return new ElementResult(it.sql());
    }

    public static List<RecordComponent> resolvePath(Class<? extends Record> root, Class<? extends Record> target) throws SqlTemplateException {
        List<RecordComponent> path = new ArrayList<>();
        resolvePath(root, target, path);
        return path;
    }

    private static boolean resolvePath(Class<? extends Record> current, Class<? extends Record> target, List<RecordComponent> path) {
        if (current == target) {
            return true;
        }
        for (RecordComponent component : current.getRecordComponents()) {
            Class<?> componentType = component.getType();
            if (componentType.isRecord()) {
                path.add(component);
                //noinspection unchecked
                if (resolvePath((Class<? extends Record>) componentType, target, path)) {
                    return true;
                }
                path.removeLast();
            } else if (componentType == target) {
                path.add(component);
                return true;
            }
        }
        return false;
    }

    private static String getColumnsStringForSelect(@Nonnull Class<? extends Record> type,
                                                    @Nonnull AliasMapper aliasMapper,
                                                    @Nonnull AliasResolveStrategy aliasResolveStrategy,
                                                    @Nullable Class<? extends Record> primaryTable,
                                                    @Nullable ColumnNameResolver columnNameResolver,
                                                    @Nullable ForeignKeyResolver foreignKeyResolver) throws SqlTemplateException {
        var parts = new ArrayList<String>();
        // Resolve the path of type, starting from the primary table. This will help in resolving the alias in case the same table is used multiple times.
        var path = primaryTable == null ? List.<RecordComponent>of() : resolvePath(primaryTable, type);
        getColumnsStringForSelect(type, path, aliasMapper, aliasResolveStrategy, getAlias(type, path, "", aliasMapper, aliasResolveStrategy), parts, columnNameResolver, foreignKeyResolver);
        if (!parts.isEmpty()) {
            parts.removeLast();
        }
        return java.lang.String.join("", parts);
    }

    private static void getColumnsStringForSelect(@Nonnull Class<? extends Record> type,
                                                  @Nonnull List<RecordComponent> path,
                                                  @Nonnull AliasMapper aliasMapper,
                                                  @Nonnull AliasResolveStrategy aliasResolveStrategy,
                                                  @Nonnull String alias,
                                                  @Nonnull List<String> parts,
                                                  @Nullable ColumnNameResolver columnNameResolver,
                                                  @Nullable ForeignKeyResolver foreignKeyResolver) throws SqlTemplateException {
        for (var component : type.getRecordComponents()) {
            var converter = getORMConverter(component).orElse(null);
            if (converter != null) {
                converter.getColumns(c -> getColumnName(c, columnNameResolver)).forEach(name -> {
                    if (!alias.isEmpty()) {
                        parts.add(STR."\{alias}.\{name}");
                    } else {
                        parts.add(name);
                    }
                    parts.add(", ");
                });
            } else if (component.getType().isRecord()) {
                @SuppressWarnings("unchecked")
                var recordType = (Class<? extends Record>) component.getType();
                var list = new ArrayList<>(path);
                list.add(component);
                var copy = copyOf(list);
                getColumnsStringForSelect(recordType, copy, aliasMapper, aliasResolveStrategy, getAlias(recordType, copy, alias, aliasMapper, aliasResolveStrategy), parts, columnNameResolver, foreignKeyResolver);
            } else if (Lazy.class.isAssignableFrom(component.getType())) {
                String name = getForeignKey(component, foreignKeyResolver);
                if (!alias.isEmpty()) {
                    parts.add(STR."\{alias}.\{name}");
                } else {
                    parts.add(name);
                }
                parts.add(", ");
            } else {
                String name = getColumnName(component, columnNameResolver);
                if (!alias.isEmpty()) {
                    parts.add(STR."\{alias}.\{name}");
                } else {
                    parts.add(name);
                }
                parts.add(", ");
            }
        }
    }

    private static String getColumnsStringForInsert(@Nonnull Class<? extends Record> type,
                                                    @Nullable ColumnNameResolver columnNameResolver,
                                                    @Nullable ForeignKeyResolver foreignKeyResolver,
                                                    @Nonnull List<String> generatedKeys) throws SqlTemplateException {
        return getStringForInsert(type, false, columnNameResolver, foreignKeyResolver, generatedKeys, false);
    }

    private static String getBindVarsStringForInsert(@Nonnull Class<? extends Record> type,
                                                     @Nullable ColumnNameResolver columnNameResolver,
                                                     @Nullable ForeignKeyResolver foreignKeyResolver,
                                                     @Nonnull List<String> generatedKeys) throws SqlTemplateException {
        return getStringForInsert(type, true, columnNameResolver, foreignKeyResolver, generatedKeys, false);
    }

    private static String getStringForInsert(@Nonnull Class<? extends Record> type,
                                             boolean placeholders,
                                             @Nullable ColumnNameResolver columnNameResolver,
                                             @Nullable ForeignKeyResolver foreignKeyResolver,
                                             @Nonnull List<String> generatedKeys,
                                             boolean primaryKey) throws SqlTemplateException {
        var parts = new ArrayList<String>();
        for (var component : type.getRecordComponents()) {
            Persist persist = REFLECTION.getAnnotation(component, Persist.class);
            if (persist != null && !persist.insertable()) {
                continue;
            }
            var converter = getORMConverter(component).orElse(null);
            if (converter != null) {
                converter.getColumns(c -> getColumnName(c, columnNameResolver)).forEach(column -> {
                    if (primaryKey) {
                        generatedKeys.add(column);
                    }
                    parts.add(placeholders ? "?" : column);
                    parts.add(", ");
                });
                continue;
            }
            PK pk = REFLECTION.getAnnotation(component, PK.class);
            if (pk != null) {
                if (!component.getType().isRecord()) {  // Record PKs will be handled below.
                    generatedKeys.add(getColumnName(component, columnNameResolver));
                }
                if (pk.autoGenerated()) {
                    continue;
                }
            }
            if (Lazy.class.isAssignableFrom(component.getType())) {
                parts.add(placeholders ? "?" : getForeignKey(component, foreignKeyResolver));
                parts.add(", ");
                continue;
            }
            if (REFLECTION.isAnnotationPresent(component, FK.class)) {
                parts.add(placeholders ? "?" : getForeignKey(component, foreignKeyResolver));
                parts.add(", ");
                continue;
            }
            if (component.getType().isRecord()) {
                if (pk != null || REFLECTION.isAnnotationPresent(component, Inline.class)) {
                    //noinspection unchecked
                    String str = getStringForInsert((Class<? extends Record>) component.getType(), placeholders, columnNameResolver, foreignKeyResolver, generatedKeys, primaryKey || pk != null);
                    if (!str.isEmpty()) {
                        parts.add(str);
                        parts.add(", ");
                    }
                    continue;
                }
                // Only include inlined components.
                continue;
            }
            parts.add(placeholders ? "?" : getColumnName(component, columnNameResolver));
            parts.add(", ");
        }
        if (!parts.isEmpty()) {
            parts.removeLast();
        }
        return java.lang.String.join("", parts);
    }

    private static String getBindVarsStringForSet(@Nonnull Class<? extends Record> recordType,
                                                  @Nonnull String alias,
                                                  @Nullable ColumnNameResolver columnNameResolver,
                                                  @Nullable ForeignKeyResolver foreignKeyResolver) throws SqlTemplateException {
        return getNamesForSet(recordType, alias, null, null, columnNameResolver, foreignKeyResolver).stream()
                .map(name -> STR."\{name} = ?")
                .collect(joining(", "));
    }

    @SuppressWarnings("unchecked")
    private static List<String> getNamesForSet(@Nonnull Class<? extends Record> recordType,
                                               @Nonnull String alias,
                                               @Nullable String fkName,
                                               @Nullable Class<? extends Record> fkClass,
                                               @Nullable ColumnNameResolver columnNameResolver,
                                               @Nullable ForeignKeyResolver foreignKeyResolver) throws SqlTemplateException {
        var names = new ArrayList<String>();
        for (var component : recordType.getRecordComponents()) {
            Persist persist = REFLECTION.getAnnotation(component, Persist.class);
            if (persist != null && !persist.updatable()) {
                continue;
            }
            var converter = getORMConverter(component).orElse(null);
            if (converter != null) {
                converter.getColumns(c -> getColumnName(c, columnNameResolver))
                        .forEach(name -> names.add(STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{name}"));
                continue;
            }
            if (Lazy.class.isAssignableFrom(component.getType())) {
                if (!REFLECTION.isAnnotationPresent(component, FK.class)) {
                    throw new SqlTemplateException(STR."Lazy component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is not a foreign key.");
                }
                names.add(STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getForeignKey(component, foreignKeyResolver)}");
                continue;
            }
            if (fkClass != null) {
                if (component.getType().isRecord() && REFLECTION.isAnnotationPresent(component, Inline.class)) {
                    var pks = getNamesForSet(recordType, alias, fkName, fkClass, columnNameResolver, foreignKeyResolver);
                    if (!pks.isEmpty()) {
                        names.addAll(pks);
                        // We found the PK for the foreign key. We can now return.
                        break;
                    }
                }
                if (REFLECTION.isAnnotationPresent(component, PK.class)) {
                    names.add(STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{fkName != null ? fkName : getColumnName(component, columnNameResolver)}");
                    // We found the PK for the foreign key. We can now return.
                    break;
                }
            } else {
                PK pk = REFLECTION.getAnnotation(component, PK.class);
                if (pk != null) {
                    continue;
                }
                if (REFLECTION.isAnnotationPresent(component, FK.class)) {
                    names.addAll(getNamesForSet(recordType, alias, getForeignKey(component, foreignKeyResolver), (Class<? extends Record>) component.getType(), columnNameResolver, foreignKeyResolver));
                    continue;
                }
                if (component.getType().isRecord() || REFLECTION.isAnnotationPresent(component, Inline.class)) {
                    names.addAll(getNamesForSet(recordType, alias, getForeignKey(component, foreignKeyResolver), fkClass, columnNameResolver, foreignKeyResolver));
                    continue;
                }
                names.add(STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(component, columnNameResolver)}");
            }
        }
        return names;
    }

    private static Map<String, Object> getValuesForSet(@Nullable Record record,
                                                       @Nonnull String alias,
                                                       @Nullable ColumnNameResolver columnNameResolver,
                                                       @Nullable ForeignKeyResolver foreignKeyResolver,
                                                       @Nonnull AtomicBoolean versionAware) throws SqlTemplateException {
        return getValuesForSet(record, alias,  null, null, columnNameResolver, foreignKeyResolver, versionAware);
    }

    private static Map<String, Object> getValuesForSet(@Nullable Record record,
                                                       @Nonnull String alias,
                                                       @Nullable String fkName,
                                                       @Nullable Class<? extends Record> fkClass,
                                                       @Nullable ColumnNameResolver columnNameResolver,
                                                       @Nullable ForeignKeyResolver foreignKeyResolver,
                                                       @Nonnull AtomicBoolean versionAware) throws SqlTemplateException {
        try {
            var values = new LinkedHashMap<String, Object>();
            if (record == null) {
                assert fkName != null;
                values.put(STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{fkName}", null);
                return values;
            }
            for (var component : record.getClass().getRecordComponents()) {
                Persist persist = REFLECTION.getAnnotation(component, Persist.class);
                if (persist != null && !persist.updatable()) {
                    continue;
                }
                var converter = getORMConverter(component).orElse(null);
                if (converter != null) {
                    values.putAll(converter.getValues(record, c -> getColumnName(c, columnNameResolver)));
                    continue;
                }
                if (Lazy.class.isAssignableFrom(component.getType())) {
                    if (!REFLECTION.isAnnotationPresent(component, FK.class)) {
                        throw new SqlTemplateException(STR."Lazy component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is not a foreign key.");
                    }
                    Lazy<?> lazy = (Lazy<?>) REFLECTION.invokeComponent(component, record);
                    var id = lazy == null ? null : lazy.id();
                    if (id == null && REFLECTION.isNonnull(component)) {
                        throw new SqlTemplateException(STR."Nonnull Lazy component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is null.");
                    }
                    values.put(STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getForeignKey(component, foreignKeyResolver)}", id);
                    continue;
                }
                if (fkClass != null) {
                    if (component.getType().isRecord() && REFLECTION.isAnnotationPresent(component, Inline.class)) {
                        var r = (Record) REFLECTION.invokeComponent(component, record);
                        if (r == null) {
                            // Skipping; We're only interested in finding a PK.
                            continue;
                        }
                        var pks = getValuesForSet(r, alias, fkName, fkClass, columnNameResolver, foreignKeyResolver, versionAware);
                        if (!pks.isEmpty()) {
                            values.putAll(pks);
                            // We found the PK for the foreign key. We can now return.
                            break;
                        }
                    }
                    if (REFLECTION.isAnnotationPresent(component, PK.class)) {
                        var v = REFLECTION.invokeComponent(component, record);
                        if (v == null && REFLECTION.isNonnull(component)) {
                            throw new SqlTemplateException(STR."Primary key component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is null.");
                        }
                        values.put(STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{fkName != null ? fkName : getColumnName(component, columnNameResolver)}", v);
                        // We found the PK for the foreign key. We can now return.
                        break;
                    }
                } else {
                    PK pk = REFLECTION.getAnnotation(component, PK.class);
                    if (pk != null) {
                        continue;
                    }
                    if (REFLECTION.isAnnotationPresent(component, FK.class)) {
                        var r = (Record) REFLECTION.invokeComponent(component, record);
                        if (r == null && REFLECTION.isNonnull(component)) {
                            throw new SqlTemplateException(STR."Nonnull foreign key component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is null.");
                        }
                        //noinspection unchecked
                        values.putAll(getValuesForSet(r, alias, getForeignKey(component, foreignKeyResolver), (Class<? extends Record>) component.getType(), columnNameResolver, foreignKeyResolver, versionAware));
                        continue;
                    }
                    if (REFLECTION.isAnnotationPresent(component, Version.class)) {
                        // No field will be added for version.
                        versionAware.setPlain(true);
                        continue;
                    }
                    if (component.getType().isRecord() || REFLECTION.isAnnotationPresent(component, Inline.class)) {
                        var r = (Record) REFLECTION.invokeComponent(component, record);
                        if (r == null && REFLECTION.isNonnull(component)) {
                            throw new SqlTemplateException(STR."Nonnull inline component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is null.");
                        }
                        //noinspection ConstantValue
                        values.putAll(getValuesForSet(r, alias, fkName, fkClass, columnNameResolver, foreignKeyResolver, versionAware));
                        continue;
                    }
                    var v = REFLECTION.invokeComponent(component, record);
                    if (v == null && REFLECTION.isNonnull(component)) {
                        throw new SqlTemplateException(STR."Nonnull component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is null.");
                    }
                    values.put(STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(component, columnNameResolver)}", v);
                }
            }
            return values;
        } catch (SqlTemplateException e) {
            throw e;
        } catch (Throwable t) {
            throw new SqlTemplateException(t);
        }
    }

    /**
     * Returns the primary key value for the specified foreign key record.
     *
     * @param record the record to retrieve the primary key value for.
     * @return the primary key value for the specified record.
     * @throws SqlTemplateException if zero or multiple primary key columns are found, or if the primary key value
     * cannot be retrieved.
     */
    private static Object getPkForForeignKey(@Nonnull Record record) throws SqlTemplateException {
        for (var component : record.getClass().getRecordComponents()) {
            if (REFLECTION.isAnnotationPresent(component, PK.class)) {
                try {
                    Object pk = REFLECTION.invokeComponent(component, record);
                    if (pk == null) {
                        throw new SqlTemplateException(STR."Primary key value is null for \{record.getClass().getSimpleName()}.");
                    }
                    if (pk instanceof Record) {
                        throw new SqlTemplateException(STR."Foreign key specifies a compound primary key: \{record.getClass().getSimpleName()}.");
                    }
                    return pk;
                } catch (SqlTemplateException e) {
                    throw e;
                } catch (Throwable t) {
                    throw new SqlTemplateException(t);
                }
            }
        }
        throw new SqlTemplateException(STR."No primary key found for \{record.getClass().getSimpleName()}.");
    }

    private static String getBindVarsStringForWhere(@Nonnull Class<? extends Record> recordType,
                                                    @Nonnull String alias,
                                                    @Nullable ColumnNameResolver columnNameResolver) {
        return getPkNamesForWhere(recordType, alias, columnNameResolver).stream()
                .map(pkName -> STR."\{pkName} = ?")
                .collect(joining(" AND "));
    }

    @SuppressWarnings("unchecked")
    private static List<String> getPkNamesForWhere(@Nonnull Class<? extends Record> recordType,
                                                   @Nonnull String alias,
                                                   @Nullable ColumnNameResolver columnNameResolver) {
        var names = new ArrayList<String>();
        for (var component : recordType.getRecordComponents()) {
            if (component.getType().isRecord() && (REFLECTION.isAnnotationPresent(component, PK.class) // Record PKs are implicitly inlined.
                    || REFLECTION.isAnnotationPresent(component, Inline.class))) {
                names.addAll(getPkNamesForWhere((Class<? extends Record>) component.getType(), alias, columnNameResolver));
                continue;
            }
            if (REFLECTION.isAnnotationPresent(component, PK.class)) {
                names.add(STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(component, columnNameResolver)}");
                break;
            }
        }
        return names;
    }

    private static Map<String, Object> getValuesForCondition(@Nonnull Record record,
                                                             @Nonnull Class<? extends Record> primaryTable,
                                                             @Nonnull String alias,
                                                             @Nonnull TableMapper tableMapper,
                                                             @Nonnull AliasResolveStrategy aliasResolveStrategy,
                                                             @Nullable ColumnNameResolver columnNameResolver,
                                                             @Nullable ForeignKeyResolver foreignKeyResolver,
                                                             boolean updateMode) throws SqlTemplateException {
        try {
            var values = new LinkedHashMap<String, Object>();
            if (primaryTable.isInstance(record)) {
                for (var component : record.getClass().getRecordComponents()) {
                    if (REFLECTION.isAnnotationPresent(component, PK.class)) {
                        Object pk = REFLECTION.invokeComponent(component, record);
                        if (pk instanceof Record) {
                            values.putAll(getValuesForInlined((Record) pk, alias, columnNameResolver));
                        } else {
                            values.put(STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(component, columnNameResolver)}", REFLECTION.invokeComponent(component, record));
                        }
                    } else if (updateMode // Only apply version check if in update mode to prevent side effects when comparing objects in other modes.
                            && REFLECTION.isAnnotationPresent(component, Version.class)) {
                        values.put(STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(component, columnNameResolver)}", REFLECTION.invokeComponent(component, record));
                    }
                }
                return values;
            }
            var mappings = tableMapper.getMappings(record.getClass());
            if (mappings.isEmpty()) {
                throw new SqlTemplateException(STR."Cannot find \{record.getClass().getSimpleName()} entity in \{primaryTable.getSimpleName()} hierarchy.");
            } else if (mappings.size() > 1 && aliasResolveStrategy == FAIL) {
                throw new SqlTemplateException(STR."Multiple occurences of \{record.getClass().getSimpleName()} entity found in \{primaryTable.getSimpleName()} hierarchy.");
            }
            for (var mapping : mappings) {
                String a = mapping.alias();
                if (mapping.primaryKey()) {
                    for (var component : mapping.components()) {
                        Object pk = REFLECTION.invokeComponent(component, record);
                        if (pk instanceof Record) {
                            values.putAll(getValuesForInlined((Record) pk, mapping.alias(), columnNameResolver));
                        } else {
                            values.put(STR."\{a.isEmpty() ? "" : STR."\{a}."}\{getColumnName(component, columnNameResolver)}", REFLECTION.invokeComponent(component, record));
                        }
                    }
                } else {
                    assert mapping.components().size() == 1;
                    for (var component : record.getClass().getRecordComponents()) {
                        if (REFLECTION.isAnnotationPresent(component, PK.class)) {
                            values.put(STR."\{a.isEmpty() ? "" : STR."\{a}."}\{getForeignKey(mapping.components().getFirst(), foreignKeyResolver)}", REFLECTION.invokeComponent(component, record));
                            break;  // Foreign key mappings can only be based on a single column.
                        }
                    }
                }
                if (aliasResolveStrategy == FIRST) {
                    break;
                }
            }
            return values;
        } catch (SqlTemplateException e) {
            throw e;
        } catch (Throwable t) {
            throw new SqlTemplateException(t);
        }
    }

    private static Map<String, Object> getValuesForCondition(@Nonnull Object id,
                                                             @Nonnull Class<? extends Record> recordType,
                                                             @Nonnull String alias,
                                                             @Nullable ColumnNameResolver columnNameResolver) throws SqlTemplateException {
        try {
            var values = new LinkedHashMap<String, Object>();
            for (var component : recordType.getRecordComponents()) {
                if (REFLECTION.isAnnotationPresent(component, PK.class)) {
                    if (component.getType().isRecord()) {
                        if (REFLECTION.isAnnotationPresent(component, FK.class)) {
                            values.put(STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(component, columnNameResolver)}", getPkForForeignKey((Record) id));
                        } else if (recordType.isInstance(id)) {
                            values.putAll(getValuesForInlined((Record) REFLECTION.invokeComponent(component, id), alias, columnNameResolver));
                        } else {
                            values.putAll(getValuesForInlined((Record) id, alias, columnNameResolver));
                        }
                    } else {
                        values.put(STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(component, columnNameResolver)}", id);
                    }
                    break;
                }
            }
            return values;
        } catch (SqlTemplateException e) {
            throw e;
        } catch (Throwable t) {
            throw new SqlTemplateException(t);
        }
    }

    private static Map<String, Object> getValuesForInlined(@Nonnull Record record, @Nonnull String alias, @Nullable ColumnNameResolver columnNameResolver) throws SqlTemplateException {
        try {
            var values = new LinkedHashMap<String, Object>();
            for (var component : record.getClass().getRecordComponents()) {
                Object o = REFLECTION.invokeComponent(component, record);
                if (o instanceof Record r) {
                    values.putAll(getValuesForInlined(r, alias, columnNameResolver));
                } else {
                    values.put(STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(component, columnNameResolver)}", o);
                }
            }
            return values;
        } catch (SqlTemplateException e) {
            throw e;
        } catch (Throwable t) {
            throw new SqlTemplateException(t);
        }
    }

    private static List<Object> getValuesForInsert(@Nonnull Record record) throws SqlTemplateException {
        return getValuesForInsert(record, null);
    }

    private static List<Object> getValuesForInsert(@Nullable Record record, @Nullable Class<? extends Record> fkClass) throws SqlTemplateException {
        try {
            var values = new ArrayList<>();
            if (record == null) {
                values.add(null);
                return values;
            }
            for (var component : record.getClass().getRecordComponents()) {
                Persist persist = REFLECTION.getAnnotation(component, Persist.class);
                if (persist != null && !persist.insertable()) {
                    continue;
                }
                var converter = getORMConverter(component).orElse(null);
                if (converter != null) {
                    values.addAll(converter.getValues(record));
                    continue;
                }
                if (Lazy.class.isAssignableFrom(component.getType())) {
                    if (!REFLECTION.isAnnotationPresent(component, FK.class)) {
                        throw new SqlTemplateException(STR."Lazy component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is not a foreign key.");
                    }
                    var id = ((Lazy<?>) REFLECTION.invokeComponent(component, record)).id();
                    if (id == null && REFLECTION.isNonnull(component)) {
                        throw new SqlTemplateException(STR."Nonnull Lazy component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is null.");
                    }
                    values.add(id);
                    continue;
                }
                if (fkClass != null) {
                    if (component.getType().isRecord() && (REFLECTION.isAnnotationPresent(component, PK.class)  // Record PKs are implicitly inlined.
                            || REFLECTION.isAnnotationPresent(component, Inline.class))) {
                        var r = (Record) REFLECTION.invokeComponent(component, record);
                        if (r == null) {
                            // Skipping; We're only interested in finding a PK.
                            continue;
                        }
                        var pk = getValuesForInsert(r, fkClass);
                        if (!pk.isEmpty()) {
                            values.add(pk);
                            // We found the PK for the foreign key. We can now return.
                            break;
                        }
                    }
                    if (REFLECTION.isAnnotationPresent(component, PK.class)) {
                        values.add(REFLECTION.invokeComponent(component, record));
                        // We found the PK for the foreign key. We can now return.
                        break;
                    }
                } else {
                    PK pk = REFLECTION.getAnnotation(component, PK.class);
                    if (pk != null && pk.autoGenerated()) {
                        continue;
                    }
                    if (REFLECTION.isAnnotationPresent(component, FK.class)) {
                        var r = (Record) REFLECTION.invokeComponent(component, record);
                        if (r == null) {
                            throw new SqlTemplateException(STR."Nonnull foreign key component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is null.");
                        }
                        //noinspection unchecked
                        values.addAll(getValuesForInsert(r, (Class<? extends Record>) component.getType()));
                        continue;
                    }
                    if (component.getType().isRecord() && (REFLECTION.isAnnotationPresent(component, PK.class) // Record PKs are implicitly inlined.
                            || REFLECTION.isAnnotationPresent(component, Inline.class))) {
                        var r = (Record) REFLECTION.invokeComponent(component, record);
                        if (r == null) {
                            throw new SqlTemplateException(STR."Nonnull component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is null.");
                        }
                        values.addAll(getValuesForInsert(r, null));
                        continue;
                    }
                    values.add(REFLECTION.invokeComponent(component, record));
                }
            }
            return values;
        } catch (SqlTemplateException e) {
            throw e;
        } catch (Throwable t) {
            throw new SqlTemplateException(t);
        }
    }

    private static String getAlias(@Nonnull Class<? extends Record> type,
                                   @Nonnull List<RecordComponent> path,
                                   @Nonnull String alias,
                                   @Nonnull AliasMapper aliasMapper,
                                   @Nonnull AliasResolveStrategy aliasResolveStrategy) throws SqlTemplateException {
        if (!path.isEmpty()) {
            if (REFLECTION.isAnnotationPresent(path.getLast(), PK.class) // PKs are implicitly inlined.
                    || REFLECTION.isAnnotationPresent(path.getLast(), Inline.class)) {
                return alias;
            }
            return aliasMapper.getAlias(path);
        }
        return aliasMapper.getAlias(type, aliasResolveStrategy);
    }

    private boolean isPrimitiveCompatible(Object o, Class<?> clazz) {
        if (clazz == int.class) return o instanceof Integer;
        if (clazz == long.class) return o instanceof Long;
        if (clazz == boolean.class) return o instanceof Boolean;
        if (clazz == byte.class) return o instanceof Byte;
        if (clazz == char.class) return o instanceof Character;
        if (clazz == short.class) return o instanceof Short;
        if (clazz == float.class) return o instanceof Float;
        if (clazz == double.class) return o instanceof Double;
        return false;
    }
}
