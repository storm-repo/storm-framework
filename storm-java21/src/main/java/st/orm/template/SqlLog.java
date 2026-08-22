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

import org.jspecify.annotations.Nullable;

/**
 * Records the statements a call executes, so a unit of work can be judged by what it cost the database.
 *
 * <p>A scope covers whatever executes inside it, whichever repository, query builder or template issued the
 * statement, so it can wrap the handling of a request rather than a single repository:</p>
 *
 * <pre>{@code
 * try (var scope = SqlLog.open("getOwner")) {
 *     ownerService.load(id);
 * }
 * }</pre>
 *
 * <p>The summary reports through the {@code st.orm.sql.perf} logger when the scope closes, and the logger is the
 * only switch: statements are recorded only while it is enabled at {@code INFO}, and at {@code TRACE} the full
 * statement texts follow the summary. What a scope observed is a report, not an API: production numbers belong to
 * the Micrometer observations, and test assertions to {@code SqlCapture}.</p>
 *
 * <p><strong>Cost when inactive is zero.</strong> A scope registers on the interceptor chain that every statement
 * already walks, so a statement executed with no scope open reads a single counter and stops.</p>
 *
 * <p>A scope follows the thread that opened it. Work handed to another thread, including a subtask forked from a
 * {@code StructuredTaskScope}, falls outside it.</p>
 *
 * <p>How summaries render — line width, call-site skips — is a property of the deployment, configured rather
 * than programmed: the {@code storm.sql_log.performance.line_width} and
 * {@code storm.sql_log.call_site_skip} system properties on a plain JVM, or the corresponding keys of the Spring
 * and Ktor integrations.</p>
 *
 * @since 1.13
 */
public final class SqlLog {

    private SqlLog() {
    }

    /**
     * Opens a scope on the calling thread, closed with try-with-resources.
     *
     * <pre>{@code
     * try (var scope = SqlLog.open("importOwners")) {
     *     ownerService.importAll(batch);
     * }
     * }</pre>
     *
     * <p>The scope must be closed on the thread that opened it, which a try-with-resources block guarantees.
     * Closing reports the summary under {@code st.orm.sql.perf}; a scope whose summary nothing consumes, because
     * that logger is disabled, records nothing.</p>
     *
     * @param name what the scope covers, used to label the summary.
     * @return the open scope.
     */
    public static Scope open(String name) {
        return new Scope(st.orm.core.template.SqlLog.reporting()
                ? st.orm.core.template.SqlLog.open(name)
                : null);
    }

    /**
     * Opens a scope on the calling thread, recording up to {@code limit} statements.
     *
     * @param name what the scope covers, used to label the summary.
     * @param limit the number of statements to record; the summary counts the rest regardless.
     * @return the open scope.
     */
    public static Scope open(String name, int limit) {
        return new Scope(st.orm.core.template.SqlLog.reporting()
                ? st.orm.core.template.SqlLog.open(name, limit)
                : null);
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
    public static Scope open(String name, int limit, boolean callSites) {
        return new Scope(st.orm.core.template.SqlLog.reporting()
                ? st.orm.core.template.SqlLog.open(name, limit, callSites)
                : null);
    }

    /**
     * A scope opened with {@link #open}, reporting its summary once closed.
     */
    public static final class Scope implements AutoCloseable {

        /** The recording scope, or {@code null} when the summary would reach nothing. */
        private final st.orm.core.template.SqlLog.@Nullable Scope scope;

        private Scope(st.orm.core.template.SqlLog.@Nullable Scope scope) {
            this.scope = scope;
        }

        @Override
        public void close() {
            if (scope == null) {
                return;
            }
            scope.close();
            st.orm.core.template.SqlLog.report(scope.summary());
        }
    }
}
