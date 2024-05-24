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
import st.orm.Inline;
import st.orm.Lazy;
import st.orm.Name;
import st.orm.PK;
import st.orm.repository.Entity;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.ColumnNameResolver;
import st.orm.template.ForeignKeyResolver;
import st.orm.template.JoinType;
import st.orm.template.Sql;
import st.orm.template.SqlTemplate;
import st.orm.template.SqlTemplateException;
import st.orm.template.TableAliasResolver;
import st.orm.template.TableNameResolver;
import st.orm.template.impl.Elements.Delete;
import st.orm.template.impl.Elements.Expression;
import st.orm.template.impl.Elements.From;
import st.orm.template.impl.Elements.Insert;
import st.orm.template.impl.Elements.Param;
import st.orm.template.impl.Elements.Select;
import st.orm.template.impl.Elements.Source;
import st.orm.template.impl.Elements.Table;
import st.orm.template.impl.Elements.TableSource;
import st.orm.template.impl.Elements.Unsafe;
import st.orm.template.impl.Elements.Update;
import st.orm.template.impl.Elements.Where;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.MissingFormatArgumentException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Character.isUpperCase;
import static java.lang.Character.toLowerCase;
import static java.lang.Long.toHexString;
import static java.lang.StringTemplate.RAW;
import static java.lang.System.getProperty;
import static java.lang.ThreadLocal.withInitial;
import static java.util.Collections.newSetFromMap;
import static java.util.Comparator.comparing;
import static java.util.List.copyOf;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static java.util.regex.Pattern.DOTALL;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static st.orm.Templates.alias;
import static st.orm.Templates.delete;
import static st.orm.Templates.from;
import static st.orm.Templates.insert;
import static st.orm.Templates.param;
import static st.orm.Templates.select;
import static st.orm.Templates.set;
import static st.orm.Templates.update;

/**
 *
 */
public final class SqlTemplateImpl implements SqlTemplate {

    public static final ThreadLocal<Set<Consumer<Sql>>> CONSUMERS = withInitial(() -> newSetFromMap(new IdentityHashMap<>()));

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private static final AliasResolveStrategy DEFAULT_ALIAS_RESOLVE_STRATEGY = AliasResolveStrategy.valueOf(getProperty("st.orm.alias_resolve_strategy", "ALL"));

    /**
     * A result record that contains the generated SQL and the parameters that were used to generate it.
     *
     * @param statement the generated SQL with all parameters replaced by '?' or named ':name' placeholders.
     * @param parameters the parameters that were used to generate the SQL.
     * @param bindVariables a bind variables object that can be used to add bind variables to a batch.
     * @param generatedKeys the primary key that have been auto generated as part of in insert statement.
     * @param versionAware true if the statement is version aware, false otherwise.
     */
    private record SqlImpl(
            @Nonnull String statement,
            @Nonnull List<Parameter> parameters,
            @Nullable Optional<BindVariables> bindVariables,
            @Nonnull List<String> generatedKeys,
            boolean versionAware
    ) implements Sql {}

    private enum SqlMode {
        SELECT, INSERT, UPDATE, DELETE, UNDEFINED
    }

    record Wrapped(@Nonnull List<Element> elements) implements Element {
        public Wrapped {
            requireNonNull(elements, "elements");
        }
    }

    public enum DefaultJoinType implements JoinType {
        INNER("INNER JOIN", true, false),
        CROSS("CROSS JOIN", false, false),
        LEFT("LEFT JOIN", true, true),
        RIGHT("RIGHT JOIN", true, true);

        private final String sql;
        private final boolean on;
        private final boolean outer;

        DefaultJoinType(@Nonnull String sql, boolean on, boolean outer) {
            this.sql = sql;
            this.on = on;
            this.outer = outer;
        }

        @Override
        public String sql() {
            return sql;
        }

        @Override
        public boolean hasOnClause() {
            return on;
        }

        @Override
        public boolean isOuter() {
            return outer;
        }
    }

    record Join(@Nonnull Source source, @Nonnull String alias, @Nonnull StringTemplate on, @Nonnull JoinType type) implements Element {
        public Join {
            requireNonNull(source, "source");
            requireNonNull(alias, "alias");
            requireNonNull(on, "on");
            requireNonNull(type, "type");
        }
    }

    record On(@Nonnull Class<? extends Record> fromTable, @Nonnull Class<? extends Record> toTable) {
        public On {
            requireNonNull(fromTable, "fromTable");
            requireNonNull(toTable, "toTable");
        }
    }

    record Eval(@Nonnull Object object) implements Element {
        public Eval {
            requireNonNull(object, "object");
        }
    }
    record ResolvedEval(@Nonnull Element element) implements Element {}

    private final boolean positionalOnly;
    private final boolean expandCollection;
    private final boolean supportRecords;
    private final AliasResolveStrategy aliasResolveStrategy;
    private final TableNameResolver tableNameResolver;
    private final TableAliasResolver tableAliasResolver;
    private final ColumnNameResolver columnNameResolver;
    private final ForeignKeyResolver foreignKeyResolver;

    public SqlTemplateImpl(boolean positionalOnly, boolean expandCollection, boolean supportRecords) {
        this(positionalOnly, expandCollection, supportRecords, DEFAULT_ALIAS_RESOLVE_STRATEGY, null, null, null, null);
    }

    public SqlTemplateImpl(boolean positionalOnly,
                           boolean expandCollection,
                           boolean supportRecords,
                           @Nonnull AliasResolveStrategy aliasResolveStrategy,
                           @Nullable TableNameResolver tableNameResolver,
                           @Nullable TableAliasResolver tableAliasResolver,
                           @Nullable ColumnNameResolver columnNameResolver,
                           @Nullable ForeignKeyResolver foreignKeyResolver) {
        this.positionalOnly = positionalOnly;
        this.expandCollection = expandCollection;
        this.supportRecords = supportRecords;
        this.aliasResolveStrategy = aliasResolveStrategy;
        this.tableNameResolver = tableNameResolver;
        this.tableAliasResolver = tableAliasResolver;
        this.columnNameResolver = columnNameResolver;
        this.foreignKeyResolver = foreignKeyResolver;
    }

    @Override
    public boolean positionalOnly() {
        return positionalOnly;
    }

    @Override
    public boolean expandCollection() {
        return expandCollection;
    }

    @Override
    public SqlTemplateImpl withTableNameResolver(TableNameResolver resolver) {
        return new SqlTemplateImpl(positionalOnly, expandCollection, supportRecords, aliasResolveStrategy, resolver, tableAliasResolver, columnNameResolver, foreignKeyResolver);
    }

    @Override
    public TableNameResolver tableNameResolver() {
        return tableNameResolver;
    }

    @Override
    public SqlTemplateImpl withTableAliasResolver(TableAliasResolver resolver) {
        return new SqlTemplateImpl(positionalOnly, expandCollection, supportRecords, aliasResolveStrategy, tableNameResolver, resolver, columnNameResolver, foreignKeyResolver);
    }

    @Override
    public TableAliasResolver tableAliasResolver() {
        return tableAliasResolver;
    }

    @Override
    public SqlTemplateImpl withColumnNameResolver(ColumnNameResolver resolver) {
        return new SqlTemplateImpl(positionalOnly, expandCollection, supportRecords, aliasResolveStrategy, tableNameResolver, tableAliasResolver, resolver, foreignKeyResolver);
    }

    @Override
    public ColumnNameResolver columnNameResolver() {
        return columnNameResolver;
    }

    @Override
    public SqlTemplateImpl withForeignKeyResolver(ForeignKeyResolver resolver) {
        return new SqlTemplateImpl(positionalOnly, expandCollection, supportRecords, aliasResolveStrategy, tableNameResolver, tableAliasResolver, columnNameResolver, resolver);
    }

    @Override
    public ForeignKeyResolver foreignKeyResolver() {
        return foreignKeyResolver;
    }

    @Override
    public SqlTemplateImpl withSupportRecords(boolean supportRecords) {
        return new SqlTemplateImpl(positionalOnly, expandCollection, supportRecords, aliasResolveStrategy, tableNameResolver, tableAliasResolver, columnNameResolver, foreignKeyResolver);
    }

    @Override
    public boolean supportRecords() {
        return supportRecords;
    }

    @Override
    public SqlTemplate withAliasResolveStrategy(AliasResolveStrategy strategy) {
        return new SqlTemplateImpl(positionalOnly, expandCollection, supportRecords, strategy, tableNameResolver, tableAliasResolver, columnNameResolver, foreignKeyResolver);
    }

    @Override
    public AliasResolveStrategy aliasResolveStrategy() {
        return aliasResolveStrategy;
    }

    static class BindVarsImpl implements BindVars, BindVariables {
        private final List<Function<Record, List<PositionalParameter>>> parameterExtractors;
        private BatchListener batchListener;

        public BindVarsImpl() {
            this.parameterExtractors = new ArrayList<>();
        }

        @Override
        public BindVarsHandle getHandle() {
            return record -> {
                if (batchListener == null) {
                    throw new IllegalStateException("Batch listener not set.");
                }
                if (parameterExtractors.isEmpty()) {
                    throw new IllegalStateException("No parameter extractors not set.");
                }
                var positionalParameters = parameterExtractors.stream()
                        .flatMap(pe -> pe.apply(record).stream())
                        .toList();
                batchListener.onBatch(positionalParameters);
            };
        }

        @Override
        public void setBatchListener(@Nonnull BatchListener batchListener) {
            this.batchListener = batchListener;
        }

        public void addParameterExtractor(@Nonnull Function<Record, List<PositionalParameter>> parameterExtractor) {
            parameterExtractors.add(parameterExtractor);
        }

        @Override
        public String toString() {
            return STR."\{getClass().getSimpleName()}@\{toHexString(System.identityHashCode(this))}";
        }
    }

    @Override
    public BindVars createBindVars() {
        return new BindVarsImpl();
    }

    private Element resolveBindVarsElement(@Nonnull SqlMode mode,
                                           @Nonnull List<Element> resolvedElements,
                                           @Nonnull BindVars bindVars) throws SqlTemplateException {
        return switch (mode) {
            case SELECT, DELETE -> w(bindVars);
            case INSERT -> v(bindVars);
            case UPDATE -> {
                if (resolvedElements.stream().noneMatch(Elements.Set.class::isInstance)) {
                    yield set(bindVars);
                }
                yield w(bindVars);
            }
            case UNDEFINED -> throw new SqlTemplateException("BindVars element not supported in undefined sql mode.");
        };
    }

    private Element resolveCollectionElement(@Nonnull SqlMode mode,
                                             @Nonnull Collection<?> collection) throws SqlTemplateException {
        return switch (mode) {
            case SELECT, DELETE -> w(collection);
            case INSERT, UPDATE -> p(collection);
            case UNDEFINED -> throw new SqlTemplateException("Collection element not supported in undefined sql mode.");
        };
    }

    @SuppressWarnings("unchecked")
    private Element resolveStreamElement(@Nonnull SqlMode mode,
                                         @Nonnull Stream<?> stream) throws SqlTemplateException {
        return switch (mode) {
            case SELECT, DELETE -> w(stream);
            case INSERT -> v((Stream<Record>) stream);
            case UPDATE -> throw new SqlTemplateException("Stream element not supported in update sql mode.");
            case UNDEFINED -> throw new SqlTemplateException("Stream element not supported in undefined sql mode.");
        };
    }

    private Element resolveRecordElement(@Nonnull SqlMode mode,
                                         @Nonnull List<Element> resolvedElements,
                                         @Nonnull Record record) throws SqlTemplateException {
        return switch (mode) {
            case SELECT, DELETE -> w(record);
            case INSERT -> v(record);
            case UPDATE -> {
                if (resolvedElements.stream().noneMatch(Elements.Set.class::isInstance)) {
                    yield set(record);
                }
                yield w(record);
            }
            case UNDEFINED -> throw new SqlTemplateException("Record element not supported in undefined sql mode.");
        };
    }

    private Element resolveTypeElement(@Nonnull SqlMode mode,
                                       @Nonnull List<Element> resolvedElements,
                                       @Nonnull List<?> values,
                                       @Nonnull String nextFragment,
                                       @Nonnull Class<? extends Record> recordType) throws SqlTemplateException {
        List<Element> allElements = new ArrayList<>();
        allElements.addAll(resolvedElements.stream()
                .flatMap(e -> e instanceof Wrapped w ? w.elements().stream() : Stream.of(e))
                .map(e -> e instanceof ResolvedEval re ? re.element() : e)
                .toList());
        allElements.addAll(values.stream()
                .filter(v -> v instanceof Element)
                .map(Element.class::cast)
                .toList());
        return switch (mode) {
            case SELECT -> {
                if (!nextFragment.startsWith(".")) {
                    if (allElements.stream().noneMatch(Select.class::isInstance)) {
                        yield select(recordType);
                    }
                    if (allElements.stream().noneMatch(From.class::isInstance)) {
                        yield from((Class<? extends Record>) recordType);
                    }
                }
                yield alias(recordType);
            }
            case INSERT -> {
                if (!nextFragment.startsWith(".")) {
                    if (allElements.stream().noneMatch(Insert.class::isInstance)) {
                        yield insert(recordType);
                    }
                }
                yield alias(recordType);
            }
            case UPDATE -> {
                if (!nextFragment.startsWith(".")) {
                    if (allElements.stream().noneMatch(Update.class::isInstance)) {
                        yield update((Class<? extends Record>) recordType);
                    }
                }
                yield alias(recordType);
            }
            case DELETE -> {
                if (!nextFragment.startsWith(".")) {
                    if (allElements.stream().noneMatch(Delete.class::isInstance)
                            && nextFragment.matches("\\W+FROM\\W+")) {
                        yield delete(recordType);
                    }
                    if (allElements.stream().noneMatch(From.class::isInstance)) {
                        yield from(recordType);
                    }
                }
                yield alias(recordType);
            }
            case UNDEFINED -> throw new SqlTemplateException(STR."Class type value \{recordType.getSimpleName()} not supported in undefined sql mode.");
        };
    }

    private List<Element> resolveElements(@Nonnull SqlMode sqlMode, @Nonnull List<?> values, @Nonnull List<String> fragments) throws SqlTemplateException {
        List<Element> resolvedValues = new ArrayList<>();
        boolean first = false;
        for (int i = 0; i < values.size() ; i++) {
            var v = values.get(i);
            var f = fragments.get(i + 1);
            switch (v) {
                case Select _ when sqlMode != SqlMode.SELECT -> throw new SqlTemplateException("Select element is only allowed for select statements.");
                case Insert _ when sqlMode != SqlMode.INSERT -> throw new SqlTemplateException("Insert element is only allowed for insert statements.");
                case Update _ when sqlMode != SqlMode.UPDATE -> throw new SqlTemplateException("Update element is only allowed for update statements.");
                case Delete _ when sqlMode != SqlMode.DELETE -> throw new SqlTemplateException("Delete element is only allowed for delete statements.");
                case Select it when !supportRecords -> throw new SqlTemplateException(STR."Records are not supported in this configuration: '\{it.table().getSimpleName()}'.");
                case Insert it when !supportRecords -> throw new SqlTemplateException(STR."Records are not supported in this configuration: '\{it.table().getSimpleName()}'.");
                case Update it when !supportRecords -> throw new SqlTemplateException(STR."Records are not supported in this configuration: '\{it.table().getSimpleName()}'.");
                case Delete it when !supportRecords -> throw new SqlTemplateException(STR."Records are not supported in this configuration: '\{it.table().getSimpleName()}'.");
                case Table t when !supportRecords -> throw new SqlTemplateException(STR."Records are not supported in this configuration: '\{t.table().getSimpleName()}'.");
                case Class<?> c when c.isRecord() && !supportRecords -> throw new SqlTemplateException(STR."Records are not supported in this configuration: '\{c.getSimpleName()}'.");
                case Select it -> {
                    if (first) {
                        throw new SqlTemplateException("Only a single Select element is allowed.");
                    }
                    resolvedValues.add(it);
                    first = true;
                }
                case Insert it -> {
                    if (first) {
                        throw new SqlTemplateException("Only a single Insert element is allowed.");
                    }
                    resolvedValues.add(it);
                    first = true;
                }
                case Update it -> {
                    if (first) {
                        throw new SqlTemplateException("Only a single Update element is allowed.");
                    }
                    resolvedValues.add(it);
                    first = true;
                }
                case Delete it -> {
                    if (first) {
                        throw new SqlTemplateException("Only a single Delete element is allowed.");
                    }
                    resolvedValues.add(it);
                    first = true;
                }
                case Expression _ -> throw new SqlTemplateException("Expression element not allowed in this context.");
                case BindVars b -> resolvedValues.add(resolveBindVarsElement(sqlMode, resolvedValues, b));
                case Eval e -> resolvedValues.add(new ResolvedEval(switch (e.object()) {
                    case Element it -> it;
                    case Stream<?> s -> resolveStreamElement(sqlMode, s);
                    case Record r -> resolveRecordElement(sqlMode, resolvedValues, r);
                    case Class<?> c when c.isRecord() -> //noinspection unchecked
                            resolveTypeElement(sqlMode, resolvedValues, values, f, (Class<? extends Record>) c);
                    // Note that the following flow would also support Class<?> c. but we'll keep the Class<?> c case for performance and readability.
                    case Object k when REFLECTION.isSupportedType(k) -> resolveTypeElement(sqlMode, resolvedValues, values, f, REFLECTION.getRecordType(k));
                    default -> param(e.object());
                }));
                case Element e -> resolvedValues.add(e);
                case Stream<?> l -> resolvedValues.add(resolveStreamElement(sqlMode, l));
                case Record r -> resolvedValues.add(resolveRecordElement(sqlMode, resolvedValues, r));
                case Class<?> c when c.isRecord() -> //noinspection unchecked
                        resolvedValues.add(
                        resolveTypeElement(sqlMode, resolvedValues, values, f, (Class<? extends Record>) c));
                case Object k when REFLECTION.isSupportedType(k) -> {
                    // Kotlin does not support Java style string interpolations. For that reason, we should not have
                    // Kotlin classes in this flow.
                    assert false;
                }
                case null, default -> resolvedValues.add(param(v));
            }
        }
        return resolvedValues;
    }

    private AliasMapper getAliasMapper(@Nonnull List<Element> elements, @Nullable TableAliasResolver tableAliasResolver) throws SqlTemplateException {
        assert elements.stream().noneMatch(Wrapped.class::isInstance);
        record TableAlias(Class<? extends Record> table, String path, String alias) {}
        Map<Class<? extends Record>, List<TableAlias>> aliasMap = elements.stream()
                .map(e -> e instanceof ResolvedEval re ? re.element() : e)
                .map(element -> switch(element) {
                    case Table t -> new TableAlias(t.table(), "", t.alias());
                    case From(TableSource t, String alias) -> new TableAlias(t.table(), "", alias);
                    case Join(TableSource t, String alias, _, _) -> new TableAlias(t.table(), "", alias);
                    default -> null;
                })
                .filter(Objects::nonNull)
                .filter(ta -> !ta.alias().isEmpty())
                .collect(groupingBy(TableAlias::table, HashMap::new, toList()));
        class AliasMapperImpl implements AliasMapper {
            @Override
            public String useAlias(@Nonnull Class<? extends Record> table, @Nonnull String alias) throws SqlTemplateException {
                if (aliasMap.getOrDefault(table, List.of()).stream().noneMatch(a -> a.alias().equals(alias))) {
                    throw new SqlTemplateException(STR."Alias \{alias} for table \{table.getSimpleName()} not found.");
                }
                return alias;
            }

            @Override
            public List<String> getAliases(@Nonnull Class<? extends Record> table) {
                var list = aliasMap.get(table);
                if (list == null) {
                    return List.of();
                }
                return list.stream().map(TableAlias::alias).toList();
            }

            @Override
            public String getAlias(@Nonnull Class<? extends Record> table, @Nonnull AliasResolveStrategy aliasResolveStrategy) throws SqlTemplateException {
                var list = getAliases(table);
                if (list.isEmpty()) {
                    return getTableName(table, tableNameResolver);
                }
                // The \{table.getSimpleName()} table has been specified multiple times in the query or in the entity hierarchy.
                if (list.size() > 1) {
                    if (aliasResolveStrategy != AliasResolveStrategy.FIRST) {
                        throw new SqlTemplateException(STR."""
                        Multiple aliases found for \{table.getSimpleName()}:
                        \t\tCause:
                        \t\t - The \{table.getSimpleName()} table has been specified multiple times in the query, or
                        \t\t - The \{table.getSimpleName()} entity is present multiple times the entity hierarchy.
                        \t\tSolution:
                        \t\t - Specify explicit aliases in the query, or
                        \t\t - Mark ancillary \{table.getSimpleName()} relations as Lazy, or
                        \t\t - Remove the ambiguity by creating variant(s) of the \{table.getSimpleName()} entiy, or
                        \t\t - Set the AliasResolveStrategy to FIRST.""");
                    }
                }
                return list.getFirst();
            }

            @Override
            public String generateAlias(@Nonnull Class<? extends Record> table) throws SqlTemplateException {
                String alias = SqlTemplateImpl.this.generateAlias(table, tableAliasResolver,
                        proposedAlias -> aliasMap.values().stream().flatMap(it -> it.stream().map(TableAlias::alias)).noneMatch(proposedAlias::equals));
                aliasMap.computeIfAbsent(table, _ -> new ArrayList<>()).add(new TableAlias(table, "", alias));
                return alias;
            }

            private String toString(@Nonnull List<RecordComponent> path) {
                return path.stream().map(RecordComponent::getName).collect(Collectors.joining("."));
            }

            @Override
            public String getAlias(@Nonnull List<RecordComponent> path) throws SqlTemplateException {
                var component = path.getLast();
                if (!component.getType().isRecord()) {
                    throw new SqlTemplateException(STR."Component \{component.getDeclaringRecord()}.\{component.getName()} is not a record.");
                }
                String p = toString(path);
                var alias = aliasMap.getOrDefault(component.getType(), List.of()).stream()
                        .filter(a -> a.path().equals(p))
                        .map(TableAlias::alias)
                        .findFirst();
                if (alias.isPresent()) {
                    return alias.get();
                }
                //noinspection unchecked
                return getAlias((Class<? extends Record>) component.getType(), aliasResolveStrategy);
            }

            @Override
            public String generateAlias(@Nonnull List<RecordComponent> path) throws SqlTemplateException {
                var component = path.getLast();
                if (!component.getType().isRecord()) {
                    throw new SqlTemplateException(STR."Component \{component.getDeclaringRecord()}.\{component.getName()} is not a record.");
                }
                String p = toString(path);
                //noinspection unchecked
                Class<? extends Record> table = (Class<? extends Record>) component.getType();
                String alias = SqlTemplateImpl.this.generateAlias(table, tableAliasResolver,
                        proposedAlias -> aliasMap.values().stream().flatMap(it -> it.stream().map(TableAlias::alias)).noneMatch(proposedAlias::equals));
                aliasMap.computeIfAbsent(table, _ -> new ArrayList<>()).add(new TableAlias(table, p, alias));
                return alias;
            }
        }
        return new AliasMapperImpl();
    }

    private TableMapper getTableMapper() {
        class TableMapperImpl implements TableMapper {
            private final Map<Class<? extends Record>, List<Mapping>> mappings;

            TableMapperImpl() {
                this.mappings = new HashMap<>();
            }

            @Override
            public List<Mapping> getMappings(@Nonnull Class<? extends Record> table) {
                return mappings.getOrDefault(table, List.of());
            }

            @Override
            public void mapPrimaryKey(@Nonnull Class<? extends Record> table, @Nonnull String alias, @Nonnull List<RecordComponent> components) {
                mappings.computeIfAbsent(table, _ -> new ArrayList<>())
                        .add(new Mapping(alias, components, true));
            }

            @Override
            public void mapForeignKey(@Nonnull Class<? extends Record> table, @Nonnull String alias, @Nonnull RecordComponent component) {
                mappings.computeIfAbsent(table, _ -> new ArrayList<>())
                        .add(new Mapping(alias, List.of(component), false));
            }
        }
        return new TableMapperImpl();
    }

    /**
     * Updates {@code elements} to include joins and aliases for the table in the FROM clause.
     *
     * @param elements all elements in the sql statement.
     */
    private void postProcessSelect(@Nonnull List<Element> elements, @Nonnull AliasMapper aliasMapper, @Nonnull TableMapper tableMapper) throws SqlTemplateException {
        final From from = elements.stream()
                .map(e -> e instanceof ResolvedEval re ? re.element() : e)
                .filter(From.class::isInstance)
                .map(From.class::cast)
                .filter(f -> f.source() instanceof TableSource)
                .findAny()
                .orElse(null);
        final Optional<Class<? extends Record>> primaryTable;
        final From adjustedFrom;
        if (from != null) {
            Class<? extends Record> table = ((TableSource) from.source()).table();
            if (from.alias().isEmpty()) {
                // Replace From element by from element with alias.
                String alias = aliasMapper.generateAlias(table);
                adjustedFrom = new From(new TableSource(table), alias);
                elements.replaceAll(element -> element instanceof From ? adjustedFrom : element);
                tableMapper.mapPrimaryKey(table, alias, getPkComponents(table).toList());
            } else {
                adjustedFrom = null;
            }
            primaryTable = Optional.of(table);
        } else {
            primaryTable = empty();
            adjustedFrom = null;
        }
        List<Join> customJoins = new ArrayList<>();
        for (ListIterator<Element> it = elements.listIterator(); it.hasNext(); ) {
            Element element = it.next();
            if (element instanceof Table t && t.alias().isEmpty()) {
                String alias = aliasMapper.generateAlias(t.table());
                it.set(new Table(t.table(), alias));
                primaryTable.ifPresent(pt -> tableMapper.mapPrimaryKey(pt, alias, getPkComponents(pt).toList()));
            } else if (element instanceof Join j) {
                // Move custom join to list of (expanded) joins to allow proper ordering of inner and outer joins.
                if (j instanceof Join(TableSource ts, _, _, _)) {
                    String alias;
                    if (j.alias().isEmpty()) {
                        alias = aliasMapper.generateAlias(ts.table());
                        customJoins.add(new Join(j.source(), alias, j.on(), j.type()));
                        tableMapper.mapPrimaryKey(ts.table(), alias, getPkComponents(ts.table()).toList());
                    } else {
                        alias = j.alias();
                        aliasMapper.useAlias(ts.table(), j.alias());
                        customJoins.add(j);
                    }
                    // Make the FKs of the join also available for mapping.
                    mapForeignKeys(tableMapper, alias, ts.table());
                } else {
                    customJoins.add(j);
                }
                it.set(new Unsafe("")); // Replace by empty string to keep fragments and values in sync.
            }
        }
        if (primaryTable.isPresent()) {
            List<Join> expandedJoins = new ArrayList<>();
            expandJoins(primaryTable.get(), customJoins, aliasMapper, tableMapper, expandedJoins);
            if (!expandedJoins.isEmpty()) {
                List<Element> replacementElements = new ArrayList<>();
                replacementElements.add(adjustedFrom == null ? from : adjustedFrom);
                Select select = elements.stream()
                        .map(e -> e instanceof ResolvedEval re ? re.element() : e)
                        .filter(Select.class::isInstance)
                        .map(Select.class::cast)
                        .findAny()
                        .orElse(null);
                if (select == null) {
                    replacementElements.addAll(expandedJoins);
                } else {
                    for (var join : expandedJoins) {
                        if (join instanceof Join(
                                TableSource(var joinTable), _, _, _
                        ) && joinTable == select.table() && join.type().isOuter()) {
                            // If join is part of the select table and is an outer join, replace it with an inner join.
                            replacementElements.add(new Join(new TableSource(joinTable), join.alias(), join.on(), DefaultJoinType.INNER));
                        } else {
                            replacementElements.add(join);
                        }
                    }
                }
                elements.replaceAll(element -> element instanceof From
                        ? new Wrapped(replacementElements)
                        : element instanceof ResolvedEval(From _)
                        ? new ResolvedEval(new Wrapped(replacementElements))
                        : element);
            }
        }
        if (elements.stream().filter(Where.class::isInstance).count() > 1) {
            throw new SqlTemplateException("Multiple Where elements found.");
        }
    }

    /**
     * Updates {@code elements} to handle include aliases for the table in the UPDATE clause.
     *
     * @param elements all elements in the sql statement.
     */
    private void postProcessUpdate(@Nonnull List<Element> elements, @Nonnull AliasMapper aliasMapper, @Nonnull TableMapper tableMapper) throws SqlTemplateException {
        final Update update = elements.stream()
                .map(e -> e instanceof ResolvedEval re ? re.element() : e)
                .filter(Update.class::isInstance)
                .map(Update.class::cast)
                .findAny()
                .orElse(null);
        if (update != null) {
            Update effectiveUpdate;
            if (update.alias().isEmpty()) {
                String alias = aliasMapper.generateAlias(update.table());
                effectiveUpdate = new Update(update.table(), alias);
            } else {
                effectiveUpdate = update;
            }
            // Make the FKs of the entity also available for mapping.
            mapForeignKeys(tableMapper, effectiveUpdate.alias(), effectiveUpdate.table());
        }
    }

    /**
     * Updates {@code elements} to handle aliases for the table in the DELETE clause.
     *
     * @param elements all elements in the sql statement.
     */
    private void postProcessDelete(@Nonnull List<Element> elements, @Nonnull AliasMapper aliasMapper, @Nonnull TableMapper tableMapper) throws SqlTemplateException {
        final Delete delete = elements.stream()
                .map(e -> e instanceof ResolvedEval re ? re.element() : e)
                .filter(Delete.class::isInstance)
                .map(Delete.class::cast)
                .findAny()
                .orElse(null);
        final From from = elements.stream()
                .map(e -> e instanceof ResolvedEval re ? re.element() : e)
                .filter(From.class::isInstance)
                .map(From.class::cast)
                .filter(f -> f.source() instanceof TableSource)
                .findAny()
                .orElse(null);
        if (from != null) {
            Class<? extends Record> table = ((TableSource) from.source()).table();
            String alias;
            From effectiveFrom;
            if (from.alias().isEmpty()) {
                alias = delete == null ? "" :  aliasMapper.generateAlias(table);
                effectiveFrom = new From(new TableSource(table), alias);
                elements.replaceAll(element -> element instanceof From ? effectiveFrom : element);
                tableMapper.mapPrimaryKey(table, alias, getPkComponents(table).toList());
            } else {
                effectiveFrom = from;
                alias = from.alias();
            }
            if (delete != null) {
                if (delete.table() != table) {
                    throw new SqlTemplateException(STR."Delete entity \{delete.table().getSimpleName()} does not match From table \{table.getSimpleName()}.");
                }
                if (delete.alias().isEmpty()) {
                    if (!effectiveFrom.alias().isEmpty()) {
                        elements.replaceAll(element -> element instanceof Delete
                                ? d(table, alias)
                                : element);
                    }
                }
            }
            if (effectiveFrom.source() instanceof TableSource ts) {
                // Make the FKs of the entity also available for mapping.
                mapForeignKeys(tableMapper, alias, ts.table());
            }
        } else if (delete != null) {
            throw new SqlTemplateException("From element required when using Delete element.");
        }
    }

    private void mapForeignKeys(@Nonnull TableMapper tableMapper, @Nonnull String alias, @Nonnull Class<? extends Record> table)
            throws SqlTemplateException {
        for (var component : table.getRecordComponents()) {
            if (REFLECTION.isAnnotationPresent(component, FK.class)) {
                if (Lazy.class.isAssignableFrom(component.getType())) {
                    tableMapper.mapForeignKey(getLazyRecordType(component), alias, component);
                } else {
                    if (!component.getType().isRecord()) {
                        throw new SqlTemplateException(STR."FK annotation is only allowed on record types: \{component.getType().getSimpleName()}.");
                    }
                    //noinspection unchecked
                    Class<? extends Record> componentType = (Class<? extends Record>) component.getType();
                    tableMapper.mapForeignKey(componentType, alias, component);
                }
            }
        }
    }

    private void expandJoins(@Nonnull Class<? extends Record> recordType,
                             @Nonnull List<Join> joins,
                             @Nonnull AliasMapper aliasMapper,
                             @Nonnull TableMapper tableMapper,
                             @Nonnull List<Join> expandedJoins) throws SqlTemplateException {
        expandJoins(recordType, List.of(), aliasMapper, tableMapper, expandedJoins, null, false);
        expandedJoins.addAll(joins);
        // Move outer joins to the end of the list to ensure proper filtering across multiple databases.
        expandedJoins.sort(comparing(join -> join.type().isOuter()));
    }

    @SuppressWarnings("unchecked")
    private void expandJoins(@Nonnull Class<? extends Record> recordType,
                             @Nonnull List<RecordComponent> path,
                             @Nonnull AliasMapper aliasMapper,
                             @Nonnull TableMapper tableMapper,
                             @Nonnull List<Join> expandedJoins,
                             @Nullable String fkName,
                             boolean outerJoin) throws SqlTemplateException {
        for (var component : recordType.getRecordComponents()) {
            var list = new ArrayList<>(path);
            list.add(component);
            var copy = copyOf(list);
            if (REFLECTION.isAnnotationPresent(component, FK.class)) {
                if (Lazy.class.isAssignableFrom(component.getType())) {
                    // No join needed for lazy components, but we will map the table, so we can query the lazy component.
                    String fromAlias;
                    if (fkName == null) {
                        fromAlias = aliasMapper.getAlias(recordType, aliasResolveStrategy);
                    } else {
                        fromAlias = fkName;
                    }
                    tableMapper.mapForeignKey(getLazyRecordType(component), fromAlias, component);
                    continue;
                }
                if (!component.getType().isRecord()) {
                    throw new SqlTemplateException(STR."FK annotation is only allowed on record types: \{component.getType().getSimpleName()}.");
                }
                Class<? extends Record> componentType = (Class<? extends Record>) component.getType();
                // We may detect that the component is already by present by checking
                // aliasMap.containsKey(componentType), but we'll handle duplicate joins later to detect such issues
                // in a unified way (auto join vs manual join).
                String fromAlias;
                if (fkName == null) {
                    fromAlias = aliasMapper.getAlias(recordType, aliasResolveStrategy);
                } else {
                    fromAlias = fkName;
                }
                String alias = aliasMapper.generateAlias(copy);
                tableMapper.mapForeignKey(componentType, fromAlias, component);
                var pkComponent = getPkComponents(componentType).findFirst()    // We only support single primary keys for FK fields.
                        .orElseThrow(() -> new SqlTemplateException(STR."No primary key found for entity \{componentType.getSimpleName()}."));
                String pkColumnName = getColumnName(pkComponent, columnNameResolver);
                tableMapper.mapPrimaryKey(componentType, alias, List.of(pkComponent));
                outerJoin = outerJoin || !REFLECTION.isNonnull(component);
                JoinType joinType = outerJoin ? DefaultJoinType.LEFT : DefaultJoinType.INNER;
                expandedJoins.add(new Join(new TableSource(componentType), alias, RAW."\{new Unsafe(STR."\{fromAlias}.\{getForeignKey(component, foreignKeyResolver)} = \{alias}.\{pkColumnName}")}", joinType));
                expandJoins(componentType, copy, aliasMapper, tableMapper, expandedJoins, alias, outerJoin);
            } else if (REFLECTION.isAnnotationPresent(component, Inline.class)) {
                if (!component.getType().isRecord()) {
                    throw new SqlTemplateException(STR."Inlined annotation is only allowed on record types: \{component.getType().getSimpleName()}.");
                }
                Class<? extends Record> componentType = (Class<? extends Record>) component.getType();
                String fromAlias;
                if (fkName == null) {
                    fromAlias = aliasMapper.getAlias(recordType, aliasResolveStrategy);
                } else {
                    fromAlias = fkName;
                }
                expandJoins(componentType, copy, aliasMapper, tableMapper, expandedJoins, fromAlias, outerJoin);
            }
        }
    }

    @SuppressWarnings("unchecked")
    static Stream<RecordComponent> getPkComponents(@Nonnull Class<? extends Record> componentType) {
        return Stream.of(componentType.getRecordComponents())
                .flatMap(c -> REFLECTION.isAnnotationPresent(c, PK.class)
                        ? Stream.of(c)
                        : REFLECTION.isAnnotationPresent(c, Inline.class) && c.getType().isRecord()
                                ? getPkComponents((Class<? extends Record>) c.getType())
                                : Stream.empty());
    }

    @SuppressWarnings("unchecked")
    static Stream<RecordComponent> getFkComponents(@Nonnull Class<? extends Record> componentType) {
        return Stream.of(componentType.getRecordComponents())
                .flatMap(c -> REFLECTION.isAnnotationPresent(c, FK.class)
                        ? Stream.of(c)
                        : (REFLECTION.isAnnotationPresent(c, PK.class) || REFLECTION.isAnnotationPresent(c, Inline.class)) && c.getType().isRecord()
                                ? getFkComponents((Class<? extends Record>) c.getType())
                                : Stream.empty());
    }

    static Optional<RecordComponent> findComponent(@Nonnull List<RecordComponent> components, @Nonnull Class<? extends Record> recordType) throws SqlTemplateException {
        for (var component : components) {
            if (component.getType().equals(recordType)
                    || (Lazy.class.isAssignableFrom(component.getType()) && getLazyRecordType(component).equals(recordType))) {
                return Optional.of(component);
            }
        }
        return empty();
    }

    /**
     * Returns the primary table and its alias in the sql statement, such as the table in the FROM clause for a SELECT
     * or DELETE, or the table in the INSERT or UPDATE clause.
     *
     * @param elements all elements in the sql statement.
     * @param aliasMapper a mapper of table classes to their aliases.
     * @return the primary table for the sql statement.
     */
    private Optional<Table> getPrimaryTable(@Nonnull List<Element> elements, @Nonnull AliasMapper aliasMapper) {
        assert elements.stream().noneMatch(Wrapped.class::isInstance);
        assert elements.stream().noneMatch(ResolvedEval.class::isInstance);
        Table table = elements.stream()
                .filter(From.class::isInstance)
                .map(From.class::cast)
                .map(f -> {
                    if (f.source() instanceof TableSource ts) {
                        return new Table(ts.table(), f.alias());
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .findAny()
                .orElse(null);
        if (table != null) {
            return Optional.of(table);
        }
        table = elements.stream()
                .map(e -> e instanceof ResolvedEval re ? re.element() : e)
                .map(element -> switch(element) {
                    case Insert it -> new Table(it.table(), "");
                    case Update it -> new Table(it.table(), it.alias());
                    case Delete it -> new Table(it.table(), "");
                    default -> null;
                })
                .filter(Objects::nonNull)
                .findAny()
                .orElse(null);
        if (table != null) {
            return Optional.of(table);
        }
        return elements.stream()
                .map(e -> e instanceof ResolvedEval re ? re.element() : e)
                .filter(Select.class::isInstance)
                .map(Select.class::cast)
                .findAny()
                .map(select -> new Table(select.table(), aliasMapper.resolveAlias(select.table(), aliasResolveStrategy).orElse("")));
    }

    private String generateAlias(@Nonnull Class<? extends Record> table,
                                 @Nullable TableAliasResolver tableAliasResolver,
                                 @Nonnull Predicate<String> tester) throws SqlTemplateException {
        String alias;
        if (tableAliasResolver == null) {
            // Extract the capitals from the class name to form the base alias.
            String className = table.getSimpleName();
            StringBuilder aliasBuilder = new StringBuilder("_");    // Use underscore as prefix to avoid clashes with client aliases.
            for (char ch : className.toCharArray()) {
                if (isUpperCase(ch)) {
                    aliasBuilder.append(toLowerCase(ch));
                }
            }
            String baseAlias = aliasBuilder.toString();
            alias = baseAlias;
            // Check if the base alias passes the tester predicate. If not, append a number and check again.
            int counter = 1;
            while (!tester.test(alias)) {
                alias = baseAlias + counter;
                counter++;
            }
        } else {
            int counter = 0;
            var aliases = new HashSet<>();
            do {
                alias = tableAliasResolver.resolveTableAlias(table, counter++);
                if (alias.isEmpty()) {
                    break;
                }
                if (!aliases.add(alias)) {
                    throw new SqlTemplateException(STR."Table alias returns the same alias \{alias} multiple times.");
                }
            } while (!tester.test(alias));
        }
        if (alias.isEmpty()) {
            throw new SqlTemplateException(STR."Table alias for \{table.getSimpleName()} is empty.");
        }
        return alias;
    }

    private void postProcessElements(@Nonnull SqlMode sqlMode, @Nonnull List<Element> resolvedValues, @Nonnull AliasMapper aliasMapper, @Nonnull TableMapper tableMapper) throws SqlTemplateException {
        switch (sqlMode) {
            case SELECT -> postProcessSelect(resolvedValues, aliasMapper, tableMapper);
            case UPDATE -> postProcessUpdate(resolvedValues, aliasMapper, tableMapper);
            case DELETE -> postProcessDelete(resolvedValues, aliasMapper, tableMapper);
        }
        assert resolvedValues.stream().noneMatch(Join.class::isInstance);
    }


    /**
     * Processes the specified {@code stringTemplate} and returns the resulting SQL and parameters.
     *
     * @param stringTemplate the string template to process.
     * @return the resulting SQL and parameters.
     * @throws SqlTemplateException if an error occurs while processing the input.
     */
    @Override
    public Sql process(StringTemplate stringTemplate) throws SqlTemplateException {
        return process(stringTemplate, false);
    }

    /**
     * Processes the specified {@code stringTemplate} and returns the resulting SQL and parameters.
     *
     * @param stringTemplate the string template to process.
     * @param nested whether to the call is recursive.
     * @return the resulting SQL and parameters.
     * @throws SqlTemplateException if an error occurs while processing the input.
     */
    public Sql process(StringTemplate stringTemplate, boolean nested) throws SqlTemplateException {
        var fragments = stringTemplate.fragments();
        var values = stringTemplate.values();
        assert fragments.size() == values.size() + 1;
        var sqlMode = getSqlMode(stringTemplate);
        var elements = resolveElements(sqlMode, values, fragments);
        var aliasMapper = getAliasMapper(elements, tableAliasResolver);
        var tableMapper = getTableMapper();
        postProcessElements(sqlMode, elements, aliasMapper, tableMapper);
        var unwrappedElements = elements.stream()
                .map(e -> e instanceof ResolvedEval re ? re.element() : e)  // First unpack resolved eval.
                .flatMap(e -> e instanceof Wrapped c ? c.elements().stream() : Stream.of(e))
                .toList();
        assert values.size() == elements.size();
        Optional<Table> primaryTable = getPrimaryTable(unwrappedElements, aliasMapper);
        if (primaryTable.isPresent()) {
            validateRecordType(sqlMode, primaryTable.get().table());
        }
        List<String> parts = new ArrayList<>();
        List<Parameter> parameters = new ArrayList<>();
        AtomicReference<BindVariables> bindVariables = new AtomicReference<>();
        List<String> generatedKeys = new ArrayList<>();
        AtomicBoolean versionAware = new AtomicBoolean();
        AtomicInteger parameterPosition = new AtomicInteger(1);
        AtomicInteger nameIndex = new AtomicInteger();
        StringBuilder rawSql = new StringBuilder();
        List<String> args = new ArrayList<>();
        for (int i = 0; i < fragments.size(); i++) {
            String fragment = fragments.get(i);
            try {
                if (i < values.size()) {
                    Element e = elements.get(i);
                    var result = new ElementProcessor(
                            this,
                            e,
                            parameters,
                            parameterPosition,
                            nameIndex,
                            aliasMapper,
                            tableMapper,
                            bindVariables,
                            generatedKeys,
                            versionAware,
                            primaryTable.orElse(null)
                    ).process().orElse(null);
                    if (result != null) {
                        String sql = result.sql();
                        if (!result.args().isEmpty()) {
                            args.addAll(result.args());
                        }
                        if (!sql.isEmpty() && !args.isEmpty()) {
                            sql = sql.formatted(args.toArray());
                            args.clear();
                        }
                        rawSql.append(fragment);
                        rawSql.append(sql);
                        if (e instanceof Param) {
                            parts.add(rawSql.toString());
                            rawSql.setLength(0);
                        }
                    } else {
                        rawSql.append(fragment);
                    }
                } else {
                    rawSql.append(fragment);
                    if (!args.isEmpty()) {
                        parts.add(rawSql.toString().formatted(args.toArray()));
                    } else {
                        parts.add(rawSql.toString());
                    }
                }
            } catch (MissingFormatArgumentException ex) {
                throw new SqlTemplateException("Invalid number of argument placeholders found. Template appears to specify custom %s placeholders.", ex);
            }
        }
        validateNamedParameters(parameters);
        Sql sql = new SqlImpl(
                String.join("", parts),
                parameters,
                ofNullable(bindVariables.get()),
                copyOf(generatedKeys),
                versionAware.getPlain());
        if (!nested) {
            CONSUMERS.get().forEach(c -> c.accept(sql));
        }
        return sql;
    }

    record ValidationKey(@Nonnull SqlMode sqlMode, Class<? extends Record> recordType) {}
    private static final Map<ValidationKey, String> VALIDATION_CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private void validateRecordType(@Nonnull SqlMode sqlMode,
                                    @Nonnull Class<? extends Record> recordType) throws SqlTemplateException {
        String message = VALIDATION_CACHE.computeIfAbsent(new ValidationKey(sqlMode, recordType), _ -> {
            // Note that this result can be cached as we're inspecting types.
            var pkComponents = getPkComponents(recordType).toList();
            if (pkComponents.isEmpty()) {
                if (sqlMode != SqlMode.SELECT && sqlMode != SqlMode.UNDEFINED) {
                    return STR."No primary key found for entity \{recordType.getSimpleName()}.";
                }
            }
            for (var pkComponent : pkComponents) {
                if (Lazy.class.isAssignableFrom(pkComponent.getType())) {
                    return STR."Primary key must not be lazy: \{recordType.getSimpleName()}.";
                }
            }
            if (pkComponents.size() > 1) {
                return STR."Multiple primary keys found for entity \{recordType.getSimpleName()}.";
            }
            for (var fkComponent : getFkComponents(recordType).toList()) {
                if (fkComponent.getType().isRecord()) {
                    if (getPkComponents((Class<? extends Record>) fkComponent.getType()).anyMatch(pk -> pk.getType().isRecord())) {
                        return STR."Foreign key must not specify a compound primary key: \{fkComponent.getType().getSimpleName()}.";
                    }
                }
            }
            return "";
        });
        if (!message.isEmpty()) {
            throw new SqlTemplateException(message);
        }
    }

    /**
     * Validates that named parameters are not being used multiple times with varying values.
     *
     * @param parameters the parameters to validate.
     * @throws SqlTemplateException if a named parameter is being used multiple times with varying values.
     */
    private void validateNamedParameters(List<Parameter> parameters) throws SqlTemplateException {
        var namedParameters = parameters.stream()
                .filter(NamedParameter.class::isInstance)
                .map(NamedParameter.class::cast)
                .collect(Collectors.<NamedParameter, String>groupingBy(NamedParameter::name));
        for (var entry : namedParameters.entrySet()) {
            var list = entry.getValue();
            if (list.size() > 1) {
                Object first = null;
                for (var value : list) {
                    var v = value.dbValue();
                    if (first == null) {
                        first = v;
                    } else {
                        if (!first.equals(v)) {
                            throw new SqlTemplateException(STR."Named parameter '\{value.name()}' is being used multiple times with varying values.");
                        }
                    }
                }
            }
        }
    }

    // Use RecordComponentKey as key as multiple new instances of the same RecordComponent are created, which return
    // false for equals and hashCode.
    record RecordComponentKey(Class<? extends Record> recordType, String name) {
        RecordComponentKey(@Nonnull RecordComponent component) {
            //noinspection unchecked
            this((Class<? extends Record>) component.getDeclaringRecord(), component.getName());
        }
    }
    private static final Map<RecordComponentKey, Class<?>> LAZY_PK_TYPE_CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    static Class<?> getLazyPkType(@Nonnull RecordComponent component) throws SqlTemplateException {
        try {
            return LAZY_PK_TYPE_CACHE.computeIfAbsent(new RecordComponentKey(component), _ -> {
                try {
                    var type = component.getGenericType();
                    if (type instanceof ParameterizedType parameterizedType) {
                        Type supplied = parameterizedType.getActualTypeArguments()[0];
                        if (supplied instanceof Class<?> c && c.isRecord()) {
                            return REFLECTION.findPKType((Class<? extends Record>) c)
                                    .orElseThrow(() -> new SqlTemplateException(STR."Primary key not found for entity: \{c.getSimpleName()}."));
                        }
                    }
                    throw new SqlTemplateException(STR."Lazy component must specify an entity: \{component.getType().getSimpleName()}.");
                } catch (SqlTemplateException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            throw (SqlTemplateException) e.getCause();
        }
    }

    private static final Map<RecordComponentKey, Class<? extends Record>> LAZY_RECORD_TYPE_CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    static Class<? extends Record> getLazyRecordType(@Nonnull RecordComponent component) throws SqlTemplateException {
        try {
            return LAZY_RECORD_TYPE_CACHE.computeIfAbsent(new RecordComponentKey(component), _ -> {
                try {
                    Class<? extends Record> recordType = null;
                    var type = component.getGenericType();
                    if (type instanceof ParameterizedType parameterizedType) {
                        Type supplied = parameterizedType.getActualTypeArguments()[0];
                        if (supplied instanceof Class<?> c && c.isRecord()) {
                            recordType = (Class<? extends Record>) c;
                        }
                    }
                    if (!Entity.class.isAssignableFrom(component.getType()) && recordType == null) {
                        throw new SqlTemplateException(STR."Lazy component must specify an entity: \{component.getType().getSimpleName()}.");
                    }
                    if (recordType == null) {
                        throw new SqlTemplateException(STR."Lazy component must be a record: \{component.getType().getSimpleName()}.");
                    }
                    return recordType;
                } catch (SqlTemplateException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            throw (SqlTemplateException) e.getCause();
        }
    }

    /**
     * Returns the table name for the specified record type taking the table name resolver into account, if present.
     *
     * @param recordType the record type to obtain the table name for.
     * @param tableNameResolver the table name resolver.
     * @return the table name for the specified record type.
     */
    static String getTableName(@Nonnull Class<? extends Record> recordType, @Nullable TableNameResolver tableNameResolver) {
        return ofNullable(REFLECTION.getAnnotation(recordType, Name.class))
                .map(Name::value)
                .filter(not(String::isEmpty))
                .orElse(tableNameResolver != null
                        ? tableNameResolver.resolveTableName(recordType)
                        : recordType.getSimpleName());
    }

    /**
     * Returns the column name for the specified record component taking the column name resolver into account,
     * if present.
     *
     * @param component the record component to obtain the column name for.
     * @param columnNameResolver the column name resolver.
     * @return the column name for the specified record component.
     */
    static String getColumnName(@Nonnull RecordComponent component, @Nullable ColumnNameResolver columnNameResolver) {
        return ofNullable(REFLECTION.getAnnotation(component, Name.class))
                .map(Name::value)
                .filter(not(String::isEmpty))
                .orElse(columnNameResolver != null
                        ? columnNameResolver.resolveColumnName(component)
                        : component.getName());
    }

    /**
     * Returns the column name for the specified record component taking the column name resolver into account,
     * if present.
     *
     * @param component the record component to obtain the foreign key column name for.
     * @param foreignKeyResolver the foreign key resolver.
     * @return the column name for the specified record component.
     */
    @SuppressWarnings("unchecked")
    static String getForeignKey(@Nonnull RecordComponent component, @Nullable ForeignKeyResolver foreignKeyResolver) throws SqlTemplateException {
        var foreignKey = ofNullable(REFLECTION.getAnnotation(component, Name.class))
                .map(Name::value)
                .filter(not(String::isEmpty));
        if (foreignKey.isPresent()) {
            return foreignKey.get();
        }
        Class<? extends Record> recordType = Lazy.class.isAssignableFrom(component.getType())
                ? getLazyRecordType(component)
                : (Class<? extends Record>) component.getType();
        if (foreignKeyResolver != null) {
            return foreignKeyResolver.resolveColumnName(recordType);
        }
        throw new SqlTemplateException(STR."Cannot infer foreign key column name for entity \{component.getType().getSimpleName()}. Specify a @Named annotation or provide a foreign key resolver.");
    }

    // Basic SQL processing.

    private static final Pattern WITH_PATTERN = Pattern.compile("^(?i:WITH)\\W.*", DOTALL);
    private static final Map<Pattern, SqlMode> SQL_MODES = Map.of(
            Pattern.compile("^(?i:SELECT)\\W.*", DOTALL), SqlMode.SELECT,
            Pattern.compile("^(?i:INSERT)\\W.*", DOTALL), SqlMode.INSERT,
            Pattern.compile("^(?i:UPDATE)\\W.*", DOTALL), SqlMode.UPDATE,
            Pattern.compile("^(?i:DELETE)\\W.*", DOTALL), SqlMode.DELETE
    );

    /**
     * Determines the SQL mode for the specified {@code stringTemplate}.
     *
     * @param stringTemplate The string template.
     * @return the SQL mode.
     */
    private static SqlMode getSqlMode(@Nonnull StringTemplate stringTemplate) {
        String first = stringTemplate.fragments().getFirst().stripLeading();
        if (first.isEmpty()) {
            first = stringTemplate.values().stream()
                    .findFirst()
                    .filter(Unsafe.class::isInstance)
                    .map(Unsafe.class::cast)
                    .map(Unsafe::sql)
                    .orElse("");
        }
        String input = removeComments(first).stripLeading();
        if (WITH_PATTERN.matcher(input).matches()) {
            input = removeWithClause(removeComments(String.join("", stringTemplate.fragments())));
        }
        String sql = input.stripLeading();
        return SQL_MODES.entrySet().stream()
                .filter(e -> e.getKey().matcher(sql).matches())
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(SqlMode.UNDEFINED);
    }

    private static String removeWithClause(String sql) {
        assert sql.trim().toUpperCase().startsWith("WITH");
        int depth = 0; // Depth of nested parentheses.
        boolean inSingleQuotes = false; // Track whether inside single quotes.
        boolean inDoubleQuotes = false; // Track whether inside double quotes.
        int startIndex = sql.indexOf('('); // Find the first opening parenthesis.
        // If there's no opening parenthesis after "WITH", return the original string.
        if (startIndex == -1) {
            return sql;
        }
        for (int i = startIndex; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            // Toggle state for single quotes.
            if (ch == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            }
            // Toggle state for double quotes.
            else if (ch == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            }
            // Count parentheses depth if not within quotes.
            if (!inSingleQuotes && !inDoubleQuotes) {
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                    if (depth == 0) {
                        // Found the matching closing parenthesis for the first opening parenthesis.
                        String afterWithClause = sql.substring(i + 1).trim();
                        // Check if it needs to remove a comma right after the closing parenthesis of WITH clause.
                        if (afterWithClause.startsWith(",")) {
                            afterWithClause = afterWithClause.substring(1).trim();
                        }
                        return afterWithClause;
                    }
                }
            }
        }
        // If depth never reaches 0, return the original string as it might be malformed or the logic above didn't correctly parse it.
        return sql;
    }

    /**
     * Removes both single-line and multi-line comments from a SQL string.
     *
     * @param sql The original SQL string.
     * @return The SQL string with comments removed.
     */
    private static String removeComments(@Nonnull String sql) {
        // Pattern for multi-line comments.
        String multiLineCommentRegex = "(?s)/\\*.*?\\*/";
        // Pattern for single-line comments (both -- and #).
        String singleLineCommentRegex = "(--|#).*?(\\n|$)";
        // Remove multi-line comments, then single-line comments.
        return sql.replaceAll(multiLineCommentRegex, "")
                .replaceAll(singleLineCommentRegex, "")
                .stripLeading();
    }
}
