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
package st.orm.jackson;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Nonnull;
import st.orm.core.spi.ORMConverter;
import st.orm.core.spi.ORMConverterProvider;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.Json;

import java.lang.reflect.RecordComponent;
import java.util.Optional;

import static java.util.Optional.empty;

/**
 * Provides an ORM converter for JSON annotated record components.
 *
 * <p>This implementation retrieves the {@link Json} annotation from the record component and creates a
 * {@link JsonORMConverterImpl} if the annotation is present.</p>
 */
public class JsonORMConverterProviderImpl implements ORMConverterProvider {
    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    /**
     * Retrieves the converter for the specified record component.
     *
     * @param component the record component for which to get the converter
     * @return an Optional containing the ORMConverter if available, or empty if not supported.
     */
    @Override
    public Optional<ORMConverter> getConverter(@Nonnull RecordComponent component) {
        Json json = REFLECTION.getAnnotation(component, Json.class);
        if (json == null) {
            return empty();
        }
        TypeReference<?> typeRef = new TypeReference<>() {
            public java.lang.reflect.Type getType() {
                return component.getGenericType();
            }
        };
        return Optional.of(new JsonORMConverterImpl(component, typeRef, json));
    }
}
