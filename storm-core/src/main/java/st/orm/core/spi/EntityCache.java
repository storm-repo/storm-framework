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

import java.util.Optional;

/**
 * Transaction-local cache that interns entities by primary key.
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
 * @param <E> the entity type.
 * @param <ID> the primary key type.
 * @since 1.7
 */
public interface EntityCache<E extends Entity<ID>, ID> {

    /**
     * Retrieves an entity from the cache by primary key, if available.
     *
     * <p>The returned entity is guaranteed to be the canonical cached instance for the given primary key
     * <em>only if</em> it is still strongly reachable elsewhere.</p>
     *
     * @param pk the primary key to look up.
     * @return an {@link Optional} containing the cached entity if present and still alive, or
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
    E intern(@Nonnull E entity);
}
