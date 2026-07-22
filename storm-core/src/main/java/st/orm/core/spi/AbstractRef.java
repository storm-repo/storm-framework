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

/**
 * Abstract implementation of {@link Ref} to have consistent implementations of {@link #hashCode()}
 * and {@link #equals(Object)}.
 *
 * <p>Equality is based on the type and the row identity of the id (see {@link RowIdentity}), matching the detached
 * ref implementations of the foundation module: an entity-typed id counts by its primary key rather than by
 * structural equality, so two refs describing the same database row compare equal even when a non-key column of
 * the key entity does not round-trip bit-exact. Scalar ids, and composite ids carrying only scalars, are compared
 * as-is.</p>
 *
 * @param <T> record type.
 * @since 1.3
 */
abstract class AbstractRef<T extends Data> implements Ref<T> {

    /**
     * Lazily computed row identity of the id. Computed outside construction because only map-keyed usage needs the
     * identity; the computation is idempotent over the immutable id, so the unsynchronized publication is a benign
     * race, as with {@code String} hash caching. Keys that are their own row identity are served by a specialized
     * implementation that carries no cache at all (see {@code ScalarRefImpl}).
     */
    private Object rowId;

    private Object rowId() {
        Object rowId = this.rowId;
        if (rowId == null) {
            rowId = RowIdentity.normalize(id());
            this.rowId = rowId;
        }
        return rowId;
    }

    /**
     * Lazily computed hash code over the type and row identity, both immutable; zero means not yet computed and a
     * value that genuinely hashes to zero is recomputed on each call, as with {@code String} hash caching.
     */
    private int hash;

    @Override
    public int hashCode() {
        int hash = this.hash;
        if (hash == 0) {
            hash = RowIdentity.hash(type(), rowId());
            this.hash = hash;
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof AbstractRef<?> other) {
            return Objects.equals(type(), other.type())
                    && Objects.equals(rowId(), other.rowId());
        }
        if (obj instanceof Ref<?> l) {
            return RowIdentity.refEquals(type(), rowId(), l);
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
