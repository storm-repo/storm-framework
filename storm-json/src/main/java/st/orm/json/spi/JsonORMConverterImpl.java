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
import static java.util.Optional.empty;

public class JsonORMConverterImpl implements ORMConverter {
    private static final ORMReflection REFLECTION = Providers.getORMReflection();
    private static final Map<CacheKey, ObjectMapper> OBJECT_MAPPER = new ConcurrentHashMap<>();

    private final RecordComponent component;
    private final TypeReference<?> typeReference;
    private final ObjectMapper mapper;

    record CacheKey(Class<?> sealedType, Json json) {}

    public JsonORMConverterImpl(@Nonnull RecordComponent component, @Nonnull TypeReference<?> typeReference, @Nonnull Json json) {
        this.component = component;
        this.typeReference = typeReference;
        var type = getRawType(typeReference.getType())
                .filter(Class::isSealed)
                .orElse(null);
        this.mapper = OBJECT_MAPPER.computeIfAbsent(new CacheKey(type, json), key -> {
            var mapper = new ObjectMapper();
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

    @Override
    public int getParameterCount() {
        return 1;
    }

    @Override
    public List<Class<?>> getParameterTypes() {
        return List.of(String.class);
    }

    @Override
    public Object convert(@Nonnull Object[] args) throws SqlTemplateException {
        try {
            Object arg = args[0];
            return arg == null ? null : mapper.readValue((String) args[0], typeReference);
        } catch (JsonProcessingException e) {
            throw new SqlTemplateException(e);
        }
    }

    @Override
    public List<Name> getColumns(@Nonnull NameResolver nameResolver) throws SqlTemplateException {
        return List.of(nameResolver.getName(component));
    }

    @Override
    public List<Object> getValues(@Nullable Record record) throws SqlTemplateException {
        try {
            return List.of(mapper.writeValueAsString(record == null ? null : REFLECTION.invokeComponent(component, record)));
        } catch (Throwable e) {
            throw new SqlTemplateException(e);
        }
    }
}
