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
import st.orm.template.Sql;
import st.orm.template.SqlTemplate;
import st.orm.template.SqlTemplate.BindVariables;
import st.orm.template.SqlTemplate.NamedParameter;
import st.orm.template.SqlTemplate.Parameter;
import st.orm.template.SqlTemplate.PositionalParameter;
import st.orm.template.SqlTemplateException;
import st.orm.template.impl.Elements.Alias;
import st.orm.template.impl.Elements.Column;
import st.orm.template.impl.Elements.Delete;
import st.orm.template.impl.Elements.From;
import st.orm.template.impl.Elements.Insert;
import st.orm.template.impl.Elements.Param;
import st.orm.template.impl.Elements.Select;
import st.orm.template.impl.Elements.Set;
import st.orm.template.impl.Elements.Subquery;
import st.orm.template.impl.Elements.Table;
import st.orm.template.impl.Elements.Unsafe;
import st.orm.template.impl.Elements.Update;
import st.orm.template.impl.Elements.Values;
import st.orm.template.impl.Elements.Var;
import st.orm.template.impl.Elements.Where;
import st.orm.template.impl.SqlTemplateImpl.Wrapped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
        @Nullable Table primaryTable
) implements ElementProcessor<Element> {
    private static final ScopedValue<SqlTemplateProcessor> CURRENT_PROCESSOR = ScopedValue.newInstance();

    /**
     * Returns the current processor of the calling thread.
     *
     * @return the current processor of the calling thread.
     */
    static Optional<SqlTemplateProcessor> current() {
        return ofNullable(CURRENT_PROCESSOR.orElse(null));
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
                case Wrapped _ -> {
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
                case Var it -> new VarProcessor(this).process(it);
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
     * @return the string of positional parameters.
     */
    private String toArgsString(@Nonnull Iterable<?> iterable) {
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

    /**
     * Registers the next positional parameter. As a result, the parameter is added to the list of parameters and the
     * parameter position is incremented.
     *
     * @param value the value of the parameter.
     * @return the string representation of the parameter, which is one or more '?' characters.
     * @throws SqlTemplateException if the value is not supported.
     */
    String registerParam(@Nullable Object value) throws SqlTemplateException {
        return switch (value) {
            case Object[] array when template.expandCollection() -> toArgsString(List.of(array));
            case Iterable<?> it when template.expandCollection() -> toArgsString(it);
            case Object[] _ -> throw new SqlTemplateException("Array parameters not supported.");
            case Iterable<?> _ -> throw new SqlTemplateException("Collection parameters not supported.");
            case null, default -> {
                parameters.add(new PositionalParameter(parameterPosition.getAndIncrement(), value));
                yield "?";
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
    String registerParam(@Nonnull String name, @Nullable Object value) throws SqlTemplateException {
        if (template.positionalOnly()) {
            throw new SqlTemplateException("Named parameters not supported.");
        }
        parameters.add(new NamedParameter(name, value));
        return STR.":\{name}";
    }

    /**
     * Parses a string template and returns the SQL statement.
     *
     * @param stringTemplate the string template to parse.
     * @param correlate whether the elements in the template should be correlated to the outer query.
     * @return the SQL statement.
     * @throws SqlTemplateException if the template does not comply to the specification.
     */
    String parse(@Nonnull StringTemplate stringTemplate, boolean correlate) throws SqlTemplateException {
        Callable<String> callable = () -> {
            Sql sql = template.process(stringTemplate);
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

    /**
     * Sets the bind variables for the current processor.
     *
     * @param vars the bind variables to set.
     * @throws SqlTemplateException if the bind variables are not supported.
     */
    void setBindVars(@Nonnull BindVars vars) throws SqlTemplateException {
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
}
