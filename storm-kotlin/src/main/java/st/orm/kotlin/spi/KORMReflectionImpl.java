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
package st.orm.kotlin.spi;

import jakarta.annotation.Nonnull;
import kotlin.Metadata;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KCallable;
import kotlin.reflect.KClass;
import kotlin.reflect.KType;
import st.orm.PK;
import st.orm.spi.DefaultORMReflectionImpl;
import st.orm.spi.ORMReflection;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class KORMReflectionImpl implements ORMReflection {
    private final DefaultORMReflectionImpl defaultReflection = new DefaultORMReflectionImpl();

    @Override
    public Optional<Constructor<?>> findCanonicalConstructor(@Nonnull Class<? extends Record> recordType) {
        assert recordType.isRecord();
        return defaultReflection.findCanonicalConstructor(recordType);
    }

    @Override
    public Optional<Class<?>> findPKType(@Nonnull Class<? extends Record> recordType) {
        assert recordType.isRecord();
        if (!defaultReflection.isSupportedType(recordType)) {
            return defaultReflection.findPKType(recordType);
        }
        Constructor<?> constructor = findCanonicalConstructor(recordType)
                .orElseThrow(() -> new IllegalArgumentException(STR."No canonical constructor found for record type: \{recordType.getSimpleName()}."));
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
        return Optional.ofNullable(pkType);
    }

    @Override
    public boolean isAnnotationPresent(@Nonnull RecordComponent component, @Nonnull Class<? extends Annotation> annotationType) {
        return getAnnotation(component, annotationType) != null;
    }

    @Override
    public boolean isAnnotationPresent(@Nonnull Class<?> type, @Nonnull Class<? extends Annotation> annotationType) {
        return getAnnotation(type, annotationType) != null;
    }

    record ComponentCacheKey(@Nonnull Constructor<?> constructor, @Nonnull String componentName) {}
    private final static Map<ComponentCacheKey, Parameter> COMPONENT_PARAMETER_CACHE = new ConcurrentHashMap<>();

    @Override
    public <A extends Annotation> A getAnnotation(@Nonnull RecordComponent component, @Nonnull Class<A> annotationType) {
        if (!defaultReflection.isSupportedType(component.getDeclaringRecord())) {
            return defaultReflection.getAnnotation(component, annotationType);
        }
        //noinspection unchecked
        Class<? extends Record> recordType = (Class<? extends Record>) component.getDeclaringRecord();
        Constructor<?> constructor = findCanonicalConstructor(recordType)
                .orElseThrow(() -> new IllegalArgumentException(STR."No canonical constructor found for record type: \{component.getDeclaringRecord().getSimpleName()}."));
        assert constructor.getParameters().length == recordType.getRecordComponents().length;
        Parameter parameter = COMPONENT_PARAMETER_CACHE.computeIfAbsent(new ComponentCacheKey(constructor, component.getName()), _ -> {
            int index = 0;
            for (var candidate : recordType.getRecordComponents()) {
                if (candidate.getName().equals(component.getName())) {
                    return constructor.getParameters()[index];
                }
                index++;
            }
            throw new IllegalArgumentException(STR."No parameter found for component: \{component.getName()} for record type: \{component.getDeclaringRecord().getSimpleName()}.");
        });
        return parameter.getAnnotation(annotationType);
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
    public Object invokeComponent(@Nonnull RecordComponent component, @Nonnull Object record) throws Throwable {
        Method method = component.getAccessor();
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

    @Override
    public boolean isNonnull(@Nonnull RecordComponent component) {
        try {
            KClass<?> kClass = JvmClassMappingKt.getKotlinClass(component.getDeclaringRecord());
            var nullable = kClass.getMembers().stream()
                    .filter(member -> member.getName().equals(component.getName())
                            && member.getParameters().size() == 1)
                    .map(KCallable::getReturnType)
                    .map(KType::isMarkedNullable)
                    .findAny()
                    .orElse(false);
            return !nullable;
        } catch (Exception e) {
            return defaultReflection.isNonnull(component);
        }
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
    public Object execute(@Nonnull Object proxy, @Nonnull Method method, Object... args) throws Throwable {
        if (defaultReflection.isDefaultMethod(method)) {
            return defaultReflection.execute(proxy, method, args);
        }
        Class<?> interfaceClass = method.getDeclaringClass();
        Class<?> defaultImplsClass = Class.forName(STR."\{interfaceClass.getName()}$DefaultImpls");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(defaultImplsClass, MethodHandles.lookup());
        List<Class<?>> parameterTypes = new ArrayList<>();
        parameterTypes.add(interfaceClass);
        parameterTypes.addAll(Arrays.asList(method.getParameterTypes()));
        MethodHandle methodHandle = lookup.findStatic(defaultImplsClass, method.getName(), MethodType.methodType(method.getReturnType(), parameterTypes.toArray(new Class[0])));
        return methodHandle.bindTo(proxy).invokeWithArguments(args);
    }
}
