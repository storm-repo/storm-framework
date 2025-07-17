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
import st.orm.ResolveScope;
import st.orm.BindVars;
import st.orm.core.template.TemplateString;
import st.orm.Metamodel;
import st.orm.Operator;
import st.orm.SelectMode;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static st.orm.Operator.EQUALS;
import static st.orm.SelectMode.NESTED;

public final class Elements {

    private Elements() {
    }

    public record Select(@Nonnull Class<? extends Record> table, @Nonnull SelectMode mode) implements Element {
        public Select {
            requireNonNull(table, "table");
            requireNonNull(mode, "mode");
        }
        public Select(@Nonnull Class<? extends Record> table) {
            this(table, NESTED);
        }
    }

    public record Insert(@Nonnull Class<? extends Record> table, boolean ignoreAutoGenerate) implements Element {
        public Insert(@Nonnull Class<? extends Record> table) {
            this(table, false);
        }
        public Insert {
            requireNonNull(table, "table");
        }
    }

    public record Values(@Nullable Iterable<? extends Record> records, @Nullable BindVars bindVars, boolean ignoreAutoGenerate) implements Element {
        public Values(@Nullable Iterable<? extends Record> records, @Nullable BindVars bindVars) {
            this(records, bindVars, false);
        }
    }

    public record Update(@Nonnull Class<? extends Record> table, @Nonnull String alias) implements Element {
        public Update {
            requireNonNull(table, "table");
            requireNonNull(alias, "alias");
        }
        public Update(@Nonnull Class<? extends Record> table) {
            this(table, "");
        }
    }

    public record Set(@Nullable Record record, @Nullable BindVars bindVars) implements Element {}

    public sealed interface Expression {}
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
    public record TemplateExpression(@Nonnull TemplateString template) implements Expression {}

    public record Where(@Nullable Expression expression, @Nullable BindVars bindVars) implements Element {}

    public record Delete(@Nonnull Class<? extends Record> table, @Nonnull String alias) implements Element {
        public Delete {
            requireNonNull(table, "table");
            requireNonNull(alias, "alias");
        }
        public Delete(@Nonnull Class<? extends Record> table) {
            this(table, "");
        }
    }

    public sealed interface Source {}
    public record TableSource(@Nonnull Class<? extends Record> table) implements Source {}
    public record TemplateSource(@Nonnull TemplateString template) implements Source {}

    public sealed interface Target {}
    public record TableTarget(@Nonnull Class<? extends Record> table) implements Target {}
    public record TemplateTarget(@Nonnull TemplateString template) implements Target {}

    public record From(@Nonnull Source source, @Nonnull String alias, boolean autoJoin) implements Element {
        public From {
            requireNonNull(source, "source");
            requireNonNull(alias, "alias");
        }
        public From(@Nonnull Class<? extends Record> table, boolean autoJoin) {
            this(new TableSource(table), "", autoJoin);
        }
        public From(@Nonnull TemplateString template) {
            this(new TemplateSource(template), "", false);
        }
    }

    public record Table(@Nonnull Class<? extends Record> table, @Nonnull String alias) implements Element {
        public Table {
            requireNonNull(table, "table");
            requireNonNull(alias, "alias");
        }
        public Table(@Nonnull Class<? extends Record> table) {
            this(table, "");
        }
    }

    public record Alias(@Nonnull Class<? extends Record> table, @Nonnull ResolveScope scope) implements Element {
        public Alias {
            requireNonNull(table, "table");
            requireNonNull(scope, "scope");
        }
    }

    public record Column(@Nonnull Metamodel<?, ?> metamodel, @Nonnull ResolveScope scope) implements Element {
        public Column {
            requireNonNull(metamodel, "metamodel");
            requireNonNull(scope, "scope");
        }
    }

    public record Param(@Nullable String name, @Nullable Object dbValue) implements Element {
        public Param(@Nullable String name, @Nullable Object value, @Nonnull Function<Object, ?> converter) {
            this(name, requireNonNull(converter, "converter").apply(value));
        }

        @Override
        public String toString() {
            return name == null ? "?" : ":%s".formatted(name);
        }
    }

    public record BindVar(@Nonnull BindVars bindVars, @Nonnull Function<Record, ?> extractor) implements Element {
        public BindVar {
            requireNonNull(bindVars, "bindVars");
            requireNonNull(extractor, "extractor");
        }
    }

    public record Subquery(@Nonnull TemplateString template, boolean correlate) implements Element {}

    public record Unsafe(@Nonnull String sql) implements Element {
        public Unsafe {
            requireNonNull(sql, "sql");
        }
    }
}
