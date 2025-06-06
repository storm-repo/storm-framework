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
package st.orm;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.repository.Entity;
import st.orm.repository.Projection;
import st.orm.template.impl.AbstractRef;

import static java.util.Objects.requireNonNull;

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
public interface Ref<T extends Record> {

    /**
     * Creates a ref instance with a null value. This can be used to represent a null value for a foreign key
     * reference.
     *
     * @return ref instance.
     * @param <T> record type.
     */
    static <T extends Record> Ref<T> ofNull() {
        return new AbstractRef<>() {
            @Override
            protected boolean isFetched() {
                return true;
            }

            @Override
            protected Class<T> type() {
                return null;
            }

            @Override
            public Object id() {
                return null;
            }

            @Override
            public T fetch() {
                return null;
            }

            @Override
            public void unload() {
                // Nothing to unload.
            }
        };
    }

    /**
     * Creates a ref instance with the specified {@code entity}, if non-null, otherwise returns a null ref instance.
     *
     * @param entity the entity to wrap in a ref, or null if no entity is provided.
     * @return ref instance.
     * @param <E> record type.
     */
    static <E extends Record & Entity<?>> Ref<E> ofNullable(@Nullable E entity) {
        return entity == null ? ofNull() : of(entity);
    }

    /**
     * Creates a fully loaded ref instance that wraps the given entity.
     *
     * <p>This method creates a ref for an entity that is already fully loaded. The returned ref
     * is considered immutable; calling {@link #unload()} on it is a no-op, as the entity cannot be re-fetched.</p>
     *
     * @param entity the fully loaded entity to wrap in a ref.
     * @param <E> the type of the entity, which must extend {@link Record} and implement {@link Entity}.
     * @return a fully loaded ref instance for the provided entity.
     */
    static <E extends Record & Entity<?>> Ref<E> of(@Nonnull E entity) {
        return new AbstractRef<>() {
            @Override
            protected boolean isFetched() {
                return true;
            }

            @Override
            protected Class<E> type() {
                //noinspection unchecked
                return (Class<E>) entity.getClass();
            }

            @Override
            public Object id() {
                return entity.id();
            }

            @Override
            public E fetch() {
                return entity;
            }

            @Override
            public void unload() {
            }
        };
    }

    /**
     * Creates a ref instance with the specified {@code projection} and {@code id}, if both non-null, otherwise returns
     * a null ref instance.
     *
     * @param projection the projection to wrap in a ref, or null if no projection is provided.
     * @param id the primary key of the projection, or null if no primary key is provided.
     * @return ref instance.
     * @param <P> the type of the projection.
     * @param <ID> the type of the primary key.
     */
    static <P extends Record & Projection<ID>, ID> Ref<P> ofNullable(@Nullable P projection, @Nullable ID id)  {
        return projection == null || id == null ? ofNull() : of(projection, id);
    }

    /**
     * Creates a fully loaded ref instance that wraps the given projection along with its primary key.
     *
     * <p>This method creates a ref for a projection that is already fully loaded. The provided projection and its id are used
     * to form the ref instance. Similar to entity refs, calling {@link #unload()} on this ref is a no-op because the projection
     * is immutable and cannot be re-fetched.</p>
     *
     * @param projection the fully loaded projection to wrap in a ref.
     * @param id the primary key of the projection.
     * @param <P> the type of the projection.
     * @param <ID> the type of the primary key.
     * @return a fully loaded ref instance for the provided projection.
     */
    static <P extends Record & Projection<ID>, ID> Ref<P> of(@Nonnull P projection, @Nonnull ID id) {
        requireNonNull(projection, "Projection cannot be null.");
        requireNonNull(id, "ID cannot be null.");
        return new AbstractRef<>() {
            @Override
            protected boolean isFetched() {
                return true;
            }

            @Override
            public Class<P> type() {
                //noinspection unchecked
                return (Class<P>) projection.getClass();
            }

            @Override
            public ID id() {
                return id;
            }

            @Override
            public P fetch() {
                return projection;
            }

            @Override
            public void unload() {
            }
        };
    }

    /**
     * Extracts the primary key from the given entity ref returning a type-safe id.
     * 
     * @param ref ref to extract the primary key from.
     * @return the primary key of the specified ref.
     * @param <ID> the id type.
     * @param <E> the entity type.
     */
    static <ID, E extends Record & Entity<ID>> ID entityId(@Nonnull Ref<E> ref) {
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
    static <ID, P extends Record & Projection<ID>> ID projectionId(@Nonnull Ref<P> ref) {
        //noinspection unchecked
        return (ID) ref.id();
    }

    /**
     * Returns true if the ref instance represents a null value.
     *
     * @return true if the ref instance represents a null value.
     */
    default boolean isNull() {
        return id() == null;
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
     * Fetches the record from the database if the record has not been fetched yet. The record will be fetched at most
     * once.
     *
     * @return the fetched record.
     */
    T fetch();

    /**
     * Unloads the entity from memory, if applicable.
     *
     * <p>For refs that support lazy-loading, this method clears the cached record to free memory.
     * However, for fully loaded or immutable refs (such as refs generated via {@link #of(Record)} and
     * {@link #of(Record, Object)}), this method is a no-op because the record cannot be re-fetched. In such cases,
     * calling unload has no effect.</p>
     */
    void unload();
}
