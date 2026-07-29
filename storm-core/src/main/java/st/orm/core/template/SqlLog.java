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
package st.orm.core.template;

import static java.util.Comparator.comparingLong;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static st.orm.core.spi.StormConfigHelper.getEnum;
import static st.orm.core.spi.StormConfigHelper.getInt;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import st.orm.Data;
import st.orm.PersistenceException;
import st.orm.StormConfig;
import st.orm.core.spi.QueryContext;
import st.orm.core.template.SqlTemplate.Parameter;
import st.orm.core.template.impl.SqlInterceptorManager;
import st.orm.core.template.impl.StatementListener;

/**
 * Records the statements a call executes, so a unit of work can be judged by what it cost the database.
 *
 * <p>A scope answers what a single call did as a whole: how many statements it took, which of them resolved
 * references rather than being asked for, and which statement carried the weight. That total is invisible from any
 * one statement.</p>
 *
 * <p>A scope covers whatever executes inside it, whichever repository, query builder or template issued the
 * statement, so it can wrap the entry point of a request rather than a single repository.</p>
 *
 * <p><strong>Cost when inactive is zero.</strong> A scope registers on the interceptor chain that every statement
 * already walks, so a statement executed with no scope open reads a single counter and stops. The recording cost
 * is paid only while a scope is open.</p>
 *
 * <p>A scope is bound to the thread that opened it, so it records the statements of a blocking call. Statements
 * issued after a suspension that resumed on another thread fall outside it.</p>
 *
 * @since 1.13
 */
public final class SqlLog {

    private SqlLog() {
    }

    /**
     * The logger every scope summary reports through. The logger is the switch: a summary is recorded only while
     * it is enabled and read only through it, so what a scope observed never becomes API surface an application
     * couples to.
     */
    private static final Logger REPORT_LOGGER = LoggerFactory.getLogger("st.orm.sql.perf");

    /**
     * Returns whether a reported summary reaches anything, so a caller can skip opening a scope whose summary
     * nothing consumes.
     *
     * @return whether the {@code st.orm.sql.perf} logger is enabled at {@code INFO}.
     */
    public static boolean reporting() {
        return REPORT_LOGGER.isInfoEnabled();
    }

    /**
     * Reports a summary under the {@code st.orm.sql.perf} logger: at {@code INFO}, with the full statement texts
     * appended while the logger is at {@code TRACE}. A summary without statements says nothing worth a line and is
     * not reported.
     *
     * <p>The texts follow at {@code TRACE} rather than {@code DEBUG} because this logger is a child of
     * {@code st.orm.sql}: raising that to {@code DEBUG} for per-statement logging would otherwise raise this one
     * with it and repeat every statement the statement logger had just written on its own line.</p>
     *
     * @param summary the summary to report.
     */
    public static void report(@Nonnull Summary summary) {
        if (summary.statementCount() == 0) {
            return;
        }
        REPORT_LOGGER.info("{}", REPORT_LOGGER.isTraceEnabled() ? summary.toDetailedString() : summary);
    }

    /**
     * A statement recorded by a scope.
     *
     * @param operation what the statement does.
     * @param dataType the entity or projection it targets, or {@code null} when it targets none.
     * @param origin what caused it to execute.
     * @param statement the statement text, with placeholders.
     */
    public record Statement(@Nonnull SqlOperation operation,
                            Class<? extends Data> dataType,
                            @Nonnull StatementOrigin origin,
                            @Nonnull String statement,
                            long startNanos,
                            long endNanos,
                            long shapeId,
                            @Nullable String callSite,
                            long rows,
                            boolean exactRows) {

        /** Returns how long the execution took. */
        public long durationNanos() {
            return endNanos - startNanos;
        }
    }

    /**
     * What a call cost the database.
     */
    public record Summary(@Nonnull String name,
                          @Nonnull List<Statement> statements,
                          int recorded,
                          int cacheHits,
                          long durationNanos) {

        public Summary {
            requireNonNull(name, "name");
            statements = List.copyOf(statements);
        }

        /**
         * Returns the number of statements the call executed, including any beyond the recording limit.
         *
         * @return the statement count.
         */
        public int statementCount() {
            return recorded;
        }

        /**
         * Returns the number of statements the call executed with the given origin.
         *
         * <p>A {@link StatementOrigin#FETCH} count reports what resolving references cost: one statement per
         * reference the call read that no query had already brought along. A reference the transaction's entity
         * cache served issues no statement, so this counts distinct cache misses.</p>
         *
         * @param origin the origin to count.
         * @return the matching statement count, over the recorded statements.
         */
        public int count(@Nonnull StatementOrigin origin) {
            return (int) statements.stream().filter(s -> s.origin() == origin).count();
        }

        /**
         * Returns the reads the transaction's entity cache served without a statement: a reference resolving to
         * an entity the transaction had already read, or an identity lookup at {@code REPEATABLE_READ} and
         * above. The fetch count reports the cache misses; this is what the cache saved.
         *
         * @return the cache-served read count.
         */
        public int cacheHits() {
            return cacheHits;
        }

        /**
         * Returns whether statements were executed beyond the ones recorded.
         *
         * @return {@code true} when the recording limit truncated the statements.
         */
        public boolean truncated() {
            return recorded > statements.size();
        }

        /**
         * Returns the total time the executions took, which under concurrency exceeds the elapsed time.
         *
         * @return the summed statement duration, in nanoseconds.
         */
        public long databaseNanos() {
            return statements.stream().mapToLong(Statement::durationNanos).sum();
        }

        /**
         * Returns the time during which at least one statement was in flight, which is what the summed duration was
         * compressed into.
         *
         * @return the union of the execution intervals, in nanoseconds.
         */
        public long databaseElapsedNanos() {
            var intervals = statements.stream()
                    .sorted(comparingLong(Statement::startNanos))
                    .toList();
            long elapsed = 0;
            long openedAt = 0;
            long closesAt = 0;
            for (var interval : intervals) {
                if (interval.startNanos() > closesAt) {
                    elapsed += closesAt - openedAt;
                    openedAt = interval.startNanos();
                }
                closesAt = Math.max(closesAt, interval.endNanos());
            }
            return elapsed + (closesAt - openedAt);
        }

        /**
         * Returns the greatest number of statements that were in flight at once, which tells a fan-out that ran in
         * parallel from one that did not.
         *
         * @return the peak number of concurrent executions.
         */
        public int peakConcurrency() {
            long[] starts = statements.stream().mapToLong(Statement::startNanos).sorted().toArray();
            long[] ends = statements.stream().mapToLong(Statement::endNanos).sorted().toArray();
            int peak = 0;
            int inFlight = 0;
            int closing = 0;
            for (long start : starts) {
                while (closing < ends.length && ends[closing] <= start) {
                    inFlight--;
                    closing++;
                }
                inFlight++;
                peak = Math.max(peak, inFlight);
            }
            return peak;
        }

        /**
         * Returns the recorded statements grouped by their text, heaviest first, which is the view that answers
         * where the time went: a statement run many times cheaply outranks one slow statement when it cost more in
         * total. When {@link #hydrationShapes(HydrationShapes)} enables shapes, each line carries the declared
         * hydration shape of its statement's type in the configured form.
         *
         * @return one line per distinct statement, ordered by total time.
         */
        public List<StatementLine> byStatement() {
            // Statements group by shape, so a collection parameter that expands to a different number of
            // placeholders per execution stays one group; text is the fallback for statements without one.
            Map<Object, Group> groups = new LinkedHashMap<>();
            for (var statement : statements) {
                Object key = statement.shapeId() != 0 ? statement.shapeId() : statement.statement();
                var group = groups.computeIfAbsent(key, ignore -> new Group(statement.statement(),
                        statement.dataType() == null ? "-" : statement.dataType().getSimpleName()));
                group.executions++;
                group.durationNanos += statement.durationNanos();
                group.fetch |= statement.origin() == StatementOrigin.FETCH;
                group.texts.add(statement.statement());
                group.rows += statement.rows();
                group.exactRows &= statement.exactRows();
                if (statement.callSite() != null) {
                    group.sites.add(statement.callSite());
                }
            }
            return groups.values().stream()
                    .map(group -> new StatementLine(group.text, group.dataType, group.fetch, group.executions,
                            group.texts.size(), group.durationNanos,
                            group.sites.isEmpty() ? null : group.sites.iterator().next(), group.sites.size(),
                            group.rows, group.exactRows,
                            hydrationOf(group.text, hydrationShapes)))
                    .sorted(comparingLong(StatementLine::durationNanos).reversed())
                    .toList();
        }

        /** One statement group under construction; the first execution seen represents the group. */
        private static final class Group {
            final String text;
            final String dataType;
            final java.util.Set<String> texts = new java.util.HashSet<>();
            final java.util.Set<String> sites = new java.util.LinkedHashSet<>();
            boolean fetch;
            int executions;
            long durationNanos;
            long rows;
            boolean exactRows = true;

            Group(String text, String dataType) {
                this.text = text;
                this.dataType = dataType;
            }
        }

        /**
         * Renders the summary with the full statement texts appended, one per row in row order, so a row whose
         * elided text is not enough can be matched to the statement it stands for.
         *
         * @return the rendered summary, followed by the full statements.
         */
        public String toDetailedString() {
            var rendered = new StringBuilder(render(name, recorded, count(StatementOrigin.FETCH), cacheHits,
                    NANOSECONDS.toMillis(databaseNanos()), NANOSECONDS.toMillis(databaseElapsedNanos()),
                    peakConcurrency(), NANOSECONDS.toMillis(durationNanos), byStatement(),
                    recorded - statements.size()));
            var lines = byStatement();
            if (!lines.isEmpty()) {
                rendered.append(String.format("%n\tstatements:"));
            }
            for (var line : lines) {
                rendered.append(String.format("%n\t  ")).append(flatten(line.statement()));
            }
            return rendered.toString();
        }

        /**
         * Returns the rendered hydration shape of the statement's type in the configured form, or {@code null}
         * when shapes are off or the statement has none.
         */
        @Nullable
        private String hydrationOf(@Nonnull String statementText, @Nonnull HydrationShapes shapes) {
            if (shapes == HydrationShapes.OFF) {
                return null;
            }
            var dataType = statements.stream()
                    .filter(statement -> statement.statement().equals(statementText))
                    .findFirst()
                    .map(Statement::dataType)
                    .orElse(null);
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
         * Renders the summary as a headline plus a line per distinct statement.
         */
        @Override
        public String toString() {
            return render(name, recorded, count(StatementOrigin.FETCH), cacheHits,
                    NANOSECONDS.toMillis(databaseNanos()), NANOSECONDS.toMillis(databaseElapsedNanos()),
                    peakConcurrency(), NANOSECONDS.toMillis(durationNanos), byStatement(),
                    recorded - statements.size());
        }
    }

    /**
     * One distinct statement within a scope, with what it cost in total.
     *
     * @param statement a representative of the group's text, with placeholders.
     * @param dataType the simple name of the entity or projection the statement targets, or {@code -} when it
     *                 targets none.
     * @param fetch whether it resolved a reference.
     * @param executions how many times it ran.
     * @param variants how many distinct texts the group covers; above one, a collection parameter expanded to a
     *                 different number of placeholders per execution.
     * @param durationNanos the summed duration of those executions.
     * @param callSite the application frame the executions came from, or {@code null} when the scope does not
     *                 record call sites; the first seen when a group covers several.
     * @param sites how many distinct call sites the group covers.
     * @param rows the rows the group's executions produced or affected, in total.
     * @param exactRows whether that count is exact; when a driver declined to report a batch entry's count or a
     *                  stream closed before its end, the count is a lower bound and the rendering marks it
     *                  {@code *}.
     * @param hydration the rendered hydration shape of the statement's type in the configured
     *                  {@link HydrationShapes} form, or {@code null} when shapes are off or the statement has no
     *                  record type.
     */
    public record StatementLine(@Nonnull String statement,
                                @Nonnull String dataType,
                                boolean fetch,
                                int executions,
                                int variants,
                                long durationNanos,
                                @Nullable String callSite,
                                int sites,
                                long rows,
                                boolean exactRows,
                                @Nullable String hydration) {
    }

    /**
     * How summary rows render the declared hydration shape of their statement's type.
     *
     * @since 1.13
     */
    public enum HydrationShapes {

        /** No shape renders. The default. */
        OFF,

        /**
         * A row whose type hydrates beyond its own table ends with the numeric shape, {@code j2 c12 d3}: joins,
         * columns, and graph depth. A flat type shows none.
         */
        SHORT,

        /** Every mapped row ends with the full shape, {@code joins=2 columns=12 graph=Pet(Owner(City))}. */
        FULL
    }

    /**
     * How shapes render, a property of the log viewer rather than of any scope: derived at rendering and cached
     * per type, the setting costs nothing while calls run. Set once at startup; read at rendering only.
     */
    private static volatile HydrationShapes hydrationShapes = HydrationShapes.OFF;

    /**
     * Sets how summary rows render the declared hydration shape of their statement's type. Off by default;
     * intended to be called once at startup.
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

    /** Statements recorded per scope before recording stops, keeping a runaway call from retaining the lot. */
    private static final int DEFAULT_LIMIT = 200;

    /**
     * Runs the action, recording the statements it executes, and hands the result to the given consumer.
     *
     * @param name what the scope covers, used to label the summary.
     * @param action the action to run.
     * @param onSummary receives the summary once the action completes, normally or not.
     * @param <T> the result type.
     * @return the action's result.
     * @throws Exception whatever the action throws.
     */
    public static <T> T recordThrowing(@Nonnull String name,
                                       @Nonnull Callable<T> action,
                                       @Nonnull Consumer<Summary> onSummary) throws Exception {
        return recordThrowing(name, DEFAULT_LIMIT, action, onSummary);
    }

    /**
     * Runs the action, recording up to {@code limit} of the statements it executes.
     *
     * @param name what the scope covers, used to label the summary.
     * @param limit the number of statements to record; the summary still counts the rest.
     * @param action the action to run.
     * @param onSummary receives the summary once the action completes, normally or not.
     * @param <T> the result type.
     * @return the action's result.
     * @throws Exception whatever the action throws.
     */
    public static <T> T recordThrowing(@Nonnull String name,
                                       int limit,
                                       @Nonnull Callable<T> action,
                                       @Nonnull Consumer<Summary> onSummary) throws Exception {
        return recordThrowing(name, limit, false, action, onSummary);
    }

    /**
     * Runs the action, recording up to {@code limit} statements and, when {@code callSites} is set, the
     * application frame each execution came from, which costs a stack walk per execution.
     *
     * @param name what the scope covers, used to label the summary.
     * @param limit the number of statements to record; the summary counts the rest regardless.
     * @param callSites whether to record call sites.
     * @param action the action to run.
     * @param onSummary receives the summary once the action completes, normally or not.
     * @param <T> the result type.
     * @return the action's result.
     * @throws Exception whatever the action throws.
     */
    public static <T> T recordThrowing(@Nonnull String name,
                                       int limit,
                                       boolean callSites,
                                       @Nonnull Callable<T> action,
                                       @Nonnull Consumer<Summary> onSummary) throws Exception {
        requireNonNull(name, "name");
        requireNonNull(action, "action");
        requireNonNull(onSummary, "onSummary");
        var recorder = new Recorder(limit, callSites);
        long started = System.nanoTime();
        try {
            return SqlInterceptorManager.listen(recorder).call(action);
        } finally {
            // A call that failed is worth summarizing too: the statements leading up to it are the evidence.
            long elapsed = System.nanoTime() - started;
            onSummary.accept(new Summary(name, List.copyOf(recorder.statements), recorder.recorded.get(),
                    recorder.cacheHits.get(), elapsed));
        }
    }

    /**
     * Runs the action, recording the statements it executes.
     *
     * @param name what the scope covers, used to label the summary.
     * @param action the action to run.
     * @param onSummary receives the summary once the action completes, normally or not.
     * @param <T> the result type.
     * @return the action's result.
     */
    public static <T> T record(@Nonnull String name,
                               @Nonnull Supplier<T> action,
                               @Nonnull Consumer<Summary> onSummary) {
        return record(name, DEFAULT_LIMIT, action, onSummary);
    }

    /**
     * Runs the action, recording up to {@code limit} of the statements it executes.
     *
     * <p>For an action that throws a checked exception, use
     * {@link #recordThrowing(String, int, Callable, Consumer)}.</p>
     *
     * @param name what the scope covers, used to label the summary.
     * @param limit the number of statements to record; the summary still counts the rest.
     * @param action the action to run.
     * @param onSummary receives the summary once the action completes, normally or not.
     * @param <T> the result type.
     * @return the action's result.
     */
    public static <T> T record(@Nonnull String name,
                               int limit,
                               @Nonnull Supplier<T> action,
                               @Nonnull Consumer<Summary> onSummary) {
        try {
            return recordThrowing(name, limit, action::get, onSummary);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // A Supplier declares none, so anything checked arriving here came from the interceptor chain.
            throw new PersistenceException(e);
        }
    }

    /**
     * Opens a scope on the calling thread, closed with try-with-resources.
     *
     * <pre>{@code
     * var scope = SqlLog.open("importOwners");
     * try (scope) {
     *     ...
     * }
     * // scope.summary() reports what the block cost
     * }</pre>
     *
     * <p>The scope must be closed on the thread that opened it, which a try-with-resources block guarantees.</p>
     *
     * @param name what the scope covers, used to label the summary.
     * @return the open scope.
     */
    public static Scope open(@Nonnull String name) {
        return open(name, DEFAULT_LIMIT);
    }

    /**
     * Opens a scope on the calling thread, recording up to {@code limit} statements.
     *
     * @param name what the scope covers, used to label the summary.
     * @param limit the number of statements to record; the summary counts the rest regardless.
     * @return the open scope.
     */
    public static Scope open(@Nonnull String name, int limit) {
        return open(name, limit, false);
    }

    /**
     * Opens a scope that additionally attributes each execution to the application frame that caused it, which
     * costs a stack walk per execution while the scope records.
     *
     * @param name what the scope covers, used to label the summary.
     * @param limit the number of statements to record; the summary counts the rest regardless.
     * @param callSites whether to record call sites.
     * @return the open scope.
     */
    public static Scope open(@Nonnull String name, int limit, boolean callSites) {
        return new Scope(requireNonNull(name, "name"), limit, callSites);
    }


    /**
     * A scope opened with {@link #open}, reporting its summary once closed.
     */
    public static final class Scope implements AutoCloseable {
        private final String name;
        private final Recorder recorder;
        private final AutoCloseable detach;
        private final long started;
        private Summary summary;

        private Scope(String name, int limit, boolean callSites) {
            this.name = name;
            this.recorder = new Recorder(limit, callSites);
            this.detach = SqlInterceptorManager.attach(recorder);
            this.started = System.nanoTime();
        }

        /**
         * Returns what the scope observed. Available once the scope is closed.
         *
         * @return the summary.
         * @throws IllegalStateException if the scope is still open.
         */
        public Summary summary() {
            if (summary == null) {
                throw new IllegalStateException("The scope is still open; the summary is available after close().");
            }
            return summary;
        }

        @Override
        public void close() {
            if (summary != null) {
                return;
            }
            long elapsed = System.nanoTime() - started;
            try {
                detach.close();
            } catch (Exception e) {
                throw new PersistenceException(e);
            }
            summary = new Summary(name, List.copyOf(recorder.statements), recorder.recorded.get(),
                    recorder.cacheHits.get(), elapsed);
        }
    }

    /**
     * Creates a recorder a caller can install itself, for a facade that binds the scope to its own execution
     * model rather than to the calling thread.
     *
     * @param limit the number of statements to record.
     * @return the recorder to install as a listener.
     */
    public static Recorder recorder(int limit) {
        return new Recorder(limit, false);
    }

    /**
     * Creates a recorder that additionally attributes each execution to the application frame that caused it.
     *
     * @param limit the number of statements to record.
     * @param callSites whether to record call sites; costs a stack walk per execution while the scope records.
     * @return the recorder to install as a listener.
     */
    public static Recorder recorder(int limit, boolean callSites) {
        return new Recorder(limit, callSites);
    }

    /**
     * Builds the summary of what the given recorder observed.
     *
     * @param name what the scope covered.
     * @param recorder the recorder that observed the executions.
     * @param durationNanos how long the scope was open.
     * @return the summary.
     */
    public static Summary summary(@Nonnull String name, @Nonnull Recorder recorder, long durationNanos) {
        return new Summary(name, List.copyOf(recorder.statements), recorder.recorded.get(),
                recorder.cacheHits.get(), durationNanos);
    }

    /**
     * Accumulates the statements of one scope.
     */
    public static final class Recorder implements StatementListener {
        private final Queue<Statement> statements = new ConcurrentLinkedQueue<>();
        private final int limit;
        private final boolean callSites;
        private final AtomicInteger recorded = new AtomicInteger();
        private final AtomicInteger cacheHits = new AtomicInteger();

        Recorder(int limit, boolean callSites) {
            this.limit = limit;
            this.callSites = callSites;
        }

        @Override
        public Handle onExecute(@Nonnull QueryContext context, @Nonnull List<Parameter> parameters) {
            if (recorded.incrementAndGet() > limit) {
                return Handle.NOOP;
            }
            var callSite = callSites ? callSite() : null;
            long start = System.nanoTime();
            var operation = context.operation();
            var dataType = context.dataType().orElse(null);
            var origin = context.origin();
            var statement = context.statement().orElse("");
            var shapeId = context.shapeId();
            return (rows, exact) -> statements.add(new Statement(operation, dataType, origin, statement, start,
                    System.nanoTime(), shapeId, callSite, rows, exact));
        }

        @Override
        public boolean callSites() {
            return callSites;
        }

        @Override
        public void onCacheHit(@Nonnull Class<? extends Data> dataType, int count) {
            cacheHits.addAndGet(count);
        }
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
    record Hydration(int joins, int columns, int depth, @Nonnull String graph) {
    }

    /** Marks a type without a mapped record structure, for which no shape renders. */
    private static final Hydration NO_HYDRATION = new Hydration(0, 0, 0, "");

    private static final ClassValue<Hydration> HYDRATION = new ClassValue<>() {
        @Override
        protected Hydration computeValue(@Nonnull Class<?> type) {
            // The reflection provider recognizes the mapped structure of Java records and Kotlin data classes
            // alike, which is the same bridge the model itself is built over.
            if (st.orm.core.spi.Providers.getORMReflection().findRecordType(type).isEmpty()) {
                return NO_HYDRATION;
            }
            var shape = shape(type, new java.util.HashSet<>());
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

    private static Shape shape(@Nonnull Class<?> type, @Nonnull java.util.Set<Class<?>> path) {
        if (!path.add(type)) {
            // A cycle recurses no further; the revisited entity contributes its foreign key column.
            return new Shape(0, 1, 0, "");
        }
        try {
            var reflection = st.orm.core.spi.Providers.getORMReflection();
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
                if (st.orm.Ref.class.isAssignableFrom(fieldType)) {
                    // The foreign key column; a reference does not widen the read.
                    columns++;
                } else if (st.orm.Entity.class.isAssignableFrom(fieldType)) {
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

    private static final StackWalker CALL_SITE_WALKER = StackWalker.getInstance();

    /**
     * The launch-site fallback for executions whose stack no longer contains the caller, such as work resumed
     * on a coroutine dispatcher. Bound by integrations that carry a scope onto another thread, alongside the
     * scope itself.
     */
    private static final ThreadLocal<String> CALL_SITE_HINT = new ThreadLocal<>();

    /**
     * Returns the thread local carrying the launch-site fallback for executions whose stack no longer contains
     * the caller.
     *
     * <p>Intended for integrations that carry a scope onto another thread, such as coroutine context elements,
     * which bind it alongside the scope. Application code should not modify it.</p>
     *
     * @return the thread local holding the current launch site.
     */
    public static ThreadLocal<String> callSiteHint() {
        return CALL_SITE_HINT;
    }

    /**
     * Returns the application frame launching work, for an integration to carry as the call-site fallback of a
     * scope that records call sites.
     *
     * <p>At the moment work is launched, the caller is still on the stack; on the thread the work resumes on,
     * it no longer is. Carrying what this returns, bound through {@link #callSiteHint()}, is what lets a
     * statement whose stack is plumbing end to end name the frame that launched the work. When the launch
     * itself has no application frame on its stack, the fallback already carried is returned, so chained
     * launches preserve the original caller.</p>
     *
     * <p>Costs a stack walk; callers gate on whether an observing scope records call sites.</p>
     *
     * @return the launching application frame, or {@code null} when there is none to carry.
     */
    @Nullable
    public static String captureCallSite() {
        var walked = walkFrames();
        return walked.application() != null ? walked.application() : CALL_SITE_HINT.get();
    }

    /**
     * Returns the application frame that caused the execution: the innermost frame that is neither framework
     * infrastructure nor declared plumbing, as {@code File.ext:line}.
     *
     * <p>When every application frame on the stack is declared plumbing, the carried launch site is returned
     * when one is bound, since it names the caller the stack lost; the innermost plumbing frame otherwise, as a
     * plumbing site still says more than none.</p>
     */
    @Nullable
    private static String callSite() {
        var walked = walkFrames();
        if (walked.application() != null) {
            return walked.application();
        }
        String hint = CALL_SITE_HINT.get();
        if (hint != null) {
            return hint;
        }
        return walked.plumbing();
    }

    /** The two frames a walk can surface: the first application frame, and the innermost plumbing frame. */
    private record WalkedFrames(@Nullable String application, @Nullable String plumbing) {
    }

    private static WalkedFrames walkFrames() {
        return CALL_SITE_WALKER.walk(frames -> {
            String plumbing = null;
            for (var iterator = frames.iterator(); iterator.hasNext(); ) {
                var frame = iterator.next();
                if (isInfrastructure(frame.getClassName())) {
                    continue;
                }
                if (isDeclaredPlumbing(frame.getClassName(), frame.getFileName())) {
                    if (plumbing == null) {
                        plumbing = format(frame);
                    }
                    continue;
                }
                return new WalkedFrames(format(frame), plumbing);
            }
            return new WalkedFrames(null, plumbing);
        });
    }

    private static String format(@Nonnull StackWalker.StackFrame frame) {
        String file = frame.getFileName();
        return file != null
                ? "%s:%d".formatted(file, frame.getLineNumber())
                : "%s.%s".formatted(frame.getClassName(), frame.getMethodName());
    }

    /**
     * Returns whether the frame belongs to a package or source file the application declared as plumbing.
     *
     * <p>Entries naming a source file match the frame's file, which is what covers inline functions: inlining
     * regenerates a lambda under the caller's class, where a package prefix cannot see it, while the frame keeps
     * the declaring file's name.</p>
     */
    private static boolean isDeclaredPlumbing(@Nonnull String className, @Nullable String fileName) {
        for (var entry : ignoredCallSitePrefixes) {
            if (entry.endsWith(".kt") || entry.endsWith(".java")) {
                if (entry.equals(fileName)) {
                    return true;
                }
            } else if (className.startsWith(entry)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Package prefixes the application declared as its own database plumbing, so call sites name the code that
     * asked for the work rather than the layer that carried it. Copy-on-write: registered once at startup, read
     * on every walk.
     */
    private static volatile String[] ignoredCallSitePrefixes = {};

    /**
     * Declares packages whose frames are skipped when attributing an execution to a call site.
     *
     * <p>A database layer of the application's own, such as a wrapper that fans a query out over several
     * templates, sits between the caller and Storm on every statement; its frames identify the plumbing rather
     * than the code that asked for the work. Declaring its packages here makes call sites name the caller
     * beyond it.</p>
     *
     * <p>An entry is a package prefix matched against the fully qualified class name, or, when it ends in
     * {@code .kt} or {@code .java}, a source file name matched against the frame's file. The file form covers
     * inline functions, whose lambdas are regenerated under the caller's class while keeping the declaring
     * file's name. When every application frame on a stack is declared plumbing, the innermost plumbing frame is
     * reported rather than none. Intended to be called once at startup.</p>
     *
     * @param packagePrefixes the package prefixes or source file names to skip, such as {@code "com.acme.db"} or
     *                        {@code "DbExtensions.kt"}.
     */
    public static void ignoreCallSites(@Nonnull String... packagePrefixes) {
        var merged = new ArrayList<>(List.of(ignoredCallSitePrefixes));
        for (var prefix : packagePrefixes) {
            merged.add(requireNonNull(prefix, "packagePrefix"));
        }
        ignoredCallSitePrefixes = merged.toArray(String[]::new);
    }

    private static boolean isInfrastructure(@Nonnull String className) {
        if (className.startsWith("st.orm.")
                || className.startsWith("java.")
                || className.startsWith("jdk.")
                || className.startsWith("sun.")
                || className.startsWith("kotlin.")
                || className.startsWith("kotlinx.")
                || className.startsWith("org.springframework.")
                || className.startsWith("org.apache.")
                || className.startsWith("io.ktor.")
                || className.startsWith("io.netty.")
                || className.startsWith("org.eclipse.jetty.")) {
            return true;
        }
        return false;
    }

    /**
     * Applies the display settings from configuration ({@link StormConfig#defaults()}, which reads system
     * properties). How the log renders is a property of the deployment, so it is configured like one —
     * {@code storm.sql_log.hydration}, {@code storm.sql_log.line_width}, {@code storm.sql_log.call_site_skip} —
     * rather than through an API. The Spring and Ktor integrations apply their own configuration through the
     * setters.
     */
    private static void applyConfiguredDisplaySettings() {
        var config = StormConfig.defaults();
        hydrationShapes(getEnum(config, StormConfig.SQL_LOG_HYDRATION, HydrationShapes.class, HydrationShapes.OFF));
        lineWidth(getInt(config, StormConfig.SQL_LOG_LINE_WIDTH, 200));
        String skip = config.getProperty(StormConfig.SQL_LOG_CALL_SITE_SKIP);
        if (skip != null) {
            ignoreCallSites(Arrays.stream(skip.split(","))
                    .map(String::trim)
                    .filter(entry -> !entry.isEmpty())
                    .toArray(String[]::new));
        }
    }

    // Placed after every field it touches, since static initialization runs in textual order.
    static {
        applyConfiguredDisplaySettings();
    }
}
