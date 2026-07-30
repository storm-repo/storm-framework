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

import static java.util.Objects.requireNonNull;
import static st.orm.Operator.EQUALS;
import static st.orm.SelectMode.NESTED;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.function.Function;
import st.orm.BindVars;
import st.orm.Data;
import st.orm.Element;
import st.orm.Metamodel;
import st.orm.Operator;
import st.orm.ResolveScope;
import st.orm.SelectMode;
import st.orm.core.template.TemplateString;
import st.orm.mapping.RecordField;

/**
 * The typed placeholders a SQL template is compiled from.
 *
 * <p>Anything dynamic in a template is an element. Values interpolated into a template resolve to elements, and the
 * query builder contributes elements directly. Each element is compiled to SQL text by its processor (see
 * {@code ElementRouter}) when the query is built, so rendering can use the model, the resolved aliases, and the SQL
 * dialect, none of which exist when the query is written.</p>
 */
public final class Elements {

    private Elements() {
    }

    /**
     * Renders the SELECT column list for a table.
     *
     * <p>The mode picks the column set: {@link SelectMode#PK} selects the identifying columns only,
     * {@link SelectMode#DECLARED} the columns declared on the table itself, and {@link SelectMode#NESTED} the full
     * hierarchical column set needed to materialize the record, expanding foreign key relationships.</p>
     *
     * <p>The references a statement resolves as part of its select list are not part of this element: they are
     * carried by {@link Fetch} and read from the compiler, so the select element states only what a template author
     * means — which table, which column set.</p>
     */
    public record Select(@Nonnull Class<? extends Data> table, @Nonnull SelectMode mode) implements Element {
        public Select {
            requireNonNull(table, "table");
            requireNonNull(mode, "mode");
        }
        public Select(@Nonnull Class<? extends Data> table) {
            this(table, NESTED);
        }
    }

    /**
     * The references the statement resolves as part of its select list, named by field path relative to the
     * selected record.
     *
     * <p>Renders nothing. The compiler collects this element before compiling, so its position in the template does
     * not matter: the select list rendering and the row mapper read the plan from the compiler. Paths are
     * prefix-closed on construction, so plans that resolve the same references compare equal and share a compiled
     * statement, however they were spelled.</p>
     *
     * @since 1.13
     */
    public record Fetch(@Nonnull Collection<String> paths) implements Element {
        public Fetch {
            requireNonNull(paths, "paths");
            paths = FetchPlan.of(paths).paths();
        }
    }

    /**
     * Renders the INSERT target: the table name and its insertable column list.
     *
     * <p>{@code ignoreAutoGenerate} includes auto-generated primary key columns, so caller-supplied keys are written
     * verbatim. {@code returningKeys} makes the statement hand back the generated keys, using the mechanism the
     * dialect provides.</p>
     */
    public record Insert(@Nonnull Class<? extends Data> table, boolean ignoreAutoGenerate,
                         boolean returningKeys) implements Element {
        public Insert(@Nonnull Class<? extends Data> table) {
            this(table, false, false);
        }
        public Insert(@Nonnull Class<? extends Data> table, boolean ignoreAutoGenerate) {
            this(table, ignoreAutoGenerate, false);
        }
        public Insert {
            requireNonNull(table, "table");
        }
    }

    /**
     * Renders the VALUES rows of an INSERT: one row per record, or a single placeholder row bound through
     * {@code bindVars} for batch execution.
     *
     * <p>{@code ignoreAutoGenerate} mirrors {@link Insert}: the supplied primary key values are bound instead of
     * omitted.</p>
     */
    public record Values(@Nullable Iterable<? extends Data> records, @Nullable BindVars bindVars, boolean ignoreAutoGenerate) implements Element {
        public Values(@Nullable Iterable<? extends Data> records, @Nullable BindVars bindVars) {
            this(records, bindVars, false);
        }
    }

    /**
     * Renders the UPDATE target: the table name under the given alias. An empty alias lets the template derive one.
     */
    public record Update(@Nonnull Class<? extends Data> table, @Nonnull String alias) implements Element {
        public Update {
            requireNonNull(table, "table");
            requireNonNull(alias, "alias");
        }
        public Update(@Nonnull Class<? extends Data> table) {
            this(table, "");
        }
    }

    /**
     * Renders the SET clause of an UPDATE: assignments taken from the record's columns, or placeholder assignments
     * bound through {@code bindVars} for batch execution. A non-empty {@code fields} restricts the assignments to
     * those columns (a partial update).
     */
    public record Set(@Nullable Data record, @Nullable BindVars bindVars, @Nonnull Collection<Metamodel<?, ?>> fields) implements Element {
        public Set {
            fields = java.util.Set.copyOf(fields);
        }
    }

    /** A condition contributed to a WHERE or HAVING clause: either value-based or a template fragment. */
    public sealed interface Expression {}

    /**
     * A value comparison: the path compared against the object using the operator. The object may be a value, a
     * collection of values, a record, a reference, or a primary key; a multi-column path expands component-wise.
     * When the metamodel is {@code null}, the object is matched against the table's primary key.
     */
    public record ObjectExpression(@Nullable Metamodel<?, ?> metamodel, @Nonnull Operator operator, @Nonnull Object object) implements Expression {
        public ObjectExpression(@Nonnull Operator operator, @Nonnull Object object) {
            this(null, operator, object);
        }
        public ObjectExpression(@Nonnull Object object) {
            this(null, EQUALS, object);
        }
        public ObjectExpression {
            requireNonNull(object, "object");
            requireNonNull(operator, "operator");
        }
    }
    /** A condition written as a template fragment, rendered with its interpolations in place. */
    public record TemplateExpression(@Nonnull TemplateString template) implements Expression {}

    /**
     * Renders a WHERE condition from the expression, or from {@code bindVars} placeholders for batch execution.
     *
     * <p>{@code bindVarsKey} compiles the bind-vars condition for a specific key, so a raw key value can supply
     * every column; query plans use this to bind key lookups without a full record.</p>
     */
    public record Where(@Nullable Expression expression, @Nullable BindVars bindVars,
                        @Nullable Metamodel<?, ?> bindVarsKey) implements Element {
        public Where(@Nullable Expression expression, @Nullable BindVars bindVars) {
            this(expression, bindVars, null);
        }
    }

    /**
     * Renders the DELETE target: the table name under the given alias. An empty alias lets the template derive one.
     */
    public record Delete(@Nonnull Class<? extends Data> table, @Nonnull String alias) implements Element {
        public Delete {
            requireNonNull(table, "table");
            requireNonNull(alias, "alias");
        }
        public Delete(@Nonnull Class<? extends Data> table) {
            this(table, "");
        }
    }

    /** What a FROM clause or join reads from: a mapped table, or a template such as a subquery. */
    public sealed interface Source {}
    public record TableSource(@Nonnull Class<? extends Data> table) implements Source {}
    public record TemplateSource(@Nonnull TemplateString template) implements Source {}

    /** What a join joins onto: a table whose join condition is derived, or a template supplying the ON clause. */
    public sealed interface Target {}

    /**
     * @param table the table to join against.
     * @param field the resolved foreign key field for graph-derived joins, or {@code null} when
     *              the field must be resolved from the table types.
     */
    public record TableTarget(@Nonnull Class<? extends Data> table, @Nullable RecordField field) implements Target {
        public TableTarget(@Nonnull Class<? extends Data> table) {
            this(table, null);
        }
    }
    /** An ON condition supplied as a template fragment. */
    public record TemplateTarget(@Nonnull TemplateString template) implements Target {}

    /**
     * Renders the FROM clause: the source under the given alias (empty derives one). With {@code autoJoin}, the
     * root table's foreign key graph is expanded into derived joins, so the statement can select and reference the
     * related tables.
     */
    public record From(@Nonnull Source source, @Nonnull String alias, boolean autoJoin) implements Element {
        public From {
            requireNonNull(source, "source");
            requireNonNull(alias, "alias");
        }
        public From(@Nonnull Class<? extends Data> table, boolean autoJoin) {
            this(new TableSource(table), "", autoJoin);
        }
        public From(@Nonnull TemplateString template) {
            this(new TemplateSource(template), "", false);
        }
    }

    /**
     * Renders the qualified table name, followed by the alias when one is given. Used to name a table verbatim
     * inside a template, for example in a custom join or correlation.
     */
    public record Table(@Nonnull Class<? extends Data> table, @Nonnull String alias) implements Element {
        public Table {
            requireNonNull(table, "table");
            requireNonNull(alias, "alias");
        }
        public Table(@Nonnull Class<? extends Data> table) {
            this(table, "");
        }
    }

    /**
     * Renders the alias the table resolved to, looked up by type within the given scope. The scope selects where
     * the lookup may resolve: the current template, the enclosing template of a correlated subquery, or both.
     */
    public record Alias(@Nonnull Class<? extends Data> table, @Nonnull ResolveScope scope) implements Element {
        public Alias {
            requireNonNull(table, "table");
            requireNonNull(scope, "scope");
        }
    }

    /**
     * Renders exactly one column: the alias-qualified column the path resolves to, resolved like a predicate would
     * resolve it, so a foreign key names its foreign key column on the referencing table. A bare metamodel
     * interpolated into a template becomes this element.
     *
     * <p>A path that resolves to more than one column is rejected: this placeholder may sit in arbitrary SQL, where
     * splicing in a column list would produce broken statements. List contexts use {@link Columns} instead.</p>
     */
    public record Column(@Nonnull Metamodel<?, ?> field, @Nonnull ResolveScope scope) implements Element {
        public Column {
            requireNonNull(field, "field");
            requireNonNull(scope, "scope");
        }
    }

    /**
     * Renders every column the metamodel resolves to, comma-separated, for list contexts such as GROUP BY and
     * ORDER BY.
     *
     * <p>A single-column path renders exactly like {@link Column}. A multi-column path (a foreign key to a table
     * with a compound primary key, or an inline record) expands to each of its columns. Resolution matches
     * predicate resolution: the columns are the ones the metamodel resolves to on the table that holds them, so a
     * foreign key expands to its foreign key column(s) on the referencing table without joining the referenced
     * table. When {@code descending} is set, each column is followed by {@code DESC}.</p>
     *
     * @since 1.13
     */
    public record Columns(@Nonnull Metamodel<?, ?> field, @Nonnull ResolveScope scope, boolean descending) implements Element {
        public Columns {
            requireNonNull(field, "field");
            requireNonNull(scope, "scope");
        }
    }

    /**
     * A bind parameter: positional ({@code ?}) when unnamed, named ({@code :name}) otherwise. Holds the
     * database-ready value; conversions are applied when the element is created.
     */
    public record Param(@Nullable String name, @Nullable Object dbValue) implements Element {
        public Param(@Nullable String name, @Nullable Object value, @Nonnull Function<Object, ?> converter) {
            this(name, requireNonNull(converter, "converter").apply(value));
        }

        @Override
        public String toString() {
            return name == null ? "?" : ":%s".formatted(name);
        }
    }

    /**
     * One bind-variable slot in a batch template: the extractor reads the value from each record at execution time,
     * so the same compiled statement serves every record in the batch.
     */
    public record BindVar(@Nonnull BindVars bindVars, @Nonnull Function<Data, ?> extractor) implements Element {
        public BindVar {
            requireNonNull(bindVars, "bindVars");
            requireNonNull(extractor, "extractor");
        }
    }

    /**
     * Renders a nested template as a subquery. With {@code correlate}, the subquery may resolve aliases from the
     * enclosing template; without it, the subquery is self-contained.
     */
    public record Subquery(@Nonnull TemplateString template, boolean correlate) implements Element {}

    /** Raw SQL rendered verbatim, bypassing the template's safety checks. */
    public record Unsafe(@Nonnull String sql) implements Element {
        public Unsafe {
            requireNonNull(sql, "sql");
        }
    }
}
