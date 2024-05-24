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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

/**
 * Returns a lazily initialized value.
 *
 * @param <T> the type of results supplied by this supplier.
 */
final class LazySupplier<T> implements Supplier<T> {

    /**
     * Returns a supplier which lazy initializes the value.
     *
     * @param supplier the supplier that provides the value.
     * @return a supplier which lazy initializes the value.
     * @param <T> the type of results supplied by this supplier.
     */
    public static <T> Supplier<T> lazy(@Nonnull Supplier<T> supplier) {
        return new LazySupplier<>(supplier);
    }

    private final Supplier<T> supplier;
    private final AtomicReference<T> reference;

    public LazySupplier(@Nonnull Supplier<T> supplier) {
        this.supplier = requireNonNull(supplier);
        this.reference = new AtomicReference<>();
    }

    /**
     * Gets a result.
     *
     * @return a result
     */
    @Override
    public T get() {
        return reference.updateAndGet(value -> requireNonNullElseGet(value, supplier));
    }

    /**
     * Returns the value if it has already been lazily loaded, otherwise an empty optional is returned.
     *
     * @return the value if it has already been lazily loaded, otherwise an empty optional.
     * @since 2.10
     */
    public Optional<T> value() {
        return ofNullable(reference.get());
    }

    /**
     * Returns the first argument if it is non-{@code null} and otherwise returns the non-{@code null} value of
     * {@code supplier.get()}.
     *
     * @param obj an object.
     * @param supplier of a non-{@code null} object to return if the first argument is {@code null}.
     * @param <T> the type of the first argument and return type.
     * @return the first argument if it is non-{@code null} and otherwise the value from {@code supplier.get()} if it is
     * non-{@code null}.
     * @throws NullPointerException if both {@code obj} is null and either the {@code supplier} is {@code null} or
     * the {@code supplier.get()} value is {@code null}.
     */
    static <T> T requireNonNullElseGet(T obj, Supplier<? extends T> supplier) {
        return (obj != null) ? obj
                : requireNonNull(requireNonNull(supplier, "supplier").get(), "supplier.get()");
    }
}
