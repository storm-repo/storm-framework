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
package st.orm.core.spi;

import jakarta.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.net.URL;
import java.util.*;
import st.orm.Converter;
import st.orm.Data;

public final class TypeDiscovery {

    private static final String INDEX_DIRECTORY = "META-INF/storm/";
    private static final String DATA_TYPE = "st.orm.Data";
    private static final String CONVERTER_TYPE = "st.orm.Converter";
    private static final String REPOSITORY_TYPE = "st.orm.repository.Repository";

    private TypeDiscovery() {
    }

    /**
     * Returns all discovered subtypes of st.orm.Data based on the index file.
     */
    public static List<Class<? extends Data>> getDataTypes() {
        return loadTypes(DATA_TYPE, Data.class);
    }

    /**
     * Returns all discovered subtypes of st.orm.Converter based on the index file.
     */
    public static List<Class<? extends Converter<?, ?>>> getConverterTypes() {
        //noinspection unchecked
        return (List<Class<? extends Converter<?, ?>>>) (Object) loadTypes(CONVERTER_TYPE, Converter.class);
    }

    /**
     * Returns all discovered subtypes of st.orm.repository.Repository based on the index file.
     *
     * <p>The index is generated at compile time by the Storm metamodel processor (annotation processor or KSP).
     * It contains all interfaces in the user's project that extend {@code EntityRepository} or
     * {@code ProjectionRepository}.</p>
     *
     * <p>The type check is intentionally lenient: the index is already curated at compile time, and the
     * repository interface may come from different modules ({@code st.orm.core.repository.Repository},
     * {@code st.orm.repository.Repository} in Java 21 or Kotlin). Checking against a single base type
     * would silently reject valid entries.</p>
     */
    public static List<Class<?>> getRepositoryTypes() {
        return loadClasses(REPOSITORY_TYPE);
    }

    /**
     * Returns the component types of the given Data type: compound primary keys and inline components
     * are plain records or data classes that do not appear in the type index themselves, yet they are
     * introspected the same way as the Data types that carry them. The constructor parameters are
     * walked recursively; JDK, Kotlin, Jakarta, and Storm types are left out, as their metadata is
     * covered elsewhere.
     *
     * <p>Native-image support builds on this component set: the GraalVM feature and the Spring AOT hints
     * register these types for reflection alongside the indexed Data types.</p>
     *
     * @param type the Data type whose components to resolve.
     * @return the transitive application-domain component types, excluding the given type itself.
     * @since 1.13
     */
    public static List<Class<?>> getComponentTypes(@Nonnull Class<?> type) {
        Set<Class<?>> components = new LinkedHashSet<>();
        collectComponents(type, components);
        components.remove(type);
        return List.copyOf(components);
    }

    private static void collectComponents(@Nonnull Class<?> type, @Nonnull Set<Class<?>> visited) {
        if (!visited.add(type)) {
            return;
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            for (Type parameterType : constructor.getGenericParameterTypes()) {
                collectFromType(parameterType, visited);
            }
        }
    }

    /**
     * Follows a generic constructor parameter type into the application types it mentions: the raw
     * class itself, and the type arguments of parameterized types such as {@code List<Photo>}, whose
     * elements are introspected during serialization even though the raw type is a platform type.
     */
    private static void collectFromType(@Nonnull Type genericType, @Nonnull Set<Class<?>> visited) {
        if (genericType instanceof Class<?> cls) {
            if (isApplicationType(cls)) {
                collectComponents(cls, visited);
            }
        } else if (genericType instanceof ParameterizedType parameterizedType) {
            collectFromType(parameterizedType.getRawType(), visited);
            for (Type typeArgument : parameterizedType.getActualTypeArguments()) {
                collectFromType(typeArgument, visited);
            }
        } else if (genericType instanceof WildcardType wildcardType) {
            for (Type bound : wildcardType.getUpperBounds()) {
                collectFromType(bound, visited);
            }
        } else if (genericType instanceof GenericArrayType genericArrayType) {
            collectFromType(genericArrayType.getGenericComponentType(), visited);
        }
    }

    /**
     * Whether the type belongs to the application domain rather than to the JDK or the Kotlin runtime,
     * whose types are covered by their own metadata. Framework types that appear as constructor
     * parameters, such as {@code Ref}, are interfaces without constructors, so including them is
     * harmless and keeps the filter from accidentally excluding application packages.
     */
    private static boolean isApplicationType(@Nonnull Class<?> type) {
        if (type.isPrimitive() || type.isArray()) {
            return false;
        }
        String name = type.getName();
        return !name.startsWith("java.")
                && !name.startsWith("javax.")
                && !name.startsWith("jakarta.")
                && !name.startsWith("kotlin");
    }

    @SuppressWarnings("SameParameterValue")
    private static List<Class<?>> loadClasses(@Nonnull String typeFqName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = TypeDiscovery.class.getClassLoader();
        }
        String resourceName = INDEX_DIRECTORY + typeFqName + ".idx";
        List<String> classNames = loadResourceLines(classLoader, resourceName);
        if (classNames.isEmpty()) {
            return List.of();
        }
        List<Class<?>> result = new ArrayList<>();
        for (String fqClassName : new LinkedHashSet<>(classNames)) {
            try {
                result.add(Class.forName(fqClassName, false, classLoader));
            } catch (Throwable ignore) {
                // Skip bad entries or missing classes.
            }
        }
        return result;
    }

    private static <T> List<Class<? extends T>> loadTypes(@Nonnull String typeFqName, @Nonnull Class<T> expectedType) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = TypeDiscovery.class.getClassLoader();
        }
        String resourceName = INDEX_DIRECTORY + typeFqName + ".idx";
        List<String> classNames = loadResourceLines(classLoader, resourceName);
        if (classNames.isEmpty()) {
            return List.of();
        }
        List<Class<? extends T>> result = new ArrayList<>();
        for (String fqClassName : new LinkedHashSet<>(classNames)) {
            try {
                Class<?> cls = Class.forName(fqClassName, false, classLoader);
                if (expectedType.isAssignableFrom(cls)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends T> cast = (Class<? extends T>) cls;
                    result.add(cast);
                }
            } catch (Throwable ignore) {
                // Skip bad entries or missing classes.
            }
        }
        return result;
    }

    private static List<String> loadResourceLines(@Nonnull ClassLoader classLoader, @Nonnull String resourceName) {
        try {
            Enumeration<URL> resources = classLoader.getResources(resourceName);
            if (!resources.hasMoreElements()) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(url.openStream(), java.nio.charset.StandardCharsets.UTF_8)
                )) {
                    reader.lines()
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .forEach(lines::add);
                }
            }
            return lines;
        } catch (IOException e) {
            return List.of();
        }
    }
}
