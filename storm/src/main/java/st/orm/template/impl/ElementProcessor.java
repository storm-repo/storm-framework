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
import st.orm.BindVars;
import st.orm.FK;
import st.orm.Lazy;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.Query;
import st.orm.Version;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.SqlDialect;
import st.orm.template.Metamodel;
import st.orm.template.Operator;
import st.orm.template.Sql;
import st.orm.template.SqlTemplate.BindVariables;
import st.orm.template.SqlTemplate.NamedParameter;
import st.orm.template.SqlTemplate.Parameter;
import st.orm.template.SqlTemplate.PositionalParameter;
import st.orm.template.SqlTemplateException;
import st.orm.template.impl.Elements.Alias;
import st.orm.template.impl.Elements.Column;
import st.orm.template.impl.Elements.Delete;
import st.orm.template.impl.Elements.Expression;
import st.orm.template.impl.Elements.From;
import st.orm.template.impl.Elements.Insert;
import st.orm.template.impl.Elements.ObjectExpression;
import st.orm.template.impl.Elements.Param;
import st.orm.template.impl.Elements.Select;
import st.orm.template.impl.Elements.Set;
import st.orm.template.impl.Elements.Subquery;
import st.orm.template.impl.Elements.Table;
import st.orm.template.impl.Elements.TableSource;
import st.orm.template.impl.Elements.TableTarget;
import st.orm.template.impl.Elements.TemplateExpression;
import st.orm.template.impl.Elements.TemplateSource;
import st.orm.template.impl.Elements.TemplateTarget;
import st.orm.template.impl.Elements.Unsafe;
import st.orm.template.impl.Elements.Update;
import st.orm.template.impl.Elements.Values;
import st.orm.template.impl.Elements.Var;
import st.orm.template.impl.Elements.Where;
import st.orm.template.impl.SqlTemplateImpl.Join;
import st.orm.template.impl.SqlTemplateImpl.Wrapped;
import st.orm.template.impl.TableMapper.Mapping;

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
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.List.copyOf;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static st.orm.spi.Providers.getORMConverter;
import static st.orm.template.Metamodel.root;
import static st.orm.template.Operator.EQUALS;
import static st.orm.template.ResolveScope.CASCADE;
import static st.orm.template.ResolveScope.INNER;
import static st.orm.template.impl.RecordReflection.findComponent;
import static st.orm.template.impl.RecordReflection.getColumnName;
import static st.orm.template.impl.RecordReflection.getFkComponents;
import static st.orm.template.impl.RecordReflection.getForeignKey;
import static st.orm.template.impl.RecordReflection.getLazyRecordType;
import static st.orm.template.impl.RecordReflection.getPkComponents;
import static st.orm.template.impl.RecordReflection.getRecordComponent;
import static st.orm.template.impl.RecordReflection.getTableName;
import static st.orm.template.impl.SqlTemplateImpl.toPathString;

/**
 * Processes an element in a SQL template.
 */
record ElementProcessor(
        @Nonnull SqlTemplateImpl sqlTemplate,
        @Nonnull ModelBuilder modelBuilder,
        @Nonnull Element element,
        @Nonnull List<Parameter> parameters,
        @Nonnull AtomicInteger parameterPosition,
        @Nonnull AtomicInteger nameIndex,
        @Nonnull TableUse tableUse,
        @Nonnull AliasMapper aliasMapper,
        @Nonnull TableMapper tableMapper,
        @Nonnull AtomicReference<BindVariables> bindVariables,
        @Nonnull List<String> generatedKeys,
        @Nonnull AtomicBoolean versionAware,
        @Nullable Table primaryTable
) {
    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    DialectTemplate dialectTemplate() {
        return sqlTemplate.dialectTemplate();
    }

    @FunctionalInterface
    private interface OptionalSql {
        String get() throws SqlTemplateException;
    }

    record ElementResult(@Nonnull OptionalSql sql) implements OptionalSql {
        ElementResult {
            requireNonNull(sql, "sql");
        }
        ElementResult(@Nonnull String sql) {
            this(() -> sql);
        }

        @Override
        public String get() throws SqlTemplateException {
            return sql.get();
        }
    }

    private static final ScopedValue<ElementProcessor> CURRENT_PROCESSOR = ScopedValue.newInstance();

    static Optional<ElementProcessor> current() {
        return ofNullable(CURRENT_PROCESSOR.orElse(null));
    }

    ElementResult process() throws SqlTemplateException {
        Element fromResolved = element;
        var results = new ArrayList<OptionalSql>();
        for (Element fromWrapped : fromResolved instanceof Wrapped(var elements) ? elements : List.of(fromResolved)) {
            results.add(switch (fromWrapped) {
                case Wrapped _ -> {
                    assert false;
                    yield null;
                }
                case Select it -> select(it);
                case Insert it -> insert(it);
                case Update it -> update(it);
                case Delete it -> delete(it);
                case From it -> from(it);
                case Join it -> join(it);
                case Table it -> table(it);
                case Alias it -> alias(it);
                case Column it -> column(it);
                case Set it -> set(it);
                case Where it -> where(it);
                case Values it -> values(it);
                case Param it -> param(it);
                case Var it -> var(it);
                case Subquery it -> subquery(it);
                case Unsafe it -> unsafe(it);
            });
        }
        return new ElementResult(() -> {
            StringBuilder sql = new StringBuilder();
            for (OptionalSql result : results) {
                sql.append(result.get());
            }
            return sql.toString();
        });
    }

    ElementResult select(Select select) throws SqlTemplateException {
        return new ElementResult(getColumnsStringForSelect(
                select.table(),
                select.nested(),
                aliasMapper,
                primaryTable == null ? null : primaryTable.table()));
    }

    ElementResult insert(Insert it) throws SqlTemplateException {
        //noinspection Convert2MethodRef
        String columns = modelBuilder.build(it.table(), false)
                .columns().stream()
                .filter(column -> column.insertable())
                .map(column -> {
                    if (column.autoGenerated()) {
                        generatedKeys.add(column.qualifiedName(sqlTemplate.dialect()));
                        return null;
                    }
                    return column.qualifiedName(sqlTemplate.dialect());
                })
                .filter(Objects::nonNull)
                .collect(joining(", "));
        return new ElementResult(dialectTemplate()."\{getTableName(it.table(), sqlTemplate.tableNameResolver())} (\{columns})");
    }

    ElementResult update(Update it) throws SqlTemplateException {
        return new ElementResult(dialectTemplate()."\{getTableName(it.table(), sqlTemplate.tableNameResolver())}\{it.alias().isEmpty() ? "" : STR." \{it.alias()}"}");
    }

    ElementResult delete(Delete it) {
        return new ElementResult(STR."\{it.alias().isEmpty() ? "" : STR."\{it.alias()}"}");
    }

    ElementResult from(From it) throws SqlTemplateException {
        return new ElementResult(switch (it) {
            case From(TableSource ts, _, _) -> dialectTemplate()."\{getTableName(ts.table(), sqlTemplate.tableNameResolver())}\{it.alias().isEmpty() ? "" : STR." \{it.alias()}"}";
            case From(TemplateSource ts, _, _) -> {
                var from = parse(ts.template(), false);   // From-clause is not correlated.
                yield STR."(\{from})\{it.alias().isEmpty() ? "" : STR." \{it.alias()}"}";
            }
        });
    }

    ElementResult join(Join join) throws SqlTemplateException {
        if (join.autoJoin() && join.source() instanceof TableSource(var table)) {
            return new ElementResult(() -> {
                if (!tableUse.isReferencedTable(table)) {
                    return "";
                }
                return optionalJoin(join);
            });
        }
        return new ElementResult(optionalJoin(join));
    }

    String optionalJoin(Join join) throws SqlTemplateException {
        var columnNameResolver = sqlTemplate.columnNameResolver();
        var foreignKeyResolver = sqlTemplate.foreignKeyResolver();
        var tableNameResolver = sqlTemplate.tableNameResolver();
        var dialect = sqlTemplate.dialect();
        if (join.type().hasOnClause()) {
            String on = switch (join.target()) {
                case TableTarget(var toTable) when join.source() instanceof TableSource(var fromTable) -> {
                    var leftComponents = getFkComponents(fromTable).toList();
                    var rightComponents = getFkComponents(toTable).toList();
                    var leftComponent = findComponent(leftComponents, toTable);
                    Supplier<SqlTemplateException> exception = () -> new SqlTemplateException(STR."Failed to join \{fromTable.getSimpleName()} with \{toTable.getSimpleName()}.");
                    if (leftComponent.isPresent()) {
                        // Joins foreign key of left table to primary key of right table.
                        var fk = getForeignKey(leftComponent.get(), foreignKeyResolver);
                        var pk = getColumnName(getPkComponents(toTable).findFirst().orElseThrow(exception), columnNameResolver);
                        yield dialectTemplate()."\{aliasMapper.getAlias(root(fromTable), INNER, sqlTemplate.dialect())}.\{fk} = \{aliasMapper.getAlias(toTable, null, INNER, dialect)}.\{pk}";
                    } else {
                        var rightComponent = findComponent(rightComponents, fromTable);
                        if (rightComponent.isPresent()) {
                            // Joins foreign key of right table to primary key of left table.
                            var fk = getForeignKey(rightComponent.get(), foreignKeyResolver);
                            var pk = getColumnName(getPkComponents(fromTable).findFirst().orElseThrow(exception), columnNameResolver);
                            yield dialectTemplate()."\{aliasMapper.getAlias(root(fromTable), INNER, dialect)}.\{pk} = \{aliasMapper.getAlias(toTable, null, INNER, dialect)}.\{fk}";
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
                            yield dialectTemplate()."\{aliasMapper.getAlias(root(fromTable), INNER, dialect)}.\{fk} = \{aliasMapper.getAlias(toTable, null, INNER, dialect)}.\{pk}";
                        }
                    }
                }
                case TableTarget _ -> throw new SqlTemplateException("Unsupported source type.");   // Should not happen. See Join validation logic.
                case TemplateTarget ts -> parse(ts.template(), true);   // On-clause is correlated.
            };
            return switch (join) {
                case Join(TableSource ts, var alias, _, _, _) ->
                        dialectTemplate()."\n\{join.type().sql()} \{getTableName(ts.table(), tableNameResolver)} \{aliasMapper.useAlias(ts.table(), alias, INNER)} ON \{on}";
                case Join(TemplateSource ts, var alias, _, _, _) -> {
                    var source = parse(ts.template(), false);   // Source is not correlated.
                    yield dialectTemplate()."\n\{join.type().sql()} (\{source}) \{alias} ON \{on}";
                }
            };
        }
        return switch (join) {
            case Join(TableSource ts, var alias, _, _, _) ->
                    dialectTemplate()."\n\{join.type().sql()} \{getTableName(ts.table(), tableNameResolver)}\{alias.isEmpty() ? "" : STR." \{alias}"}";
            case Join(TemplateSource ts, var alias, _, _, _) -> {
                var source = parse(ts.template(), false);   // Source is not correlated.
                yield dialectTemplate()."\n\{join.type().sql()} (\{source})\{alias.isEmpty() ? "" : STR." \{alias}"}";
            }
        };
    }

    ElementResult table(Table it) throws SqlTemplateException {
        StringBuilder s = new StringBuilder();
        s.append(dialectTemplate()."\{getTableName(it.table(), sqlTemplate.tableNameResolver())}");
        if (!it.alias().isEmpty()) {
            s.append(STR." \{it.alias()}");
        }
        return new ElementResult(s.toString());
    }

    ElementResult alias(Alias it) throws SqlTemplateException {
        return new ElementResult(aliasMapper.getAlias(it.metamodel(), it.scope(), sqlTemplate.dialect()));
    }

    ElementResult column(Column it) throws SqlTemplateException {
        var columnNameResolver = sqlTemplate.columnNameResolver();
        var foreignKeyResolver = sqlTemplate.foreignKeyResolver();
        var dialect = sqlTemplate.dialect();
        RecordComponent component = getRecordComponent(it.metamodel().root(), it.metamodel().componentPath());
        String alias;
        ColumnName columnName;
        if (REFLECTION.isAnnotationPresent(component, FK.class)) {
            Class<?> table = component.getDeclaringRecord();
            if (Lazy.class.isAssignableFrom(table)) {
                table = getLazyRecordType(component);
            }
            //noinspection unchecked
            alias = aliasMapper.getAlias((Class<? extends Record>) table, it.metamodel().table().path(), it.scope(), dialect);
            columnName = getForeignKey(component, foreignKeyResolver);
        } else {
            alias = aliasMapper.getAlias(it.metamodel(), it.scope(), dialect);
            columnName = getColumnName(component, columnNameResolver);
        }
        return new ElementResult(dialectTemplate()."\{alias}.\{columnName}");
    }

    private String getVersionString(@Nonnull String columnName, @Nonnull Class<?> type, @Nonnull String alias) {
        String value = switch (type) {
            case Class<?> c when
                Integer.TYPE.isAssignableFrom(c)
                        || Long.TYPE.isAssignableFrom(c)
                        || Integer.class.isAssignableFrom(c)
                        || Long.class.isAssignableFrom(c)
                        || BigInteger.class.isAssignableFrom(c) -> STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{columnName} + 1";
            case Class<?> c when
                    Instant.class.isAssignableFrom(c)
                            || Date.class.isAssignableFrom(c)
                            || Calendar.class.isAssignableFrom(c)
                            || Timestamp.class.isAssignableFrom(c) -> "CURRENT_TIMESTAMP";
            default -> STR."\{columnName}";
        };
        return STR."\{alias.isEmpty() ? "" : STR."\{alias}."}\{columnName} = \{value}";
    }

    ElementResult set(Set it) throws SqlTemplateException{
        if (primaryTable == null) {
            throw new SqlTemplateException("Primary table not found.");
        }
        if (it.record() != null) {
            if (!primaryTable.table().isInstance(it.record())) {
                throw new SqlTemplateException(STR."Record \{it.record().getClass().getSimpleName()} does not match entity \{primaryTable.table().getSimpleName()}.");
            }
            var mapped = ModelMapper.of(modelBuilder.build(it.record(), false))
                    .map(it.record(), column -> !column.primaryKey() && column.updatable());
            List<String> args = new ArrayList<>();
            for (var entry : mapped.entrySet()) {
                var column = entry.getKey();
                if (!column.version()) {
                    args.add(STR."\{primaryTable.alias().isEmpty() ? "" : STR."\{primaryTable.alias()}."}\{column.qualifiedName(sqlTemplate.dialect())} = ?");
                    parameters.add(new PositionalParameter(parameterPosition.getAndIncrement(), entry.getValue()));
                    args.add(", ");
                } else {
                    var versionString = getVersionString(column.qualifiedName(sqlTemplate.dialect()), column.type(), primaryTable.alias());
                    versionAware.setPlain(true);
                    args.add(versionString);
                    args.add(", ");
                }
            }
            if (!args.isEmpty()) {
                args.removeLast();
            }
            return new ElementResult(String.join("", args));
        } else if (it.bindVars() != null) {
            if (it.bindVars() instanceof BindVarsImpl vars) {
                setBindVars(vars);
                AtomicInteger parameterCount = new AtomicInteger();
                String bindVarsString = modelBuilder.build(primaryTable.table(), false)
                        .columns().stream()
                        .filter(column -> !column.primaryKey() && column.updatable())
                        .map(column -> {
                            if (!column.version()) {
                                parameterCount.incrementAndGet();
                                //noinspection DataFlowIssue
                                return STR."\{primaryTable.alias().isEmpty() ? "" : STR."\{primaryTable.alias()}."}\{column.qualifiedName(sqlTemplate.dialect())} = ?";
                            }
                            versionAware.setPlain(true);
                            return getVersionString(column.qualifiedName(sqlTemplate.dialect()), column.type(), primaryTable.alias());
                        })
                        .collect(joining(", "));
                final int fixedParameterPosition = parameterPosition.get();
                vars.addParameterExtractor(record -> {
                    try {
                        AtomicInteger position = new AtomicInteger(fixedParameterPosition);
                        return ModelMapper.of(modelBuilder.build(record, false))
                                .map(record, column -> !column.primaryKey() && column.updatable() && !column.version())
                                .values().stream()
                                .map(o -> new PositionalParameter(position.getAndIncrement(), o))
                                .toList();
                    } catch (SqlTemplateException ex) {
                        // BindVars works at the abstraction level of the ORM, so we throw a PersistenceException here.
                        throw new PersistenceException(ex);
                    }
                });
                parameterPosition.setPlain(parameterPosition.get() + parameterCount.getPlain());
                return new ElementResult(bindVarsString);
            }
            throw new SqlTemplateException("Unsupported BindVars type.");
        }
        throw new SqlTemplateException("No values found for Set.");
    }

    private Object resolveElements(@Nullable Object value) throws SqlTemplateException {
        return switch (value) {
            case StringTemplate _ -> throw new SqlTemplateException("StringTemplate not allowed as string template value.");
            case Stream<?> _ -> throw new SqlTemplateException("Stream not supported as string template value.");
            case Subqueryable t -> new Subquery(t.getSubquery(), true);
            case Metamodel<?, ?> m -> new Column(m, CASCADE);
            case null, default -> value;
        };
    }

    private String getTemplateExpressionString(@Nonnull StringTemplate template) throws SqlTemplateException{
        var fragments = template.fragments();
        var values = template.values();
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < fragments.size(); i++) {
            String fragment = fragments.get(i);
            parts.add(fragment);
            if (i < values.size()) {
                Object value = resolveElements(values.get(i));
                switch (value) {
                    case Expression exp -> parts.add(getExpressionString(exp));
                    case Subquery s -> parts.add(parse(s.template(), s.correlate()));
                    case Column c -> parts.add(column(c).get());
                    case Unsafe u -> parts.add(u.sql());
                    case Table t -> parts.add(dialectTemplate()."\{getTableName(t.table(), sqlTemplate.tableNameResolver())}\{t.alias().isEmpty() ? "" : STR." \{t.alias()}"}");
                    case Alias a -> parts.add(aliasMapper.getAlias(a.metamodel(), a.scope(), sqlTemplate.dialect()));
                    case Param p when p.name() != null -> parts.add(registerParam(p.name(), p.dbValue()));
                    case Param p -> parts.add(registerParam(p.dbValue()));
                    case Record r -> parts.add(getObjectExpressionString(r));
                    case Class<?> c when c.isRecord() -> //noinspection unchecked
                            parts.add(aliasMapper.getAlias(root((Class<? extends Record>) c), CASCADE, sqlTemplate.dialect()));
                    case Object k when REFLECTION.isSupportedType(k) -> parts.add(aliasMapper.getAlias(root(REFLECTION.getRecordType(k)), CASCADE, sqlTemplate.dialect()));
                    case Stream<?> _ -> throw new SqlTemplateException("Stream not supported in expression.");
                    case Query _ -> throw new SqlTemplateException("Query not supported in expression. Use QueryBuilder instead.");
                    case Element e -> throw new SqlTemplateException(STR."Unsupported element type in expression: \{e.getClass().getSimpleName()}.");
                    default -> parts.add(registerParam(value));
                }
            }
        }
        return String.join("", parts);
    }

    private String getExpressionString(Expression expression) throws SqlTemplateException {
        return switch(expression) {
            case TemplateExpression it -> getTemplateExpressionString(it.template());
            case ObjectExpression it -> getObjectExpressionString(it.metamodel(), it.operator(), it.object());
        };
    }

    private String getObjectExpressionString(@Nonnull Object object)
            throws SqlTemplateException {
        return getObjectExpressionString(null, EQUALS, object);
    }

    private Iterable<?> getObjectIterable(@Nonnull Object object) throws SqlTemplateException {
        return switch (object) {
            case null -> throw new SqlTemplateException("Null object not supported.");
            case Object[] a -> asList(a);   // Use this instead of List.of() to allow null values.
            case Iterable<?> i -> i;
            case BindVars _ -> throw new SqlTemplateException("BindVars not supported in this context.");
            case Stream<?> _ -> throw new SqlTemplateException("Stream not supported in this context. Use Iterable or varargs instead.");
            case StringTemplate _ -> throw new SqlTemplateException("String template not supported in this context. Use expression method instead.");
            default -> List.of(object); // Not expected at the moment though.
        };
    }

    private String getObjectExpressionString(@Nullable Metamodel<?, ?> metamodel,
                                             @Nonnull Operator operator,
                                             @Nonnull Object object) throws SqlTemplateException {
        Class<? extends Record> rootTable;
        String path;
        String alias;
        Class<?> pkType;
        if (primaryTable == null) {
            throw new SqlTemplateException("Primary table unknown.");
        }
        if (metamodel == null) {
            rootTable = primaryTable.table();
            alias = primaryTable.alias();
            pkType = REFLECTION.findPKType(primaryTable.table()).orElse(null);
            path = null;
        } else {
            rootTable = metamodel.root();
            path = metamodel.componentPath();
            alias = aliasMapper.getAlias(rootTable, null, INNER, sqlTemplate.dialect());
            pkType = null;  // PKs only supported when using primaryTable directly.
        }
        String column = null;
        int size = 0;
        List<Map<String, Object>> multiValues = new ArrayList<>();
        for (var o : getObjectIterable(object)) {
            if (o == null) {
                parameters.add(new PositionalParameter(parameterPosition.getAndIncrement(), null));
                size++;
                continue;
            }
            Class<?> elementType = o.getClass();
            Map<String, Object> valueMap;
            if (metamodel == null) {
                if ((pkType != null && (pkType == elementType || (pkType.isPrimitive() && isPrimitiveCompatible(o, pkType))))) {
                    valueMap = getValuesForCondition(o, rootTable, alias);
                    if (valueMap.isEmpty()) {
                        throw new SqlTemplateException(STR."Failed to find primary key field for \{rootTable.getSimpleName()} table.");
                    }
                } else if (elementType.isRecord()) {
                    //noinspection DataFlowIssue
                    valueMap = getValuesForCondition((Record) o, null, primaryTable.table(), rootTable, alias, versionAware.getPlain());
                    if (valueMap.isEmpty()) {
                        throw new SqlTemplateException(STR."Failed to find \{o.getClass().getSimpleName()} record on \{rootTable.getSimpleName()} table graph.");
                    }
                } else {
                    throw new SqlTemplateException("Specify a metamodel to uniquely identify a field.");
                }
            } else {
                if (elementType.isRecord()) {
                    valueMap = getValuesForCondition((Record) o, path, primaryTable.table(), rootTable, alias, versionAware.getPlain());
                    if (valueMap.isEmpty()) {
                        throw new SqlTemplateException(STR."Failed to find field for \{o.getClass().getSimpleName()} argument on \{rootTable.getSimpleName()} table graph.");
                    }
                } else {
                    valueMap = getValuesForCondition(o, path, primaryTable.table(), rootTable);
                    if (valueMap.isEmpty()) {
                        throw new SqlTemplateException(STR."Failed to find field for \{o.getClass().getSimpleName()} argument on \{rootTable.getSimpleName()} table at path '\{path}'.");
                    }
                }
            }
            if (multiValues.isEmpty() && valueMap.size() == 1) {
                var entry = valueMap.entrySet().iterator().next();
                parameters.add(new PositionalParameter(parameterPosition.getAndIncrement(), entry.getValue()));
                var k = entry.getKey();
                if (column != null) {
                    if (!column.equals(k)) {
                        throw new SqlTemplateException(STR."Multiple columns specified by where-clause argument: \{column} and \{k}.");
                    }
                }
                column = k;
                size++;
            } else {
                if (column != null) {
                    throw new SqlTemplateException("Multiple columns specified by where-clause arguments.");
                }
                multiValues.add(valueMap);
            }
        }
        if (!multiValues.isEmpty()) {
            return getMultiValuesIn(multiValues);
        }
        if (column == null) {
            var valueMap = path != null
                    ? getValuesForCondition(null, path, primaryTable.table(), rootTable)
                    : getValuesForCondition(null, rootTable, alias);
            if (valueMap.size() == 1) {
                column = valueMap.sequencedKeySet().getFirst();
            } else if (valueMap.isEmpty()) {
                throw new SqlTemplateException(STR."Failed to find field on \{rootTable.getSimpleName()} table at path '\{path}'.");
            }
        }
        // If column is still not resolved, we will let the operator take care it, and potentially raise an error.
        // A common (valid) use case is where object is an empty array or collection and no path is specified.
        try {
            return operator.format(column, size);
        } catch (IllegalArgumentException e) {
            throw new SqlTemplateException(e);
        }
    }

    private String getMultiValuesIn(@Nonnull List<Map<String, Object>> values) throws SqlTemplateException {
        return sqlTemplate.dialect().multiValueIn(values,
                o -> parameters.add(new PositionalParameter(parameterPosition.getAndIncrement(), o)));
    }

    ElementResult where(Where it) throws SqlTemplateException {
        if (primaryTable == null) {
            throw new SqlTemplateException("Primary entity not found.");
        }
        Expression expression = it.expression();
        if (expression != null) {
            return new ElementResult(getExpressionString(expression));
        } else if (it.bindVars() != null) {
            String bindVarsStr = getBindVarsStringForWhere(primaryTable.table(), primaryTable.alias(), versionAware.getPlain());
            if (it.bindVars() instanceof BindVarsImpl vars) {
                setBindVars(vars);
                final int fixedParameterPosition = parameterPosition.get();
                vars.addParameterExtractor(record -> {
                    try {
                        AtomicInteger position = new AtomicInteger(fixedParameterPosition);
                        return getValuesForCondition(record, null, primaryTable.table(), primaryTable.table(), primaryTable.alias(), versionAware.getPlain())
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
        var table = primaryTable.table();
        var records = it.records();
        if (records != null) {
            List<String> args = new ArrayList<>();
            for (var record : records) {
                if (record == null) {
                    throw new SqlTemplateException("Record is null.");
                }
                if (!table.isInstance(record)) {
                    throw new SqlTemplateException(STR."Record \{record.getClass().getSimpleName()} does not match entity \{table.getSimpleName()}.");
                }
                var values = ModelMapper.of(modelBuilder.build(record, false))
                        .map(record, column -> !column.autoGenerated() && column.insertable())
                        .sequencedValues();
                if (values.isEmpty()) {
                    throw new SqlTemplateException("No values found for Insert.");
                }
                args.add(STR."(\{"?, ".repeat(values.size() - 1)}?)");
                for (var o : values) {
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
                setBindVars(vars);
                AtomicInteger parameterCount = new AtomicInteger();
                String bindVarsString = modelBuilder.build(primaryTable.table(), false)
                        .columns().stream()
                        .filter(column -> !column.autoGenerated() && column.insertable())
                        .map(_ -> {
                            parameterCount.incrementAndGet();
                            return "?";
                        })
                        .collect(joining(", "));
                final int fixedParameterPosition = parameterPosition.get();
                vars.addParameterExtractor(record -> {
                    try {
                        AtomicInteger position = new AtomicInteger(fixedParameterPosition);
                        return ModelMapper.of(modelBuilder.build(record, false))
                                .map(record, column -> !column.autoGenerated() && column.insertable())
                                .sequencedValues()
                                .stream()
                                .map(o -> new PositionalParameter(position.getAndIncrement(), o))
                                .toList();
                    } catch (SqlTemplateException ex) {
                        // BindVars works at the abstraction level of the ORM, so we throw a PersistenceException here.
                        throw new PersistenceException(ex);
                    }
                });
                parameterPosition.set(parameterPosition.get() + parameterCount.getPlain());
                return new ElementResult(STR."(\{bindVarsString})");
            }
            throw new SqlTemplateException("Unsupported BindVars type.");
        }
        throw new SqlTemplateException("No values found for Values.");
    }

    private String toArgsString(Iterable<?> iterable) {
        List<String> args = new ArrayList<>();
        for (var v : iterable) {
            args.add("?");
            args.add(", ");
            parameters.add(new PositionalParameter(parameterPosition.getAndIncrement(), v));
        }
        if (!args.isEmpty()) {
            args.removeLast();    // Remove last ", " element.
        }
        return String.join("", args);
    }

    private String registerParam(@Nullable Object value) throws SqlTemplateException {
        return switch (value) {
            case Object[] array when sqlTemplate.expandCollection() -> toArgsString(List.of(array));
            case Iterable<?> it when sqlTemplate.expandCollection() -> toArgsString(it);
            case Object[] _ -> throw new SqlTemplateException("Array parameters not supported.");
            case Iterable<?> _ -> throw new SqlTemplateException("Collection parameters not supported.");
            case null, default -> {
                parameters.add(new PositionalParameter(parameterPosition.getAndIncrement(), value));
                yield "?";
            }
        };
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

    ElementResult var(Var it) throws SqlTemplateException{
        if (it.bindVars() instanceof BindVarsImpl vars) {
            setBindVars(vars);
            final int position = parameterPosition.getAndIncrement();
            vars.addParameterExtractor(record -> List.of(new PositionalParameter(position, it.extractor().apply(record))));
            return new ElementResult("?");
        }
        throw new SqlTemplateException("Unsupported BindVars type.");
    }

    ElementResult subquery(Subquery it) throws SqlTemplateException{
        return new ElementResult(parse(it.template(), it.correlate()));
    }

    ElementResult unsafe(Unsafe it) {
        return new ElementResult(it.sql());
    }

    String parse(StringTemplate template, boolean correlate) throws SqlTemplateException{
        Callable<String> callable = () -> {
            Sql sql = sqlTemplate.process(template, true);
            for (var parameter : sql.parameters()) {
                switch (parameter) {
                    case PositionalParameter p -> registerParam(p.dbValue());
                    case NamedParameter n -> registerParam(n.name(), n.dbValue());
                }
            }
            return sql.statement();
        };
        try {
            if (correlate) {
                return ScopedValue.callWhere(CURRENT_PROCESSOR, this, callable);
            }
            return callable.call();
        } catch (SqlTemplateException e) {
            throw e;
        } catch (Exception e) {
            throw new SqlTemplateException(e);
        }
    }

    private void setBindVars(BindVarsImpl vars) throws SqlTemplateException{
        var current = bindVariables.getPlain();
        if (current != null && current != vars) {
            throw new SqlTemplateException("Multiple BindVars instances not supported.");
        }
        if (vars instanceof BindVarsImpl bindVars) {
            bindVariables.set(bindVars);
        } else {
            throw new SqlTemplateException("Unsupported BindVars type.");
        }
    }

    private static @Nullable List<RecordComponent> resolvePath(@Nonnull Class<? extends Record> root,
                                                               @Nonnull Class<? extends Record> target) throws SqlTemplateException{
        List<RecordComponent> path = new ArrayList<>();
        List<RecordComponent> searchPath = new ArrayList<>(); // Temporary path for exploration.
        int pathsFound = resolvePath(root, target, searchPath, path, 0);
        if (pathsFound == 0) {
            return null;
        } else if (pathsFound > 1) {
            throw new SqlTemplateException(STR."Multiple paths to the target \{target.getSimpleName()} found in \{root.getSimpleName()}.");
        }
        return path;
    }

    private static int resolvePath(@Nonnull Class<? extends Record> current,
                                   @Nonnull Class<? extends Record> target,
                                   @Nonnull List<RecordComponent> searchPath,
                                   @Nonnull List<RecordComponent> path, int pathsFound) {
        if (current == target) {
            if (pathsFound == 0) {
                path.clear();
                path.addAll(searchPath);
            }
            return pathsFound + 1;
        }
        for (RecordComponent component : current.getRecordComponents()) {
            Class<?> componentType = component.getType();
            if (componentType.isRecord() && REFLECTION.isAnnotationPresent(component, FK.class)) {
                searchPath.add(component);
                //noinspection unchecked
                pathsFound = resolvePath((Class<? extends Record>) componentType, target, searchPath, path, pathsFound);
                if (pathsFound > 1) {
                    return pathsFound; // Early return if multiple paths are found.
                }
                searchPath.removeLast();
            } else if (componentType == target) {
                searchPath.add(component);
                pathsFound++;
                if (pathsFound == 1) {
                    path.clear();
                    path.addAll(searchPath);
                }
                searchPath.removeLast();
                if (pathsFound > 1) {
                    return pathsFound;
                }
            }
        }
        return pathsFound;
    }

    private String getColumnsStringForSelect(@Nonnull Class<? extends Record> type,
                                             boolean nested,
                                             @Nonnull AliasMapper aliasMapper,
                                             @Nullable Class<? extends Record> primaryTable) throws SqlTemplateException {
        var parts = new ArrayList<String>();
        // Resolve the path of type, starting from the primary table. This will help in resolving the alias in case the same table is used multiple times.
        List<RecordComponent> path = null;
        String pathString = null;
        if (primaryTable != null) {
            List<RecordComponent> pathList = resolvePath(primaryTable, type);
            if (pathList != null) {
                path = pathList;
                pathString = toPathString(pathList);
            }
        }
        // Path is implicit. First try the path, if not found, try without the path.
        var resolvedAlias = aliasMapper.findAlias(type, pathString, INNER);
        if (resolvedAlias.isEmpty()) {
            resolvedAlias = aliasMapper.findAlias(type, null, INNER);
        }
        var alias = resolvedAlias.orElse("");
        getColumnsStringForSelect(type, nested, path, aliasMapper, primaryTable, alias, parts);
        if (!parts.isEmpty()) {
            parts.removeLast();
        }
        return java.lang.String.join("", parts);
    }

    private void getColumnsStringForSelect(@Nonnull Class<? extends Record> type,
                                           boolean nested,
                                           @Nullable List<RecordComponent> path,
                                           @Nonnull AliasMapper aliasMapper,
                                           @Nullable Class<? extends Record> primaryTable,
                                           @Nonnull String alias,
                                           @Nonnull List<String> parts) throws SqlTemplateException {
        for (var component : type.getRecordComponents()) {
            boolean fk = REFLECTION.isAnnotationPresent(component, FK.class);
            boolean lazy = Lazy.class.isAssignableFrom(component.getType());
            var converter = getORMConverter(component).orElse(null);
            if (converter != null) {
                converter.getColumns(c -> RecordReflection.getColumnName(c, sqlTemplate.columnNameResolver())).forEach(name -> {
                    if (!alias.isEmpty()) {
                        parts.add(dialectTemplate()."\{alias}.\{name}");
                    } else {
                        parts.add(dialectTemplate()."\{name}");
                    }
                    parts.add(", ");
                });
            } else if (fk && (!nested || lazy)) {   // Use foreign key column if not nested or if lazy.
                var name = getForeignKey(component, sqlTemplate.foreignKeyResolver());
                if (!alias.isEmpty()) {
                    parts.add(dialectTemplate()."\{alias}.\{name}");
                } else {
                    parts.add(dialectTemplate()."\{name}");
                }
                parts.add(", ");
            } else if (component.getType().isRecord()) {
                @SuppressWarnings("unchecked")
                var recordType = (Class<? extends Record>) component.getType();
                List<RecordComponent> newPath;
                if (path != null) {
                    newPath = new ArrayList<>(path);
                    newPath.add(component);
                    newPath = copyOf(newPath);
                } else if (recordType == primaryTable) {
                    newPath = List.of();
                } else {
                    newPath = null;
                }
                getColumnsStringForSelect(recordType, true, newPath, aliasMapper, primaryTable, getAlias(recordType, newPath, alias, aliasMapper, sqlTemplate.dialect()), parts);
            } else {
                if (lazy) {
                    throw new SqlTemplateException(STR."Lazy component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is not a foreign key.");
                }
                var name = getColumnName(component, sqlTemplate.columnNameResolver());
                if (!alias.isEmpty()) {
                    parts.add(dialectTemplate()."\{alias}.\{name}");
                } else {
                    parts.add(dialectTemplate()."\{name}");
                }
                parts.add(", ");
            }
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

    private String getBindVarsStringForWhere(@Nonnull Class<? extends Record> recordType,
                                             @Nonnull String alias,
                                             boolean updateMode) throws SqlTemplateException {
        return getPkNamesForWhere(recordType, alias, updateMode).stream()
                .map(pkName -> STR."\{pkName} = ?")
                .collect(joining(" AND "));
    }

    @SuppressWarnings("unchecked")
    private List<String> getPkNamesForWhere(@Nonnull Class<? extends Record> recordType,
                                            @Nonnull String alias,
                                            boolean updateMode) throws SqlTemplateException {
        var names = new ArrayList<String>();
        for (var component : recordType.getRecordComponents()) {
            boolean pk = REFLECTION.isAnnotationPresent(component, PK.class);
            if (component.getType().isRecord()) {
                if (getORMConverter(component).isPresent()) {
                    continue;
                }
                if (REFLECTION.isAnnotationPresent(component, FK.class)) {
                    continue;
                }
                // @Inline is implicitly assumed.
                names.addAll(getPkNamesForWhere((Class<? extends Record>) component.getType(), alias, pk));
            } else if (pk) {
                names.add(dialectTemplate()."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(component, sqlTemplate.columnNameResolver())}");
            } else if (updateMode && REFLECTION.isAnnotationPresent(component, Version.class)) {
                names.add(dialectTemplate()."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(component, sqlTemplate.columnNameResolver())}");
            }
        }
        return names;
    }

    private Map<String, Object> getValuesForCondition(@Nonnull Record record,
                                                      @Nullable String path,
                                                      @Nonnull Class<? extends Record> primaryTable,
                                                      @Nonnull Class<? extends Record> rootTable,
                                                      @Nonnull String alias,
                                                      boolean updateMode) throws SqlTemplateException {
        try {
            var values = new LinkedHashMap<String, Object>();
            if (rootTable.isInstance(record)) {
                for (var component : record.getClass().getRecordComponents()) {
                    if (REFLECTION.isAnnotationPresent(component, PK.class)) {
                        Object pk = REFLECTION.invokeComponent(component, record);
                        if (pk instanceof Record) {
                            values.putAll(getValuesForCondition(pk, record.getClass(), alias));
                        } else {
                            values.put(dialectTemplate()."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(component, sqlTemplate.columnNameResolver())}", REFLECTION.invokeComponent(component, record));
                        }
                    } else if (updateMode // Only apply version check if in update mode to prevent side effects when comparing objects in other modes.
                            && REFLECTION.isAnnotationPresent(component, Version.class)) {
                        values.put(dialectTemplate()."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(component, sqlTemplate.columnNameResolver())}", REFLECTION.invokeComponent(component, record));
                    }
                }
                return values;
            }
            String searchPath = rootTable != primaryTable && path != null && path.isEmpty() ? null : path;
            Mapping mapping = tableMapper.getMapping(record.getClass(), searchPath == null ? null : rootTable, searchPath);
            String a = mapping.alias();
            if (mapping.primaryKey()) {
                for (var component : mapping.components()) {
                    Object pk = REFLECTION.invokeComponent(component, record);
                    if (pk instanceof Record) {
                        values.putAll(getValuesForInlined((Record) pk, mapping.alias()));
                    } else {
                        values.put(dialectTemplate()."\{a.isEmpty() ? "" : STR."\{a}."}\{getColumnName(component, sqlTemplate.columnNameResolver())}", REFLECTION.invokeComponent(component, record));
                    }
                }
            } else {
                assert mapping.components().size() == 1;
                for (var component : record.getClass().getRecordComponents()) {
                    if (REFLECTION.isAnnotationPresent(component, PK.class)) {
                        values.put(dialectTemplate()."\{a.isEmpty() ? "" : STR."\{a}."}\{getForeignKey(mapping.components().getFirst(), sqlTemplate.foreignKeyResolver())}", REFLECTION.invokeComponent(component, record));
                        break;  // Foreign key mappings can only be based on a single column.
                    }
                }
            }
            return values;
        } catch (SqlTemplateException e) {
            throw e;
        } catch (Throwable t) {
            throw new SqlTemplateException(t);
        }
    }

    private SequencedMap<String, Object> getValuesForCondition(@Nullable Object id,
                                                               @Nonnull Class<? extends Record> recordType,
                                                               @Nonnull String alias) throws SqlTemplateException {
        try {
            var values = new LinkedHashMap<String, Object>();
            for (var component : recordType.getRecordComponents()) {
                if (REFLECTION.isAnnotationPresent(component, PK.class)) {
                    if (component.getType().isRecord()) {
                        if (id != null) {
                            if (REFLECTION.isAnnotationPresent(component, FK.class)) {
                                values.put(dialectTemplate()."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getForeignKey(component, sqlTemplate.foreignKeyResolver())}", getPkForForeignKey((Record) id));
                            } else if (recordType.isInstance(id)) {
                                values.putAll(getValuesForInlined((Record) REFLECTION.invokeComponent(component, id), alias));
                            } else {
                                values.putAll(getValuesForInlined((Record) id, alias));
                            }
                        } else {
                            values.put(dialectTemplate()."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(component, sqlTemplate.columnNameResolver())}", null);
                        }
                    } else {
                        values.put(dialectTemplate()."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(component, sqlTemplate.columnNameResolver())}", id);
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

    private SequencedMap<String, Object> getValuesForCondition(@Nullable Object value,
                                                               @Nonnull String path,
                                                               @Nonnull Class<? extends Record> primaryTable,
                                                               @Nonnull Class<? extends Record> recordType) throws SqlTemplateException {
        return getValuesForCondition(value, path, primaryTable, recordType, 0, null, 0);
    }

    private SequencedMap<String, Object> getValuesForCondition(@Nullable Object value,
                                                               @Nonnull String path,
                                                               @Nonnull Class<? extends Record> primaryTable,
                                                               @Nonnull Class<? extends Record> recordType,
                                                               int depth,
                                                               @Nullable Class<? extends Record> inlineParentType,
                                                               int inlineDepth) throws SqlTemplateException {
        assert value == null || !value.getClass().isRecord();
        try {
            var values = new LinkedHashMap<String, Object>();
            var parts = path.split("\\.");
            var components = (inlineParentType != null ? inlineParentType : recordType).getRecordComponents();
            if (parts.length == depth + 1) {
                String name = parts[depth];
                for (var component : components) {
                    if (component.getName().equals(name)) {
                        String searchPath = Stream.of(parts).limit(depth - inlineDepth).collect(joining("."));
                        if (recordType != primaryTable && searchPath.isEmpty()) {
                            searchPath = null;
                        }
                        String alias = aliasMapper.getAlias(recordType, searchPath, INNER, sqlTemplate.dialect());
                        if (REFLECTION.isAnnotationPresent(component, FK.class)) {
                            values.put(dialectTemplate()."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getForeignKey(component, sqlTemplate.foreignKeyResolver())}", value);
                        } else {
                            values.put(dialectTemplate()."\{alias.isEmpty() ? "" : STR."\{alias}."}\{getColumnName(component, sqlTemplate.columnNameResolver())}", value);
                        }
                        break;
                    }
                }
            } else {
                for (var component : components) {
                    if (component.getName().equals(parts[depth])) {
                        if (component.getType().isRecord()) {
                            var converter = getORMConverter(component);
                            if (converter.isPresent()) {
                                continue;
                            }
                            boolean fk = REFLECTION.isAnnotationPresent(component, FK.class);
                            Class<? extends Record> type;
                            Class<? extends Record> inlineType;
                            if (fk) {
                                //noinspection unchecked
                                type = (Class<? extends Record>) component.getType();
                                inlineType = null;
                            } else {    // Can either be PK or Inline.
                                // Assuming @Inline; No need to check for optional annotation.
                                type = recordType;
                                //noinspection unchecked
                                inlineType = (Class<? extends Record>) component.getType();
                            }
                            values.putAll(getValuesForCondition(
                                    value,
                                    path,
                                    primaryTable,
                                    type,
                                    depth + 1,
                                    inlineType,
                                    inlineDepth + (inlineType != null ? 1 : 0)));
                        }
                        break;
                    }
                }
            }
            return values;
        } catch (SqlTemplateException e) {
            throw e;
        } catch (Throwable t) {
            throw new SqlTemplateException(t);
        }
    }

    private Map<String, Object> getValuesForInlined(@Nonnull Record record,
                                                    @Nonnull String alias) throws SqlTemplateException {
        try {
            var values = new LinkedHashMap<String, Object>();
            for (var component : record.getClass().getRecordComponents()) {
                Object o = REFLECTION.invokeComponent(component, record);
                if (o instanceof Record r) {
                    values.putAll(getValuesForInlined(r, alias));
                } else {
                    values.put(STR."\{alias.isEmpty() ? "" : dialectTemplate()."\{alias}.\{getColumnName(component, sqlTemplate.columnNameResolver())}"}", o);
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
                                   @Nullable List<RecordComponent> path,
                                   @Nonnull String alias,
                                   @Nonnull AliasMapper aliasMapper,
                                   @Nonnull SqlDialect dialect) throws SqlTemplateException {
        if (path == null) {
            var result = aliasMapper.findAlias(type, null, INNER);
            if (result.isPresent()) {
                return result.get();
            }
            if (alias.isEmpty()) {
                throw new SqlTemplateException(STR."Alias not found for \{type.getSimpleName()}");
            }
            return alias;
        }
        String p = toPathString(path);
        if (!path.isEmpty()) {
            RecordComponent lastComponent = path.getLast();
            if (REFLECTION.isAnnotationPresent(lastComponent, FK.class)) {
                // Path is implicit. First try the path, if not found, try without the path.
                var resolvedAlias = aliasMapper.findAlias(type, p, INNER);
                if (resolvedAlias.isPresent()) {
                    return resolvedAlias.get();
                }
                return aliasMapper.findAlias(type, null, INNER)
                        .orElseThrow(() -> new SqlTemplateException(STR."Alias not found for \{type.getSimpleName()} at path \{p}."));
            }
            if (REFLECTION.isAnnotationPresent(lastComponent, PK.class)) {
                return alias;
            }
            if (getORMConverter(lastComponent).isEmpty()
                    && !aliasMapper.exists(type, INNER)) { // Check needed for records without annotations.
                return alias; // @Inline is implicitly assumed.
            }
        }
        // Fallback for records without annotations.
        return aliasMapper.getAlias(root(type), INNER, dialect);
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
