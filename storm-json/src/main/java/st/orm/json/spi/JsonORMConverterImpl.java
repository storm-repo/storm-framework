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
package st.orm.json.spi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.json.Json;
import st.orm.spi.Name;
import st.orm.spi.ORMConverter;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.SqlTemplateException;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

public class JsonORMConverterImpl implements ORMConverter {
    private static final ORMReflection REFLECTION = Providers.getORMReflection();
    private static final Map<Json, ObjectMapper> OBJECT_MAPPER = new ConcurrentHashMap<>();

    private final RecordComponent component;
    private final TypeReference<?> typeReference;
    private final ObjectMapper mapper;

    public JsonORMConverterImpl(@Nonnull RecordComponent component, @Nonnull TypeReference<?> typeReference, @Nonnull Json json) {
        this.component = component;
        this.typeReference = typeReference;
        this.mapper = OBJECT_MAPPER.computeIfAbsent(json, _ -> {
            var mapper = new ObjectMapper();
            if (!json.failOnUnknown()) {
                mapper.disable(FAIL_ON_UNKNOWN_PROPERTIES);
            }
            if (!json.failOnMissing()) {
                mapper.disable(FAIL_ON_MISSING_CREATOR_PROPERTIES);
            }
            return mapper;
        });
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
