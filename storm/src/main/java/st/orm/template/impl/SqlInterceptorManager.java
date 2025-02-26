/*
 * Copyright 2024 the original author or authors.
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
import st.orm.template.Sql;
import st.orm.template.SqlInterceptor;

import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.SequencedSet;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static java.util.Collections.newSetFromMap;

/**
 * Manages SQL interceptors.
 *
 * @since 1.1
 */
public final class SqlInterceptorManager {

    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();
    private static final Set<Object> GLOBAL_OPERATORS = newSetFromMap(new IdentityHashMap<>());

    private static final ThreadLocal<SequencedSet<UnaryOperator<Sql>>> LOCAL_OPERATORS =
            ThreadLocal.withInitial(LinkedHashSet::new);

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
     * Register a global consumer that will be called for all SQL statements.
     *
     * @param consumer the consumer to call for each SQL statement.
     */
    public static void registerGlobalConsumer(@Nonnull Consumer<Sql> consumer) {
        LOCK.writeLock().lock();
        try {
            GLOBAL_OPERATORS.add(consumer);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Unregister a global consumer.
     *
     * @param consumer the consumer to unregister.
     */
    public static void unregisterGlobalConsumer(@Nonnull UnaryOperator<Sql> consumer) {
        LOCK.writeLock().lock();
        try {
            GLOBAL_OPERATORS.remove(consumer);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Unregister a global interceptor.
     *
     * @param consumer the consumer to unregister.
     */
    public static void unregisterGlobalConsumer(@Nonnull Consumer<Sql> consumer) {
        LOCK.writeLock().lock();
        try {
            GLOBAL_OPERATORS.remove(consumer);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Create a new interceptor that will be called for SQL statements processed by the current thread. The interceptor
     * must be closed when it is no longer needed. It is recommended to use a try-with-resources block to ensure that
     * the interceptor is closed.
     *
     * @param operator the operator to call for each SQL statement.
     * @return the interceptor.
     */
    public static SqlInterceptor create(@Nonnull UnaryOperator<Sql> operator) {
        class SqlInterceptorImpl implements SqlInterceptor, UnaryOperator<Sql> {
            @Override
            public Sql apply(Sql sql) {
                return operator.apply(sql);
            }

            @Override
            public void close() {
                LOCAL_OPERATORS.get().remove(this);
            }
        }
        var interceptor = new SqlInterceptorImpl();
        LOCAL_OPERATORS.get().addFirst(interceptor);
        return MonitoredResource.wrap(interceptor);
    }

    /**
     * Create a new interceptor that will be called for SQL statements processed by the current thread. The interceptor
     * must be closed when it is no longer needed. It is recommended to use a try-with-resources block to ensure that
     * the interceptor is closed.
     *
     * @param consumer the consumer to call for each SQL statement.
     * @return the interceptor.
     */
    public static SqlInterceptor create(@Nonnull Consumer<Sql> consumer) {
        class SqlInterceptorImpl implements SqlInterceptor, UnaryOperator<Sql> {
            @Override
            public Sql apply(Sql sql) {
                consumer.accept(sql);
                return sql;
            }

            @Override
            public void close() {
                LOCAL_OPERATORS.get().remove(this);
            }
        }
        var interceptor = new SqlInterceptorImpl();
        LOCAL_OPERATORS.get().addFirst(interceptor);
        return MonitoredResource.wrap(interceptor);
    }

    /**
     * Intercepts the specified SQL statement by calling all globally and locally registered interceptors.
     *
     * @param sql the SQL statement to intercept.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void intercept(@Nonnull Sql sql) {
        Sql adjusted = sql;
        // The local operators are not protected by a lock, but that is fine since they are thread-local. They should
        // however not modify the local operators from the accept method.
        for (var operator : LOCAL_OPERATORS.get()) {
            adjusted = operator.apply(adjusted);
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
    }
}
