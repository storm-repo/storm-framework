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
import st.orm.PersistenceException;
import st.orm.repository.Entity;
import st.orm.repository.EntityRepository;
import st.orm.repository.Projection;
import st.orm.repository.ProjectionRepository;
import st.orm.repository.Repository;
import st.orm.spi.ORMReflection;
import st.orm.spi.Provider;
import st.orm.spi.Providers;
import st.orm.template.ColumnNameResolver;
import st.orm.template.ForeignKeyResolver;
import st.orm.template.ORMRepositoryTemplate;
import st.orm.template.Sql;
import st.orm.template.SqlTemplateException;
import st.orm.template.TableNameResolver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static java.lang.System.identityHashCode;
import static st.orm.template.SqlTemplate.aroundInvoke;

public final class ORMRepositoryTemplateImpl extends ORMTemplateImpl implements ORMRepositoryTemplate {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    public ORMRepositoryTemplateImpl(@Nonnull QueryFactory factory,
                                     @Nullable TableNameResolver tableNameResolver,
                                     @Nullable ColumnNameResolver columnNameResolver,
                                     @Nullable ForeignKeyResolver foreignKeyResolver,
                                     @Nullable Predicate<? super Provider> providerFilter) {
        super(factory, tableNameResolver, columnNameResolver, foreignKeyResolver, providerFilter);
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
        return wrapRepository(Providers.getEntityRepository(this, createModel(type, true), providerFilter == null ? _ -> true : providerFilter));
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
        return wrapRepository(Providers.getProjectionRepository(this, createModel(type, false), providerFilter == null ? _ -> true : providerFilter));
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
    public <R extends Repository> R proxy(@Nonnull Class<R> type) {
        EntityRepository<?, ?> entityRepository = createEntityRepository(type)
                .orElse(null);
        Repository repository = createRepository();
        return wrapRepository((R) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            try {
                if (method.getName().equals("hashCode") && method.getParameterCount() == 0) {
                    return identityHashCode(proxy);
                }
                if (method.getName().equals("equals") && method.getParameterCount() == 1) {
                    return proxy == args[0];
                }
                if (method.getName().equals("toString") && method.getParameterCount() == 0) {
                    return STR."RepositoryProxy(\{type.getSimpleName()})";
                }
                if (method.getDeclaringClass().isAssignableFrom(Repository.class)) {
                    // Handle Repository interface methods by delegating to the 'repository' instance.
                    return method.invoke(repository, args);
                }
                if (method.getDeclaringClass().isAssignableFrom(EntityRepository.class)) {
                    assert entityRepository != null;
                    // Handle EntityRepository interface methods by delegating to the 'entityRepository' instance.
                    return method.invoke(entityRepository, args);
                }
                if (REFLECTION.isDefaultMethod(method)) {
                    return REFLECTION.execute(proxy, method, args);
                }
                throw new UnsupportedOperationException(STR."Unsupported method: \{method.getName()} for \{type.getName()}.");
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }));
    }

    private <T extends Record & Entity<ID>, ID> Optional<EntityRepository<T, ID>> createEntityRepository(@Nonnull Class<?> type) {
        //noinspection unchecked
        return findGenericClass(type, EntityRepository.class, 0).map(cls -> entity((Class<T>) (Object) cls));
    }

    private Repository createRepository() {
        return new Repository() {
            @Override
            public ORMRepositoryTemplate template() {
                return ORMRepositoryTemplateImpl.this;
            }
        };
    }

    private static <T extends Record & Entity<?>> Optional<Class<T>> findGenericClass(@Nonnull Class<?> clazz,
                                                                                      @Nonnull Class<?> targetInterface,
                                                                                      int typeArgumentIndex) {
        //noinspection unchecked
        return findGenericType(clazz, targetInterface, typeArgumentIndex)
                .filter(type -> type instanceof Class<?>)
                .map(type -> (Class<T>) type);
    }

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

    private static Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        Set<Class<?>> interfacesFound = new HashSet<>();
        getAllInterfaces(clazz, interfacesFound);
        return interfacesFound;
    }

    private static void getAllInterfaces(Class<?> clazz, Set<Class<?>> interfacesFound) {
        while (clazz != null) {
            Class<?>[] interfaces = clazz.getInterfaces();
            for (Class<?> iFace : interfaces) {
                if (interfacesFound.add(iFace)) {
                    // Recursively find more interfaces if this interface extends others
                    getAllInterfaces(iFace, interfacesFound);
                }
            }
            // Move to the superclass and repeat the process
            clazz = clazz.getSuperclass();
        }
    }

    @SuppressWarnings("unchecked")
    private  <T extends Repository> T wrapRepository(@Nonnull T repository) {
        return (T) Proxy.newProxyInstance(repository.getClass().getClassLoader(), getAllInterfaces(repository.getClass()).toArray(new Class[0]), (_, method, args) -> {
            var lastSql = new AtomicReference<Sql>();
            try {
                return aroundInvoke(() -> {
                    try {
                        return method.invoke(repository, args);
                    } catch (Exception | Error e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new PersistenceException(e);
                    }
                }, lastSql::setPlain);
            } catch (InvocationTargetException e) {
                try {
                    throw e.getTargetException();
                } catch (SQLException | PersistenceException ex) {
                    Sql sql = lastSql.getPlain();
                    if (sql != null && ex.getSuppressed().length == 0) {
                        ex.addSuppressed(new SqlTemplateException(STR."""
                            Last SQL statement:
                            \{sql.statement()}"""));
                    }
                    throw ex;
                }
            }
        });
    }
}