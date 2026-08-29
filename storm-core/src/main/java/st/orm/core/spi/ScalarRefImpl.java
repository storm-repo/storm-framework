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

import java.util.Objects;
import st.orm.Data;
import st.orm.Ref;
import st.orm.core.template.impl.LazySupplier;

/**
 * {@link Ref} implementation for keys that are their own row identity.
 *
 * <p>The factory selects this implementation at type level, when the pk class carries no non-key state (see
 * {@link RowIdentity#requiresNormalization(Class)}). The identity of such a ref is the pk itself and its hash is a
 * few instructions, so this implementation carries neither the row identity nor the hash cache of the general
 * implementation, keeping refs created per row during materialization at their minimal footprint. Equality and
 * hash follow the shared contract in {@link RowIdentity}, so instances compare correctly against every other ref
 * implementation.</p>
 *
 * @param <T> record type.
 * @param <ID> primary key type.
 */
final class ScalarRefImpl<T extends Data, ID> extends BaseRef<T, ID> {

    ScalarRefImpl(LazySupplier<T> supplier, Class<T> type, ID pk) {
        super(supplier, type, pk);
    }

    @Override
    public int hashCode() {
        return RowIdentity.hash(type(), id());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ScalarRefImpl<?, ?> other) {
            // Both sides hold their identity as the raw pk of a class that is its own row identity; the type gate guarantees the
            // pk classes match.
            return Objects.equals(type(), other.type()) && id().equals(other.id());
        }
        if (obj instanceof Ref<?> l) {
            return RowIdentity.refEquals(type(), id(), l);
        }
        return false;
    }

    @Override
    public String toString() {
        Class<?> type = type();
        return type == Record.class
                ? "%s".formatted(id())
                : "%s@%s".formatted(type.getSimpleName(), id());
    }
}
