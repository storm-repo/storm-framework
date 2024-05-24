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
package st.orm.spi;

import jakarta.annotation.Nonnull;
import st.orm.PK;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static java.util.Optional.empty;

public final class DefaultORMReflectionImpl implements ORMReflection {
    private static final Map<Class<? extends Record>, Optional<Constructor<?>>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<? extends Record>, Optional<Class<?>>> PK_TYPE_CACHE = new ConcurrentHashMap<>();

    @Override
    public Optional<Constructor<?>> findCanonicalConstructor(@Nonnull Class<? extends Record> recordType) {
        assert recordType.isRecord();
        return CONSTRUCTOR_CACHE.computeIfAbsent(recordType, _ -> {
            RecordComponent[] components = recordType.getRecordComponents();
            Constructor<?>[] constructors = recordType.getDeclaredConstructors();
            for (Constructor<?> constructor : constructors) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length != components.length) {
                    continue; // Not matching in number of parameters.
                }
                boolean matches = true; // Assume this constructor matches until proven otherwise.
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (parameterTypes[i] != components[i].getType()) {
                        matches = false; // Parameter types do not match.
                        break; // No need to check further parameters.
                    }
                }
                if (matches) {
                    // This constructor matches in both number and types of parameters.
                    return Optional.of(constructor);
                }
            }
            return empty();
        });
    }

    @Override
    public Optional<Class<?>> findPKType(@Nonnull Class<? extends Record> recordType) {
        assert recordType.isRecord();
        return PK_TYPE_CACHE.computeIfAbsent(recordType, _ -> {
            RecordComponent[] components = recordType.getRecordComponents();
            Class<?> pkType = null;
            for (RecordComponent component : components) {
                if (component.isAnnotationPresent(PK.class)) {
                    if (pkType != null) {
                        // Found multiple components with @PK annotation, throwing an exception.
                        throw new IllegalArgumentException("Multiple components are annotated with @PK.");
                    }
                    pkType = component.getType();
                }
            }
            // May be null if no @PK annotation found.
            return Optional.ofNullable(pkType);
        });
    }

    @Override
    public boolean isAnnotationPresent(@Nonnull RecordComponent component, @Nonnull Class<? extends Annotation> annotationType) {
        return getAnnotation(component, annotationType) != null;
    }

    @Override
    public boolean isAnnotationPresent(@Nonnull Class<?> type, @Nonnull Class<? extends Annotation> annotationType) {
        return getAnnotation(type, annotationType) != null;
    }

    @Override
    public <A extends Annotation> A getAnnotation(@Nonnull RecordComponent component, @Nonnull Class<A> annotationType) {
        return component.getAnnotation(annotationType);
    }

    @Override
    public <A extends Annotation> A getAnnotation(@Nonnull Class<?> type, @Nonnull Class<A> annotationType) {
        return type.getAnnotation(annotationType);
    }

    @Override
    public Class<?> getType(@Nonnull Object o) {
        return (Class<?>) o;
    }

    @Override
    public Class<? extends Record> getRecordType(@Nonnull Object clazz) {
        //noinspection unchecked
        return (Class<? extends Record>) clazz;
    }

    @Override
    public boolean isSupportedType(@Nonnull Object clazz) {
        return clazz instanceof Class<?>;
    }

    @SuppressWarnings("unchecked")
    static final Class<? extends Annotation> JAVAX_NONNULL = ((Supplier<Class<? extends Annotation>>) () -> {
        try {
            return (Class<? extends Annotation>) Class.forName("javax.annotation.Nonnull");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }).get();
    @SuppressWarnings("unchecked")
    static final Class<? extends Annotation> JAKARTA_NONNULL = ((Supplier<Class<? extends Annotation>>) () -> {
        try {
            return (Class<? extends Annotation>) Class.forName("jakarta.annotation.Nonnull");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }).get();

    @Override
    public boolean isNonnull(@Nonnull RecordComponent component) {
        return component.isAnnotationPresent(PK.class)
                || (JAVAX_NONNULL != null && component.isAnnotationPresent(JAVAX_NONNULL))
                || (JAKARTA_NONNULL != null && component.isAnnotationPresent(JAKARTA_NONNULL));
    }

    @Override
    public Object invokeComponent(@Nonnull RecordComponent component, @Nonnull Object record) throws Throwable {
        Method method = component.getAccessor();
        try {
            method.setAccessible(true);
            return method.invoke(record);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    @Override
    public boolean isDefaultMethod(@Nonnull Method method) {
        return method.isDefault();
    }

    @Override
    public Object execute(@Nonnull Object proxy, @Nonnull Method method, Object... args) throws Throwable {
        // Handle default methods using MethodHandles.
        final Class<?> declaringClass = method.getDeclaringClass();
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(declaringClass, MethodHandles.lookup());
        MethodHandle methodHandle = lookup.findSpecial(declaringClass, method.getName(), MethodType.methodType(method.getReturnType(), method.getParameterTypes()), declaringClass);
        return methodHandle.bindTo(proxy).invokeWithArguments(args);
    }
}
