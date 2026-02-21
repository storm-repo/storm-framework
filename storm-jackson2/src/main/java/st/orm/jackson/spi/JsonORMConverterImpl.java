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
package st.orm.jackson.spi;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import st.orm.Json;
import st.orm.core.spi.Name;
import st.orm.core.spi.ORMConverter;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.core.spi.RefFactory;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.impl.SegmentedLruCache;
import st.orm.jackson.StormModule;
import st.orm.mapping.RecordField;

/**
 * Implementation of {@link ORMConverter} that converts JSON fields to and from the database. It uses Jackson for JSON
 * serialization and deserialization.
 */
public final class JsonORMConverterImpl implements ORMConverter {
    private static final ORMReflection REFLECTION = Providers.getORMReflection();
    private static final SegmentedLruCache<CacheKey, ObjectMapper> OBJECT_MAPPER = new SegmentedLruCache<>(1024);
    private static final ThreadLocal<RefFactory> REF_FACTORY = new ThreadLocal<>();

    private final RecordField field;
    private final TypeReference<?> typeReference;
    private final ObjectMapper mapper;

    record CacheKey(@Nonnull Json json,
                    @Nullable Class<?> sealedType,
                    @Nullable Class<? extends JsonSerializer<?>> serializer,
                    @Nullable Class<? extends JsonDeserializer<?>> deserializer) {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public JsonORMConverterImpl(@Nonnull RecordField field,
                                @Nonnull TypeReference<?> typeReference,
                                @Nonnull Json json) {
        this.field = requireNonNull(field, "field");
        this.typeReference = requireNonNull(typeReference, "typeReference");
        var type = getRawType(typeReference.getType())
                .filter(Class::isSealed)
                .orElse(null);
        // Check for custom serializer/deserializer annotations.
        var serializeAnnotation = field.getAnnotation(JsonSerialize.class);
        var deserializeAnnotation = field.getAnnotation(JsonDeserialize.class);
        Class<? extends JsonSerializer<?>> serializerClass =
                serializeAnnotation != null && serializeAnnotation.using() != JsonSerializer.None.class
                        ? (Class<? extends JsonSerializer<?>>) serializeAnnotation.using()
                        : null;
        Class<? extends JsonDeserializer<?>> deserializerClass =
                deserializeAnnotation != null && deserializeAnnotation.using() != JsonDeserializer.None.class
                        ? (Class<? extends JsonDeserializer<?>>) deserializeAnnotation.using()
                        : null;
        this.mapper = OBJECT_MAPPER.getOrCompute(
                new CacheKey(requireNonNull(json, "json"), type, serializerClass, deserializerClass),
                () -> {
                    var mapper = new ObjectMapper();
                    mapper.findAndRegisterModules();
                    if (!json.failOnUnknown()) {
                        mapper.disable(FAIL_ON_UNKNOWN_PROPERTIES);
                    }
                    if (!json.failOnMissing()) {
                        mapper.disable(FAIL_ON_MISSING_CREATOR_PROPERTIES);
                    }
                    if (type != null) {
                        mapper.registerSubtypes(getPermittedSubtypes(type));
                    }
                    // Register StormModule with supplier for dynamic RefFactory resolution.
                    mapper.registerModule(new StormModule(REF_FACTORY::get));
                    // Register custom serializers/deserializers if specified.
                    if (serializerClass != null || deserializerClass != null) {
                        var customModule = new SimpleModule();
                        if (serializerClass != null) {
                            try {
                                Class<?> fieldType = getRawType(typeReference.getType()).orElse(Object.class);
                                JsonSerializer serializerInstance = serializerClass.getDeclaredConstructor().newInstance();
                                customModule.addSerializer(fieldType, serializerInstance);
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to instantiate custom serializer: " + serializerClass, e);
                            }
                        }
                        if (deserializerClass != null) {
                            try {
                                Class fieldType = getRawType(typeReference.getType()).orElse(Object.class);
                                JsonDeserializer deserializerInstance = deserializerClass.getDeclaredConstructor().newInstance();
                                customModule.addDeserializer(fieldType, deserializerInstance);
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to instantiate custom deserializer: " + deserializerClass, e);
                            }
                        }
                        mapper.registerModule(customModule);
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

    private static NamedType[] getPermittedSubtypes(@Nonnull Class<?> sealedClass) {
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
    public List<Name> getColumns(@Nonnull NameResolver nameResolver) throws SqlTemplateException {
        return List.of(nameResolver.getName(field));
    }

    @Override
    public List<Object> toDatabase(@Nullable Object record) throws SqlTemplateException {
        try {
            Object o = record == null ? null : REFLECTION.invoke(field, record);
            return singletonList(o == null ? null : mapper.writeValueAsString(o));
        } catch (Throwable e) {
            throw new SqlTemplateException(e);
        }
    }

    @Override
    public Object fromDatabase(@Nonnull Object[] values, @Nonnull RefFactory refFactory) throws SqlTemplateException {
        Object value = values[0];
        if (value == null) {
            return null;
        }
        try {
            REF_FACTORY.set(refFactory);
            return mapper.readValue((String) values[0], typeReference);
        } catch (JsonProcessingException e) {
            throw new SqlTemplateException(e);
        } finally {
            REF_FACTORY.remove();
        }
    }
}
