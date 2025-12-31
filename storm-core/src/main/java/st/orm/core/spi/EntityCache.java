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
import st.orm.Ref;

import java.util.Optional;

/**
 * Transaction-local cache that interns entities by primary key.
 *
 * <p>This cache provides canonicalization within a transaction: for a given primary key, callers can reuse an existing
 * in-memory entity instance when it is logically equal to a newly loaded instance.</p>
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
 * <p>This behavior allows identity stability for logically identical data while still permitting newer or different
 * representations of the same primary key to replace older ones.</p>
 *
 * @param <E> the entity type.
 * @param <ID> the primary key type.
 * @since 1.7
 */
public interface EntityCache<E extends Entity<ID>, ID> {

    /**
     * Retrieves an entity from the cache by primary key, if available.
     *
     * <p>The returned entity is the currently cached instance for the given primary key if it is still reachable
     * elsewhere. Implementations may use weak references, in which case a previously cached entity may have been
     * garbage collected and therefore no longer be available.</p>
     *
     * @param pk the primary key to look up.
     * @return an {@link Optional} containing the cached entity if present and still available, or
     * {@link Optional#empty()} otherwise.
     */
    Optional<E> get(@Nonnull ID pk);

    /**
     * Returns a canonical instance for the given entity within this cache.
     *
     * <p>If an entity with the same primary key is already cached and still reachable, that instance is returned
     * <em>only if</em> it is considered logically equal according to {@link Object#equals(Object)}. In that case, the
     * existing cached instance is reused.</p>
     *
     * <p>If no cached instance exists, the cached instance is no longer available, or the cached instance is not equal
     * to the provided entity, the cache is updated to reference the provided entity and that instance is returned.</p>
     *
     * @param entity the entity to intern.
     * @return the canonical cached instance for the entity's primary key.
     */
    E intern(@Nonnull E entity);

    /**
     * Stores the given entity in the cache under its primary key.
     *
     * <p>This is a "replace" operation: the cache entry for {@code entity.id()} is updated to point to the given
     * instance, regardless of whether a logically equal instance is already cached.</p>
     *
     * @param entity the entity to cache.
     */
    void set(@Nonnull E entity);

    /**
     * Stores all given entities in the cache.
     *
     * <p>This is a batch form of {@link #set(Entity)}. If multiple entities with the same primary key appear in the
     * input, the last one wins.</p>
     *
     * @param entities the entities to cache.
     */
    void set(@Nonnull Iterable<? extends E> entities);

    /**
     * Removes the cached entry for the given primary key, if present.
     *
     * @param pk the primary key to remove.
     */
    void remove(@Nonnull ID pk);

    /**
     * Removes cached entries for the given primary keys.
     *
     * <p>If a primary key is not present, it is ignored.</p>
     *
     * @param pks the primary keys to remove.
     */
    void remove(@Nonnull Iterable<? extends ID> pks);

    /**
     * Removes cached entries for the given entities by their primary keys.
     *
     * <p>This removes by primary key only. It does not check whether the cached instance is identical to the provided
     * entity instance.</p>
     *
     * @param entities the entities whose primary keys should be removed.
     */
    void removeEntities(@Nonnull Iterable<? extends E> entities);

    /**
     * Removes cached entries for the given {@link Ref references} by their ids.
     *
     * <p>This removes by id only. The cache does not require the referenced entity to be loaded.</p>
     *
     * <p><strong>Type note:</strong> {@link Ref#id()} returns {@code Object}. Implementations typically assume that the
     * id value is assignable to {@code ID}. If not, a {@link ClassCastException} may be thrown.</p>
     *
     * @param entities the references whose ids should be removed.
     */
    void removeRefs(@Nonnull Iterable<? extends Ref<E>> entities);

    /**
     * Clears all cached mappings.
     */
    void clear();
}