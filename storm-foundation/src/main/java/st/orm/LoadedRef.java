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

/**
 * Detached {@link Ref} implementation wrapping an already loaded record along with its row identity.
 *
 * <p>The id is stored rather than derived so one implementation serves both entities, whose id comes from the
 * record itself, and projections, whose row identity is supplied separately. The ref is not attached to a
 * database context; the wrapped record is all it can return.</p>
 *
 * @param <T> record type.
 * @since 1.14
 */
final class LoadedRef<T extends Data> extends AbstractRef<T> {
    private final T value;
    private final Object id;

    LoadedRef(T value, Object id) {
        this.value = requireNonNull(value, "value");
        this.id = id;
    }

    @Override
    public Class<T> type() {
        //noinspection unchecked
        return (Class<T>) value.getClass();
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public T getOrNull() {
        return value;
    }

    @Override
    public T fetchOrNull() {
        return value;
    }

    @Override
    public boolean isFetchable() {
        return false;
    }

    @Override
    public Ref<T> unload() {
        return Ref.of(type(), id());
    }
}
