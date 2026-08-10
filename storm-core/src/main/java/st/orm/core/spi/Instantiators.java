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

import static java.lang.Thread.currentThread;
import static java.util.Optional.ofNullable;
import static java.util.ServiceLoader.load;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import st.orm.mapping.Instantiator;

/**
 * Registry of generated {@link Instantiator} implementations, discovered through the {@link ServiceLoader}.
 *
 * <p>The metamodel generators register an instantiator per record type in
 * {@code META-INF/services/st.orm.mapping.Instantiator}. The registry is loaded once per class loader and consulted
 * by the row mapper to construct records without reflection; types without a registered instantiator fall back to
 * reflective construction.</p>
 */
public final class Instantiators {

    /**
     * Instantiators per class loader, keyed by the record type they construct. The registered instantiators and
     * record types keep their class loader reachable, so the registry is scoped to the loader's lifetime via
     * {@link ClassLoaderCache} rather than pinned for the lifetime of the JVM.
     */
    private static final ClassLoaderCache<Map<Class<?>, Instantiator<?>>> INSTANTIATOR_CACHE =
            new ClassLoaderCache<>();

    private Instantiators() {
    }

    /**
     * Returns the instantiator registered for the given record type, or {@code null} if none is registered.
     *
     * @param type the record type to find an instantiator for.
     * @param <T> the record type.
     * @return the registered instantiator, or {@code null} if the type has no generated instantiator.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> Instantiator<T> find(@Nonnull Class<T> type) {
        ClassLoader classLoader = ofNullable(currentThread().getContextClassLoader())
                .orElseGet(() -> Instantiators.class.getClassLoader());
        return (Instantiator<T>) INSTANTIATOR_CACHE
                .computeIfAbsent(classLoader, Instantiators::loadInstantiators)
                .get(type);
    }

    private static Map<Class<?>, Instantiator<?>> loadInstantiators(@Nonnull ClassLoader classLoader) {
        Map<Class<?>, Instantiator<?>> instantiators = new HashMap<>();
        for (Instantiator<?> instantiator : load(Instantiator.class, classLoader)) {
            instantiators.put(instantiator.type(), instantiator);
        }
        if (instantiators.isEmpty() && classLoader != Instantiators.class.getClassLoader()) {
            // Revert to the registry's class loader, matching the provider loading strategy.
            for (Instantiator<?> instantiator : load(Instantiator.class, Instantiators.class.getClassLoader())) {
                instantiators.put(instantiator.type(), instantiator);
            }
        }
        return Map.copyOf(instantiators);
    }
}
