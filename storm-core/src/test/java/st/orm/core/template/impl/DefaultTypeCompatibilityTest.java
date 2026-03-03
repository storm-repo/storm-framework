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
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import st.orm.core.template.impl.TypeCompatibility.Compatibility;

/**
 * Tests for {@link DefaultTypeCompatibility} covering all type mapping categories:
 * enum types, numeric cross-category conversions, unknown types, and all registered
 * Java-to-SQL type mappings.
 */
class DefaultTypeCompatibilityTest {

    private final DefaultTypeCompatibility compatibility = DefaultTypeCompatibility.INSTANCE;

    // Enum type tests

    enum TestEnum { VALUE_A, VALUE_B }

    @Test
    void testEnumWithCharIsCompatible() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(TestEnum.class, CHAR, "CHAR"));
    }

    @Test
    void testEnumWithVarcharIsCompatible() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(TestEnum.class, VARCHAR, "VARCHAR"));
    }

    @Test
    void testEnumWithNcharIsCompatible() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(TestEnum.class, NCHAR, "NCHAR"));
    }

    @Test
    void testEnumWithNvarcharIsCompatible() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(TestEnum.class, NVARCHAR, "NVARCHAR"));
    }

    @Test
    void testEnumWithTinyintIsCompatible() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(TestEnum.class, TINYINT, "TINYINT"));
    }

    @Test
    void testEnumWithSmallintIsCompatible() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(TestEnum.class, SMALLINT, "SMALLINT"));
    }

    @Test
    void testEnumWithIntegerIsCompatible() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(TestEnum.class, INTEGER, "INTEGER"));
    }

    @Test
    void testEnumWithBlobIsIncompatible() {
        assertEquals(Compatibility.INCOMPATIBLE, compatibility.check(TestEnum.class, BLOB, "BLOB"));
    }

    @Test
    void testEnumWithTimestampIsIncompatible() {
        assertEquals(Compatibility.INCOMPATIBLE, compatibility.check(TestEnum.class, TIMESTAMP, "TIMESTAMP"));
    }

    // Unknown Java type tests

    @Test
    void testUnknownJavaTypeIsCompatible() {
        // Unknown Java types should be treated as compatible to avoid false positives.
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(Object.class, VARCHAR, "VARCHAR"));
    }

    @Test
    void testCustomClassIsCompatible() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(DefaultTypeCompatibilityTest.class, INTEGER, "INTEGER"));
    }

    // Boolean type tests

    @Test
    void testPrimitiveBooleanWithBooleanType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(boolean.class, BOOLEAN, "BOOLEAN"));
    }

    @Test
    void testBoxedBooleanWithBitType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(Boolean.class, BIT, "BIT"));
    }

    @Test
    void testBooleanWithTinyintType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(boolean.class, TINYINT, "TINYINT"));
    }

    @Test
    void testBooleanWithVarcharIsIncompatible() {
        assertEquals(Compatibility.INCOMPATIBLE, compatibility.check(boolean.class, VARCHAR, "VARCHAR"));
    }

    // Integer type tests

    @Test
    void testIntWithIntegerType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(int.class, INTEGER, "INTEGER"));
    }

    @Test
    void testIntegerWithBigintType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(Integer.class, BIGINT, "BIGINT"));
    }

    @Test
    void testIntWithDecimalType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(int.class, DECIMAL, "DECIMAL"));
    }

    @Test
    void testIntWithFloatTypeIsNarrowing() {
        // Integer to FLOAT is a numeric cross-category conversion.
        assertEquals(Compatibility.NARROWING, compatibility.check(int.class, FLOAT, "FLOAT"));
    }

    @Test
    void testIntWithDoubleTypeIsNarrowing() {
        assertEquals(Compatibility.NARROWING, compatibility.check(int.class, DOUBLE, "DOUBLE"));
    }

    @Test
    void testIntWithVarcharIsIncompatible() {
        assertEquals(Compatibility.INCOMPATIBLE, compatibility.check(int.class, VARCHAR, "VARCHAR"));
    }

    // Long type tests

    @Test
    void testLongWithBigintType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(long.class, BIGINT, "BIGINT"));
    }

    @Test
    void testLongWithIntegerType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(Long.class, INTEGER, "INTEGER"));
    }

    @Test
    void testLongWithFloatIsNarrowing() {
        assertEquals(Compatibility.NARROWING, compatibility.check(long.class, FLOAT, "FLOAT"));
    }

    // Float type tests

    @Test
    void testFloatWithRealType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(float.class, REAL, "REAL"));
    }

    @Test
    void testFloatWithDoubleType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(Float.class, DOUBLE, "DOUBLE"));
    }

    @Test
    void testFloatWithIntegerIsNarrowing() {
        assertEquals(Compatibility.NARROWING, compatibility.check(float.class, INTEGER, "INTEGER"));
    }

    // Double type tests

    @Test
    void testDoubleWithDoubleType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(double.class, DOUBLE, "DOUBLE"));
    }

    @Test
    void testDoubleWithRealType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(Double.class, REAL, "REAL"));
    }

    @Test
    void testDoubleWithBigintIsNarrowing() {
        assertEquals(Compatibility.NARROWING, compatibility.check(double.class, BIGINT, "BIGINT"));
    }

    // Byte type tests

    @Test
    void testByteWithTinyintType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(byte.class, TINYINT, "TINYINT"));
    }

    @Test
    void testByteWithFloatIsNarrowing() {
        assertEquals(Compatibility.NARROWING, compatibility.check(Byte.class, FLOAT, "FLOAT"));
    }

    // Short type tests

    @Test
    void testShortWithSmallintType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(short.class, SMALLINT, "SMALLINT"));
    }

    @Test
    void testShortWithDoubleIsNarrowing() {
        assertEquals(Compatibility.NARROWING, compatibility.check(Short.class, DOUBLE, "DOUBLE"));
    }

    // String type tests

    @Test
    void testStringWithVarcharType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(String.class, VARCHAR, "VARCHAR"));
    }

    @Test
    void testStringWithCharType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(String.class, CHAR, "CHAR"));
    }

    @Test
    void testStringWithClobType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(String.class, CLOB, "CLOB"));
    }

    @Test
    void testStringWithNclobType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(String.class, NCLOB, "NCLOB"));
    }

    @Test
    void testStringWithLongvarcharType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(String.class, LONGVARCHAR, "LONGVARCHAR"));
    }

    @Test
    void testStringWithIntegerIsIncompatible() {
        assertEquals(Compatibility.INCOMPATIBLE, compatibility.check(String.class, INTEGER, "INTEGER"));
    }

    // Date/time type tests

    @Test
    void testLocalDateWithDateType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(LocalDate.class, DATE, "DATE"));
    }

    @Test
    void testLocalDateWithTimestampIsIncompatible() {
        assertEquals(Compatibility.INCOMPATIBLE, compatibility.check(LocalDate.class, TIMESTAMP, "TIMESTAMP"));
    }

    @Test
    void testLocalTimeWithTimeType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(LocalTime.class, TIME, "TIME"));
    }

    @Test
    void testLocalDateTimeWithTimestampType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(LocalDateTime.class, TIMESTAMP, "TIMESTAMP"));
    }

    @Test
    void testLocalDateTimeWithTimestampWithTimezoneType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(LocalDateTime.class, TIMESTAMP_WITH_TIMEZONE, "TIMESTAMP_WITH_TIMEZONE"));
    }

    @Test
    void testInstantWithTimestampType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(Instant.class, TIMESTAMP, "TIMESTAMP"));
    }

    @Test
    void testOffsetDateTimeWithTimestampType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(OffsetDateTime.class, TIMESTAMP, "TIMESTAMP"));
    }

    @Test
    void testZonedDateTimeWithTimestampType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(ZonedDateTime.class, TIMESTAMP, "TIMESTAMP"));
    }

    // UUID type tests

    @Test
    void testUuidWithVarcharType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(UUID.class, VARCHAR, "VARCHAR"));
    }

    @Test
    void testUuidWithBinaryType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(UUID.class, BINARY, "BINARY"));
    }

    @Test
    void testUuidWithOtherTypeNameUuid() {
        // Special case: UUID stored as native database type (OTHER with type name "uuid").
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(UUID.class, OTHER, "uuid"));
    }

    @Test
    void testUuidWithOtherTypeNameUuidCaseInsensitive() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(UUID.class, OTHER, "UUID"));
    }

    @Test
    void testUuidWithOtherTypeNotUuidIsCompatible() {
        // OTHER type with a non-uuid type name is still treated as compatible via the OTHER check.
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(UUID.class, OTHER, "json"));
    }

    @Test
    void testUuidWithIntegerIsIncompatible() {
        assertEquals(Compatibility.INCOMPATIBLE, compatibility.check(UUID.class, INTEGER, "INTEGER"));
    }

    // BigDecimal type tests

    @Test
    void testBigDecimalWithDecimalType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(BigDecimal.class, DECIMAL, "DECIMAL"));
    }

    @Test
    void testBigDecimalWithNumericType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(BigDecimal.class, NUMERIC, "NUMERIC"));
    }

    @Test
    void testBigDecimalWithIntegerType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(BigDecimal.class, INTEGER, "INTEGER"));
    }

    @Test
    void testBigDecimalWithVarcharIsIncompatible() {
        assertEquals(Compatibility.INCOMPATIBLE, compatibility.check(BigDecimal.class, VARCHAR, "VARCHAR"));
    }

    // BigInteger type tests

    @Test
    void testBigIntegerWithBigintType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(BigInteger.class, BIGINT, "BIGINT"));
    }

    @Test
    void testBigIntegerWithVarcharIsIncompatible() {
        assertEquals(Compatibility.INCOMPATIBLE, compatibility.check(BigInteger.class, VARCHAR, "VARCHAR"));
    }

    // ByteBuffer type tests

    @Test
    void testByteBufferWithBinaryType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(ByteBuffer.class, BINARY, "BINARY"));
    }

    @Test
    void testByteBufferWithVarbinaryType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(ByteBuffer.class, VARBINARY, "VARBINARY"));
    }

    @Test
    void testByteBufferWithLongvarbinaryType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(ByteBuffer.class, LONGVARBINARY, "LONGVARBINARY"));
    }

    @Test
    void testByteBufferWithBlobType() {
        assertEquals(Compatibility.COMPATIBLE, compatibility.check(ByteBuffer.class, BLOB, "BLOB"));
    }

    @Test
    void testByteBufferWithVarcharIsIncompatible() {
        assertEquals(Compatibility.INCOMPATIBLE, compatibility.check(ByteBuffer.class, VARCHAR, "VARCHAR"));
    }
}
