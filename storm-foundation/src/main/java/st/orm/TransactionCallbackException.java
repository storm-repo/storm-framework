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
package st.orm;

/**
 * Thrown when a transaction completion callback fails.
 *
 * <p>Callbacks run after the transaction has completed, so this exception reports a failed side effect rather
 * than a failed transaction. {@link #isCommitted()} says which completion it followed: {@code true} when the
 * transaction committed, {@code false} when it rolled back.</p>
 *
 * <p>Catching this type is what lets a caller tell "the work was not persisted" apart from "the work was
 * persisted and something after it failed". The two need opposite responses: the first is a candidate for a
 * retry, the second usually is not, because retrying repeats work that already succeeded.</p>
 *
 * <p>When several callbacks fail, the first failure is the {@linkplain #getCause() cause} and the remaining
 * ones are attached to it as suppressed exceptions. A callback failure that follows a rollback caused by
 * another exception is not thrown on its own: it is attached to that exception as suppressed, still wrapped in
 * this type.</p>
 *
 * @since 1.13
 */
public class TransactionCallbackException extends PersistenceException {

    private final boolean committed;

    public TransactionCallbackException(String message, Throwable cause, boolean committed) {
        super(message, cause);
        this.committed = committed;
    }

    /**
     * Whether the transaction committed before the failing callback ran.
     *
     * @return {@code true} when the transaction committed, {@code false} when it rolled back.
     */
    public boolean isCommitted() {
        return committed;
    }
}
