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
package st.orm.core.repository;

import jakarta.annotation.Nonnull;
import st.orm.Entity;
import st.orm.Projection;

/**
 * Provides access to repositories.
 *
 * <p>Entity repositories returned by {@code entity} provide basic CRUD operations for database tables. Projection
 * repositories returned by {@code projection} provide read operations for database views and projection queries. The
 * repositories returned by the {@code repository} method allow implementing specialized repository logic by
 * implementing default methods. The default methods have full access to the CRUD and {@code QueryBuilder} logic of the
 * repository it extends:</p>
 *
 * <pre>{@code
 * interface UserRepository extends EntityRepository<User, Integer> {
 *     default Optional<User> findByName(String name) {
 *         return select().
 *             .where(User_.name, EQUALS, name) // Type-safe metamodel.
 *             .getOptionalResult();
 *     }
 *
 *     default List<User> findByCity(City city) {
 *         return select().
 *             .where(User_.city, city) // Type-safe metamodel.
 *             .getResultList();
 *     }
 * }}</pre>
 *
 * @see EntityRepository
 * @see ProjectionRepository
 */
public interface RepositoryLookup {

    /**
     * Returns the repository for the given entity type.
     *
     * @param type the entity type.
     * @param <T> the entity type.
     * @param <ID> the type of the entity's primary key.
     * @return the repository for the given entity type.
     */
    <T extends Record & Entity<ID>, ID> EntityRepository<T, ID> entity(@Nonnull Class<T> type);

    /**
     * Returns the repository for the given projection type.
     *
     * @param type the projection type.
     * @param <T> the projection type.
     * @param <ID> the type of the projection's primary key, or Void if the projection specifies no primary key.
     * @return the repository for the given projection type.
     */
    <T extends Record & Projection<ID>, ID> ProjectionRepository<T, ID> projection(@Nonnull Class<T> type);

    /**
     * Returns a proxy for the repository of the given type.
     *
     * @param type the repository type.
     * @param <R> the repository type.
     * @return a proxy for the repository of the given type.
     */
    <R extends Repository> R repository(@Nonnull Class<R> type);
}
