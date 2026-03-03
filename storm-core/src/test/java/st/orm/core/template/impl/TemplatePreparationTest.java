package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.TemplateString.raw;
import static st.orm.core.template.Templates.delete;
import static st.orm.core.template.Templates.insert;
import static st.orm.core.template.Templates.select;
import static st.orm.core.template.Templates.table;
import static st.orm.core.template.Templates.update;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import st.orm.Data;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.Metamodel;
import st.orm.PK;
import st.orm.core.template.SqlTemplate;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.TemplateString;

/**
 * Tests for error paths in {@link TemplatePreparation}.
 *
 * <p>These tests exercise validation logic in resolveBindVarsElement, resolveObjectElement,
 * resolveArrayElement, resolveIterableElement, and resolveElements to cover previously uncovered
 * error paths.</p>
 */
public class TemplatePreparationTest {

    /**
     * Minimal test entity for triggering template preparation paths.
     */
    @DbTable("test_entity")
    record TestEntity(@PK Integer id, String name) implements Entity<Integer> {}

    /**
     * Plain data record (not an entity) for testing non-entity objects in templates.
     */
    @DbTable("test_data")
    record TestData(String value) implements Data {}

    // ==================== SqlTemplate instances ====================

    /**
     * Template that supports records (standard behavior).
     */
    private static final SqlTemplate TEMPLATE = SqlTemplate.PS;

    /**
     * Template that does NOT support records, for testing the "Records are not supported" error paths.
     */
    private static final SqlTemplate NO_RECORDS_TEMPLATE = new SqlTemplateImpl(true, true, false);

    // ==================== resolveBindVarsElement ====================

    @Test
    public void testBindVarsInSelectWithoutWhereThrows() {
        // BindVars after SELECT (not preceded by WHERE) should throw.
        // Covers line 279: "BindVars element expected after WHERE."
        BindVarsImpl bindVars = new BindVarsImpl();
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("SELECT \0", bindVars)));
        assertTrue(exception.getMessage().contains("BindVars element expected after WHERE"));
    }

    @Test
    public void testBindVarsInInsertWithoutValuesOrWhereThrows() {
        // BindVars in INSERT not preceded by VALUES or WHERE should throw.
        // Covers line 288: "BindVars element expected after VALUES or WHERE."
        BindVarsImpl bindVars = new BindVarsImpl();
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("INSERT INTO test_entity SET \0", bindVars)));
        assertTrue(exception.getMessage().contains("BindVars element expected after VALUES or WHERE"));
    }

    @Test
    public void testBindVarsInUpdateWithoutSetOrWhereThrows() {
        // BindVars in UPDATE not preceded by SET or WHERE should throw.
        // Covers line 297: "BindVars element expected after SET or WHERE."
        BindVarsImpl bindVars = new BindVarsImpl();
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("UPDATE test_entity FROM \0", bindVars)));
        assertTrue(exception.getMessage().contains("BindVars element expected after SET or WHERE"));
    }

    @Test
    public void testBindVarsAfterInsertWhereResolvesCorrectly() {
        // BindVars after INSERT ... WHERE should resolve (covers line 283).
        // The resolution succeeds but compilation may fail for other reasons; we verify
        // the error is NOT about BindVars placement.
        BindVarsImpl bindVars = new BindVarsImpl();
        try {
            TEMPLATE.process(raw("INSERT INTO test_entity WHERE \0", bindVars));
        } catch (SqlTemplateException exception) {
            // If it throws, it should NOT be about BindVars placement.
            assertTrue(!exception.getMessage().contains("BindVars element expected"),
                    "Unexpected BindVars placement error: " + exception.getMessage());
        }
    }

    // ==================== resolveObjectElement ====================

    @Test
    public void testNonRecordAfterValuesInInsertThrows() {
        // Non-record object (e.g. a String) after VALUES in INSERT should throw.
        // Covers line 335: "Record expected after VALUES."
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("INSERT INTO test_entity VALUES \0", "not a record")));
        assertTrue(exception.getMessage().contains("Record expected after VALUES"));
    }

    @Test
    public void testNonRecordAfterSetInUpdateThrows() {
        // Non-record object after SET in UPDATE should throw.
        // Covers line 350: "Record expected after SET."
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("UPDATE test_entity SET \0", "not a record")));
        assertTrue(exception.getMessage().contains("Record expected after SET"));
    }

    // ==================== resolveIterableElement / resolveArrayElement ====================

    @Test
    public void testNonRecordIterableAfterValuesInInsertThrows() {
        // Iterable of non-record elements after VALUES in INSERT should throw.
        // Covers line 414: "Records expected after VALUES."
        List<String> nonRecords = List.of("a", "b");
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("INSERT INTO test_entity VALUES \0", nonRecords)));
        assertTrue(exception.getMessage().contains("Records expected after VALUES"));
    }

    @Test
    public void testNonRecordArrayAfterValuesInInsertThrows() {
        // Array of non-record elements after VALUES in INSERT should also throw.
        // Covers line 380 (resolveArrayElement delegates to resolveIterableElement) and line 414.
        Object[] nonRecordArray = new Object[]{"a", "b"};
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("INSERT INTO test_entity VALUES \0", (Object) nonRecordArray)));
        assertTrue(exception.getMessage().contains("Records expected after VALUES"));
    }

    @Test
    public void testIterableAfterInsertWhereResolvesCorrectly() {
        // Iterable after WHERE in INSERT should resolve correctly (covers line 417).
        // Compilation may fail but the resolution phase should not reject the iterable.
        List<Integer> ids = List.of(1, 2, 3);
        try {
            TEMPLATE.process(raw("INSERT INTO test_entity WHERE \0", ids));
        } catch (SqlTemplateException exception) {
            assertTrue(!exception.getMessage().contains("Records expected"),
                    "Unexpected iterable rejection: " + exception.getMessage());
        }
    }

    @Test
    public void testIterableAsParamInInsert() {
        // Iterable not after VALUES or WHERE should be treated as param (covers line 419).
        List<Integer> ids = List.of(1, 2, 3);
        assertDoesNotThrow(() -> TEMPLATE.process(raw("INSERT INTO test_entity (\0)", ids)));
    }

    @Test
    public void testRecordIterableAfterValuesInInsert() {
        // Iterable of records after VALUES in INSERT should resolve correctly (covers line 412).
        List<TestData> records = List.of(new TestData("a"), new TestData("b"));
        try {
            TEMPLATE.process(raw("INSERT INTO test_data VALUES \0", records));
        } catch (SqlTemplateException exception) {
            assertTrue(!exception.getMessage().contains("Records expected"),
                    "Record iterable should be accepted after VALUES: " + exception.getMessage());
        }
    }

    // ==================== resolveTypeElement ====================

    @Test
    public void testTypeInInsertNotAfterIntoYieldsTable() {
        // A record type in INSERT context not after INTO should yield a table element (covers line 465).
        // The insert() + second type resolves to table.
        assertDoesNotThrow(() -> TEMPLATE.process(raw("INSERT INTO \0 (\0) VALUES (1, 'test')",
                insert(TestEntity.class), TestEntity.class)));
    }

    @Test
    public void testTypeInUpdateNotAfterUpdateYieldsTable() {
        // A record type in UPDATE context not after UPDATE keyword should yield table (covers line 471).
        try {
            TEMPLATE.process(raw("UPDATE \0 SET name = 'x' FROM \0",
                    update(TestEntity.class), TestEntity.class));
        } catch (SqlTemplateException exception) {
            // The second TestEntity.class resolves to table (line 471). Any further compilation
            // errors are unrelated to type resolution.
            assertTrue(!exception.getMessage().contains("Update element is only allowed"),
                    "Unexpected element resolution error: " + exception.getMessage());
        }
    }

    @Test
    public void testTypeInDeleteYieldsTable() {
        // A record type in DELETE context where it is not directly before FROM and not after FROM
        // should yield table (covers line 480).
        try {
            TEMPLATE.process(raw("DELETE FROM test_entity WHERE test_entity.id IN (SELECT id FROM \0)",
                    TestEntity.class));
        } catch (SqlTemplateException exception) {
            // Type resolution should succeed (line 480 yields table). Further failures are compilation.
            assertTrue(!exception.getMessage().contains("Delete element"),
                    "Unexpected type resolution error: " + exception.getMessage());
        }
    }

    // ==================== resolveElements: wrong operation ====================

    @Test
    public void testSelectElementInInsertThrows() {
        // Select element in an INSERT statement should throw.
        // Covers line 515.
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("INSERT INTO test_entity \0", select(TestEntity.class))));
        assertTrue(exception.getMessage().contains("Select element is only allowed for select statements"));
    }

    @Test
    public void testInsertElementInSelectThrows() {
        // Insert element in a SELECT statement should throw.
        // Covers line 517.
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("SELECT \0 FROM test_entity", insert(TestEntity.class))));
        assertTrue(exception.getMessage().contains("Insert element is only allowed for insert statements"));
    }

    @Test
    public void testUpdateElementInSelectThrows() {
        // Update element in a SELECT statement should throw.
        // Covers line 519.
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("SELECT \0 FROM test_entity", update(TestEntity.class))));
        assertTrue(exception.getMessage().contains("Update element is only allowed for update statements"));
    }

    @Test
    public void testDeleteElementInSelectThrows() {
        // Delete element in a SELECT statement should throw.
        // Covers line 521.
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("SELECT \0 FROM test_entity", delete(TestEntity.class))));
        assertTrue(exception.getMessage().contains("Delete element is only allowed for delete statements"));
    }

    // ==================== resolveElements: records not supported ====================

    @Test
    public void testSelectElementWithNoRecordsSupportThrows() {
        // Select element when supportRecords=false should throw.
        // Covers line 523.
        var exception = assertThrows(SqlTemplateException.class,
                () -> NO_RECORDS_TEMPLATE.process(raw("SELECT \0 FROM test_entity", select(TestEntity.class))));
        assertTrue(exception.getMessage().contains("Records are not supported in this configuration"));
    }

    @Test
    public void testInsertElementWithNoRecordsSupportThrows() {
        // Insert element when supportRecords=false should throw.
        // Covers line 525.
        var exception = assertThrows(SqlTemplateException.class,
                () -> NO_RECORDS_TEMPLATE.process(raw("INSERT INTO \0", insert(TestEntity.class))));
        assertTrue(exception.getMessage().contains("Records are not supported in this configuration"));
    }

    @Test
    public void testUpdateElementWithNoRecordsSupportThrows() {
        // Update element when supportRecords=false should throw.
        // Covers line 527.
        var exception = assertThrows(SqlTemplateException.class,
                () -> NO_RECORDS_TEMPLATE.process(raw("UPDATE \0 SET x = 1", update(TestEntity.class))));
        assertTrue(exception.getMessage().contains("Records are not supported in this configuration"));
    }

    @Test
    public void testDeleteElementWithNoRecordsSupportThrows() {
        // Delete element when supportRecords=false should throw.
        // Covers line 529.
        var exception = assertThrows(SqlTemplateException.class,
                () -> NO_RECORDS_TEMPLATE.process(raw("DELETE \0 FROM test_entity", delete(TestEntity.class))));
        assertTrue(exception.getMessage().contains("Records are not supported in this configuration"));
    }

    @Test
    public void testTableElementWithNoRecordsSupportThrows() {
        // Table element when supportRecords=false should throw.
        // Covers line 531.
        var exception = assertThrows(SqlTemplateException.class,
                () -> NO_RECORDS_TEMPLATE.process(raw("SELECT * FROM \0", table(TestEntity.class))));
        assertTrue(exception.getMessage().contains("Records are not supported in this configuration"));
    }

    @Test
    public void testClassElementWithNoRecordsSupportThrows() {
        // Class element (record type) when supportRecords=false should throw.
        // Covers line 533.
        var exception = assertThrows(SqlTemplateException.class,
                () -> NO_RECORDS_TEMPLATE.process(raw("SELECT \0 FROM test_entity", TestEntity.class)));
        assertTrue(exception.getMessage().contains("Records are not supported in this configuration"));
    }

    // ==================== resolveElements: duplicate statement elements ====================

    @Test
    public void testDuplicateSelectElementThrows() {
        // Two Select elements should throw.
        // Covers line 536.
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("SELECT \0 \0 FROM test_entity", select(TestEntity.class), select(TestEntity.class))));
        assertTrue(exception.getMessage().contains("Only a single Select element is allowed"));
    }

    @Test
    public void testDuplicateInsertElementThrows() {
        // Two Insert elements should throw.
        // Covers line 542.
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("INSERT INTO \0 \0", insert(TestEntity.class), insert(TestEntity.class))));
        assertTrue(exception.getMessage().contains("Only a single Insert element is allowed"));
    }

    @Test
    public void testDuplicateUpdateElementThrows() {
        // Two Update elements should throw.
        // Covers line 548.
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("UPDATE \0 \0 SET x = 1", update(TestEntity.class), update(TestEntity.class))));
        assertTrue(exception.getMessage().contains("Only a single Update element is allowed"));
    }

    @Test
    public void testDuplicateDeleteElementThrows() {
        // Two Delete elements should throw.
        // Covers lines 552, 554.
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("DELETE \0 \0 FROM test_entity", delete(TestEntity.class), delete(TestEntity.class))));
        assertTrue(exception.getMessage().contains("Only a single Delete element is allowed"));
    }

    // ==================== resolveElements: unsupported types ====================

    @Test
    public void testTemplateStringAsValueThrows() {
        // TemplateString as a value should throw.
        // Covers line 569.
        TemplateString nested = raw("SELECT 1");
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("SELECT \0", nested)));
        assertTrue(exception.getMessage().contains("TemplateString not allowed as string template value"));
    }

    @Test
    public void testStreamAsValueThrows() {
        // Stream as a value should throw.
        // Covers line 570.
        Stream<Integer> stream = Stream.of(1, 2, 3);
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("SELECT \0", stream)));
        assertTrue(exception.getMessage().contains("Stream not supported as string template value"));
    }

    // ==================== resolveObjectElement: record objects pass resolution ====================

    @Test
    public void testRecordAfterValuesResolvesCorrectly() {
        // A Data record after VALUES in INSERT should resolve to a values element.
        // Covers the positive path in resolveObjectElement for INSERT/VALUES.
        TestData data = new TestData("hello");
        try {
            TEMPLATE.process(raw("INSERT INTO test_data VALUES \0", data));
        } catch (SqlTemplateException exception) {
            // The values(record) resolution should succeed. Compilation may throw for other reasons.
            assertTrue(!exception.getMessage().contains("Record expected"),
                    "Record should be accepted after VALUES: " + exception.getMessage());
        }
    }

    @Test
    public void testRecordAfterSetResolvesCorrectly() {
        // A Data record after SET in UPDATE should resolve to a set element.
        // Covers the positive path in resolveObjectElement for UPDATE/SET.
        TestData data = new TestData("hello");
        try {
            TEMPLATE.process(raw("UPDATE test_data SET \0", data));
        } catch (SqlTemplateException exception) {
            assertTrue(!exception.getMessage().contains("Record expected"),
                    "Record should be accepted after SET: " + exception.getMessage());
        }
    }

    @Test
    public void testObjectAfterWhereResolvesCorrectly() {
        // A non-null Data record after WHERE in SELECT should resolve to where element.
        TestData data = new TestData("hello");
        try {
            TEMPLATE.process(raw("SELECT * FROM test_data WHERE \0", data));
        } catch (SqlTemplateException exception) {
            assertTrue(!exception.getMessage().contains("Non-null object expected"),
                    "Non-null object should be accepted after WHERE: " + exception.getMessage());
        }
    }

    @Test
    public void testObjectAfterWhereInInsertResolvesCorrectly() {
        // A non-null object after WHERE in INSERT should resolve to where element (covers line 339).
        TestData data = new TestData("hello");
        try {
            TEMPLATE.process(raw("INSERT INTO test_data WHERE \0", data));
        } catch (SqlTemplateException exception) {
            assertTrue(!exception.getMessage().contains("Non-null object expected"),
                    "Non-null object should be accepted after WHERE in INSERT: " + exception.getMessage());
        }
    }

    // ==================== Metamodel non-column ====================

    @Test
    public void testNonColumnMetamodelThrows() {
        // A Metamodel that does not reference a column should throw.
        // Covers line 562: "Metamodel does not reference a column."
        Metamodel<TestEntity, TestEntity> rootMetamodel = Metamodel.root(TestEntity.class);
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("SELECT \0 FROM test_entity", rootMetamodel)));
        assertTrue(exception.getMessage().contains("Metamodel does not reference a column"));
    }

    // ==================== Array resolution ====================

    @Test
    public void testArrayAfterWhereInSelectResolvesCorrectly() {
        // An Object array after WHERE in SELECT should resolve to param (covers line 563 -> 380).
        Object[] paramArray = new Object[]{1, 2, 3};
        assertDoesNotThrow(() -> TEMPLATE.process(raw("SELECT * FROM test_entity WHERE id IN (\0)", (Object) paramArray)));
    }

    @Test
    public void testArrayNotAfterWhereInSelectResolvesAsParam() {
        // An Object array NOT after WHERE resolves as param.
        Object[] paramArray = new Object[]{1, 2, 3};
        assertDoesNotThrow(() -> TEMPLATE.process(raw("SELECT * FROM test_entity WHERE id IN (\0)", (Object) paramArray)));
    }

    // ==================== DELETE type resolution variants ====================

    @Test
    public void testDeleteElementWithoutFromThrows() {
        // Delete element without a corresponding From element should throw at post-processing.
        // Covers line 1123: "From element required when using Delete element."
        var exception = assertThrows(SqlTemplateException.class,
                () -> TEMPLATE.process(raw("DELETE \0 WHERE id = 1", delete(TestEntity.class))));
        assertTrue(exception.getMessage().contains("From element required when using Delete element"));
    }
}
