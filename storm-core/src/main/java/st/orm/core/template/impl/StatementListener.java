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
package st.orm.core.template.impl;

import java.util.List;
import st.orm.Data;
import st.orm.core.template.SqlTemplate.Parameter;
import st.orm.spi.QueryContext;

/**
 * Observes statement executions on behalf of a scope.
 *
 * <p>A scope reports what a call cost, which is a property of executions rather than of the statements a call
 * builds: a statement carries no duration until it runs, and one built statement can run many times. A listener is
 * therefore notified around execution, and travels on the interceptor chain so that it reaches the scope wherever
 * the work runs.</p>
 *
 * @since 1.13
 */
public interface StatementListener {

    /**
     * Called when a statement starts executing.
     *
     * <p>The parameters are the values bound to the statement. They stay off {@link QueryContext} deliberately:
     * observers report to external systems and must never see values, while a listener serves a scope the caller
     * opened around its own work, such as a test capture.</p>
     *
     * @param context describes the execution; never {@code null}.
     * @param parameters the values bound to the statement; never {@code null}.
     * @return the handle closed when the execution completes; never {@code null}.
     */
    Handle onExecute(QueryContext context, List<Parameter> parameters);

    /**
     * Returns whether this listener attributes executions to call sites, which is what lets an integration
     * skip capturing a launch site when nothing would use it.
     *
     * @return {@code true} when the listener records call sites.
     */
    default boolean callSites() {
        return false;
    }

    /**
     * Called when reads were served from the transaction's entity cache without a statement: a reference
     * resolving to an entity the transaction had already read, or an identity lookup at {@code REPEATABLE_READ}
     * and above.
     *
     * @param dataType the entity type the cache served.
     * @param count how many reads the cache served; at least one.
     */
    default void onCacheHit(Class<? extends Data> dataType, int count) {
    }

    /**
     * Tracks one observed execution.
     */
    interface Handle {

        /** A handle that records nothing. */
        Handle NOOP = (rows, exact) -> {
        };

        /**
         * Signals that the statement returned from the database: the execute call completed, with a result set
         * opened, an update count reported, or a batch acknowledged. What the execution cost the database is the
         * time up to this point; for a stream, what follows is fetch round trips interleaved with consumption.
         * Invoked exactly once per handle, before {@link #close}, on the thread that executed the statement, so
         * a listener that attributes executions can still see the caller on the stack. When the execute call
         * itself fails, it is invoked immediately before the close that reports the failure.
         */
        default void executed() {
        }

        /**
         * Signals that the execution failed. Invoked at most once per handle, before {@link #close}.
         *
         * @param throwable what the execution failed with.
         */
        default void error(Throwable throwable) {
        }

        /**
         * Signals that the execution has completed. Invoked exactly once per handle.
         *
         * @param rows the rows the execution produced or affected; a lower bound when not exact.
         * @param exact whether the count is exact: false when a driver declined to report a batch entry's
         *              count, or a stream closed before its end.
         */
        void close(long rows, boolean exact);
    }
}
