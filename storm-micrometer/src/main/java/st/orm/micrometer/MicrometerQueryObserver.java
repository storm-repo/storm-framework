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

import static io.micrometer.observation.Observation.*;
import static java.util.Objects.requireNonNull;

import io.micrometer.common.KeyValues;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import st.orm.core.spi.QueryContext;
import st.orm.core.spi.QueryObserver;
import st.orm.core.spi.TransactionScope;

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
 * <p>Naming and key values come from the {@link StormQueryObservationConvention} and
 * {@link StormTransactionObservationConvention} by default; supply a custom {@link ObservationConvention} to
 * override either. Extra low-cardinality key values, such as the database name in a multi-database setup, are
 * appended to every observation.</p>
 *
 * @since 1.13
 */
public class MicrometerQueryObserver implements QueryObserver {

    private final ObservationRegistry observationRegistry;
    private final ObservationConvention<StormQueryObservationContext> convention;
    private final ObservationConvention<StormTransactionObservationContext> transactionConvention;
    private final KeyValues extraLowCardinalityKeyValues;

    /**
     * Creates an observer reporting to the given registry with the default conventions.
     *
     * @param observationRegistry the registry to report observations to.
     */
    public MicrometerQueryObserver(ObservationRegistry observationRegistry) {
        this(observationRegistry, KeyValues.empty());
    }

    /**
     * Creates an observer reporting to the given registry with the default conventions and extra low-cardinality
     * key values appended to every observation.
     *
     * @param observationRegistry the registry to report observations to.
     * @param extraLowCardinalityKeyValues extra key values, such as {@code storm.database} in a multi-database
     *                                     setup.
     */
    public MicrometerQueryObserver(ObservationRegistry observationRegistry,
                                   KeyValues extraLowCardinalityKeyValues) {
        this(observationRegistry, new StormQueryObservationConvention(), extraLowCardinalityKeyValues);
    }

    /**
     * Creates an observer reporting to the given registry with a custom query convention.
     *
     * @param observationRegistry the registry to report observations to.
     * @param convention the convention that names the query observations and derives their key values.
     * @param extraLowCardinalityKeyValues extra key values, exposed to the conventions via
     *                                     {@link StormQueryObservationContext#extraLowCardinalityKeyValues()}.
     */
    public MicrometerQueryObserver(ObservationRegistry observationRegistry,
                                   ObservationConvention<StormQueryObservationContext> convention,
                                   KeyValues extraLowCardinalityKeyValues) {
        this(observationRegistry, convention, new StormTransactionObservationConvention(), extraLowCardinalityKeyValues);
    }

    /**
     * Creates an observer reporting to the given registry with custom query and transaction conventions.
     *
     * @param observationRegistry the registry to report observations to.
     * @param convention the convention that names the query observations and derives their key values.
     * @param transactionConvention the convention that names the transaction observations and derives their key
     *                              values.
     * @param extraLowCardinalityKeyValues extra key values, exposed to the conventions via
     *                                     {@link StormQueryObservationContext#extraLowCardinalityKeyValues()} and
     *                                     {@link StormTransactionObservationContext#extraLowCardinalityKeyValues()}.
     * @since 1.14
     */
    public MicrometerQueryObserver(ObservationRegistry observationRegistry,
                                   ObservationConvention<StormQueryObservationContext> convention,
                                   ObservationConvention<StormTransactionObservationContext> transactionConvention,
                                   KeyValues extraLowCardinalityKeyValues) {
        this.observationRegistry = requireNonNull(observationRegistry, "observationRegistry");
        this.convention = requireNonNull(convention, "convention");
        this.transactionConvention = requireNonNull(transactionConvention, "transactionConvention");
        this.extraLowCardinalityKeyValues = requireNonNull(extraLowCardinalityKeyValues, "extraLowCardinalityKeyValues");
    }

    @Override
    public TransactionObservation onTransaction(TransactionScope.Options options) {
        if (observationRegistry.isNoop()) {
            return TransactionObservation.NOOP;
        }
        var observationContext = new StormTransactionObservationContext(options, extraLowCardinalityKeyValues);
        var observation = createNotStarted(transactionConvention, () -> observationContext, observationRegistry)
                .start();
        return new TransactionObservation() {
            @Override
            public void error(Throwable throwable) {
                observation.error(throwable);
            }

            @Override
            public void close(boolean rolledBack) {
                // Set before stop: the convention's key values are collected when the observation stops.
                observationContext.setRolledBack(rolledBack);
                observation.stop();
            }
        };
    }

    @Override
    public Observation onExecute(QueryContext context) {
        if (observationRegistry.isNoop()) {
            // Nothing handles the observation, so skip building the context it would have carried.
            return Observation.NOOP;
        }
        var observationContext = new StormQueryObservationContext(context, extraLowCardinalityKeyValues);
        var observation = createNotStarted(convention, () -> observationContext, observationRegistry)
                .start();
        return new Observation() {
            @Override
            public void error(Throwable throwable) {
                observation.error(throwable);
            }

            @Override
            public void close() {
                observation.stop();
            }
        };
    }
}
