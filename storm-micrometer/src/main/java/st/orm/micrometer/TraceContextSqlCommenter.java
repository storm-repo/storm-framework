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
package st.orm.micrometer;

import static java.util.Objects.requireNonNull;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.Nonnull;
import java.util.Optional;
import st.orm.core.spi.SqlCommenter;

/**
 * {@link SqlCommenter} that appends the current trace context to SQL statements, following the sqlcommenter
 * convention with a W3C {@code traceparent} value: {@code traceparent='00-{traceId}-{spanId}-{flags}'}.
 *
 * <p>Database-side diagnostics that capture statements — slow query logs, statement views — then carry the
 * trace identity, so a captured statement correlates directly to the trace that issued it, and the trace to
 * the exact database-side execution.</p>
 *
 * <p>Statements execute uncommented when no span is current. Note that a per-execution comment changes the
 * statement text on every call, which defeats driver-side and server-side prepared statement caching; enable
 * selectively where the correlation is worth that trade-off.</p>
 *
 * @since 1.13
 */
public class TraceContextSqlCommenter implements SqlCommenter {

    private final Tracer tracer;
    private final boolean onlySampled;

    /**
     * Creates a commenter that comments every statement executed inside a span.
     *
     * @param tracer the tracer providing the current trace context.
     */
    public TraceContextSqlCommenter(@Nonnull Tracer tracer) {
        this(tracer, false);
    }

    /**
     * Creates a commenter that optionally comments only statements of sampled traces.
     *
     * <p>With sampling below 1.0, commenting every statement pays the prepared statement caching cost for
     * comments whose trace is mostly not exported; sampled-only mode aligns the cost with the correlation
     * benefit.</p>
     *
     * @param tracer the tracer providing the current trace context.
     * @param onlySampled whether to comment only when the current span is sampled.
     */
    public TraceContextSqlCommenter(@Nonnull Tracer tracer, boolean onlySampled) {
        this.tracer = requireNonNull(tracer, "tracer");
        this.onlySampled = onlySampled;
    }

    @Override
    public Optional<String> comment() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return Optional.empty();
        }
        TraceContext context = span.context();
        if (onlySampled && !Boolean.TRUE.equals(context.sampled())) {
            return Optional.empty();
        }
        String flags = Boolean.TRUE.equals(context.sampled()) ? "01" : "00";
        return Optional.of("traceparent='00-%s-%s-%s'".formatted(context.traceId(), context.spanId(), flags));
    }
}
