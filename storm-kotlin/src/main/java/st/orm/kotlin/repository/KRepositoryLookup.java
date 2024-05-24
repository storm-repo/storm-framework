/*
 * Copyright 2024 the original author or authors.
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
import st.orm.repository.RepositoryLookup;

/**
 * Base interface for all repositories.
 */
public interface KRepositoryLookup {

    RepositoryLookup bridge();

    <T extends Entity<ID>, ID> KEntityRepository<T, ID> repository(@Nonnull KClass<T> type);

    <R extends KRepository> R repositoryProxy(@Nonnull KClass<R> type);

    <T extends Entity<ID>, ID> KEntityRepository<T, ID> repository(@Nonnull Class<T> type);

    <R extends KRepository> R repositoryProxy(@Nonnull Class<R> type);
}
