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

import java.util.Objects;

/**
 *
 */
public interface Lazy<T extends Entity<?>> {

    static <T extends Entity<?>> Lazy<T> ofNull() {
        return of(null);
    }

    static <T extends Entity<?>> Lazy<T> of(@Nullable T entity) {
        return new Lazy<>() {
            @Override
            public boolean isNull() {
                return entity == null;
            }

            @Override
            public Object id() {
                return entity == null ? null : entity.id();
            }

            @Override
            public T fetch() {
                return entity;
            }

            @Override
            public int hashCode() {
                return Objects.hashCode(entity == null ? null : entity.id());
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Lazy<?> other) {
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

    boolean isNull();

    Object id();

    T fetch();
}
