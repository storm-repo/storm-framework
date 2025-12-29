package st.orm.core.spi;

import jakarta.annotation.Nonnull;
import st.orm.Entity;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Transaction-local cache that interns entities by primary key using weak references.
 *
 * <p>This cache ensures that, within a transaction, logically identical entities are represented by a single object
 * instance whenever possible. Cached entities are held via {@link java.lang.ref.WeakReference weak references}, so
 * they do not prevent garbage collection once no strong references remain.</p>
 *
 * <p>Cache entries are cleaned up lazily using a {@link java.lang.ref.ReferenceQueue}. Stale entries whose referents
 * have been garbage collected are removed during cache access.</p>
 *
 * <h2>Interning semantics</h2>
 * <ul>
 *   <li>Entities are indexed by their primary key.</li>
 *   <li>If an entity with the same primary key is already cached and still alive, it is returned
 *       <em>only if</em> it is considered equal according to {@link Object#equals(Object)}.</li>
 *   <li>If the cached instance differs logically (i.e. {@code equals} returns {@code false}),
 *       the cache is updated to reference the newly provided instance.</li>
 * </ul>
 *
 * <p>This behavior allows identity stability for logically identical data while still permitting newer or different
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

    private final ReferenceQueue<E> queue = new ReferenceQueue<>();
    private final Map<ID, PkWeakRef<ID, E>> map = new HashMap<>();

    /**
     * Retrieves an entity from the cache by primary key, if available.
     *
     * <p>The returned entity is guaranteed to be the canonical cached instance for the given primary key
     * <em>only if</em> it is still strongly reachable elsewhere. Because entities are stored using
     * {@link java.lang.ref.WeakReference weak references}, a previously cached entity may have been garbage collected
     * and therefore no longer be available.</p>
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
        PkWeakRef<ID, E> ref = map.get(pk);
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
     * <p>This method guarantees that, for a given primary key, logically identical entities share a single object
     * identity within the scope of the cache.</p>
     *
     * @param entity the entity to intern.
     * @return the canonical cached instance for the entity's primary key.
     */
    @Override
    public E intern(@Nonnull E entity) {
        drainQueue();
        ID pk = entity.id();
        PkWeakRef<ID, E> existingRef = map.get(pk);
        if (existingRef != null) {
            E existing = existingRef.get();
            if (existing != null && existing.equals(entity)) {
                // Return the existing entity instance.
                return existing;
            }
        }
        map.put(pk, new PkWeakRef<>(pk, entity, queue));
        return entity;
    }

    private void drainQueue() {
        PkWeakRef<ID, E> ref;
        //noinspection unchecked
        while ((ref = (PkWeakRef<ID, E>) queue.poll()) != null) {
            map.remove(ref.pk, ref);
        }
    }

    private static final class PkWeakRef<ID, E> extends WeakReference<E> {
        final ID pk;

        PkWeakRef(ID pk, E referent, ReferenceQueue<? super E> q) {
            super(referent, q);
            this.pk = pk;
        }
    }
}