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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.BindVars;
import st.orm.core.template.Sql;
import st.orm.core.template.SqlTemplate;
import st.orm.core.template.SqlTemplate.BindVariables;
import st.orm.core.template.SqlTemplate.NamedParameter;
import st.orm.core.template.SqlTemplate.Parameter;
import st.orm.core.template.SqlTemplate.PositionalParameter;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.TemplateString;
import st.orm.core.template.impl.Elements.Alias;
import st.orm.core.template.impl.Elements.Column;
import st.orm.core.template.impl.Elements.Delete;
import st.orm.core.template.impl.Elements.From;
import st.orm.core.template.impl.Elements.Insert;
import st.orm.core.template.impl.Elements.Param;
import st.orm.core.template.impl.Elements.Select;
import st.orm.core.template.impl.Elements.Set;
import st.orm.core.template.impl.Elements.Subquery;
import st.orm.core.template.impl.Elements.Table;
import st.orm.core.template.impl.Elements.Unsafe;
import st.orm.core.template.impl.Elements.Update;
import st.orm.core.template.impl.Elements.Values;
import st.orm.core.template.impl.Elements.BindVar;
import st.orm.core.template.impl.Elements.Where;
import st.orm.core.template.impl.SqlTemplateImpl.Wrapped;

import java.lang.ScopedValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.ScopedValue.callWhere;
import static java.lang.String.join;
import static java.util.Optional.ofNullable;

/**
 * Processes elements in a SQL template.
 */
record SqlTemplateProcessor(
        @Nonnull SqlTemplate template,
        @Nonnull SqlDialectTemplate dialectTemplate,
        @Nonnull ModelBuilder modelBuilder,
        @Nonnull List<Parameter> parameters,
        @Nonnull AtomicInteger parameterPosition,
        @Nonnull AtomicInteger nameIndex,
        @Nonnull TableUse tableUse,
        @Nonnull AliasMapper aliasMapper,
        @Nonnull TableMapper tableMapper,
        @Nonnull AtomicReference<BindVariables> bindVariables,
        @Nonnull List<String> generatedKeys,
        @Nonnull AtomicBoolean versionAware,
        @Nullable PrimaryTable primaryTable
) implements ElementProcessor<Element> {
    private static final ScopedValue<SqlTemplateProcessor> CURRENT_PROCESSOR = ScopedValue.newInstance();
    private static final ScopedValue<Boolean> SUBQUERY = ScopedValue.newInstance();

    /**
     * Returns the current processor of the calling thread.
     *
     * @return the current processor of the calling thread.
     */
    static Optional<SqlTemplateProcessor> current() {
        return ofNullable(CURRENT_PROCESSOR.orElse(null));
    }

    /**
     * Return true if we're in the context of a subquery.
     *
     * @return true if we're in the context of a subquery.
     */
    static boolean isSubquery() {
        return SUBQUERY.isBound();
    }

    /**
     * Process an element of a template.
     *
     * @param element the element to process.
     * @return the result of processing the element.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    @Override
    public ElementResult process(@Nonnull Element element) throws SqlTemplateException {
        var results = new ArrayList<SqlGenerator>();
        for (Element unwrapped : element instanceof Wrapped(var elements) ? elements : List.of(element)) {
            results.add(switch (unwrapped) {
                case Wrapped ignore -> {
                    assert false;
                    yield null;
                }
                case Select it -> new SelectProcessor(this).process(it);
                case Insert it -> new InsertProcessor(this).process(it);
                case Update it -> new UpdateProcessor(this).process(it);
                case Delete it -> new DeleteProcessor(this).process(it);
                case From it -> new FromProcessor(this).process(it);
                case Join it -> new JoinProcessor(this).process(it);
                case Table it -> new TableProcessor(this).process(it);
                case Alias it -> new AliasProcessor(this).process(it);
                case Column it -> new ColumnProcessor(this).process(it);
                case Set it -> new SetProcessor(this).process(it);
                case Where it ->  new WhereProcessor(this).process(it);
                case Values it -> new ValuesProcessor(this).process(it);
                case Param it -> new ParamProcessor(this).process(it);
                case BindVar it -> new VarProcessor(this).process(it);
                case Subquery it -> new SubqueryProcessor(this).process(it);
                case Unsafe it -> new UnsafeProcessor(this).process(it);
            });
        }
        return new ElementResult(() -> {
            StringBuilder sql = new StringBuilder();
            for (SqlGenerator generator : results) {
                sql.append(generator.get());
            }
            return sql.toString();
        });
    }

    /**
     * Converts an iterable to a string of positional parameters.
     *
     * @param iterable the iterable to convert.
     * @param inline whether to inline the parameters, instead of binding.
     * @return the string of positional parameters.
     */
    private String toArgsString(@Nonnull Iterable<?> iterable, boolean inline) {
        List<String> args = new ArrayList<>();
        for (var v : iterable) {
            if (inline) {
                args.add(toLiteral(v));
            } else {
                args.add("?");
                parameters.add(new PositionalParameter(parameterPosition.getAndIncrement(), v));
            }
            args.add(", ");
        }
        if (!args.isEmpty()) {
            args.removeLast();    // Remove last ", " element.
        }
        return join("", args);
    }

    /**
     * Converts a Java object into its SQL literal representation.
     *
     * @param dbValue the value to convert.
     * @return a String suitable for inlining into SQL (including quotes or NULL).
     */
    private static String toLiteral(@Nullable Object dbValue) {
        return switch (dbValue) {
            case null -> "NULL";
            case Short s   -> s.toString();
            case Integer i -> i.toString();
            case Long l    -> l.toString();
            case Float f   -> f.toString();
            case Double d  -> d.toString();
            case Byte b    -> b.toString();
            case Boolean b -> b ? "TRUE" : "FALSE";
            case String s  -> // First double every backslash, then double single-quotes.
                    "'%s'".formatted(s.replace("\\", "\\\\")
                            .replace("'", "''"));
            case java.sql.Date d       -> "'%s'".formatted(d);
            case java.sql.Time t       -> "'%s'".formatted(t);
            case java.sql.Timestamp t  -> "'%s'".formatted(t);
            case Enum<?> e -> "'%s'".formatted(e.name()
                    .replace("\\", "\\\\")
                    .replace("'", "''"));
            default -> {
                String str = dbValue.toString()
                        .replace("\\", "\\\\")
                        .replace("'", "''");
                yield "'%s'".formatted(str);
            }
        };
    }

    /**
     * Registers the next positional parameter. As a result, the parameter is added to the list of parameters and the
     * parameter position is incremented.
     *
     * @param value the value of the parameter.
     * @return the string representation of the parameter, which is one or more '?' characters.
     * @throws SqlTemplateException if the value is not supported.
     */
    String bindParameter(@Nullable Object value) throws SqlTemplateException {
        boolean inline = template.inlineParameters();
        return switch (value) {
            case Object[] array when template.expandCollection() -> toArgsString(List.of(array), inline);
            case Iterable<?> it when template.expandCollection() -> toArgsString(it, inline);
            case Object[] ignore -> throw new SqlTemplateException("Array parameters not supported.");
            case Iterable<?> ignore -> throw new SqlTemplateException("Collection parameters not supported.");
            case null, default -> {
                if (inline) {
                    yield toLiteral(value);
                } else {
                    parameters.add(new PositionalParameter(parameterPosition.getAndIncrement(), value));
                    yield "?";
                }
            }
        };
    }

    /**
     * Registers a named parameter. As a result, the parameter is added to the list of parameters.
     *
     * @param name the name of the parameter.
     * @param value the value of the parameter.
     * @return the string representation of the parameter, which is a named placeholder.
     * @throws SqlTemplateException if named parameters are not supported.
     */
    String bindParameter(@Nonnull String name, @Nullable Object value) throws SqlTemplateException {
        if (template.positionalOnly()) {
            throw new SqlTemplateException("Named parameters not supported.");
        }
        parameters.add(new NamedParameter(name, value));
        return ":%s".formatted(name);
    }

    /**
     * Parses a string template and returns the SQL statement.
     *
     * @param stringTemplate the string template to parse.
     * @param correlate whether the elements in the template should be correlated to the outer query.
     * @return the SQL statement.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    String parse(@Nonnull TemplateString stringTemplate, boolean correlate)
            throws SqlTemplateException {
        Callable<String> callable = () -> {
            Sql sql = template.process(stringTemplate);
            for (var parameter : sql.parameters()) {
                switch (parameter) {
                    case PositionalParameter p -> bindParameter(p.dbValue());
                    case NamedParameter n -> bindParameter(n.name(), n.dbValue());
                }
            }
            return sql.statement();
        };
        Callable<String> scopedCallable = () -> callWhere(SUBQUERY, true, () -> correlate
                ? callWhere(CURRENT_PROCESSOR, this, callable)
                : callable.call());
        try {
            return scopedCallable.call();
        } catch (SqlTemplateException e) {
            throw e;
        } catch (Exception e) {
            throw new SqlTemplateException(e);
        }
    }

    interface ParameterFactory {

        void bind(@Nullable Object value);

        List<PositionalParameter> getParameters() throws SqlTemplateException;
    }

    /**
     * Sets the bind variables for the current processor.
     *
     * @param vars the bind variables to set.
     * @param bindVarsCount number of positional parameters to set.
     * @return the parameter position at which the bind vars are set.
     * @throws SqlTemplateException if the bind variables are not supported.
     */
    ParameterFactory setBindVars(@Nonnull BindVars vars, int bindVarsCount) throws SqlTemplateException {
        var current = bindVariables.getPlain();
        if (current != null && current != vars) {
            throw new SqlTemplateException("Multiple BindVars instances not supported.");
        }
        if (vars instanceof BindVarsImpl bindVars) {
            bindVariables.set(bindVars);
        } else {
            throw new SqlTemplateException("Unsupported BindVars type.");
        }
        int startPosition = parameterPosition.getAndAdd(bindVarsCount);
        return new ParameterFactory() {
            final List<PositionalParameter> parameters = new ArrayList<>(bindVarsCount);
            @Override
            public void bind(@Nullable Object value) {
                parameters.add(new PositionalParameter(startPosition + parameters.size(), value));
            }

            @Override
            public List<PositionalParameter> getParameters() throws SqlTemplateException {
                if (bindVarsCount != parameters.size()) {
                    throw new SqlTemplateException("Expected %d parameters, but got %s.".formatted(bindVarsCount, parameters.size()));
                }
                var result = List.copyOf(parameters);
                parameters.clear();
                return result;
            }
        };
    }
}
