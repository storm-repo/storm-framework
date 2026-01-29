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

import jakarta.annotation.Nonnull;
import st.orm.Entity;
import st.orm.Ref;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Transaction-local cache that interns entities by primary key using weak references.
 *
 * <p>This cache ensures that, within a transaction, logically identical entities are represented by a single object
 * instance whenever possible. Cached entities are held via {@link WeakReference weak references}, so they do not
 * prevent garbage collection once no strong references remain.</p>
 *
 * <p>Cache entries are cleaned up lazily using a {@link ReferenceQueue}. Stale entries whose referents have been
 * garbage collected are removed during cache access.</p>
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

    /** Queue for tracking garbage-collected entities to enable lazy cleanup of {@link #map}. */
    private final ReferenceQueue<E> queue = new ReferenceQueue<>();

    /** Map from primary key to weakly-referenced entity. Keys are held strongly; values are weak references. */
    private final Map<ID, PkWeakReference<ID, E>> map = new HashMap<>();

    /**
     * Retrieves an entity from the cache by primary key, if available.
     *
     * <p>The returned entity is the currently cached instance for the given primary key if it is still reachable
     * elsewhere. Because entities are stored using {@link WeakReference weak references}, a previously cached entity
     * may have been garbage collected and therefore no longer be available.</p>
     *
     * <p>Stale cache entries whose referents have been garbage collected are removed lazily during this call.</p>
     *
     * @param pk the primary key to look up.
     * @return an {@link Optional} containing the cached entity if present and still alive, or
     * {@link Optional#empty()} otherwise.
     */
    @Override
    public Optional<E> get(@Nonnull ID pk) {
        drainQueue();
        PkWeakReference<ID, E> ref = map.get(pk);
        if (ref == null) {
            return Optional.empty();
        }
        E value = ref.get();
        if (value != null) {
            return Optional.of(value);
        }
        // Collected but not yet drained.
        map.remove(pk, ref);
        return Optional.empty();
    }

    /**
     * Returns a canonical instance for the given entity within this cache.
     *
     * <p>If an entity with the same primary key is already cached and still reachable, that instance is returned
     * <em>only if</em> it is considered logically equal according to {@link Object#equals(Object)}. In that case, the
     * existing cached instance is reused.</p>
     *
     * <p>If no cached instance exists, the cached instance has been garbage collected, or the cached instance is not
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
    public E intern(@Nonnull E entity) {
        drainQueue();
        ID pk = entity.id();
        PkWeakReference<ID, E> existingRef = map.get(pk);
        if (existingRef != null) {
            E existing = existingRef.get();
            if (existing != null && existing.equals(entity)) {
                return existing;
            }
        }
        map.put(pk, new PkWeakReference<>(pk, entity, queue));
        return entity;
    }

    /**
     * Stores the given entity in the cache under its primary key.
     *
     * <p>This is a "replace" operation: the cache entry for {@code entity.id()} is updated to point to the given
     * instance, regardless of whether a logically equal instance is already cached.</p>
     *
     * <p>As with all operations, stale entries are cleaned up lazily before the update is applied.</p>
     *
     * @param entity the entity to cache.
     */
    @Override
    public void set(@Nonnull E entity) {
        drainQueue();
        ID pk = entity.id();
        map.put(pk, new PkWeakReference<>(pk, entity, queue));
    }

    /**
     * Stores all given entities in the cache.
     *
     * <p>This is a batch form of {@link #set(Entity)}. It drains the reference queue once and then applies all updates,
     * reducing per-call overhead compared to repeated {@code set(...)} calls.</p>
     *
     * <p>If multiple entities with the same primary key appear in the input, the last one wins.</p>
     *
     * @param entities the entities to cache.
     */
    @Override
    public void set(@Nonnull Iterable<? extends E> entities) {
        drainQueue();
        for (E entity : entities) {
            ID pk = entity.id();
            map.put(pk, new PkWeakReference<>(pk, entity, queue));
        }
    }

    /**
     * Removes the cached entry for the given primary key, if present.
     *
     * <p>If an entity instance for {@code pk} is currently cached, the mapping is removed. If the instance has already
     * been garbage collected, the mapping may be removed either by this call or later via lazy queue draining.</p>
     *
     * @param pk the primary key to remove.
     */
    @Override
    public void remove(@Nonnull ID pk) {
        drainQueue();
        map.remove(pk);
    }

    /**
     * Removes all cached entries for the given primary keys.
     *
     * <p>This is a batch form of {@link #remove(Object)}. It drains the reference queue once and then removes all keys,
     * reducing per-call overhead compared to repeated {@code remove(...)} calls.</p>
     *
     * <p>If a primary key is not present, it is ignored.</p>
     *
     * @param pks the primary keys to remove.
     */
    @Override
    public void remove(@Nonnull Iterable<? extends ID> pks) {
        drainQueue();
        for (ID pk : pks) {
            map.remove(pk);
        }
    }

    /**
     * Removes cached entries for the given entities by their primary keys.
     *
     * <p>This method removes by primary key only. It does not check whether the cached instance is identical to the
     * provided entity instance.</p>
     *
     * @param entities the entities whose primary keys should be removed.
     */
    @Override
    public void removeEntities(@Nonnull Iterable<? extends E> entities) {
        drainQueue();
        for (E entity : entities) {
            map.remove(entity.id());
        }
    }

    /**
     * Removes cached entries for the given {@link Ref references} by their ids.
     *
     * <p>This removes by id only. The cache does not require the referenced entity to be loaded.</p>
     *
     * <p><strong>Type note:</strong> {@link Ref#id()} returns {@code Object}. This implementation assumes that the id
     * value is assignable to {@code ID}. If not, a {@link ClassCastException} will be thrown.</p>
     *
     * @param entities the references whose ids should be removed.
     */
    @Override
    public void removeRefs(@Nonnull Iterable<? extends Ref<E>> entities) {
        drainQueue();
        for (Ref<E> ref : entities) {
            //noinspection unchecked
            map.remove((ID) ref.id());
        }
    }

    /**
     * Clears all cached mappings.
     *
     * <p>This removes all primary key mappings immediately. Any already-enqueued weak references are harmless; future
     * drains will simply find no matching entries to remove.</p>
     */
    @Override
    public void clear() {
        // Drain first so the queue doesn't keep growing with refs we could consume now.
        drainQueue();
        map.clear();
        // Drain again to eagerly consume anything that raced into the queue during clearing.
        drainQueue();
    }

    /**
     * Removes stale entries from {@link #map} by polling the reference queue.
     *
     * <p>When an entity is garbage collected, its {@link PkWeakReference} is enqueued. This method polls the queue
     * and removes the corresponding entries from the map. Uses a two-argument remove to ensure only the exact
     * weak reference is removed, preventing removal of a newer entry that may have been added with the same key.</p>
     */
    private void drainQueue() {
        PkWeakReference<ID, E> weakReference;
        //noinspection unchecked
        while ((weakReference = (PkWeakReference<ID, E>) queue.poll()) != null) {
            map.remove(weakReference.pk, weakReference);
        }
    }

    /**
     * A weak reference to an entity that retains the associated primary key for map cleanup.
     *
     * <p>When the entity is garbage collected, this reference is enqueued in the {@link ReferenceQueue}, allowing
     * {@link #drainQueue()} to remove the corresponding entry from {@link #map} using the stored primary key.</p>
     *
     * @param <ID> the primary key type.
     * @param <E> the entity type.
     */
    private static final class PkWeakReference<ID, E> extends WeakReference<E> {
        final ID pk;

        PkWeakReference(ID pk, E referent, ReferenceQueue<? super E> q) {
            super(referent, q);
            this.pk = pk;
        }
    }
}