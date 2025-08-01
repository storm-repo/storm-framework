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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.PersistenceException;
import st.orm.Entity;
import st.orm.Projection;
import st.orm.core.repository.EntityRepository;
import st.orm.core.repository.ProjectionRepository;
import st.orm.core.repository.Repository;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Provider;
import st.orm.core.spi.Providers;
import st.orm.core.spi.QueryFactory;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.SqlTemplateException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Predicate;

import static java.lang.System.identityHashCode;
import static java.lang.reflect.Proxy.newProxyInstance;
import static st.orm.core.spi.Providers.getEntityRepository;
import static st.orm.core.spi.Providers.getProjectionRepository;

public final class ORMTemplateImpl extends QueryTemplateImpl implements ORMTemplate {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private final Predicate<? super Provider> providerFilter;

    public ORMTemplateImpl(@Nonnull QueryFactory factory,
                           @Nonnull ModelBuilder modelBuilder,
                           @Nullable Predicate<? super Provider> providerFilter) {
        super(factory, modelBuilder);
        this.providerFilter = providerFilter;
    }

    /**
     * Returns the repository for the given entity type.
     *
     * @param type the entity type.
     * @param <T> the entity type.
     * @param <ID> the type of the entity's primary key.
     * @return the repository for the given entity type.
     */
    @Override
    public <T extends Record & Entity<ID>, ID> EntityRepository<T, ID> entity(@Nonnull Class<T> type) {
        try {
            return getEntityRepository(this, modelBuilder.build(type, true), providerFilter == null ? ignore -> true : providerFilter);
        } catch (SqlTemplateException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Returns the repository for the given projection type.
     *
     * @param type the projection type.
     * @param <T> the projection type.
     * @param <ID> the type of the projection's primary key, or Void if the projection specifies no primary key.
     * @return the repository for the given projection type.
     */
    @Override
    public <T extends Record & Projection<ID>, ID> ProjectionRepository<T, ID> projection(@Nonnull Class<T> type) {
        try {
            return getProjectionRepository(this, modelBuilder.build(type, false), providerFilter == null ? ignore -> true : providerFilter);
        } catch (SqlTemplateException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Returns a proxy for the repository of the given type.
     *
     * @param type the repository type.
     * @param <R> the repository type.
     * @return a proxy for the repository of the given type.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <R extends Repository> R repository(@Nonnull Class<R> type) {
        EntityRepository<?, ?> entityRepository = createEntityRepository(type).orElse(null);
        ProjectionRepository<?, ?> projectionRepository = createProjectionRepository(type).orElse(null);
        Repository repository = createRepository();
        return (R) newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            try {
                if (method.getName().equals("hashCode") && method.getParameterCount() == 0) {
                    return identityHashCode(proxy);
                }
                if (method.getName().equals("equals") && method.getParameterCount() == 1) {
                    return proxy == args[0];
                }
                if (method.getName().equals("toString") && method.getParameterCount() == 0) {
                    return "%s@proxy".formatted(type.getName());
                }
                if (REFLECTION.isDefaultMethod(method)) {
                    return REFLECTION.execute(proxy, method, args);
                }
                if (method.getDeclaringClass().isAssignableFrom(Repository.class)) {
                    // Handle Repository interface methods by delegating to the 'repository' instance.
                    return method.invoke(repository, args);
                }
                if (EntityRepository.class.isAssignableFrom(method.getDeclaringClass())) {   // Also support sub-interfaces of EntityRepository.
                    if (entityRepository == null) {
                        throw new UnsupportedOperationException("EntityRepository not available for %s.".formatted(type.getName()));
                    }
                    // Handle EntityRepository interface methods by delegating to the 'entityRepository' instance.
                    return method.invoke(entityRepository, args);
                }
                if (ProjectionRepository.class.isAssignableFrom(method.getDeclaringClass())) {   // Also support sub-interfaces of ProjectionRepository.
                    if (projectionRepository == null) {
                        throw new UnsupportedOperationException("ProjectionRepository not available for %s.".formatted(type.getName()));
                    }
                    // Handle ProjectionRepository interface methods by delegating to the 'projectionRepository' instance.
                    return method.invoke(projectionRepository, args);
                }
                throw new UnsupportedOperationException("Unsupported method: %s for %s.".formatted(method.getName(), type.getName()));
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        });
    }

    private <T extends Record & Entity<ID>, ID> Optional<EntityRepository<T, ID>> createEntityRepository(@Nonnull Class<?> type) {
        if (!EntityRepository.class.isAssignableFrom(type)) {
            return Optional.empty();
        }
        //noinspection unchecked
        return findGenericClass(type, EntityRepository.class, 0).map(cls -> entity((Class<T>) (Object) cls));
    }

    private <T extends Record & Projection<ID>, ID> Optional<ProjectionRepository<T, ID>> createProjectionRepository(@Nonnull Class<?> type) {
        if (!ProjectionRepository.class.isAssignableFrom(type)) {
            return Optional.empty();
        }
        //noinspection unchecked
        return findGenericClass(type, ProjectionRepository.class, 0).map(cls -> projection((Class<T>) (Object) cls));
    }

    private Repository createRepository() {
        //noinspection Convert2Lambda
        return new Repository() {
            @Override
            public ORMTemplate orm() {
                return ORMTemplateImpl.this;
            }
        };
    }

    @SuppressWarnings("SameParameterValue")
    private static <T extends Record & Entity<?>> Optional<Class<T>> findGenericClass(@Nonnull Class<?> clazz,
                                                                                      @Nonnull Class<?> targetInterface,
                                                                                      int typeArgumentIndex) {
        //noinspection unchecked
        return findGenericType(clazz, targetInterface, typeArgumentIndex)
                .filter(type -> type instanceof Class<?>)
                .map(type -> (Class<T>) type);
    }

    @SuppressWarnings("ConstantValue")
    public static Optional<Type> findGenericType(@Nonnull Class<?> clazz,
                                                 @Nonnull Class<?> targetInterface,
                                                 int typeArgumentIndex) {
        // Inspect interfaces directly implemented by the class.
        for (Type genericInterface : clazz.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType parameterizedType) {
                Type rawType = parameterizedType.getRawType();
                // Check if this is the interface we're interested in.
                if (rawType.equals(targetInterface) || rawType.equals(parameterizedType.getRawType())) {
                    Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                    if (typeArgumentIndex >= 0 && typeArgumentIndex < actualTypeArguments.length) {
                        return Optional.of(actualTypeArguments[typeArgumentIndex]); // Found the type
                    }
                }
            }
            // If the current interface itself extends other interfaces, recursively check them.
            if (genericInterface instanceof Class<?>) {
                Optional<Type> foundType = findGenericType((Class<?>) genericInterface, targetInterface, typeArgumentIndex);
                if (foundType.isPresent()) {
                    return foundType;
                }
            }
        }
        // If not found, check the superclass.
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && !superclass.equals(Object.class)) {
            return findGenericType(superclass, targetInterface, typeArgumentIndex);
        }
        return Optional.empty(); // Type not found.
    }
}