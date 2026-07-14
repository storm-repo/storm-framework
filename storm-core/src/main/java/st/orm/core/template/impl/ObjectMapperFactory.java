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
package st.orm.core.template.impl;

import static java.util.Optional.empty;
import static st.orm.core.template.impl.RecordReflection.getRecordType;
import static st.orm.core.template.impl.RecordReflection.isRecord;
import static st.orm.core.template.impl.RecordReflection.isSealedEntity;
import static st.orm.core.template.impl.RecordValidation.validateDataType;

import jakarta.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.BitSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import st.orm.Data;
import st.orm.PK;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.core.spi.RefFactory;
import st.orm.core.template.SqlTemplateException;

/**
 * Factory for creating instances of a specific type.
 */
public final class ObjectMapperFactory {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private ObjectMapperFactory() {
    }

    /**
     * Returns a factory for creating instances of the specified type.
     *
     * @param columnCount the number of columns to use as constructor arguments.
     * @param type the type of the instance to create.
     * @param refFactory the factory for creating ref instances for entities and projections.
     * @return a factory for creating instances of the specified type.
     * @param <T> the type of the instance to create.
     * @throws SqlTemplateException if the factory could not be created.
     */
    public static <T> Optional<ObjectMapper<T>> getObjectMapper(int columnCount,
                                                                @Nonnull Class<T> type,
                                                                @Nonnull RefFactory refFactory) throws SqlTemplateException {
        if (type.isPrimitive()) {
            return PrimitiveMapper.getFactory(columnCount, type);
        }
        if (Data.class.isAssignableFrom(type)) {
            //noinspection unchecked
            validateDataType((Class<? extends Data>) type, false);
        }
        if (isSealedEntity(type)) {
            return RecordMapper.getSealedFactory(columnCount, type, refFactory,
                    refFactory.transactionContext());
        }
        if (isRecord(type)) {
            return RecordMapper.getFactory(columnCount, getRecordType(type), refFactory,
                    refFactory.transactionContext());
        }
        if (type.isEnum()) {
            return EnumMapper.getFactory(columnCount, type);
        }
        // Leaf value types (UUID, String, BigDecimal, boxed primitives, java.time.*, etc.) are
        // produced as-is by the column reader. Skip the constructor scan so we don't try to
        // reflect into JDK-private constructors like UUID(byte[]).
        if (ValueMapper.isValueType(type)) {
            Optional<ObjectMapper<T>> valueMapper = ValueMapper.getFactory(columnCount, type);
            if (valueMapper.isPresent()) {
                return valueMapper;
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            int parameterCount = constructor.getParameterTypes().length;
            if (parameterCount == columnCount) {
                return Optional.of(wrapConstructor(constructor));
            }
        }
        return empty();
    }

    /**
     * Wraps the specified constructor in a factory.
     *
     * @param constructor the constructor to wrap.
     * @return a factory for creating instances using the specified constructor.
     * @param <T> the type of the instance to create.
     */
    private static <T> ObjectMapper<T> wrapConstructor(@Nonnull Constructor<?> constructor) {
        // Replace StringBuilder with String in the constructor for max JDBC compatibility.
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        BitSet stringBuilders = new BitSet(parameterTypes.length);
        for (int i = 0; i < parameterTypes.length; i++) {
            if (parameterTypes[i] == StringBuilder.class) {
                stringBuilders.set(i);
                parameterTypes[i] = String.class;
            }
        }
        if (stringBuilders.isEmpty()) {
            return new ObjectMapper<>() {
                @Override public Class<?>[] getParameterTypes() { return parameterTypes; }

                @SuppressWarnings("unchecked") @Override public T newInstance(@Nonnull Object[] args) throws SqlTemplateException {
                    return construct((Constructor<T>) constructor, args);
                }
            };
        }
        return new ObjectMapper<>() {
            @Override public Class<?>[] getParameterTypes() { return parameterTypes; }

            @SuppressWarnings("unchecked") @Override public T newInstance(@Nonnull Object[] args) throws SqlTemplateException {
                for (int i = stringBuilders.nextSetBit(0); i >= 0; i = stringBuilders.nextSetBit(i + 1)) {
                    args[i] = new StringBuilder(args[i].toString());
                }
                return construct((Constructor<T>) constructor, args);
            }
        };
    }

    /**
     * Constructs a new instance of the specified type using the specified constructor and arguments.
     *
     * @param constructor the constructor to use for creating the instance.
     * @param args the arguments to pass to the constructor.
     * @return a new instance of the specified type using the specified constructor and arguments.
     * @param <T> the type of the instance to create.
     * @throws SqlTemplateException if the instance could not be created.
     */
    private static <T> T construct(@Nonnull Constructor<T> constructor, @Nonnull Object[] args) throws SqlTemplateException {
        return construct(constructor, args, 0);
    }

    /**
     * Constructs a new instance of the specified type using the specified constructor and arguments.
     *
     * @param constructor the constructor to use for creating the instance.
     * @param args the arguments to pass to the constructor.
     * @param offset the parameter offset of the top-level input.
     * @return a new instance of the specified type using the specified constructor and arguments.
     * @param <T> the type of the instance to create.
     * @throws SqlTemplateException if the instance could not be created.
     */
    static <T> T construct(@Nonnull Constructor<T> constructor, @Nonnull Object[] args, int offset) throws SqlTemplateException {
        try {
            // Constructor metadata is precomputed and cached: per-invocation getParameterTypes/getParameters calls
            // clone their arrays, which is measurable on the row mapping hot path.
            ConstructorMeta meta = CONSTRUCTOR_META.computeIfAbsent(constructor, ObjectMapperFactory::constructorMeta);
            boolean[] nonNull = meta.nonNull();
            boolean[] primitive = meta.primitive();
            for (int i = 0; i < nonNull.length; i++) {
                if (args[i] == null) {
                    if (nonNull[i]) {
                        throw new SqlTemplateException("Database returned NULL for non-nullable field '%s.%s' at column position %d. Either %s, ensure the column has a NOT NULL constraint with a default value, or verify the query returns the expected data."
                                .formatted(constructor.getDeclaringClass().getSimpleName(), meta.parameterNames()[i], offset + i + 1, nullableHint(constructor.getDeclaringClass())));
                    }
                    if (primitive[i]) {
                        throw new SqlTemplateException("Database returned NULL for primitive field '%s.%s' at column position %d. Primitive types cannot hold null values. Change the field type to its wrapper class (e.g., int to Integer) and %s, or ensure the column is NOT NULL."
                                .formatted(constructor.getDeclaringClass().getSimpleName(), meta.parameterNames()[i], offset + i + 1, nullableHint(constructor.getDeclaringClass())));
                    }
                }
            }
            try {
                // Use the map's constructor instance: its accessible flag was set before publication, so the
                // happens-before edge of the concurrent map makes the flag visible to all threads.
                //noinspection unchecked
                return (T) meta.constructor().newInstance(args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        } catch (SqlTemplateException e) {
            throw e;
        } catch (Throwable t) {
            throw new SqlTemplateException("Failed to create a new instance of %s.".formatted(constructor.getDeclaringClass().getSimpleName()), t);
        }
    }

    /**
     * Precomputed constructor metadata for the row mapping hot path.
     *
     * @param constructor the constructor with its accessible flag set.
     * @param parameterNames the parameter names, for error messages.
     * @param nonNull whether each parameter is marked as non-null.
     * @param primitive whether each parameter is a primitive type.
     */
    private record ConstructorMeta(@Nonnull Constructor<?> constructor,
                                   @Nonnull String[] parameterNames,
                                   @Nonnull boolean[] nonNull,
                                   @Nonnull boolean[] primitive) {}

    /** Cache of precomputed constructor metadata, keyed by constructor. Thread-safe for concurrent access. */
    private static final Map<Constructor<?>, ConstructorMeta> CONSTRUCTOR_META = new ConcurrentHashMap<>();

    private static ConstructorMeta constructorMeta(@Nonnull Constructor<?> constructor) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Parameter[] parameters = constructor.getParameters();
        String[] parameterNames = new String[parameters.length];
        boolean[] nonNull = new boolean[parameters.length];
        boolean[] primitive = new boolean[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            parameterNames[i] = parameters[i].getName();
            nonNull[i] = isNonnull(parameters[i]);
            primitive[i] = parameterTypes[i].isPrimitive();
        }
        constructor.setAccessible(true);
        return new ConstructorMeta(constructor, parameterNames, nonNull, primitive);
    }

    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> KOTLIN_METADATA = ((Supplier<Class<? extends Annotation>>) () -> {
        try {
            return (Class<? extends Annotation>) Class.forName("kotlin.Metadata");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }).get();

    /**
     * Returns {@code true} if the specified class is a Kotlin class (i.e. annotated with {@code kotlin.Metadata}).
     */
    static boolean isKotlinClass(@Nonnull Class<?> clazz) {
        return KOTLIN_METADATA != null && clazz.isAnnotationPresent(KOTLIN_METADATA);
    }

    /**
     * Returns language-appropriate guidance for making a field nullable.
     *
     * @param clazz the declaring class of the field.
     * @return a hint string for Java ({@code @Nullable}) or Kotlin (nullable type syntax).
     */
    static String nullableHint(@Nonnull Class<?> clazz) {
        return isKotlinClass(clazz)
                ? "make the property nullable (e.g., String?)"
                : "annotate the field with @Nullable";
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

    /**
     * Returns true if the specified parameter is marked as non-null, false otherwise.
     *
     * <p>Only called when building {@link ConstructorMeta}, which caches the result per constructor parameter.</p>
     *
     * @param parameter the parameter to check for a non-null characteristics.
     * @return true if the specified parameter is marked as non-null, false otherwise.
     */
    static boolean isNonnull(@Nonnull Parameter parameter) {
        return parameter.isAnnotationPresent(PK.class)
                || (JAVAX_NONNULL != null && parameter.isAnnotationPresent(JAVAX_NONNULL))
                || (JAKARTA_NONNULL != null && parameter.isAnnotationPresent(JAKARTA_NONNULL));
    }
}
