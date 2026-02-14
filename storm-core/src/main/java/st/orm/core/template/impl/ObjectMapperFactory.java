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
import st.orm.core.spi.TransactionTemplate;
import st.orm.core.template.SqlTemplateException;

/**
 * Factory for creating instances of a specific type.
 */
public final class ObjectMapperFactory {

    private static final TransactionTemplate TRANSACTION_TEMPLATE = Providers.getTransactionTemplate();
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
        if (isRecord(type)) {
            return RecordMapper.getFactory(columnCount, getRecordType(type), refFactory,
                    TRANSACTION_TEMPLATE.currentContext().orElse(null));
        }
        if (type.isEnum()) {
            return EnumMapper.getFactory(columnCount, type);
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
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            Parameter[] parameters = constructor.getParameters();
            for (int i = 0; i < parameterTypes.length; i++) {
                Object arg = args[i];
                Class<?> paramType = parameterTypes[i];
                if (arg == null) {
                    if (isNonnull(parameters[i])) {
                        throw new SqlTemplateException("Non-null argument of %s (%s) is null at position %d."
                                .formatted(constructor.getDeclaringClass().getSimpleName(), parameters[i].getName(), offset + i + 1));
                    }
                    if (paramType.isPrimitive()) {
                        throw new SqlTemplateException("Primitive argument of %s (%s) is null at position %d."
                                .formatted(constructor.getDeclaringClass().getSimpleName(), parameters[i].getName(), offset + i + 1));
                    }
                }
            }
            constructor.setAccessible(true);
            try {
                return constructor.newInstance(args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        } catch (SqlTemplateException e) {
            throw e;
        } catch (Throwable t) {
            throw new SqlTemplateException("Failed to create a new instance.", t);
        }
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

    private static final Map<Parameter, Boolean> NONNULL_CACHE = new ConcurrentHashMap<>();

    /**
     * Returns true if the specified parameter is marked as non-null, false otherwise.
     *
     * @param parameter the parameter to check for a non-null characteristics.
     * @return true if the specified parameter is marked as non-null, false otherwise.
     */
    static boolean isNonnull(@Nonnull Parameter parameter) {
        // Use the cache to return the result if it's already calculated
        return NONNULL_CACHE.computeIfAbsent(parameter, param ->
                param.isAnnotationPresent(PK.class)
                        || (JAVAX_NONNULL != null && param.isAnnotationPresent(JAVAX_NONNULL))
                        || (JAKARTA_NONNULL != null && param.isAnnotationPresent(JAKARTA_NONNULL))
        );
    }
}
