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

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Nonnull;
import st.orm.json.Json;
import st.orm.spi.ORMConverter;
import st.orm.spi.ORMConverterProvider;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;

import java.lang.reflect.RecordComponent;
import java.util.Optional;

import static java.util.Optional.empty;

public class JsonORMConverterProviderImpl implements ORMConverterProvider {
    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    @Override
    public Optional<ORMConverter> getConverter(@Nonnull RecordComponent component) {
        if (!REFLECTION.isAnnotationPresent(component, Json.class)) {
            return empty();
        }
        TypeReference<?> typeRef = new TypeReference<>() {
            public java.lang.reflect.Type getType() {
                return component.getGenericType();
            }
        };
        return Optional.of(new JsonORMConverterImpl(component, typeRef));
    }
}
