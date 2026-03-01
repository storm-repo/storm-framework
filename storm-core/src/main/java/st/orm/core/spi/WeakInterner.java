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

import jakarta.annotation.Nonnull;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import st.orm.Entity;
import st.orm.Ref;

/**
 * A weak interner that ensures canonical instances of objects while holding them weakly to permit garbage collection.
 *
 * <p>This class uses a dual-path interning strategy optimized for different object types:</p>
 * <ul>
 *   <li><b>Entities</b>: Uses primary key-based lookup via {@link Ref} for efficient equality checks. Entities are
 *       stored in a separate map with {@link ReferenceQueue}-based cleanup to ensure stale entries are removed when
 *       entities are garbage collected.</li>
 *   <li><b>Non-entities</b>: Uses object equality-based lookup via {@link WeakHashMap}, which provides automatic
 *       cleanup when objects are no longer strongly referenced.</li>
 * </ul>
 *
 * <p>The primary key-based lookup for entities avoids potentially expensive deep equality checks on complex entity
 * objects, while maintaining correct identity semantics (same primary key = same canonical instance).</p>
 *
 * <p>This class is not thread-safe. A new instance is expected to be created for each result set processing call,
 * ensuring that interning is scoped to a single query execution.</p>
 */
public final class WeakInterner {

    /** Map for non-entity objects, using object equality for lookup. Keys are held weakly. */
    private final Map<Object, WeakReference<Object>> map;

    /** Queue for tracking garbage-collected entities to enable lazy cleanup of {@link #entityMap}. */
    private final ReferenceQueue<Entity<?>> queue;

    /** Map for entities, using {@link Ref} (primary key) for efficient lookup. Keys are held strongly. */
    private final Map<Ref<?>, RefWeakReference> entityMap;

    /**
     * Creates a new weak interner.
     */
    public WeakInterner() {
        map = new WeakHashMap<>();
        queue = new ReferenceQueue<>();
        entityMap = new HashMap<>();
    }

    /**
     * Interns the given object, ensuring that only one canonical instance exists. If an equivalent object is already
     * present, returns the existing instance. Otherwise, adds the new object to the interner and returns it.
     *
     * <p>For {@link Entity} instances, lookup is performed using the entity's primary key (via {@link Ref}) for
     * efficiency. For all other objects, lookup is based on object equality.</p>
     *
     * @param object the object to intern.
     * @param <T> the type of the object.
     * @return the canonical instance of the object.
     * @throws NullPointerException if {@code object} is {@code null}.
     */
    public <T> T intern(@Nonnull T object) {
        requireNonNull(object, "Cannot intern null object.");
        if (object instanceof Entity<?> entity) {
            //noinspection unchecked
            return (T) internEntity(entity);
        }
        return internObject(object);
    }

    /**
     * Retrieves a cached entity by its type and primary key, if available.
     *
     * <p>This method enables early cache lookups before constructing nested objects. If an entity with the given
     * type and primary key was previously interned and is still reachable, it is returned.</p>
     *
     * @param entityType the entity class.
     * @param pk the primary key value.
     * @param <E> the entity type.
     * @return the cached entity, or {@code null} if not found or already garbage collected.
     */
    public <E extends Entity<?>> E get(@Nonnull Class<E> entityType, @Nonnull Object pk) {
        drainQueue();
        Ref<?> ref = Ref.of(entityType, pk);
        WeakReference<Entity<?>> existing = entityMap.get(ref);
        if (existing != null) {
            Entity<?> result = existing.get();
            if (result != null) {
                //noinspection unchecked
                return (E) result;
            }
        }
        return null;
    }

    /**
     * Interns an entity using its primary key (via {@link Ref}) for efficient lookup.
     *
     * <p>This avoids expensive deep equality checks on complex entity objects. The entity is stored with a weak
     * reference, and cleanup is handled via {@link #drainQueue()} when entities are garbage collected.</p>
     *
     * @param entity the entity to intern.
     * @param <E> the entity type.
     * @return the canonical instance for the entity's primary key.
     */
    private <E extends Entity<?>> E internEntity(@Nonnull E entity) {
        drainQueue();
        Ref<?> ref = Ref.of(entity);
        WeakReference<Entity<?>> existing = entityMap.get(ref);
        if (existing != null) {
            var result = existing.get();
            if (result != null) {
                //noinspection unchecked
                return (E) result;
            }
        }
        entityMap.put(ref, new RefWeakReference(ref, entity, queue));
        return entity;
    }

    /**
     * Interns a non-entity object using object equality for lookup.
     *
     * <p>Uses {@link WeakHashMap} which automatically removes entries when keys are garbage collected.</p>
     *
     * @param object the object to intern.
     * @param <T> the type of the object.
     * @return the canonical instance.
     * @throws IllegalArgumentException if an equivalent object of a different class is already interned.
     */
    private <T> T internObject(@Nonnull T object) {
        WeakReference<Object> existing = map.get(object);
        if (existing != null) {
            // Equivalent object found; return existing instance
            var result = existing.get();
            if (result != null) {
                if (result.getClass() != object.getClass()) {
                    throw new IllegalArgumentException("Cannot intern objects of different classes.");
                }
                //noinspection unchecked
                return (T) result;
            }
            return object;
        }
        map.put(object, new WeakReference<>(object));
        return object;
    }

    /**
     * Removes stale entries from {@link #entityMap} by polling the reference queue.
     *
     * <p>When an entity is garbage collected, its {@link RefWeakReference} is enqueued. This method polls the queue
     * and removes the corresponding entries from the map. Uses a two-argument remove to ensure only the exact
     * weak reference is removed, preventing removal of a newer entry with the same key.</p>
     */
    private void drainQueue() {
        RefWeakReference weakReference;
        while ((weakReference = (RefWeakReference) queue.poll()) != null) {
            entityMap.remove(weakReference.ref, weakReference);
        }
    }

    /**
     * A weak reference to an entity that retains the associated {@link Ref} for map cleanup.
     *
     * <p>When the entity is garbage collected, this reference is enqueued in the {@link ReferenceQueue}, allowing
     * {@link #drainQueue()} to remove the corresponding entry from {@link #entityMap} using the stored ref.</p>
     */
    private static final class RefWeakReference extends WeakReference<Entity<?>> {
        final Ref<?> ref;

        RefWeakReference(Ref<?> ref, Entity<?> referent, ReferenceQueue<? super Entity<?>> q) {
            super(referent, q);
            this.ref = ref;
        }
    }
}
