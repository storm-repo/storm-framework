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
package st.orm.core.spi;


/**
 * Observes query executions performed by an ORM template.
 *
 * <p>Query observers are designed to back metrics and tracing bindings, such as Micrometer Observations. They are
 * configured per ORM template via the template builder; they are deliberately not discovered through the
 * {@code ServiceLoader} mechanism.</p>
 *
 * <p>Observer failures are contained by the framework and never affect query execution: an exception thrown by an
 * observer is logged and discarded, and the query result or failure is delivered to the caller unchanged.</p>
 *
 * @see QueryContext
 * @since 1.13
 */
public interface QueryObserver {

    /**
     * Called when a statement execution starts.
     *
     * <p>The returned observation is closed exactly once: for updates and batches when execution completes; for
     * result streams when the stream is closed.</p>
     *
     * @param context describes the statement execution; never {@code null}.
     * @return the observation tracking this execution; never {@code null}.
     */
    Observation onExecute(QueryContext context);

    /**
     * Called when a physical transaction opens: an outermost transaction block, or a {@code REQUIRES_NEW}
     * block. Joined blocks and savepoint scopes are not physical transactions and are not observed.
     *
     * <p>The default implementation ignores transactions.</p>
     *
     * @param options the options the transaction was opened with; never {@code null}.
     * @return the observation tracking this transaction; never {@code null}.
     * @since 1.13
     */
    default TransactionObservation onTransaction(TransactionScope.Options options) {
        return TransactionObservation.NOOP;
    }

    /**
     * Tracks a single observed physical transaction.
     *
     * @since 1.13
     */
    interface TransactionObservation {

        /**
         * An observation that ignores all signals.
         */
        TransactionObservation NOOP = new TransactionObservation() {
            @Override
            public void error(Throwable throwable) {
                // Ignore.
            }

            @Override
            public void close(boolean rolledBack) {
                // Ignore.
            }
        };

        /**
         * Signals that completing the observed transaction failed.
         *
         * <p>Invoked at most once, before {@link #close(boolean)}.</p>
         *
         * @param throwable the failure.
         */
        void error(Throwable throwable);

        /**
         * Signals that the observed transaction has completed.
         *
         * <p>Invoked exactly once per observation.</p>
         *
         * @param rolledBack whether the transaction rolled back rather than committed.
         */
        void close(boolean rolledBack);
    }

    /**
     * Tracks a single observed statement execution.
     *
     * @since 1.13
     */
    interface Observation {

        /**
         * An observation that ignores all signals.
         */
        Observation NOOP = new Observation() {
            @Override
            public void error(Throwable throwable) {
                // Ignore.
            }

            @Override
            public void close() {
                // Ignore.
            }
        };

        /**
         * Signals that the observed execution failed.
         *
         * <p>Invoked at most once, before {@link #close()}.</p>
         *
         * @param throwable the failure.
         */
        void error(Throwable throwable);

        /**
         * Signals that the observed execution has completed.
         *
         * <p>Invoked exactly once per observation.</p>
         */
        void close();
    }

    /**
     * Returns an observer that ignores all executions.
     *
     * @return the no-op query observer.
     */
    /**
     * Observer that ignores all executions. Exposed as a constant so hot paths can skip context creation with an
     * identity check.
     */
    QueryObserver NOOP = context -> Observation.NOOP;

    static QueryObserver noop() {
        return NOOP;
    }
}
