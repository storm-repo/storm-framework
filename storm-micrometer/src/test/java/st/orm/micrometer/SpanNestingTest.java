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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.test.simple.SimpleTracer;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import st.orm.Data;
import st.orm.spi.QueryContext;
import st.orm.spi.SqlOperation;

/**
 * Verifies that Storm query observations become spans that nest under the enclosing observation, which is how they
 * appear under a server request span when tracing handlers (micrometer-tracing or an OpenTelemetry bridge) are
 * attached to the registry.
 */
public class SpanNestingTest {

    private record FakeQueryContext(SqlOperation operation) implements QueryContext {
        @Override
        public Optional<Class<? extends Data>> dataType() {
            return Optional.empty();
        }

        @Override
        public ExecutionKind kind() {
            return ExecutionKind.QUERY;
        }

        @Override
        public OptionalInt batchSize() {
            return OptionalInt.empty();
        }

        @Override
        public Optional<String> statement() {
            return Optional.of("SELECT 1");
        }
    }

    @Test
    public void querySpanNestsUnderTheEnclosingObservation() {
        var tracer = new SimpleTracer();
        var registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new DefaultTracingObservationHandler(tracer));

        var observer = new MicrometerQueryObserver(registry);
        var serverObservation = Observation.start("http.server.request", registry);
        try (var ignored = serverObservation.openScope()) {
            observer.onExecute(new FakeQueryContext(SqlOperation.SELECT)).close();
        } finally {
            serverObservation.stop();
        }

        var spans = tracer.getSpans();
        assertEquals(2, spans.size(), "Expected the server span and the Storm query span.");
        var serverSpan = spans.stream()
                .filter(span -> "http.server.request".equals(span.getName()))
                .findFirst().orElseThrow();
        var querySpan = spans.stream()
                .filter(span -> !"http.server.request".equals(span.getName()))
                .findFirst().orElseThrow();
        assertNotNull(querySpan.getParentId(), "The query span must have a parent.");
        assertEquals(serverSpan.getSpanId(), querySpan.getParentId(),
                "The Storm query span must nest under the enclosing server span.");
    }
}
