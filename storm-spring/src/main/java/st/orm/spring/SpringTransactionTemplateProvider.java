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
package st.orm.spring;

import static java.util.Optional.empty;
import static java.util.Optional.of;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import st.orm.Entity;
import st.orm.core.spi.CacheRetention;
import st.orm.core.spi.EntityCache;
import st.orm.core.spi.EntityCacheImpl;
import st.orm.core.spi.TransactionContext;
import st.orm.core.spi.TransactionTemplate;
import st.orm.core.spi.TransactionTemplateProvider;

/**
 * Transaction template provider that exposes a transaction context bound to Spring's
 * {@code TransactionSynchronizationManager}.
 *
 * <p>When a Spring-managed transaction is active, templates configured with this provider observe a transaction
 * context scoped to that transaction, enabling transaction-scoped entity caching and dirty tracking. The context is
 * bound once per physical Spring transaction and unbound on completion.</p>
 *
 * <p>Storm's own transaction API is not bridged by this provider: transactions are managed by Spring, typically via
 * {@code @Transactional} or Spring's {@code TransactionTemplate}.</p>
 *
 * @see SpringConnectionProvider
 * @since 1.13
 */
public class SpringTransactionTemplateProvider implements TransactionTemplateProvider {

    private static final Object CONTEXT_RESOURCE_KEY =
            SpringTransactionTemplateProvider.class.getName() + ".TX_CONTEXT";

    private static final ThreadLocal<TransactionContext> CONTEXT_HOLDER = new ThreadLocal<>();

    @Override
    public TransactionTemplate getTransactionTemplate() {
        return new TransactionTemplate() {
            @Override
            public TransactionTemplate propagation(String propagation) {
                throw new UnsupportedOperationException(
                        "Storm transactions are not supported with Spring-managed transactions; use Spring's transaction management.");
            }

            @Override
            public TransactionTemplate isolation(int isolation) {
                throw new UnsupportedOperationException(
                        "Storm transactions are not supported with Spring-managed transactions; use Spring's transaction management.");
            }

            @Override
            public TransactionTemplate readOnly(boolean readOnly) {
                throw new UnsupportedOperationException(
                        "Storm transactions are not supported with Spring-managed transactions; use Spring's transaction management.");
            }

            @Override
            public TransactionTemplate timeout(int timeoutSeconds) {
                throw new UnsupportedOperationException(
                        "Storm transactions are not supported with Spring-managed transactions; use Spring's transaction management.");
            }

            @Override
            public TransactionHandle open(@Nullable TransactionContext existing, boolean suspendMode) {
                throw new UnsupportedOperationException(
                        "Storm transactions are not supported with Spring-managed transactions; use Spring's transaction management.");
            }

            @Override
            public Optional<TransactionContext> currentContext() {
                if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                    return empty();
                }
                Object existing = TransactionSynchronizationManager.getResource(CONTEXT_RESOURCE_KEY);
                if (existing instanceof TransactionContext context) {
                    return of(context);
                }
                var created = new SpringLinkedTransactionContext();
                TransactionSynchronizationManager.bindResource(CONTEXT_RESOURCE_KEY, created);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        TransactionSynchronizationManager.unbindResourceIfPossible(CONTEXT_RESOURCE_KEY);
                    }
                });
                return of(created);
            }

            @Override
            public ThreadLocal<TransactionContext> contextHolder() {
                return CONTEXT_HOLDER;
            }
        };
    }

    /**
     * Transaction context bound to Spring's {@code TransactionSynchronizationManager} resources.
     */
    private static final class SpringLinkedTransactionContext implements TransactionContext {
        private final Map<Class<? extends Entity<?>>, EntityCache<? extends Entity<?>, ?>> caches = new HashMap<>();
        private final Decorator<?> noopDecorator = resource -> resource;

        @Override
        public boolean isRepeatableRead() {
            // Spring returns null when no explicit isolation level is set (database default). Most databases default
            // to READ_COMMITTED, so return false to ensure fresh data is fetched on each read; users who want cached
            // instances should explicitly set REPEATABLE_READ or higher.
            Integer isolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
            if (isolationLevel == null || isolationLevel < 0) {
                return false;
            }
            return isolationLevel >= Connection.TRANSACTION_REPEATABLE_READ;
        }

        @Override
        public EntityCache<? extends Entity<?>, ?> entityCache(@Nonnull Class<? extends Entity<?>> entityType,
                                                               @Nonnull CacheRetention retention) {
            // The context is bound once per physical Spring transaction, which gives correct cache scoping for
            // REQUIRED and REQUIRES_NEW. NESTED savepoint rollbacks are not observable through Spring's hooks, so no
            // cache splitting is attempted for savepoints.
            return caches.computeIfAbsent(entityType, ignore -> new EntityCacheImpl<>(retention));
        }

        @Override
        public EntityCache<? extends Entity<?>, ?> getEntityCache(@Nonnull Class<? extends Entity<?>> entityType) {
            var cache = caches.get(entityType);
            if (cache == null) {
                throw new IllegalStateException("No entity cache exists for " + entityType.getName() + ".");
            }
            return cache;
        }

        @Override
        public EntityCache<? extends Entity<?>, ?> findEntityCache(@Nonnull Class<? extends Entity<?>> entityType) {
            return caches.get(entityType);
        }

        @Override
        public void clearAllEntityCaches() {
            for (EntityCache<? extends Entity<?>, ?> cache : caches.values()) {
                cache.clear();
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Decorator<T> getDecorator(@Nonnull Class<T> resourceType) {
            return (Decorator<T>) noopDecorator;
        }
    }
}
