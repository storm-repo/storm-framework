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

import static java.util.Objects.requireNonNull;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import st.orm.Entity;

/**
 * Transaction-local cache that interns entities by primary key with configurable retention behavior.
 *
 * <p>This cache ensures that, within a transaction, logically identical entities are represented by a single object
 * instance whenever possible. Observed entity state is retained for dirty checking as long as the application holds
 * a reference to the entity, and may be cleaned up automatically when the entity is no longer referenced.</p>
 *
 * <p>Cache entries are cleaned up lazily when new entries are interned.</p>
 *
 * <h2>Retention configuration</h2>
 * <p>The retention behavior can be configured via the {@code storm.entity_cache.retention} property
 * (see {@link st.orm.StormConfig}):</p>
 * <ul>
 *   <li>{@code default} (default): Observed state is retained for the duration of the transaction, improving the
 *       dirty-check hit rate at the cost of higher memory usage during transactions.</li>
 *   <li>{@code light}: Observed state may be cleaned up as soon as the application no longer holds a reference to
 *       the entity. This reduces memory overhead.</li>
 * </ul>
 *
 * <h2>Interning semantics</h2>
 * <ul>
 *   <li>Entities are indexed by their primary key.</li>
 *   <li>If an entity with the same primary key is already cached and still alive, it is returned
 *       <em>only if</em> it is considered equal according to {@link Object#equals(Object)}.</li>
 *   <li>If the cached instance differs logically (i.e. {@code equals} returns {@code false}), the cache is updated to
 *       reference the newly provided instance.</li>
 * </ul>
 *
 * <p>This behavior provides identity stability for logically identical data while still permitting newer or different
 * representations of the same primary key to replace older ones.</p>
 *
 * <h2>Thread-safety</h2>
 * <p>This implementation is not thread-safe. It is intended for use as a transaction-scoped cache accessed by a single
 * thread.</p>
 *
 * @param <E> the entity type.
 * @param <ID> the primary key type.
 * @since 1.7
 */
public final class EntityCacheImpl<E extends Entity<ID>, ID> implements EntityCache<E, ID> {

    private static final EntityCacheMetrics metrics = EntityCacheMetrics.getInstance();

    private final CacheRetention retention;

    /**
     * Creates a new entity cache with the specified retention behavior.
     *
     * @param retention the cache retention strategy to use.
     */
    public EntityCacheImpl(CacheRetention retention) {
        this.retention = retention;
    }

    /** Queue for tracking garbage-collected entities to enable lazy cleanup of {@link #map}. */
    private final ReferenceQueue<E> queue = new ReferenceQueue<>();

    /**
     * Map from row identity to referenced entity. Keys are held strongly; values are weak or soft references.
     *
     * <p>Keys are primary key values normalized through {@link RowIdentity}, so an entity-typed key matches by its
     * database key rather than by structural equality: two representations of the same row that diverge in a
     * non-key column (for example after a lossy database round-trip) still resolve to the same entry. A wrong hit
     * remains impossible, because equal row identities denote the same row by construction. Scalar keys, and
     * composite keys carrying only scalars, are used as-is; the per-class decision is cached, so such keys pay a
     * single cached class probe and no allocation.</p>
     */
    private final Map<Object, PkReference<E>> map = new HashMap<>();

    /**
     * Retrieves an entity from the cache by primary key, if available.
     *
     * <p>The returned entity is the currently cached instance for the given primary key if it is still reachable.
     * A previously cached entity may have been cleaned up and therefore no longer be available.</p>
     *
     * @param pk the primary key to look up.
     * @return an {@link Optional} containing the cached entity if present and still alive, or
     * {@link Optional#empty()} otherwise.
     */
    @Override
    public Optional<E> get(ID pk) {
        Object key = RowIdentity.normalize(requireNonNull(pk));
        PkReference<E> ref = map.get(key);
        if (ref == null) {
            metrics.recordGetMiss();
            return Optional.empty();
        }
        E value = ref.get();
        if (value != null) {
            metrics.recordGetHit();
            return Optional.of(value);
        }
        // Collected but not yet drained.
        map.remove(key, ref);
        metrics.recordGetMiss();
        return Optional.empty();
    }

    /**
     * Returns a canonical instance for the given entity within this cache.
     *
     * <p>If an entity with the same primary key is already cached and still reachable, that instance is returned
     * <em>only if</em> it is considered logically equal according to {@link Object#equals(Object)}. In that case, the
     * existing cached instance is reused.</p>
     *
     * <p>If no cached instance exists, the cached instance has been cleaned up, or the cached instance is not
     * equal to the provided entity, the cache is updated to reference the provided entity and that instance is
     * returned.</p>
     *
     * <p>Within this cache, for a given primary key, logically identical entities are represented by a single Java
     * object whenever possible.</p>
     *
     * @param entity the entity to intern.
     * @return the canonical cached instance for the entity's primary key.
     */
    @Override
    public E intern(E entity) {
        drainQueue();
        Object key = RowIdentity.normalize(entity.id());
        PkReference<E> existingRef = map.get(key);
        if (existingRef != null) {
            E existing = existingRef.get();
            if (existing != null && existing.equals(entity)) {
                metrics.recordInternHit();
                return existing;
            }
        }
        map.put(key, createReference(key, entity));
        metrics.recordInternMiss();
        return entity;
    }

    /**
     * Creates a reference to the given entity with the configured retention behavior.
     *
     * @param key the normalized primary key.
     * @param entity the entity to reference.
     * @return a reference based on {@link #retention}.
     */
    private PkReference<E> createReference(Object key, E entity) {
        return retention == CacheRetention.LIGHT
                ? new PkWeakReference<>(key, entity, queue)
                : new PkSoftReference<>(key, entity, queue);
    }

    /**
     * Removes the cached entry for the given primary key, if present.
     *
     * <p>If an entity instance for {@code pk} is currently cached, the mapping is removed. If the instance has already
     * been cleaned up, the mapping may be removed either by this call or later via lazy cleanup.</p>
     *
     * @param pk the primary key to remove; must not be {@code null}.
     */
    @Override
    public void remove(ID pk) {
        map.remove(RowIdentity.normalize(requireNonNull(pk)));
        metrics.recordRemoval();
    }

    /**
     * Clears all cached mappings.
     *
     * <p>This removes all primary key mappings immediately. Any already-enqueued references are harmless; future
     * cleanup will simply find no matching entries to remove.</p>
     */
    @Override
    public void clear() {
        map.clear();
        metrics.recordClear();
    }

    /**
     * Removes stale entries from {@link #map} by polling the reference queue.
     *
     * <p>When an entity is garbage collected, its {@link PkReference} is enqueued. This method polls the queue
     * and removes the corresponding entries from the map. Uses a two-argument remove to ensure only the exact
     * reference is removed, preventing removal of a newer entry that may have been added with the same key.</p>
     */
    private void drainQueue() {
        Reference<? extends E> ref;
        while ((ref = queue.poll()) != null) {
            if (ref instanceof PkReference<?> pkRef) {
                if (map.remove(pkRef.pk(), pkRef)) {
                    metrics.recordEviction();
                }
            }
        }
    }

    /**
     * A reference to an entity that retains the associated normalized primary key for map cleanup.
     *
     * <p>When the entity is garbage collected, this reference is enqueued in the {@link ReferenceQueue}, allowing
     * {@link #drainQueue()} to remove the corresponding entry from {@link #map} using the stored key.</p>
     *
     * @param <E> the entity type.
     */
    private sealed interface PkReference<E> permits PkWeakReference, PkSoftReference {
        Object pk();
        E get();
    }

    /**
     * A weak reference implementation of {@link PkReference}.
     */
    private static final class PkWeakReference<E> extends WeakReference<E> implements PkReference<E> {
        private final Object pk;

        PkWeakReference(Object pk, E referent, ReferenceQueue<? super E> q) {
            super(referent, q);
            this.pk = pk;
        }

        @Override
        public Object pk() {
            return pk;
        }
    }

    /**
     * A soft reference implementation of {@link PkReference}.
     */
    private static final class PkSoftReference<E> extends SoftReference<E> implements PkReference<E> {
        private final Object pk;

        PkSoftReference(Object pk, E referent, ReferenceQueue<? super E> q) {
            super(referent, q);
            this.pk = pk;
        }

        @Override
        public Object pk() {
            return pk;
        }
    }
}
