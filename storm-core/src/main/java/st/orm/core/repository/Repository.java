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
package st.orm.core.repository;

import st.orm.WriteSet;
import st.orm.core.template.ORMTemplate;

/**
 * Base interface for all repositories.
 *
 * <h2>Method names</h2>
 *
 * <p>Repository method names follow a grammar, so a name is mostly constructed rather than looked up:</p>
 *
 * <ul>
 *     <li>{@code find} yields an empty {@code Optional} when nothing matches, {@code get} throws, and
 *     {@code findAll} yields a list.</li>
 *     <li>{@code Ref} <em>before</em> {@code By} returns {@link st.orm.Ref} in place of the entity:
 *     {@code findAllBy} gives {@code List<E>}, {@code findAllRefBy} gives {@code List<Ref<E>>}.</li>
 *     <li>{@code Id} selects on the primary key.</li>
 * </ul>
 *
 * <p>A ref argument is an overload rather than a separate method, so {@code findAllBy(field, value)} and
 * {@code findAllBy(field, ref)} share one name and the compiler picks by argument type.</p>
 *
 * <p>The {@code ByRef} suffix marks the two cases where a distinct name is unavoidable. Selecting on a
 * <em>collection</em> of refs erases to the same JVM signature as a collection of values, so the ref form is spelled
 * {@code findAllByRef(field, refs)}. Selecting on the entity's own refs likewise takes the suffix, as in
 * {@code findByRef(ref)} and {@code removeByRef(ref)}.</p>
 */
public interface Repository {

    /**
     * Provides access to the underlying ORM template.
     *
     * @return the ORM template.
     */
    ORMTemplate orm();

    /**
     * Returns dependency-aware write operations over mixed-type sets of entities.
     *
     * <p>The write set belongs to the underlying ORM template and is not scoped to this repository's entity type;
     * it accepts entities of any type. This accessor is a convenience for repository methods, equivalent to
     * {@code orm().writeSet()}.</p>
     *
     * @return the write set operations of the underlying ORM template.
     * @see WriteSet
     * @since 1.13
     */
    default WriteSet writeSet() {
        return orm().writeSet();
    }
}
