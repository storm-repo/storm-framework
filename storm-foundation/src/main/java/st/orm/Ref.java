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
 * Ref records are used to represent reference to records, allowing them to be fetched from the database. This can be
 * used to defer the fetching of records until they are actually needed. Ref records are used to represent entities,
 * projections and regular records.
 *
 * <p>Ref records are generally used for foreign key references in entities and projections, preventing deep object
 * graphs. Alternatively, ref records can be used outside the scope entities and projections to simply act as a factory
 * pattern for fetching records on demand.</p>
 *
 * <p>Ref records are effectively immutable and can be used as keys in maps and sets. Equality is based on the primary
 * key of the record and the actual record instance will be fetched at most once.</p>
 *
 * @param <T> record type.
 * @since 1.3
 */
public interface Ref<T extends Data> {

    /**
     * The type of the record.
     *
     * @return the type of the record.
     */
    Class<T> type();

    /**
     * Creates a detached ref instance for the given type and primary key.
     *
     * <p>The returned ref is not connected to a database context. Calling {@link #fetch()} or {@link #fetchOrNull()}
     * on a detached ref will return {@code null} since there is no database connection available to retrieve the
     * record.</p>
     *
     * @param type the class of the record.
     * @param pk the primary key of the record.
     * @param <T> the type of the record, which must extend {@link Data}.
     * @param <ID> the type of the primary key.
     * @return a detached ref instance for the given type and primary key.
     */
    static <T extends Data, ID> Ref<T> of(@Nonnull Class<T> type, @Nonnull ID pk) {
        return new DetachedRef<>(type, pk);
    }

    /**
     * Creates a fully loaded ref instance that wraps the given entity.
     *
     * @param entity the fully loaded entity to wrap in a ref.
     * @param <E> the type of the entity, which must extend {@link Record} and implement {@link Entity}.
     * @return a fully loaded ref instance for the provided entity.
     */
    static <E extends Entity<?>> Ref<E> of(@Nonnull E entity) {
        class DetachedEntity<TE extends Entity<?>> extends AbstractRef<TE> {
            private final TE entity;

            DetachedEntity(@Nonnull TE entity) {
                requireNonNull(entity, "Entity cannot be null.");
                requireNonNull(entity.id(), "Entity ID cannot be null.");
                this.entity = entity;
            }

            @Override
            public Class<TE> type() {
                //noinspection unchecked
                return (Class<TE>) entity.getClass();
            }

            @Override
            public Object id() {
                return entity.id();
            }

            @Override
            public TE getOrNull() {
                return entity;
            }

            @Override
            public TE fetchOrNull() {
                return entity;
            }

            @Override
            public boolean isFetchable() {
                return false;
            }

            @Override
            public Ref<TE> unload() {
                return Ref.of(type(), id());
            }
        }
        return new DetachedEntity<>(entity);
    }

    /**
     * Creates a fully loaded ref instance that wraps the given projection along with its primary key.
     *
     * @param projection the fully loaded projection to wrap in a ref.
     * @param id the primary key of the projection.
     * @param <P> the type of the projection.
     * @param <ID> the type of the primary key.
     * @return a fully loaded ref instance for the provided projection.
     */
    static <P extends Projection<ID>, ID> Ref<P> of(@Nonnull P projection, @Nonnull ID id) {
        class DetachedProjection<TE extends Projection<TID>, TID> extends AbstractRef<TE> {
            private final TID id;
            private final TE projection;

            DetachedProjection(TID id, TE projection) {
                this.id = requireNonNull(id, "ID cannot be null.");
                this.projection = requireNonNull(projection, "Projection cannot be null.");
            }

            @Override
            public Class<TE> type() {
                //noinspection unchecked
                return (Class<TE>) projection.getClass();
            }

            @Override
            public Object id() {
                return id;
            }

            @Override
            public TE getOrNull() {
                return projection;
            }

            @Override
            public TE fetchOrNull() {
                return projection;
            }

            @Override
            public boolean isFetchable() {
                return false;
            }

            @Override
            public Ref<TE> unload() {
                return Ref.of(type(), id());
            }
        }
        return new DetachedProjection<>(id, projection);
    }

    /**
     * Extracts the primary key from the given entity ref returning a type-safe id.
     *
     * @param ref ref to extract the primary key from.
     * @return the primary key of the specified ref.
     * @param <ID> the id type.
     * @param <E> the entity type.
     */
    static <ID, E extends Entity<ID>> ID entityId(@Nonnull Ref<E> ref) {
        //noinspection unchecked
        return (ID) ref.id();
    }

    /**
     * Extracts the primary key from the given projection ref returning a type-safe id.
     *
     * @param ref ref to extract the primary key from.
     * @return the primary key of the specified ref.
     * @param <ID> the id type.
     * @param <P> the projection type.
     */
    static <ID, P extends Projection<ID>> ID projectionId(@Nonnull Ref<P> ref) {
        //noinspection unchecked
        return (ID) ref.id();
    }

    /**
     * Returns the primary key of the record.
     *
     * <p>This method is provided for convenience. If the type of the id is known, you can cast it to the appropriate
     * type.</p>
     *
     * @return the primary key as an Object.
     */
    Object id();

    /**
     * Returns the record if it has already been fetched, without triggering a database call.
     *
     * @return the record if already loaded, or {@code null} if not yet fetched.
     * @since 1.7
     */
    @Nullable
    T getOrNull();

    /**
     * Fetches the record if it has not been fetched yet. The record will be fetched at most once.
     *
     * <p>Within a transaction, this method may return the same instance as other retrieval operations for the same
     * primary key without querying the database, depending on the transaction isolation level.</p>
     *
     * @return the fetched record.
     * @throws PersistenceException if the record is not available and the Ref is not attached.
     */
    default T fetch() {
        T record = fetchOrNull();
        if (record == null) {
            throw new PersistenceException("Record is not available.");
        }
        return record;
    }

    /**
     * Fetches the record if it has not been fetched yet. Returns {@code null} if the record is not available and the
     * Ref is not attached.
     *
     * <p>Within a transaction, this method may return the same instance as other retrieval operations for the same
     * primary key without querying the database, depending on the transaction isolation level.</p>
     *
     * @return the fetched record, or {@code null} if the record is not available and the Ref is not attached.
     * @since 1.7
     */
    @Nullable
    T fetchOrNull();

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
    boolean isFetchable();

    /**
     * Returns whether the record has already been fetched and is available in memory.
     *
     * <p>This method does not trigger a database call. It simply checks if the record is currently cached.</p>
     *
     * @return {@code true} if the record is loaded and available via {@link #getOrNull()}, {@code false} otherwise.
     * @since 1.7
     */
    default boolean isLoaded() {
        return getOrNull() != null;
    }

    /**
     * Returns a ref with the same identity but without data.
     *
     * <p>For attached refs, this clears the fetched record while preserving the ability to re-fetch from the database.
     * The same ref instance may be returned since the data can be recovered on demand.</p>
     *
     * <p>For detached refs that hold a loaded record, a new unloaded ref is returned. Note that calling {@link #fetch()}
     * on the returned ref will fail since there is no database connection to recover the data. Use this with caution
     * when working with detached refs, as the original data cannot be retrieved.</p>
     *
     * <p>For detached refs that are already unloaded, this method returns the same instance.</p>
     *
     * @return a ref with the same type and primary key but without cached data; may return {@code this} if no new
     *         instance is required.
     */
    Ref<T> unload();
}
