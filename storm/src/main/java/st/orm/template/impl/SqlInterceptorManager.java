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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.PersistenceException;
import st.orm.template.Sql;

import java.lang.ScopedValue.Carrier;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static java.lang.ScopedValue.newInstance;
import static java.util.Collections.newSetFromMap;
import static java.util.Collections.synchronizedList;

/**
 * Manages SQL interceptors.
 *
 * @since 1.1
 */
public final class SqlInterceptorManager {

    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();
    private static final Set<Object> GLOBAL_OPERATORS = newSetFromMap(new IdentityHashMap<>());

    private static final ScopedValue<List<UnaryOperator<Sql>>> LOCAL_OPERATORS = newInstance();

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
        var operators = synchronizedList(new ArrayList<>(LOCAL_OPERATORS.orElse(List.of())));
        operators.addFirst(operator);
        return ScopedValue.where(LOCAL_OPERATORS, operators);
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
        var operators = synchronizedList(new ArrayList<>(LOCAL_OPERATORS.orElse(List.of())));
        operators.addFirst(sql -> {
            observer.accept(sql);
            return sql;
        });
        return ScopedValue.where(LOCAL_OPERATORS, operators);
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
            for (var operator : LOCAL_OPERATORS.orElse(List.of())) {
                adjusted = operator.apply(adjusted);
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
