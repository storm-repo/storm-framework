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

import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;

/**
 * Default {@link ObservationConvention} for Storm transaction observations.
 *
 * <p>Observations are named {@code storm.transaction} with a contextual name of {@code transaction}. The
 * low-cardinality key values are suitable as metric tags:</p>
 *
 * <ul>
 *   <li>{@code storm.tx.propagation} — the propagation the transaction was opened with, such as {@code REQUIRED}
 *   or {@code REQUIRES_NEW}</li>
 *   <li>{@code storm.tx.read_only} — whether the transaction was opened read-only</li>
 *   <li>{@code storm.tx.outcome} — {@code committed} or {@code rolled_back}; {@code unknown} until the
 *   transaction completes</li>
 * </ul>
 *
 * <p>Extend this class or supply your own {@link ObservationConvention} to the
 * {@link MicrometerQueryObserver} to customize naming and key values.</p>
 *
 * @since 1.14
 */
public class StormTransactionObservationConvention implements ObservationConvention<StormTransactionObservationContext> {

    /**
     * The observation name for Storm transactions.
     */
    public static final String OBSERVATION_NAME = "storm.transaction";

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof StormTransactionObservationContext;
    }

    @Override
    public String getName() {
        return OBSERVATION_NAME;
    }

    @Override
    public String getContextualName(StormTransactionObservationContext context) {
        return "transaction";
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(StormTransactionObservationContext context) {
        var options = context.options();
        var rolledBack = context.rolledBack();
        return KeyValues.of(
                        "storm.tx.propagation",
                        options.propagation() != null ? options.propagation().name() : "REQUIRED",
                        "storm.tx.read_only",
                        String.valueOf(Boolean.TRUE.equals(options.readOnly())),
                        // "unknown" rather than an absent key: meters of one name must share a single set of tag
                        // keys, and the key values are also collected at start, before the outcome exists.
                        "storm.tx.outcome",
                        rolledBack == null ? "unknown" : rolledBack ? "rolled_back" : "committed")
                .and(context.extraLowCardinalityKeyValues());
    }
}
