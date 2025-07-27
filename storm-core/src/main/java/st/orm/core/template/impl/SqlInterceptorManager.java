/*
 * Copyright 2024 - 2025 the original author or authors.
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

import jakarta.annotation.Nonnull;
import st.orm.PersistenceException;
import st.orm.core.template.Sql;
import st.orm.core.template.SqlTemplate;

import java.util.ArrayDeque;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.util.Collections.newSetFromMap;

/**
 * Manages SQL interceptors.
 *
 * @since 1.1
 */
@SuppressWarnings("ALL")
public final class SqlInterceptorManager {

    record Operator(UnaryOperator<Sql> interceptor, UnaryOperator<SqlTemplate> customizer) {
        Operator(UnaryOperator<Sql> interceptor) {
            this(interceptor, it -> it);
        }
    }

    public interface Carrier {
        void run(@Nonnull Runnable runnable);
        <R> R call(@Nonnull Callable<? extends R> op) throws Exception;
        <R> R get(@Nonnull Supplier<? extends R> op);
    }

    private static class CarrierImpl implements Carrier {
        private final Operator operator;

        public CarrierImpl(Operator operator) {
            this.operator = operator;
        }

        @Override
        public void run(@Nonnull Runnable runnable) {
            var operators = LOCAL_OPERATORS.get();
            LOCAL_OPERATORS.set(operators);
            try {
                operators.addFirst(operator);
                runnable.run();
            } finally {
                operators.removeFirst();
                if (operators.isEmpty()) {
                    // Clear the thread-local to prevent memory leaks.
                    LOCAL_OPERATORS.remove();
                }
            }
        }

        @Override
        public <R> R call(@Nonnull Callable<? extends R> op) throws Exception {
            var operators = LOCAL_OPERATORS.get();
            LOCAL_OPERATORS.set(operators);
            try {
                operators.addFirst(operator);
                return op.call();
            } finally {
                operators.removeFirst();
                if (operators.isEmpty()) {
                    // Clear the thread-local to prevent memory leaks.
                    LOCAL_OPERATORS.remove();
                }
            }
        }

        @Override
        public <R> R get(@Nonnull Supplier<? extends R> op) {
            var operators = LOCAL_OPERATORS.get();
            LOCAL_OPERATORS.set(operators);
            try {
                operators.addFirst(operator);
                return op.get();
            } finally {
                operators.removeFirst();
                if (operators.isEmpty()) {
                    // Clear the thread-local to prevent memory leaks.
                    LOCAL_OPERATORS.remove();
                }
            }
        }
    }

    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();
    private static final Set<Object> GLOBAL_OPERATORS = newSetFromMap(new IdentityHashMap<>());

    private static final ThreadLocal<Deque<Operator>> LOCAL_OPERATORS = ThreadLocal.withInitial(() -> new ArrayDeque<>(4));

    private SqlInterceptorManager() {
    }

    /**
     * Register a global interceptor that will be called for all SQL statements.
     *
     * @param interceptor the interceptor to call for each SQL statement.
     */
    public static void registerGlobalInterceptor(@Nonnull UnaryOperator<Sql> interceptor) {
        LOCK.writeLock().lock();
        try {
            GLOBAL_OPERATORS.add(interceptor);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Register a global observer that will be called for all SQL statements.
     *
     * @param observer the observer to call for each SQL statement.
     */
    public static void registerGlobalObserver(@Nonnull Consumer<Sql> observer) {
        LOCK.writeLock().lock();
        try {
            GLOBAL_OPERATORS.add(observer);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Unregister a global observer.
     *
     * @param observer the observer to unregister.
     */
    public static void unregisterGlobalObserver(@Nonnull UnaryOperator<Sql> observer) {
        LOCK.writeLock().lock();
        try {
            GLOBAL_OPERATORS.remove(observer);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Unregister a global observer.
     *
     * @param observer the observer to unregister.
     */
    public static void unregisterGlobalObserver(@Nonnull Consumer<Sql> observer) {
        LOCK.writeLock().lock();
        try {
            GLOBAL_OPERATORS.remove(observer);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Create a new scoped interceptor that applies an operator to SQL statements processed by the current thread and
     * any child threads.
     *
     * <p>This interceptor is scoped to the current thread context and propagates only to its child threads.
     * It is isolated from sibling threads, meaning changes made to the interceptor set will not affect other threads
     * that share the same parent scope.</p>
     *
     * @param operator the operator to apply to each SQL statement.
     * @return a {@link Carrier} that binds the interceptor to the current thread's scoped context.
     */
    public static Carrier intercept(@Nonnull UnaryOperator<Sql> operator) {
        return new CarrierImpl(new Operator(operator));
    }

    /**
     * Create a new scoped interceptor that applies an operator to SQL statements processed by the current thread and
     * any child threads.
     *
     * <p>This interceptor is scoped to the current thread context and propagates only to its child threads.
     * It is isolated from sibling threads, meaning changes made to the interceptor set will not affect other threads
     * that share the same parent scope.</p>
     *
     * @param customizer a function to customize the SQL template before use.
     * @param operator the operator to apply to each SQL statement.
     * @return a {@link Carrier} that binds the interceptor to the current thread's scoped context.
     * @since 1.3
     */
    public static Carrier intercept(@Nonnull UnaryOperator<SqlTemplate> customizer, @Nonnull UnaryOperator<Sql> operator) {
        return new CarrierImpl(new Operator(operator, customizer));
    }

    /**
     * Create a new scoped interceptor that applies an operator to SQL statements processed by the current thread and
     * any child threads.
     *
     * <p>This interceptor is scoped to the current thread context and propagates only to its child threads.
     * It is isolated from sibling threads, meaning changes made to the interceptor set will not affect other threads
     * that share the same parent scope.</p>
     *
     * @param observer the observer to invoke with each SQL statement.
     * @return a {@link Carrier} that binds the interceptor to the current thread's scoped context.
     */
    public static Carrier intercept(@Nonnull Consumer<Sql> observer) {
        return new CarrierImpl(new Operator(sql -> {
            observer.accept(sql);
            return sql;
        }));
    }

    /**
     * Create a new scoped interceptor that applies an operator to SQL statements processed by the current thread and
     * any child threads.
     *
     * <p>This interceptor is scoped to the current thread context and propagates only to its child threads.
     * It is isolated from sibling threads, meaning changes made to the interceptor set will not affect other threads
     * that share the same parent scope.</p>
     *
     * @param observer the observer to invoke with each SQL statement.
     * @return a {@link Carrier} that binds the interceptor to the current thread's scoped context.
     * @since 1.3
     */
    public static Carrier intercept(@Nonnull UnaryOperator<SqlTemplate> customizer, @Nonnull Consumer<Sql> observer) {
        return new CarrierImpl(new Operator(sql -> {
            observer.accept(sql);
            return sql;
        }, customizer));
    }

    /**
     * Customizes the given SQL template using the current thread's scoped customizer, if available.
     *
     * <p>This method applies a customizer to the SQL template that is scoped to the current thread context.
     * If no customizer is set, it returns the original template.</p>
     *
     * <p>This method is intended to be used internally within the ORM framework, or it's extensions, to ensure that
     * SQL templates are adjusted according to the current thread's context, such as applying custom SQL dialects or
     * other template modifications.</p>
     *
     * @param template the SQL template to customize.
     * @return the customized SQL template, or the original template if no customizer is set.
     */
    public static SqlTemplate customize(@Nonnull SqlTemplate template) {
        // Apply the customizer to the template if it is set.
        SqlTemplate adjusted = template;
        // The local operators are not protected by a lock, but that is fine since they are locally scoped. However,
        // they must not modify the local operators from the accept/apply method.
        try {
            for (var operator : LOCAL_OPERATORS.get()) {
                adjusted = operator.customizer().apply(adjusted);
            }
        } catch (ConcurrentModificationException e) {
            throw new PersistenceException("Registering interceptors from within their execution scope is not allowed.");
        }
        return adjusted;
    }

    /**
     * Intercepts the specified SQL statement by calling all globally and locally registered interceptors.
     *
     * @param sql the SQL statement to intercept.
     * @return the adjusted SQL statement.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static Sql intercept(@Nonnull Sql sql) {
        Sql adjusted = sql;
        // The local operators are not protected by a lock, but that is fine since they are locally scoped. However,
        // they must not modify the local operators from the accept/apply method.
        try {
            for (var operator : LOCAL_OPERATORS.get()) {
                adjusted = operator.interceptor().apply(adjusted);
            }
        } catch (ConcurrentModificationException e) {
            throw new PersistenceException("Registering interceptors from within their execution scope is not allowed.");
        }
        LOCK.readLock().lock();
        try {
            for (var operator : GLOBAL_OPERATORS) {
                if (operator instanceof Consumer c) {
                    c.accept(adjusted);
                } else if (operator instanceof UnaryOperator o) {
                    adjusted = (Sql) o.apply(adjusted);
                }
            }
        } finally {
            LOCK.readLock().unlock();
        }
        return adjusted;
    }
}
