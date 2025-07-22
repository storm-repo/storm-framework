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
package st.orm.kt.spi;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import kotlin.Metadata;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KCallable;
import kotlin.reflect.KClass;
import kotlin.reflect.KType;
import st.orm.PK;
import st.orm.core.spi.DefaultORMReflectionImpl;
import st.orm.core.spi.ORMReflection;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import static java.lang.System.arraycopy;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

public class ORMReflectionImpl implements ORMReflection {
    private final static Map<ComponentCacheKey, Parameter> COMPONENT_PARAMETER_CACHE = new ConcurrentHashMap<>();
    private final static Map<ComponentCacheKey, Boolean> COMPONENT_NONNULL_CACHE = new ConcurrentHashMap<>();

    private static final DefaultORMReflectionImpl defaultReflection = new DefaultORMReflectionImpl();

    @Override
    public Optional<Constructor<?>> findCanonicalConstructor(@Nonnull Class<? extends Record> type) {
        assert type.isRecord();
        return defaultReflection.findCanonicalConstructor(type);
    }

    @Override
    public Optional<Class<?>> findPKType(@Nonnull Class<? extends Record> recordType) {
        assert recordType.isRecord();
        Constructor<?> constructor = findCanonicalConstructor(recordType)
                .orElseThrow(() -> new IllegalArgumentException("No canonical constructor found for record type: %s.".formatted(recordType.getSimpleName())));
        Class<?> pkType = null;
        for (Parameter parameter : constructor.getParameters()) {
            if (parameter.isAnnotationPresent(PK.class)) {
                if (pkType != null) {
                    // Found multiple components with @PK annotation, throwing an exception.
                    throw new IllegalArgumentException("Multiple components are annotated with @PK.");
                }
                pkType = parameter.getType();
            }
        }
        // May be null if no @PK annotation found.
        return ofNullable(pkType);
    }

    @Override
    public boolean isAnnotationPresent(@Nonnull RecordComponent component, @Nonnull Class<? extends Annotation> annotationType) {
        return getAnnotation(component, annotationType) != null;
    }

    @Override
    public boolean isAnnotationPresent(@Nonnull Class<?> type, @Nonnull Class<? extends Annotation> annotationType) {
        return getAnnotation(type, annotationType) != null;
    }

    record ComponentCacheKey(@Nonnull Class<? extends Record> recordType, @Nonnull String componentName) {
        ComponentCacheKey(RecordComponent component) throws IllegalArgumentException {
            //noinspection unchecked
            this((Class<? extends Record>) component.getDeclaringRecord(), component.getName());
        }

        private Constructor<?> constructor() {
            return defaultReflection.findCanonicalConstructor(recordType)
                    .orElseThrow(() -> new IllegalArgumentException("No canonical constructor found for record type: %s.".formatted(recordType.getSimpleName())));
        }
    }

    private Parameter getParameter(@Nonnull RecordComponent component) {
        return COMPONENT_PARAMETER_CACHE.computeIfAbsent(new ComponentCacheKey(component), k -> {
            //noinspection unchecked
            Class<? extends Record> recordType = (Class<? extends Record>) component.getDeclaringRecord();
            var recordComponents = recordType.getRecordComponents();
            assert k.constructor().getParameters().length == recordComponents.length;
            int index = 0;
            for (var candidate : recordComponents) {
                if (candidate.getName().equals(k.componentName())) {
                    return k.constructor().getParameters()[index];
                }
                index++;
            }
            throw new IllegalArgumentException("No parameter found for component: %s for record type: %s.".formatted(component.getName(), component.getDeclaringRecord().getSimpleName()));
        });
    }

    @Override
    public <A extends Annotation> A getAnnotation(@Nonnull RecordComponent component, @Nonnull Class<A> annotationType) {
        return getParameter(component).getAnnotation(annotationType);
    }

    @Override
    public <A extends Annotation> A[] getAnnotations(@Nonnull RecordComponent component, @Nonnull Class<A> annotationType) {
        return getParameter(component).getAnnotationsByType(annotationType);
    }

    @Override
    public <A extends Annotation> A getAnnotation(@Nonnull Class<?> type, @Nonnull Class<A> annotationType) {
        return defaultReflection.getAnnotation(type, annotationType);
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
    public Class<? extends Record> getRecordType(@Nonnull Object clazz) {
        //noinspection unchecked
        return (Class<? extends Record>) getType(clazz);
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
    public Object invokeComponent(@Nonnull RecordComponent component, @Nonnull Object record) throws Throwable {
        Method method = component.getAccessor();
        //noinspection ConstantValue
        if (method != null) {
            try {
                method.setAccessible(true);
                return method.invoke(record);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
        // Fallback to field access in case of private vals.
        Field field = component.getDeclaringRecord().getDeclaredField(component.getName());
        field.setAccessible(true);
        return field.get(record);
    }

    @SuppressWarnings("unchecked")
    static final Class<? extends Annotation> JAVAX_NULLABLE = ((Supplier<Class<? extends Annotation>>) () -> {
        try {
            return (Class<? extends Annotation>) Class.forName("javax.annotation.Nullable");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }).get();
    @SuppressWarnings("unchecked")
    static final Class<? extends Annotation> JAKARTA_NULLABLE = ((Supplier<Class<? extends Annotation>>) () -> {
        try {
            return (Class<? extends Annotation>) Class.forName("jakarta.annotation.Nullable");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }).get();

    private boolean isNullable(@Nonnull RecordComponent component) {
        return !isAnnotationPresent(component, PK.class)
                && !component.getType().isPrimitive()
                && ((JAVAX_NULLABLE != null && isAnnotationPresent(component, JAVAX_NULLABLE))
                || (JAKARTA_NULLABLE != null && isAnnotationPresent(component, JAKARTA_NULLABLE)));
    }

    @Override
    public boolean isNonnull(@Nonnull RecordComponent component) {
        return COMPONENT_NONNULL_CACHE.computeIfAbsent(new ComponentCacheKey(component), k -> {
            Class<?> declaringClass = component.getDeclaringRecord();
            boolean isKotlinClass = declaringClass.isAnnotationPresent(Metadata.class);
            if (!isKotlinClass) {
                return defaultReflection.isNonnull(component);
            }
            // In Kotlin, the default is nonnull, so we need to check if the component is nullable.
            if (isNullable(component)) {
                return false;
            }
            if (isAnnotationPresent(component, PK.class)) {
                return true;
            }
            try {
                KClass<?> kClass = JvmClassMappingKt.getKotlinClass(declaringClass);
                return !kClass.getMembers().stream()
                        .filter(member -> member.getName().equals(k.componentName())
                                && member.getParameters().size() == 1)
                        .map(KCallable::getReturnType)
                        .map(KType::isMarkedNullable)
                        .findAny()
                        .orElse(false);
            } catch (Exception e) {
                return defaultReflection.isNonnull(component);
            }
        });
    }

    @Override
    public List<Class<?>> getPermittedSubclasses(@Nonnull Class<?> sealedClass) {
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
