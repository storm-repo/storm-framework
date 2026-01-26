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

import jakarta.annotation.Nonnull;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * A sharded LRU cache optimized for concurrent, read-dominated workloads.
 *
 * <p>This cache partitions entries across multiple independent LRU segments. Each segment is implemented as an
 * access-order {@link LinkedHashMap} protected by a segment-level monitor.</p>
 *
 * <p>Eviction is strict LRU within each segment, and approximate LRU globally (because segments are independent).
 * For many high-hit-rate caches, this yields much higher throughput with minimal complexity and no external
 * dependencies.</p>
 *
 * @param <K> key type.
 * @param <V> value type.
 * @since 1.8
 */
public final class SegmentedLruCache<K, V> {

    private final Segment<K, V>[] segments;
    private final int segmentMask;

    /**
     * Creates a cache with an automatically chosen segment count.
     *
     * <p>The segment count is derived from {@code maxSize} using a simple heuristic that targets roughly 128
     * entries per segment. The resulting segment count is clamped to 4..32 and rounded up to a power of two.
     * This keeps per-segment contention low while maintaining reasonably accurate eviction behavior.</p>
     *
     * @param maxSize the maximum number of entries held by the cache across all segments; must be {@code > 0}.
     * @throws IllegalArgumentException if {@code maxSize <= 0}.
     */
    public SegmentedLruCache(int maxSize) {
        this(maxSize, defaultSegmentCount(maxSize));
    }

    /**
     * Creates a cache with a given segment count.
     *
     * <p>The supplied {@code segmentCount} is rounded up to a power of two. Using a power-of-two segment count
     * makes segment selection fast (bitmask) and keeps distribution stable.</p>
     *
     * <p>The total {@code maxSize} is split evenly across segments using {@code ceil(maxSize / segmentCount)},
     * ensuring the overall cache size bound is respected.</p>
     *
     * @param maxSize       the maximum number of entries held by the cache across all segments; must be {@code > 0}.
     * @param segmentCount  the desired number of segments; must be {@code > 0} (rounded up to a power of two).
     * @throws IllegalArgumentException if {@code maxSize <= 0} or {@code segmentCount <= 0}.
     */
    @SuppressWarnings("unchecked")
    public SegmentedLruCache(int maxSize, int segmentCount) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be > 0.");
        }
        if (segmentCount <= 0) {
            throw new IllegalArgumentException("segmentCount must be > 0.");
        }
        int sc = 1;
        while (sc < segmentCount) sc <<= 1; // round up to power of two
        this.segmentMask = sc - 1;
        int perSegment = Math.max(1, (maxSize + sc - 1) / sc); // ceil(maxSize / sc)
        this.segments = (Segment<K, V>[]) new Segment[sc];
        for (int i = 0; i < sc; i++) {
            this.segments[i] = new Segment<>(perSegment);
        }
    }

    /**
     * Returns the cached value for {@code key}, or {@code null} if no entry exists.
     *
     * <p>On a hit, this also updates the entry's recency within its segment, affecting per-segment LRU eviction.</p>
     *
     * @param key the key to look up; must not be {@code null}.
     * @return the cached value, or {@code null} if absent.
     * @throws NullPointerException if {@code key} is {@code null}.
     */
    public V get(@Nonnull K key) {
        return segmentFor(key).get(key);
    }

    /**
     * Stores {@code value} under {@code key}, overwriting any existing mapping.
     *
     * <p>After insertion, the owning segment evicts least-recently-used entries until its segment capacity is
     * respected. Eviction is performed under the segment lock.</p>
     *
     * @param key the key; must not be {@code null}.
     * @param value the value; must not be {@code null}.
     * @throws NullPointerException if {@code key} or {@code value} is {@code null}.
     */
    public void put(@Nonnull K key, @Nonnull V value) {
        segmentFor(key).put(key, value);
    }

    /**
     * Stores {@code value} under {@code key} only if {@code key} is absent.
     *
     * <p>This method is intended for the "compute outside lock, publish quickly" pattern. It does not call any
     * mapping function and does not perform expensive work while holding the segment lock.</p>
     *
     * <p>If the key is present, this method returns the existing value and updates recency (because an internal
     * {@code get} is used to check presence).</p>
     *
     * @param key the key; must not be {@code null}
     * @param value the value to publish if absent; must not be {@code null}
     * @return the existing value if present, otherwise {@code null} (and {@code value} is stored)
     * @throws NullPointerException if {@code key} or {@code value} is {@code null}
     */
    public V putIfAbsent(@Nonnull K key, @Nonnull V value) {
        return segmentFor(key).putIfAbsent(key, value);
    }

    /**
     * Returns the cached value for {@code key}, computing and publishing it if absent.
     *
     * <p>This implements a fast read path and a slow compute path:</p>
     * <ol>
     *   <li>Attempt {@link #get(Object)}.</li>
     *   <li>If absent, compute the value outside the segment lock using {@code supplier}.</li>
     *   <li>Publish using {@link #putIfAbsent(Object, Object)} to avoid duplicate publication.</li>
     * </ol>
     *
     * <p>If multiple callers compute concurrently, exactly one computed value is stored. Losers of the race
     * receive the stored (winner) value. This avoids holding locks during computation at the expense of possible
     * duplicate computation under contention.</p>
     *
     * @param key the key; must not be {@code null}.
     * @param supplier computes a value when absent; must not be {@code null}.
     * @return the cached or computed value, or {@code null} if {@code supplier} returns {@code null}.
     * @throws NullPointerException if {@code key} or {@code supplier} is {@code null}.
     */
    public V getOrCompute(@Nonnull K key, @Nonnull Supplier<? extends V> supplier) {
        V v = get(key);
        if (v != null) {
            return v;
        }
        V computed = supplier.get();
        if (computed == null) {
            return null;
        }
        V existing = putIfAbsent(key, computed);
        return existing != null ? existing : computed;
    }

    /**
     * Returns the number of segments in this cache.
     *
     * <p>This is always a power of two and may be larger than the requested segment count passed to the
     * constructor (because it is rounded up).</p>
     *
     * @return the number of cache segments.
     */
    public int segmentCount() {
        return segments.length;
    }

    /**
     * Removes all entries from all segments.
     *
     * <p>This method acquires each segment lock in sequence. It is intended for tests and administrative tasks,
     * not for hot paths.</p>
     */
    public void clear() {
        for (Segment<K, V> s : segments) {
            s.clear();
        }
    }

    /**
     * Removes the mapping for {@code key} if present.
     *
     * <p>This removal does not affect other keys' recency ordering except for the underlying map mechanics in
     * the owning segment.</p>
     *
     * @param key the key to remove; must not be {@code null}.
     * @return the removed value, or {@code null} if no mapping existed.
     * @throws NullPointerException if {@code key} is {@code null}.
     */
    public V remove(@Nonnull K key) {
        return segmentFor(key).remove(key);
    }

    private Segment<K, V> segmentFor(@Nonnull K key) {
        int h = spread(key.hashCode());
        return segments[h & segmentMask];
    }

    /**
     * Spreads hash codes to reduce poor key distributions across segments.
     *
     * <p>This mirrors the classic {@code ConcurrentHashMap} spreader pattern. It helps when user-provided
     * {@code hashCode()} implementations have weak high bits.</p>
     */
    private static int spread(int h) {
        return h ^ (h >>> 16);
    }

    /**
     * Chooses a default number of segments for a given maximum cache size.
     *
     * <p>Heuristic:</p>
     * <ul>
     *   <li>Targets roughly 128 entries per segment.</li>
     *   <li>Clamps the segment count to the range 4..32.</li>
     *   <li>Rounds up to the next power of two for fast masking.</li>
     * </ul>
     *
     * @param maxSize total cache capacity.
     * @return a power-of-two segment count in the range 4..32.
     */
    private static int defaultSegmentCount(int maxSize) {
        int segments = maxSize / 128; // target ~128 entries per segment
        if (segments < 4) {
            segments = 4;
        }
        if (segments > 32) {
            segments = 32;
        }
        int sc = 1;
        while (sc < segments) {
            sc <<= 1;
        }
        return sc;
    }

    /**
     * A single LRU segment protected by a monitor.
     *
     * <p>All operations are synchronized to keep the access-order {@link LinkedHashMap} consistent. This keeps
     * the implementation small and predictable while still allowing concurrency across segments.</p>
     */
    private static final class Segment<K, V> {
        private final int maxSize;
        private final LinkedHashMap<K, V> lru;

        /**
         * Creates a segment with a fixed maximum size.
         *
         * @param maxSize maximum entries for this segment; must be {@code >= 1}.
         */
        Segment(int maxSize) {
            this.maxSize = maxSize;
            this.lru = new LinkedHashMap<>(16, 0.75f, true);
        }

        /**
         * Returns the value mapped to {@code key}, updating recency on a hit.
         */
        synchronized V get(@Nonnull K key) {
            return lru.get(key);
        }

        /**
         * Stores a mapping, overwriting any previous value, and then evicts if needed.
         */
        synchronized void put(@Nonnull K key, @Nonnull V value) {
            lru.put(key, requireNonNull(value));
            evictIfNeeded();
        }

        /**
         * Stores a mapping only if absent, updating recency if present, and then evicts if needed.
         *
         * @return the existing value if present, otherwise {@code null}.
         */
        synchronized V putIfAbsent(@Nonnull K key, @Nonnull V value) {
            V existing = lru.get(key); // Counts as access (recency).
            if (existing != null) {
                return existing;
            }
            lru.put(key, requireNonNull(value));
            evictIfNeeded();
            return null;
        }

        /**
         * Removes the mapping for {@code key} if present.
         */
        synchronized V remove(@Nonnull K key) {
            return lru.remove(key);
        }

        /**
         * Removes all entries from this segment.
         */
        synchronized void clear() {
            lru.clear();
        }

        /**
         * Evicts least-recently-used entries until {@link #maxSize} is respected.
         *
         * <p>This is normally at most one eviction, but a loop is used to stay correct even if callers insert a
         * burst of entries while sizing parameters change or if {@code maxSize} is very small.</p>
         */
        private void evictIfNeeded() {
            while (lru.size() > maxSize) {
                var eldest = lru.entrySet().iterator().next();
                lru.remove(eldest.getKey());
            }
        }
    }
}