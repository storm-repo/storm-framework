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

import java.util.Objects;

/**
 *
 */
public interface Lazy<T extends Record, ID> {

    static <T extends Record, ID> Lazy<T, ID> ofNull() {
        return new Lazy<T, ID>() {
            @Override
            public boolean isNull() {
                return true;
            }

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

    static <E extends Record & Entity<ID>, ID> Lazy<E, ID> of(@Nullable E entity) {
        return new Lazy<>() {
            @Override
            public boolean isNull() {
                return entity == null;
            }

            @Override
            public ID id() {
                return entity == null ? null : entity.id();
            }

            @Override
            public E fetch() {
                return entity;
            }

            @Override
            public int hashCode() {
                return Objects.hashCode(entity == null ? null : entity.id());
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Lazy<?, ?> other) {
                    var otherId = other.id();
                    return Objects.equals(entity == null
                                    ? null
                                    : entity.id(), otherId);
                }
                return false;
            }

            @Override
            public String toString() {
                return STR."Lazy[pk=\{entity == null ? null : entity.id()}, fetched=\{entity != null}]";
            }
        };
    }

    static <P extends Record & Projection<ID>, ID> Lazy<P, ID> of(@Nullable P projection, @Nullable ID id) {
        if (projection == null && id != null) {
            throw new IllegalArgumentException(STR."Projection is null but id is not: \{id}.");
        }
        if (projection != null && id == null) {
            throw new IllegalArgumentException(STR."Projection is not null but id is: \{projection}.");
        }
        return new Lazy<>() {
            @Override
            public boolean isNull() {
                return projection == null;
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
            public int hashCode() {
                return Objects.hashCode(id);
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Lazy<?, ?> other) {
                    var otherId = other.id();
                    return Objects.equals(id, other.id());
                }
                return false;
            }

            @Override
            public String toString() {
                return STR."Lazy[pk=\{projection == null ? null : id}, fetched=\{projection != null}]";
            }
        };

    }

    boolean isNull();

    ID id();

    T fetch();
}
