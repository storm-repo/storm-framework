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
import st.orm.spi.ORMConverter;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.SqlTemplateException;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;
import java.util.function.Function;

public class JsonORMConverterImpl implements ORMConverter {
    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private final RecordComponent component;
    private final TypeReference<?> typeReference;

    public JsonORMConverterImpl(@Nonnull RecordComponent component, @Nonnull TypeReference<?> typeReference) {
        this.component = component;
        this.typeReference = typeReference;
    }

    @Override
    public List<Class<?>> getParameterTypes() {
        return List.of(String.class);
    }

    @Override
    public Object convert(@Nonnull Object[] args) throws SqlTemplateException {
        try {
            return new ObjectMapper().readValue((String) args[0], typeReference);
        } catch (JsonProcessingException e) {
            throw new SqlTemplateException(e);
        }
    }

    @Override
    public List<String> getColumns(@Nonnull Function<RecordComponent, String> columnNameResolver) {
        return List.of(columnNameResolver.apply(component));
    }

    @Override
    public List<Object> getValues(@Nonnull Record record) throws SqlTemplateException {
        try {
            return List.of(new ObjectMapper().writeValueAsString(REFLECTION.invokeComponent(component, record)));
        } catch (Throwable e) {
            throw new SqlTemplateException(e);
        }
    }
}
