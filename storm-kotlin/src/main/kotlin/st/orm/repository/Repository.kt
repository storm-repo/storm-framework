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
package st.orm.repository

import st.orm.WriteSet
import st.orm.template.ORMTemplate

/**
 * Base interface for all Storm repositories, providing access to the underlying [ORMTemplate].
 *
 * Both [EntityRepository] and [ProjectionRepository] extend this interface. Custom repository
 * interfaces should extend one of those specialized interfaces rather than this one directly.
 *
 * @see EntityRepository
 * @see ProjectionRepository
 * @see ORMTemplate
 */
public interface Repository {
    /**
     * Provides access to the underlying ORM template.
     *
     * @return the ORM template.
     */
    public val orm: ORMTemplate

    /**
     * Returns dependency-aware write operations over mixed-type sets of entities.
     *
     * The write set belongs to the underlying ORM template and is not scoped to this repository's entity type;
     * it accepts entities of any type. This accessor is a convenience for repository methods, equivalent to
     * `orm.writeSet()`.
     *
     * @return the write set operations of the underlying ORM template.
     * @see WriteSet
     * @since 1.13
     */
    public fun writeSet(): WriteSet = orm.writeSet()
}
