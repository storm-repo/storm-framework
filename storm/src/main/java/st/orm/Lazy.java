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
package st.orm;

import jakarta.annotation.Nullable;
import st.orm.repository.Entity;
import st.orm.repository.Projection;
import st.orm.template.impl.AbstractLazy;

/**
 * Lazy records are used to represent records that are not yet fetched from the database. This can be used to defer the
 * fetching of records until they are actually needed. Lazy records are used to represent entities, projections and
 * regular records.
 *
 * <p>Lazy records are generally used for foreign key references in entities and projections, preventing deep object
 * graphs. Alternatively, lazy records can be used outside the scope entities and projections to simply act as a factory
 * pattern for fetching records on demand.</p>
 *
 * <p>Lazy records are effectively immutable and can be used as keys in maps and sets. Equality is based on the primary
 * key of the record and the actual record instance will be fetched at most once.</p>
 */
public interface Lazy<T extends Record, ID> {

    /**
     * Creates a lazy instance with a null value. This can be used to represent a null value for a foreign key
     * reference.
     *
     * @return lazy instance.
     * @param <T> record type.
     * @param <ID> primary key type.
     */
    static <T extends Record, ID> Lazy<T, ID> ofNull() {
        return new Lazy<>() {
            @Override
            public ID id() {
                return null;
            }

            @Override
            public T fetch() {
                return null;
            }
        };
    }

    /**
     * Creates a lazy instance for the specified record {@code entity}.
     *
     * @param entity record.
     * @return lazy instance.
     * @param <E> entity type.
     * @param <ID> primary key type.
     */
    static <E extends Record & Entity<ID>, ID> Lazy<E, ID> of(@Nullable E entity) {
        return new AbstractLazy<>() {
            @Override
            protected Class<E> type() {
                //noinspection unchecked
                return entity != null ? (Class<E>) entity.getClass() : null;
            }

            @Override
            protected boolean isFetched() {
                return entity != null;
            }

            @Override
            public ID id() {
                return entity == null ? null : entity.id();
            }

            @Override
            public E fetch() {
                return entity;
            }
        };
    }

    /**
     * Creates a lazy instance for the specified record {@code projection} and {@code id}.
     *
     * @param projection projection.
     * @param id primary key.
     * @return lazy instance.
     * @param <P> projection type.
     * @param <ID> primary key type.
     * @throws IllegalArgumentException if {@code projection} is null and {@code id} is not or if {@code projection} is
     * not null and {@code id} is.
     */
    static <P extends Record & Projection<ID>, ID> Lazy<P, ID> of(@Nullable P projection, @Nullable ID id) {
        if (projection == null && id != null) {
            throw new IllegalArgumentException(STR."Projection is null but id is not: \{id}.");
        }
        if (projection != null && id == null) {
            throw new IllegalArgumentException(STR."Projection is not null but id is: \{projection}.");
        }
        return new AbstractLazy<>() {
            @Override
            protected Class<P> type() {
                //noinspection unchecked
                return projection != null ? (Class<P>) projection.getClass() : null;
            }

            @Override
            protected boolean isFetched() {
                return projection != null;
            }

            @Override
            public ID id() {
                return id;
            }

            @Override
            public P fetch() {
                return projection;
            }
        };
    }

    /**
     * Returns true if the lazy instance represents a null value.
     *
     * @return true if the lazy instance represents a null value.
     */
    default boolean isNull() {
        return id() == null;
    }

    /**
     * Returns the primary key of the record.
     *
     * @return primary key.
     */
    ID id();

    /**
     * Fetches the record from the database if the record has not been fetched yet. The record will be fetched at most
     * once.
     *
     * @return the fetched record.
     */
    T fetch();
}
