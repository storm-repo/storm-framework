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
package st.orm.json.spi;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.json.Json;
import st.orm.spi.Name;
import st.orm.spi.ORMConverter;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.SqlTemplateException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;

/**
 * Implementation of {@link ORMConverter} that converts JSON fields to and from the database. It uses Jackson for JSON
 * serialization and deserialization.
 */
public final class JsonORMConverterImpl implements ORMConverter {
    private static final ORMReflection REFLECTION = Providers.getORMReflection();
    private static final Map<CacheKey, ObjectMapper> OBJECT_MAPPER = new ConcurrentHashMap<>();

    private final RecordComponent component;
    private final TypeReference<?> typeReference;
    private final ObjectMapper mapper;

    record CacheKey(Class<?> sealedType, Json json) {}

    public JsonORMConverterImpl(@Nonnull RecordComponent component,
                                @Nonnull TypeReference<?> typeReference,
                                @Nonnull Json json) {
        this.component = requireNonNull(component, "component");
        this.typeReference = requireNonNull(typeReference, "typeReference");
        var type = getRawType(typeReference.getType())
                .filter(Class::isSealed)
                .orElse(null);
        this.mapper = OBJECT_MAPPER.computeIfAbsent(new CacheKey(type, requireNonNull(json, "json")), key -> {
            var mapper = new ObjectMapper();
            mapper.findAndRegisterModules();
            if (!json.failOnUnknown()) {
                mapper.disable(FAIL_ON_UNKNOWN_PROPERTIES);
            }
            if (!json.failOnMissing()) {
                mapper.disable(FAIL_ON_MISSING_CREATOR_PROPERTIES);
            }
            if (key.sealedType != null) {
                mapper.registerSubtypes(getPermittedSubtypes(key.sealedType));
            }
            return mapper;
        });
    }

    private static Optional<Class<?>> getRawType(@Nonnull Type type) {
        if (type instanceof ParameterizedType) {
            return Optional.of((Class<?>) ((ParameterizedType) type).getRawType());
        } else if (type instanceof Class<?>) {
            return Optional.of((Class<?>) type);
        } else {
            return empty();
        }
    }

    private static NamedType[] getPermittedSubtypes(Class<?> sealedClass) {
        return REFLECTION.getPermittedSubclasses(sealedClass).stream()
                .map(subclass -> {
                    JsonTypeName typeNameAnnotation = subclass.getAnnotation(JsonTypeName.class);
                    String typeName = typeNameAnnotation != null ? typeNameAnnotation.value() : subclass.getSimpleName();
                    return new NamedType(subclass, typeName);
                })
                .toArray(NamedType[]::new);
    }

    /**
     * Returns the number of parameters in the SQL template.
     *
     * <p><strong>Note:</strong> The count must match the parameter as returned by {@link #getParameterTypes()}.</p>
     *
     * @return the number of parameters.
     */
    @Override
    public int getParameterCount() {
        // The number of parameters is always 1 for this implementation, as it only handles a single JSON field.
        return 1;
    }

    /**
     * Returns the types of the parameters in the SQL template.
     *
     * @return a list of parameter types.
     */
    @Override
    public List<Class<?>> getParameterTypes() {
        return List.of(String.class);
    }

    /**
     * Returns the names of the columns that will be used in the SQL template.
     *
     * <p><strong>Note:</strong> The names must match the parameters as returned by {@link #getParameterTypes()}.</p>
     *
     * @return a list of column names.
     */
    @Override
    public List<Name> getColumns(@Nonnull NameResolver nameResolver) throws SqlTemplateException {
        return List.of(nameResolver.getName(component));
    }

    /**
     * Converts the given record to a list of values that can be used in the SQL template.
     *
     * <p><strong>Note:</strong> The values must match the parameters as returned by {@link #getParameterTypes()}.</p>
     *
     * @param record the record to convert.
     * @return the values to be used in the SQL template.
     */
    @Override
    public List<Object> toDatabase(@Nullable Record record) throws SqlTemplateException {
        try {
            Object o = record == null ? null : REFLECTION.invokeComponent(component, record);
            return singletonList(o == null ? null : mapper.writeValueAsString(o));
        } catch (Throwable e) {
            throw new SqlTemplateException(e);
        }
    }

    /**
     * Converts the given values to an object that is used in the object model.
     *
     * @param values the arguments to convert. The arguments match the parameters as returned by getParameterTypes().
     * @return the converted object.
     * @throws SqlTemplateException if an error occurs during conversion.
     */
    @Override
    public Object fromDatabase(@Nonnull Object[] values) throws SqlTemplateException {
        try {
            Object value = values[0];
            return value == null ? null : mapper.readValue((String) values[0], typeReference);
        } catch (JsonProcessingException e) {
            throw new SqlTemplateException(e);
        }
    }
}
