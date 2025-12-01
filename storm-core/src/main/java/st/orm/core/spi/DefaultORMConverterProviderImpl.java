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
package st.orm.core.spi;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import st.orm.Convert;
import st.orm.Converter;
import st.orm.DefaultConverter;
import st.orm.PersistenceException;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * @since 1.7
 */
public class DefaultORMConverterProviderImpl implements ORMConverterProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("st.orm.converter");
    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    /**
     * Entry describing a discovered default converter.
     */
    private record ConverterEntry(Converter<?, ?> converter, Class<?> databaseType, Class<?> entityType) {
    }

    private record ConverterTypes(Class<?> databaseType, Class<?> entityType) {
    }

    /**
     * All default converters discovered once at startup.
     */
    private static final List<ConverterEntry> DEFAULT_CONVERTERS = scanDefaultConverters();

    private static List<ConverterEntry> scanDefaultConverters() {
        List<ConverterEntry> result = new ArrayList<>();
        var converterTypes = REFLECTION.getSubTypesOf(Converter.class);
        for (Class<?> converterClass : converterTypes) {
            if (converterClass.isInterface() || Modifier.isAbstract(converterClass.getModifiers())) {
                continue;
            }
            if (converterClass.isMemberClass() && !Modifier.isStatic(converterClass.getModifiers())) {
                LOGGER.warn("Skipping non-static inner Converter class {} as it cannot be instantiated.", converterClass.getName());
                continue;
            }
            if (!Converter.class.isAssignableFrom(converterClass)) {
                continue;
            }
            // Only pick converters annotated as default.
            DefaultConverter defaultConverter = REFLECTION.getAnnotation(converterClass, DefaultConverter.class);
            if (defaultConverter == null) {
                continue;
            }
            ConverterTypes genericTypes = resolveConverterTypes(converterClass);
            if (genericTypes == null) {
                // Cannot resolve generics, skip this default converter.
                continue;
            }
            Converter<?, ?> converterInstance = instantiateConverter(converterClass);
            ConverterEntry entry = new ConverterEntry(
                    converterInstance,
                    genericTypes.databaseType,
                    genericTypes.entityType
            );
            result.add(entry);
        }
        return List.copyOf(result);
    }

    private static Converter<?, ?> instantiateConverter(@Nonnull Class<?> converterClass) {
        try {
            return (Converter<?, ?>) converterClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new PersistenceException("Failed to instantiate converter " + converterClass.getName(), e);
        }
    }

    /**
     * Resolve the generic &lt;D, E&gt; types of a Converter&lt;D, E&gt; implementation class.
     */
    private static ConverterTypes resolveConverterTypes(@Nonnull Class<?> converterClass) {
        // Check all directly implemented interfaces.
        for (Type type : converterClass.getGenericInterfaces()) {
            if (!(type instanceof ParameterizedType pt)) {
                continue;
            }
            Type raw = pt.getRawType();
            if (!(raw instanceof Class<?> rawClass)) {
                continue;
            }
            if (!Converter.class.isAssignableFrom(rawClass)) {
                continue;
            }
            Type[] args = pt.getActualTypeArguments();
            if (args.length != 2) {
                continue;
            }
            if (args[0] instanceof Class<?> dbType && args[1] instanceof Class<?> entityType) {
                return new ConverterTypes(dbType, entityType);
            }
        }
        // Optionally, walk up the superclass hierarchy if needed.
        Class<?> superclass = converterClass.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            return resolveConverterTypes(superclass);
        }
        return null;
    }

    /**
     * Retrieves the converter for the specified record component.
     *
     * @param component the record component for which to get the converter
     * @return an Optional containing the ORMConverter if available, or empty if not supported.
     */
    @Override
    public Optional<ORMConverter> getConverter(@Nonnull RecordComponent component) {
        requireNonNull(component, "component");
        Convert convert = REFLECTION.getAnnotation(component, Convert.class);
        if (convert != null) {
            if (convert.disableConversion()) {
                // Explicitly disabled.
                return Optional.empty();
            }
            Class<?> explicitConverterClass = convert.converter();
            if (explicitConverterClass != Void.class) {
                // Explicit converter specified, takes precedence over default converters.
                return Optional.of(createExplicitConverter(component, explicitConverterClass));
            }
        }
        // No explicit converter: try to find a single matching default converter.
        return resolveDefaultConverter(component);
    }

    private Optional<ORMConverter> resolveDefaultConverter(@Nonnull RecordComponent component) {
        Class<?> fieldType = component.getType();
        ConverterEntry match = null;
        for (ConverterEntry entry : DEFAULT_CONVERTERS) {
            // A converter's entity type must be assignable from the record component type.
            if (entry.entityType.isAssignableFrom(fieldType)) {
                if (match != null && match != entry) {
                    // Ambiguous: multiple default converters match this field type.
                    throw new PersistenceException(
                            "Multiple default converters match " +
                                    fieldType.getSimpleName() + " type for component " +
                                    component.getDeclaringRecord().getName() + "." +
                                    component.getName() +
                                    ". Use @Convert(converter = ...) or disableConversion = true."
                    );
                }
                match = entry;
            }
        }
        if (match == null) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Converter<Object, Object> converter = (Converter<Object, Object>) match.converter;
        @SuppressWarnings("unchecked")
        Class<Object> dbType = (Class<Object>) match.databaseType;
        ORMConverter ormConverter = new DefaultORMConverterImpl<>(component, converter, dbType);
        return Optional.of(ormConverter);
    }

    private ORMConverter createExplicitConverter(@Nonnull RecordComponent component,
                                                 @Nonnull Class<?> converterClass) {
        if (!Converter.class.isAssignableFrom(converterClass)) {
            throw new PersistenceException(
                    "@Convert on " + component.getDeclaringRecord().getName() + "." +
                            component.getName() + " refers to " + converterClass.getName() +
                            " which does not implement " + Converter.class.getName()
            );
        }
        Converter<?, ?> converter;
        try {
            converter = (Converter<?, ?>) converterClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new PersistenceException(
                    "Failed to instantiate converter " + converterClass.getName() +
                            " for component " + component.getDeclaringRecord().getName() + "." +
                            component.getName(),
                    e
            );
        }
        ConverterTypes types = resolveConverterTypes(converterClass);
        if (types == null) {
            throw new PersistenceException(
                    "Cannot resolve generic types for converter " + converterClass.getName() +
                            " used on " + component.getDeclaringRecord().getName() + "." +
                            component.getName()
            );
        }
        @SuppressWarnings("unchecked")
        Converter<Object, Object> typedConverter = (Converter<Object, Object>) converter;
        @SuppressWarnings("unchecked")
        Class<Object> dbType = (Class<Object>) types.databaseType;
        return new DefaultORMConverterImpl<>(component, typedConverter, dbType);
    }
}