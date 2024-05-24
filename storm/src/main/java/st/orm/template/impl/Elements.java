package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.BindVars;

import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public final class Elements {
    public record Select(@Nonnull Class<? extends Record> table) implements Element {
        public Select {
            requireNonNull(table, "table");
        }
    }

    public record Insert(@Nonnull Class<? extends Record> table) implements Element {
        public Insert {
            requireNonNull(table, "table");
        }
    }

    public record Values(@Nullable Stream<? extends Record> records, @Nullable BindVars bindVars) implements Element {}

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

    public record ObjectExpression(@Nonnull Object object) implements Expression {
        public ObjectExpression {
            requireNonNull(object, "object");
        }
    }
    public record TemplateExpression(@Nonnull StringTemplate template) implements Expression {}

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

    public sealed interface Source {
    }
    public record TableSource(@Nonnull Class<? extends Record> table) implements Source {}
    public record TemplateSource(@Nonnull StringTemplate template) implements Source {}

    public record From(@Nonnull Source source, @Nonnull String alias) implements Element {
        public From {
            requireNonNull(source, "source");
            requireNonNull(alias, "alias");
        }
        public From(@Nonnull Class<? extends Record> table) {
            this(new TableSource(table), "");
        }
        public From(@Nonnull StringTemplate template) {
            this(new TemplateSource(template), "");
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

    public record Alias(@Nonnull Class<? extends Record> table) implements Element {
    }

    public record Param(@Nullable String name, @Nullable Object dbValue) implements Element {
        public Param(@Nullable String name, @Nullable Object value, @Nonnull Function<Object, ?> converter) {
            this(name, requireNonNull(converter, "converter").apply(value));
        }

        @Override
        public String toString() {
            return name == null ? "?" : STR.":\{name}";
        }
    }

    public record Unsafe(@Nonnull String sql) implements Element {
        public Unsafe {
            requireNonNull(sql, "sql");
        }
    }
}
