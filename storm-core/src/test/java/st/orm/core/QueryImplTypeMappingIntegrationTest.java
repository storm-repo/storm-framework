package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.TemplateString.raw;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.core.model.City;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.Query;

/**
 * Integration tests targeting the readColumnValue type-mapping switch expression in QueryImpl
 * and other Query interface methods. Each record type is crafted to exercise specific type
 * mappings that are not covered by existing tests.
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class QueryImplTypeMappingIntegrationTest {

    @Autowired
    private DataSource dataSource;

    // -----------------------------------------------------------------------
    // Integer type mapping via record
    // -----------------------------------------------------------------------

    record IntegerResult(Integer value) {}

    @Test
    public void testIntegerTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        IntegerResult result = orm.query(raw("SELECT 42")).getSingleResult(IntegerResult.class);
        assertEquals(42, result.value());
    }

    // -----------------------------------------------------------------------
    // Long type mapping via record
    // -----------------------------------------------------------------------

    record LongResult(Long value) {}

    @Test
    public void testLongTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        LongResult result = orm.query(raw("SELECT CAST(123456789 AS BIGINT)")).getSingleResult(LongResult.class);
        assertEquals(123456789L, result.value());
    }

    // -----------------------------------------------------------------------
    // Short type mapping via record
    // -----------------------------------------------------------------------

    record ShortResult(Short value) {}

    @Test
    public void testShortTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        ShortResult result = orm.query(raw("SELECT CAST(7 AS SMALLINT)")).getSingleResult(ShortResult.class);
        assertEquals((short) 7, result.value());
    }

    // -----------------------------------------------------------------------
    // Float type mapping via record
    // -----------------------------------------------------------------------

    record FloatResult(Float value) {}

    @Test
    public void testFloatTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        FloatResult result = orm.query(raw("SELECT CAST(1.5 AS REAL)")).getSingleResult(FloatResult.class);
        assertEquals(1.5f, result.value(), 0.001f);
    }

    // -----------------------------------------------------------------------
    // Double type mapping via record
    // -----------------------------------------------------------------------

    record DoubleResult(Double value) {}

    @Test
    public void testDoubleTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        DoubleResult result = orm.query(raw("SELECT CAST(3.14 AS DOUBLE PRECISION)")).getSingleResult(DoubleResult.class);
        assertEquals(3.14, result.value(), 0.001);
    }

    // -----------------------------------------------------------------------
    // Byte type mapping via record
    // -----------------------------------------------------------------------

    record ByteResult(Byte value) {}

    @Test
    public void testByteTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        ByteResult result = orm.query(raw("SELECT CAST(42 AS TINYINT)")).getSingleResult(ByteResult.class);
        assertEquals((byte) 42, result.value());
    }

    // -----------------------------------------------------------------------
    // Boolean type mapping via record
    // -----------------------------------------------------------------------

    record BooleanResult(Boolean value) {}

    @Test
    public void testBooleanTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        BooleanResult result = orm.query(raw("SELECT TRUE")).getSingleResult(BooleanResult.class);
        assertTrue(result.value());
    }

    // -----------------------------------------------------------------------
    // String type mapping via record
    // -----------------------------------------------------------------------

    record StringResult(String value) {}

    @Test
    public void testStringTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        // City id=1 is "Sun Paririe" based on data.sql.
        StringResult result = orm.query(raw("SELECT name FROM city WHERE id = 1")).getSingleResult(StringResult.class);
        assertEquals("Sun Paririe", result.value());
    }

    // -----------------------------------------------------------------------
    // BigDecimal type mapping via record
    // -----------------------------------------------------------------------

    record BigDecimalResult(BigDecimal value) {}

    @Test
    public void testBigDecimalTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        BigDecimalResult result = orm.query(raw("SELECT CAST(99.99 AS DECIMAL(10,2))")).getSingleResult(BigDecimalResult.class);
        assertNotNull(result.value());
        assertEquals(0, new BigDecimal("99.99").compareTo(result.value()));
    }

    // -----------------------------------------------------------------------
    // LocalDate type mapping via record
    // -----------------------------------------------------------------------

    record LocalDateResult(LocalDate value) {}

    @Test
    public void testLocalDateTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        LocalDateResult result = orm.query(raw("SELECT visit_date FROM visit WHERE id = 1")).getSingleResult(LocalDateResult.class);
        assertNotNull(result.value());
    }

    // -----------------------------------------------------------------------
    // LocalDateTime type mapping via record
    // -----------------------------------------------------------------------

    record LocalDateTimeResult(LocalDateTime value) {}

    @Test
    public void testLocalDateTimeTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        LocalDateTimeResult result = orm.query(raw("SELECT CAST('2023-06-15 10:30:00' AS TIMESTAMP)"))
                .getSingleResult(LocalDateTimeResult.class);
        assertNotNull(result.value());
        assertEquals(2023, result.value().getYear());
        assertEquals(6, result.value().getMonthValue());
        assertEquals(15, result.value().getDayOfMonth());
    }

    // -----------------------------------------------------------------------
    // LocalTime type mapping via record
    // -----------------------------------------------------------------------

    record LocalTimeResult(LocalTime value) {}

    @Test
    public void testLocalTimeTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        LocalTimeResult result = orm.query(raw("SELECT CAST('14:30:00' AS TIME)"))
                .getSingleResult(LocalTimeResult.class);
        assertNotNull(result.value());
        assertEquals(14, result.value().getHour());
        assertEquals(30, result.value().getMinute());
    }

    // -----------------------------------------------------------------------
    // Instant type mapping via record
    // -----------------------------------------------------------------------

    record InstantResult(Instant value) {}

    @Test
    public void testInstantTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        // Use "timestamp" column which is the @Version Instant field - but "timestamp" is reserved in H2.
        // Use the actual column name via a safe alias approach.
        InstantResult result = orm.query(raw("SELECT CAST('2023-06-15 10:30:00' AS TIMESTAMP)"))
                .getSingleResult(InstantResult.class);
        assertNotNull(result.value());
    }

    // -----------------------------------------------------------------------
    // OffsetDateTime type mapping via record
    // -----------------------------------------------------------------------

    record OffsetDateTimeResult(OffsetDateTime value) {}

    @Test
    public void testOffsetDateTimeTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        OffsetDateTimeResult result = orm.query(raw("SELECT CAST('2023-06-15 10:30:00' AS TIMESTAMP)"))
                .getSingleResult(OffsetDateTimeResult.class);
        assertNotNull(result.value());
        assertEquals(2023, result.value().getYear());
    }

    // -----------------------------------------------------------------------
    // ZonedDateTime type mapping via record
    // -----------------------------------------------------------------------

    record ZonedDateTimeResult(ZonedDateTime value) {}

    @Test
    public void testZonedDateTimeTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        ZonedDateTimeResult result = orm.query(raw("SELECT CAST('2023-06-15 10:30:00' AS TIMESTAMP)"))
                .getSingleResult(ZonedDateTimeResult.class);
        assertNotNull(result.value());
        assertEquals(2023, result.value().getYear());
    }

    // -----------------------------------------------------------------------
    // java.sql.Timestamp type mapping via record
    // -----------------------------------------------------------------------

    record SqlTimestampResult(java.sql.Timestamp value) {}

    @Test
    public void testSqlTimestampTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        SqlTimestampResult result = orm.query(raw("SELECT CAST('2023-06-15 10:30:00' AS TIMESTAMP)"))
                .getSingleResult(SqlTimestampResult.class);
        assertNotNull(result.value());
    }

    // -----------------------------------------------------------------------
    // java.sql.Date type mapping via record
    // -----------------------------------------------------------------------

    record SqlDateResult(java.sql.Date value) {}

    @Test
    public void testSqlDateTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        SqlDateResult result = orm.query(raw("SELECT CAST('2023-06-15' AS DATE)"))
                .getSingleResult(SqlDateResult.class);
        assertNotNull(result.value());
    }

    // -----------------------------------------------------------------------
    // java.sql.Time type mapping via record
    // -----------------------------------------------------------------------

    record SqlTimeResult(java.sql.Time value) {}

    @Test
    public void testSqlTimeTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        SqlTimeResult result = orm.query(raw("SELECT CAST('14:30:00' AS TIME)"))
                .getSingleResult(SqlTimeResult.class);
        assertNotNull(result.value());
    }

    // -----------------------------------------------------------------------
    // java.util.Date type mapping via record
    // -----------------------------------------------------------------------

    record JavaUtilDateResult(java.util.Date value) {}

    @Test
    public void testJavaUtilDateTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        JavaUtilDateResult result = orm.query(raw("SELECT CAST('2023-06-15 10:30:00' AS TIMESTAMP)"))
                .getSingleResult(JavaUtilDateResult.class);
        assertNotNull(result.value());
    }

    // -----------------------------------------------------------------------
    // Null handling for various types via records with nullable fields
    // -----------------------------------------------------------------------

    @Test
    public void testNullLocalDateMapping() {
        var orm = ORMTemplate.of(dataSource);
        LocalDateResult result = orm.query(raw("SELECT CAST(NULL AS DATE)"))
                .getSingleResult(LocalDateResult.class);
        assertNull(result.value());
    }

    @Test
    public void testNullLocalDateTimeMapping() {
        var orm = ORMTemplate.of(dataSource);
        LocalDateTimeResult result = orm.query(raw("SELECT CAST(NULL AS TIMESTAMP)"))
                .getSingleResult(LocalDateTimeResult.class);
        assertNull(result.value());
    }

    @Test
    public void testNullInstantMapping() {
        var orm = ORMTemplate.of(dataSource);
        InstantResult result = orm.query(raw("SELECT CAST(NULL AS TIMESTAMP)"))
                .getSingleResult(InstantResult.class);
        assertNull(result.value());
    }

    @Test
    public void testNullStringMapping() {
        var orm = ORMTemplate.of(dataSource);
        StringResult result = orm.query(raw("SELECT CAST(NULL AS VARCHAR)"))
                .getSingleResult(StringResult.class);
        assertNull(result.value());
    }

    @Test
    public void testNullOffsetDateTimeMapping() {
        var orm = ORMTemplate.of(dataSource);
        OffsetDateTimeResult result = orm.query(raw("SELECT CAST(NULL AS TIMESTAMP)"))
                .getSingleResult(OffsetDateTimeResult.class);
        assertNull(result.value());
    }

    @Test
    public void testNullZonedDateTimeMapping() {
        var orm = ORMTemplate.of(dataSource);
        ZonedDateTimeResult result = orm.query(raw("SELECT CAST(NULL AS TIMESTAMP)"))
                .getSingleResult(ZonedDateTimeResult.class);
        assertNull(result.value());
    }

    @Test
    public void testNullLocalTimeMapping() {
        var orm = ORMTemplate.of(dataSource);
        LocalTimeResult result = orm.query(raw("SELECT CAST(NULL AS TIME)"))
                .getSingleResult(LocalTimeResult.class);
        assertNull(result.value());
    }

    @Test
    public void testNullJavaUtilDateMapping() {
        var orm = ORMTemplate.of(dataSource);
        JavaUtilDateResult result = orm.query(raw("SELECT CAST(NULL AS TIMESTAMP)"))
                .getSingleResult(JavaUtilDateResult.class);
        assertNull(result.value());
    }

    // -----------------------------------------------------------------------
    // Multi-column record mapping via getResultList(Class)
    // -----------------------------------------------------------------------

    record CityInfo(int id, String name) {}

    @Test
    public void testMultiColumnRecordMapping() {
        var orm = ORMTemplate.of(dataSource);
        List<CityInfo> results = orm.query(raw("SELECT id, name FROM city ORDER BY id"))
                .getResultList(CityInfo.class);
        assertEquals(6, results.size());
        assertEquals(1, results.getFirst().id());
        assertEquals("Sun Paririe", results.getFirst().name());
    }

    // -----------------------------------------------------------------------
    // getResultStream() returning Object[] arrays
    // -----------------------------------------------------------------------

    @Test
    public void testGetResultStreamObjectArray() {
        var orm = ORMTemplate.of(dataSource);
        List<Object[]> results = orm.query(raw("SELECT id, name FROM city WHERE id = 1"))
                .getResultList();
        assertEquals(1, results.size());
        Object[] row = results.getFirst();
        assertEquals(2, row.length);
        assertNotNull(row[0]);
        assertNotNull(row[1]);
    }

    // -----------------------------------------------------------------------
    // Query.getSingleResult() - returning Object[]
    // -----------------------------------------------------------------------

    @Test
    public void testGetSingleResultObjectArray() {
        var orm = ORMTemplate.of(dataSource);
        Object[] row = orm.query(raw("SELECT id, name FROM city WHERE id = 1"))
                .getSingleResult();
        assertEquals(2, row.length);
    }

    // -----------------------------------------------------------------------
    // Query.getOptionalResult(Class) - present
    // -----------------------------------------------------------------------

    @Test
    public void testGetOptionalResultPresent() {
        var orm = ORMTemplate.of(dataSource);
        var result = orm.query(raw("SELECT name FROM city WHERE id = 1"))
                .getOptionalResult(StringResult.class);
        assertTrue(result.isPresent());
        assertEquals("Sun Paririe", result.get().value());
    }

    // -----------------------------------------------------------------------
    // Query.getOptionalResult(Class) - empty
    // -----------------------------------------------------------------------

    @Test
    public void testGetOptionalResultEmpty() {
        var orm = ORMTemplate.of(dataSource);
        var result = orm.query(raw("SELECT name FROM city WHERE id = 999"))
                .getOptionalResult(StringResult.class);
        assertTrue(result.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Query.getResultCount() on raw query
    // -----------------------------------------------------------------------

    @Test
    public void testRawQueryGetResultCount() {
        var orm = ORMTemplate.of(dataSource);
        long count = orm.query(raw("SELECT id FROM city"))
                .getResultCount();
        assertEquals(6, count);
    }

    // -----------------------------------------------------------------------
    // Query.toString() - exercises the toString path in QueryImpl
    // -----------------------------------------------------------------------

    @Test
    public void testQueryToString() {
        var orm = ORMTemplate.of(dataSource);
        Query query = orm.selectFrom(City.class).build();
        String toString = query.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("Query@"), "Expected toString to contain 'Query@' prefix");
    }

    // -----------------------------------------------------------------------
    // Primitive int type mapping (via record with int field)
    // -----------------------------------------------------------------------

    record PrimitiveIntResult(int value) {}

    @Test
    public void testPrimitiveIntMapping() {
        var orm = ORMTemplate.of(dataSource);
        PrimitiveIntResult result = orm.query(raw("SELECT 42")).getSingleResult(PrimitiveIntResult.class);
        assertEquals(42, result.value());
    }

    // -----------------------------------------------------------------------
    // Primitive boolean mapping via record
    // -----------------------------------------------------------------------

    record PrimitiveBooleanResult(boolean value) {}

    @Test
    public void testPrimitiveBooleanMapping() {
        var orm = ORMTemplate.of(dataSource);
        PrimitiveBooleanResult result = orm.query(raw("SELECT TRUE")).getSingleResult(PrimitiveBooleanResult.class);
        assertTrue(result.value());
    }

    // -----------------------------------------------------------------------
    // Primitive long mapping via record
    // -----------------------------------------------------------------------

    record PrimitiveLongResult(long value) {}

    @Test
    public void testPrimitiveLongMapping() {
        var orm = ORMTemplate.of(dataSource);
        PrimitiveLongResult result = orm.query(raw("SELECT CAST(123456789 AS BIGINT)")).getSingleResult(PrimitiveLongResult.class);
        assertEquals(123456789L, result.value());
    }

    // -----------------------------------------------------------------------
    // Primitive double mapping via record
    // -----------------------------------------------------------------------

    record PrimitiveDoubleResult(double value) {}

    @Test
    public void testPrimitiveDoubleMapping() {
        var orm = ORMTemplate.of(dataSource);
        PrimitiveDoubleResult result = orm.query(raw("SELECT CAST(3.14 AS DOUBLE PRECISION)")).getSingleResult(PrimitiveDoubleResult.class);
        assertEquals(3.14, result.value(), 0.001);
    }

    // -----------------------------------------------------------------------
    // Primitive short mapping via record
    // -----------------------------------------------------------------------

    record PrimitiveShortResult(short value) {}

    @Test
    public void testPrimitiveShortMapping() {
        var orm = ORMTemplate.of(dataSource);
        PrimitiveShortResult result = orm.query(raw("SELECT CAST(7 AS SMALLINT)")).getSingleResult(PrimitiveShortResult.class);
        assertEquals((short) 7, result.value());
    }

    // -----------------------------------------------------------------------
    // Primitive float mapping via record
    // -----------------------------------------------------------------------

    record PrimitiveFloatResult(float value) {}

    @Test
    public void testPrimitiveFloatMapping() {
        var orm = ORMTemplate.of(dataSource);
        PrimitiveFloatResult result = orm.query(raw("SELECT CAST(1.5 AS REAL)")).getSingleResult(PrimitiveFloatResult.class);
        assertEquals(1.5f, result.value(), 0.001f);
    }

    // -----------------------------------------------------------------------
    // Primitive byte mapping via record
    // -----------------------------------------------------------------------

    record PrimitiveByteResult(byte value) {}

    @Test
    public void testPrimitiveByteMapping() {
        var orm = ORMTemplate.of(dataSource);
        PrimitiveByteResult result = orm.query(raw("SELECT CAST(42 AS TINYINT)")).getSingleResult(PrimitiveByteResult.class);
        assertEquals((byte) 42, result.value());
    }
}
