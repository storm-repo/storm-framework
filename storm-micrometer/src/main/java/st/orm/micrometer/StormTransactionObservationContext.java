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
import io.micrometer.observation.Observation;
import org.jspecify.annotations.Nullable;
import st.orm.core.spi.TransactionScope;

/**
 * {@link Observation.Context} for a Storm transaction.
 *
 * <p>Carries the {@link TransactionScope.Options} the transaction was opened with, so custom
 * {@link io.micrometer.observation.ObservationConvention}s and
 * {@link io.micrometer.observation.ObservationHandler}s can derive their own names, key values or attributes from
 * the transaction's configuration. The outcome is set when the transaction completes, before the observation
 * stops, so conventions read it when their key values are collected.</p>
 *
 * @since 1.14
 */
public class StormTransactionObservationContext extends Observation.Context {

    private final TransactionScope.Options options;
    private final KeyValues extraLowCardinalityKeyValues;
    private @Nullable Boolean rolledBack;

    public StormTransactionObservationContext(TransactionScope.Options options,
                                              KeyValues extraLowCardinalityKeyValues) {
        this.options = requireNonNull(options, "options");
        this.extraLowCardinalityKeyValues = requireNonNull(extraLowCardinalityKeyValues, "extraLowCardinalityKeyValues");
    }

    /**
     * Returns the options the observed transaction was opened with.
     *
     * @return the transaction options; never {@code null}.
     */
    public TransactionScope.Options options() {
        return options;
    }

    /**
     * Returns the additional low-cardinality key values configured on the observer, such as
     * {@code storm.database} naming the template the observation came from.
     *
     * @return the extra low-cardinality key values; never {@code null}.
     */
    public KeyValues extraLowCardinalityKeyValues() {
        return extraLowCardinalityKeyValues;
    }

    /**
     * Returns whether the observed transaction rolled back rather than committed.
     *
     * @return {@code true} for a rollback, {@code false} for a commit, or {@code null} while the transaction has
     * not completed yet.
     */
    public @Nullable Boolean rolledBack() {
        return rolledBack;
    }

    /**
     * Records the outcome of the observed transaction.
     *
     * @param rolledBack whether the transaction rolled back rather than committed.
     */
    public void setRolledBack(boolean rolledBack) {
        this.rolledBack = rolledBack;
    }
}
