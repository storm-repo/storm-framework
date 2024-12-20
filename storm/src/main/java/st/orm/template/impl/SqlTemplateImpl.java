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
import st.orm.Query;
import st.orm.repository.Entity;
import st.orm.repository.Projection;
import st.orm.repository.ProjectionQuery;
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
import st.orm.template.impl.Elements.Alias;
import st.orm.template.impl.Elements.Delete;
import st.orm.template.impl.Elements.Expression;
import st.orm.template.impl.Elements.From;
import st.orm.template.impl.Elements.Insert;
import st.orm.template.impl.Elements.Param;
import st.orm.template.impl.Elements.Select;
import st.orm.template.impl.Elements.Source;
import st.orm.template.impl.Elements.Subquery;
import st.orm.template.impl.Elements.Table;
import st.orm.template.impl.Elements.TableSource;
import st.orm.template.impl.Elements.Target;
import st.orm.template.impl.Elements.TemplateSource;
import st.orm.template.impl.Elements.TemplateTarget;
import st.orm.template.impl.Elements.Unsafe;
import st.orm.template.impl.Elements.Update;
import st.orm.template.impl.Elements.Where;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.MissingFormatArgumentException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.Long.toHexString;
import static java.lang.System.identityHashCode;
import static java.util.Comparator.comparing;
import static java.util.List.copyOf;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static java.util.regex.Pattern.DOTALL;
import static st.orm.Templates.delete;
import static st.orm.Templates.from;
import static st.orm.Templates.insert;
import static st.orm.Templates.param;
import static st.orm.Templates.select;
import static st.orm.Templates.set;
import static st.orm.Templates.table;
import static st.orm.Templates.update;
import static st.orm.Templates.values;
import static st.orm.Templates.where;
import static st.orm.spi.Providers.getORMConverter;
import static st.orm.template.ResolveScope.CASCADE;
import static st.orm.template.ResolveScope.INNER;

/**
 *
 */
public final class SqlTemplateImpl implements SqlTemplate {

    private static final Logger LOGGER = Logger.getLogger("st.orm.sql");
    private static final ORMReflection REFLECTION = Providers.getORMReflection();

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

    record Wrapped(@Nonnull List<? extends Element> elements) implements Element {
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

    record Join(@Nonnull Source source, @Nonnull String alias, @Nonnull Target target, @Nonnull JoinType type) implements Element {
        public Join {
            requireNonNull(source, "source");
            requireNonNull(alias, "alias");
            requireNonNull(target, "target");
            requireNonNull(type, "type");
            if (source instanceof TemplateSource && !(target instanceof TemplateTarget)) {
                throw new IllegalArgumentException("TemplateSource must be used in combination with TemplateTarget.");
            }
        }
    }

    private final boolean positionalOnly;
    private final boolean expandCollection;
    private final boolean supportRecords;
    private final TableNameResolver tableNameResolver;
    private final TableAliasResolver tableAliasResolver;
    private final ColumnNameResolver columnNameResolver;
    private final ForeignKeyResolver foreignKeyResolver;

    public SqlTemplateImpl(boolean positionalOnly, boolean expandCollection, boolean supportRecords) {
        this(positionalOnly, expandCollection, supportRecords, TableNameResolver.DEFAULT, TableAliasResolver.DEFAULT, ColumnNameResolver.DEFAULT, ForeignKeyResolver.DEFAULT);
    }

    public SqlTemplateImpl(boolean positionalOnly,
                           boolean expandCollection,
                           boolean supportRecords,
                           @Nullable TableNameResolver tableNameResolver,
                           @Nullable TableAliasResolver tableAliasResolver,
                           @Nullable ColumnNameResolver columnNameResolver,
                           @Nullable ForeignKeyResolver foreignKeyResolver) {
        this.positionalOnly = positionalOnly;
        this.expandCollection = expandCollection;
        this.supportRecords = supportRecords;
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
        return new SqlTemplateImpl(positionalOnly, expandCollection, supportRecords, resolver, tableAliasResolver, columnNameResolver, foreignKeyResolver);
    }

    @Override
    public TableNameResolver tableNameResolver() {
        return tableNameResolver;
    }

    @Override
    public SqlTemplateImpl withTableAliasResolver(TableAliasResolver resolver) {
        return new SqlTemplateImpl(positionalOnly, expandCollection, supportRecords, tableNameResolver, resolver, columnNameResolver, foreignKeyResolver);
    }

    @Override
    public TableAliasResolver tableAliasResolver() {
        return tableAliasResolver;
    }

    @Override
    public SqlTemplateImpl withColumnNameResolver(ColumnNameResolver resolver) {
        return new SqlTemplateImpl(positionalOnly, expandCollection, supportRecords, tableNameResolver, tableAliasResolver, resolver, foreignKeyResolver);
    }

    @Override
    public ColumnNameResolver columnNameResolver() {
        return columnNameResolver;
    }

    @Override
    public SqlTemplateImpl withForeignKeyResolver(ForeignKeyResolver resolver) {
        return new SqlTemplateImpl(positionalOnly, expandCollection, supportRecords, tableNameResolver, tableAliasResolver, columnNameResolver, resolver);
    }

    @Override
    public ForeignKeyResolver foreignKeyResolver() {
        return foreignKeyResolver;
    }

    @Override
    public SqlTemplateImpl withSupportRecords(boolean supportRecords) {
        return new SqlTemplateImpl(positionalOnly, expandCollection, supportRecords, tableNameResolver, tableAliasResolver, columnNameResolver, foreignKeyResolver);
    }

    @Override
    public boolean supportRecords() {
        return supportRecords;
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
            return STR."\{getClass().getSimpleName()}@\{toHexString(identityHashCode(this))}";
        }
    }

    @Override
    public BindVars createBindVars() {
        return new BindVarsImpl();
    }

    private Element resolveBindVarsElement(@Nonnull SqlMode mode,
                                           @Nonnull String previousFragment,
                                           @Nonnull BindVars bindVars) throws SqlTemplateException {
        String previous = removeComments(previousFragment).stripTrailing().toUpperCase();
        return switch (mode) {
            case SELECT, DELETE, UNDEFINED -> {
                if (previous.endsWith("WHERE")) {
                    yield where(bindVars);
                }
                throw new SqlTemplateException("BindVars element expected after WHERE.");
            }
            case INSERT -> {
                if (previous.endsWith("WHERE")) {
                    yield where(bindVars);
                }
                if (previous.endsWith("VALUES")) {
                    yield values(bindVars);
                }
                throw new SqlTemplateException("BindVars element expected after VALUES or WHERE.");
            }
            case UPDATE -> {
                if (previous.endsWith("SET")) {
                    yield set(bindVars);
                }
                if (previous.endsWith("WHERE")) {
                    yield where(bindVars);
                }
                throw new SqlTemplateException("BindVars element expected after SET or WHERE.");
            }
        };
    }

    private Element resolveObjectElement(@Nonnull SqlMode mode,
                                         @Nonnull String previousFragment,
                                         @Nullable Object o) throws SqlTemplateException {
        String previous = removeComments(previousFragment).stripTrailing().toUpperCase();
        return switch (mode) {
            case SELECT, DELETE, UNDEFINED -> {
                if (previous.endsWith("WHERE")) {
                    if (o != null) {
                        yield where(o);
                    }
                    throw new SqlTemplateException("Non-null object expected after WHERE.");
                }
                yield param(o);
            }
            case INSERT -> {
                if (previous.endsWith("VALUES")) {
                    if (o instanceof Record r) {
                        yield values(r);
                    }
                    throw new SqlTemplateException("Record expected after VALUES.");
                }
                if (previous.endsWith("WHERE")) {
                    if (o != null) {
                        yield where(o);
                    }
                    throw new SqlTemplateException("Non-null object expected after WHERE.");
                }
                yield param(o);
            }
            case UPDATE -> {
                if (previous.endsWith("SET")) {
                    if (o instanceof Record r) {
                        yield set(r);
                    }
                    throw new SqlTemplateException("Record expected after SET.");
                }
                if (previous.endsWith("WHERE")) {
                    if (o != null) {
                        yield where(o);
                    }
                    throw new SqlTemplateException("Non-null object expected after WHERE.");
                }
                yield param(o);
            }
        };
    }

    private Element resolveArrayElement(@Nonnull SqlMode mode,
                                        @Nonnull String previousFragment,
                                        @Nonnull Object[] array) throws SqlTemplateException {
        return resolveIterableElement(mode, previousFragment, List.of(array));
    }

    @SuppressWarnings("unchecked")
    private Element resolveIterableElement(@Nonnull SqlMode mode,
                                           @Nonnull String previousFragment,
                                           @Nonnull Iterable<?> iterable) throws SqlTemplateException {
        String previous = removeComments(previousFragment).stripTrailing().toUpperCase();
        return switch (mode) {
            case SELECT, UPDATE, DELETE, UNDEFINED -> {
                if (previous.endsWith("WHERE")) {
                    yield where(iterable);
                }
                yield param(iterable);
            }
            case INSERT -> {
                if (previous.endsWith("VALUES")) {
                    if (StreamSupport.stream(iterable.spliterator(), false)
                            .allMatch(it -> it instanceof Record)) {
                        yield values((Iterable<? extends Record>) iterable);
                    }
                    throw new SqlTemplateException("Records expected after VALUES.");
                }
                if (previous.endsWith("WHERE")) {
                    yield where(iterable);
                }
                yield param(iterable);
            }
        };
    }

    private Element resolveTypeElement(@Nonnull SqlMode mode,
                                       @Nullable Element first,
                                       @Nonnull String previousFragment,
                                       @Nonnull String nextFragment,
                                       @Nonnull Class<? extends Record> recordType) throws SqlTemplateException {
        if (nextFragment.startsWith(".")) {
            return new Alias(recordType, null, CASCADE);
        }
        String next = removeComments(nextFragment).stripLeading().toUpperCase();
        String previous = removeComments(previousFragment).stripTrailing().toUpperCase();
        return switch (mode) {
            case SELECT -> {
                if (previous.endsWith("FROM")) {
                    // Only use auto join if the selected table is present in the from-table graph.
                    boolean autoJoin = first instanceof Select(var table) && isTypePresent(recordType, table);
                    yield from(recordType, autoJoin);
                }
                if (previous.endsWith("JOIN")) {
                    yield table(recordType);
                }
                yield select(recordType);
            }
            case INSERT -> {
                if (previous.endsWith("INTO")) {
                    yield insert(recordType);
                }
                yield table(recordType);
            }
            case UPDATE -> {
                if (previous.endsWith("UPDATE")) {
                    yield update(recordType);
                }
                yield table(recordType);
            }
            case DELETE -> {
                if (next.startsWith("FROM")) {
                    yield delete(recordType);
                }
                if (previous.endsWith("FROM")) {
                    yield from(recordType, false);
                }
                yield table(recordType);
            }
            case UNDEFINED -> table(recordType);
        };
    }

    private List<Element> resolveElements(@Nonnull SqlMode sqlMode,
                                          @Nonnull List<?> values,
                                          @Nonnull List<String> fragments) throws SqlTemplateException {
        List<Element> resolvedValues = new ArrayList<>();
        Element first = null;
        for (int i = 0; i < values.size() ; i++) {
            var v = values.get(i);
            var p = fragments.get(i);
            var n = fragments.get(i + 1);
            var element = switch (v) {
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
                    if (first != null) {
                        throw new SqlTemplateException("Only a single Select element is allowed.");
                    }
                    yield it;
                }
                case Insert it -> {
                    if (first != null) {
                        throw new SqlTemplateException("Only a single Insert element is allowed.");
                    }
                    yield it;
                }
                case Update it -> {
                    if (first != null) {
                        throw new SqlTemplateException("Only a single Update element is allowed.");
                    }
                    yield it;
                }
                case Delete it -> {
                    if (first != null) {
                        throw new SqlTemplateException("Only a single Delete element is allowed.");
                    }
                    yield it;
                }
                case Expression _ -> throw new SqlTemplateException("Expression element not allowed in this context.");
                case BindVars b -> resolveBindVarsElement(sqlMode, p, b);
                case Templatable t -> new Subquery(t.asStringTemplate(), true); // Correlate implicit subqueries.
                case StringTemplate t -> new Subquery(t, true);                 // Correlate implicit subqueries.
                case Object[] a -> resolveArrayElement(sqlMode, p, a);
                case Iterable<?> l -> resolveIterableElement(sqlMode, p, l);
                case Element e -> e;
                case Class<?> c when c.isRecord() -> //noinspection unchecked
                        resolveTypeElement(sqlMode, first, p, n, (Class<? extends Record>) c);
                // Note that the following flow would also support Class<?> c. but we'll keep the Class<?> c case for performance and readability.
                case Object k when REFLECTION.isSupportedType(k) ->
                        resolveTypeElement(sqlMode, first, p, n, REFLECTION.getRecordType(k));
                case Stream<?> _ -> throw new SqlTemplateException("Stream not supported as string template value.");
                case Query _ -> throw new SqlTemplateException("Query not supported as string template value. Use QueryBuilder instead.");
                case Object o -> resolveObjectElement(sqlMode, p, o);
                case null -> //noinspection ConstantValue
                        param(v);
            };
            if (first == null
                    && (element instanceof Select || element instanceof Insert || element instanceof Update || element instanceof Delete)) {
                first = element;
            }
            resolvedValues.add(element);
        }
        return resolvedValues;
    }

    static String toPathString(@Nonnull List<RecordComponent> components) {
        return components.stream().map(RecordComponent::getName).collect(Collectors.joining("."));
    }

    static SqlTemplateException multiplePathsFoundException(@Nonnull Class<? extends Record> table, @Nonnull List<String> paths) {
        paths = paths.stream().filter(Objects::nonNull).distinct().map(p -> STR."'\{p}'").toList();
        if (paths.isEmpty()) {
            return new SqlTemplateException(STR."Multiple paths found for \{table.getSimpleName()}.");
        }
        if (paths.size() == 1) {
            return new SqlTemplateException(STR."Multiple paths found for \{table.getSimpleName()}. Specify path \{paths.getFirst()} to uniquely identify the table.");
        }
        return new SqlTemplateException(STR."Multiple patths found for \{table.getSimpleName()} in table graph. Specify one of the following paths to uniquely identify the table: \{String.join(", ", paths)}.");
    }

    private TableMapper getTableMapper() {
        return new TableMapper();
    }

    private AliasMapper getAliasMapper() {
        return new AliasMapper(tableAliasResolver, tableNameResolver, ElementProcessor.current()
                .map(ElementProcessor::aliasMapper)
                .orElse(null));
    }

    /**
     * Updates {@code elements} to include joins and aliases for the table in the FROM clause.
     *
     * @param elements all elements in the sql statement.
     */
    private void postProcessSelect(@Nonnull List<Element> elements,
                                   @Nonnull AliasMapper aliasMapper,
                                   @Nonnull TableMapper tableMapper) throws SqlTemplateException {
        final From from = elements.stream()
                .filter(From.class::isInstance)
                .map(From.class::cast)
                .filter(f -> f.source() instanceof TableSource)
                .findAny()
                .orElse(null);
        final From effectiveFrom;
        if (from != null && from.source() instanceof TableSource(var table)) {
            String path = "";   // Use "" because it's the root table.
            String alias;
            if (from.alias().isEmpty()) {
                // Replace From element by from element with alias.
                alias = aliasMapper.generateAlias(table, path);
            } else {
                alias = from.alias();
                aliasMapper.setAlias(table, alias, path);
            }
            var projectionQuery = REFLECTION.getAnnotation(table, ProjectionQuery.class);
            Source source = projectionQuery != null
                    ? new TemplateSource(StringTemplate.of(projectionQuery.value()))
                    : new TableSource(table);
            effectiveFrom = new From(source, alias, projectionQuery == null && from.autoJoin());
            elements.replaceAll(element -> element instanceof From ? effectiveFrom : element);
            // We will only make primary keys available for mapping if the table is not part of the entity graph,
            // because the entities can already be resolved by their foreign keys.
            // tableMapper.mapPrimaryKey(table, alias, getPkComponents(table).toList(), path);
            addJoins(elements, effectiveFrom, aliasMapper, tableMapper);
        } else {
            // If no From element is present, we will only add table aliases.
            addTableAliases(elements, aliasMapper);
        }
        validateWhere(elements);
    }

    /**
     * Updates {@code elements} to handle include aliases for the table in the UPDATE clause.
     *
     * @param elements all elements in the sql statement.
     */
    private void postProcessUpdate(@Nonnull List<Element> elements,
                                   @Nonnull AliasMapper aliasMapper,
                                   @Nonnull TableMapper tableMapper) throws SqlTemplateException {
        final Update update = elements.stream()
                .filter(Update.class::isInstance)
                .map(Update.class::cast)
                .findAny()
                .orElse(null);
        if (update != null) {
            var table = update.table();
            String path = "";   // Use "" because it's the root table.
            String alias;
            if (update.alias().isEmpty()) {
                alias = aliasMapper.generateAlias(table, path);
            } else {
                alias = update.alias();
                aliasMapper.setAlias(table, alias, path);
            }
            // We will only make primary keys available for mapping if the table is not part of the entity graph,
            // because the entities can already be resolved by their foreign keys.
            //  tableMapper.mapPrimaryKey(table, alias, getPkComponents(update.table()).toList(), path);
            // Make the FKs of the entity also available for mapping.
            mapForeignKeys(tableMapper, alias, table, path);
        }
        addTableAliases(elements, aliasMapper);
        validateWhere(elements);
    }

    /**
     * Updates {@code elements} to handle joins and aliases for the table in the DELETE clause.
     *
     * @param elements all elements in the sql statement.
     */
    private void postProcessDelete(@Nonnull List<Element> elements,
                                   @Nonnull AliasMapper aliasMapper,
                                   @Nonnull TableMapper tableMapper) throws SqlTemplateException {
        final Delete delete = elements.stream()
                .filter(Delete.class::isInstance)
                .map(Delete.class::cast)
                .findAny()
                .orElse(null);
        final From from = elements.stream()
                .filter(From.class::isInstance)
                .map(From.class::cast)
                .filter(f -> f.source() instanceof TableSource)
                .findAny()
                .orElse(null);
        final From effectiveFrom;
        if (from != null && from.source() instanceof TableSource(var table)) {
            String path = "";   // Use "" because it's the root table.
            String alias;
            if (from.alias().isEmpty()) {
                if (delete == null) {
                    // Only include alias when delete element is present as some database don't support aliases in delete statements.
                    aliasMapper.setAlias(table, "", path);
                    alias = "";
                } else {
                    alias = aliasMapper.generateAlias(table, path);
                }
                effectiveFrom = new From(new TableSource(table), alias, from.autoJoin());
                elements.replaceAll(element -> element instanceof From ? effectiveFrom : element);
            } else {
                effectiveFrom = from;
                alias = from.alias();
                aliasMapper.setAlias(table, alias, path);
            }
            // We will only make primary keys available for mapping if the table is not part of the entity graph,
            // because the entities can already be resolved by their foreign keys.
            //  tableMapper.mapPrimaryKey(table, alias, getPkComponents(table).toList(), path);
            if (delete != null) {
                if (delete.table() != table) {
                    throw new SqlTemplateException(STR."Delete entity \{delete.table().getSimpleName()} does not match From table \{table.getSimpleName()}.");
                }
                if (delete.alias().isEmpty()) {
                    if (!effectiveFrom.alias().isEmpty()) {
                        elements.replaceAll(element -> element instanceof Delete
                                ? delete(table, alias)
                                : element);
                    }
                }
            }
            // Make the FKs of the entity also available for mapping.
            mapForeignKeys(tableMapper, alias, table, path);
            // We will only make primary keys available for mapping if the table is not part of the entity graph,
            // because the entities can already be resolved by their foreign keys.
            // tableMapper.mapPrimaryKey(table, alias, getPkComponents(table).toList(), path);
            addJoins(elements, effectiveFrom, aliasMapper, tableMapper);
        } else if (delete != null) {
            throw new SqlTemplateException("From element required when using Delete element.");
        } else {
            // If no From element is present, we will only add table aliases.
            addTableAliases(elements, aliasMapper);
        }
        validateWhere(elements);
    }

    /**
     * Updates {@code elements} to handle joins and aliases for the table in the scenario where no primary table is
     * present for the statement.
     *
     * @param elements all elements in the sql statement.
     */
    private void postProcessOther(@Nonnull List<Element> elements,
                                  @Nonnull AliasMapper aliasMapper,
                                  @Nonnull TableMapper tableMapper) throws SqlTemplateException {
        final From from = elements.stream()
                .filter(From.class::isInstance)
                .map(From.class::cast)
                .filter(f -> f.source() instanceof TableSource)
                .findAny()
                .orElse(null);
        if (from != null) {
            addJoins(elements, from, aliasMapper, tableMapper);
        } else {
            // If no From element is present, we will only add table aliases.
            addTableAliases(elements, aliasMapper);
        }
        validateWhere(elements);
    }

    private void mapForeignKeys(@Nonnull TableMapper tableMapper, @Nonnull String alias, @Nonnull Class<? extends Record> table, @Nullable String path)
            throws SqlTemplateException {
        for (var component : table.getRecordComponents()) {
            if (REFLECTION.isAnnotationPresent(component, FK.class)) {
                if (Lazy.class.isAssignableFrom(component.getType())) {
                    tableMapper.mapForeignKey(getLazyRecordType(component), alias, component, path);
                } else {
                    if (!component.getType().isRecord()) {
                        throw new SqlTemplateException(STR."FK annotation is only allowed on record types: \{component.getType().getSimpleName()}.");
                    }
                    //noinspection unchecked
                    Class<? extends Record> componentType = (Class<? extends Record>) component.getType();
                    tableMapper.mapForeignKey(componentType, alias, component, path);
                }
            }
        }
    }

    void addJoins(@Nonnull List<Element> elements, @Nonnull From from, @Nonnull AliasMapper aliasMapper, @Nonnull TableMapper tableMapper) throws SqlTemplateException {
        List<Join> customJoins = new ArrayList<>();
        for (ListIterator<Element> it = elements.listIterator(); it.hasNext(); ) {
            Element element = it.next();
            if (element instanceof Table(var table, var alias)) {
                aliasMapper.setAlias(table, alias, null);
            } else if (element instanceof Join j) {
                String path = null; // Custom joins are not part of the primary table.
                // Move custom join to list of (auto) joins to allow proper ordering of inner and outer joins.
                if (j instanceof Join(TableSource ts, _, _, _)) {
                    String alias;
                    if (j.alias().isEmpty()) {
                        alias = aliasMapper.generateAlias(ts.table(), null);
                    } else {
                        alias = j.alias();
                        aliasMapper.setAlias(ts.table(), j.alias(), null);
                    }
                    var projectionQuery = REFLECTION.getAnnotation(ts.table(), ProjectionQuery.class);
                    Source source = projectionQuery != null
                            ? new TemplateSource(StringTemplate.of(projectionQuery.value()))
                            : ts;
                    customJoins.add(new Join(source, alias, j.target(), j.type()));
                    tableMapper.mapPrimaryKey(ts.table(), alias, getPkComponents(ts.table()).toList(), path);
                    // Make the FKs of the join also available for mapping.
                    mapForeignKeys(tableMapper, alias, ts.table(), path);
                } else {
                    customJoins.add(j);
                }
                it.set(new Unsafe("")); // Replace by empty string to keep fragments and values in sync.
            }
        }
        List<Join> joins;
        if (from != null && from.autoJoin()) {
            if (!(from.source() instanceof TableSource(var table))) {
                throw new SqlTemplateException("From with table required when using auto join.");
            }
            joins = new ArrayList<>();
            addAutoJoins(table, customJoins, aliasMapper, tableMapper, joins);
        } else {
            joins = customJoins;
        }
        if (!joins.isEmpty()) {
            List<Element> replacementElements = new ArrayList<>();
            replacementElements.add(from);
            Select select = elements.stream()
                    .filter(Select.class::isInstance)
                    .map(Select.class::cast)
                    .findAny()
                    .orElse(null);
            if (select == null) {
                replacementElements.addAll(joins);
            } else {
                for (var join : joins) {
                    if (join instanceof Join(TableSource(var joinTable), _, _, _) &&
                            joinTable == select.table() && join.type().isOuter()) {
                        // If join is part of the select table and is an outer join, replace it with an inner join.
                        replacementElements.add(new Join(new TableSource(joinTable), join.alias(), join.target(), DefaultJoinType.INNER));
                    } else {
                        replacementElements.add(join);
                    }
                }
            }
            elements.replaceAll(element -> element instanceof From
                    ? new Wrapped(replacementElements)
                    : element);
        }
    }

    private void addAutoJoins(@Nonnull Class<? extends Record> recordType,
                              @Nonnull List<Join> customJoins,
                              @Nonnull AliasMapper aliasMapper,
                              @Nonnull TableMapper tableMapper,
                              @Nonnull List<Join> joins) throws SqlTemplateException {
        addAutoJoins(recordType, List.of(), aliasMapper, tableMapper, joins, null, false);
        joins.addAll(customJoins);
        // Move outer joins to the end of the list to ensure proper filtering across multiple databases.
        joins.sort(comparing(join -> join.type().isOuter()));
    }

    @SuppressWarnings("unchecked")
    private void addAutoJoins(@Nonnull Class<? extends Record> recordType,
                              @Nonnull List<RecordComponent> path,
                              @Nonnull AliasMapper aliasMapper,
                              @Nonnull TableMapper tableMapper,
                              @Nonnull List<Join> joins,
                              @Nullable String fkName,
                              boolean outerJoin) throws SqlTemplateException {
        for (var component : recordType.getRecordComponents()) {
            var list = new ArrayList<>(path);
            String fkPath = toPathString(path);
            list.add(component);
            var copy = copyOf(list);
            String pkPath = toPathString(copy);
            if (REFLECTION.isAnnotationPresent(component, FK.class)) {
                if (Lazy.class.isAssignableFrom(component.getType())) {
                    // No join needed for lazy components, but we will map the table, so we can query the lazy component.
                    String fromAlias;
                    if (fkName == null) {
                        fromAlias = aliasMapper.getAlias(recordType, fkPath, INNER);    // Use local resolve mode to prevent shadowing.
                    } else {
                        fromAlias = fkName;
                    }
                    tableMapper.mapForeignKey(getLazyRecordType(component), fromAlias, component, fkPath);
                    continue;
                }
                if (!component.getType().isRecord()) {
                    throw new SqlTemplateException(STR."FK annotation is only allowed on record types: \{component.getType().getSimpleName()}.");
                }
                Class<? extends Record> componentType = (Class<? extends Record>) component.getType();
                if (componentType == recordType) {
                    throw new SqlTemplateException(STR."Self-referencing FK annotation is not allowed: \{recordType.getSimpleName()}. FK must be marked as Lazy.");
                }
                // We may detect that the component is already by present by checking
                // aliasMap.containsKey(componentType), but we'll handle duplicate joins later to detect such issues
                // in a unified way (auto join vs manual join).
                String fromAlias;
                if (fkName == null) {
                    fromAlias = aliasMapper.getAlias(recordType, fkPath, INNER);    // Use local resolve mode to prevent shadowing.
                } else {
                    fromAlias = fkName;
                }
                String alias = aliasMapper.generateAlias(componentType, pkPath);
                tableMapper.mapForeignKey(componentType, fromAlias, component, fkPath);
                var pkComponent = getPkComponents(componentType).findFirst()    // We only support single primary keys for FK fields.
                        .orElseThrow(() -> new SqlTemplateException(STR."No primary key found for entity \{componentType.getSimpleName()}."));
                String pkColumnName = getColumnName(pkComponent, columnNameResolver);
                // We will only make primary keys available for mapping if the table is not part of the entity graph,
                // because the entities can already be resolved by their foreign keys.
                //  tableMapper.mapPrimaryKey(componentType, alias, List.of(pkComponent), pkPath);
                outerJoin = outerJoin || !REFLECTION.isNonnull(component);
                JoinType joinType = outerJoin ? DefaultJoinType.LEFT : DefaultJoinType.INNER;
                ProjectionQuery query = REFLECTION.getAnnotation(componentType, ProjectionQuery.class);
                Source source = query != null
                        ? new TemplateSource(StringTemplate.of(query.value()))
                        : new TableSource(componentType);
                joins.add(new Join(source, alias, new TemplateTarget(StringTemplate.of(STR."\{fromAlias}.\{getForeignKey(component, foreignKeyResolver)} = \{alias}.\{pkColumnName}")), joinType));
                addAutoJoins(componentType, copy, aliasMapper, tableMapper, joins, alias, outerJoin);
            } else if (component.getType().isRecord()) {
                if (REFLECTION.isAnnotationPresent(component, PK.class) || getORMConverter(component).isPresent()) {
                    continue;
                }
                // @Inlined is implicitly assumed.
                Class<? extends Record> componentType = (Class<? extends Record>) component.getType();
                String fromAlias;
                if (fkName == null) {
                    fromAlias = aliasMapper.getAlias(recordType, fkPath, INNER);    // Use local resolve mode to prevent shadowing.
                } else {
                    fromAlias = fkName;
                }
                addAutoJoins(componentType, copy, aliasMapper, tableMapper, joins, fromAlias, outerJoin);
            }
        }
    }

    private void addTableAliases(@Nonnull List<Element> elements,
                                 @Nonnull AliasMapper aliasMapper) throws SqlTemplateException {
        for (Element element : elements) {
            if (element instanceof Table(var table, var alias)) {
                aliasMapper.setAlias(table, alias, null);
            }
        }
    }

    private void validateWhere(@Nonnull List<Element> elements) throws SqlTemplateException {
        if (elements.stream().filter(Where.class::isInstance).count() > 1) {
            throw new SqlTemplateException("Multiple Where elements found.");
        }
    }

    @SuppressWarnings("unchecked")
    static Stream<RecordComponent> getPkComponents(@Nonnull Class<? extends Record> componentType) {
        return Stream.of(componentType.getRecordComponents())
                .flatMap(c -> {
                    if (REFLECTION.isAnnotationPresent(c, PK.class)) {
                        return Stream.of(c);
                    }
                    if (c.getType().isRecord() && getORMConverter(c).isEmpty()
                            && !REFLECTION.isAnnotationPresent(c, FK.class)) {
                        return getPkComponents((Class<? extends Record>) c.getType());
                    }
                    return Stream.empty();
                });
    }

    @SuppressWarnings("unchecked")
    static Stream<RecordComponent> getFkComponents(@Nonnull Class<? extends Record> componentType) {
        return Stream.of(componentType.getRecordComponents())
                .flatMap(c -> {
                    if (REFLECTION.isAnnotationPresent(c, FK.class)) {
                        return Stream.of(c);
                    }
                    if (c.getType().isRecord() && getORMConverter(c).isEmpty()) {
                        return getFkComponents((Class<? extends Record>) c.getType());
                    }
                    return Stream.empty();
                });
    }

    static boolean isAutoGenerated(@Nonnull RecordComponent component) {
        PK pk = REFLECTION.getAnnotation(component, PK.class);
        return pk != null
                && pk.autoGenerated()
                && !component.getType().isRecord()                          // Record PKs are not auto-generated.
                && !REFLECTION.isAnnotationPresent(component, FK.class);    // PKs that are also FKs are not auto-generated.
    }

    static boolean isTypePresent(@Nonnull Class<? extends Record> source,
                                 @Nonnull Class<? extends Record> target) throws SqlTemplateException {
        if (target.equals(source)) {
            return true;
        }
        return findComponent(List.of(source.getRecordComponents()), target).isPresent();
    }

    static Optional<RecordComponent> findComponent(@Nonnull List<RecordComponent> components,
                                                   @Nonnull Class<? extends Record> recordType) throws SqlTemplateException {
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
        Table table = elements.stream()
                .filter(From.class::isInstance)
                .map(From.class::cast)
                .map(f -> {
                    if (f.source() instanceof TableSource(var t)) {
                        return new Table(t, f.alias());
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
                .filter(Select.class::isInstance)
                .map(Select.class::cast)
                .findAny()
                .map(select -> new Table(select.table(), aliasMapper.getPrimaryAlias(select.table()).orElse("")));
    }


    private void postProcessElements(@Nonnull SqlMode sqlMode,
                                     @Nonnull List<Element> elements,
                                     @Nonnull AliasMapper aliasMapper,
                                     @Nonnull TableMapper tableMapper) throws SqlTemplateException {
        switch (sqlMode) {
            case SELECT -> postProcessSelect(elements, aliasMapper, tableMapper);
            case UPDATE -> postProcessUpdate(elements, aliasMapper, tableMapper);
            case DELETE -> postProcessDelete(elements, aliasMapper, tableMapper);
            default -> postProcessOther(elements, aliasMapper, tableMapper);
        }
    }

    /**
     * Processes the specified {@code stringTemplate} and returns the resulting SQL and parameters.
     *
     * @param template the string template to process.
     * @return the resulting SQL and parameters.
     * @throws SqlTemplateException if an error occurs while processing the input.
     */
    @Override
    public Sql process(@Nonnull StringTemplate template) throws SqlTemplateException {
        return process(template, false);
    }

    /**
     * Processes the specified {@code stringTemplate} and returns the resulting SQL and parameters.
     *
     * @param template the string template to process.
     * @return the resulting SQL and parameters.
     * @throws SqlTemplateException if an error occurs while processing the input.
     */
    public Sql process(@Nonnull StringTemplate template, boolean nested) throws SqlTemplateException {
        var fragments = template.fragments();
        var values = template.values();
        Sql generated;
        if (!values.isEmpty()) {
            var sqlMode = getSqlMode(template);
            var elements = resolveElements(sqlMode, values, fragments);
            var tableMapper = getTableMapper(); // No need to pass parent table mapper as only aliases are correlated.
            var aliasMapper = getAliasMapper();
            postProcessElements(sqlMode, elements, aliasMapper, tableMapper);
            var unwrappedElements = elements.stream()
                    .flatMap(e -> e instanceof Wrapped(var we) ? we.stream() : Stream.of(e))
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
            generated = new SqlImpl(
                    String.join("", parts),
                    copyOf(parameters),
                    ofNullable(bindVariables.get()),
                    copyOf(generatedKeys),
                    versionAware.getPlain());
        } else {
            assert fragments.size() == 1;
            generated = new SqlImpl(fragments.getFirst(), List.of(), empty(), List.of(), false);
        }
        if (!nested) {
            // Don't intercept nested calls.
            SqlInterceptorManager.intercept(generated);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(STR."Generated SQL:\n\{generated.statement()}");
            } else if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest(STR."Generated SQL:\n\{generated}");
            }
        }
        return generated;
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
                    return STR."No primary key found for record \{recordType.getSimpleName()}.";
                }
            }
            for (var pkComponent : pkComponents) {
                if (Lazy.class.isAssignableFrom(pkComponent.getType())) {
                    return STR."Primary key must not be lazy: \{recordType.getSimpleName()}.";
                }
            }
            if (pkComponents.size() > 1) {
                return STR."Multiple primary keys found for record \{recordType.getSimpleName()}.";
            }
            for (var fkComponent : getFkComponents(recordType).toList()) {
                if (fkComponent.getType().isRecord()) {
                    if (getPkComponents((Class<? extends Record>) fkComponent.getType()).anyMatch(pk -> pk.getType().isRecord())) {
                        return STR."Foreign key must not specify a compound primary key: \{fkComponent.getType().getSimpleName()} \{fkComponent.getName()}.";
                    }
                    if (REFLECTION.isAnnotationPresent(fkComponent, Inline.class)) {
                        return STR."Foreign key must not be inlined: \{fkComponent.getType().getSimpleName()} \{fkComponent.getName()}.";
                    }
                } else if (!Lazy.class.isAssignableFrom(fkComponent.getType())) {
                    return STR."Foreign key must be a record: \{fkComponent.getType().getSimpleName()} \{fkComponent.getName()}.";
                }
            }
            for (var component : recordType.getRecordComponents()) {
                if (getORMConverter(component).isPresent()) {
                    for (var annotation : List.of(PK.class, FK.class, Inline.class)) {
                        if (REFLECTION.isAnnotationPresent(component, annotation)) {
                            return STR."Converted component must not be @\{annotation.getSimpleName()}: \{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}.";
                        }
                    }
                }
                if (!component.getType().isRecord() && REFLECTION.isAnnotationPresent(component, Inline.class)) {
                    return STR."Inlined component must be a record: \{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}.";
                }
            }
            ProjectionQuery projectionQuery = REFLECTION.getAnnotation(recordType, ProjectionQuery.class);
            if (projectionQuery != null) {
                if (!Projection.class.isAssignableFrom(recordType)) {
                    return STR."ProjectionQuery must only be used on records implementing Projection: \{recordType.getSimpleName()}";
                }
                if (projectionQuery.value().isEmpty()) {
                    return STR."ProjectionQuery must specify a query: \{recordType.getSimpleName()}";
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
            return foreignKeyResolver.resolveColumnName(component, recordType);
        }
        throw new SqlTemplateException(STR."Cannot infer foreign key column name for entity \{component.getDeclaringRecord().getSimpleName()}. Specify a @Name annotation or provide a foreign key resolver.");
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
