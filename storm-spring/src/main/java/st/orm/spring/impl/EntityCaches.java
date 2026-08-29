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
package st.orm.spring.impl;

import java.util.Map;
import st.orm.core.spi.CacheRetention;
import st.orm.core.spi.EntityCache;
import st.orm.core.spi.EntityCacheImpl;

/**
 * The entity-cache bookkeeping shared by the Spring transaction contexts: each context owns one cache map
 * per physical transaction, keyed by entity type.
 *
 * @since 1.14
 */
public final class EntityCaches {

    private EntityCaches() {
    }

    /**
     * Returns the cache for the entity type, creating it with the given retention on first use.
     */
    @SuppressWarnings("unchecked")
    public static <K extends Class<?>, C extends EntityCache<?, ?>> C entityCache(Map<K, C> caches,
                                                                                  K entityType,
                                                                                  CacheRetention retention) {
        return caches.computeIfAbsent(entityType, ignore -> (C) new EntityCacheImpl<>(retention));
    }

    /**
     * Returns the cache for the entity type, failing when none exists.
     */
    public static <K extends Class<?>, C extends EntityCache<?, ?>> C getEntityCache(Map<K, C> caches,
                                                                                     K entityType) {
        var cache = caches.get(entityType);
        if (cache == null) {
            throw new IllegalStateException("No entity cache exists for " + entityType.getName() + ".");
        }
        return cache;
    }

    /**
     * Clears every cache in the map.
     */
    public static void clearAll(Map<?, ? extends EntityCache<?, ?>> caches) {
        caches.values().forEach(EntityCache::clear);
    }
}
