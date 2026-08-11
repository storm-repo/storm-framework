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
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import st.orm.core.spi.SqlCommenter;

/**
 * {@link SqlCommenter} that appends the current trace context to SQL statements, following the sqlcommenter
 * convention with a W3C {@code traceparent} value: {@code traceparent='00-{traceId}-{spanId}-{flags}'}.
 *
 * <p>Database-side diagnostics that capture statements — slow query logs, statement views — then carry the
 * trace identity, so a captured statement correlates directly to the trace that issued it, and the trace to
 * the exact database-side execution.</p>
 *
 * <p>Statements execute uncommented when the current span carries no usable trace identity: no span is
 * current, the tracer is a no-op, or the context's identifiers are absent or malformed. A comment is only
 * worth its cost when it correlates to something, and an identifier that no W3C parser accepts correlates to
 * nothing. Note that a per-execution comment changes the statement text on every call, which defeats
 * driver-side and server-side prepared statement caching; enable selectively where the correlation is worth
 * that trade-off.</p>
 *
 * @since 1.13
 */
public class TraceContextSqlCommenter implements SqlCommenter {

    /** Length of a W3C trace id in hex characters; a 64-bit trace id occupies the low-order half. */
    private static final int TRACE_ID_LENGTH = 32;

    /** Length of a W3C parent id in hex characters. */
    private static final int SPAN_ID_LENGTH = 16;

    /** Length of the trace id a tracer configured for 64-bit trace identifiers reports. */
    private static final int SHORT_TRACE_ID_LENGTH = 16;

    private final Tracer tracer;
    private final boolean onlySampled;

    /**
     * Creates a commenter that comments every statement executed inside a span.
     *
     * @param tracer the tracer providing the current trace context.
     */
    public TraceContextSqlCommenter(Tracer tracer) {
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
    public TraceContextSqlCommenter(Tracer tracer, boolean onlySampled) {
        this.tracer = requireNonNull(tracer, "tracer");
        this.onlySampled = onlySampled;
    }

    @Override
    public Optional<String> comment() {
        Span span = tracer.currentSpan();
        // A no-op tracer reports a non-null span whose context carries empty identifiers.
        if (span == null || span.isNoop()) {
            return Optional.empty();
        }
        TraceContext context = span.context();
        if (onlySampled && !Boolean.TRUE.equals(context.sampled())) {
            return Optional.empty();
        }
        String traceId = traceId(context.traceId());
        String spanId = spanId(context.spanId());
        if (traceId == null || spanId == null) {
            return Optional.empty();
        }
        String flags = Boolean.TRUE.equals(context.sampled()) ? "01" : "00";
        return Optional.of("traceparent='00-%s-%s-%s'".formatted(traceId, spanId, flags));
    }

    /**
     * Returns the trace id in W3C form, or {@code null} when it carries no trace identity.
     *
     * <p>A tracer configured for 64-bit trace identifiers reports half a W3C trace id; the W3C form of such an
     * identifier is left-padded with zeros, so padding renders it rather than discarding it.</p>
     */
    @Nullable
    private static String traceId(@Nullable String traceId) {
        if (traceId == null) {
            return null;
        }
        String rendered = switch (traceId.length()) {
            case SHORT_TRACE_ID_LENGTH -> "0".repeat(TRACE_ID_LENGTH - SHORT_TRACE_ID_LENGTH) + traceId;
            case TRACE_ID_LENGTH -> traceId;
            default -> null;
        };
        return isIdentity(rendered) ? rendered : null;
    }

    /**
     * Returns the span id when it is a usable W3C parent id, {@code null} otherwise.
     */
    @Nullable
    private static String spanId(@Nullable String spanId) {
        if (spanId == null || spanId.length() != SPAN_ID_LENGTH) {
            return null;
        }
        return isIdentity(spanId) ? spanId : null;
    }

    /**
     * Returns whether the value is lowercase hex and not all zeros, which W3C reserves as the invalid
     * identifier.
     */
    private static boolean isIdentity(@Nullable String value) {
        if (value == null) {
            return false;
        }
        boolean nonZero = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
                return false;
            }
            nonZero |= c != '0';
        }
        return nonZero;
    }
}
