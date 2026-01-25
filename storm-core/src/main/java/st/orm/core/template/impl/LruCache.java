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
package st.orm.core.template.impl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A simple in-memory Least Recently Used (LRU) cache.
 *
 * <p>This cache evicts entries based on access order. Every successful {@link #get(Object)} or
 * {@link #put(Object, Object)} marks the entry as most recently used. When the cache size exceeds {@code maxSize}, the
 * least recently used entry is automatically removed.</p>
 *
 * <h2>Eviction semantics</h2>
 * <ul>
 *   <li>Eviction is triggered <em>after</em> inserting a new entry.</li>
 *   <li>At most one entry is evicted per {@code put} operation.</li>
 *   <li>The eldest entry is determined by access order, not insertion order.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>This class is <strong>not thread-safe</strong>. If concurrent access is required, wrap the instance using
 * {@link java.util.Collections#synchronizedMap(Map)} or provide external synchronization.</p>
 *
 * <h2>Null handling</h2>
 * <p>This cache permits {@code null} keys and values, following the behavior of {@link LinkedHashMap}.</p>
 *
 * @param <K> the cache key type
 * @param <V> the cache value type
 */
public final class LruCache<K, V> extends LinkedHashMap<K, V> {

    private final int maxSize;

    /**
     * Creates a new LRU cache with the given maximum size.
     *
     * <p>The initial capacity is derived from {@code maxSize} and the default
     * load factor (0.75) to minimize internal rehashing.</p>
     *
     * @param maxSize the maximum number of entries this cache may hold.
     * @throws IllegalArgumentException if {@code maxSize <= 0}.
     */
    public LruCache(int maxSize) {
        this(maxSize, (int) Math.ceil(maxSize / 0.75f) + 1, 0.75f);
    }

    /**
     * Creates a new LRU cache with full control over sizing parameters.
     *
     * @param maxSize the maximum number of entries this cache may hold.
     * @param initialCapacity the initial hash table capacity.
     * @param loadFactor the hash table load factor.
     * @throws IllegalArgumentException if {@code maxSize <= 0}.
     */
    public LruCache(int maxSize, int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor, true);
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be > 0.");
        }
        this.maxSize = maxSize;
    }

    /**
     * Determines whether the eldest entry should be removed from the map.
     *
     * <p>This method is invoked by {@link LinkedHashMap} after each
     * {@code put} operation. Returning {@code true} causes the eldest
     * entry (the least recently accessed entry) to be removed.</p>
     *
     * <p>Eviction is based solely on entry count and does not consider
     * entry weight or memory usage.</p>
     *
     * @param eldest the least recently accessed entry.
     * @return {@code true} if the eldest entry should be removed.
     */
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;
    }
}