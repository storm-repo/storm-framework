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

import jakarta.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * Sink for transaction lifecycle callbacks of the current physical transaction.
 *
 * <p>Callbacks registered while a block joins an outer transaction are deferred to the holder that owns the
 * physical transaction. Language layers may store the callbacks in richer forms (the Kotlin suspend flow keeps
 * suspend lambdas and executes them in the enclosing coroutine context); this interface is the language-neutral
 * registration contract used by the blocking flow.</p>
 *
 * @see TransactionRunner
 * @since 1.13
 */
public interface TransactionCallbacks {

    /**
     * Registers a callback invoked after the physical transaction commits successfully.
     *
     * @param callback the callback to invoke after commit.
     */
    void addOnCommit(@Nonnull Runnable callback);

    /**
     * Registers a callback invoked after the physical transaction rolls back.
     *
     * @param callback the callback to invoke after rollback.
     */
    void addOnRollback(@Nonnull Runnable callback);

    /**
     * Registers a callback invoked after the physical transaction completes, receiving {@code true} when the
     * transaction committed and {@code false} when it rolled back.
     *
     * <p>Implementations keep this callback in the same order as the commit-only and rollback-only callbacks, so
     * that a transaction's callbacks run in registration order regardless of which kind they are.</p>
     *
     * @param callback the callback to invoke after completion.
     */
    void addOnCompletion(@Nonnull Consumer<Boolean> callback);
}
