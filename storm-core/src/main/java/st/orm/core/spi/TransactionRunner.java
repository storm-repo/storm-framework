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
import jakarta.annotation.Nullable;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.List;
import st.orm.Transaction;
import st.orm.TransactionPropagation;
import st.orm.TransactionTimedOutException;
import st.orm.UnexpectedRollbackException;

/**
 * Executes transactional blocks on the pending-scope engine: the single source of the blocking transaction
 * orchestration, driven by the Java {@code Transactions} API and Kotlin's {@code transactionBlocking}.
 *
 * <p>Opening a block only records the requested options as a {@link TransactionScope}; the transaction binds to
 * the first ORM template that executes inside the block. A block that never touches a template completes as a
 * no-op, with deadline and inherited-rollback checks still enforced deterministically.</p>
 *
 * <p>The Kotlin suspend flow keeps its own coroutine-shaped orchestration, reusing
 * {@link #afterSuccessfulCompletion(TransactionScope)} and
 * {@link #completeAfterFailure(TransactionScope, Throwable)} and installing its callback holder in
 * {@link #callbacksHolder()} so blocking blocks nested inside suspend blocks defer their callbacks to the
 * owning physical transaction.</p>
 *
 * @since 1.13
 */
public final class TransactionRunner {

    /**
     * A transactional block. Checked exceptions propagate to the caller unchanged and trigger rollback.
     *
     * @param <R> the result type.
     * @param <E> the checked exception type thrown by the block, if any.
     */
    @FunctionalInterface
    public interface Block<R, E extends Exception> {
        R execute(@Nonnull Transaction transaction) throws E;
    }

    /**
     * Holds the callbacks sink of the current physical transaction for the blocking flow. Exposed so coroutine
     * integrations can propagate it across dispatcher hops as a context element.
     */
    private static final ThreadLocal<TransactionCallbacks> CURRENT_CALLBACKS = new ThreadLocal<>();

    private TransactionRunner() {
    }

    /**
     * Returns the thread local holding the callbacks sink of the current physical transaction.
     */
    public static ThreadLocal<TransactionCallbacks> callbacksHolder() {
        return CURRENT_CALLBACKS;
    }

    /**
     * Executes the given block within a transaction scope built from the given options.
     *
     * @param options the fully resolved transaction options.
     * @param block the transactional logic to execute.
     * @return the result of the block.
     * @param <R> the result type.
     * @param <E> the checked exception type thrown by the block, if any.
     */
    public static <R, E extends Exception> R execute(@Nonnull TransactionScope.Options options,
                                                     @Nonnull Block<R, E> block) throws E {
        var scope = TransactionScope.open(options);
        var parentCallbacks = CURRENT_CALLBACKS.get();
        if (isJoining(options.propagation()) && scope.parent() != null && parentCallbacks != null) {
            // Joining an outer transaction block: delegate callbacks to the outer holder; do not fire here.
            R result;
            try {
                result = block.execute(scopeTransaction(scope, parentCallbacks));
            } catch (Throwable e) {
                try {
                    throw sneakyThrow(completeAfterFailure(scope, e));
                } finally {
                    scope.close();
                }
            }
            try {
                scope.complete(false);
                afterSuccessfulCompletion(scope);
            } finally {
                scope.close();
            }
            return result;
        }
        // Owner of the callback lifecycle: outermost block, REQUIRES_NEW / NOT_SUPPORTED, or no parent
        // callbacks.
        var callbacks = new BlockingCallbacks();
        var previousCallbacks = parentCallbacks;
        CURRENT_CALLBACKS.set(callbacks);
        R value;
        boolean rollbackOnly;
        try {
            value = block.execute(scopeTransaction(scope, callbacks));
            rollbackOnly = scope.isRollbackOnly();
        } catch (Throwable e) {
            // Restore and uninstall the scope before firing so callbacks see a clean state.
            restoreCallbacks(previousCallbacks);
            Throwable wrapped;
            try {
                wrapped = completeAfterFailure(scope, e);
            } catch (Throwable completionException) {
                wrapped = completionException;
            } finally {
                scope.close();
            }
            try {
                callbacks.fireRollback();
            } catch (Throwable callbackException) {
                wrapped.addSuppressed(callbackException);
            }
            throw sneakyThrow(wrapped);
        }
        // Restore and uninstall the scope before firing so callbacks see a clean state.
        restoreCallbacks(previousCallbacks);
        try {
            try {
                scope.complete(false);
                afterSuccessfulCompletion(scope);
            } finally {
                scope.close();
            }
        } catch (Throwable completionException) {
            try {
                callbacks.fireRollback();
            } catch (Throwable callbackException) {
                completionException.addSuppressed(callbackException);
            }
            throw sneakyThrow(completionException);
        }
        if (rollbackOnly) {
            callbacks.fireRollback();
        } else {
            callbacks.fireCommit();
        }
        return value;
    }

    /**
     * Post-completion checks that mirror the semantics of eagerly entered transaction frames.
     *
     * <p>A scope that never touched a template still fails deterministically when its deadline has passed,
     * marking a joined outer scope rollback-only. A scope whose rollback-only mark was inherited from a joined
     * inner scope raises an {@link UnexpectedRollbackException} when its block attempts to commit; materialized
     * frames raise this from the provider's commit path, this check covers marks that were propagated at the
     * scope level.</p>
     */
    public static void afterSuccessfulCompletion(@Nonnull TransactionScope scope) {
        if (!scope.isMaterialized() && scope.isDeadlineExpired()) {
            var propagation = scope.options().propagation();
            boolean joining = propagation == null
                    || propagation == TransactionPropagation.REQUIRED
                    || propagation == TransactionPropagation.SUPPORTS
                    || propagation == TransactionPropagation.MANDATORY;
            if (joining && scope.parent() != null) {
                scope.parent().setRollbackOnly();
            }
            throw new TransactionTimedOutException("Did not complete within timeout.");
        }
        if (scope.isRollbackInherited()) {
            throw new UnexpectedRollbackException("Transaction was marked rollback-only by a joined scope.");
        }
    }

    /**
     * Completes the scope after a failed block and returns the exception to throw: the original failure, or a
     * {@link TransactionTimedOutException} when the failure was caused by a statement timeout. Completion
     * failures are suppressed onto the original failure.
     */
    public static Throwable completeAfterFailure(@Nonnull TransactionScope scope, @Nonnull Throwable e) {
        String description;
        try {
            var context = scope.materializedContext();
            description = context == null ? null : context.describe().orElse(null);
        } catch (Throwable ignore) {
            description = null;
        }
        try {
            scope.complete(true); // Suppresses rollback exceptions internally to surface the original error.
        } catch (Throwable completionException) {
            e.addSuppressed(completionException);
        }
        if (e.getCause() instanceof SQLTimeoutException) {
            var base = e.getMessage() == null ? "Did not complete within timeout." : e.getMessage();
            return new TransactionTimedOutException(description != null ? base + " [" + description + "]" : base, e);
        }
        return e;
    }

    private static boolean isJoining(@Nullable TransactionPropagation propagation) {
        if (propagation == null) {
            return true; // Provider default is REQUIRED.
        }
        return switch (propagation) {
            case REQUIRED, SUPPORTS, MANDATORY, NESTED -> true;
            case REQUIRES_NEW, NOT_SUPPORTED, NEVER -> false;
        };
    }

    private static void restoreCallbacks(@Nullable TransactionCallbacks previous) {
        if (previous == null) {
            CURRENT_CALLBACKS.remove();
        } else {
            CURRENT_CALLBACKS.set(previous);
        }
    }

    private static Transaction scopeTransaction(@Nonnull TransactionScope scope,
                                                @Nonnull TransactionCallbacks callbacks) {
        return new Transaction() {
            @Override
            public boolean isRollbackOnly() {
                return scope.isRollbackOnly();
            }

            @Override
            public void setRollbackOnly() {
                scope.setRollbackOnly();
            }

            @Override
            public void onCommit(@Nonnull Runnable callback) {
                callbacks.addOnCommit(callback);
            }

            @Override
            public void onRollback(@Nonnull Runnable callback) {
                callbacks.addOnRollback(callback);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneakyThrow(Throwable t) throws E {
        throw (E) t;
    }

    /**
     * Collects and executes transaction lifecycle callbacks in registration order. If a callback throws,
     * remaining callbacks still execute; the first exception is surfaced with subsequent ones added as
     * suppressed.
     */
    private static final class BlockingCallbacks implements TransactionCallbacks {
        private final List<Runnable> onCommit = new ArrayList<>();
        private final List<Runnable> onRollback = new ArrayList<>();

        @Override
        public void addOnCommit(@Nonnull Runnable callback) {
            onCommit.add(callback);
        }

        @Override
        public void addOnRollback(@Nonnull Runnable callback) {
            onRollback.add(callback);
        }

        void fireCommit() {
            fire(onCommit);
        }

        void fireRollback() {
            fire(onRollback);
        }

        private static void fire(List<Runnable> callbacks) {
            Throwable first = null;
            for (var callback : callbacks) {
                try {
                    callback.run();
                } catch (Throwable e) {
                    if (first == null) {
                        first = e;
                    } else {
                        first.addSuppressed(e);
                    }
                }
            }
            if (first != null) {
                throw sneakyThrow(first);
            }
        }
    }
}
