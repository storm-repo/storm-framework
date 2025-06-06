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
import st.orm.template.QueryTemplate;

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of {@link RefFactory}.
 *
 * @since 1.3
 */
public final class RefFactoryImpl implements RefFactory {
    private final QueryTemplate template;
    private final WeakInterner interner;

    public RefFactoryImpl(@Nonnull QueryFactory factory,
                          @Nonnull ModelBuilder modelBuilder,
                          @Nullable Predicate<? super Provider> providerFilter) {
        this(new ORMTemplateImpl(factory, modelBuilder, providerFilter));
    }

    public RefFactoryImpl(@Nonnull QueryTemplate template) {
        this.template = requireNonNull(template, "template");
        this.interner = new WeakInterner();
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
    @SuppressWarnings("unchecked")
    @Override
    public <T extends Record, ID> Ref<T> create(@Nonnull Class<T> type, @Nonnull ID pk) {
        var supplier = new LazySupplier<>(() ->
                ((QueryBuilder<T, T, ID>) template
                        .selectFrom(type))
                        .where(pk)
                        .getSingleResult());
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
    @SuppressWarnings("unchecked")
    @Override
    public <T extends Record, ID> Ref<T> create(@Nonnull T record, @Nonnull ID pk) {
        var type = (Class<T>) record.getClass();
        var supplier = new LazySupplier<>(() ->
                ((QueryBuilder<T, T, ID>) template
                        .selectFrom(type))
                        .where(pk)
                        .getSingleResult(), record);
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
        // Use the interner to reuse the same ref to reduce fetch calls for the same entity.
        return interner.intern(new RefImpl<>(supplier, type, pk));
    }
}
