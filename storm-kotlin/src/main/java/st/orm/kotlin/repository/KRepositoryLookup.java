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
package st.orm.kotlin.repository;

import jakarta.annotation.Nonnull;
import kotlin.reflect.KClass;
import st.orm.repository.Entity;
import st.orm.repository.Projection;

/**
 * Provides access to repositories.
 *
 * <p>Entity repositories provide basic CRUD operations for database tables. Projection repositories provide read
 * operations for database views and projection queries. The repositories returned by the {@code repository} method
 * allow to implement custom repository logic.</p>
 *
 * <pre>{@code
 * interface UserRepository extends KEntityRepository<User, Integer> {
 *     default Optional<User> findByName(String name) {
 *         return select().
 *             .where(User_.name, EQUALS, name)
 *             .getOptionalResult();
 *     }
 *
 *     default List<User> findByCity(City city) {
 *         return select().
 *             .where(User_city, city)
 *             .getResultList();
 *     }
 * }}</pre>
 *
 * @see KEntityRepository
 * @see KProjectionRepository
 */
public interface KRepositoryLookup {

    /**
     * Returns the repository for the given entity type.
     *
     * @param type the entity type.
     * @param <T> the entity type.
     * @param <ID> the type of the entity's primary key.
     * @return the repository for the given entity type.
     */
    <T extends Record & Entity<ID>, ID> KEntityRepository<T, ID> entity(@Nonnull KClass<T> type);

    /**
     * Returns the repository for the given projection type.
     *
     * @param type the projection type.
     * @param <T> the projection type.
     * @param <ID> the type of the projection's primary key, or Void if the projection specifies no primary key.
     * @return the repository for the given projection type.
     */
    <T extends Record & Projection<ID>, ID> KProjectionRepository<T, ID> projection(@Nonnull KClass<T> type);

    /**
     * Returns a proxy for the repository of the given type.
     *
     * @param type the repository type.
     * @param <R> the repository type.
     * @return a proxy for the repository of the given type.
     */
    <R extends KRepository> R repository(@Nonnull KClass<R> type);

    /**
     * Returns the repository for the given entity type.
     *
     * @param type the entity type.
     * @param <T> the entity type.
     * @param <ID> the type of the entity's primary key.
     * @return the repository for the given entity type.
     */
    <T extends Record & Entity<ID>, ID> KEntityRepository<T, ID> entity(@Nonnull Class<T> type);

    /**
     * Returns the repository for the given projection type.
     *
     * @param type the projection type.
     * @param <T> the projection type.
     * @param <ID> the type of the projection's primary key, or Void if the projection specifies no primary key.
     * @return the repository for the given projection type.
     */
    <T extends Record & Projection<ID>, ID> KProjectionRepository<T, ID> projection(@Nonnull Class<T> type);

    /**
     * Returns a proxy for the repository of the given type.
     *
     * @param type the repository type.
     * @param <R> the repository type.
     * @return a proxy for the repository of the given type.
     */
    <R extends KRepository> R repository(@Nonnull Class<R> type);
}
