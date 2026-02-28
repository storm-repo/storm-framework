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

import static java.sql.Types.BIGINT;
import static java.sql.Types.BINARY;
import static java.sql.Types.BIT;
import static java.sql.Types.BLOB;
import static java.sql.Types.BOOLEAN;
import static java.sql.Types.CHAR;
import static java.sql.Types.CLOB;
import static java.sql.Types.DATE;
import static java.sql.Types.DECIMAL;
import static java.sql.Types.DOUBLE;
import static java.sql.Types.FLOAT;
import static java.sql.Types.INTEGER;
import static java.sql.Types.LONGVARBINARY;
import static java.sql.Types.LONGVARCHAR;
import static java.sql.Types.NCHAR;
import static java.sql.Types.NCLOB;
import static java.sql.Types.NUMERIC;
import static java.sql.Types.NVARCHAR;
import static java.sql.Types.OTHER;
import static java.sql.Types.REAL;
import static java.sql.Types.SMALLINT;
import static java.sql.Types.TIME;
import static java.sql.Types.TIMESTAMP;
import static java.sql.Types.TIMESTAMP_WITH_TIMEZONE;
import static java.sql.Types.TINYINT;
import static java.sql.Types.VARBINARY;
import static java.sql.Types.VARCHAR;

import jakarta.annotation.Nonnull;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Default implementation of {@link TypeCompatibility} that covers common Java-to-SQL type mappings.
 *
 * <p>Unknown Java types are treated as compatible to avoid false positives for types that are handled by custom
 * converters or database-specific extensions.</p>
 *
 * <p>Numeric cross-category conversions (e.g., Java {@code Integer} mapped to SQL {@code FLOAT}) are reported as
 * {@link Compatibility#NARROWING} rather than {@link Compatibility#INCOMPATIBLE}, since JDBC handles the conversion
 * transparently but there may be precision or range differences.</p>
 *
 * @since 1.9
 */
final class DefaultTypeCompatibility implements TypeCompatibility {

    static final DefaultTypeCompatibility INSTANCE = new DefaultTypeCompatibility();

    /** All numeric SQL type codes. */
    private static final Set<Integer> NUMERIC_SQL_TYPES = Set.of(
            TINYINT, SMALLINT, INTEGER, BIGINT, FLOAT, DOUBLE, REAL, DECIMAL, NUMERIC);

    /** Java types that are numeric. */
    private static final Set<Class<?>> NUMERIC_JAVA_TYPES = Set.of(
            byte.class, Byte.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class,
            BigDecimal.class, BigInteger.class);

    private final Map<Class<?>, Set<Integer>> compatibilityMap;

    DefaultTypeCompatibility() {
        Map<Class<?>, Set<Integer>> map = new HashMap<>();

        // Boolean types.
        Set<Integer> booleanTypes = Set.of(BIT, BOOLEAN, TINYINT, SMALLINT, INTEGER);
        map.put(boolean.class, booleanTypes);
        map.put(Boolean.class, booleanTypes);

        // Byte types.
        Set<Integer> byteTypes = Set.of(TINYINT, SMALLINT, INTEGER, DECIMAL, NUMERIC);
        map.put(byte.class, byteTypes);
        map.put(Byte.class, byteTypes);

        // Short types.
        Set<Integer> shortTypes = Set.of(TINYINT, SMALLINT, INTEGER, DECIMAL, NUMERIC);
        map.put(short.class, shortTypes);
        map.put(Short.class, shortTypes);

        // Integer types.
        Set<Integer> intTypes = Set.of(TINYINT, SMALLINT, INTEGER, BIGINT, DECIMAL, NUMERIC);
        map.put(int.class, intTypes);
        map.put(Integer.class, intTypes);

        // Long types.
        Set<Integer> longTypes = Set.of(INTEGER, BIGINT, DECIMAL, NUMERIC);
        map.put(long.class, longTypes);
        map.put(Long.class, longTypes);

        // Float types.
        Set<Integer> floatTypes = Set.of(REAL, FLOAT, DOUBLE, DECIMAL, NUMERIC);
        map.put(float.class, floatTypes);
        map.put(Float.class, floatTypes);

        // Double types.
        Set<Integer> doubleTypes = Set.of(FLOAT, DOUBLE, DECIMAL, NUMERIC, REAL);
        map.put(double.class, doubleTypes);
        map.put(Double.class, doubleTypes);

        // String types.
        map.put(String.class, Set.of(CHAR, VARCHAR, LONGVARCHAR, NCHAR, NVARCHAR, CLOB, NCLOB));

        // Date/time types.
        map.put(LocalDate.class, Set.of(DATE));
        map.put(LocalTime.class, Set.of(TIME));
        map.put(LocalDateTime.class, Set.of(TIMESTAMP, TIMESTAMP_WITH_TIMEZONE));
        map.put(Instant.class, Set.of(TIMESTAMP, TIMESTAMP_WITH_TIMEZONE));
        map.put(OffsetDateTime.class, Set.of(TIMESTAMP, TIMESTAMP_WITH_TIMEZONE));
        map.put(ZonedDateTime.class, Set.of(TIMESTAMP, TIMESTAMP_WITH_TIMEZONE));

        // UUID type: typically stored as CHAR/VARCHAR, BINARY, or native UUID (OTHER).
        map.put(UUID.class, Set.of(CHAR, VARCHAR, BINARY, VARBINARY, OTHER));

        // BigDecimal/BigInteger types.
        map.put(BigDecimal.class, Set.of(DECIMAL, NUMERIC, FLOAT, DOUBLE, REAL, INTEGER, BIGINT, TINYINT, SMALLINT));
        map.put(BigInteger.class, Set.of(DECIMAL, NUMERIC, BIGINT, INTEGER));

        // Binary types (ByteBuffer is always hydrated as read-only).
        map.put(ByteBuffer.class, Set.of(BINARY, VARBINARY, LONGVARBINARY, BLOB));

        this.compatibilityMap = Map.copyOf(map);
    }

    @Override
    public Compatibility check(@Nonnull Class<?> javaType, int sqlType, @Nonnull String sqlTypeName) {
        // Enum types can be stored as character or integer columns.
        if (javaType.isEnum()) {
            return Set.of(CHAR, VARCHAR, NCHAR, NVARCHAR, TINYINT, SMALLINT, INTEGER).contains(sqlType)
                    ? Compatibility.COMPATIBLE
                    : Compatibility.INCOMPATIBLE;
        }

        Set<Integer> compatibleTypes = compatibilityMap.get(javaType);
        if (compatibleTypes == null) {
            // Unknown type: skip validation to avoid false positives (may use a converter).
            return Compatibility.COMPATIBLE;
        }
        if (compatibleTypes.contains(sqlType)) {
            return Compatibility.COMPATIBLE;
        }

        // Special case: UUID stored as native database type (reported as OTHER with type name "uuid").
        if (javaType == UUID.class && sqlType == OTHER && sqlTypeName.equalsIgnoreCase("uuid")) {
            return Compatibility.COMPATIBLE;
        }

        // Numeric cross-category: JDBC handles the conversion, but precision or range may differ.
        if (NUMERIC_JAVA_TYPES.contains(javaType) && NUMERIC_SQL_TYPES.contains(sqlType)) {
            return Compatibility.NARROWING;
        }

        return Compatibility.INCOMPATIBLE;
    }
}
