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
import st.orm.BindVars;
import st.orm.Data;
import st.orm.DefaultJoinType;
import st.orm.Element;
import st.orm.FK;
import st.orm.JoinType;
import st.orm.Metamodel;
import st.orm.PK;
import st.orm.ProjectionQuery;
import st.orm.Ref;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.core.template.Query;
import st.orm.core.template.SqlOperation;
import st.orm.core.template.SqlTemplate;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.TemplateString;
import st.orm.core.template.impl.Elements.Column;
import st.orm.core.template.impl.Elements.Delete;
import st.orm.core.template.impl.Elements.Expression;
import st.orm.core.template.impl.Elements.From;
import st.orm.core.template.impl.Elements.Insert;
import st.orm.core.template.impl.Elements.Select;
import st.orm.core.template.impl.Elements.Source;
import st.orm.core.template.impl.Elements.Table;
import st.orm.core.template.impl.Elements.TableSource;
import st.orm.core.template.impl.Elements.TemplateTarget;
import st.orm.core.template.impl.Elements.Update;
import st.orm.core.template.impl.SqlTemplateImpl.ElementNode;
import st.orm.core.template.impl.SqlTemplateImpl.Wrapped;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Comparator.comparing;
import static java.util.List.copyOf;
import static java.util.stream.Collectors.joining;
import static st.orm.ResolveScope.CASCADE;
import static st.orm.ResolveScope.INNER;
import static st.orm.core.spi.Providers.getORMConverter;
import static st.orm.core.template.SqlOperation.DELETE;
import static st.orm.core.template.SqlOperation.INSERT;
import static st.orm.core.template.SqlOperation.SELECT;
import static st.orm.core.template.SqlOperation.UPDATE;
import static st.orm.core.template.Templates.delete;
import static st.orm.core.template.Templates.from;
import static st.orm.core.template.Templates.insert;
import static st.orm.core.template.Templates.param;
import static st.orm.core.template.Templates.select;
import static st.orm.core.template.Templates.set;
import static st.orm.core.template.Templates.table;
import static st.orm.core.template.Templates.update;
import static st.orm.core.template.Templates.values;
import static st.orm.core.template.Templates.where;
import static st.orm.core.template.impl.RecordReflection.findPkField;
import static st.orm.core.template.impl.RecordReflection.getForeignKeys;
import static st.orm.core.template.impl.RecordReflection.getPrimaryKeys;
import static st.orm.core.template.impl.RecordReflection.getRecordType;
import static st.orm.core.template.impl.RecordReflection.getRefDataType;
import static st.orm.core.template.impl.RecordReflection.isRecord;
import static st.orm.core.template.impl.RecordReflection.isTypePresent;
import static st.orm.core.template.impl.RecordReflection.mapForeignKeys;
import static st.orm.core.template.impl.RecordValidation.validateDataType;
import static st.orm.core.template.impl.SqlParser.getSqlOperation;
import static st.orm.core.template.impl.SqlParser.removeComments;

/**
 * Prepares a {@link TemplateString} for compilation and binding.
 *
 * <p>This class performs three responsibilities:</p>
 *
 * <ul>
 *   <li><b>Preprocessing</b>: determines the {@link SqlOperation} and resolves raw template values into {@link Element}
 *   instances with context-aware rules.</li>
 *   <li><b>Post-processing</b>: derives table aliases and join structure, and registers join mappings required for
 *   expression compilation and record mapping.</li>
 *   <li><b>Preparation</b>: produces a {@link TemplateProcessor} and a {@link CompilationContext} that can be compiled
 *   once and then bound repeatedly.</li>
 * </ul>
 *
 * <p>Preparation is per template invocation. A prepared template can be correlated with a parent processor when nested
 * subqueries must share alias resolution and hint ordering.</p>
 *
 * @since 1.8
 */
class TemplatePreparation {

    /**
     * Reflection helper used to interpret runtime classes and supported types.
     */
    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    /**
     * The template being prepared.
     */
    private final SqlTemplate template;

    /**
     * Builder for resolving ORM models used during post-processing and query model creation.
     */
    private final ModelBuilder modelBuilder;

    /**
     * Dialect helper used for constructing SQL fragments such as join conditions.
     */
    private final SqlDialectTemplate dialectTemplate;

    /**
     * Factory for creating the optional {@link QueryModel} used by expression compilation and alias resolution.
     */
    private final QueryModelFactory queryModelFactory;

    /**
     * Creates a new preparation instance for a specific {@link SqlTemplate}.
     *
     * @param template     the template to prepare.
     * @param modelBuilder the model builder used for resolving record and table metadata.
     */
    TemplatePreparation(@Nonnull SqlTemplate template, @Nonnull ModelBuilder modelBuilder) {
        this.template = template;
        this.modelBuilder = modelBuilder;
        this.dialectTemplate = new SqlDialectTemplate(template.dialect());
        this.queryModelFactory = new QueryModelFactory(template, modelBuilder);
    }

    /**
     * Binding context produced by {@link #preprocess(TemplateString)}.
     *
     * <p>This context contains the raw template fragments, resolved elements (not yet post-processed), and the SQL
     * operation determined from the template string.</p>
     *
     * @param fragments  the template fragments.
     * @param elements   resolved elements corresponding to template values.
     * @param operation  the SQL operation inferred from the template.
     */
    record BindingContext(@Nonnull List<String> fragments, @Nonnull List<Element> elements, @Nonnull SqlOperation operation) { }

    /**
     * Compilation context produced by {@link #prepare(BindingContext)}.
     *
     * <p>This context contains the fragments and post-processed elements used by
     * {@link TemplateProcessor#compile(CompilationContext, boolean)}.</p>
     *
     * @param fragments  the template fragments.
     * @param elements   post-processed elements, possibly containing {@link Wrapped} nodes to inject joins.
     * @param operation  the SQL operation inferred from the template.
     */
    record CompilationContext(@Nonnull List<String> fragments, @Nonnull List<Element> elements, @Nonnull SqlOperation operation) { }

    /**
     * Prepared template consisting of a processor and its compilation context.
     *
     * <p>The returned processor can be compiled once and then bound multiple times. When preparation is performed as a
     * child of another processor, compilation state is shared via the parent-child processor relationship.</p>
     *
     * @param processor the processor responsible for compilation and binding.
     * @param context   the compilation context to compile.
     */
    record PreparedTemplate(@Nonnull TemplateProcessor processor, @Nonnull CompilationContext context) { }

    /**
     * Preprocesses a template string into fragments and resolved elements.
     *
     * <p>This step determines the {@link SqlOperation} and resolves each raw value into an {@link Element} based on
     * operation, surrounding SQL fragments, and template configuration.</p>
     *
     * @param templateString the template string to preprocess.
     * @return the binding context.
     * @throws SqlTemplateException if a value cannot be resolved in the given context.
     */
    BindingContext preprocess(@Nonnull TemplateString templateString) throws SqlTemplateException {
        var fragments = templateString.fragments();
        var values = templateString.values();
        var operation = getSqlOperation(templateString, template.dialect());
        var elements = resolveElements(operation, values, fragments);
        assert values.size() == elements.size();
        return new BindingContext(fragments, elements, operation);
    }

    /**
     * Prepares a binding context for compilation without correlation.
     *
     * @param bindingContext the binding context.
     * @return the prepared template.
     * @throws SqlTemplateException if preparation fails.
     */
    PreparedTemplate prepare(@Nonnull BindingContext bindingContext) throws SqlTemplateException {
        return prepare(bindingContext, null, false);
    }

    /**
     * Prepares a binding context for compilation and binding.
     *
     * <p>This method performs post-processing (aliases, joins, mapping availability) and creates the processor to compile
     * and bind the statement. When {@code correlate} is true, aliases may be shared with the parent processor.</p>
     *
     * @param bindingContext  the binding context.
     * @param parentProcessor the parent processor used for correlated nested preparation, or {@code null}.
     * @param correlate       whether the preparation should correlate with the parent processor.
     * @return the prepared template.
     * @throws SqlTemplateException if preparation fails.
     */
    PreparedTemplate prepare(
            @Nonnull BindingContext bindingContext,
            @Nullable TemplateProcessor parentProcessor,
            boolean correlate
    ) throws SqlTemplateException {
        assert !correlate || parentProcessor != null;
        var tableUse = new TableUse();
        var tableMapper = new TableMapper();
        var aliasMapper = new AliasMapper(
                tableUse,
                template.tableAliasResolver(),
                template.tableNameResolver(),
                correlate ? parentProcessor.aliasMapper() : null
        );
        var postProcessedElements = postProcessElements(bindingContext.operation(), bindingContext.elements(), aliasMapper, tableMapper);
        var flattened = postProcessedElements.stream()
                .flatMap(e -> e instanceof Wrapped(var elements) ? elements.stream().map(ElementNode::element) : Stream.of(e))
                .toList();
        var queryModel = queryModelFactory.getQueryModel(flattened, tableMapper, aliasMapper).orElse(null);

        TemplateProcessor processor;
        if (parentProcessor == null) {
            processor = new TemplateProcessor(template, this, bindingContext.operation(), modelBuilder, tableUse, aliasMapper, queryModel);
        } else {
            processor = parentProcessor.child(this, bindingContext.operation(), modelBuilder, tableUse, aliasMapper, queryModel);
        }
        return new PreparedTemplate(processor, new CompilationContext(bindingContext.fragments(), postProcessedElements, bindingContext.operation()));
    }

    /**
     * Resolves a {@link BindVars} value into an appropriate element based on operation and surrounding fragment.
     *
     * <p>{@link BindVars} must appear in specific locations, for example after WHERE, SET, or VALUES. This method converts
     * a raw bind vars value into an element that encodes the correct target clause.</p>
     *
     * @param operation         the SQL operation.
     * @param previousFragment  the fragment before the value.
     * @param bindVars          the bind vars value.
     * @return the resolved element.
     * @throws SqlTemplateException if the bind vars value is not valid in the given context.
     */
    private Element resolveBindVarsElement(
            @Nonnull SqlOperation operation,
            @Nonnull String previousFragment,
            @Nonnull BindVars bindVars
    ) throws SqlTemplateException {
        String previous = removeComments(previousFragment, template.dialect()).stripTrailing().toUpperCase();
        return switch (operation) {
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

    /**
     * Resolves a raw object value into an element based on operation and surrounding fragment.
     *
     * <p>Objects after WHERE are treated as where-objects (and must be non-null). Records after VALUES or SET are treated
     * as record value sources. All other objects become parameters.</p>
     *
     * @param operation         the SQL operation.
     * @param previousFragment  the fragment before the value.
     * @param o                 the value to resolve.
     * @return the resolved element.
     * @throws SqlTemplateException if the object is invalid in the given context.
     */
    private Element resolveObjectElement(
            @Nonnull SqlOperation operation,
            @Nonnull String previousFragment,
            @Nullable Object o
    ) throws SqlTemplateException {
        String previous = removeComments(previousFragment, template.dialect()).stripTrailing().toUpperCase();
        return switch (operation) {
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
                    if (o instanceof Data r) {
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
                    if (o instanceof Data r) {
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

    /**
     * Resolves an array value into an element.
     *
     * <p>Arrays are treated like iterables and may be used for IN-like constructs or as parameter collections depending on
     * context.</p>
     *
     * @param operation         the SQL operation.
     * @param previousFragment  the fragment before the value.
     * @param array             the array to resolve.
     * @return the resolved element.
     * @throws SqlTemplateException if the array is invalid in the given context.
     */
    private Element resolveArrayElement(
            @Nonnull SqlOperation operation,
            @Nonnull String previousFragment,
            @Nonnull Object[] array
    ) throws SqlTemplateException {
        return resolveIterableElement(operation, previousFragment, List.of(array));
    }

    /**
     * Resolves an iterable value into an element based on operation and surrounding fragment.
     *
     * <p>Iterables after WHERE become where-iterables. Iterables after VALUES in INSERT must contain only records. In all
     * other contexts they are treated as parameter values.</p>
     *
     * @param operation         the SQL operation.
     * @param previousFragment  the fragment before the value.
     * @param iterable          the iterable to resolve.
     * @return the resolved element.
     * @throws SqlTemplateException if the iterable is invalid in the given context.
     */
    @SuppressWarnings("unchecked")
    private Element resolveIterableElement(
            @Nonnull SqlOperation operation,
            @Nonnull String previousFragment,
            @Nonnull Iterable<?> iterable
    ) throws SqlTemplateException {
        String previous = removeComments(previousFragment, template.dialect()).stripTrailing().toUpperCase();
        return switch (operation) {
            case SELECT, UPDATE, DELETE, UNDEFINED -> {
                if (previous.endsWith("WHERE")) {
                    yield where(iterable);
                }
                yield param(iterable);
            }
            case INSERT -> {
                if (previous.endsWith("VALUES")) {
                    if (StreamSupport.stream(iterable.spliterator(), false).allMatch(it -> it instanceof Data)) {
                        yield values((Iterable<? extends Data>) iterable);
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

    /**
     * Resolves a table type into an element based on operation and surrounding fragments.
     *
     * <p>Depending on where the type is used in the template, it can represent FROM, JOIN, SELECT projection, INTO, UPDATE
     * target, DELETE target, or alias access.</p>
     *
     * @param operation         the SQL operation.
     * @param first             the first statement element (Select/Insert/Update/Delete), if already seen.
     * @param previousFragment  the fragment before the value.
     * @param nextFragment      the fragment after the value.
     * @param recordType        the record type to resolve.
     * @return the resolved element.
     * @throws SqlTemplateException if the type cannot be used in the given context.
     */
    private Element resolveTypeElement(
            @Nonnull SqlOperation operation,
            @Nullable Element first,
            @Nonnull String previousFragment,
            @Nonnull String nextFragment,
            @Nonnull Class<? extends Data> recordType
    ) throws SqlTemplateException {
        if (nextFragment.startsWith(".")) {
            return new Elements.Alias(recordType, CASCADE);
        }
        String next = removeComments(nextFragment, template.dialect()).stripLeading().toUpperCase();
        String previous = removeComments(previousFragment, template.dialect()).stripTrailing().toUpperCase();
        return switch (operation) {
            case SELECT -> {
                if (previous.endsWith("FROM")) {
                    boolean autoJoin = first instanceof Select(var table, var ignore) && isTypePresent(recordType, table);
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

    /**
     * Resolves raw template values into {@link Element} instances.
     *
     * <p>The resolution is context-aware: the operation, fragments, and template configuration influence how values are
     * interpreted. For example, a {@link Data} instance after VALUES becomes a {@code values(record)} element, while the
     * same instance elsewhere becomes a parameter.</p>
     *
     * <p>This method also enforces constraints such as single statement element usage (Select/Insert/Update/Delete) and
     * record support configuration.</p>
     *
     * @param sqlOperation the operation inferred from the template.
     * @param values       the raw template values.
     * @param fragments    the template fragments.
     * @return resolved elements corresponding one-to-one with {@code values}.
     * @throws SqlTemplateException if a value cannot be resolved or violates statement rules.
     */
    List<Element> resolveElements(
            @Nonnull SqlOperation sqlOperation,
            @Nonnull List<?> values,
            @Nonnull List<String> fragments
    ) throws SqlTemplateException {
        List<Element> resolvedValues = new ArrayList<>();
        Element first = null;
        for (int i = 0; i < values.size(); i++) {
            var v = values.get(i);
            var p = fragments.get(i);
            var n = fragments.get(i + 1);
            Element element = switch (v) {
                case Select ignore when sqlOperation != SELECT ->
                        throw new SqlTemplateException("Select element is only allowed for select statements.");
                case Insert ignore when sqlOperation != INSERT ->
                        throw new SqlTemplateException("Insert element is only allowed for insert statements.");
                case Update ignore when sqlOperation != UPDATE ->
                        throw new SqlTemplateException("Update element is only allowed for update statements.");
                case Delete ignore when sqlOperation != DELETE ->
                        throw new SqlTemplateException("Delete element is only allowed for delete statements.");
                case Select it when !template.supportRecords() ->
                        throw new SqlTemplateException("Records are not supported in this configuration: '%s'".formatted(it.table().getSimpleName()));
                case Insert it when !template.supportRecords() ->
                        throw new SqlTemplateException("Records are not supported in this configuration: '%s'".formatted(it.table().getSimpleName()));
                case Update it when !template.supportRecords() ->
                        throw new SqlTemplateException("Records are not supported in this configuration: '%s'".formatted(it.table().getSimpleName()));
                case Delete it when !template.supportRecords() ->
                        throw new SqlTemplateException("Records are not supported in this configuration: '%s'".formatted(it.table().getSimpleName()));
                case Table t when !template.supportRecords() ->
                        throw new SqlTemplateException("Records are not supported in this configuration: '%s'.".formatted(t.table().getSimpleName()));
                case Class<?> c when isRecord(c) && !template.supportRecords() ->
                        throw new SqlTemplateException("Records are not supported in this configuration: '%s'.".formatted(c.getSimpleName()));
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
                case Expression e -> new Cacheable(e);
                case BindVars b -> resolveBindVarsElement(sqlOperation, p, b);
                case Subqueryable t -> new Elements.Subquery(t.getSubquery(), true);
                case Metamodel<?, ?> m when m.isColumn() -> new Column(m, CASCADE);
                case Metamodel<?, ?> ignore -> throw new SqlTemplateException("Metamodel does not reference a column.");
                case Object[] a -> resolveArrayElement(sqlOperation, p, a);
                case Iterable<?> l -> resolveIterableElement(sqlOperation, p, l);
                case Element e -> e;
                case Class<?> c -> resolveTypeElement(sqlOperation, first, p, n, REFLECTION.getDataType(c));
                case Object k when REFLECTION.isSupportedType(k) ->
                        resolveTypeElement(sqlOperation, first, p, n, REFLECTION.getDataType(k));
                case TemplateString ignore -> throw new SqlTemplateException("TemplateString not allowed as string template value.");
                case Stream<?> ignore -> throw new SqlTemplateException("Stream not supported as string template value.");
                case Query ignore -> throw new SqlTemplateException("Query not supported as string template value. Use QueryBuilder instead.");
                case Object o -> resolveObjectElement(sqlOperation, p, o);
                case null -> param(v);
            };
            if (first == null && (element instanceof Select || element instanceof Insert || element instanceof Update || element instanceof Delete)) {
                first = element;
            }
            resolvedValues.add(element);
        }
        return resolvedValues;
    }

    /**
     * Post-processes elements to derive aliases, joins, and mapping availability.
     *
     * <p>This step mutates the element list. In particular, it may replace the FROM element by a {@link Wrapped} node
     * containing the FROM node and additional join nodes, while preserving fragment alignment.</p>
     *
     * @param sqlOperation the statement operation.
     * @param elements     the resolved elements to post-process.
     * @param aliasMapper  alias mapper used for alias derivation and lookups.
     * @param tableMapper  table mapper used for primary key and foreign key mapping availability.
     * @return the mutated list of elements.
     * @throws SqlTemplateException if post-processing fails.
     */
    private List<Element> postProcessElements(
            @Nonnull SqlOperation sqlOperation,
            @Nonnull List<Element> elements,
            @Nonnull AliasMapper aliasMapper,
            @Nonnull TableMapper tableMapper
    ) throws SqlTemplateException {
        var mutableElements = new ArrayList<>(elements);
        switch (sqlOperation) {
            case SELECT -> postProcessSelect(mutableElements, aliasMapper, tableMapper);
            case UPDATE -> postProcessUpdate(mutableElements, aliasMapper, tableMapper);
            case DELETE -> postProcessDelete(mutableElements, aliasMapper, tableMapper);
            default -> postProcessUndefined(mutableElements, aliasMapper, tableMapper);
        }
        return mutableElements;
    }

    /**
     * Updates the element list to include table aliases and optional joins for SELECT statements.
     *
     * <p>If a FROM clause is present and references a table, this method ensures an alias exists for the root table and,
     * when auto-join is enabled, injects joins derived from the record type graph. If no FROM is present, only explicit
     * table aliases are registered.</p>
     *
     * @param mutableElements the mutable element list.
     * @param aliasMapper     alias mapper used for alias registration and generation.
     * @param tableMapper     table mapper used for mapping primary keys and foreign keys.
     * @throws SqlTemplateException if processing fails.
     */
    private void postProcessSelect(
            @Nonnull List<Element> mutableElements,
            @Nonnull AliasMapper aliasMapper,
            @Nonnull TableMapper tableMapper
    ) throws SqlTemplateException {
        final From from = mutableElements.stream()
                .filter(From.class::isInstance)
                .map(From.class::cast)
                .findAny()
                .orElse(null);
        final From effectiveFrom;
        if (from != null && from.source() instanceof TableSource(var table)) {
            validateDataType(table);
            String path = "";
            String alias;
            if (from.alias().isEmpty()) {
                alias = aliasMapper.generateAlias(table, path, template.dialect());
            } else {
                alias = from.alias();
                aliasMapper.setAlias(table, alias, path);
            }
            var projectionQuery = getRecordType(table).getAnnotation(ProjectionQuery.class);
            Source source = projectionQuery != null
                    ? new Elements.TemplateSource(TemplateString.of(projectionQuery.value()))
                    : new TableSource(table);
            effectiveFrom = new From(source, alias, from.autoJoin());
            mutableElements.replaceAll(element -> element instanceof From ? effectiveFrom : element);
            addJoins(table, mutableElements, effectiveFrom, aliasMapper, tableMapper);
        } else {
            addTableAliases(mutableElements, aliasMapper);
        }
    }

    /**
     * Collects custom joins, optionally adds auto-joins derived from record types, and injects joins into the element list.
     *
     * <p>Custom joins are removed from their original positions and injected after the FROM element so join ordering can
     * be normalized. Custom joins that reference tables are converted to explicit table joins with assigned aliases and
     * registered mapping information.</p>
     *
     * @param fromTable       the root FROM table.
     * @param mutableElements the mutable element list.
     * @param from            the effective FROM element.
     * @param aliasMapper     alias mapper used for alias registration and generation.
     * @param tableMapper     table mapper used for mapping primary keys and foreign keys.
     * @throws SqlTemplateException if processing fails.
     */
    private void addJoins(
            @Nonnull Class<? extends Data> fromTable,
            @Nonnull List<Element> mutableElements,
            @Nonnull From from,
            @Nonnull AliasMapper aliasMapper,
            @Nonnull TableMapper tableMapper
    ) throws SqlTemplateException {
        List<Join> customJoins = new ArrayList<>();
        for (ListIterator<Element> it = mutableElements.listIterator(); it.hasNext(); ) {
            Element element = it.next();
            if (element instanceof Table(var table, var alias)) {
                aliasMapper.setAlias(table, alias, null);
            } else if (element instanceof Join j) {
                String path = "";
                if (j instanceof Join(TableSource ts, var ignore1, var ignore2, var ignore3, var ignore4, boolean ignore5)) {
                    String alias;
                    if (j.sourceAlias().isEmpty()) {
                        alias = aliasMapper.generateAlias(ts.table(), null, template.dialect());
                    } else {
                        alias = j.sourceAlias();
                        aliasMapper.setAlias(ts.table(), j.sourceAlias(), null);
                    }
                    var projectionQuery = getRecordType(ts.table()).getAnnotation(ProjectionQuery.class);
                    Source source = projectionQuery != null
                            ? new Elements.TemplateSource(TemplateString.of(projectionQuery.value()))
                            : ts;
                    customJoins.add(new Join(source, alias, j.target(), j.type(), false));
                    tableMapper.mapPrimaryKey(
                            fromTable,
                            ts.table(),
                            alias,
                            findPkField(ts.table()).orElseThrow(
                                    () -> new SqlTemplateException("No primary key found for table %s.".formatted(ts.table().getSimpleName()))
                            ),
                            ts.table(),
                            path
                    );
                    mapForeignKeys(tableMapper, alias, ts.table(), ts.table(), path);
                } else {
                    customJoins.add(j);
                }
                it.set(new Wrapped(List.of()));
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
            List<ElementNode> elementNodes = new ArrayList<>();
            elementNodes.add(new ElementNode(from, false));
            Select select = mutableElements.stream()
                    .filter(Select.class::isInstance)
                    .map(Select.class::cast)
                    .findAny()
                    .orElse(null);
            if (select == null) {
                elementNodes.addAll(joins.stream().map(it -> new ElementNode(it, it.autoJoin())).toList());
            } else {
                for (var join : joins) {
                    if (join instanceof Join(TableSource(var joinTable), var ignore1, var ignore2, var ignore3, var ignore4, var autoJoin)
                            && joinTable == select.table() && join.type().isOuter()) {
                        elementNodes.add(new ElementNode(
                                new Join(new TableSource(joinTable), join.sourceAlias(), join.target(), DefaultJoinType.INNER, autoJoin),
                                autoJoin
                        ));
                    } else {
                        elementNodes.add(new ElementNode(join, join.autoJoin()));
                    }
                }
            }
            mutableElements.replaceAll(element -> element instanceof From ? new Wrapped(elementNodes) : element);
        }
    }

    /**
     * Computes auto-joins for a FROM table and merges them with any custom joins.
     *
     * <p>Auto-joins are derived from {@link FK} fields of the record type graph. Outer joins are moved to the end of the
     * list to improve portability across databases.</p>
     *
     * @param table        the FROM table.
     * @param rootTable    the root table for mapping purposes.
     * @param customJoins  user-defined joins collected from the template.
     * @param aliasMapper  alias mapper used for alias registration and generation.
     * @param tableMapper  table mapper used for mapping foreign keys.
     * @param joins        output list that receives joins.
     * @throws SqlTemplateException if auto-join derivation fails.
     */
    private void addAutoJoins(
            @Nonnull Class<? extends Data> table,
            @Nonnull Class<? extends Data> rootTable,
            @Nonnull List<Join> customJoins,
            @Nonnull AliasMapper aliasMapper,
            @Nonnull TableMapper tableMapper,
            @Nonnull List<Join> joins
    ) throws SqlTemplateException {
        addAutoJoins(getRecordType(table), table, rootTable, List.of(), aliasMapper, tableMapper, joins, null, false);
        joins.addAll(customJoins);
        joins.sort(comparing(join -> join.type().isOuter()));
    }

    /**
     * Recursively derives joins and alias mappings for a record type graph.
     *
     * <p>{@link FK} fields trigger join generation. {@link Ref} foreign keys do not generate a join, but they register
     * mapping information so the ref component can be queried. Nested record fields that are not primary keys and have no
     * explicit converter are treated as inlined and are traversed.</p>
     *
     * @param type        the current record type.
     * @param table       the current table type.
     * @param rootTable   the root table for mapping purposes.
     * @param path        the current record field path.
     * @param aliasMapper alias mapper used for alias lookup and generation.
     * @param tableMapper table mapper used for mapping foreign keys.
     * @param joins       output list that receives derived joins.
     * @param fkName      the alias to use as the join source for nested traversal, or {@code null}.
     * @param outerJoin   whether joins in this subtree must be outer joins due to a nullable ancestor.
     * @throws SqlTemplateException if derivation fails.
     */
    private void addAutoJoins(
            @Nonnull RecordType type,
            @Nonnull Class<? extends Data> table,
            @Nonnull Class<? extends Data> rootTable,
            @Nonnull List<RecordField> path,
            @Nonnull AliasMapper aliasMapper,
            @Nonnull TableMapper tableMapper,
            @Nonnull List<Join> joins,
            @Nullable String fkName,
            boolean outerJoin
    ) throws SqlTemplateException {
        for (var field : type.fields()) {
            var list = new ArrayList<>(path);
            String fkPath = toPathString(path);
            list.add(field);
            var copy = copyOf(list);
            String pkPath = toPathString(copy);
            if (field.isAnnotationPresent(FK.class)) {
                if (Ref.class.isAssignableFrom(field.type())) {
                    String fromAlias;
                    if (fkName == null) {
                        fromAlias = aliasMapper.getAlias(
                                table,
                                fkPath,
                                INNER,
                                template.dialect(),
                                () -> new SqlTemplateException("Table %s for From not found at %s."
                                        .formatted(type.type().getSimpleName(), fkPath))
                        );
                    } else {
                        fromAlias = fkName;
                    }
                    tableMapper.mapForeignKey(table, getRefDataType(field), fromAlias, field, rootTable, fkPath);
                    continue;
                }
                if (!isRecord(field.type())) {
                    throw new SqlTemplateException("FK is only allowed on Ref and record types.");
                }
                if (field.type() == type.type()) {
                    throw new SqlTemplateException(
                            "Self-referencing FK annotation is not allowed: %s. FK must be marked as Ref."
                                    .formatted(type.type().getSimpleName())
                    );
                }
                String fromAlias;
                if (fkName == null) {
                    fromAlias = aliasMapper.getAlias(
                            table,
                            fkPath,
                            INNER,
                            template.dialect(),
                            () -> new SqlTemplateException("Table %s for From not found at path %s."
                                    .formatted(type.type().getSimpleName(), fkPath))
                    );
                } else {
                    fromAlias = fkName;
                }
                RecordType fieldType = getRecordType(field.type());
                String alias = aliasMapper.generateAlias(fieldType.requireDataType(), pkPath, table, fromAlias, template.dialect());
                tableMapper.mapForeignKey(table, fieldType.requireDataType(), fromAlias, field, rootTable, fkPath);

                boolean effectiveOuterJoin = outerJoin || field.nullable();
                JoinType joinType = effectiveOuterJoin ? DefaultJoinType.LEFT : DefaultJoinType.INNER;

                ProjectionQuery query = fieldType.getAnnotation(ProjectionQuery.class);
                Source source = query == null
                        ? new TableSource(fieldType.requireDataType())
                        : new Elements.TemplateSource(TemplateString.of(query.value()));
                Elements.Target target = query == null
                        ? new Elements.TableTarget(table)
                        : getTemplateTarget(
                        fromAlias,
                        alias,
                        field,
                        findPkField(fieldType.type()).orElseThrow(
                                () -> new SqlTemplateException("Failed to find primary key for table %s."
                                        .formatted(fieldType.type().getSimpleName()))
                        )
                );
                joins.add(new Join(source, alias, target, fromAlias, joinType, true));
                addAutoJoins(fieldType, fieldType.requireDataType(), rootTable, copy, aliasMapper, tableMapper, joins, alias, effectiveOuterJoin);
            } else if (isRecord(field.type())) {
                if (field.isAnnotationPresent(PK.class) || getORMConverter(field).isPresent()) {
                    continue;
                }
                String fromAlias;
                if (fkName == null) {
                    fromAlias = aliasMapper.getAlias(
                            table,
                            fkPath,
                            INNER,
                            template.dialect(),
                            () -> new SqlTemplateException("Table %s for From not found at path %s."
                                    .formatted(type.type().getSimpleName(), fkPath))
                    );
                } else {
                    fromAlias = fkName;
                }
                addAutoJoins(getRecordType(field.type()), table, rootTable, copy, aliasMapper, tableMapper, joins, fromAlias, outerJoin);
            }
        }
    }

    /**
     * Builds a join target template for projection queries.
     *
     * <p>The join condition is generated by matching foreign key columns on the source field to primary key columns on the
     * target field. This supports compound keys by joining all corresponding columns.</p>
     *
     * @param fromAlias  alias of the source table.
     * @param toAlias    alias of the target table.
     * @param fromField  source record field containing FK columns.
     * @param toField    target record field containing PK columns.
     * @return a template target representing the join condition.
     * @throws SqlTemplateException if foreign key and primary key column counts do not match.
     */
    @SuppressWarnings("DuplicatedCode")
    private TemplateTarget getTemplateTarget(
            @Nonnull String fromAlias,
            @Nonnull String toAlias,
            @Nonnull RecordField fromField,
            @Nonnull RecordField toField
    ) throws SqlTemplateException {
        var foreignKeys = getForeignKeys(fromField, template.foreignKeyResolver(), template.columnNameResolver());
        var primaryKeys = getPrimaryKeys(toField, template.foreignKeyResolver(), template.columnNameResolver());
        if (foreignKeys.size() != primaryKeys.size()) {
            throw new SqlTemplateException("Mismatch between foreign keys and primary keys count.");
        }
        StringBuilder joinCondition = new StringBuilder();
        for (int i = 0; i < foreignKeys.size(); i++) {
            if (i > 0) {
                joinCondition.append(" AND ");
            }
            joinCondition.append(dialectTemplate.process("\0.\0 = \0.\0", fromAlias, foreignKeys.get(i), toAlias, primaryKeys.get(i)));
        }
        return new TemplateTarget(TemplateString.of(joinCondition.toString()));
    }

    /**
     * Registers aliases for explicit {@link Table} elements found in the statement.
     *
     * @param elements    the statement elements.
     * @param aliasMapper the alias mapper used for registration.
     * @throws SqlTemplateException if alias registration fails.
     */
    private void addTableAliases(@Nonnull List<Element> elements, @Nonnull AliasMapper aliasMapper) throws SqlTemplateException {
        for (Element element : elements) {
            if (element instanceof Table(var table, var alias)) {
                aliasMapper.setAlias(table, alias, null);
            }
        }
    }

    /**
     * Converts a record field path into a dot-separated path string.
     *
     * @param fields the record field path.
     * @return the dot-separated path string.
     */
    static String toPathString(@Nonnull List<RecordField> fields) {
        return fields.stream().map(RecordField::name).collect(joining("."));
    }

    /**
     * Updates the element list to handle alias registration and FK mapping availability for UPDATE statements.
     *
     * <p>Update statements may not support table aliases on all databases. When no explicit alias is provided, the empty
     * alias is registered. Foreign keys of the updated entity are registered for mapping.</p>
     *
     * @param mutableElements the mutable element list.
     * @param aliasMapper     alias mapper used for alias registration.
     * @param tableMapper     table mapper used for mapping foreign keys.
     * @throws SqlTemplateException if processing fails.
     */
    private void postProcessUpdate(
            @Nonnull List<Element> mutableElements,
            @Nonnull AliasMapper aliasMapper,
            @Nonnull TableMapper tableMapper
    ) throws SqlTemplateException {
        final Update update = mutableElements.stream()
                .filter(Update.class::isInstance)
                .map(Update.class::cast)
                .findAny()
                .orElse(null);
        if (update != null) {
            var table = update.table();
            validateDataType(table);
            String path = "";
            String alias;
            if (update.alias().isEmpty()) {
                alias = "";
            } else {
                alias = update.alias();
                aliasMapper.setAlias(table, alias, path);
            }
            mapForeignKeys(tableMapper, alias, table, table, path);
        }
        addTableAliases(mutableElements, aliasMapper);
    }

    /**
     * Updates the element list to include aliases and optional joins for DELETE statements.
     *
     * <p>If a FROM clause is present, an alias is ensured for the FROM table, and joins may be injected similarly to
     * SELECT. If a DELETE element is present, it must match the FROM table. If a DELETE element is used without FROM,
     * this method fails because the target table is ambiguous.</p>
     *
     * @param mutableElements the mutable element list.
     * @param aliasMapper     alias mapper used for alias registration and generation.
     * @param tableMapper     table mapper used for mapping foreign keys.
     * @throws SqlTemplateException if processing fails.
     */
    private void postProcessDelete(
            @Nonnull List<Element> mutableElements,
            @Nonnull AliasMapper aliasMapper,
            @Nonnull TableMapper tableMapper
    ) throws SqlTemplateException {
        final Delete delete = mutableElements.stream()
                .filter(Delete.class::isInstance)
                .map(Delete.class::cast)
                .findAny()
                .orElse(null);
        final From from = mutableElements.stream()
                .filter(From.class::isInstance)
                .map(From.class::cast)
                .filter(f -> f.source() instanceof TableSource)
                .findAny()
                .orElse(null);
        final From effectiveFrom;
        if (from != null && from.source() instanceof TableSource(var table)) {
            validateDataType(table);
            String path = "";
            String alias;
            if (from.alias().isEmpty()) {
                if (delete == null) {
                    aliasMapper.setAlias(table, "", path);
                    alias = "";
                } else {
                    alias = aliasMapper.generateAlias(table, path, template.dialect());
                }
                effectiveFrom = new From(new TableSource(table), alias, from.autoJoin());
                mutableElements.replaceAll(element -> element instanceof From ? effectiveFrom : element);
            } else {
                effectiveFrom = from;
                alias = from.alias();
                aliasMapper.setAlias(table, alias, path);
            }
            if (delete != null) {
                if (delete.table() != table) {
                    throw new SqlTemplateException("Delete entity %s does not match From table %s."
                            .formatted(delete.table().getSimpleName(), table.getSimpleName()));
                }
                if (delete.alias().isEmpty()) {
                    if (!effectiveFrom.alias().isEmpty()) {
                        mutableElements.replaceAll(element -> element instanceof Delete ? delete(table, alias) : element);
                    }
                }
            }
            addJoins(table, mutableElements, effectiveFrom, aliasMapper, tableMapper);
        } else if (delete != null) {
            throw new SqlTemplateException("From element required when using Delete element.");
        } else {
            addTableAliases(mutableElements, aliasMapper);
        }
    }

    /**
     * Post-processes a statement when no primary table is present.
     *
     * <p>This path treats the statement similarly to SELECT and registers aliases and joins based on detected FROM usage.
     * It is used for templates where the operation is unknown or the primary table cannot be inferred.</p>
     *
     * @param mutableElements the mutable element list.
     * @param aliasMapper     alias mapper used for alias registration.
     * @param tableMapper     table mapper used for mapping availability.
     * @throws SqlTemplateException if processing fails.
     */
    private void postProcessUndefined(
            @Nonnull List<Element> mutableElements,
            @Nonnull AliasMapper aliasMapper,
            @Nonnull TableMapper tableMapper
    ) throws SqlTemplateException {
        postProcessSelect(mutableElements, aliasMapper, tableMapper);
    }
}