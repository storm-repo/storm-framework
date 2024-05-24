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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.persistence.PersistenceException;

import java.util.Optional;

import static java.util.Optional.empty;

/**
 * Factory for creating instances of a specific type.
 */
final class EnumMapper {

    private EnumMapper() {
    }

    /**
     * Returns a factory for creating instances of the specified type.
     *
     * @param columnCount the number of columns to use as constructor arguments.
     * @param type the type of the instance to create.
     * @return a factory for creating instances of the specified type.
     * @param <T> the type of the instance to create.
     */
    static <T> Optional<ObjectMapper<T>> getFactory(int columnCount, @Nonnull Class<T> type) {
        if (!type.isEnum()) {
            throw new PersistenceException(STR."Type must be an enum: \{type.getName()}.");
        }
        if (columnCount == 1) {
            return Optional.of(new ObjectMapper<T>() {
                @Override
                public Class<?>[] getParameterTypes() {
                    return new Class<?>[] { type };
                }

                @SuppressWarnings("unchecked")
                @Override
                public T newInstance(@Nonnull Object[] args) {
                    return (T) args[0];
                }
            });
        }
        return empty();
    }
}