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

import static java.util.Optional.empty;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Factory for creating instances of leaf value types from a single-column result.
 *
 * <p>Leaf value types are immutable types that the column reader produces directly
 * (see {@code QueryImpl.readColumnValue}). For these types, mapping reduces to passing the column value through
 * as-is — no reflection into a constructor is needed. This avoids {@link java.lang.reflect.InaccessibleObjectException}
 * for JDK value types whose only single-arg constructors are private (e.g. {@link UUID#UUID(byte[])}).
 */
final class ValueMapper {

    private static final Set<Class<?>> VALUE_TYPES = Set.of(
            // Boxed primitives — readColumnValue returns the boxed value directly via rs.getX.
            Boolean.class, Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class,
            // Common scalar types.
            String.class, BigDecimal.class, ByteBuffer.class, UUID.class,
            // Legacy date/time.
            java.util.Date.class, Calendar.class,
            // JDBC date/time.
            java.sql.Date.class, Time.class, Timestamp.class,
            // java.time.
            LocalDateTime.class, LocalDate.class, LocalTime.class,
            Instant.class, OffsetDateTime.class, ZonedDateTime.class
    );

    private ValueMapper() {
    }

    /**
     * Returns true if the specified type is a leaf value type handled by this mapper.
     */
    static boolean isValueType(Class<?> type) {
        return VALUE_TYPES.contains(type);
    }

    /**
     * Returns a pass-through factory for the specified leaf value type, or empty if not applicable.
     *
     * @param columnCount the number of columns; only single-column results are supported.
     * @param type the leaf value type to map to.
     * @return an {@link ObjectMapper} that returns the single column value as-is, or empty.
     */
    static <T> Optional<ObjectMapper<T>> getFactory(int columnCount, Class<T> type) {
        if (columnCount != 1 || !isValueType(type)) {
            return empty();
        }
        return Optional.of(new ObjectMapper<>() {
            @Override
            public Class<?>[] getParameterTypes() {
                return new Class<?>[] { type };
            }

            @SuppressWarnings("unchecked")
            @Override
            public T newInstance(Object[] args) {
                return (T) args[0];
            }
        });
    }
}
