package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import st.orm.core.spi.DefaultSqlDialect;
import st.orm.core.template.SqlDialect;

/**
 * Tests for {@link TableName}.
 */
public class TableNameTest {

    private final SqlDialect dialect = new DefaultSqlDialect();

    @Test
    public void testName() {
        var tableName = new TableName("users", "", false);
        assertEquals("users", tableName.name());
    }

    @Test
    public void testTable() {
        var tableName = new TableName("users", "", false);
        assertEquals("users", tableName.table());
    }

    @Test
    public void testSchema() {
        var tableName = new TableName("users", "public", false);
        assertEquals("public", tableName.schema());
    }

    @Test
    public void testEmptySchema() {
        var tableName = new TableName("users", "", false);
        assertEquals("", tableName.schema());
    }

    @Test
    public void testQualifiedWithoutSchema() {
        var tableName = new TableName("users", "", false);
        String qualified = tableName.qualified(dialect);
        assertEquals("users", qualified);
    }

    @Test
    public void testQualifiedWithSchema() {
        var tableName = new TableName("users", "myschema", false);
        String qualified = tableName.qualified(dialect);
        // Schema and table both pass through getSafeIdentifier.
        assertTrue(qualified.contains("myschema"));
        assertTrue(qualified.contains("users"));
        assertTrue(qualified.contains("."));
    }

    @Test
    public void testQualifiedWithEscape() {
        var tableName = new TableName("users", "public", true);
        String qualified = tableName.qualified(dialect);
        // With escape=true, dialect.escape() is used instead of getSafeIdentifier.
        assertTrue(qualified.contains("."));
    }

    @Test
    public void testQualifiedWithEscapeNoSchema() {
        var tableName = new TableName("users", "", true);
        String qualified = tableName.qualified(dialect);
        // No schema, so no dot.
        assertFalse(qualified.contains("."));
    }

    @Test
    public void testNullTableThrows() {
        assertThrows(NullPointerException.class, () -> new TableName(null, "", false));
    }

    @Test
    public void testNullSchemaThrows() {
        assertThrows(NullPointerException.class, () -> new TableName("users", null, false));
    }

    @Test
    public void testEmptyTableThrows() {
        assertThrows(IllegalArgumentException.class, () -> new TableName("", "", false));
    }
}
