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
package st.orm.core.spi;

import jakarta.annotation.Nonnull;
import st.orm.Entity;

/**
 * @since 1.5
 */
public interface TransactionContext {

    /**
     * Returns true if the transaction is marked as read-only, false otherwise.
     *
     * @return true if the transaction is marked as read-only, false otherwise.
     * @since 1.7
     */
    boolean isReadOnly();

    /**
     * Returns a transaction-local cache for entities of the given type, keyed by primary key.
     */
    EntityCache<? extends Entity<?>, ?> entityCache(@Nonnull Class<? extends Entity<?>> entityType);

    /**
     * Decorates a transaction resource before it is used.
     *
     * <p>
     * Implementations may wrap, configure, or otherwise adapt the given {@code resource} so it matches the
     * transaction characteristics and scope (for example isolation, read-only behavior, timeouts, or
     * thread/connection binding).</p>
     *
     * @param <T> the resource type being decorated
     */
    interface Decorator<T> {

        /**
         * Returns a decorated variant of the given resource.
         * <p>
         * Implementations may return the same instance if no decoration is required.
         *
         * @param resource the resource to decorate
         * @return the decorated resource (never {@code null})
         */
        T decorate(T resource);
    }

    /**
     * Gets the decorator for the specified resource type.
     *
     * @param resourceType the resource type.
     * @return the decorator.
     * @param <T> the resource type.
     */
    <T> Decorator<T> getDecorator(@Nonnull Class<T> resourceType);
}
