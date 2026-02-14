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
package st.orm.spi;

import static java.lang.System.arraycopy;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import kotlin.Metadata;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KCallable;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KMutableProperty1;
import kotlin.reflect.KParameter;
import kotlin.reflect.KProperty1;
import kotlin.reflect.KType;
import kotlin.reflect.KVisibility;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.ReflectJvmMapping;
import st.orm.Data;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.core.repository.impl.DefaultORMReflectionImpl;
import st.orm.core.spi.ORMReflection;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;

@SuppressWarnings("unchecked")
public class ORMReflectionImpl implements ORMReflection {
    private static final Map<Class<?>, Optional<RecordType>> TYPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<RecordField>> PK_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Constructor<?>>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    private static final DefaultORMReflectionImpl defaultReflection = new DefaultORMReflectionImpl();

    private static Class<? extends Data> toData(Class<?> table) {
        if (!Data.class.isAssignableFrom(table)) {
            throw new PersistenceException("Table %s is not a Data type.".formatted(table.getName()));
        }
        return (Class<? extends Data>) table;
    }

    private boolean isKotlinDataClass(Class<?> type) {
        Metadata metadata = type.getAnnotation(Metadata.class);
        if (metadata == null) {
            return false;
        }
        return JvmClassMappingKt.getKotlinClass(type).isData();
    }

    @Override
    public Object getId(@Nonnull Data data) {
        return PK_FIELD_CACHE.computeIfAbsent(data.getClass(), ignore ->
                        getRecordType(data.getClass()).fields().stream()
                                .filter(field -> field.isAnnotationPresent(PK.class))
                                .findFirst()
                )
                .map(field -> invoke(field, data))
                .orElseThrow(() -> new PersistenceException("No PK found for %s.".formatted(data.getClass().getName())));
    }

    @Override
    public Object getRecordValue(@Nonnull Object record, int index) {
        return invoke(getRecordType(record.getClass()).fields().get(index), record);
    }

    @Override
    public Optional<RecordType> findRecordType(@Nonnull Class<?> type) {
        return TYPE_CACHE.computeIfAbsent(type, ignore -> {
            if (isKotlinDataClass(type)) {
                KClass<?> kClass = JvmClassMappingKt.getKotlinClass(type);
                var constructor = findCanonicalConstructor(type).orElse(null);
                if (constructor == null) {
                    return empty();
                }
                @SuppressWarnings("unchecked")
                KFunction<?> primary = KClasses.getPrimaryConstructor((KClass<Object>) kClass);
                if (primary == null) {
                    return empty();
                }
                Map<String, KVisibility> visibilityByName =
                        KClasses.getMemberProperties(kClass).stream()
                                .collect(Collectors.toMap(
                                        KCallable::getName,
                                        p -> {
                                            KVisibility v = p.getVisibility();   // May be null.
                                            return v != null ? v : KVisibility.PRIVATE; // Or keep null via Optional.
                                        },
                                        (a, b) -> a
                                ));
                List<RecordField> fields = primary.getParameters().stream()
                        .filter(p -> p.getKind() == KParameter.Kind.VALUE)
                        .map(p -> {
                            String name = p.getName();
                            if (name == null) {
                                throw new PersistenceException("Unnamed constructor parameter in %s.".formatted(type.getName()));
                            }
                            KVisibility visibility = visibilityByName.get(name);
                            if (visibility == null) {
                                // Parameter exists, but there is no property with that name (no val/var, or something odd).
                                throw new PersistenceException(
                                        "Constructor parameter '%s' is not a Kotlin property in %s.".formatted(name, type.getName())
                                );
                            }
                            if (visibility != KVisibility.PUBLIC) {
                                throw new PersistenceException(
                                        "Property '%s' in %s must be public but is %s.".formatted(name, type.getName(), visibility)
                                );
                            }
                            return toRecordField(p, constructor);
                        })
                        .toList();
                return Optional.of(
                        new RecordType(
                                type,
                                constructor,
                                stream(type.getAnnotations())
                                        .filter(annotation -> annotation.annotationType() != Metadata.class)
                                        .toList(),
                                fields
                        )
                );
            }
            return defaultReflection.findRecordType(type);
        });
    }

    private KProperty1<?, ?> findVarProperty(@Nonnull KClass<?> kClass) {
        for (KProperty1<?, ?> prop : KClasses.getMemberProperties(kClass)) {
            if (prop instanceof KMutableProperty1<?, ?>) {
                return prop;
            }
        }
        return null;
    }

    private boolean isMutableProperty(Class<?> declaringClass, String name) {
        KClass<?> kClass = JvmClassMappingKt.getKotlinClass(declaringClass);
        for (KProperty1<?, ?> p : KClasses.getMemberProperties(kClass)) {
            if (name.equals(p.getName())) {
                return p instanceof KMutableProperty1; // var => true, val => false.
            }
        }
        return false;
    }

    private RecordField toRecordField(KParameter parameter, Constructor<?> constructor) {
        String name = parameter.getName();
        if (name == null) {
            // Fallback.
            name = "p" + parameter.getIndex();
        }
        KType kType = parameter.getType();
        Type genericType = ReflectJvmMapping.getJavaType(kType);
        Class<?> rawType = Object.class;
        if (genericType instanceof Class<?>) {
            rawType = (Class<?>) genericType;
        } else if (genericType instanceof ParameterizedType) {
            rawType = (Class<?>) ((ParameterizedType) genericType).getRawType();
        }
        Class<?> declaringClass = constructor.getDeclaringClass();
        Method accessor = findKotlinGetter(declaringClass, name);
        List<Annotation> annotations = mergeAnnotations(
                findConstructorParameterAnnotations(constructor, parameter),
                findKotlinPropertyAnnotations(declaringClass, name)
        );
        return new RecordField(
                declaringClass,
                name,
                rawType,                     // Raw type, like List.class.
                genericType,                 // Full generic type, like List<String>.
                parameter.getType().isMarkedNullable(),
                isMutableProperty(declaringClass, name),
                accessor,
                annotations
        );
    }

    private List<Annotation> findKotlinPropertyAnnotations(Class<?> type, String propertyName) {
        KClass<?> kClass = JvmClassMappingKt.getKotlinClass(type);
        for (KCallable<?> callable : kClass.getMembers()) {
            if (callable instanceof KProperty1<?, ?> property &&
                    property.getName().equals(propertyName)) {
                // This returns annotations declared on the Kotlin property,
                // including @property: and default property annotations.
                return property.getAnnotations();
            }
        }
        return List.of();
    }

    @SafeVarargs
    private List<Annotation> mergeAnnotations(List<Annotation>... sources) {
        int size = 0;
        for (List<Annotation> source : sources) {
            size += source.size();
        }
        if (size == 0) {
            return List.of();
        }
        List<Annotation> result = new ArrayList<>(size);
        for (List<Annotation> source : sources) {
            result.addAll(source);
        }
        return result;
    }

    private Method findKotlinGetter(Class<?> type, String propertyName) {
        KClass<?> kClass = JvmClassMappingKt.getKotlinClass(type);
        for (KCallable<?> callable : kClass.getMembers()) {
            if (callable instanceof KProperty1<?, ?> property) {
                if (property.getName().equals(propertyName)) {
                    return ReflectJvmMapping.getJavaGetter(property);
                }
            }
        }
        throw new IllegalArgumentException("No getter found for property %s in class %s.".formatted(propertyName, type.getSimpleName()));
    }

    private List<Annotation> findConstructorParameterAnnotations(Constructor<?> constructor, KParameter kotlinParam) {
        int index = kotlinParam.getIndex();
        // For constructors, Kotlin only has VALUE parameters, so index should align with Java parameter index. If that
        // assumption ever breaks, map by name instead.
        if (index < 0 || index >= constructor.getParameterCount()) {
            return List.of();
        }
        return asList(constructor.getParameters()[index].getAnnotations());
    }

    private Optional<Constructor<?>> findCanonicalConstructor(@Nonnull Class<?> type) {
        return CONSTRUCTOR_CACHE.computeIfAbsent(type, ignore -> {
            if (!isKotlinDataClass(type)) {
                return empty();
            }
            KClass<?> kClass = JvmClassMappingKt.getKotlinClass(type);
            if (!kClass.isData()) {
                return Optional.empty();
            }
            @SuppressWarnings("unchecked")
            KFunction<?> primary = KClasses.getPrimaryConstructor((KClass<Object>) kClass);
            if (primary == null) {
                return Optional.empty();
            }
            Constructor<?> javaCtor = ReflectJvmMapping.getJavaConstructor(primary);
            if (javaCtor != null) {
                return Optional.of(javaCtor);
            }
            // Fallback: match by parameter count if ReflectJvmMapping fails
            int paramCount = (int) primary.getParameters().stream()
                    .filter(p -> p.getKind() == KParameter.Kind.VALUE)
                    .count();
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (constructor.getParameterCount() == paramCount) {
                    return Optional.of(constructor);
                }
            }
            return Optional.empty();
        });
    }

    private static final Method GET_JAVA_CLASS_METHOD;
    static {
        try {
            GET_JAVA_CLASS_METHOD = JvmClassMappingKt.class.getMethod("getJavaClass", KClass.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Class<?> getType(@Nonnull Object clazz) {
        if (defaultReflection.isSupportedType(clazz)) {
            return defaultReflection.getType(clazz);
        }
        try {
            try {
                //noinspection JavaReflectionInvocation
                return (Class<?>) GET_JAVA_CLASS_METHOD.invoke(null, clazz);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        } catch (Throwable t) {
            throw new IllegalArgumentException(t);
        }
    }

    @Override
    public Class<? extends Data> getDataType(@Nonnull Object clazz) {
        Class<?> o = getType(clazz);
        return toData(o);
    }

    @Override
    public boolean isSupportedType(@Nonnull Object clazz) {
        return defaultReflection.isSupportedType(clazz) || clazz instanceof KClass;
    }

    @Override
    public boolean isDefaultValue(@Nullable Object o) {
        return defaultReflection.isDefaultValue(o);
    }

    @Override
    public <T> List<Class<? extends T>> getPermittedSubclasses(@Nonnull Class<T> sealedClass) {
        return JvmClassMappingKt.getKotlinClass(sealedClass).getSealedSubclasses().stream()
                .map(JvmClassMappingKt::getJavaClass)
                .collect(toList());
    }

    @Override
    public boolean isDefaultMethod(@Nonnull Method method) {
        if (defaultReflection.isDefaultMethod(method)) {
            return true;
        }
        Class<?> clazz = method.getDeclaringClass();
        return clazz.getDeclaredAnnotation(Metadata.class) != null;
    }

    @Override
    public Object invoke(@Nonnull RecordField field, @Nonnull Object record) {
        return defaultReflection.invoke(field, record);
    }

    record MethodCacheKey(@Nonnull List<Class<?>> interfaces, @Nonnull Method method) {}
    private static final ConcurrentMap<MethodCacheKey, MethodHandle> METHOD_HANDLE_CACHE = new ConcurrentHashMap<>();

    @Override
    public Object execute(@Nonnull Object proxy, @Nonnull Method method, @Nonnull Object... args) throws Throwable {
        if (defaultReflection.isDefaultMethod(method)) {
            return defaultReflection.execute(proxy, method, args);
        }
        var interfaces = List.of(proxy.getClass().getInterfaces());
        MethodHandle methodHandle = METHOD_HANDLE_CACHE.computeIfAbsent(new MethodCacheKey(interfaces, method), key -> {
            try {
                return findKotlinDefault(interfaces, method);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalArgumentException("Failed to find Kotlin DefaultImpls for %s".formatted(method));
            }
        });
        return methodHandle.bindTo(proxy).invokeWithArguments(args);
    }

    private MethodHandle findKotlinDefault(@Nonnull List<Class<?>> interfaces, @Nonnull Method method) throws Throwable {
        if (interfaces.size() != 1) {
            throw new IllegalArgumentException("Single interface expected for proxy.");
        }
        // Attempt exact match on the first interface.
        Class<?> iface = interfaces.getFirst();
        String implsName = iface.getName() + "$DefaultImpls";
        Class<?> impls = Class.forName(implsName, true, iface.getClassLoader());
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(impls, MethodHandles.lookup());
        // Try the exact signature.
        Class<?>[] compileParams = method.getParameterTypes();
        Class<?>[] exact = new Class<?>[compileParams.length + 1];
        exact[0] = iface;
        arraycopy(compileParams, 0, exact, 1, compileParams.length);
        try {
            return lookup.findStatic(impls,
                    method.getName(),
                    MethodType.methodType(method.getReturnType(), exact));
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            // Fall through to args-based scan.
        }
        return ofNullable(scanMethods(impls.getDeclaredMethods(), iface, method, lookup))
                .or(() -> ofNullable(scanMethods(impls.getMethods(), iface, method, lookup)))
                .orElseThrow(() -> new NoSuchMethodError("No Kotlin DefaultImpls for %s.".formatted(method)));
    }

    private MethodHandle scanMethods(@Nonnull Method[] candidates,
                                     @Nonnull Class<?> iface,
                                     @Nonnull Method method,
                                     @Nonnull MethodHandles.Lookup lookup) {
        Class<?>[] originalParams = method.getParameterTypes();
        int needed = originalParams.length + 1;
        for (Method m : candidates) {
            // Must be a static helper with the same name and arity.
            if (!Modifier.isStatic(m.getModifiers()) ||
                    !m.getName().equals(method.getName()) ||
                    m.getParameterCount() != needed) {
                continue;
            }
            Class<?>[] parameterTypes = m.getParameterTypes();
            if (!parameterTypes[0].isAssignableFrom(iface)) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < originalParams.length; i++) {
                Class<?> declared = originalParams[i];
                Class<?> candidateParameter = parameterTypes[i+1];
                if (!declared.isAssignableFrom(candidateParameter)) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            m.setAccessible(true);
            try {
                return lookup.unreflect(m);
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException("Failed to invoke %s.".formatted(m));
            }
        }
        return null;
    }
}
