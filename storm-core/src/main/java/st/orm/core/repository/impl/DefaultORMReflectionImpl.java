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
package st.orm.core.repository.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.Data;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.core.spi.ORMReflection;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;

public final class DefaultORMReflectionImpl implements ORMReflection {
    private static final Map<Class<?>, Optional<RecordType>> TYPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<RecordField>> PK_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Constructor<?>>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

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
            if (!type.isRecord()) {
                return empty();
            }
            return findCanonicalConstructor(type)
                    .map(constructor -> new RecordType(
                            type,
                            constructor,
                            asList(type.getAnnotations()),
                            stream(requireNonNull(type.getRecordComponents(), "getRecordComponents should not return null"))
                                    .map(component -> new RecordField(
                                            component.getDeclaringRecord(),
                                            component.getName(),
                                            component.getType(),
                                            component.getGenericType(),
                                            !isNonnull(component),
                                            false,
                                            component.getAccessor(),
                                            asList(component.getAnnotations())
                                        )
                                    )
                                    .toList()
                        )
                    );
        });
    }

    private Optional<Constructor<?>> findCanonicalConstructor(@Nonnull Class<?> type) {
        return CONSTRUCTOR_CACHE.computeIfAbsent(type, ignore -> {
            RecordComponent[] components = type.getRecordComponents();
            Constructor<?>[] constructors = type.getDeclaredConstructors();
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
    public Class<?> getType(@Nonnull Object o) {
        if (!(o instanceof Class<?>)) {
            throw new PersistenceException("Unsupported type: %s".formatted(o.getClass().getName()));
        }
        if (!Data.class.isAssignableFrom((Class<?>) o)) {
            throw new PersistenceException("Not a Data type: %s".formatted(((Class<?>) o).getName()));
        }
        return (Class<?>) o;
    }

    @Override
    public Class<? extends Data> getDataType(@Nonnull Object clazz) {
        if (!(clazz instanceof Class<?>)) {
            throw new PersistenceException("Unsupported type: %s".formatted(clazz.getClass().getName()));
        }
        if (!Data.class.isAssignableFrom((Class<?>) clazz)) {
            throw new PersistenceException("Not a Data type: %s".formatted(clazz.getClass().getName()));
        }
        //noinspection unchecked
        return (Class<? extends Data>) clazz;
    }

    @Override
    public boolean isDefaultValue(@Nullable Object o) {
        if (o == null) {
            return true;
        }
        if (isPrimitiveOrWrapper(o)) {
            return isPrimitiveDefaultValue(o);
        }
        if (o.getClass().isRecord()) {
            return areRecordComponentsDefault(o);
        }
        return false;
    }

    private boolean isPrimitiveOrWrapper(Object o) {
        return o instanceof Byte || o instanceof Short || o instanceof Integer ||
                o instanceof Long || o instanceof Float || o instanceof Double ||
                o instanceof Character || o instanceof Boolean;
    }

    private boolean isPrimitiveDefaultValue(Object o) {
        if (o instanceof Byte && (Byte) o == 0) return true;
        if (o instanceof Short && (Short) o == 0) return true;
        if (o instanceof Integer && (Integer) o == 0) return true;
        if (o instanceof Long && (Long) o == 0) return true;
        if (o instanceof Float && (Float) o == 0.0f) return true;
        if (o instanceof Double && (Double) o == 0.0) return true;
        if (o instanceof Character && (Character) o == '\u0000') return true;
        if (o instanceof Boolean && !(Boolean) o) return true;
        return false;
    }

    private static final ConcurrentHashMap<Class<?>, List<RecordComponent>> RECORD_COMPONENT_CACHE
            = new ConcurrentHashMap<>();

    /**
     * Returns the record components for the specified record type. The result is cached to avoid repeated expensive
     * reflection lookups.
     *
     * @param recordType the record type to obtain the record components for.
     * @return the record components for the specified record type.
     * @throws IllegalArgumentException if the record type is not a record.
     */
    private static List<RecordComponent> getRecordComponents(@Nonnull Class<?> recordType) {
        return RECORD_COMPONENT_CACHE.computeIfAbsent(recordType, ignore -> {
            if (!recordType.isRecord()) {
                throw new IllegalArgumentException("The specified class %s is not a record type.".formatted(recordType.getName()));
            }
            return List.of(recordType.getRecordComponents());
        });
    }

    private boolean areRecordComponentsDefault(Object record) {
        try {
            for (RecordComponent component : getRecordComponents(record.getClass())) {
                Object componentValue = invokeComponent(component, record);
                if (!isDefaultValue(componentValue)) {
                    return false;
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to access record component values.", t);
        }
        return true;
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

    private boolean isNonnull(@Nonnull RecordComponent component) {
        return component.isAnnotationPresent(PK.class)
                || component.getType().isPrimitive()
                || (JAVAX_NONNULL != null && component.isAnnotationPresent(JAVAX_NONNULL))
                || (JAKARTA_NONNULL != null && component.isAnnotationPresent(JAKARTA_NONNULL));
    }

    @Override
    public <T> List<Class<? extends T>> getPermittedSubclasses(@Nonnull Class<T> sealedClass) {
        Class<?>[] classes = sealedClass.getPermittedSubclasses();
        if (classes == null) {
            return List.of();
        }
        //noinspection unchecked
        return (List<Class<? extends T>>) (Object) List.of(classes);
    }

    private Object invokeComponent(@Nonnull RecordComponent component, @Nonnull Object record) throws Throwable {
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
    public Object invoke(@Nonnull RecordType type, @Nonnull Object[] args) {
        try {
            try {
                // Method is expected to be accessible.
                return type.constructor().newInstance(args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        } catch (PersistenceException e) {
            throw e;
        } catch (Throwable t) {
            throw new PersistenceException(t);
        }
    }

    @Override
    public Object invoke(@Nonnull RecordField field, @Nonnull Object record) {
        try {
            try {
                // Method is expected to be accessible.
                return field.method().invoke(record);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        } catch (PersistenceException e) {
            throw e;
        } catch (Throwable t) {
            throw new PersistenceException(t);
        }
    }

    @Override
    public Object execute(@Nonnull Object proxy, @Nonnull Method method, @Nonnull Object... args) throws Throwable {
        // Handle default methods using MethodHandles.
        final Class<?> declaringClass = method.getDeclaringClass();
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(declaringClass, MethodHandles.lookup());
        MethodHandle methodHandle = lookup.findSpecial(declaringClass, method.getName(), MethodType.methodType(method.getReturnType(), method.getParameterTypes()), declaringClass);
        return methodHandle.bindTo(proxy).invokeWithArguments(args);
    }
}
