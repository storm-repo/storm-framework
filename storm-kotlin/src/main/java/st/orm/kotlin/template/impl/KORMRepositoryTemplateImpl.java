package st.orm.kotlin.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.persistence.PersistenceException;
import kotlin.reflect.KClass;
import st.orm.BindVars;
import st.orm.kotlin.repository.KEntityRepository;
import st.orm.kotlin.repository.KRepository;
import st.orm.kotlin.spi.KEntityRepositoryImpl;
import st.orm.kotlin.template.KORMRepositoryTemplate;
import st.orm.repository.Entity;
import st.orm.repository.RepositoryLookup;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.ORMRepositoryTemplate;
import st.orm.template.Sql;
import st.orm.template.SqlTemplateException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.System.identityHashCode;
import static java.util.Optional.empty;
import static st.orm.template.SqlTemplate.aroundInvoke;

public final class KORMRepositoryTemplateImpl extends KORMTemplateImpl implements KORMRepositoryTemplate {
    private final static ORMReflection REFLECTION = Providers.getORMReflection();

    private final ORMRepositoryTemplate ORM;

    public KORMRepositoryTemplateImpl(ORMRepositoryTemplate orm) {
        super(orm);
        ORM = orm;
    }

    @Override
    public ORMRepositoryTemplate bridge() {
        return ORM;
    }

    @Override
    public <T extends Entity<ID>, ID> KEntityRepository<T, ID> repository(@Nonnull Class<T> type) {
        return new KEntityRepositoryImpl<>(ORM.repository(type));
    }

    @Override
    public <T extends Entity<ID>, ID> KEntityRepository<T, ID> repository(@Nonnull KClass<T> type) {
        //noinspection unchecked
        return repository((Class<T>) REFLECTION.getType(type));
    }

    @Override
    public <R extends KRepository> R repositoryProxy(@Nonnull Class<R> type) {
        KEntityRepository<?, ?> entityRepository = createEntityRepository(type)
                .orElse(null);
        KRepository repository = createRepository();
        //noinspection unchecked
        return wrapRepository((R) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
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
    public <R extends KRepository> R repositoryProxy(@Nonnull KClass<R> type) {
        //noinspection unchecked
        return repositoryProxy((Class<R>) REFLECTION.getType(type));
    }

    @SuppressWarnings("unchecked")
    private Optional<KEntityRepository<?, ?>> createEntityRepository(@Nonnull Class<?> type) {
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
            return Optional.of(repository((Class<Entity<Object>>) entityClass));
        }
        return empty();
    }

    private KRepository createRepository() {
        return new KRepository() {
            @Override
            public RepositoryLookup bridge() {
                return ORM;
            }

            @Override
            public KORMRepositoryTemplate template() {
                return KORMRepositoryTemplateImpl.this;
            }

            @Override
            public <T extends Entity<ID>, ID> KEntityRepository<T, ID> repository(@Nonnull KClass<T> type) {
                return KORMRepositoryTemplateImpl.this.repository(type);
            }

            @Override
            public <R extends KRepository> R repositoryProxy(@Nonnull KClass<R> type) {
                return KORMRepositoryTemplateImpl.this.repositoryProxy(type);
            }

            @Override
            public <T extends Entity<ID>, ID> KEntityRepository<T, ID> repository(@Nonnull Class<T> type) {
                return KORMRepositoryTemplateImpl.this.repository(type);
            }

            @Override
            public <R extends KRepository> R repositoryProxy(@Nonnull Class<R> type) {
                return KORMRepositoryTemplateImpl.this.repositoryProxy(type);
            }

            @Override
            public BindVars createBindVars() {
                return KORMRepositoryTemplateImpl.this.createBindVars();
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
