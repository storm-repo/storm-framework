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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.PersistenceException;
import st.orm.core.template.SqlTemplateException;

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
            throw new PersistenceException("Type must be an enum: %s.".formatted(type.getName()));
        }
        if (columnCount == 1) {
            return Optional.of(new ObjectMapper<>() {
                @Override
                public Class<?>[] getParameterTypes() {
                    return new Class<?>[] { type };
                }

                @SuppressWarnings("unchecked")
                @Override
                public T newInstance(@Nonnull Object[] args) throws SqlTemplateException {
                    Object arg = args[0];
                    if (arg == null) {
                        return null;
                    }
                    if (arg instanceof String s) {
                        return (T) getEnumFromName(type, s);
                    }
                    if (arg instanceof Integer n) {
                        return (T) getEnumFromOrdinal(type, n);
                    }
                    throw new SqlTemplateException("Invalid value '%s' for enum %s.".formatted(arg, type.getName()));
                }
            });
        }
        return empty();
    }

    private static Enum<?> getEnumFromName(@Nonnull Class<?> enumType, @Nonnull String name)
            throws SqlTemplateException {
        try {
            //noinspection unchecked,rawtypes
            return Enum.valueOf((Class<? extends Enum>) enumType, name);
        } catch (IllegalArgumentException e) {
            throw new SqlTemplateException("No enum constant %s for value '%s'.".formatted(enumType.getName(), name), e);
        }
    }

    private static Enum<?> getEnumFromOrdinal(@Nonnull Class<?> enumType, @Nonnull Integer ordinal)
            throws SqlTemplateException {
        Enum<?>[] enumConstants = (Enum<?>[]) enumType.getEnumConstants();
        if (ordinal >= 0 && ordinal < enumConstants.length) {
            return enumConstants[ordinal];
        } else {
            throw new SqlTemplateException("Invalid ordinal '%d' for enum %s.".formatted(ordinal, enumType.getName()));
        }
    }
}