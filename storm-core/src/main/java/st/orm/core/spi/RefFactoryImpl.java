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
package st.orm.core.spi;

import static java.util.Objects.requireNonNull;
import static st.orm.core.spi.Providers.getTransactionTemplate;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.function.Predicate;
import st.orm.Data;
import st.orm.Entity;
import st.orm.Ref;
import st.orm.core.template.QueryBuilder;
import st.orm.core.template.QueryTemplate;
import st.orm.core.template.impl.LazySupplier;
import st.orm.core.template.impl.ModelBuilder;
import st.orm.core.template.impl.ORMTemplateImpl;

/**
 * Implementation of {@link RefFactory}.
 *
 * @since 1.3
 */
public final class RefFactoryImpl implements RefFactory {
    private final QueryTemplate template;

    public RefFactoryImpl(@Nonnull QueryFactory factory,
                          @Nonnull ModelBuilder modelBuilder,
                          @Nullable Predicate<? super Provider> providerFilter) {
        this(new ORMTemplateImpl(factory, modelBuilder, providerFilter));
    }

    public RefFactoryImpl(@Nonnull QueryTemplate template) {
        this.template = requireNonNull(template, "template");
    }

    /**
     * Creates a ref instance for the specified record {@code type} and {@code pk}. This method can be used to generate
     * ref instances for entities, projections and regular records.
     *
     * <p>For entity types, this method first checks the entity cache (if available) before querying the database.</p>
     *
     * @param type record type.
     * @param pk primary key.
     * @return ref instance.
     * @param <T> record type.
     * @param <ID> primary key type.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends Data, ID> Ref<T> create(@Nonnull Class<T> type, @Nonnull ID pk) {
        var supplier = new LazySupplier<>(() -> {
            // Cache-first lookup for entities.
            if (Entity.class.isAssignableFrom(type)) {
                var context = getTransactionTemplate().currentContext();
                if (context.isPresent()) {
                    var cache = (EntityCache<?, ID>) context.get()
                        .findEntityCache((Class<? extends Entity<?>>) type);
                    if (cache != null) {
                        var cached = cache.get(pk);
                        if (cached.isPresent()) {
                            return (T) cached.get();
                        }
                    }
                }
            }
            return ((QueryBuilder<T, T, ID>) template
                    .selectFrom(type))
                    .where(pk)
                    .getSingleResult();
        });
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
    public <T extends Data, ID> Ref<T> create(@Nonnull T record, @Nonnull ID pk) {
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
    private <T extends Data, ID> Ref<T> create(@Nonnull LazySupplier<T> supplier, @Nonnull Class<T> type, @Nonnull ID pk) {
        return new RefImpl<>(supplier, type, pk);
    }
}
