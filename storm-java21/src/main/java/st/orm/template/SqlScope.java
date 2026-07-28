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
package st.orm.template;

import static st.orm.core.template.SqlScope.HydrationShapes.*;

import jakarta.annotation.Nonnull;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Records the statements a call executes, so a unit of work can be judged by what it cost the database.
 *
 * <p>A scope covers whatever executes inside it, whichever repository, query builder or template issued the
 * statement, so it can wrap the handling of a request rather than a single repository:</p>
 *
 * <pre>{@code
 * var view = SqlScope.record("getOwner",
 *         () -> ownerService.load(id),
 *         summary -> LOGGER.info("{}", summary));
 * }</pre>
 *
 * <p><strong>Cost when inactive is zero.</strong> A scope registers on the interceptor chain that every statement
 * already walks, so a statement executed with no scope open reads a single counter and stops.</p>
 *
 * <p>A scope follows the thread that opened it. Work handed to another thread, including a subtask forked from a
 * {@code StructuredTaskScope}, falls outside it.</p>
 *
 * @since 1.13
 */
public final class SqlScope {

    private SqlScope() {
    }

    /**
     * Runs the action, recording the statements it executes, and hands the summary to the given consumer.
     *
     * @param name what the scope covers, used to label the summary.
     * @param action the action to run.
     * @param onSummary receives the summary once the action completes, normally or not.
     * @param <T> the result type.
     * @return the action's result.
     */
    public static <T> T record(@Nonnull String name,
                               @Nonnull Supplier<T> action,
                               @Nonnull Consumer<SqlSummary> onSummary) {
        return st.orm.core.template.SqlScope.record(name, action, summary -> onSummary.accept(convert(summary)));
    }

    /**
     * Runs the action, recording up to {@code limit} of the statements it executes.
     *
     * @param name what the scope covers, used to label the summary.
     * @param limit the number of statements to record; the summary counts the rest regardless.
     * @param action the action to run.
     * @param onSummary receives the summary once the action completes, normally or not.
     * @param <T> the result type.
     * @return the action's result.
     */
    public static <T> T record(@Nonnull String name,
                               int limit,
                               @Nonnull Supplier<T> action,
                               @Nonnull Consumer<SqlSummary> onSummary) {
        return st.orm.core.template.SqlScope.record(name, limit, action,
                summary -> onSummary.accept(convert(summary)));
    }

    /**
     * Runs the action, recording the statements it executes, allowing checked exceptions.
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
                                       @Nonnull Consumer<SqlSummary> onSummary) throws Exception {
        return st.orm.core.template.SqlScope.recordThrowing(name, action,
                summary -> onSummary.accept(convert(summary)));
    }

    /**
     * Runs the action, recording up to {@code limit} of the statements it executes, allowing checked exceptions.
     *
     * @param name what the scope covers, used to label the summary.
     * @param limit the number of statements to record; the summary counts the rest regardless.
     * @param action the action to run.
     * @param onSummary receives the summary once the action completes, normally or not.
     * @param <T> the result type.
     * @return the action's result.
     * @throws Exception whatever the action throws.
     */
    public static <T> T recordThrowing(@Nonnull String name,
                                       int limit,
                                       @Nonnull Callable<T> action,
                                       @Nonnull Consumer<SqlSummary> onSummary) throws Exception {
        return st.orm.core.template.SqlScope.recordThrowing(name, limit, action,
                summary -> onSummary.accept(convert(summary)));
    }

    /**
     * Opens a scope on the calling thread, closed with try-with-resources.
     *
     * <pre>{@code
     * var scope = SqlScope.open("importOwners");
     * try (scope) {
     *     ownerService.importAll(batch);
     * }
     * LOGGER.info("{}", scope.summary());
     * }</pre>
     *
     * <p>The scope must be closed on the thread that opened it, which a try-with-resources block guarantees.</p>
     *
     * @param name what the scope covers, used to label the summary.
     * @return the open scope.
     */
    public static Scope open(@Nonnull String name) {
        return new Scope(st.orm.core.template.SqlScope.open(name));
    }

    /**
     * Opens a scope on the calling thread, recording up to {@code limit} statements.
     *
     * @param name what the scope covers, used to label the summary.
     * @param limit the number of statements to record; the summary counts the rest regardless.
     * @return the open scope.
     */
    public static Scope open(@Nonnull String name, int limit) {
        return new Scope(st.orm.core.template.SqlScope.open(name, limit));
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
        return new Scope(st.orm.core.template.SqlScope.open(name, limit, callSites));
    }


    /**
     * Sets how summary rows render the declared hydration shape of their statement's type. Off by default; a
     * display property of the deployment, intended to be called once at startup.
     *
     * @param shapes how shapes render.
     */
    public static void hydrationShapes(@Nonnull HydrationShapes shapes) {
        st.orm.core.template.SqlScope.hydrationShapes(switch (shapes) {
            case OFF -> OFF;
            case SHORT -> SHORT;
            case FULL -> FULL;
        });
    }

    public static void lineWidth(int width) {
        st.orm.core.template.SqlScope.lineWidth(width);
    }

    /**
     * A scope opened with {@link #open}, reporting its summary once closed.
     */
    public static final class Scope implements AutoCloseable {
        private final st.orm.core.template.SqlScope.Scope scope;

        private Scope(st.orm.core.template.SqlScope.Scope scope) {
            this.scope = scope;
        }

        /**
         * Returns what the scope observed. Available once the scope is closed.
         *
         * @return the summary.
         * @throws IllegalStateException if the scope is still open.
         */
        public SqlSummary summary() {
            return convert(scope.summary());
        }

        @Override
        public void close() {
            scope.close();
        }
    }

    /**
     * Declares packages or source files whose frames are skipped when a scope attributes an execution to a
     * call site, so rows name the code that asked for the work rather than the application's own database
     * plumbing. An entry ending in {@code .kt} or {@code .java} matches the frame's source file, which is what
     * covers inline functions; when every application frame on a stack is declared plumbing, the innermost
     * plumbing frame is reported rather than none. Intended to be called once at startup.
     *
     * @param packagePrefixes the package prefixes or source file names to skip, such as {@code "com.acme.db"}
     *                        or {@code "DbExtensions.kt"}.
     */
    public static void ignoreCallSites(@Nonnull String... packagePrefixes) {
        st.orm.core.template.SqlScope.ignoreCallSites(packagePrefixes);
    }

    /** Wraps the core summary in the one an application reads, which carries no core type on its surface. */
    static SqlSummary convert(@Nonnull st.orm.core.template.SqlScope.Summary summary) {
        return new SqlSummary(summary);
    }
}
