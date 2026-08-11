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
package st.orm.core.repository.impl;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.stream.IntStream.range;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;
import st.orm.Data;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.core.spi.Nullability;
import st.orm.core.spi.ORMReflection;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;

public final class DefaultORMReflectionImpl implements ORMReflection {

    private interface Accessor {
        Object get(Object receiver) throws Throwable;
    }

    /**
     * Accessors per declaring class, keyed by method. {@link ClassValue} ties each entry to the lifetime of the
     * declaring class, so cached reflection artifacts never outlive the class or its class loader.
     */
    private static final ClassValue<ConcurrentMap<Method, Accessor>> ACCESSOR_CACHE = new ClassValue<>() {
        @Override
        protected ConcurrentMap<Method, Accessor> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private static Accessor accessorFor(Method m) {
        return ACCESSOR_CACHE.get(m.getDeclaringClass()).computeIfAbsent(m, method -> {
            try {
                Class<?> owner = method.getDeclaringClass();
                MethodType mt = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
                MethodHandle mh = MethodHandles.publicLookup()
                        .findVirtual(owner, method.getName(), mt);
                return mh::invoke;
            } catch (Throwable mhFailure) {
                // Fallback to reflection (works in more module setups).
                method.trySetAccessible(); // do once, not per call
                return receiver -> {
                    try {
                        return method.invoke(receiver);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                };
            }
        });
    }

    /** Primary key field per record class; empty when the record declares no {@link PK} field. */
    private static final ClassValue<Optional<RecordField>> PK_FIELD_CACHE = new ClassValue<>() {
        @Override
        protected Optional<RecordField> computeValue(Class<?> type) {
            return TYPE_CACHE.get(type)
                    .orElseThrow(() -> new PersistenceException("Record type expected: %s.".formatted(type.getName())))
                    .fields().stream()
                    .filter(field -> field.isAnnotationPresent(PK.class))
                    .findFirst();
        }
    };

    @Override
    public Object getId(Data data) {
        return PK_FIELD_CACHE.get(data.getClass())
                .map(field -> invoke(field, data))
                .orElseThrow(() -> new PersistenceException("No PK found for %s.".formatted(data.getClass().getName())));
    }

    @Override
    public Object getRecordValue(Object record, int index) {
        return invoke(getRecordType(record.getClass()).fields().get(index), record);
    }

    /** Record type descriptor per class; empty when the class is not a record. */
    private static final ClassValue<Optional<RecordType>> TYPE_CACHE = new ClassValue<>() {
        @Override
        protected Optional<RecordType> computeValue(Class<?> type) {
            if (!type.isRecord()) {
                return empty();
            }
            var components = requireNonNull(type.getRecordComponents(), "getRecordComponents should not return null");
            return CONSTRUCTOR_CACHE.get(type)
                    .map(constructor -> new RecordType(
                            type,
                            constructor,
                            asList(type.getAnnotations()),
                            range(0, components.length)
                                    .mapToObj(index -> {
                                        var component = components[index];
                                        return new RecordField(
                                                component.getDeclaringRecord(),
                                                component.getName(),
                                                component.getType(),
                                                component.getGenericType(),
                                                !isNonnull(component),
                                                false,
                                                component.getAccessor(),
                                                getAnnotations(component, constructor.getParameters()[index])
                                        );
                                    })
                                    .toList()
                        )
                    );
        }
    };

    /**
     * An annotation on a record component reaches the component itself only when its targets include
     * RECORD_COMPONENT; annotations from other libraries typically propagate to the backing field, accessor or
     * constructor parameter instead, so all four sites are folded together. An instance propagated to several
     * sites compares equal and collapses to one, keeping single-instance lookups unambiguous.
     */
    private static List<Annotation> getAnnotations(RecordComponent component, Parameter parameter) {
        var annotations = new LinkedHashSet<>(asList(component.getAnnotations()));
        try {
            annotations.addAll(asList(component.getDeclaringRecord().getDeclaredField(component.getName()).getAnnotations()));
        } catch (NoSuchFieldException e) {
            // A record component always has a backing field of the same name.
        }
        annotations.addAll(asList(component.getAccessor().getAnnotations()));
        annotations.addAll(asList(parameter.getAnnotations()));
        return List.copyOf(annotations);
    }

    @Override
    public Optional<RecordType> findRecordType(Class<?> type) {
        return TYPE_CACHE.get(type);
    }

    /** Canonical constructor per record class; empty when no constructor matches the record components. */
    private static final ClassValue<Optional<Constructor<?>>> CONSTRUCTOR_CACHE = new ClassValue<>() {
        @Override
        protected Optional<Constructor<?>> computeValue(Class<?> type) {
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
        }
    };

    @Override
    public Class<?> getType(Object o) {
        if (!(o instanceof Class<?>)) {
            throw new PersistenceException("Unsupported type: %s".formatted(o.getClass().getName()));
        }
        if (!Data.class.isAssignableFrom((Class<?>) o)) {
            throw new PersistenceException("Not a Data type: %s".formatted(((Class<?>) o).getName()));
        }
        return (Class<?>) o;
    }

    @Override
    public Class<? extends Data> getDataType(Object clazz) {
        if (!(clazz instanceof Class<?>)) {
            throw new PersistenceException("Unsupported type: %s".formatted(clazz.getClass().getName()));
        }
        if (!Data.class.isAssignableFrom((Class<?>) clazz)) {
            throw new PersistenceException("Not a Data type: %s".formatted(((Class<?>) clazz).getSimpleName()));
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

    /** Record components per record class, cached to avoid repeated expensive reflection lookups. */
    private static final ClassValue<List<RecordComponent>> RECORD_COMPONENT_CACHE = new ClassValue<>() {
        @Override
        protected List<RecordComponent> computeValue(Class<?> recordType) {
            if (!recordType.isRecord()) {
                throw new IllegalArgumentException("The specified class %s is not a record type.".formatted(recordType.getName()));
            }
            return List.of(recordType.getRecordComponents());
        }
    };

    /**
     * Returns the record components for the specified record type.
     *
     * @param recordType the record type to obtain the record components for.
     * @return the record components for the specified record type.
     * @throws IllegalArgumentException if the record type is not a record.
     */
    private static List<RecordComponent> getRecordComponents(Class<?> recordType) {
        return RECORD_COMPONENT_CACHE.get(recordType);
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
    public boolean isSupportedType(Object clazz) {
        return clazz instanceof Class<?>;
    }

    private static boolean isNonnull(RecordComponent component) {
        return component.isAnnotationPresent(PK.class)
                || component.getType().isPrimitive()
                || Nullability.isNonNull(component, component.getAnnotatedType(), null, component.getDeclaringRecord());
    }

    @Override
    public <T> List<Class<? extends T>> getPermittedSubclasses(Class<T> sealedClass) {
        Class<?>[] classes = sealedClass.getPermittedSubclasses();
        if (classes == null) {
            return List.of();
        }
        //noinspection unchecked
        return (List<Class<? extends T>>) (Object) List.of(classes);
    }

    private Object invokeComponent(RecordComponent component, Object record) throws Throwable {
        Method method = component.getAccessor();
        try {
            method.setAccessible(true);
            return method.invoke(record);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    @Override
    public boolean isDefaultMethod(Method method) {
        return method.isDefault();
    }

    @Override
    public Object invoke(RecordField field, Object record) {
        try {
            return accessorFor(field.method()).get(record);
        } catch (PersistenceException e) {
            throw e;
        } catch (Throwable t) {
            throw new PersistenceException(t);
        }
    }

    @Override
    public Object execute(Object proxy, Method method, Object... args) throws Throwable {
        // Handle default methods using MethodHandles.
        final Class<?> declaringClass = method.getDeclaringClass();
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(declaringClass, MethodHandles.lookup());
        MethodHandle methodHandle = lookup.findSpecial(declaringClass, method.getName(), MethodType.methodType(method.getReturnType(), method.getParameterTypes()), declaringClass);
        return methodHandle.bindTo(proxy).invokeWithArguments(args);
    }
}
