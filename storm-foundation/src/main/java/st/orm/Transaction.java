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
 * Handle to the transaction a transactional block runs in: exposes the rollback-only state and registration of
 * completion callbacks.
 *
 * <p>This is the language-neutral base handle. The Kotlin API extends it with suspend-friendly callback
 * overloads.</p>
 *
 * @since 1.13
 */
public interface Transaction {

    /**
     * Whether the transaction is marked rollback-only.
     */
    boolean isRollbackOnly();

    /**
     * Marks the transaction rollback-only: the block completes normally, but the transaction rolls back.
     */
    void setRollbackOnly();

    /**
     * Registers a callback invoked after the physical transaction commits successfully.
     *
     * <p>If this scope is joined to an outer transaction (for example via {@link TransactionPropagation#REQUIRED}
     * or {@link TransactionPropagation#NESTED}), the callback is deferred to the outermost physical transaction's
     * commit. {@link TransactionPropagation#REQUIRES_NEW} scopes fire their own callbacks independently.</p>
     *
     * <p>Multiple callbacks execute in registration order. If a callback throws, remaining callbacks still
     * execute; the first exception is surfaced with the others attached as suppressed.</p>
     *
     * @param callback the callback to invoke after commit.
     */
    void onCommit(Runnable callback);

    /**
     * Registers a callback invoked after the physical transaction rolls back.
     *
     * <p>Rollback may be triggered by an exception, {@link #setRollbackOnly()}, or a timeout. Deferral and
     * ordering semantics match {@link #onCommit(Runnable)}.</p>
     *
     * @param callback the callback to invoke after rollback.
     */
    void onRollback(Runnable callback);
}
