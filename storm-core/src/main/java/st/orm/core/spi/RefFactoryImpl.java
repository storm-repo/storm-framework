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

    /**
     * The pk class most recently resolved as being its own row identity. Refs are created per row during
     * materialization with the same pk class throughout, so this resolves the row identity decision at type level
     * once and selects the ref implementation at construction, leaving one pointer comparison per ref. A single
     * field keeps the unsynchronized access safe: a stale read can only miss (falling back to the resolution or to
     * the general implementation), never claim own-row-identity for a class that requires normalization.
     */
    private Class<?> ownRowIdentityPkClass;

    public RefFactoryImpl(@Nonnull QueryFactory factory,
                          @Nonnull ModelBuilder modelBuilder,
                          @Nullable Predicate<? super Provider> providerFilter) {
        this(new ORMTemplateImpl(factory, modelBuilder, providerFilter));
    }

    public RefFactoryImpl(@Nonnull QueryTemplate template) {
        this.template = requireNonNull(template, "template");
    }

    /**
     * Returns the transaction context that is active for the template backing this factory, or {@code null} when no
     * transaction is active.
     *
     * @return the active transaction context, or {@code null}.
     * @since 1.13
     */
    @Override
    public @Nullable TransactionContext transactionContext() {
        return TransactionScope.peekContext(template.transactionTemplateProvider());
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
                var context = transactionContext();
                if (context != null) {
                    var cache = (EntityCache<?, ID>) context
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
        var supplier = new LazySupplier<>(record);
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
        return isOwnRowIdentity(pk)
                ? new DirectKeyRefImpl<>(supplier, type, pk)
                : new RefImpl<>(supplier, type, pk);
    }

    /**
     * Returns whether the pk class is its own row identity, selecting the ref implementation that carries no
     * identity or hash cache.
     */
    private boolean isOwnRowIdentity(@Nonnull Object pk) {
        Class<?> pkClass = pk.getClass();
        if (pkClass == ownRowIdentityPkClass) {
            return true;
        }
        if (!RowIdentity.requiresNormalization(pkClass)) {
            ownRowIdentityPkClass = pkClass;
            return true;
        }
        return false;
    }
}
