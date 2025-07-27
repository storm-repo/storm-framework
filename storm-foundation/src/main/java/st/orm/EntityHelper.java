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
package st.orm;

import jakarta.annotation.Nonnull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import static java.util.Optional.empty;

/**
 * Helper class for entity operations.
 */
class EntityHelper {
    private static final Map<Class<? extends Record>, Optional<Constructor<?>>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<? extends Record>, RecordComponent> PK_CACHE = new ConcurrentHashMap<>();
    private final static Map<ComponentCacheKey, Parameter> COMPONENT_PARAMETER_CACHE = new ConcurrentHashMap<>();

    private EntityHelper() {
        // Prevent instantiation.
    }

    static <ID> ID getId(Entity<ID> entity) {
        try {
            //noinspection unchecked
            return (ID) invokeComponent(getPkComponent((Class<? extends Record>) entity.getClass()), entity);
        } catch (PersistenceException e) {
            throw e; // Re-throw as PersistenceException.
        } catch (Throwable t) {
            throw new PersistenceException(t);
        }
    }

    private static Object invokeComponent(@Nonnull RecordComponent component, @Nonnull Object record) throws Throwable {
        Method method = component.getAccessor();
        //noinspection ConstantValue
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

    record ComponentCacheKey(@Nonnull Class<? extends Record> recordType, @Nonnull String componentName) {
        ComponentCacheKey(RecordComponent component) throws IllegalArgumentException {
            //noinspection unchecked
            this((Class<? extends Record>) component.getDeclaringRecord(), component.getName());
        }

        private Constructor<?> constructor() {
            return findCanonicalConstructor(recordType)
                    .orElseThrow(() -> new IllegalArgumentException("No canonical constructor found for record type: %s.".formatted(recordType.getSimpleName())));
        }
    }
    private static RecordComponent getPkComponent(@Nonnull Class<? extends Record> componentType) {
        return PK_CACHE.computeIfAbsent(componentType, ignore -> {
            var pkComponents = Stream.of(componentType.getRecordComponents())
                    .filter(c -> getParameter(c).getAnnotation(PK.class) != null)
                    .toList();
            if (pkComponents.isEmpty()) {
                throw new PersistenceException("No primary key found for %s.".formatted(componentType.getSimpleName()));
            }
            if (pkComponents.size() > 1) {
                throw new PersistenceException("Multiple primary keys found for %s: %s.".formatted(componentType.getSimpleName(), pkComponents));
            }
            return pkComponents.getFirst();
        });
    }

    private static Parameter getParameter(@Nonnull RecordComponent component) {
        return COMPONENT_PARAMETER_CACHE.computeIfAbsent(new ComponentCacheKey(component), k -> {
            //noinspection unchecked
            Class<? extends Record> recordType = (Class<? extends Record>) component.getDeclaringRecord();
            var recordComponents = recordType.getRecordComponents();
            assert k.constructor().getParameters().length == recordComponents.length;
            int index = 0;
            for (var candidate : recordComponents) {
                if (candidate.getName().equals(k.componentName())) {
                    return k.constructor().getParameters()[index];
                }
                index++;
            }
            throw new IllegalArgumentException("No parameter found for component: %s for record type: %s.".formatted(component.getName(), component.getDeclaringRecord().getSimpleName()));
        });
    }

    private static Optional<Constructor<?>> findCanonicalConstructor(@Nonnull Class<? extends Record> type) {
        assert type.isRecord();
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
}
