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

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import static java.util.Collections.newSetFromMap;

/**
 * Manages SQL interceptors.
 *
 * @since 1.1
 */
public final class SqlInterceptorManager {

    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();
    private static final Set<Consumer<Sql>> GLOBAL_CONSUMERS = newSetFromMap(new IdentityHashMap<>());

    private static final ThreadLocal<Set<Consumer<Sql>>> LOCAL_CONSUMERS =
            ThreadLocal.withInitial(() -> newSetFromMap(new HashMap<>()) );

    private SqlInterceptorManager() {
    }

    /**
     * Register a global interceptor that will be called for all SQL statements.
     *
     * @param consumer the consumer to call for each SQL statement.
     */
    public static void registerGlobalInterceptor(@Nonnull Consumer<Sql> consumer) {
        LOCK.writeLock().lock();
        try {
            GLOBAL_CONSUMERS.add(consumer);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Unregister a global interceptor.
     *
     * @param consumer the consumer to unregister.
     */
    public static void unregisterGlobalInterceptor(@Nonnull Consumer<Sql> consumer) {
        LOCK.writeLock().lock();
        try {
            GLOBAL_CONSUMERS.remove(consumer);
        } finally {
            LOCK.writeLock().unlock();
        }
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
        class SqlInterceptorImpl implements SqlInterceptor, Consumer<Sql> {
            @Override
            public void accept(Sql sql) {
                consumer.accept(sql);
            }

            @Override
            public void close() {
                LOCAL_CONSUMERS.get().remove(this);
            }
        }
        var interceptor = new SqlInterceptorImpl();
        LOCAL_CONSUMERS.get().add(interceptor);
        return MonitoredResource.wrap(interceptor);
    }

    /**
     * Intercepts the specified SQL statement by calling all globally and locally registered consumers.
     *
     * @param sql the SQL statement to intercept.
     */
    static void intercept(@Nonnull Sql sql) {
        LOCK.readLock().lock();
        try {
            GLOBAL_CONSUMERS.forEach(consumer -> consumer.accept(sql));
        } finally {
            LOCK.readLock().unlock();
        }
        // The local consumers are not protected by a lock, but that is fine since they are thread-local. They should
        // however not modify the local consumers from the accept method.
        LOCAL_CONSUMERS.get().forEach(consumer -> consumer.accept(sql));
    }
}
