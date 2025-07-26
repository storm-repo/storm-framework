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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.EnumType;
import st.orm.DbEnum;
import st.orm.Ref;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.core.spi.RefFactory;
import st.orm.core.spi.WeakInterner;
import st.orm.core.template.SqlTemplateException;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static java.lang.System.arraycopy;
import static java.util.Collections.addAll;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static st.orm.EnumType.NAME;
import static st.orm.core.spi.Providers.getORMConverter;
import static st.orm.core.template.impl.RecordReflection.getRefPkType;
import static st.orm.core.template.impl.RecordReflection.getRefRecordType;

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
     * @param refFactory the factory for creating ref instances for entities and projections.
     * @return a factory for creating instances of the specified type.
     * @param <T> the type of the instance to create.
     * @throws SqlTemplateException if an error occurred while creating the factory.
     */
    static <T> Optional<ObjectMapper<T>> getFactory(int columnCount,
                                                    @Nonnull Class<? extends T> type,
                                                    @Nonnull RefFactory refFactory) throws SqlTemplateException {
        if (!type.isRecord()) {
            throw new SqlTemplateException("Type must be a record: %s.".formatted(type.getName()));
        }
        //noinspection unchecked
        Constructor<?> recordConstructor = REFLECTION.findCanonicalConstructor((Class<? extends Record>) type)
                .orElseThrow(() -> new SqlTemplateException("No canonical constructor found for record type: %s.".formatted(type.getSimpleName())));
        if (getParameterCount(type) == columnCount) {
            return Optional.of(wrapConstructor(recordConstructor, refFactory));
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
        var recordComponents = RecordReflection.getRecordComponents(recordType);
        for (RecordComponent component : recordComponents) {
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
     * @param refFactory the bridge for creating supplier instances for records.
     * @return a factory for creating instances using the specified constructor.
     * @param <T> the type of the instance to create.
     */
    private static <T> ObjectMapper<T> wrapConstructor(@Nonnull Constructor<?> constructor,
                                                       @Nonnull RefFactory refFactory) throws SqlTemplateException {
        // Prefetch the parameter types to avoid reflection overhead.
        Class<?>[] parameterTypes = expandParameterTypes(constructor.getParameterTypes(), constructor, refFactory);
        var interner = new WeakInterner();
        return new ObjectMapper<>() {
            @Override
            public Class<?>[] getParameterTypes() {
                return parameterTypes;
            }

            @SuppressWarnings("unchecked")
            @Override
            public T newInstance(@Nonnull Object[] args) throws SqlTemplateException {
                var constructorComponents = RecordReflection.getRecordComponents(constructor.getDeclaringClass());
                // Adapt arguments for records recursively.
                Object[] adaptedArgs = adaptArguments(
                        constructor.getParameterTypes(),
                        constructorComponents,
                        args,
                        0,
                        refFactory,
                        false,
                        interner).arguments();
                // Don't intern top level records.
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
                                                   @Nonnull RefFactory refFactory) throws SqlTemplateException {
        List<Class<?>> expandedTypes = new ArrayList<>();
        var recordComponents = constructor == null
                ? null
                : RecordReflection.getRecordComponents(constructor.getDeclaringClass());
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> paramType = parameterTypes[i];
            if (recordComponents != null) {
                var component = recordComponents.get(i);
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
                addAll(expandedTypes, expandParameterTypes(canonicalConstructor.getParameterTypes(), canonicalConstructor, refFactory));
            } else if (Ref.class.isAssignableFrom(paramType)) {
                // Ref type, add the parameterized type.
                assert constructor != null;
                assert constructor.getDeclaringClass().isRecord();
                assert recordComponents != null;
                expandedTypes.add(getRefPkType(recordComponents.get(i)));
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

    private static final Pattern INT_PATTERN = Pattern.compile("\\d+");

    private static boolean isArgNull(@Nullable Object arg) {
        return arg == null || (arg instanceof Ref<?> r && r.isNull());
    }

    /**
     * Adapts the specified arguments to match the parameter types of the constructor.
     *
     * @param parameterTypes the parameter types of the constructor.
     * @param components the constructor to adapt the arguments for.
     * @param args the arguments to adapt.
     * @param argsIndex the index of the first argument to adapt.
     * @param parentNullable whether the record is nullable.
     * @return the adapted arguments.
     * @throws SqlTemplateException if an error occurred while creating the adapted arguments.
     */
    private static AdaptArgumentsResult adaptArguments(@Nonnull Class<?>[] parameterTypes,
                                                       @Nonnull List<RecordComponent> components,
                                                       @Nonnull Object[] args,
                                                       int argsIndex,
                                                       @Nonnull RefFactory refFactory,
                                                       boolean parentNullable,
                                                       @Nonnull WeakInterner interner) throws SqlTemplateException {
        Object[] adaptedArgs = new Object[parameterTypes.length];
        int currentIndex = argsIndex;
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> paramType = parameterTypes[i];
            Object arg;
            var component = components.get(i);
            var converter = getORMConverter(component).orElse(null);
            boolean nonnull = REFLECTION.isNonnull(component);
            boolean nullable = parentNullable || !nonnull;
            if (converter != null) {
                Object[] argsCopy = new Object[converter.getParameterCount()];
                arraycopy(args, currentIndex, argsCopy, 0, argsCopy.length);
                arg = converter.fromDatabase(argsCopy, refFactory);
                currentIndex += argsCopy.length;
            } else if (paramType.isRecord()) {
                //noinspection unchecked
                Constructor<?> recordConstructor = REFLECTION.findCanonicalConstructor((Class<? extends Record>) paramType)
                        .orElseThrow(() -> new SqlTemplateException("No canonical constructor found for record type: %s.".formatted(paramType.getSimpleName())));
                Class<?>[] recordParamTypes = recordConstructor.getParameterTypes();
                // Recursively adapt arguments for nested records, updating currentIndex after processing.
                var recordComponents = RecordReflection.getRecordComponents(recordConstructor.getDeclaringClass());
                AdaptArgumentsResult result = adaptArguments(recordParamTypes, recordComponents, args, currentIndex, refFactory, nullable, interner);
                if (!nonnull && Arrays.stream(args, currentIndex, result.offset()).allMatch(RecordMapper::isArgNull)) {
                    // All elements are null and the record is nullable, so we can set the argument to null.
                    arg = Ref.class.isAssignableFrom(paramType) ? Ref.ofNull() : null;
                } else{
                    RecordComponent nullViolation = null;
                    Object[] recordArgs = result.arguments();
                    for (int j = 0; j < recordArgs.length; j++) {
                        if (isArgNull(recordArgs[j]) && REFLECTION.isNonnull(recordComponents.get(j))) {
                            nullViolation = recordComponents.get(j);
                            break;
                        }
                    }
                    if (nullViolation != null) {
                        // One of the arguments is null while the component is non-nullable.
                        if (!nullable) {
                            throw new SqlTemplateException("Argument for non-null component '%s.%s' is null.".formatted(nullViolation.getDeclaringRecord().getSimpleName(), nullViolation.getName()));
                        }
                        // One of the parent elements is nullable.
                        arg = Ref.class.isAssignableFrom(paramType) ? Ref.ofNull() : null;
                    } else {
                        // Using the weak interner to use canonical instances of nested records. The interner uses weak
                        // references to allow interning being used even in the event of (infinite) result streams. If the
                        // caller keeps the records the interner will reuse those same records. If the caller discards the
                        // records the interner will not keep track of them and new instances will be outputted.
                        arg = interner.intern(ObjectMapperFactory.construct(recordConstructor, result.arguments(), argsIndex + i));
                    }
                }
                currentIndex = result.offset();
            } else if (paramType.isEnum()) {
                EnumType type = ofNullable(REFLECTION.getAnnotation(component, DbEnum.class))
                        .map(DbEnum::value)
                        .orElse(NAME);
                Object v = switch (type) {
                    case NAME -> args[currentIndex++];
                    case ORDINAL -> {
                        Object o = args[currentIndex++];
                        if (o == null) {
                            yield null;
                        }
                        if (o instanceof String s && INT_PATTERN.matcher(s).matches()) {
                            yield Integer.parseInt(s);
                        }
                        throw new SqlTemplateException("Argument for ordinal enum component %s.%s is not valid.".formatted(component.getDeclaringRecord().getSimpleName(), component.getName()));
                    }
                };
                arg = EnumMapper.getFactory(1, paramType).orElseThrow().newInstance(new Object[] { v });
            } else if (Ref.class.isAssignableFrom(paramType)) {
                Object pk = args[currentIndex++];
                arg = pk == null ? Ref.ofNull() : refFactory.create(getRefRecordType(components.get(i)), pk);
            } else {
                arg = args[currentIndex++];
            }
            if (!nullable && isArgNull(arg)) {
                throw new SqlTemplateException("Argument for non-null component '%s.%s' is null.".formatted(component.getDeclaringRecord().getSimpleName(), component.getName()));
            }
            adaptedArgs[i] = arg;
        }
        return new AdaptArgumentsResult(adaptedArgs, currentIndex); // Return the adapted arguments and the updated offset.
    }
}