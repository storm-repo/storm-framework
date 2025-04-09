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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.Ref;
import st.orm.spi.Provider;
import st.orm.spi.QueryFactory;
import st.orm.template.QueryBuilder;

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of {@link RefFactory}.
 *
 * @since 1.3
 */
public final class RefFactoryImpl implements RefFactory {
    private final QueryFactory factory;
    private final ModelBuilder modelBuilder;
    private final Predicate<? super Provider> providerFilter;

    public RefFactoryImpl(@Nonnull QueryFactory factory,
                          @Nonnull ModelBuilder modelBuilder,
                          @Nullable Predicate<? super Provider> providerFilter) {
        this.factory = requireNonNull(factory, "factory");
        this.modelBuilder = requireNonNull(modelBuilder, "modelBuilder");
        this.providerFilter = providerFilter;
    }

    /**
     * Creates a ref instance for the specified record {@code type} and {@code pk}. This method can be used to generate
     * ref instances for entities, projections and regular records.
     *
     * @param type record type.
     * @param pk primary key.
     * @return ref instance.
     * @param <T> record type.
     * @param <ID> primary key type.
     */
    @Override
    public <T extends Record, ID> Ref<T> create(@Nonnull Class<T> type, @Nonnull ID pk) {
        //noinspection unchecked
        var builder = (QueryBuilder<T, T, ID>) new ORMTemplateImpl(
                factory,
                modelBuilder,
                providerFilter).selectFrom(type);
        var supplier = new LazySupplier<>(() -> builder.where(pk).getSingleResult());
        return create(supplier, type, pk);
    }


    /**
     * Creates a ref instance for the specified {@code record}, {@code type} and {@code pk}. This method can be used to
     * generate ref instances for entities, projections and regular records. The object returned by this method already
     * contains the fetched record.
     *
     * @param pk primary key.
     * @return ref instance.
     * @param <T> record type.
     * @param <ID> primary key type.
     */
    @Override
    public <T extends Record, ID> Ref<T> create(@Nonnull T record, @Nonnull ID pk) {
        //noinspection unchecked
        var type = (Class<T>) record.getClass();
        //noinspection unchecked
        var builder = (QueryBuilder<T, T, ID>) new ORMTemplateImpl(
                factory,
                modelBuilder,
                providerFilter).selectFrom(type);
        var supplier = new LazySupplier<>(() -> builder.where(pk).getSingleResult(), record);
        return create(supplier, type, pk);
    }

    /**
     * Creates a ref instance for the specified record {@code type} and {@code pk}. This method can be used to generate
     * ref instances for entities, projections and regular records.
     *
     * @param type record type.
     * @param pk primary key.
     * @return ref instance.
     * @param <T> record type.
     * @param <ID> primary key type.
     */
    private <T extends Record, ID> Ref<T> create(@Nonnull LazySupplier<T> supplier, @Nonnull Class<T> type, @Nonnull ID pk) {
        class RefImpl extends AbstractRef<T> {
            private final ID pk;

            RefImpl(@Nonnull ID pk) {
                this.pk = requireNonNull(pk);
            }

            @Override
            protected boolean isFetched() {
                return supplier.value().isPresent();
            }

            @Override
            public Class<T> type() {
                return type;
            }

            @Override
            public ID id() {
                return pk;
            }

            @Override
            public T fetch() {
                return supplier.get();
            }

            @Override
            public void unload() {
                supplier.remove();
            }
        }
        return new RefImpl(pk);
    }
}
