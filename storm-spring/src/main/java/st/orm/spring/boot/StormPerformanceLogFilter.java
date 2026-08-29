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
package st.orm.spring.boot;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.web.filter.OncePerRequestFilter;
import st.orm.core.template.SqlLog;

/**
 * Reports what each request cost the database, as one summary per request.
 *
 * <p>A request is the boundary every web application already has, so a scope placed here needs no annotation and
 * covers whatever the request touched, whichever repository, query builder or template issued the statement. The
 * summary says how many statements it took, how long the database spent on them against how long the request
 * took, and which statement carried the weight.</p>
 *
 * <pre>{@code
 * SQL (GET /owners/42): 12 statements, 8 fetches, 34 ms in database, 96 ms total
 * 	18 ms  112 rows  4x  Pet           SELECT p.id, p.name FROM pet p WHERE p.owner_id = ?
 * 	 9 ms    8 rows  8x  City  fetch   SELECT c.id, c.name FROM city c WHERE c.id = ?
 * }</pre>
 *
 * <p>Enabled with {@code storm.sql-log.performance.enabled=true}. Statements are recorded only while the summary logger is
 * enabled, so a disabled logger costs nothing beyond the check.</p>
 *
 * <p><strong>Covers the statements the request thread issues.</strong> A request is a thread boundary, so this
 * reports what runs on that thread and what Storm hands over from it. Work an application dispatches to a
 * coroutine it builds itself is a different unit of execution: a coroutine inherits its parent's context and not
 * the parent thread's thread locals, so its statements fall outside this scope. An application whose work runs in
 * coroutines opens the scope inside the coroutine with {@code st.orm.template.sqlLog} instead, where every child
 * inherits it, or passes {@code st.orm.template.sqlLogContext()} to the coroutine it builds.</p>
 *
 * <p>This filter is wiring shared by both language stacks, so it reads the core scope rather than a stack's own
 * summary type; an application reaching for a scope directly uses {@code st.orm.template.SqlLog} instead.</p>
 *
 * @since 1.13
 */
public class StormPerformanceLogFilter extends OncePerRequestFilter implements PerformanceLog.Boundary {

    /** Read per request, so a replacement takes effect on the request after it. */
    private volatile PerformanceLog.Settings settings;

    public StormPerformanceLogFilter(int limit) {
        this(limit, false, null, null);
    }

    /**
     * Creates a filter that reports only requests exceeding a threshold, at WARN, which is the guardrail form
     * suited to production; both thresholds {@code null} reports every request that touches the database, at
     * INFO.
     *
     * @param limit the number of statements to record per request.
     * @param statementThreshold number of statements above which a request is reported, or {@code null}.
     * @param durationThreshold request duration above which a request is reported, or {@code null}.
     */
    public StormPerformanceLogFilter(int limit,
                               @Nullable Integer statementThreshold,
                               @Nullable Duration durationThreshold) {
        this(limit, false, statementThreshold, durationThreshold);
    }

    /**
     * Creates a filter that additionally attributes each execution to the application frame that caused it,
     * which costs a stack walk per execution; suited to development.
     *
     * @param limit the number of statements to record per request.
     * @param callSites whether to record call sites.
     * @param statementThreshold number of statements above which a request is reported, or {@code null}.
     * @param durationThreshold request duration above which a request is reported, or {@code null}.
     */
    public StormPerformanceLogFilter(int limit,
                               boolean callSites,
                               @Nullable Integer statementThreshold,
                               @Nullable Duration durationThreshold) {
        this.settings = new PerformanceLog.Settings(limit, callSites, statementThreshold, durationThreshold);
    }

    @Override
    public PerformanceLog.Settings settings() {
        return settings;
    }

    @Override
    public void settings(PerformanceLog.Settings settings) {
        this.settings = settings;
    }

    @Override
    public String boundaryName() {
        return "request";
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Read once, so a replacement mid-request cannot report a request against settings it was not recorded
        // under.
        var settings = this.settings;
        if (!PerformanceLog.consumes(settings)) {
            // Nothing consumes the summary, so do not open a scope to build one.
            chain.doFilter(request, response);
            return;
        }
        String name = "%s %s".formatted(request.getMethod(), request.getRequestURI());
        try {
            SqlLog.recordThrowing(name, settings.limit(), settings.callSites(), () -> {
                chain.doFilter(request, response);
                return null;
            }, summary -> PerformanceLog.report(summary, settings));
        } catch (ServletException | IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
