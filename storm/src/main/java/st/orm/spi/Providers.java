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
package st.orm.spi;

import jakarta.annotation.Nonnull;
import st.orm.Ref;
import st.orm.repository.Entity;
import st.orm.repository.EntityRepository;
import st.orm.template.Model;
import st.orm.repository.Projection;
import st.orm.repository.ProjectionRepository;
import st.orm.template.ORMTemplate;
import st.orm.template.QueryBuilder;
import st.orm.template.QueryTemplate;
import st.orm.template.SqlDialect;
import st.orm.template.impl.LazySupplier;

import java.lang.reflect.RecordComponent;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.lang.Thread.currentThread;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.Optional.ofNullable;
import static java.util.ServiceLoader.load;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * Helper class for loading providers from the storm framework.
 */
public final class Providers {

    private static final Supplier<List<ORMReflectionProvider>> ORM_REFLECTION_PROVIDERS = createProviders(ORMReflectionProvider.class);
    private static final Supplier<List<ORMConverterProvider>> ORM_CONVERTER_PROVIDERS = createProviders(ORMConverterProvider.class);
    private static final Supplier<List<EntityRepositoryProvider>> ENTITY_REPOSITORY_PROVIDERS = createProviders(EntityRepositoryProvider.class);
    private static final Supplier<List<ProjectionRepositoryProvider>> PROJECTION_REPOSITORY_PROVIDERS = createProviders(ProjectionRepositoryProvider.class);
    private static final Supplier<List<QueryBuilderProvider>> QUERY_BUILDER_REPOSITORY_PROVIDERS = createProviders(QueryBuilderProvider.class);
    private static final Supplier<List<SqlDialectProvider>> SQL_DIALECT_PROVIDERS = createProviders(SqlDialectProvider.class);

    private static final ConcurrentMap<Object, List<?>> PROVIDER_CACHE = new ConcurrentHashMap<>();

    /**
     * Returns a supplier that caches the provider instances responsible for providing the actual service
     * implementation classes.
     *
     * @param providerClass provider class to request supplier for.
     * @param <S> type of the requested service.
     * @return a supplier that returns the provider instances responsible for providing the actual service instances.
     */
    @SuppressWarnings("unchecked")
    private static <S> Supplier<List<S>> createProviders(Class<S> providerClass) {
        return () -> {
            ClassLoader contextClassLoader = currentThread().getContextClassLoader();
            ClassLoader providersClassloader = Providers.class.getClassLoader();
            Object key = asList(providerClass, ofNullable(contextClassLoader).orElse(providersClassloader));
            // Prefetch all providers to prevent race conditions in case of parallel execution.
            return (List<S>) PROVIDER_CACHE.computeIfAbsent(key, _ -> {
                    if (contextClassLoader != null) {
                        // Try context class loader first.
                        List<S> list = toUnmodifiableList(load(providerClass, contextClassLoader));
                        if (!list.isEmpty()) {
                            return list;
                        }
                    }
                    // Revert to providers class loader.
                    return toUnmodifiableList(load(providerClass, providersClassloader));
                });
        };
    }

    /**
     * Returns a list of all services that are loaded by the specified {@code loader}.
     *
     * @param loader loader of services.
     * @param <S> service type.
     * @return a list of all services loaded by the specified {@code loader}.
     */
    private static <S> List<S> toUnmodifiableList(@Nonnull ServiceLoader<S> loader) {
        return stream(loader.spliterator(), false)
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }

    private static final AtomicReference<ORMReflection> ORM_REFLECTION = new AtomicReference<>();

    /**
     * Represents a key for a component, as RecordComponents don't have a proper equals/hashCode implementation.
     */
    record ComponentKey(Class<?> record, String name) {}
    private static final Map<ComponentKey, Optional<ORMConverter>> ORM_CONVERTERS = new ConcurrentHashMap<>();

    public static ORMReflection getORMReflection() {
        return ORM_REFLECTION.updateAndGet(value -> requireNonNullElseGet(value, () -> Orderable.sort(ORM_REFLECTION_PROVIDERS.get().stream())
                .map(ORMReflectionProvider::getReflection)
                .findFirst()
                .orElseThrow()));
    }

    public static Optional<ORMConverter> getORMConverter(@Nonnull RecordComponent component) {
        return ORM_CONVERTERS.computeIfAbsent(new ComponentKey(component.getDeclaringRecord(), component.getName()), _ ->
                Orderable.sort(ORM_CONVERTER_PROVIDERS.get().stream())
                        .filter(p -> p.isSupported(component))
                        .map(p -> p.getConverter(component))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findFirst());
    }

    public static <ID, E extends Record & Entity<ID>> EntityRepository<E, ID> getEntityRepository(
            @Nonnull ORMTemplate ormTemplate,
            @Nonnull Model<E, ID> model,
            @Nonnull Predicate<? super EntityRepositoryProvider> filter) {
        return Orderable.sort(ENTITY_REPOSITORY_PROVIDERS.get().stream())
                .filter(filter)
                .map(provider -> provider.getEntityRepository(ormTemplate, model))
                .findFirst()
                .orElseThrow();
    }

    public static <ID, P extends Record & Projection<ID>> ProjectionRepository<P, ID> getProjectionRepository(
            @Nonnull ORMTemplate ormTemplate,
            @Nonnull Model<P, ID> model,
            @Nonnull Predicate<? super ProjectionRepositoryProvider> filter) {
        return Orderable.sort(PROJECTION_REPOSITORY_PROVIDERS.get().stream())
                .filter(filter)
                .map(provider -> provider.getProjectionRepository(ormTemplate, model))
                .findFirst()
                .orElseThrow();
    }

    public static <T extends Record, R, ID> QueryBuilder<T, R, ID> selectFrom(
            @Nonnull QueryTemplate queryTemplate,
            @Nonnull Class<T> fromType,
            @Nonnull Class<R> selectType,
            @Nonnull StringTemplate template,
            boolean subquery,
            @Nonnull Supplier<Model<T, ID>> modelSupplier) {
        return Orderable.sort(QUERY_BUILDER_REPOSITORY_PROVIDERS.get().stream())
                .map(provider -> provider.selectFrom(queryTemplate, fromType, selectType, template, subquery, modelSupplier))
                .findFirst()
                .orElseThrow();
    }

    public static <T extends Record, R extends Record, ID> QueryBuilder<T, Ref<R>, ID> selectRefFrom(
            @Nonnull QueryTemplate queryTemplate,
            @Nonnull Class<T> fromType,
            @Nonnull Class<R> refType,
            @Nonnull Class<?> pkType,
            @Nonnull Supplier<Model<T, ID>> modelSupplier) {
        return Orderable.sort(QUERY_BUILDER_REPOSITORY_PROVIDERS.get().stream())
                .map(provider -> provider.selectRefFrom(queryTemplate, fromType, refType, pkType, modelSupplier))
                .findFirst()
                .orElseThrow();
    }

    public static <T extends Record, ID> QueryBuilder<T, ?, ID> deleteFrom(
            @Nonnull QueryTemplate queryTemplate,
            @Nonnull Class<T> fromType,
            @Nonnull Supplier<Model<T, ID>> modelSupplier) {
        return Orderable.sort(QUERY_BUILDER_REPOSITORY_PROVIDERS.get().stream())
                .map(provider -> provider.deleteFrom(queryTemplate, fromType, modelSupplier))
                .findFirst()
                .orElseThrow();
    }

    private static final Supplier<SqlDialect> SQL_DIALECT = new LazySupplier<>(
            () -> Orderable.sort(SQL_DIALECT_PROVIDERS.get().stream())
                    .map(SqlDialectProvider::getSqlDialect)
                    .findFirst()
                    .orElseThrow());

    public static SqlDialect getSqlDialect() {
        return SQL_DIALECT.get();
    }

    public static SqlDialect getSqlDialect(@Nonnull Predicate<? super SqlDialectProvider> filter) {
        return Orderable.sort(SQL_DIALECT_PROVIDERS.get().stream())
                .filter(filter)
                .map(SqlDialectProvider::getSqlDialect)
                .findFirst()
                .orElseThrow();
    }
}
