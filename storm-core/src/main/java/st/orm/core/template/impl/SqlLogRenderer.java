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

import static java.util.Comparator.comparingLong;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static st.orm.core.spi.StormConfigHelper.getEnum;
import static st.orm.core.spi.StormConfigHelper.getInt;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import st.orm.Data;
import st.orm.Entity;
import st.orm.Ref;
import st.orm.StormConfig;
import st.orm.core.spi.Providers;
import st.orm.core.template.SqlLog.HydrationShapes;
import st.orm.core.template.SqlLog.StatementLine;
import st.orm.core.template.SqlLog.Summary;
import st.orm.core.template.SqlOperation;
import st.orm.core.template.StatementOrigin;

/**
 * Renders a {@link Summary} as a headline plus an aligned line per distinct statement, and derives the hydration
 * shape a summary row can carry.
 *
 * <p>How a summary renders — hydration shapes, line width — is a property of the log viewer rather than of any
 * scope, configured like a deployment property: the {@code storm.sql_log.hydration} and
 * {@code storm.sql_log.line_width} system properties on a plain JVM, or the corresponding keys of the Spring and
 * Ktor integrations, which apply their configuration through the setters here.</p>
 *
 * @since 1.14
 */
public final class SqlLogRenderer {

    private SqlLogRenderer() {
    }

    /**
     * How shapes render, a property of the log viewer rather than of any scope: derived at rendering and cached
     * per type, the setting costs nothing while calls run. Set once at startup; read at rendering only.
     */
    private static volatile HydrationShapes hydrationShapes = HydrationShapes.OFF;

    /**
     * Sets how a read's summary row renders the declared hydration shape of its type. Off by default; intended to
     * be called once at startup.
     *
     * @param shapes how shapes render.
     */
    public static void hydrationShapes(@Nonnull HydrationShapes shapes) {
        hydrationShapes = requireNonNull(shapes, "shapes");
    }

    /**
     * Width a summary row aims for, a property of the log viewer rather than of any scope. The statement text
     * receives what the row's other columns leave, down to a floor that keeps it identifiable. Set once at
     * startup; read at rendering only.
     */
    private static volatile int lineWidth = 200;

    /** The least statement text a row keeps, whatever its other columns consume. */
    private static final int MIN_STATEMENT_WIDTH = 40;

    /**
     * Sets the width summary rows aim for, such as {@code 120} for narrow viewers or {@code 240} for wide
     * ones. The statement text elides to what the row's other columns leave. Intended to be called once at
     * startup.
     *
     * @param width the display width; at least 80.
     */
    public static void lineWidth(int width) {
        lineWidth = Math.max(80, width);
    }

    /**
     * Renders the summary as a headline plus a line per distinct statement.
     *
     * @param summary the summary to render.
     * @return the rendered summary.
     */
    public static String render(@Nonnull Summary summary) {
        return render(summary.name(), summary.recorded(), summary.count(StatementOrigin.FETCH),
                summary.cacheHits(), NANOSECONDS.toMillis(summary.databaseNanos()),
                NANOSECONDS.toMillis(summary.databaseElapsedNanos()), summary.peakConcurrency(),
                NANOSECONDS.toMillis(summary.durationNanos()), summary.byStatement(),
                summary.recorded() - summary.statements().size());
    }

    /**
     * Renders the summary with the full statement texts appended, one per row in row order, so a row whose
     * elided text is not enough can be matched to the statement it stands for.
     *
     * @param summary the summary to render.
     * @return the rendered summary, followed by the full statements.
     */
    public static String renderDetailed(@Nonnull Summary summary) {
        var rendered = new StringBuilder(render(summary));
        var lines = summary.byStatement();
        if (!lines.isEmpty()) {
            rendered.append(String.format("%n\tstatements:"));
        }
        for (var line : lines) {
            rendered.append(String.format("%n\t  ")).append(flatten(line.statement()));
        }
        return rendered.toString();
    }

    /**
     * Renders a summary as a headline plus a line per distinct statement, heaviest first.
     *
     * <p>The headline separates the time the database spent from the time the call took, so a call that is slow for
     * other reasons says so. Under a fan-out the summed database time exceeds the elapsed time, and their ratio is
     * the concurrency the work achieved.</p>
     *
     * <p>Statements that ran past the recording limit are counted but not retained, so they contribute no duration
     * and no row. The database times are then lower bounds and are marked {@code +}, and a closing line reports how
     * many statements went unrecorded. The statement count and the call's own duration stay exact.</p>
     *
     * @param name what the scope covered.
     * @param statementCount the statements the call executed.
     * @param fetchCount how many of those resolved a reference.
     * @param cacheHits the reads the transaction's entity cache served without a statement.
     * @param databaseMillis the summed duration of the recorded statements.
     * @param databaseElapsedMillis the time during which at least one recorded statement was in flight.
     * @param peakConcurrency the greatest number of recorded statements in flight at once.
     * @param totalMillis how long the call took.
     * @param byStatement one line per distinct statement, in any order.
     * @param notRecorded how many statements ran past the recording limit.
     * @return the rendered summary.
     */
    public static String render(@Nonnull String name,
                                int statementCount,
                                int fetchCount,
                                int cacheHits,
                                long databaseMillis,
                                long databaseElapsedMillis,
                                int peakConcurrency,
                                long totalMillis,
                                @Nonnull List<StatementLine> byStatement,
                                int notRecorded) {
        var rendered = new StringBuilder("SQL (%s): %s".formatted(name, statements(statementCount)));
        if (fetchCount > 0) {
            rendered.append(", ").append(fetches(fetchCount));
        }
        if (cacheHits > 0) {
            rendered.append(", %d from cache".formatted(cacheHits));
        }
        // Statements past the recording limit contribute no duration, so the database times cover the recorded
        // ones only; mark them as lower bounds rather than let a truncated summary understate its own cost.
        var bound = notRecorded > 0 ? "+" : "";
        rendered.append(", %d%s ms in database".formatted(databaseMillis, bound));
        // Concurrency is a count of simultaneous executions, reported only when the work overlapped: summed
        // database time then exceeds the elapsed time it was compressed into, so both appear.
        if (peakConcurrency > 1) {
            rendered.append(" over %d%s ms elapsed (peak %d concurrent)".formatted(
                    databaseElapsedMillis, bound, peakConcurrency));
        }
        rendered.append(", %d ms total".formatted(totalMillis));
        int width = byStatement.stream()
                .mapToInt(line -> String.valueOf(NANOSECONDS.toMillis(line.durationNanos())).length())
                .max()
                .orElse(1);
        int executionWidth = byStatement.stream()
                .mapToInt(line -> String.valueOf(line.executions()).length())
                .max()
                .orElse(1);
        int typeWidth = byStatement.stream()
                .mapToInt(line -> line.dataType().length())
                .max()
                .orElse(1);
        int rowsWidth = byStatement.stream()
                .mapToInt(line -> rowsLabel(line).length())
                .max()
                .orElse(1);
        // The identifying columns align and lead; the free-form statement text comes last, where raggedness
        // does not break the columns after it. The fetch and call-site columns appear only when any row has one.
        boolean anyFetch = byStatement.stream().anyMatch(StatementLine::fetch);
        int siteWidth = byStatement.stream()
                .mapToInt(line -> siteLabel(line).length())
                .max()
                .orElse(0);
        // The row aims for the configured line width: the statement text receives what the fixed columns and
        // the row's own suffixes leave, down to a floor that keeps it identifiable.
        int prefixWidth = width + 5 + executionWidth + 3 + rowsWidth + 6 + typeWidth + 2
                + (anyFetch ? 7 : 0) + (siteWidth > 0 ? siteWidth + 2 : 0);
        // Ordering is presentation, so the renderer owns it rather than trusting the order it was handed.
        for (var line : byStatement.stream()
                .sorted(comparingLong(StatementLine::durationNanos).reversed())
                .toList()) {
            rendered.append(String.format(
                    "%n\t%" + width + "d ms  %" + rowsWidth + "s rows  %" + executionWidth + "dx  %-" + typeWidth + "s",
                    NANOSECONDS.toMillis(line.durationNanos()),
                    rowsLabel(line),
                    line.executions(),
                    line.dataType()));
            if (anyFetch) {
                rendered.append(String.format("  %-5s", line.fetch() ? "fetch" : ""));
            }
            if (siteWidth > 0) {
                rendered.append(String.format("  %-" + siteWidth + "s", siteLabel(line)));
            }
            int suffixWidth = (line.variants() > 1 ? 12 + String.valueOf(line.variants()).length() : 0)
                    + (line.hydration() != null ? 2 + line.hydration().length() : 0);
            int statementBudget = Math.max(MIN_STATEMENT_WIDTH, lineWidth - prefixWidth - suffixWidth);
            rendered.append("  ").append(elide(line.statement(), statementBudget));
            if (line.variants() > 1) {
                rendered.append(" (%d variants)".formatted(line.variants()));
            }
            if (line.hydration() != null) {
                rendered.append("  ").append(line.hydration());
            }
        }
        if (notRecorded > 0) {
            rendered.append("%n\t(%s not recorded)".formatted(statements(notRecorded)));
        }
        return rendered.toString();
    }

    /** Returns the row-count column content: the count, marked {@code *} when it is a lower bound. */
    private static String rowsLabel(@Nonnull StatementLine line) {
        return line.exactRows() ? String.valueOf(line.rows()) : line.rows() + "*";
    }

    /** Returns the call-site column content for the line, empty when the scope recorded none. */
    private static String siteLabel(@Nonnull StatementLine line) {
        if (line.callSite() == null) {
            return "";
        }
        return line.sites() > 1
                ? "%s (+%d sites)".formatted(line.callSite(), line.sites() - 1)
                : line.callSite();
    }

    /**
     * Returns the statement on one line, elided from the middle so a summary stays scannable: the head names the
     * operation and columns, the tail carries the FROM and WHERE clauses that identify what the statement does.
     */
    private static String elide(@Nonnull String statement, int width) {
        // A run of placeholders says nothing its length does not; collapsing it leaves the elision budget to
        // the clauses that identify the statement. Display only: the detailed rendering keeps the exact text.
        String flattened = flatten(statement).replaceAll("\\?(?:, \\?){3,}", "?, \u2026, ?");
        if (flattened.length() <= width) {
            return flattened;
        }
        int head = width / 3;
        int tail = width - head - 1;
        return flattened.substring(0, head) + "\u2026" + flattened.substring(flattened.length() - tail);
    }

    /**
     * Returns the statement on one line, joining it on the line breaks it was rendered with.
     *
     * <p>A nested subquery is rendered on lines of its own, which leaves its closing parenthesis opening a line.
     * Joining every break with a space reads that back as {@code WHERE id = ? ) ) x}, so a break between characters
     * that belong together closes up instead.</p>
     */
    private static String flatten(@Nonnull String statement) {
        var flattened = new StringBuilder();
        statement.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .forEach(line -> {
                    if (!flattened.isEmpty()
                            && separated(flattened.charAt(flattened.length() - 1), line.charAt(0))) {
                        flattened.append(' ');
                    }
                    flattened.append(line);
                });
        return flattened.toString();
    }

    /** Returns whether a line break between the two characters reads as a space rather than as nothing at all. */
    private static boolean separated(char before, char after) {
        return before != '(' && after != ')' && after != ',';
    }

    private static String statements(int count) {
        return "%d statement%s".formatted(count, count == 1 ? "" : "s");
    }

    private static String fetches(int count) {
        return "%d fetch%s".formatted(count, count == 1 ? "" : "es");
    }

    /**
     * Returns the rendered hydration shape of the statement's type in the configured form, or {@code null} when
     * shapes are off, the statement is not a read, or its type has none.
     *
     * @param operation what the statement does.
     * @param dataType the entity or projection it targets, or {@code null} when it targets none.
     * @return the rendered shape, or {@code null} when the row carries none.
     */
    @Nullable
    public static String hydrationOf(@Nonnull SqlOperation operation, @Nullable Class<? extends Data> dataType) {
        var shapes = hydrationShapes;
        if (shapes == HydrationShapes.OFF) {
            return null;
        }
        if (operation != SqlOperation.SELECT) {
            // The shape states what reading the type joins and maps. A write touches its own table only, so
            // the shape would describe a graph its statement never traverses and columns it never sets; a
            // statement whose operation is undetermined cannot claim to be a read either.
            return null;
        }
        if (dataType == null) {
            return null;
        }
        var shape = HYDRATION.get(dataType);
        if (shape == NO_HYDRATION) {
            return null;
        }
        if (shapes == HydrationShapes.FULL) {
            return "joins=%d columns=%d graph=%s".formatted(shape.joins(), shape.columns(), shape.graph());
        }
        if (shape.joins() == 0) {
            // A flat type says nothing its row does not already say; the short token appears when hydration
            // reaches beyond the statement's own table.
            return null;
        }
        return "j%d c%d d%d".formatted(shape.joins(), shape.columns(), shape.depth());
    }

    /**
     * The declared hydration shape of a type: what an eager read of it joins and maps.
     *
     * <p>Derived from the type declaration, not from any statement: an entity component is a join edge and
     * recurses; an inline record is columns on the same table and no subgraph, so a joined entity it carries
     * splices into its parent's children; a reference is its foreign key column and stops, which is exactly the
     * width a {@code Ref} declaration saves. Computed once per type, at display time.</p>
     *
     * @param joins the join edges an eager read of the type takes.
     * @param columns the columns it maps.
     * @param depth the entity levels along the deepest chain: {@code 1} for a flat entity, {@code 3} for
     *              {@code Pet(Owner(City))}.
     * @param graph the joined-entity tree, such as {@code Pet(PetType, Owner(City))}.
     */
    private record Hydration(int joins, int columns, int depth, @Nonnull String graph) {
    }

    /** Marks a type without a mapped record structure, for which no shape renders. */
    private static final Hydration NO_HYDRATION = new Hydration(0, 0, 0, "");

    private static final ClassValue<Hydration> HYDRATION = new ClassValue<>() {
        @Override
        protected Hydration computeValue(@Nonnull Class<?> type) {
            // The reflection provider recognizes the mapped structure of Java records and Kotlin data classes
            // alike, which is the same bridge the model itself is built over.
            if (Providers.getORMReflection().findRecordType(type).isEmpty()) {
                return NO_HYDRATION;
            }
            var shape = shape(type, new HashSet<>());
            String graph = type.getSimpleName()
                    + (shape.children().isEmpty() ? "" : "(" + shape.children() + ")");
            return new Hydration(shape.joins(), shape.columns(), 1 + shape.depth(), graph);
        }
    };

    /**
     * The shape of one type level: its joins and columns, the entity levels below it, and its joined children
     * rendered as a list.
     */
    private record Shape(int joins, int columns, int depth, @Nonnull String children) {
    }

    private static Shape shape(@Nonnull Class<?> type, @Nonnull Set<Class<?>> path) {
        if (!path.add(type)) {
            // A cycle recurses no further; the revisited entity contributes its foreign key column.
            return new Shape(0, 1, 0, "");
        }
        try {
            var reflection = Providers.getORMReflection();
            var recordType = reflection.findRecordType(type).orElse(null);
            if (recordType == null) {
                return new Shape(0, 1, 0, "");
            }
            int joins = 0;
            int columns = 0;
            int depth = 0;
            var children = new StringBuilder();
            for (var field : recordType.fields()) {
                Class<?> fieldType = field.type();
                if (Ref.class.isAssignableFrom(fieldType)) {
                    // The foreign key column; a reference does not widen the read.
                    columns++;
                } else if (Entity.class.isAssignableFrom(fieldType)) {
                    var child = shape(fieldType, path);
                    joins += 1 + child.joins();
                    columns += child.columns();
                    depth = Math.max(depth, 1 + child.depth());
                    if (!children.isEmpty()) {
                        children.append(", ");
                    }
                    children.append(fieldType.getSimpleName());
                    if (!child.children().isEmpty()) {
                        children.append('(').append(child.children()).append(')');
                    }
                } else if (reflection.findRecordType(fieldType).isPresent()) {
                    // Any other mapped record structure is an inline record: columns on this table, not a
                    // subgraph; entities it joins splice up.
                    // An inline record is not a level of its own, so an entity it joins counts at this level.
                    var inline = shape(fieldType, path);
                    joins += inline.joins();
                    columns += inline.columns();
                    depth = Math.max(depth, inline.depth());
                    if (!inline.children().isEmpty()) {
                        if (!children.isEmpty()) {
                            children.append(", ");
                        }
                        children.append(inline.children());
                    }
                } else {
                    columns++;
                }
            }
            return new Shape(joins, columns, depth, children.toString());
        } finally {
            path.remove(type);
        }
    }

    /**
     * Applies the display settings from configuration ({@link StormConfig#defaults()}, which reads system
     * properties). How the log renders is a property of the deployment, so it is configured like one; the Spring
     * and Ktor integrations apply their own configuration through the setters.
     */
    // Placed after every field it touches, since static initialization runs in textual order.
    static {
        var config = StormConfig.defaults();
        hydrationShapes(getEnum(config, StormConfig.SQL_LOG_HYDRATION, HydrationShapes.class, HydrationShapes.OFF));
        lineWidth(getInt(config, StormConfig.SQL_LOG_LINE_WIDTH, 200));
    }
}
