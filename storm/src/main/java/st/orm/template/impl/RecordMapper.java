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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.Lazy;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.SqlTemplateException;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.lang.System.arraycopy;
import static java.util.Collections.addAll;
import static java.util.Optional.empty;
import static st.orm.spi.Providers.getORMConverter;

/**
 * Factory for creating instances for record types.
 */
final class RecordMapper {
    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private RecordMapper() {
    }

    /**
     * Returns a factory for creating instances of the specified record type.
     *
     * @param columnCount the number of columns to use as constructor arguments.
     * @param type the type of the instance to create.
     * @return a factory for creating instances of the specified type.
     * @param <T> the type of the instance to create.
     * @throws SqlTemplateException if an error occurred while creating the factory.
     */
    static <T> Optional<ObjectMapper<T>> getFactory(int columnCount,
                                                    @Nonnull Class<? extends T> type,
                                                    @Nonnull LazyFactory bridge) throws SqlTemplateException {
        if (!type.isRecord()) {
            throw new SqlTemplateException(STR."Type must be a record: \{type.getName()}.");
        }
        //noinspection unchecked
        Constructor<?> recordConstructor = REFLECTION.findCanonicalConstructor((Class<? extends Record>) type)
                .orElseThrow(() -> new SqlTemplateException(STR."No canonical constructor found for record type: \{type.getSimpleName()}."));
        if (getParameterCount(type) == columnCount) {
            return Optional.of(wrapConstructor(recordConstructor, bridge));
        }
        return empty();
    }

    /**
     * Returns the number of parameters for the specified record type. This method takes into account its components
     * recursively.
     *
     * @param recordType the record type to calculate the number of parameters for.
     * @return the number of parameters for the specified record type.
     */
    private static int getParameterCount(@Nonnull Class<?> recordType) throws SqlTemplateException {
        int count = 0;
        for (RecordComponent component : recordType.getRecordComponents()) {
            Class<?> componentType = component.getType();
            var converter = getORMConverter(component);
            if (converter.isPresent()) {
                count += converter.get().getParameterCount();
            } else if (componentType.isRecord()) {
                // Recursion for nested records.
                count += getParameterCount(componentType);
            } else {
                count += 1; // Component of the record, count as one.
            }
        }
        return count;
    }

    /**
     * Wraps the specified constructor in a factory.
     *
     * @param constructor the constructor to wrap.
     * @param lazyFactory the bridge for creating supplier instances for records.
     * @return a factory for creating instances using the specified constructor.
     * @param <T> the type of the instance to create.
     */
    private static <T> ObjectMapper<T> wrapConstructor(@Nonnull Constructor<?> constructor, @Nonnull LazyFactory lazyFactory) throws SqlTemplateException {
        // Prefetch the parameter types to avoid reflection overhead.
        Class<?>[] parameterTypes = expandParameterTypes(constructor.getParameterTypes(), constructor, lazyFactory);
        return new ObjectMapper<>() {
            @Override
            public Class<?>[] getParameterTypes() {
                return parameterTypes;
            }

            @SuppressWarnings("unchecked")
            @Override
            public T newInstance(@Nonnull Object[] args) throws SqlTemplateException {
                // Adapt arguments for records recursively.
                Object[] adaptedArgs = adaptArguments(
                        constructor.getParameterTypes(),
                        constructor,
                        args,
                        0,
                        lazyFactory).arguments();
                return ObjectMapperFactory.construct((Constructor<T>) constructor, adaptedArgs, 0);
            }
        };
    }

    /**
     * Expands the specified parameter types to include the types of record components.
     *
     * @param parameterTypes the parameter types to expand.
     * @param constructor the constructor to expand the parameter types for or {@code null} if not a constructor.
     * @return the expanded parameter types.
     * @throws SqlTemplateException if an error occurred while expanding the parameter types.
     */
    private static Class<?>[] expandParameterTypes(@Nonnull Class<?>[] parameterTypes,
                                                   @Nullable Constructor<?> constructor,
                                                   @Nonnull LazyFactory lazyFactory) throws SqlTemplateException {
        List<Class<?>> expandedTypes = new ArrayList<>();
        var recordComponents = constructor == null ? null : constructor.getDeclaringClass().getRecordComponents();
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> paramType = parameterTypes[i];
            if (recordComponents != null) {
                var component = recordComponents[i];
                var converter = getORMConverter(component);
                if (converter.isPresent()) {
                    expandedTypes.addAll(converter.get().getParameterTypes());
                    continue;
                }
            }
            if (paramType.isRecord()) {
                // Recursively expand record components.
                //noinspection unchecked
                Constructor<?> canonicalConstructor = REFLECTION.findCanonicalConstructor((Class<? extends Record>) paramType).orElseThrow();
                addAll(expandedTypes, expandParameterTypes(canonicalConstructor.getParameterTypes(), canonicalConstructor, lazyFactory));
            } else if (Lazy.class.isAssignableFrom(paramType)) {
                // Lazy type, add the parameterized type.
                assert constructor != null;
                assert constructor.getDeclaringClass().isRecord();
                assert recordComponents != null;
                expandedTypes.add(lazyFactory.getPkType(recordComponents[i]));
            } else {
                // Non-record type, add directly.
                expandedTypes.add(paramType);
            }
        }
        return expandedTypes.toArray(new Class<?>[0]);
    }

    /**
     * Result of adapting arguments to match the parameter types of a constructor.
     *
     * @param arguments the adapted arguments.
     * @param offset the updated offset for the arguments.
     */
    private record AdaptArgumentsResult(Object[] arguments, int offset) {}

    /**
     * Adapts the specified arguments to match the parameter types of the constructor.
     *
     * @param parameterTypes the parameter types of the constructor.
     * @param args the arguments to adapt.
     * @param argsIndex the index of the first argument to adapt.
     * @return the adapted arguments.
     * @throws SqlTemplateException if an error occurred while creating the adapted arguments.
     */
    private static AdaptArgumentsResult adaptArguments(@Nonnull Class<?>[] parameterTypes,
                                                       @Nonnull Constructor<?> constructor,
                                                       @Nonnull Object[] args,
                                                       int argsIndex,
                                                       @Nonnull LazyFactory lazyFactory) throws SqlTemplateException {
        Object[] adaptedArgs = new Object[parameterTypes.length];
        int currentIndex = argsIndex;
        var recordComponents = constructor.getDeclaringClass().getRecordComponents();
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> paramType = parameterTypes[i];
            Object arg;
            var component = recordComponents[i];
            var converter = getORMConverter(component).orElse(null);
            if (converter != null) {
                Object[] argsCopy = new Object[converter.getParameterCount()];
                arraycopy(args, currentIndex, argsCopy, 0, argsCopy.length);
                arg = converter.convert(argsCopy);
                currentIndex += argsCopy.length;
            } else if (paramType.isRecord()) {
                //noinspection unchecked
                Constructor<?> recordConstructor = REFLECTION.findCanonicalConstructor((Class<? extends Record>) paramType)
                        .orElseThrow(() -> new SqlTemplateException(STR."No canonical constructor found for record type: \{paramType.getSimpleName()}."));
                Class<?>[] recordParamTypes = recordConstructor.getParameterTypes();
                // Recursively adapt arguments for nested records, updating currentIndex after processing.
                AdaptArgumentsResult result = adaptArguments(recordParamTypes, recordConstructor, args, currentIndex, lazyFactory);
                currentIndex = result.offset();
                if (Stream.of(result.arguments())
                        .allMatch(a -> a == null || (a instanceof Lazy<?> l && l.isNull()))) {
                    arg = null;
                } else {
                    arg = ObjectMapperFactory.construct(recordConstructor, result.arguments(), argsIndex + i);
                }
            } else if (Lazy.class.isAssignableFrom(paramType)) {
                Object pk = args[currentIndex++];
                arg = lazyFactory.create(recordComponents[i], pk);
            } else {
                arg = args[currentIndex++];
            }
            adaptedArgs[i] = arg;
        }
        return new AdaptArgumentsResult(adaptedArgs, currentIndex); // Return the adapted arguments and the updated offset.
    }
}