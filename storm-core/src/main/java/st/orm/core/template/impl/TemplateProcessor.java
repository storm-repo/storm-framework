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
import st.orm.Element;
import st.orm.Metamodel;
import st.orm.ResolveScope;
import st.orm.core.template.Model;
import st.orm.core.template.Sql;
import st.orm.core.template.SqlDialect;
import st.orm.core.template.SqlOperation;
import st.orm.core.template.SqlTemplate;
import st.orm.core.template.SqlTemplate.BindVariables;
import st.orm.core.template.SqlTemplate.NamedParameter;
import st.orm.core.template.SqlTemplate.Parameter;
import st.orm.core.template.SqlTemplate.PositionalParameter;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.TemplateString;
import st.orm.core.template.impl.BindHint.NoBindHint;
import st.orm.core.template.impl.SqlTemplateImpl.Wrapped;
import st.orm.core.template.impl.TemplatePreparation.BindingContext;
import st.orm.core.template.impl.TemplatePreparation.CompilationContext;
import st.orm.core.template.impl.TemplatePreparation.PreparedTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.MissingFormatArgumentException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.join;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static st.orm.core.template.impl.ElementRouter.getElementProcessor;
import static st.orm.core.template.impl.RecordValidation.validateParameters;
import static st.orm.core.template.impl.SqlParser.hasWhereClause;

/**
 * Compiles and binds a {@link SqlTemplate} in two phases.
 *
 * <h2>Compilation</h2>
 * <p>During compilation, each {@link Element} is compiled using its element processor and a bind hint is recorded in a
 * deterministic traversal order. The resulting SQL string is stored once and reused for later bindings.</p>
 *
 * <h2>Binding</h2>
 * <p>During binding, a new {@link BindingSession} is created per invocation. The session binds runtime values while
 * consuming the previously recorded bind hints in the same order they were produced during compilation.</p>
 *
 * <p>The processor instance is safe to cache and reuse across threads. All mutable binding state is kept inside
 * {@link BindingSession}. Compile-time state is produced once and treated as immutable after compilation completes.</p>
 *
 * @since 1.8
 */
class TemplateProcessor {

    /**
     * Placeholder used to reserve a parent hint slot before nested compilation appends its hints.
     *
     * <p>The placeholder is replaced with the compiled element's hint after compilation returns. Encountering this value
     * during binding indicates an incomplete compilation.</p>
     */
    private static final BindHint PLACEHOLDER_HINT = null;

    /**
     * The template being processed.
     */
    private final SqlTemplate template;

    /**
     * Preparation component used to preprocess and prepare nested templates for compilation and binding.
     */
    private final TemplatePreparation templatePreparation;

    /**
     * Operation represented by the compiled SQL (for example SELECT, UPDATE).
     */
    private final SqlOperation operation;

    /**
     * Builder for resolving ORM models used by element processors.
     */
    private final ModelBuilder modelBuilder;

    /**
     * Tracks table usage to support alias correlation and reference checks.
     */
    private final TableUse tableUse;

    /**
     * Resolves and allocates table aliases used during compilation.
     */
    private final AliasMapper aliasMapper;

    /**
     * Optional query model used for column and expression compilation when a primary table is present.
     */
    private final QueryModel queryModel;

    /**
     * Dialect helpers used for rendering dialect-specific fragments.
     */
    private final SqlDialectTemplate dialectTemplate;

    /**
     * Shared tape of bind hints, appended during compilation in traversal order.
     *
     * <p>This list is treated as immutable after compilation completes. Binding sessions consume hints using a cursor.</p>
     */
    private final List<BindHint> bindHints;

    /**
     * Compile-time only: arity of each encountered {@link BindVars} occurrence.
     *
     * <p>The order matches compilation traversal. Binding uses a cursor to assign positional indexes for bind vars.</p>
     */
    private final List<Integer> bindVarsCounts;

    /**
     * Compile-time only: expected number of positional parameters produced by compilation.
     *
     * <p>This value is validated against runtime bindings when assembling the final {@link Sql} object.</p>
     */
    private final AtomicInteger positionalParameterCount;

    /**
     * Compile-time only: index used to generate unique named parameter identifiers when named parameters are supported.
     */
    private final AtomicInteger nameIndex;

    /**
     * Compile-time only: set if the compiled template requires version-aware binding behavior.
     */
    @Nullable
    private Boolean versionAware;

    /**
     * Compile-time only: generated keys configured during compilation (if any).
     */
    @Nullable
    private List<String> generatedKeys;

    /**
     * Compile-time only: type affected by INSERT, UPDATE, or DELETE operation.
     */
    @Nullable
    private Class<? extends Data> affectedType;

    /**
     * The compiled SQL string.
     *
     * <p>This is set exactly once by {@link #compile(CompilationContext, boolean)}.</p>
     */
    @Nullable
    private String sql;

    /**
     * Compile-time only: whether the compiled template requires binding.
     */
    private boolean requiresBinding;

    /**
     * Compiler implementation used during compilation of elements and nested templates.
     */
    private final TemplateCompiler compiler;

    /**
     * Creates a new processor for the given template and compilation context.
     *
     * <p>This constructor initializes fresh compile-time state and a dialect template. Nested processors created via
     * {@link #child(TemplatePreparation, SqlOperation, ModelBuilder, TableUse, AliasMapper, QueryModel)} share the same
     * compile-time state and hint tape.</p>
     *
     * @param template            the SQL template to process.
     * @param templatePreparation the preparation component for nested templates.
     * @param operation           the operation represented by the resulting SQL.
     * @param modelBuilder        model builder used by element processors.
     * @param tableUse            table usage tracker for alias correlation.
     * @param aliasMapper         alias mapper for metamodel-based alias resolution.
     * @param queryModel          optional query model for primary table operations.
     */
    TemplateProcessor(
            @Nonnull SqlTemplate template,
            @Nonnull TemplatePreparation templatePreparation,
            @Nonnull SqlOperation operation,
            @Nonnull ModelBuilder modelBuilder,
            @Nonnull TableUse tableUse,
            @Nonnull AliasMapper aliasMapper,
            @Nullable QueryModel queryModel
    ) {
        this(template, templatePreparation, operation, modelBuilder, tableUse, aliasMapper, queryModel,
                new SqlDialectTemplate(template.dialect()),
                new ArrayList<>(),
                new AtomicInteger(),
                new AtomicInteger());
    }

    /**
     * Internal constructor used for root and child processors.
     *
     * <p>Child processors share compile-time state with the root processor by receiving the same instances for the hint
     * tape and counters.</p>
     */
    private TemplateProcessor(
            @Nonnull SqlTemplate template,
            @Nonnull TemplatePreparation templatePreparation,
            @Nonnull SqlOperation operation,
            @Nonnull ModelBuilder modelBuilder,
            @Nonnull TableUse tableUse,
            @Nonnull AliasMapper aliasMapper,
            @Nullable QueryModel queryModel,
            @Nonnull SqlDialectTemplate dialectTemplate,
            @Nonnull List<BindHint> bindHints,
            @Nonnull AtomicInteger positionalParameterCount,
            @Nonnull AtomicInteger nameIndex
    ) {
        this.template = template;
        this.templatePreparation = templatePreparation;
        this.operation = operation;
        this.modelBuilder = modelBuilder;
        this.tableUse = tableUse;
        this.aliasMapper = aliasMapper;
        this.queryModel = queryModel;
        this.dialectTemplate = dialectTemplate;
        this.bindHints = bindHints;
        this.bindVarsCounts = new ArrayList<>();
        this.positionalParameterCount = positionalParameterCount;
        this.nameIndex = nameIndex;
        this.compiler = new TemplateCompilerImpl();
    }

    /**
     * Returns the alias mapper used by this processor.
     *
     * @return the alias mapper.
     */
    public AliasMapper aliasMapper() {
        return aliasMapper;
    }

    private void checkState(boolean frozen) {
        if (frozen) {
            if (sql == null) {
                throw new IllegalStateException("Template processor is not frozen.");
            }
        } else {
            if (sql != null) {
                throw new IllegalStateException("Template processor is already frozen.");
            }
        }
    }

    /**
     * Creates a child processor used for nested compilation.
     *
     * <p>The child shares compile-time mutable state with the root processor, including bind hints and counters. Binding
     * state is not shared, because binding always happens through a {@link BindingSession} created per invocation.</p>
     *
     * @param templatePreparation preparation component to use for nested templates.
     * @param operation           nested operation context.
     * @param modelBuilder        model builder for nested compilation.
     * @param tableUse            nested table usage tracker.
     * @param aliasMapper         nested alias mapper.
     * @param queryModel          nested query model.
     * @return a child processor sharing compile-time state with the root processor.
     */
    TemplateProcessor child(
            TemplatePreparation templatePreparation,
            SqlOperation operation,
            ModelBuilder modelBuilder,
            TableUse tableUse,
            AliasMapper aliasMapper,
            QueryModel queryModel
    ) {
        // Share compilation state across nested compilation.
        // Binding state is NOT shared; it is created per bind invocation in BindingSession.
        return new TemplateProcessor(
                template,
                templatePreparation,
                operation,
                modelBuilder,
                tableUse,
                aliasMapper,
                queryModel,
                dialectTemplate,
                bindHints,
                positionalParameterCount,
                nameIndex
        ) {
            @Override
            protected void mapPositionalParameter() {
                TemplateProcessor.this.mapPositionalParameter();
            }

            @Override
            protected void mapNamedParameter() {
                TemplateProcessor.this.mapNamedParameter();
            }

            @Override
            protected void mapBindVars(int count) {
                TemplateProcessor.this.mapBindVars(count);
            }

            @Override
            protected void markVersionAware() {
                TemplateProcessor.this.markVersionAware();
            }

            @Override
            protected void setGeneratedKeys(@Nonnull List<String> keys) {
                TemplateProcessor.this.setGeneratedKeys(keys);
            }
        };
    }

    /**
     * Records that a positional parameter was produced by compilation.
     */
    protected void mapPositionalParameter() {
        checkState(false);
        requiresBinding = true;
        positionalParameterCount.incrementAndGet();
    }

    /**
     * Records that a named parameter was produced by compilation.
     */
    protected void mapNamedParameter() {
        checkState(false);
        requiresBinding = true;
    }

    /**
     * Records the arity of a {@link BindVars} segment produced during compilation.
     *
     * @param count the number of values expected for the bind vars segment.
     */
    protected void mapBindVars(int count) {
        checkState(false);
        if (count == 0) {
            throw new IllegalArgumentException("Bind vars segment cannot be empty.");
        }
        requiresBinding = true;
        bindVarsCounts.add(count);
    }

    /**
     * Marks this compiled template as version-aware.
     *
     * <p>This flag is set once during compilation by element processors that require version checks.</p>
     *
     * @throws IllegalStateException if version awareness was already set.
     */
    protected void markVersionAware() {
        checkState(false);
        if (versionAware != null) {
            throw new IllegalStateException("Version aware already set.");
        }
        versionAware = true;
    }

    /**
     * Sets the list of generated keys configured during compilation.
     *
     * @param keys the generated key column names.
     * @throws IllegalStateException if generated keys were already set.
     */
    protected void setGeneratedKeys(@Nonnull List<String> keys) {
        checkState(false);
        if (generatedKeys != null) {
            throw new IllegalStateException("Generated keys already set.");
        }
        generatedKeys = List.copyOf(keys);
    }

    /**
     * Stores the compiled SQL string.
     *
     * @param sql the compiled SQL.
     * @throws IllegalStateException if SQL was already set.
     */
    private void freeze(@Nonnull String sql) {
        checkState(false);
        this.sql = sql;
    }

    /**
     * Compiles a single (already-unwrapped) element and records its bind hint.
     *
     * <p>Hint ordering is fixed by reserving a slot before compilation so that any nested compilation appends its hints
     * after the parent slot. After compilation returns, the reserved slot is replaced by the compiled element's hint.</p>
     *
     * @param element    the element to compile.
     * @param synthetic  whether the element is synthetic and should not produce a bind hint.
     * @return the compiled element wrapper.
     * @throws SqlTemplateException if compilation fails.
     */
    private CompiledElement compile(@Nonnull Element element, boolean synthetic) throws SqlTemplateException {
        checkState(false);
        int hintIndex = bindHints.size();
        if (!synthetic) {
            bindHints.add(PLACEHOLDER_HINT);
        }
        var compiledElement = getElementProcessor(element).compile(element, compiler());
        if (synthetic) {
            if (compiledElement.bindHint() != NoBindHint.INSTANCE) {
                throw new IllegalStateException("Synthetic element cannot have bind hint.");
            }
        } else {
            bindHints.set(hintIndex, compiledElement.bindHint());
        }
        return compiledElement;
    }

    /**
     * Compiles the template fragments and elements into a single SQL string.
     *
     * <p>This method walks fragments and elements in order, compiling each element and appending its SQL representation.
     * It also records bind hints and positional parameter expectations as part of compilation.</p>
     *
     * <p>If {@code subquery} is {@code true}, the resulting SQL may be indented to align with surrounding formatting.</p>
     *
     * @param context  compilation context holding fragments and elements.
     * @param subquery whether the compilation target is a nested subquery.
     * @return the compiled SQL string.
     * @throws SqlTemplateException if compilation fails or formatting placeholders are invalid.
     */
    String compile(@Nonnull CompilationContext context, boolean subquery) throws SqlTemplateException {
        checkState(false);
        var fragments = context.fragments();
        var elements = context.elements();
        var parts = new ArrayList<String>();
        var rawSql = new StringBuilder();
        var compilers = new ArrayList<DelayedCompilation>();
        for (int i = 0, size = fragments.size(); i < size; i++) {
            String fragment = fragments.get(i);
            try {
                if (i < elements.size()) {
                    Element element = elements.get(i);  // Use wrapped elements here to maintain fragment-value order.
                    var compiledElements = new ArrayList<CompiledElement>();
                    if (element instanceof Wrapped(var wrapped)) {
                        for (var e : wrapped) {
                            compiledElements.add(compile(e.element(), e.synthetic()));
                        }
                    } else {
                        compiledElements.add(compile(element, false));
                    }
                    compilers.add(() -> {
                        rawSql.append(fragment);
                        if (element instanceof Elements.Param) {
                            assert compiledElements.size() == 1;
                            parts.add(rawSql.toString());
                            rawSql.setLength(0);
                        }
                        for (CompiledElement compiledElement : compiledElements) {
                            rawSql.append(compiledElement.get());
                        }
                    });
                } else {
                    compilers.add(() -> {
                        rawSql.append(fragment);
                        parts.add(rawSql.toString());
                    });
                }
            } catch (MissingFormatArgumentException ex) {
                throw new SqlTemplateException(
                        "Invalid number of argument placeholders found. Template appears to specify custom %s " +
                                "placeholders.",
                        ex
                );
            }
        }
        for (var compiler : compilers) {
            compiler.compile();
        }
        String sql = String.join("", parts);
        if (subquery && !sql.startsWith("\n") && sql.contains("\n")) {
            sql = "\n" + sql.indent(2);
        }
        freeze(sql);
        return sql;
    }

    /**
     * Delays compilation side effects until the full fragment and element traversal order is established.
     *
     * <p>This allows the compilation loop to preserve correct fragment boundaries for parameter segmentation.</p>
     */
    private interface DelayedCompilation {
        /**
         * Executes the delayed compilation step.
         *
         * @throws SqlTemplateException if compilation fails.
         */
        void compile() throws SqlTemplateException;
    }

    /**
     * Binds runtime values and assembles the final {@link Sql} object for a compiled template.
     *
     * <p>This method is reusable and thread-safe, because it creates a new {@link BindingSession} for every invocation.
     * The session binds values while consuming bind hints recorded during compilation.</p>
     *
     * @param context binding context holding flattened elements.
     * @return the assembled SQL object containing the SQL string and bound parameters.
     * @throws SqlTemplateException if binding fails or parameter validation fails.
     * @throws IllegalStateException if called before {@link #compile(CompilationContext, boolean)}.
     */
    Sql bind(@Nonnull BindingContext context) throws SqlTemplateException {
        checkState(true);
        assert sql != null;
        var session = new BindingSession();
        if (requiresBinding) {
            session.bindElements(context);
            session.assertAllHintsConsumed();
        }
        validateParameters(session.parameters, positionalParameterCount.getPlain());
        return new SqlImpl(
                operation,
                sql,
                session.parameters,
                ofNullable(session.bindVariables),
                generatedKeys != null ? generatedKeys : List.of(),
                ofNullable(affectedType),
                versionAware != null && versionAware,
                checkSafety(sql, operation)
        );
    }

    /**
     * Performs a basic safety check for potentially unsafe statements.
     *
     * <p>UPDATE and DELETE statements without a WHERE clause are flagged as potentially unsafe. The returned message is
     * informational and can be used by callers to warn users or reject execution.</p>
     *
     * @param sql       the compiled SQL to analyze.
     * @param operation the operation represented by the SQL.
     * @return an optional warning message when the statement is potentially unsafe.
     */
    private Optional<String> checkSafety(@Nonnull String sql, @Nonnull SqlOperation operation) {
        return switch (operation) {
            case SELECT, INSERT, UNDEFINED -> empty();
            case UPDATE, DELETE -> {
                if (!hasWhereClause(sql, template.dialect())) {
                    yield Optional.of("%s without a WHERE clause is potentially unsafe.".formatted(operation));
                }
                yield empty();
            }
        };
    }

    /**
     * Returns the compiler used for element compilation.
     *
     * @return the template compiler.
     * @throws IllegalStateException if SQL has already been finalized.
     */
    private TemplateCompiler compiler() {
        if (sql != null) {
            throw new IllegalStateException("SQL already set.");
        }
        return compiler;
    }

    /**
     * Template compiler used during compilation of elements and nested templates.
     *
     * <p>This compiler delegates to the surrounding {@link TemplateProcessor} for shared compile-time state such as bind
     * hint recording and positional parameter counting.</p>
     */
    private final class TemplateCompilerImpl implements TemplateCompiler {

        /**
         * Returns the SQL template.
         *
         * @return the SQL template.
         */
        @Override
        public SqlTemplate template() {
            return template;
        }

        /**
         * Returns the dialect used for SQL rendering.
         *
         * @return the SQL dialect.
         */
        @Override
        public SqlDialect dialect() {
            return template.dialect();
        }

        /**
         * Returns dialect-specific helpers used for template rendering.
         *
         * @return the dialect template helper.
         */
        @Override
        public SqlDialectTemplate dialectTemplate() {
            return dialectTemplate;
        }

        /**
         * Resolves a model for the given data type.
         *
         * @param type the data type.
         * @param <T>  the data type.
         * @param <ID> the identifier type.
         * @return the model for the given type.
         * @throws UncheckedSqlTemplateException if model resolution fails.
         */
        @Override
        public <T extends Data, ID> Model<T, ID> getModel(@Nonnull Class<T> type) {
            try {
                return modelBuilder.build(type, false);
            } catch (SqlTemplateException e) {
                throw new UncheckedSqlTemplateException(e);
            }
        }

        /**
         * Returns the query model if one is available.
         *
         * @return the query model, if present.
         */
        @Override
        public Optional<QueryModel> findQueryModel() {
            return Optional.ofNullable(queryModel);
        }

        /**
         * Returns the query model, throwing if none is available.
         *
         * @return the query model.
         * @throws UncheckedSqlTemplateException if no primary table was specified.
         */
        @Override
        public QueryModel getQueryModel() {
            return findQueryModel().orElseThrow(
                    () -> new UncheckedSqlTemplateException(new SqlTemplateException("No primary table specified."))
            );
        }

        /**
         * Checks whether a given table alias is referenced in the current compilation context.
         *
         * @param table the table type.
         * @param alias the table alias.
         * @return {@code true} if the alias is referenced.
         */
        @Override
        public boolean isReferenced(@Nonnull Class<? extends Data> table, @Nonnull String alias) {
            return tableUse.isReferenced(table, alias);
        }

        /**
         * Resolves the SQL alias for the given metamodel and resolve scope.
         *
         * @param metamodel the metamodel to resolve an alias for.
         * @param scope     resolve scope controlling join visibility.
         * @return the resolved alias.
         * @throws UncheckedSqlTemplateException if alias resolution fails.
         */
        @Override
        public String getAlias(@Nonnull Metamodel<?, ?> metamodel, @Nonnull ResolveScope scope) {
            try {
                return aliasMapper.getAlias(metamodel, scope, template.dialect());
            } catch (SqlTemplateException e) {
                throw new UncheckedSqlTemplateException(e);
            }
        }

        /**
         * Allocates or reuses an alias for the given table.
         *
         * @param table the table type.
         * @param alias preferred alias.
         * @return the actual alias used.
         * @throws UncheckedSqlTemplateException if alias allocation fails.
         */
        @Override
        public String useAlias(@Nonnull Class<? extends Data> table, @Nonnull String alias) {
            try {
                return aliasMapper.useAlias(table, alias);
            } catch (SqlTemplateException e) {
                throw new UncheckedSqlTemplateException(e);
            }
        }

        /**
         * Compiles a nested template string and returns its SQL fragment.
         *
         * <p>This method performs preprocessing and preparation to ensure bind hints are recorded on the shared hint tape
         * and that nested compilation correlates correctly with the outer query when requested.</p>
         *
         * @param templateString the nested template to compile.
         * @param correlate      whether the nested template should correlate with the outer query.
         * @return the compiled SQL fragment.
         * @throws UncheckedSqlTemplateException if compilation fails.
         */
        @Override
        public String compile(@Nonnull TemplateString templateString, boolean correlate) {
            try {
                var preparedTemplate = templatePreparation.preprocess(templateString);
                var preparedProcessor = templatePreparation.prepare(preparedTemplate, TemplateProcessor.this, correlate);
                return preparedProcessor.processor().compile(preparedProcessor.context(), true);
            } catch (SqlTemplateException e) {
                throw new UncheckedSqlTemplateException(e);
            }
        }

        /**
         * Compiles an element into its SQL representation.
         *
         * @param element the element to compile.
         * @return the compiled SQL fragment.
         * @throws UncheckedSqlTemplateException if compilation fails.
         */
        @Override
        public String compile(@Nonnull Element element) {
            try {
                return getElementProcessor(element).compile(element, this).get();
            } catch (SqlTemplateException e) {
                throw new UncheckedSqlTemplateException(e);
            }
        }

        /**
         * Returns whether the current compilation is version-aware.
         *
         * @return {@code true} if version-aware.
         */
        @Override
        public boolean isVersionAware() {
            return versionAware != null && versionAware;
        }

        /**
         * Marks the compiled template as version-aware.
         */
        @Override
        public void setVersionAware() {
            markVersionAware();
        }

        /**
         * Configures generated keys for the compiled template.
         *
         * @param generatedKeys the generated key column names.
         */
        @Override
        public void setGeneratedKeys(@Nonnull List<String> generatedKeys) {
            TemplateProcessor.this.setGeneratedKeys(generatedKeys);
        }

        /**
         * Maps a runtime value to a SQL parameter placeholder.
         *
         * <p>If named parameters are supported, a fresh name is generated. If positional-only mode is active, a question
         * mark placeholder is produced and compile-time positional parameter count is incremented. When collection
         * expansion is enabled, arrays and iterables may expand into multiple placeholders or literals.</p>
         *
         * @param value the value to map.
         * @return the SQL parameter placeholder or an inlined literal.
         */
        @Override
        public String mapParameter(@Nullable Object value) {
            if (!template.positionalOnly()) {
                String name = "_p%d".formatted(nameIndex.getAndIncrement());
                return mapParameter(name, value);
            }
            return switch (value) {
                case Object[] array when template.expandCollection() -> mapArgs(List.of(array), template.inlineParameters());
                case Iterable<?> it when template.expandCollection() -> mapArgs(it, template.inlineParameters());
                case Object[] ignore -> throw new UncheckedSqlTemplateException(new SqlTemplateException("Array parameters not supported."));
                case Iterable<?> ignore ->
                        throw new UncheckedSqlTemplateException(new SqlTemplateException("Collection parameters not supported."));
                case null, default -> {
                    if (template.inlineParameters()) {
                        yield toLiteral(value);
                    }
                    TemplateProcessor.this.mapPositionalParameter();
                    yield "?";
                }
            };
        }

        /**
         * Maps a runtime value to a named SQL parameter placeholder.
         *
         * @param name  the parameter name.
         * @param value the runtime value.
         * @return the named SQL parameter placeholder.
         * @throws UncheckedSqlTemplateException if the template is configured for positional-only parameters.
         */
        @Override
        public String mapParameter(@Nonnull String name, @Nullable Object value) {
            if (template.positionalOnly()) {
                throw new UncheckedSqlTemplateException(new SqlTemplateException("Named parameters not supported."));
            }
            TemplateProcessor.this.mapNamedParameter();
            return ":%s".formatted(name);
        }

        /**
         * Records a {@link BindVars} segment length.
         *
         * @param bindVarsCount the number of positional placeholders to be produced by the bind vars segment.
         */
        @Override
        public void mapBindVars(int bindVarsCount) {
            TemplateProcessor.this.mapBindVars(bindVarsCount);
        }

        /**
         * Sets the type affected by an INSERT, UPDATE, or DELETE operation.
         *
         * @param type the type affected by the operation.
         */
        @Override
        public void setAffectedType(@Nonnull Class<? extends Data> type) {
            if (affectedType != null) {
                throw new IllegalStateException("Affected type already set.");
            }
            affectedType = type;
        }

        /**
         * Maps an iterable into an argument list suitable for IN clauses.
         *
         * @param iterable the iterable to expand.
         * @param inline   whether to inline each value as a literal.
         * @return a comma-separated list of placeholders or literals.
         */
        private String mapArgs(@Nonnull Iterable<?> iterable, boolean inline) {
            List<String> args = new ArrayList<>();
            for (var v : iterable) {
                if (inline) {
                    args.add(toLiteral(v));
                } else {
                    TemplateProcessor.this.mapPositionalParameter();
                    args.add("?");
                }
                args.add(", ");
            }
            if (!args.isEmpty()) {
                args.removeLast();
            }
            return join("", args);
        }

        /**
         * Converts a supported value into a SQL literal suitable for inline parameter rendering.
         *
         * <p>This method performs basic string escaping for backslashes and single quotes.</p>
         *
         * @param dbValue the value to convert.
         * @return the SQL literal.
         */
        private static String toLiteral(@Nullable Object dbValue) {
            return switch (dbValue) {
                case null -> "NULL";
                case Short s -> s.toString();
                case Integer i -> i.toString();
                case Long l -> l.toString();
                case Float f -> f.toString();
                case Double d -> d.toString();
                case Byte b -> b.toString();
                case Boolean b -> b ? "TRUE" : "FALSE";
                case String s -> "'%s'".formatted(s.replace("\\", "\\\\").replace("'", "''"));
                case java.sql.Date d -> "'%s'".formatted(d);
                case java.sql.Time t -> "'%s'".formatted(t);
                case java.sql.Timestamp t -> "'%s'".formatted(t);
                case Enum<?> e -> "'%s'".formatted(e.name().replace("\\", "\\\\").replace("'", "''"));
                default -> {
                    String str = dbValue.toString().replace("\\", "\\\\").replace("'", "''");
                    yield "'%s'".formatted(str);
                }
            };
        }
    }

    /**
     * A single binding session created per {@link #bind(BindingContext)} invocation.
     *
     * <p>The session owns all mutable binding state, including bound parameters and cursor positions for bind hint and
     * bind vars consumption. Nested attached binding uses the same session and advances the same cursors.</p>
     */
    private final class BindingSession implements TemplateBinder {

        /**
         * Collected parameters in binding order.
         */
        private final ArrayList<Parameter> parameters = new ArrayList<>();

        /**
         * The bind variables instance used for this binding invocation, if any.
         */
        @Nullable
        private BindVariables bindVariables;

        /**
         * Per-session index used to generate unique named parameter names.
         */
        private int nameIndex;

        /**
         * Cursor into the shared bind hint tape.
         */
        private int hintCursor;

        /**
         * Cursor into the recorded bind vars counts.
         */
        private int bindVarsCursor;

        /**
         * Binds all elements in the given binding context, consuming bind hints for each non-synthetic element.
         *
         * @param context the binding context.
         * @throws SqlTemplateException if binding fails.
         */
        void bindElements(@Nonnull BindingContext context) throws SqlTemplateException {
            for (Element element : context.elements()) {
                if (element instanceof Wrapped(var wrapped)) {
                    for (var e : wrapped) {
                        if (!e.synthetic()) {
                            var hint = nextHint();
                            getElementProcessor(e.element()).bind(e.element(), this, hint);
                        }
                    }
                } else {
                    var hint = nextHint();
                    getElementProcessor(element).bind(element, this, hint);
                }
            }
            if (hintCursor != bindHints.size()) {
                throw new UncheckedSqlTemplateException(new SqlTemplateException(
                        "Bind hint consumption mismatch. Used %d hints but %d were produced."
                                .formatted(hintCursor, bindHints.size())));
            }
        }

        /**
         * Returns the next bind hint from the shared hint tape.
         *
         * @return the next bind hint.
         * @throws IllegalStateException if no hint is available or a placeholder is encountered.
         */
        private BindHint nextHint() {
            if (hintCursor >= bindHints.size()) {
                throw new IllegalStateException(
                        "Not enough bind hints. Cursor=%d but only %d hints available."
                                .formatted(hintCursor, bindHints.size()));
            }
            var hint = bindHints.get(hintCursor++);
            if (hint == PLACEHOLDER_HINT) {
                throw new IllegalStateException(
                        "Bind hint placeholder encountered at index %d. Compilation did not finalize hints."
                                .formatted(hintCursor - 1));
            }
            return hint;
        }

        /**
         * Asserts that all bind hints were consumed during binding.
         *
         * @throws IllegalStateException if the hint cursor does not match the number of produced hints.
         */
        private void assertAllHintsConsumed() {
            if (hintCursor != bindHints.size()) {
                throw new IllegalStateException(
                        "Bind hint consumption mismatch. Used %d hints but %d were produced."
                                .formatted(hintCursor, bindHints.size()));
            }
        }

        /**
         * Binds a prepared nested template using attached binding semantics.
         *
         * <p>Attached binding consumes bind hints from the shared tape, because nested compilation appended its hints to
         * the same tape.</p>
         *
         * @param preparedTemplate the prepared nested template.
         * @throws SqlTemplateException if binding fails.
         */
        private void bindPreparedAttached(@Nonnull PreparedTemplate preparedTemplate) throws SqlTemplateException {
            for (var element : preparedTemplate.context().elements()) {
                if (element instanceof Wrapped(var wrapped)) {
                    for (var e : wrapped) {
                        if (!e.synthetic()) {
                            var hint = nextHint();
                            getElementProcessor(e.element()).bind(e.element(), this, hint);
                        }
                    }
                } else {
                    var hint = nextHint();
                    getElementProcessor(element).bind(element, this, hint);
                }
            }
        }

        /**
         * Resolves a model for the given data type.
         *
         * @param type the data type.
         * @param <T>  the data type.
         * @param <ID> the identifier type.
         * @return the model for the given type.
         */
        @Override
        public <T extends Data, ID> Model<T, ID> getModel(@Nonnull Class<T> type) {
            try {
                return modelBuilder.build(type, false);
            } catch (SqlTemplateException e) {
                throw new UncheckedSqlTemplateException(e);
            }
        }

        /**
         * Returns the query model if present.
         *
         * @return the query model, if present.
         */
        @Override
        public Optional<QueryModel> findQueryModel() {
            return Optional.ofNullable(queryModel);
        }

        /**
         * Returns the query model, throwing if none is present.
         *
         * @return the query model.
         */
        @Override
        public QueryModel getQueryModel() {
            return findQueryModel().orElseThrow(
                    () -> new UncheckedSqlTemplateException(new SqlTemplateException("No primary table specified."))
            );
        }

        /**
         * Returns whether the compiled template is version-aware.
         *
         * @return {@code true} if version-aware.
         */
        @Override
        public boolean isVersionAware() {
            return versionAware != null && versionAware;
        }

        /**
         * Binds a value as a parameter.
         *
         * <p>In named parameter mode, a fresh name is generated for each parameter. In positional mode, a positional
         * parameter is added unless inline parameter rendering is enabled. Collection expansion is handled when enabled
         * by the template configuration.</p>
         *
         * @param value the value to bind.
         */
        @Override
        public void bindParameter(@Nullable Object value) {
            if (!template.positionalOnly()) {
                String name = "_p%d".formatted(nameIndex++);
                bindParameter(name, value);
                return;
            }
            switch (value) {
                case Object[] array when template.expandCollection() -> bindArgs(List.of(array), template.inlineParameters());
                case Iterable<?> it when template.expandCollection() -> bindArgs(it, template.inlineParameters());
                case Object[] ignore -> throw new UncheckedSqlTemplateException(new SqlTemplateException("Array parameters not supported."));
                case Iterable<?> ignore ->
                        throw new UncheckedSqlTemplateException(new SqlTemplateException("Collection parameters not supported."));
                case null, default -> {
                    if (!template.inlineParameters()) {
                        parameters.add(new PositionalParameter(parameters.size() + 1, value));
                    }
                }
            }
        }

        /**
         * Binds a named parameter.
         *
         * @param name  the parameter name.
         * @param value the parameter value.
         */
        @Override
        public void bindParameter(@Nonnull String name, @Nullable Object value) {
            if (template.positionalOnly()) {
                throw new UncheckedSqlTemplateException(new SqlTemplateException("Named parameters not supported."));
            }
            parameters.add(new NamedParameter(name, value));
        }

        /**
         * Binds a nested template using attached binding semantics.
         *
         * <p>Attached binding consumes bind hints because the nested template contributed hints during compilation.</p>
         *
         * @param templateString the nested template to bind.
         * @param correlate      whether correlation rules apply to the nested template.
         */
        @Override
        public void bind(@Nonnull TemplateString templateString, boolean correlate) {
            try {
                var context = templatePreparation.preprocess(templateString);
                var preparedTemplate = templatePreparation.prepare(context, TemplateProcessor.this, correlate);
                bindPreparedAttached(preparedTemplate);
            } catch (SqlTemplateException e) {
                throw new UncheckedSqlTemplateException(e);
            }
        }

        /**
         * Binds an element using detached binding semantics.
         *
         * <p>Detached binding does not consume bind hints. It is used when binding an element outside of the compiled
         * element traversal context.</p>
         *
         * @param element the element to bind.
         */
        @Override
        public void bind(@Nonnull Element element) {
            try {
                getElementProcessor(element).bind(element, this, NoBindHint.INSTANCE);
            } catch (SqlTemplateException e) {
                throw new UncheckedSqlTemplateException(e);
            }
        }

        /**
         * Sets the {@link BindVars} instance for this session and returns a factory that binds values for one bind vars
         * segment.
         *
         * <p>The factory enforces the expected arity recorded during compilation. The start position is computed based on
         * already bound parameters and previously consumed bind vars segments.</p>
         *
         * @param vars the bind vars instance to use.
         * @return a parameter factory for a single bind vars segment.
         */
        @Override
        public ParameterFactory setBindVars(@Nonnull BindVars vars) {
            var current = bindVariables;
            if (current != null && current != vars) {
                throw new UncheckedSqlTemplateException(new SqlTemplateException("Multiple BindVars instances not supported."));
            }
            if (vars instanceof BindVarsImpl bindVars) {
                bindVariables = bindVars;
            } else {
                throw new UncheckedSqlTemplateException(new SqlTemplateException("Unsupported BindVars type."));
            }
            int startPosition = parameters.size()
                    + bindVarsCounts.subList(0, bindVarsCursor).stream().mapToInt(count -> count).sum()
                    + 1;
            if (bindVarsCursor >= bindVarsCounts.size()) {
                throw new IllegalStateException("Not enough bind variables.");
            }
            return new ParameterFactory() {
                final List<PositionalParameter> tmp = new ArrayList<>();
                final int expectedBindVarCount = bindVarsCounts.get(bindVarsCursor++);

                /**
                 * Binds one value for the current bind vars segment.
                 *
                 * @param value the value to bind.
                 */
                @Override
                public void bind(@Nullable Object value) {
                    tmp.add(new PositionalParameter(startPosition + tmp.size(), value));
                }

                /**
                 * Returns the parameters of the current bind vars segment and resets internal storage.
                 *
                 * @return the positional parameters for the bind vars segment.
                 * @throws IllegalStateException if the number of bound values differs from the expected arity.
                 */
                @Override
                public List<PositionalParameter> getParameters() {
                    if (tmp.size() != expectedBindVarCount) {
                        throw new IllegalStateException("Bind var count mismatch.");
                    }
                    var result = List.copyOf(tmp);
                    tmp.clear();
                    return result;
                }
            };
        }

        /**
         * Binds all values from an iterable in positional mode when collection expansion is enabled.
         *
         * @param iterable the iterable to bind.
         * @param inline   whether values are inlined in SQL and therefore not bound as parameters.
         */
        private void bindArgs(@Nonnull Iterable<?> iterable, boolean inline) {
            for (var v : iterable) {
                if (!inline) {
                    parameters.add(new PositionalParameter(parameters.size() + 1, v));
                }
            }
        }
    }
}