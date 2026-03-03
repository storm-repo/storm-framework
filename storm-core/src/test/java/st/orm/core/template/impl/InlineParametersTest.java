package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.TemplateString.raw;

import org.junit.jupiter.api.Test;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.PK;
import st.orm.core.template.Sql;
import st.orm.core.template.SqlTemplate;
import st.orm.core.template.SqlTemplateException;

/**
 * Tests for inline parameter rendering in {@link TemplateProcessor.TemplateCompilerImpl},
 * specifically covering the toLiteral method which converts Java values to SQL literals.
 * These tests exercise the previously uncovered inline parameter paths.
 */
class InlineParametersTest {

    @DbTable("test_entity")
    record TestEntity(@PK Integer id, String name) implements Entity<Integer> {}

    /**
     * SqlTemplate with inline parameters enabled to trigger toLiteral paths.
     */
    private static final SqlTemplate INLINE_TEMPLATE = SqlTemplate.PS.withInlineParameters(true);

    // toLiteral: null value

    @Test
    void testInlineNullParameter() throws SqlTemplateException {
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE name = \0", (Object) null));
        assertNotNull(sql);
        assertTrue(sql.statement().contains("NULL"));
    }

    // toLiteral: Integer value

    @Test
    void testInlineIntegerParameter() throws SqlTemplateException {
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE id = \0", 42));
        assertNotNull(sql);
        assertTrue(sql.statement().contains("42"));
    }

    // toLiteral: Long value

    @Test
    void testInlineLongParameter() throws SqlTemplateException {
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE id = \0", 123456789L));
        assertNotNull(sql);
        assertTrue(sql.statement().contains("123456789"));
    }

    // toLiteral: Short value

    @Test
    void testInlineShortParameter() throws SqlTemplateException {
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE id = \0", (short) 7));
        assertNotNull(sql);
        assertTrue(sql.statement().contains("7"));
    }

    // toLiteral: Float value

    @Test
    void testInlineFloatParameter() throws SqlTemplateException {
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE id = \0", 3.14f));
        assertNotNull(sql);
        assertTrue(sql.statement().contains("3.14"));
    }

    // toLiteral: Double value

    @Test
    void testInlineDoubleParameter() throws SqlTemplateException {
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE id = \0", 2.718));
        assertNotNull(sql);
        assertTrue(sql.statement().contains("2.718"));
    }

    // toLiteral: Byte value

    @Test
    void testInlineByteParameter() throws SqlTemplateException {
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE id = \0", (byte) 5));
        assertNotNull(sql);
        assertTrue(sql.statement().contains("5"));
    }

    // toLiteral: Boolean true

    @Test
    void testInlineBooleanTrueParameter() throws SqlTemplateException {
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE id = \0", true));
        assertNotNull(sql);
        assertTrue(sql.statement().contains("TRUE"));
    }

    // toLiteral: Boolean false

    @Test
    void testInlineBooleanFalseParameter() throws SqlTemplateException {
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE id = \0", false));
        assertNotNull(sql);
        assertTrue(sql.statement().contains("FALSE"));
    }

    // toLiteral: String value

    @Test
    void testInlineStringParameter() throws SqlTemplateException {
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE name = \0", "hello"));
        assertNotNull(sql);
        assertTrue(sql.statement().contains("'hello'"));
    }

    // toLiteral: String with single quote (escape)

    @Test
    void testInlineStringWithSingleQuoteEscape() throws SqlTemplateException {
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE name = \0", "it's"));
        assertNotNull(sql);
        assertTrue(sql.statement().contains("'it''s'"));
    }

    // toLiteral: String with backslash (escape)

    @Test
    void testInlineStringWithBackslashEscape() throws SqlTemplateException {
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE name = \0", "path\\to"));
        assertNotNull(sql);
        assertTrue(sql.statement().contains("'path\\\\to'"));
    }

    // toLiteral: Enum value

    enum TestStatus { ACTIVE, INACTIVE }

    @Test
    void testInlineEnumParameter() throws SqlTemplateException {
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE name = \0", TestStatus.ACTIVE));
        assertNotNull(sql);
        assertTrue(sql.statement().contains("'ACTIVE'"));
    }

    // toLiteral: java.sql.Date value

    @Test
    void testInlineSqlDateParameter() throws SqlTemplateException {
        java.sql.Date date = java.sql.Date.valueOf("2024-01-15");
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE name = \0", date));
        assertNotNull(sql);
        assertTrue(sql.statement().contains("'2024-01-15'"));
    }

    // toLiteral: java.sql.Time value

    @Test
    void testInlineSqlTimeParameter() throws SqlTemplateException {
        java.sql.Time time = java.sql.Time.valueOf("14:30:00");
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE name = \0", time));
        assertNotNull(sql);
        assertTrue(sql.statement().contains("'14:30:00'"));
    }

    // toLiteral: java.sql.Timestamp value

    @Test
    void testInlineSqlTimestampParameter() throws SqlTemplateException {
        java.sql.Timestamp timestamp = java.sql.Timestamp.valueOf("2024-01-15 14:30:00");
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE name = \0", timestamp));
        assertNotNull(sql);
        assertTrue(sql.statement().contains("'2024-01-15 14:30:00"));
    }

    // toLiteral: default/unknown type falls through to toString()

    @Test
    void testInlineCustomObjectParameter() throws SqlTemplateException {
        Object customValue = new Object() {
            @Override
            public String toString() {
                return "custom_value";
            }
        };
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE name = \0", customValue));
        assertNotNull(sql);
        assertTrue(sql.statement().contains("'custom_value'"));
    }

    // Inline collection (mapArgs with inline=true)

    @Test
    void testInlineCollectionParameter() throws SqlTemplateException {
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE id IN (\0)", java.util.List.of(1, 2, 3)));
        assertNotNull(sql);
        String statement = sql.statement();
        assertTrue(statement.contains("1"));
        assertTrue(statement.contains("2"));
        assertTrue(statement.contains("3"));
    }

    // Inline array parameter

    @Test
    void testInlineArrayParameter() throws SqlTemplateException {
        Sql sql = INLINE_TEMPLATE.process(raw("SELECT * FROM test_entity WHERE id IN (\0)", (Object) new Object[]{"a", "b"}));
        assertNotNull(sql);
        String statement = sql.statement();
        assertTrue(statement.contains("'a'"));
        assertTrue(statement.contains("'b'"));
    }
}
