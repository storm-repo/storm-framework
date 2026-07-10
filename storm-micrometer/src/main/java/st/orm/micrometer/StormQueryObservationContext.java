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
import jakarta.annotation.Nonnull;
import st.orm.core.spi.QueryContext;

/**
 * {@link Observation.Context} for a Storm query execution.
 *
 * <p>Carries the {@link QueryContext} of the observed execution, so custom
 * {@link io.micrometer.observation.ObservationConvention}s and
 * {@link io.micrometer.observation.ObservationHandler}s can derive their own names, key values or attributes from
 * the full execution context.</p>
 *
 * @since 1.13
 */
public class StormQueryObservationContext extends Observation.Context {

    private final QueryContext queryContext;
    private final KeyValues extraLowCardinalityKeyValues;

    public StormQueryObservationContext(@Nonnull QueryContext queryContext,
                                        @Nonnull KeyValues extraLowCardinalityKeyValues) {
        this.queryContext = requireNonNull(queryContext, "queryContext");
        this.extraLowCardinalityKeyValues = requireNonNull(extraLowCardinalityKeyValues, "extraLowCardinalityKeyValues");
    }

    /**
     * Returns the query execution context being observed.
     *
     * @return the query context; never {@code null}.
     */
    public QueryContext queryContext() {
        return queryContext;
    }

    /**
     * Returns the additional low-cardinality key values configured on the observer, such as the database name of a
     * multi-database setup.
     *
     * @return the extra low-cardinality key values; never {@code null}.
     */
    public KeyValues extraLowCardinalityKeyValues() {
        return extraLowCardinalityKeyValues;
    }
}
