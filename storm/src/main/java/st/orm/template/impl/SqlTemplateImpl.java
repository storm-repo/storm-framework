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
import jakarta.annotation.Nullable;
import st.orm.BindVars;
import st.orm.FK;
import st.orm.PK;
import st.orm.Query;
import st.orm.Ref;
import st.orm.repository.ProjectionQuery;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.ColumnNameResolver;
import st.orm.template.ForeignKeyResolver;
import st.orm.template.JoinType;
import st.orm.template.Metamodel;
import st.orm.template.Sql;
import st.orm.template.SqlDialect;
import st.orm.template.SqlTemplate;
import st.orm.template.SqlTemplateException;
import st.orm.template.TableAliasResolver;
import st.orm.template.TableNameResolver;
import st.orm.template.impl.Elements.Alias;
import st.orm.template.impl.Elements.Column;
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
import st.orm.template.impl.Elements.TableTarget;
import st.orm.template.impl.Elements.Target;
import st.orm.template.impl.Elements.TemplateSource;
import st.orm.template.impl.Elements.TemplateTarget;
import st.orm.template.impl.Elements.Unsafe;
import st.orm.template.impl.Elements.Update;
import st.orm.template.impl.SqlParser.SqlMode;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.MissingFormatArgumentException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Comparator.comparing;
import static java.util.List.copyOf;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
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
import static st.orm.spi.Providers.getSqlDialect;
import static st.orm.template.ResolveScope.CASCADE;
import static st.orm.template.ResolveScope.INNER;
import static st.orm.template.impl.RecordReflection.getForeignKeys;
import static st.orm.template.impl.RecordReflection.getPkComponent;
import static st.orm.template.impl.RecordReflection.getPrimaryKeys;
import static st.orm.template.impl.RecordReflection.getRefRecordType;
import static st.orm.template.impl.RecordReflection.isTypePresent;
import static st.orm.template.impl.RecordReflection.mapForeignKeys;
import static st.orm.template.impl.RecordValidation.validateParameters;
import static st.orm.template.impl.RecordValidation.validateRecordGraph;
import static st.orm.template.impl.RecordValidation.validateRecordType;
import static st.orm.template.impl.RecordValidation.validateWhere;
import static st.orm.template.impl.SqlParser.SqlMode.DELETE;
import static st.orm.template.impl.SqlParser.SqlMode.INSERT;
import static st.orm.template.impl.SqlParser.SqlMode.SELECT;
import static st.orm.template.impl.SqlParser.SqlMode.UNDEFINED;
import static st.orm.template.impl.SqlParser.SqlMode.UPDATE;
import static st.orm.template.impl.SqlParser.getSqlMode;
import static st.orm.template.impl.SqlParser.hasWhereClause;
import static st.orm.template.impl.SqlParser.removeComments;
import static st.orm.template.impl.SqlTemplateProcessor.current;
import static st.orm.template.impl.SqlTemplateProcessor.isSubquery;

/**
 * The sql template implementation that is responsible for generating SQL queries.
 */
public final class SqlTemplateImpl implements SqlTemplate {

    private static final Logger LOGGER = Logger.getLogger("st.orm.sql");
    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    record Wrapped(@Nonnull List<? extends Element> elements) implements Element {
        public Wrapped {
            requireNonNull(elements, "elements");
        }
    }

    private final boolean positionalOnly;
    private final boolean expandCollection;
    private final boolean supportRecords;
    private final ModelBuilder modelBuilder;
    private final TableAliasResolver tableAliasResolver;
    private final SqlDialect dialect;
    private final SqlDialectTemplate dialectTemplate;

    public SqlTemplateImpl(boolean positionalOnly, boolean expandCollection, boolean supportRecords) {
        this(positionalOnly, expandCollection, supportRecords, ModelBuilder.newInstance(), TableAliasResolver.DEFAULT, getSqlDialect());
    }

    public SqlTemplateImpl(boolean positionalOnly,
                           boolean expandCollection,
                           boolean supportRecords,
                           @Nonnull ModelBuilder modelBuilder,
                           @Nonnull TableAliasResolver tableAliasResolver,
                           @Nonnull SqlDialect dialect) {
        this.positionalOnly = positionalOnly;
        this.expandCollection = expandCollection;
        this.supportRecords = supportRecords;
        this.modelBuilder = requireNonNull(modelBuilder);
        this.tableAliasResolver = requireNonNull(tableAliasResolver);
        this.dialect = requireNonNull(dialect);
        this.dialectTemplate = new SqlDialectTemplate(dialect);
    }

    /**
     * Returns {@code true} if the template only support positional parameters, {@code false} otherwise.
     *
     * @return {@code true} if the template only support positional parameters, {@code false} otherwise.
     */
    @Override
    public boolean positionalOnly() {
        return positionalOnly;
    }

    /**
     * Returns {@code true} if collection parameters must be expanded as multiple (positional) parameters,
     * {@code false} otherwise.
     *
     * @return {@code true} if the template expands collection parameters, {@code false} otherwise.
     */
    @Override
    public boolean expandCollection() {
        return expandCollection;
    }

    /**
     * Returns a new SQL template with the specified table name resolver.
     *
     * @param tableNameResolver the table name resolver.
     * @return a new SQL template.
     */
    @Override
    public SqlTemplateImpl withTableNameResolver(@Nonnull TableNameResolver tableNameResolver) {
        return new SqlTemplateImpl(positionalOnly, expandCollection, supportRecords, modelBuilder.tableNameResolver(tableNameResolver), tableAliasResolver, dialect);
    }

    /**
     * Returns the table name resolver that is used by this template.
     *
     * @return the table name resolver that is used by this template.
     */
    @Override
    public TableNameResolver tableNameResolver() {
        return modelBuilder.tableNameResolver();
    }

    /**
     * Returns a new SQL template with the specified table alias resolver.
     *
     * @param tableAliasResolver the table alias resolver.
     * @return a new SQL template.
     */
    @Override
    public SqlTemplateImpl withTableAliasResolver(@Nonnull TableAliasResolver tableAliasResolver) {
        return new SqlTemplateImpl(positionalOnly, expandCollection, supportRecords, modelBuilder, tableAliasResolver, dialect);
    }

    /**
     * Returns the table alias resolver that is used by this template.
     *
     * @return the table alias resolver that is used by this template.
     */
    @Override
    public TableAliasResolver tableAliasResolver() {
        return tableAliasResolver;
    }

    /**
     * Returns a new SQL template with the specified column name resolver.
     *
     * @param columnNameResolver the column name resolver.
     * @return a new SQL template.
     */
    @Override
    public SqlTemplateImpl withColumnNameResolver(@Nonnull ColumnNameResolver columnNameResolver) {
        return new SqlTemplateImpl(positionalOnly, expandCollection, supportRecords, modelBuilder.columnNameResolver(columnNameResolver), tableAliasResolver, dialect);
    }

    /**
     * Returns the column name resolver that is used by this template.
     *
     * @return the column name resolver that is used by this template.
     */
    @Override
    public ColumnNameResolver columnNameResolver() {
        return modelBuilder.columnNameResolver();
    }

    /**
     * Returns a new SQL template with the specified foreign key resolver.
     *
     * @param foreignKeyResolver the foreign key resolver.
     * @return a new SQL template.
     */
    @Override
    public SqlTemplateImpl withForeignKeyResolver(@Nonnull ForeignKeyResolver foreignKeyResolver) {
        return new SqlTemplateImpl(positionalOnly, expandCollection, supportRecords, modelBuilder.foreignKeyResolver(foreignKeyResolver), tableAliasResolver, dialect);
    }

    /**
     * Returns the foreign key resolver that is used by this template.
     *
     * @return the foreign key resolver that is used by this template.
     */
    @Override
    public ForeignKeyResolver foreignKeyResolver() {
        return modelBuilder.foreignKeyResolver();
    }

    /**
     * Returns a new SQL template with the specified SQL dialect.
     *
     * @param dialect the SQL dialect to use.
     * @return a new SQL template.
     */
    @Override
    public SqlTemplate withDialect(@Nonnull SqlDialect dialect) {
        return new SqlTemplateImpl(positionalOnly, expandCollection, supportRecords, modelBuilder, tableAliasResolver, dialect);
    }

    /**
     * Returns the SQL dialect that is used by this template.
     *
     * @return the SQL dialect that is used by this template.
     * @since 1.2
     */
    @Override
    public SqlDialect dialect() {
        return dialect;
    }

    /**
     * Returns a new SQL template with support for records enabled or disabled.
     *
     * @param supportRecords {@code true} if the template should support records, {@code false} otherwise.
     * @return a new SQL template.
     */
    @Override
    public SqlTemplateImpl withSupportRecords(boolean supportRecords) {
        return new SqlTemplateImpl(positionalOnly, expandCollection, supportRecords, modelBuilder, tableAliasResolver, dialect);
    }

    /**
     * Returns {@code true} if the template supports tables represented as records, {@code false} otherwise.
     *
     * @return {@code true} if the template supports records, {@code false} otherwise.
     */
    @Override
    public boolean supportRecords() {
        return supportRecords;
    }

    /**
     * Create a new bind variables instance that can be used to add bind variables to a batch.
     *
     * @return a new bind variables instance.
     */
    @Override
    public BindVars createBindVars() {
        return new BindVarsImpl();
    }

    private Element resolveBindVarsElement(@Nonnull SqlMode mode,
                                           @Nonnull String previousFragment,
                                           @Nonnull BindVars bindVars) throws SqlTemplateException {
        String previous = removeComments(previousFragment, dialect).stripTrailing().toUpperCase();
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
        String previous = removeComments(previousFragment, dialect).stripTrailing().toUpperCase();
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
        String previous = removeComments(previousFragment, dialect).stripTrailing().toUpperCase();
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
            return new Alias(recordType, CASCADE);
        }
        String next = removeComments(nextFragment, dialect).stripLeading().toUpperCase();
        String previous = removeComments(previousFragment, dialect).stripTrailing().toUpperCase();
        return switch (mode) {
            case SELECT -> {
                if (previous.endsWith("FROM")) {
                    // Only use auto join if the selected table is present in the from-table graph.
                    boolean autoJoin = first instanceof Select(var table, _) && isTypePresent(recordType, table);
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
            Element element = switch (v) {
                case Select _ when sqlMode != SELECT -> throw new SqlTemplateException("Select element is only allowed for select statements.");
                case Insert _ when sqlMode != INSERT -> throw new SqlTemplateException("Insert element is only allowed for insert statements.");
                case Update _ when sqlMode != UPDATE -> throw new SqlTemplateException("Update element is only allowed for update statements.");
                case Delete _ when sqlMode != DELETE -> throw new SqlTemplateException("Delete element is only allowed for delete statements.");
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
                case Subqueryable t -> new Subquery(t.getSubquery(), true);   // Correlate implicit subqueries.
                case Metamodel<?, ?> m when m.isColumn() -> new Column(m, CASCADE);
                case Metamodel<?, ?> _ -> throw new SqlTemplateException("Metamodel does not reference a column.");
                case Object[] a -> resolveArrayElement(sqlMode, p, a);
                case Iterable<?> l -> resolveIterableElement(sqlMode, p, l);
                case Element e -> e;
                case Class<?> c when c.isRecord() -> //noinspection unchecked
                        resolveTypeElement(sqlMode, first, p, n, (Class<? extends Record>) c);
                // Note that the following flow would also support Class<?> c. but we'll keep the Class<?> c case for performance and readability.
                case Object k when REFLECTION.isSupportedType(k) ->
                        resolveTypeElement(sqlMode, first, p, n, REFLECTION.getRecordType(k));
                case StringTemplate _ -> throw new SqlTemplateException("StringTemplate not allowed as string template value.");
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

    private TableUse getTableUse() {
        return new TableUse();
    }

    private TableMapper getTableMapper(@Nonnull TableUse tableUse) {
        return new TableMapper(tableUse);
    }

    private AliasMapper getAliasMapper(@Nonnull TableUse tableUse) {
        return new AliasMapper(tableUse, tableAliasResolver, modelBuilder.tableNameResolver(), current()
                .map(SqlTemplateProcessor::aliasMapper)
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
            validateRecordGraph(table);
            String path = "";   // Use "" because it's the root table.
            String alias;
            if (from.alias().isEmpty()) {
                // Replace From element by from element with alias.
                alias = aliasMapper.generateAlias(table, path, dialect);
            } else {
                alias = from.alias();
                aliasMapper.setAlias(table, alias, path);
            }
            var projectionQuery = REFLECTION.getAnnotation(table, ProjectionQuery.class);
            Source source = projectionQuery != null
                    ? new TemplateSource(StringTemplate.of(projectionQuery.value()))
                    : new TableSource(table);
            effectiveFrom = new From(source, alias, from.autoJoin());
            elements.replaceAll(element -> element instanceof From ? effectiveFrom : element);
            // We will only make primary keys available for mapping if the table is not part of the entity graph,
            // because the entities can already be resolved by their foreign keys.
            // tableMapper.mapPrimaryKey(table, alias, getPkComponents(table).toList(), path);
            addJoins(table, elements, effectiveFrom, aliasMapper, tableMapper);
        } else {
            // If no From element is present, we will only add table aliases.
            addTableAliases(elements, aliasMapper);
        }
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
            validateRecordGraph(table);
            String path = "";   // Use "" because it's the root table.
            String alias;
            if (update.alias().isEmpty()) {
                // Use empty alias as some database don't support aliases in update statements.
                alias = "";
            } else {
                alias = update.alias();
                aliasMapper.setAlias(table, alias, path);
            }
            // We will only make primary keys available for mapping if the table is not part of the entity graph,
            // because the entities can already be resolved by their foreign keys.
            //  tableMapper.mapPrimaryKey(table, alias, getPkComponents(update.table()).toList(), path);
            // Make the FKs of the entity also available for mapping.
            mapForeignKeys(tableMapper, alias, table, table, path);
        }
        addTableAliases(elements, aliasMapper);
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
            validateRecordGraph(table);
            String path = "";   // Use "" because it's the root table.
            String alias;
            if (from.alias().isEmpty()) {
                if (delete == null) {
                    // Only include alias when delete element is present as some database don't support aliases in delete statements.
                    aliasMapper.setAlias(table, "", path);
                    alias = "";
                } else {
                    alias = aliasMapper.generateAlias(table, path, dialect);
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
            // We will only make primary keys available for mapping if the table is not part of the entity graph,
            // because the entities can already be resolved by their foreign keys.
            // tableMapper.mapPrimaryKey(table, alias, getPkComponents(table).toList(), path);
            addJoins(table, elements, effectiveFrom, aliasMapper, tableMapper);
        } else if (delete != null) {
            throw new SqlTemplateException("From element required when using Delete element.");
        } else {
            // If no From element is present, we will only add table aliases.
            addTableAliases(elements, aliasMapper);
        }
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
                .findAny()
                .orElse(null);
        if (from != null && from.source() instanceof TableSource(var table)) {
            validateRecordGraph(table);
            addJoins(table, elements, from, aliasMapper, tableMapper);
        } else {
            // If no From element is present, we will only add table aliases.
            addTableAliases(elements, aliasMapper);
        }
    }

    void addJoins(@Nonnull Class<? extends Record> fromTable,
                  @Nonnull List<Element> elements,
                  @Nonnull From from,
                  @Nonnull AliasMapper aliasMapper,
                  @Nonnull TableMapper tableMapper) throws SqlTemplateException {
        List<Join> customJoins = new ArrayList<>();
        for (ListIterator<Element> it = elements.listIterator(); it.hasNext(); ) {
            Element element = it.next();
            if (element instanceof Table(var table, var alias)) {
                aliasMapper.setAlias(table, alias, null);
            } else if (element instanceof Join j) {
                String path = ""; // Use "" for custom join, as they for their own root.
                // Move custom join to list of (auto) joins to allow proper ordering of inner and outer joins.
                if (j instanceof Join(TableSource ts, _, _, _, _, _)) {
                    String alias;
                    if (j.sourceAlias().isEmpty()) {
                        alias = aliasMapper.generateAlias(ts.table(), null, dialect);
                    } else {
                        alias = j.sourceAlias();
                        aliasMapper.setAlias(ts.table(), j.sourceAlias(), null);
                    }
                    var projectionQuery = REFLECTION.getAnnotation(ts.table(), ProjectionQuery.class);
                    Source source = projectionQuery != null
                            ? new TemplateSource(StringTemplate.of(projectionQuery.value()))
                            : ts;
                    customJoins.add(new Join(source, alias, j.target(), j.type(), false));
                    tableMapper.mapPrimaryKey(fromTable, ts.table(), alias, getPkComponent(ts.table())
                            .orElseThrow(() -> new SqlTemplateException(STR."No primary key found for table \{ts.table().getSimpleName()}.")),
                            ts.table(), path);
                    // Make the FKs of the join also available for mapping.
                    mapForeignKeys(tableMapper, alias, ts.table(), ts.table(), path);
                } else {
                    customJoins.add(j);
                }
                it.set(new Unsafe("")); // Replace by empty string to keep fragments and values in sync.
            }
        }
        List<Join> joins;
        if (from.autoJoin()) {
            joins = new ArrayList<>();
            addAutoJoins(fromTable, fromTable, customJoins, aliasMapper, tableMapper, joins);
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
                    if (join instanceof Join(TableSource(var joinTable), _, _, _, _, var autoJoin) &&
                            joinTable == select.table() && join.type().isOuter()) {
                        // If join is part of the select table and is an outer join, replace it with an inner join.
                        replacementElements.add(new Join(new TableSource(joinTable), join.sourceAlias(), join.target(), DefaultJoinType.INNER, autoJoin));
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
                              @Nonnull Class<? extends Record> rootTable,
                              @Nonnull List<Join> customJoins,
                              @Nonnull AliasMapper aliasMapper,
                              @Nonnull TableMapper tableMapper,
                              @Nonnull List<Join> joins) throws SqlTemplateException {
        addAutoJoins(recordType, recordType, rootTable, List.of(), aliasMapper, tableMapper, joins, null, false);
        joins.addAll(customJoins);
        // Move outer joins to the end of the list to ensure proper filtering across multiple databases.
        joins.sort(comparing(join -> join.type().isOuter()));
    }

    @SuppressWarnings("unchecked")
    private void addAutoJoins(@Nonnull Class<? extends Record> table,
                              @Nonnull Class<? extends Record> recordType,
                              @Nonnull Class<? extends Record> rootTable,
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
                if (Ref.class.isAssignableFrom(component.getType())) {
                    // No join needed for ref components, but we will map the table, so we can query the ref component.
                    String fromAlias;
                    if (fkName == null) {
                        fromAlias = aliasMapper.getAlias(recordType, fkPath, INNER, dialect,
                                () -> new SqlTemplateException(STR."Table \{recordType.getSimpleName()} for From not found at \{fkPath}."));    // Use local resolve mode to prevent shadowing.
                    } else {
                        fromAlias = fkName;
                    }
                    tableMapper.mapForeignKey(recordType, getRefRecordType(component), fromAlias, component, rootTable, fkPath);
                    continue;
                }
                if (!component.getType().isRecord()) {
                    throw new SqlTemplateException(STR."FK annotation is only allowed on record types: \{component.getType().getSimpleName()}.");
                }
                Class<? extends Record> componentType = (Class<? extends Record>) component.getType();
                if (componentType == recordType) {
                    throw new SqlTemplateException(STR."Self-referencing FK annotation is not allowed: \{recordType.getSimpleName()}. FK must be marked as Ref.");
                }
                // We may detect that the component is already by present by checking
                // aliasMap.containsKey(componentType), but we'll handle duplicate joins later to detect such issues
                // in a unified way (auto join vs manual join).
                String fromAlias;
                if (fkName == null) {
                    fromAlias = aliasMapper.getAlias(recordType, fkPath, INNER, componentType, dialect,
                            () -> new SqlTemplateException(STR."Table \{recordType.getSimpleName()} for From not found at path \{fkPath}."));   // Use local resolve mode to prevent shadowing.
                } else {
                    fromAlias = fkName;
                }
                String alias = aliasMapper.generateAlias(componentType, pkPath, recordType, dialect);
                tableMapper.mapForeignKey(table, componentType, fromAlias, component, rootTable, fkPath);
                // We will only make primary keys available for mapping if the table is not part of the entity graph,
                // because the entities can already be resolved by their foreign keys.
                //  tableMapper.mapPrimaryKey(componentType, alias, List.of(pkComponent), pkPath);
                boolean effectiveOuterJoin = outerJoin || !REFLECTION.isNonnull(component);
                JoinType joinType = effectiveOuterJoin ? DefaultJoinType.LEFT : DefaultJoinType.INNER;
                ProjectionQuery query = REFLECTION.getAnnotation(componentType, ProjectionQuery.class);
                Source source = query == null
                        ? new TableSource(componentType)
                        : new TemplateSource(StringTemplate.of(query.value()));
                Target target = query == null
                        ? new TableTarget(table)
                        : getTemplateTarget(fromAlias, alias, component, getPkComponent(componentType).orElseThrow(
                                () -> new SqlTemplateException(STR."Failed to find primary key for table \{componentType.getSimpleName()}")));
                joins.add(new Join(source, alias, target, fromAlias, joinType, true));
                addAutoJoins(componentType, componentType, rootTable, copy, aliasMapper, tableMapper, joins, alias, effectiveOuterJoin);
            } else if (component.getType().isRecord()) {
                if (REFLECTION.isAnnotationPresent(component, PK.class) || getORMConverter(component).isPresent()) {
                    continue;
                }
                // @Inlined is implicitly assumed.
                Class<? extends Record> componentType = (Class<? extends Record>) component.getType();
                String fromAlias;
                if (fkName == null) {
                    fromAlias = aliasMapper.getAlias(recordType, fkPath, INNER, componentType, dialect,
                            () -> new SqlTemplateException(STR."Table \{recordType.getSimpleName()} for From not found at path \{fkPath}."));   // Use local resolve mode to prevent shadowing.
                } else {
                    fromAlias = fkName;
                }
                addAutoJoins(table, componentType, rootTable, copy, aliasMapper, tableMapper, joins, fromAlias, outerJoin);
            }
        }
    }

    @SuppressWarnings("DuplicatedCode")
    private TemplateTarget getTemplateTarget(@Nonnull String fromAlias,
                                             @Nonnull String toAlias,
                                             @Nonnull RecordComponent fromComponent,
                                             @Nonnull RecordComponent toComponent) throws SqlTemplateException {
        var foreignKeys = getForeignKeys(fromComponent, foreignKeyResolver(), columnNameResolver());
        var primaryKeys = getPrimaryKeys(toComponent, columnNameResolver());
        if (foreignKeys.size() != primaryKeys.size()) {
            throw new SqlTemplateException("Mismatch between foreign keys and primary keys count.");
        }
        StringBuilder joinCondition = new StringBuilder();
        for (int i = 0; i < foreignKeys.size(); i++) {
            if (i > 0) {
                joinCondition.append(" AND ");
            }
            joinCondition.append(dialectTemplate."\{fromAlias}.\{foreignKeys.get(i)} = \{toAlias}.\{primaryKeys.get(i)}");
        }
        return new TemplateTarget(StringTemplate.of(joinCondition.toString()));
    }

    private void addTableAliases(@Nonnull List<Element> elements,
                                 @Nonnull AliasMapper aliasMapper) throws SqlTemplateException {
        for (Element element : elements) {
            if (element instanceof Table(var table, var alias)) {
                aliasMapper.setAlias(table, alias, null);
            }
        }
    }

    /**
     * Returns the primary table and its alias in the sql statement, such as the table in the FROM clause for a SELECT
     * or DELETE, or the table in the INSERT or UPDATE clause.
     *
     * @param elements all elements in the sql statement.
     * @param aliasMapper a mapper of table classes to their aliases.
     * @return the primary table for the sql statement.
     */
    private Optional<PrimaryTable> getPrimaryTable(@Nonnull List<Element> elements, @Nonnull AliasMapper aliasMapper) {
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
        return elements.stream()
                .filter(Select.class::isInstance)
                .map(Select.class::cast)
                .findAny()
                .map(select -> new PrimaryTable(select.table(), aliasMapper.getPrimaryAlias(select.table()).orElse("")));
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
     * Processes the specified {@code template} and returns the resulting SQL and parameters.
     *
     * @param template the string template to process.
     * @return the resulting SQL and parameters.
     * @throws SqlTemplateException if an error occurs while processing the input.
     */
    @Override
    public Sql process(@Nonnull StringTemplate template) throws SqlTemplateException {
        return process(template, isSubquery());
    }

    /**
     * Processes the specified {@code stringTemplate} and returns the resulting SQL and parameters.
     *
     * @param template the string template to process.
     * @param subquery whether the call is the context of a subquery.
     * @return the resulting SQL and parameters.
     * @throws SqlTemplateException if an error occurs while processing the input.
     */
    private Sql process(@Nonnull StringTemplate template, boolean subquery) throws SqlTemplateException {
        var fragments = template.fragments();
        var values = template.values();
        Sql generated;
        var sqlMode = getSqlMode(template, dialect);
        if (!values.isEmpty()) {
            var elements = resolveElements(sqlMode, values, fragments);
            var tableUse = getTableUse();
            var tableMapper = getTableMapper(tableUse); // No need to pass parent table mapper as only aliases are correlated.
            var aliasMapper = getAliasMapper(tableUse);
            postProcessElements(sqlMode, elements, aliasMapper, tableMapper);
            var unwrappedElements = elements.stream()
                    .flatMap(e -> e instanceof Wrapped(var we) ? we.stream() : Stream.of(e))
                    .toList();
            assert values.size() == elements.size();
            Optional<PrimaryTable> primaryTable = getPrimaryTable(unwrappedElements, aliasMapper);
            if (primaryTable.isPresent()) {
                validateRecordType(primaryTable.get().table(), sqlMode != SELECT && sqlMode != UNDEFINED);
            }
            validateWhere(unwrappedElements);
            List<String> parts = new ArrayList<>();
            List<Parameter> parameters = new ArrayList<>();
            AtomicReference<BindVariables> bindVariables = new AtomicReference<>();
            List<String> generatedKeys = new ArrayList<>();
            AtomicBoolean versionAware = new AtomicBoolean();
            AtomicInteger parameterPosition = new AtomicInteger(1);
            AtomicInteger nameIndex = new AtomicInteger();
            StringBuilder rawSql = new StringBuilder();
            interface DelayedResult {
                void process() throws SqlTemplateException;
            }
            SqlTemplateProcessor processor = new SqlTemplateProcessor(this, dialectTemplate, modelBuilder,
                    parameters, parameterPosition, nameIndex,
                    tableUse, aliasMapper, tableMapper,
                    bindVariables, generatedKeys, versionAware,
                    primaryTable.orElse(null));
            var results = new ArrayList<DelayedResult>();
            for (int i = 0; i < fragments.size(); i++) {
                String fragment = fragments.get(i);
                try {
                    if (i < values.size()) {
                        Element e = elements.get(i);
                        var result = processor.process(e);
                        results.add(() -> {
                            String sql = result.get();
                            if (!sql.isEmpty()) {
                                rawSql.append(fragment);
                                rawSql.append(sql);
                                if (e instanceof Param) {
                                    parts.add(rawSql.toString());
                                    rawSql.setLength(0);
                                }
                            } else {
                                rawSql.append(fragment);
                            }
                        });
                    } else {
                        results.add(() -> {
                            rawSql.append(fragment);
                            parts.add(rawSql.toString());
                        });
                    }
                } catch (MissingFormatArgumentException ex) {
                    throw new SqlTemplateException("Invalid number of argument placeholders found. Template appears to specify custom %s placeholders.", ex);
                }
            }
            for (var result : results) {
                result.process();
            }
            validateParameters(parameters);
            String sql = String.join("", parts);
            if (subquery && !sql.startsWith("\n") && sql.contains("\n")) {
                //noinspection StringTemplateMigration
                sql = "\n" + sql.indent(2);
            }
            generated = new SqlImpl(sql, parameters, ofNullable(bindVariables.get()),
                    generatedKeys, versionAware.getPlain(), checkSafety(sql, sqlMode));
        } else {
            assert fragments.size() == 1;
            generated = new SqlImpl(fragments.getFirst(), List.of(), empty(), List.of(), false, checkSafety(fragments.getFirst(), sqlMode));
        }
        if (!subquery) {
            // Don't intercept subquery calls.
            generated = SqlInterceptorManager.intercept(generated);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(STR."Generated SQL:\n\{generated.statement()}");
            } else if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest(STR."Generated SQL:\n\{generated}");
            }
        }
        return generated;
    }

    private Optional<String> checkSafety(@Nonnull String sql, @Nonnull SqlMode mode) {
        return switch (mode) {
            case SELECT, INSERT, UNDEFINED -> empty();
            case UPDATE, DELETE -> {
                if (!hasWhereClause(sql, dialect)) {
                    yield Optional.of(STR."\{mode} without a WHERE clause is potentially unsafe.");
                }
                yield empty();
            }
        };
    }
}
