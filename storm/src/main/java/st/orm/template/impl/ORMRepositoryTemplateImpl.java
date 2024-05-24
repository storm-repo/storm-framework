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
import jakarta.persistence.PersistenceException;
import st.orm.BindVars;
import st.orm.repository.Entity;
import st.orm.repository.EntityRepository;
import st.orm.repository.Repository;
import st.orm.spi.EntityRepositoryProvider;
import st.orm.spi.ORMReflection;
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
                                     @Nullable Predicate<? super EntityRepositoryProvider> providerFilter) {
        super(factory, tableNameResolver, columnNameResolver, foreignKeyResolver, providerFilter);
    }

    @Override
    public <T extends Entity<ID>, ID> EntityRepository<T, ID> repository(@Nonnull Class<T> type) {
        return wrapRepository(Providers.getEntityRepository(this, model(type), providerFilter == null ? _ -> true : providerFilter));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R extends Repository> R repositoryProxy(@Nonnull Class<R> type) {
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

    private Optional<EntityRepository<?, ?>> createEntityRepository(@Nonnull Class<?> type) {
        //noinspection unchecked
        return findGenericClass(type, EntityRepository.class, 0)
                .map(entityClass -> repository((Class<Entity<Object>>) entityClass));
    }

    private Repository createRepository() {
        return new Repository() {
            @Override
            public ORMRepositoryTemplate template() {
                return ORMRepositoryTemplateImpl.this;
            }

            @Override
            public <T extends Entity<ID>, ID> EntityRepository<T, ID> repository(@Nonnull Class<T> type) {
                return ORMRepositoryTemplateImpl.this.repository(type);
            }

            @Override
            public <R extends Repository> R repositoryProxy(@Nonnull Class<R> type) {
                return ORMRepositoryTemplateImpl.this.repositoryProxy(type);
            }

            @Override
            public BindVars createBindVars() {
                return ORMRepositoryTemplateImpl.this.createBindVars();
            }
        };
    }

    private static Optional<Class<?>> findGenericClass(@Nonnull Class<?> clazz,
                                                       @Nonnull Class<?> targetInterface,
                                                       int typeArgumentIndex) {
        return findGenericType(clazz, targetInterface, typeArgumentIndex)
                .filter(type -> type instanceof Class<?>)
                .map(type -> (Class<?>) type);
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