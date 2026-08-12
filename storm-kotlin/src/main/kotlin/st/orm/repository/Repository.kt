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
 * <h2>Method names</h2>
 *
 * Repository method names follow a grammar, so a name is mostly constructed rather than looked up:
 *
 * - `find` yields `null` when nothing matches, `get` throws, and `findAll` yields a list.
 * - `Ref` **before** `By` returns [st.orm.Ref] in place of the entity: `findAllBy` gives `List<E>`, `findAllRefBy`
 *   gives `List<Ref<E>>`.
 * - `Id` selects on the primary key.
 *
 * A ref argument is an overload rather than a separate method, so `findAllBy(field, value)` and
 * `findAllBy(field, ref)` share one name and the compiler picks by argument type.
 *
 * The `ByRef` suffix marks the two cases where a distinct name is unavoidable. Selecting on a *collection* of refs
 * erases to the same JVM signature as a collection of values, so the ref form is spelled `findAllByRef(field, refs)`.
 * Selecting on the entity's own refs likewise takes the suffix, as in `findByRef(ref)` and `removeByRef(ref)`.
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
