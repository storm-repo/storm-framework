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

import static java.lang.Thread.currentThread;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.Optional.ofNullable;
import static java.util.ServiceLoader.load;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.sql.Connection;
import java.sql.SQLException;
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
import java.util.stream.Stream;
import javax.sql.DataSource;
import st.orm.Data;
import st.orm.Entity;
import st.orm.PersistenceException;
import st.orm.Projection;
import st.orm.Ref;
import st.orm.StormConfig;
import st.orm.core.repository.EntityRepository;
import st.orm.core.repository.ProjectionRepository;
import st.orm.core.template.Model;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.QueryBuilder;
import st.orm.core.template.QueryTemplate;
import st.orm.core.template.SqlDialect;
import st.orm.core.template.TemplateString;
import st.orm.mapping.RecordField;

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
    private static final Supplier<List<ConnectionProvider>> CONNECTION_PROVIDERS = createProviders(ConnectionProvider.class);
    private static final Supplier<List<TransactionTemplateProvider>> TRANSACTION_TEMPLATE_PROVIDERS = createProviders(TransactionTemplateProvider.class);

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
    private static <S extends Provider> Supplier<List<S>> createProviders(Class<S> providerClass) {
        return () -> {
            ClassLoader contextClassLoader = currentThread().getContextClassLoader();
            ClassLoader providersClassloader = Providers.class.getClassLoader();
            Object key = asList(providerClass, ofNullable(contextClassLoader).orElse(providersClassloader));
            // Prefetch all providers to prevent race conditions in case of parallel execution.
            return (List<S>) PROVIDER_CACHE.computeIfAbsent(key, ignore -> {
                    if (contextClassLoader != null) {
                        // Try the context class loader first.
                        List<S> list = toUnmodifiableList(load(providerClass, contextClassLoader));
                        if (!list.isEmpty()) {
                            return list;
                        }
                    }
                    // Revert to the providers' class loader.
                    return toUnmodifiableList(load(providerClass, providersClassloader));
                });
        };
    }

    /**
     * Returns a list of all services that are loaded by the specified {@code loader}.
     *
     * <p>Note that {@link Provider#isEnabled()} is deliberately not evaluated here: the returned list is cached for
     * the lifetime of the class loader, whereas enablement may depend on runtime state. Enablement is re-evaluated
     * at each resolution via {@link #enabled}.</p>
     *
     * @param loader loader of services.
     * @param <S> service type.
     * @return a list of all services loaded by the specified {@code loader}.
     */
    private static <S extends Provider> List<S> toUnmodifiableList(@Nonnull ServiceLoader<S> loader) {
        return stream(loader.spliterator(), false)
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }

    /**
     * Returns a stream of the currently enabled providers from the given cached provider list.
     *
     * @param providers the cached provider list supplier.
     * @param <S> provider type.
     * @return a stream of enabled providers.
     */
    private static <S extends Provider> Stream<S> enabled(@Nonnull Supplier<List<S>> providers) {
        return providers.get().stream().filter(Provider::isEnabled);
    }

    private static final AtomicReference<ORMReflection> ORM_REFLECTION = new AtomicReference<>();

    /**
     * Represents a key for a record field.
     */
    record FieldKey(Class<?> declaringType, String name) {
        FieldKey(RecordField field) {
            this(field.declaringType(), field.name());
        }
    }
    private static final Map<FieldKey, Optional<ORMConverter>> ORM_CONVERTERS = new ConcurrentHashMap<>();

    public static ORMReflection getORMReflection() {
        return ORM_REFLECTION.updateAndGet(value -> requireNonNullElseGet(value, () -> Orderable.sort(enabled(ORM_REFLECTION_PROVIDERS))
                .map(ORMReflectionProvider::getReflection)
                .findFirst()
                .orElseThrow()));
    }

    public static Optional<ORMConverter> getORMConverter(@Nonnull RecordField field) {
        return ORM_CONVERTERS.computeIfAbsent(new FieldKey(field), ignore ->
                Orderable.sort(enabled(ORM_CONVERTER_PROVIDERS))
                        .map(p -> p.getConverter(field))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findFirst());
    }

    public static <ID, E extends Entity<ID>> EntityRepository<E, ID> getEntityRepository(
            @Nonnull ORMTemplate ormTemplate,
            @Nonnull Model<E, ID> model,
            @Nonnull Predicate<? super EntityRepositoryProvider> filter) {
        return Orderable.sort(enabled(ENTITY_REPOSITORY_PROVIDERS))
                .filter(filter)
                .map(provider -> provider.getEntityRepository(ormTemplate, model))
                .findFirst()
                .orElseThrow();
    }

    public static <ID, P extends Projection<ID>> ProjectionRepository<P, ID> getProjectionRepository(
            @Nonnull ORMTemplate ormTemplate,
            @Nonnull Model<P, ID> model,
            @Nonnull Predicate<? super ProjectionRepositoryProvider> filter) {
        return Orderable.sort(enabled(PROJECTION_REPOSITORY_PROVIDERS))
                .filter(filter)
                .map(provider -> provider.getProjectionRepository(ormTemplate, model))
                .findFirst()
                .orElseThrow();
    }

    private static final AtomicReference<QueryBuilderProvider> QUERY_BUILDER_PROVIDER = new AtomicReference<>();

    /**
     * Resolves the query builder provider once and reuses it, mirroring {@link #getORMReflection()}: query builders
     * are created on every select and the provider order is fixed after startup.
     */
    private static QueryBuilderProvider queryBuilderProvider() {
        return QUERY_BUILDER_PROVIDER.updateAndGet(value -> requireNonNullElseGet(value, () ->
                Orderable.sort(enabled(QUERY_BUILDER_REPOSITORY_PROVIDERS))
                        .findFirst()
                        .orElseThrow()));
    }

    public static <T extends Data, R, ID> QueryBuilder<T, R, ID> selectFrom(
            @Nonnull QueryTemplate queryTemplate,
            @Nonnull Class<T> fromType,
            @Nonnull Class<R> selectType,
            @Nonnull TemplateString template,
            boolean subquery,
            @Nonnull Supplier<Model<T, ID>> modelSupplier) {
        return queryBuilderProvider().selectFrom(queryTemplate, fromType, selectType, template, subquery, modelSupplier);
    }

    public static <T extends Data, R extends Data, ID> QueryBuilder<T, Ref<R>, ID> selectRefFrom(
            @Nonnull QueryTemplate queryTemplate,
            @Nonnull Class<T> fromType,
            @Nonnull Class<R> refType,
            @Nonnull Class<?> pkType,
            @Nonnull Supplier<Model<T, ID>> modelSupplier) {
        return queryBuilderProvider().selectRefFrom(queryTemplate, fromType, refType, pkType, modelSupplier);
    }

    public static <T extends Data, ID> QueryBuilder<T, ?, ID> deleteFrom(
            @Nonnull QueryTemplate queryTemplate,
            @Nonnull Class<T> fromType,
            @Nonnull Supplier<Model<T, ID>> modelSupplier) {
        return queryBuilderProvider().deleteFrom(queryTemplate, fromType, modelSupplier);
    }

    public static SqlDialect getSqlDialect() {
        return getSqlDialect(StormConfig.defaults());
    }

    public static SqlDialect getSqlDialect(@Nonnull StormConfig config) {
        return Orderable.sort(enabled(SQL_DIALECT_PROVIDERS))
                .map(p -> p.getSqlDialect(config))
                .findFirst()
                .orElseThrow();
    }

    public static SqlDialect getSqlDialect(@Nonnull Predicate<? super SqlDialectProvider> filter) {
        return getSqlDialect(filter, StormConfig.defaults());
    }

    public static SqlDialect getSqlDialect(@Nonnull Predicate<? super SqlDialectProvider> filter,
                                            @Nonnull StormConfig config) {
        return Orderable.sort(enabled(SQL_DIALECT_PROVIDERS))
                .filter(filter)
                .map(p -> p.getSqlDialect(config))
                .findFirst()
                .orElseThrow();
    }

    private static final ConcurrentMap<DataSource, String> DATABASE_PRODUCT_NAMES = new ConcurrentHashMap<>();

    /**
     * Returns the database product name for the given data source, caching the result per data source identity.
     *
     * @param dataSource the data source to inspect.
     * @return the database product name.
     * @since 1.11
     */
    public static String getDatabaseProductName(@Nonnull DataSource dataSource) {
        return DATABASE_PRODUCT_NAMES.computeIfAbsent(dataSource, ds -> {
            try (Connection connection = ds.getConnection()) {
                return connection.getMetaData().getDatabaseProductName();
            } catch (SQLException e) {
                throw new PersistenceException("Failed to determine database product name.", e);
            }
        });
    }

    /**
     * Returns the database product name for the given connection.
     *
     * @param connection the connection to inspect.
     * @return the database product name.
     * @since 1.11
     */
    public static String getDatabaseProductName(@Nonnull Connection connection) {
        try {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to determine database product name.", e);
        }
    }

    /**
     * Returns the first dialect provider that supports the given database product name, or {@code null} if no
     * specific provider matches (the default provider will be used as a fallback).
     *
     * @param databaseProductName the database product name.
     * @return the matching dialect provider, or {@code null}.
     * @since 1.11
     */
    public static @Nullable SqlDialectProvider getSqlDialectProvider(@Nonnull String databaseProductName) {
        return Orderable.sort(enabled(SQL_DIALECT_PROVIDERS))
                .filter(p -> p.supports(databaseProductName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns the SQL dialect for the given data source by inspecting the database product name and selecting the
     * appropriate dialect provider.
     *
     * @param dataSource the data source to inspect.
     * @param config the Storm configuration to apply.
     * @return the SQL dialect.
     * @since 1.11
     */
    public static SqlDialect getSqlDialect(@Nonnull DataSource dataSource, @Nonnull StormConfig config) {
        String productName = getDatabaseProductName(dataSource);
        return Orderable.sort(enabled(SQL_DIALECT_PROVIDERS))
                .filter(p -> p.supports(productName))
                .map(p -> p.getSqlDialect(config))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Returns the SQL dialect for the given connection by inspecting the database product name and selecting the
     * appropriate dialect provider.
     *
     * @param connection the connection to inspect.
     * @param config the Storm configuration to apply.
     * @return the SQL dialect.
     * @since 1.11
     */
    public static SqlDialect getSqlDialect(@Nonnull Connection connection, @Nonnull StormConfig config) {
        String productName = getDatabaseProductName(connection);
        return Orderable.sort(enabled(SQL_DIALECT_PROVIDERS))
                .filter(p -> p.supports(productName))
                .map(p -> p.getSqlDialect(config))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Resolves the fallback connection provider via {@code ServiceLoader} discovery.
     *
     * <p>This is the provider used by templates that have not been configured with an explicit
     * {@link ConnectionProvider} via the template builder. Enablement is re-evaluated on every resolution, and an
     * ambiguous resolution fails fast.</p>
     *
     * @return the fallback connection provider.
     * @throws PersistenceException if no provider is found or the resolution is ambiguous.
     * @since 1.13
     */
    public static ConnectionProvider getConnectionProvider() {
        return selectUnique(CONNECTION_PROVIDERS, "connection provider",
                "ORMTemplate.builder(dataSource).connectionProvider(...)");
    }

    /**
     * Resolves the fallback transaction template provider via {@code ServiceLoader} discovery.
     *
     * <p>This is the provider used by templates that have not been configured with an explicit
     * {@link TransactionTemplateProvider} via the template builder. Enablement is re-evaluated on every resolution,
     * and an ambiguous resolution fails fast.</p>
     *
     * @return the fallback transaction template provider.
     * @throws PersistenceException if no provider is found or the resolution is ambiguous.
     * @since 1.13
     */
    public static TransactionTemplateProvider getTransactionTemplateProvider() {
        return selectUnique(TRANSACTION_TEMPLATE_PROVIDERS, "transaction template provider",
                "ORMTemplate.builder(dataSource).transactionTemplateProvider(...)");
    }

    /**
     * Selects the single winning provider from the given cached provider list.
     *
     * <p>Fails fast when the two top-ranked candidates are unordered peers: silently picking one of several equally
     * eligible providers would make behavior dependent on classpath order.</p>
     */
    private static <S extends Provider> S selectUnique(@Nonnull Supplier<List<S>> providers,
                                                       @Nonnull String description,
                                                       @Nonnull String remedy) {
        var sorted = Orderable.sort(enabled(providers)).toList();
        if (sorted.isEmpty()) {
            throw new PersistenceException("No %s found on the classpath.".formatted(description));
        }
        if (sorted.size() > 1 && !isOrderedBefore(sorted.get(0).getClass(), sorted.get(1).getClass())) {
            throw new PersistenceException(("Multiple candidates found for %s without a defined order: %s. " +
                    "Configure the desired implementation explicitly via %s.")
                    .formatted(description,
                            sorted.stream().map(provider -> provider.getClass().getName()).collect(joining(", ")),
                            remedy));
        }
        return sorted.getFirst();
    }

    /**
     * Returns whether {@code first} is explicitly ordered before {@code second} by the {@link Orderable} annotations.
     */
    private static boolean isOrderedBefore(@Nonnull Class<?> first, @Nonnull Class<?> second) {
        boolean firstBeforeAny = first.isAnnotationPresent(Orderable.BeforeAny.class);
        boolean secondBeforeAny = second.isAnnotationPresent(Orderable.BeforeAny.class);
        if (firstBeforeAny && !secondBeforeAny) {
            return true;
        }
        boolean firstAfterAny = first.isAnnotationPresent(Orderable.AfterAny.class);
        boolean secondAfterAny = second.isAnnotationPresent(Orderable.AfterAny.class);
        if (secondAfterAny && !firstAfterAny) {
            return true;
        }
        var before = first.getAnnotation(Orderable.Before.class);
        if (before != null && asList(before.value()).contains(second)) {
            return true;
        }
        var after = second.getAnnotation(Orderable.After.class);
        if (after != null && asList(after.value()).contains(first)) {
            return true;
        }
        return false;
    }
}
