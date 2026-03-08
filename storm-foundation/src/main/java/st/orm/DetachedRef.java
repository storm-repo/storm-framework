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
package st.orm;

import static java.util.Objects.requireNonNull;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Detached {@link Ref} implementation.
 *
 * @param <T> record type.
 * @param <ID> primary key type.
 */
final class DetachedRef<T extends Data, ID> extends AbstractRef<T> {
    private final Class<T> type;
    private final ID pk;

    public DetachedRef(@Nonnull Class<T> type, @Nonnull ID pk) {
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
        return null;
    }

    /**
     * Returns the primary key of the record.
     *
     * <p>This method is provided for convenience. If the type of the id is known, you can cast it to the appropriate
     * type.</p>
     *
     * @return the primary key as an Object.
     */
    @Override
    public ID id() {
        return pk;
    }

    /**
     * Fetches the record from the database if the record has not been fetched yet. The record will be fetched at most
     * once.
     *
     * @return the fetched record.
     * @since 1.7
     */
    @Override
    public T fetchOrNull() {
        return null;
    }

    /**
     * Returns whether this ref is attached to a database context and capable of fetching the record on demand.
     *
     * <p>A fetchable ref has access to a database connection and can attempt to retrieve the record when
     * {@link #fetch()} or {@link #fetchOrNull()} is called. A non-fetchable (detached) ref can only return
     * data that was already loaded at the time of its creation.</p>
     *
     * <p>Note that this method indicates the <em>capability</em> to fetch, not a guarantee of success. A fetchable
     * ref may still fail to retrieve a record if it has been deleted from the database or if the connection
     * encounters an error.</p>
     *
     * @return {@code true} if this ref can attempt to fetch from the database, {@code false} if it is detached.
     * @since 1.7
     */
    @Override
    public boolean isFetchable() {
        return false;
    }

    /**
     * Returns a detached ref with the same identity but without data. Since this ref is already detached and unloaded,
     * the same instance is returned.
     *
     * @return this instance.
     */
    @Override
    public Ref<T> unload() {
        return this;
    }
}
