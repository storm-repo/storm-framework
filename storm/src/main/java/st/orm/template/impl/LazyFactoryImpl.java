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
import st.orm.Lazy;
import st.orm.spi.Provider;
import st.orm.spi.QueryFactory;
import st.orm.template.QueryBuilder;

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of {@link LazyFactory}.
 */
public final class LazyFactoryImpl implements LazyFactory {
    private final QueryFactory factory;
    private final ModelBuilder modelBuilder;
    private final Predicate<? super Provider> providerFilter;

    public LazyFactoryImpl(@Nonnull QueryFactory factory,
                           @Nonnull ModelBuilder modelBuilder,
                           @Nullable Predicate<? super Provider> providerFilter) {
        this.factory = requireNonNull(factory, "factory");
        this.modelBuilder = requireNonNull(modelBuilder, "modelBuilder");
        this.providerFilter = providerFilter;
    }

    /**
     * Creates a lazy instance for the specified record {@code type} and {@code pk}. This method can be used to generate
     * lazy instances for entities, projections and regular records.
     *
     * @param type record type.
     * @param pk primary key.
     * @return lazy instance.
     * @param <T> record type.
     * @param <ID> primary key type.
     */
    @Override
    public <T extends Record, ID> Lazy<T, ID> create(@Nonnull Class<T> type, @Nullable ID pk) {
        //noinspection unchecked
        QueryBuilder<T, T, ID> queryBuilder = (QueryBuilder<T, T, ID>) new ORMTemplateImpl(
                factory,
                modelBuilder,
                providerFilter).selectFrom(type);
        LazySupplier<T> supplier = new LazySupplier<>(() -> {
            if (pk == null) {
                return null;
            }
            return queryBuilder.where(pk).getSingleResult();
        });
        class LazyImpl extends AbstractLazy<T, ID>{
            private final ID pk;

            LazyImpl(@Nullable ID pk) {
                this.pk = pk;
            }

            @Override
            protected Class<T> type() {
                return type;
            }

            @Override
            protected boolean isFetched() {
                return supplier.value().isPresent();
            }

            @Override
            public ID id() {
                return pk;
            }

            @Override
            public T fetch() {
                if (pk == null) {
                    return null;
                }
                return supplier.get();
            }
        }
        return new LazyImpl(pk);
    };
}
