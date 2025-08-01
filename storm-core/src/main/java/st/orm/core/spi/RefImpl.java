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
package st.orm.core.spi;

import jakarta.annotation.Nonnull;
import st.orm.Ref;
import st.orm.core.template.impl.LazySupplier;

import static java.util.Objects.requireNonNull;

/**
 * Default {@link Ref} implementation.
 *
 * @param <T> record type.
 * @param <ID> primary key type.
 */
final class RefImpl<T extends Record, ID> extends AbstractRef<T> {
    private final LazySupplier<T> supplier;
    private final Class<T> type;
    private final ID pk;

    RefImpl(@Nonnull LazySupplier<T> supplier, @Nonnull Class<T> type, @Nonnull ID pk) {
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
     */
    @Override
    public T fetch() {
        return supplier.get();
    }

    /**
     * Unloads the entity from memory.
     */
    @Override
    public void unload() {
        supplier.remove();
    }
}
