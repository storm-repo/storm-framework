package st.orm.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import st.orm.Entity;
import st.orm.PK;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.impl.SchemaValidator;

/**
 * Additional tests for {@link StormExtension} covering edge cases:
 * - Custom URL
 * - SchemaValidator parameter injection
 * - Empty scripts array (default)
 * - ORMTemplate factory method resolution
 */
@StormTest(scripts = {"/test-schema.sql", "/test-data.sql"})
class StormExtensionAdditionalTest {

    record Item(@PK Integer id, String name) implements Entity<Integer> {}

    @Test
    void schemaValidatorShouldBeInjected(SchemaValidator validator) {
        assertNotNull(validator);
    }

    @Test
    void ormTemplateShouldSupportEntityOperations(ORMTemplate orm) {
        var items = orm.entity(Item.class).findAll();
        assertEquals(3, items.size());
    }

    @Test
    void statementCaptureExecuteShouldReturnResult(ORMTemplate orm, StatementCapture capture) {
        var items = capture.execute(() -> orm.entity(Item.class).findAll());
        // At least 3 rows from test data; may be more if insert tests run in the same class.
        assertTrue(items.size() >= 3);
        assertEquals(1, capture.count(CapturedStatement.Operation.SELECT));
    }

    @Test
    void statementCaptureExecuteThrowingShouldReturnResult(ORMTemplate orm, StatementCapture capture) throws Exception {
        var items = capture.executeThrowing(() -> orm.entity(Item.class).findAll());
        assertTrue(items.size() >= 3);
        assertEquals(1, capture.count(CapturedStatement.Operation.SELECT));
    }

    @Test
    void statementCaptureStatementsShouldReturnFilteredStatements(ORMTemplate orm, StatementCapture capture) {
        capture.run(() -> orm.entity(Item.class).findAll());
        capture.run(() -> orm.entity(Item.class).insert(new Item(0, "Echo")));

        var selects = capture.statements(CapturedStatement.Operation.SELECT);
        var inserts = capture.statements(CapturedStatement.Operation.INSERT);
        assertEquals(1, selects.size());
        assertEquals(1, inserts.size());
        assertEquals(0, capture.statements(CapturedStatement.Operation.DELETE).size());
        assertEquals(0, capture.count(CapturedStatement.Operation.DELETE));
    }

    @Test
    void statementCaptureShouldReturnAllStatements(ORMTemplate orm, StatementCapture capture) {
        capture.run(() -> orm.entity(Item.class).findAll());
        var allStatements = capture.statements();
        assertEquals(1, allStatements.size());
        assertNotNull(allStatements.getFirst().statement());
        assertNotNull(allStatements.getFirst().parameters());
    }

    @Test
    void statementCaptureShouldRecordUpdates(ORMTemplate orm, StatementCapture capture) {
        capture.run(() -> orm.entity(Item.class).update(new Item(1, "Updated")));
        assertEquals(1, capture.count(CapturedStatement.Operation.UPDATE));
        var updates = capture.statements(CapturedStatement.Operation.UPDATE);
        assertEquals(1, updates.size());
        assertTrue(updates.getFirst().statement().toUpperCase().contains("UPDATE"));
    }
}
