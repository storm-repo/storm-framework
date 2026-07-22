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

import static java.util.Objects.requireNonNull;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Objects;
import st.orm.Data;
import st.orm.Entity;
import st.orm.Ref;
import st.orm.core.repository.EntityRepository;
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
final class ScalarRefImpl<T extends Data, ID> implements Ref<T> {
    private final LazySupplier<T> supplier;
    private final Class<T> type;
    private final ID pk;

    ScalarRefImpl(@Nonnull LazySupplier<T> supplier, @Nonnull Class<T> type, @Nonnull ID pk) {
        this.supplier = requireNonNull(supplier, "supplier");
        this.type = requireNonNull(type, "type");
        this.pk = requireNonNull(pk, "pk");
    }

    /**
     * The type of the record.
     *
     * @return the type of the record.
     */
    @Override
    public Class<T> type() {
        return type;
    }

    /**
     * Returns the record if it has already been fetched, without triggering a database call.
     *
     * @return the record if already loaded, or {@code null} if not yet fetched.
     */
    @Nullable
    @Override
    public T getOrNull() {
        return supplier.value().orElse(null);
    }

    /**
     * Returns the primary key of the record.
     *
     * @return the primary key as an Object.
     */
    @Override
    public ID id() {
        return pk;
    }

    /**
     * Fetches the record from the database if the record has not been fetched yet. The record will be fetched at
     * most once.
     *
     * @return the fetched record.
     */
    @Override
    public T fetchOrNull() {
        return supplier.get();
    }

    /**
     * Returns whether this ref is attached to a database context and capable of fetching the record on demand.
     *
     * @return {@code true}, this implementation is created attached to a database context.
     */
    @Override
    public boolean isFetchable() {
        return true;
    }

    /**
     * Returns a detached ref with the same identity but without data. The returned ref is not attached to a
     * database context. To obtain an attached ref that can re-fetch the record, use
     * {@link EntityRepository#unload(Entity) EntityRepository.unload()} instead.
     *
     * @return a detached ref with the same type and primary key but without cached data.
     */
    @Override
    public Ref<T> unload() {
        return Ref.of(type, pk);
    }

    @Override
    public int hashCode() {
        return RowIdentity.hash(type, pk);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ScalarRefImpl<?, ?> other) {
            // Both sides hold their identity as the raw pk of a class that is its own row identity; the type gate guarantees the
            // pk classes match.
            return Objects.equals(type, other.type) && pk.equals(other.pk);
        }
        if (obj instanceof Ref<?> l) {
            return RowIdentity.refEquals(type, pk, l);
        }
        return false;
    }

    @Override
    public String toString() {
        Class<?> type = this.type;
        return type == Record.class
                ? "%s".formatted(pk)
                : "%s@%s".formatted(type.getSimpleName(), pk);
    }
}
