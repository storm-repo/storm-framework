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
package st.orm.kotlin.template.impl;

import jakarta.annotation.Nonnull;
import kotlin.reflect.KClass;
import st.orm.PersistenceException;
import st.orm.kotlin.repository.KEntityRepository;
import st.orm.kotlin.repository.KProjectionRepository;
import st.orm.kotlin.repository.KRepository;
import st.orm.kotlin.spi.KEntityRepositoryImpl;
import st.orm.kotlin.spi.KProjectionRepositoryImpl;
import st.orm.kotlin.template.KORMTemplate;
import st.orm.repository.Entity;
import st.orm.repository.Projection;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.ORMTemplate;
import st.orm.template.Sql;
import st.orm.template.SqlTemplateException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.System.identityHashCode;
import static java.lang.reflect.Proxy.newProxyInstance;
import static java.util.Optional.empty;
import static st.orm.template.SqlInterceptor.consume;

public final class KORMTemplateImpl extends KQueryTemplateImpl implements KORMTemplate {
    private final static ORMReflection REFLECTION = Providers.getORMReflection();

    private final ORMTemplate orm;

    public KORMTemplateImpl(ORMTemplate orm) {
        super(orm);
        this.orm = orm;
    }

    @Override
    public <T extends Record & Entity<ID>, ID> KEntityRepository<T, ID> entity(@Nonnull Class<T> type) {
        return new KEntityRepositoryImpl<>(orm.entity(type));
    }

    @Override
    public <T extends Record & Entity<ID>, ID> KEntityRepository<T, ID> entity(@Nonnull KClass<T> type) {
        //noinspection unchecked
        return entity((Class<T>) REFLECTION.getType(type));
    }

    @Override
    public <T extends Record & Projection<ID>, ID> KProjectionRepository<T, ID> projection(@Nonnull Class<T> type) {
        return new KProjectionRepositoryImpl<>(orm.projection(type));
    }

    @Override
    public <T extends Record & Projection<ID>, ID> KProjectionRepository<T, ID> projection(@Nonnull KClass<T> type) {
        //noinspection unchecked
        return projection((Class<T>) REFLECTION.getType(type));
    }

    @Override
    public <R extends KRepository> R repository(@Nonnull Class<R> type) {
        KEntityRepository<?, ?> entityRepository = createEntityRepository(type)
                .orElse(null);
        KRepository repository = createRepository();
        //noinspection unchecked
        return wrapRepository((R) newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            try {
                if (method.getName().equals("hashCode") && method.getParameterCount() == 0) {
                    return identityHashCode(proxy);
                }
                if (method.getName().equals("equals") && method.getParameterCount() == 1) {
                    return proxy == args[0];
                }
                if (method.getName().equals("toString") && method.getParameterCount() == 0) {
                    return STR."KRepositoryProxy(\{type.getSimpleName()})";
                }
                if (method.getDeclaringClass().isAssignableFrom(Object.class)) {
                    return method.invoke(proxy, args);
                }
                if (method.getDeclaringClass().isAssignableFrom(KRepository.class)) {
                    // Handle Repository interface methods by delegating to the 'repository' instance.
                    return method.invoke(repository, args);
                }
                if (method.getDeclaringClass().isAssignableFrom(KEntityRepository.class)) {
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

    @Override
    public <R extends KRepository> R repository(@Nonnull KClass<R> type) {
        //noinspection unchecked
        return repository((Class<R>) REFLECTION.getType(type));
    }

    @SuppressWarnings("unchecked")
    private <T extends Record & Entity<ID>, ID> Optional<KEntityRepository<T, ID>> createEntityRepository(@Nonnull Class<?> type) {
        if (KEntityRepository.class.isAssignableFrom(type)) {
            Class<?> entityClass = null;
            // Attempt to find the generic interface that directly extends Repository.
            Type[] genericInterfaces = type.getGenericInterfaces();
            for (Type genericInterface : genericInterfaces) {
                if (genericInterface instanceof ParameterizedType parameterizedType) {
                    Type rawType = parameterizedType.getRawType();
                    if (rawType instanceof Class && KEntityRepository.class.isAssignableFrom((Class<?>) rawType)) {
                        // The entity class must be the first parameterized type.
                        Type entityType = parameterizedType.getActualTypeArguments()[0];
                        if (entityType instanceof Class<?> clazz) {
                            entityClass = clazz;
                            break;
                        }
                    }
                }
            }
            if (entityClass == null) {
                throw new IllegalArgumentException(STR."Could not determine entity class for repository: \{type.getSimpleName()}.");
            }
            return Optional.of(entity((Class<T>) entityClass));
        }
        return empty();
    }

    private KRepository createRepository() {
        return new KRepository() {
            @Override
            public KORMTemplate orm() {
                return KORMTemplateImpl.this;
            }
        };
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
    private  <T extends KRepository> T wrapRepository(@Nonnull T repository) {
        return (T) newProxyInstance(repository.getClass().getClassLoader(), getAllInterfaces(repository.getClass()).toArray(new Class[0]), (_, method, args) -> {
            var lastSql = new AtomicReference<Sql>();
            try (var _ = consume(lastSql::setPlain)) {
                try {
                    return method.invoke(repository, args);
                } catch (Exception | Error e) {
                    throw e;
                } catch (Throwable e) {
                    throw new PersistenceException(e);
                }
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
