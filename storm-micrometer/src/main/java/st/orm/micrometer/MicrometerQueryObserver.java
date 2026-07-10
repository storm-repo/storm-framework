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

import io.micrometer.common.KeyValues;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.Nonnull;
import st.orm.core.spi.QueryContext;
import st.orm.core.spi.QueryObserver;

/**
 * {@link QueryObserver} that reports Storm query executions as Micrometer
 * {@link io.micrometer.observation.Observation}s.
 *
 * <p>Configure it on the template builder, or let an integration wire it: the Ktor plugin binds this observer
 * automatically when an {@link ObservationRegistry} is available through the application's dependency injection.
 * Depending on the handlers attached to the registry, each query execution produces timing metrics, tracing spans,
 * or both. Spans nest under the current trace context, so with context propagation in place (for example the
 * OpenTelemetry agent) Storm queries appear under the active request span.</p>
 *
 * <p>Naming and key values come from the {@link StormQueryObservationConvention} by default; supply a custom
 * {@link ObservationConvention} to override. Extra low-cardinality key values, such as the database name in a
 * multi-database setup, are appended to every observation.</p>
 *
 * @since 1.13
 */
public class MicrometerQueryObserver implements QueryObserver {

    private final ObservationRegistry observationRegistry;
    private final ObservationConvention<StormQueryObservationContext> convention;
    private final KeyValues extraLowCardinalityKeyValues;

    /**
     * Creates an observer reporting to the given registry with the default convention.
     *
     * @param observationRegistry the registry to report observations to.
     */
    public MicrometerQueryObserver(@Nonnull ObservationRegistry observationRegistry) {
        this(observationRegistry, KeyValues.empty());
    }

    /**
     * Creates an observer reporting to the given registry with the default convention and extra low-cardinality
     * key values appended to every observation.
     *
     * @param observationRegistry the registry to report observations to.
     * @param extraLowCardinalityKeyValues extra key values, such as {@code storm.database} in a multi-database
     *                                     setup.
     */
    public MicrometerQueryObserver(@Nonnull ObservationRegistry observationRegistry,
                                   @Nonnull KeyValues extraLowCardinalityKeyValues) {
        this(observationRegistry, new StormQueryObservationConvention(), extraLowCardinalityKeyValues);
    }

    /**
     * Creates an observer reporting to the given registry with a custom convention.
     *
     * @param observationRegistry the registry to report observations to.
     * @param convention the convention that names the observations and derives their key values.
     * @param extraLowCardinalityKeyValues extra key values, exposed to the convention via
     *                                     {@link StormQueryObservationContext#extraLowCardinalityKeyValues()}.
     */
    public MicrometerQueryObserver(@Nonnull ObservationRegistry observationRegistry,
                                   @Nonnull ObservationConvention<StormQueryObservationContext> convention,
                                   @Nonnull KeyValues extraLowCardinalityKeyValues) {
        this.observationRegistry = requireNonNull(observationRegistry, "observationRegistry");
        this.convention = requireNonNull(convention, "convention");
        this.extraLowCardinalityKeyValues = requireNonNull(extraLowCardinalityKeyValues, "extraLowCardinalityKeyValues");
    }

    @Override
    public Observation onExecute(@Nonnull QueryContext context) {
        var observationContext = new StormQueryObservationContext(context, extraLowCardinalityKeyValues);
        var observation = io.micrometer.observation.Observation
                .createNotStarted(convention, () -> observationContext, observationRegistry)
                .start();
        return new Observation() {
            @Override
            public void error(@Nonnull Throwable throwable) {
                observation.error(throwable);
            }

            @Override
            public void close() {
                observation.stop();
            }
        };
    }
}
