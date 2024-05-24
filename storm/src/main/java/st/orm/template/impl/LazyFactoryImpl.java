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
import jakarta.annotation.Nullable;
import st.orm.Lazy;
import st.orm.repository.Entity;
import st.orm.repository.EntityRepository;
import st.orm.spi.EntityRepositoryProvider;
import st.orm.template.ColumnNameResolver;
import st.orm.template.ForeignKeyResolver;
import st.orm.template.SqlTemplateException;
import st.orm.template.TableNameResolver;

import java.lang.reflect.RecordComponent;
import java.util.Objects;
import java.util.function.Predicate;

import static st.orm.template.impl.SqlTemplateImpl.getLazyPkType;
import static st.orm.template.impl.SqlTemplateImpl.getLazyRecordType;

public final class LazyFactoryImpl implements LazyFactory {
    private final QueryFactory factory;
    private final TableNameResolver tableNameResolver;
    private final ColumnNameResolver columnNameResolver;
    private final ForeignKeyResolver foreignKeyResolver;
    private final Predicate<? super EntityRepositoryProvider> providerFilter;

    public LazyFactoryImpl(@Nonnull QueryFactory factory,
                           @Nullable TableNameResolver tableNameResolver,
                           @Nullable ColumnNameResolver columnNameResolver,
                           @Nullable ForeignKeyResolver foreignKeyResolver,
                           @Nullable Predicate<? super EntityRepositoryProvider> providerFilter) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.tableNameResolver = tableNameResolver;
        this.columnNameResolver = columnNameResolver;
        this.foreignKeyResolver = foreignKeyResolver;
        this.providerFilter = providerFilter;
    }

    @Override
    public Class<?> getPkType(@Nonnull RecordComponent component) throws SqlTemplateException {
        return getLazyPkType(component);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Lazy<?> create(@Nonnull RecordComponent component, @Nullable Object pk) throws SqlTemplateException {
        //noinspection RedundantCast
        Class<Entity<Object>> recordType = (Class<Entity<Object>>) (Object) getLazyRecordType(component);
        LazySupplier<Entity<Object>> supplier = new LazySupplier<>(() -> {
            if (pk == null) {
                return null;
            }
            EntityRepository<Entity<Object>, Object> repository = new ORMRepositoryTemplateImpl(
                    factory,
                    tableNameResolver,
                    columnNameResolver,
                    foreignKeyResolver,
                    providerFilter).repository(recordType);
            return repository.select(pk);
        });
        class LazyImpl implements Lazy<Entity<Object>>{
            private final Object pk;

            LazyImpl(@Nullable Object pk) {
                this.pk = pk;
            }

            @Override
            public boolean isNull() {
                return pk == null;
            }

            @Override
            public Object id() {
                return pk;
            }

            @Override
            public Entity<Object> fetch() {
                if (pk == null) {
                    return null;
                }
                return supplier.get();
            }

            @Override
            public int hashCode() {
                return Objects.hash(pk);
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof LazyImpl other) {
                    return Objects.equals(pk, other.pk);
                }
                return false;
            }

            @Override
            public String toString() {
                return STR."Lazy[pk=\{pk}, fetched=\{supplier.value().isPresent()}]";
            }
        }
        return new LazyImpl(pk);
    };
}
