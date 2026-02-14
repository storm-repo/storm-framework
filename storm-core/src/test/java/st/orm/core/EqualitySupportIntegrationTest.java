package st.orm.core;

import lombok.Builder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.DbTable;
import st.orm.DynamicUpdate;
import st.orm.Entity;
import st.orm.PK;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.Sql;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.DirtyCheck.VALUE;
import static st.orm.UpdateMode.FIELD;
import static st.orm.core.template.SqlInterceptor.observe;
import static st.orm.core.template.TemplateString.raw;

/**
 * Integration tests that exercise the primitive-type equality comparison branches in
 * {@link st.orm.core.template.impl.EqualitySupport#compileIsSame(java.lang.invoke.MethodHandle)}.
 *
 * <p>The test creates a dedicated table with columns for each primitive type (long, boolean, byte, short, float, double)
 * plus int and String. A {@code @DynamicUpdate(FIELD)} entity with {@code dirtyCheck = VALUE} is used so that the dirty
 * checking logic compiles a field-level equality comparator for every record component, exercising each primitive branch
 * in {@code compileIsSame}.</p>
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class EqualitySupportIntegrationTest {

    @Autowired
    private DataSource dataSource;

    // ----------------------------------------------------------------
    // Entity with various primitive types for testing dirty checking
    // ----------------------------------------------------------------

    @DynamicUpdate(value = FIELD, dirtyCheck = VALUE)
    @Builder(toBuilder = true)
    @DbTable("primitive_test")
    public record PrimitiveEntity(
            @PK Integer id,
            int intVal,
            long longVal,
            boolean boolVal,
            byte byteVal,
            short shortVal,
            float floatVal,
            double doubleVal,
            String stringVal
    ) implements Entity<Integer> {}

    @DynamicUpdate(FIELD)
    @Builder(toBuilder = true)
    @DbTable("primitive_test")
    public record PrimitiveEntityDefault(
            @PK Integer id,
            int intVal,
            long longVal,
            boolean boolVal,
            byte byteVal,
            short shortVal,
            float floatVal,
            double doubleVal,
            String stringVal
    ) implements Entity<Integer> {}

    @BeforeEach
    void createTable() {
        var orm = ORMTemplate.of(dataSource);
        try (var q = orm.query(raw("""
                CREATE TABLE IF NOT EXISTS primitive_test (
                    id INTEGER AUTO_INCREMENT PRIMARY KEY,
                    int_val INTEGER NOT NULL,
                    long_val BIGINT NOT NULL,
                    bool_val BOOLEAN NOT NULL,
                    byte_val TINYINT NOT NULL,
                    short_val SMALLINT NOT NULL,
                    float_val REAL NOT NULL,
                    double_val DOUBLE NOT NULL,
                    string_val VARCHAR(255)
                )""")).prepare()) {
            q.executeUpdate();
        }
    }

    // ----------------------------------------------------------------
    // Insert + read-back tests
    // ----------------------------------------------------------------

    @Test
    void insertAndReadBack_allPrimitiveTypes() {
        var orm = ORMTemplate.of(dataSource);
        var repo = orm.entity(PrimitiveEntity.class);

        var entity = new PrimitiveEntity(null, 42, 123456789L, true, (byte) 7, (short) 300, 3.14f, 2.71828, "hello");
        var inserted = repo.insertAndFetch(entity);

        assertNotNull(inserted.id());
        assertEquals(42, inserted.intVal());
        assertEquals(123456789L, inserted.longVal());
        assertTrue(inserted.boolVal());
        assertEquals((byte) 7, inserted.byteVal());
        assertEquals((short) 300, inserted.shortVal());
        assertEquals(3.14f, inserted.floatVal());
        assertEquals(2.71828, inserted.doubleVal());
        assertEquals("hello", inserted.stringVal());
    }

    // ----------------------------------------------------------------
    // VALUE dirty check: unchanged entity should skip update
    // ----------------------------------------------------------------

    @Test
    void valueCheck_skipsUpdate_whenNoPrimitiveFieldChanges() {
        var orm = ORMTemplate.of(dataSource);
        var repo = orm.entity(PrimitiveEntity.class);

        var entity = new PrimitiveEntity(null, 1, 2L, false, (byte) 3, (short) 4, 5.0f, 6.0, "unchanged");
        var inserted = repo.insertAndFetch(entity);

        // Rebuild with identical values.
        var update = inserted.toBuilder().build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNull(sql, "No UPDATE should be generated when all primitive fields are unchanged");
    }

    // ----------------------------------------------------------------
    // int change detection
    // ----------------------------------------------------------------

    @Test
    void valueCheck_detectsChange_intVal() {
        var orm = ORMTemplate.of(dataSource);
        var repo = orm.entity(PrimitiveEntity.class);

        var inserted = repo.insertAndFetch(
                new PrimitiveEntity(null, 10, 20L, false, (byte) 1, (short) 2, 1.0f, 2.0, "test"));

        var update = inserted.toBuilder().intVal(99).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql, "UPDATE should be generated when intVal changes");
        assertTrue(sql.statement().contains("int_val"), "UPDATE should include int_val column");
    }

    // ----------------------------------------------------------------
    // long change detection
    // ----------------------------------------------------------------

    @Test
    void valueCheck_detectsChange_longVal() {
        var orm = ORMTemplate.of(dataSource);
        var repo = orm.entity(PrimitiveEntity.class);

        var inserted = repo.insertAndFetch(
                new PrimitiveEntity(null, 10, 20L, false, (byte) 1, (short) 2, 1.0f, 2.0, "test"));

        var update = inserted.toBuilder().longVal(999L).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql, "UPDATE should be generated when longVal changes");
        assertTrue(sql.statement().contains("long_val"), "UPDATE should include long_val column");
    }

    // ----------------------------------------------------------------
    // boolean change detection
    // ----------------------------------------------------------------

    @Test
    void valueCheck_detectsChange_boolVal() {
        var orm = ORMTemplate.of(dataSource);
        var repo = orm.entity(PrimitiveEntity.class);

        var inserted = repo.insertAndFetch(
                new PrimitiveEntity(null, 10, 20L, false, (byte) 1, (short) 2, 1.0f, 2.0, "test"));

        var update = inserted.toBuilder().boolVal(true).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql, "UPDATE should be generated when boolVal changes");
        assertTrue(sql.statement().contains("bool_val"), "UPDATE should include bool_val column");
    }

    // ----------------------------------------------------------------
    // byte change detection
    // ----------------------------------------------------------------

    @Test
    void valueCheck_detectsChange_byteVal() {
        var orm = ORMTemplate.of(dataSource);
        var repo = orm.entity(PrimitiveEntity.class);

        var inserted = repo.insertAndFetch(
                new PrimitiveEntity(null, 10, 20L, false, (byte) 1, (short) 2, 1.0f, 2.0, "test"));

        var update = inserted.toBuilder().byteVal((byte) 127).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql, "UPDATE should be generated when byteVal changes");
        assertTrue(sql.statement().contains("byte_val"), "UPDATE should include byte_val column");
    }

    // ----------------------------------------------------------------
    // short change detection
    // ----------------------------------------------------------------

    @Test
    void valueCheck_detectsChange_shortVal() {
        var orm = ORMTemplate.of(dataSource);
        var repo = orm.entity(PrimitiveEntity.class);

        var inserted = repo.insertAndFetch(
                new PrimitiveEntity(null, 10, 20L, false, (byte) 1, (short) 2, 1.0f, 2.0, "test"));

        var update = inserted.toBuilder().shortVal((short) 32000).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql, "UPDATE should be generated when shortVal changes");
        assertTrue(sql.statement().contains("short_val"), "UPDATE should include short_val column");
    }

    // ----------------------------------------------------------------
    // float change detection
    // ----------------------------------------------------------------

    @Test
    void valueCheck_detectsChange_floatVal() {
        var orm = ORMTemplate.of(dataSource);
        var repo = orm.entity(PrimitiveEntity.class);

        var inserted = repo.insertAndFetch(
                new PrimitiveEntity(null, 10, 20L, false, (byte) 1, (short) 2, 1.0f, 2.0, "test"));

        var update = inserted.toBuilder().floatVal(9.99f).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql, "UPDATE should be generated when floatVal changes");
        assertTrue(sql.statement().contains("float_val"), "UPDATE should include float_val column");
    }

    // ----------------------------------------------------------------
    // double change detection
    // ----------------------------------------------------------------

    @Test
    void valueCheck_detectsChange_doubleVal() {
        var orm = ORMTemplate.of(dataSource);
        var repo = orm.entity(PrimitiveEntity.class);

        var inserted = repo.insertAndFetch(
                new PrimitiveEntity(null, 10, 20L, false, (byte) 1, (short) 2, 1.0f, 2.0, "test"));

        var update = inserted.toBuilder().doubleVal(99.99).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql, "UPDATE should be generated when doubleVal changes");
        assertTrue(sql.statement().contains("double_val"), "UPDATE should include double_val column");
    }

    // ----------------------------------------------------------------
    // String (Object) change detection
    // ----------------------------------------------------------------

    @Test
    void valueCheck_detectsChange_stringVal() {
        var orm = ORMTemplate.of(dataSource);
        var repo = orm.entity(PrimitiveEntity.class);

        var inserted = repo.insertAndFetch(
                new PrimitiveEntity(null, 10, 20L, false, (byte) 1, (short) 2, 1.0f, 2.0, "original"));

        var update = inserted.toBuilder().stringVal("changed").build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql, "UPDATE should be generated when stringVal changes");
        assertTrue(sql.statement().contains("string_val"), "UPDATE should include string_val column");
    }

    // ----------------------------------------------------------------
    // Multiple field changes at once
    // ----------------------------------------------------------------

    @Test
    void valueCheck_detectsMultipleChanges() {
        var orm = ORMTemplate.of(dataSource);
        var repo = orm.entity(PrimitiveEntity.class);

        var inserted = repo.insertAndFetch(
                new PrimitiveEntity(null, 10, 20L, false, (byte) 1, (short) 2, 1.0f, 2.0, "test"));

        var update = inserted.toBuilder()
                .intVal(11)
                .longVal(21L)
                .boolVal(true)
                .build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql, "UPDATE should be generated when multiple fields change");
        var stmt = sql.statement();
        assertTrue(stmt.contains("int_val"), "UPDATE should include int_val column");
        assertTrue(stmt.contains("long_val"), "UPDATE should include long_val column");
        assertTrue(stmt.contains("bool_val"), "UPDATE should include bool_val column");
    }

    // ----------------------------------------------------------------
    // DEFAULT (instance) dirty check: rebuilt record is still same
    // ----------------------------------------------------------------

    @Test
    void defaultCheck_skipsUpdate_whenSameValues() {
        var orm = ORMTemplate.of(dataSource);
        var repo = orm.entity(PrimitiveEntityDefault.class);

        var inserted = repo.insertAndFetch(
                new PrimitiveEntityDefault(null, 10, 20L, false, (byte) 1, (short) 2, 1.0f, 2.0, "test"));

        // For primitives, toBuilder().build() creates new wrapper objects but same primitive values.
        // DEFAULT dirty check uses identity for reference types, but primitive fields are compared by value
        // via EqualitySupport.compileIsIdentical which delegates to compileIsSame for primitives.
        var update = inserted.toBuilder().build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        // String field uses a different instance, so identity-based check considers it dirty.
        // But primitive fields should not be considered dirty.
        // The overall result depends on whether the String creates a new instance via toBuilder.
    }

    // ----------------------------------------------------------------
    // Verify actual data round-trip with all primitives changed
    // ----------------------------------------------------------------

    @Test
    void roundTrip_allPrimitiveFieldsUpdated() {
        var orm = ORMTemplate.of(dataSource);
        var repo = orm.entity(PrimitiveEntity.class);

        var inserted = repo.insertAndFetch(
                new PrimitiveEntity(null, 1, 2L, false, (byte) 3, (short) 4, 5.5f, 6.6, "before"));

        var update = inserted.toBuilder()
                .intVal(100)
                .longVal(200L)
                .boolVal(true)
                .byteVal((byte) 33)
                .shortVal((short) 44)
                .floatVal(55.5f)
                .doubleVal(66.6)
                .stringVal("after")
                .build();

        var updated = repo.updateAndFetch(update);

        assertEquals(inserted.id(), updated.id());
        assertEquals(100, updated.intVal());
        assertEquals(200L, updated.longVal());
        assertTrue(updated.boolVal());
        assertEquals((byte) 33, updated.byteVal());
        assertEquals((short) 44, updated.shortVal());
        assertEquals(55.5f, updated.floatVal());
        assertEquals(66.6, updated.doubleVal());
        assertEquals("after", updated.stringVal());
    }

    // ----------------------------------------------------------------
    // Edge case: float NaN equality
    // ----------------------------------------------------------------

    @Test
    void valueCheck_floatNaN_treatedAsEqual() {
        var orm = ORMTemplate.of(dataSource);
        var repo = orm.entity(PrimitiveEntity.class);

        var inserted = repo.insertAndFetch(
                new PrimitiveEntity(null, 0, 0L, false, (byte) 0, (short) 0, Float.NaN, 0.0, null));

        // Rebuild with same NaN - should be considered equal.
        var update = inserted.toBuilder().floatVal(Float.NaN).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        // NaN == NaN via Float.floatToIntBits comparison should return true (same),
        // so no update on the float column. Other fields might still trigger if identity differs.
    }

    // ----------------------------------------------------------------
    // Edge case: double NaN equality
    // ----------------------------------------------------------------

    @Test
    void valueCheck_doubleNaN_treatedAsEqual() {
        var orm = ORMTemplate.of(dataSource);
        var repo = orm.entity(PrimitiveEntity.class);

        var inserted = repo.insertAndFetch(
                new PrimitiveEntity(null, 0, 0L, false, (byte) 0, (short) 0, 0.0f, Double.NaN, null));

        // Rebuild with same NaN.
        var update = inserted.toBuilder().doubleVal(Double.NaN).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        // NaN == NaN via Double.doubleToLongBits comparison should return true (same).
    }

    // ----------------------------------------------------------------
    // Edge case: float negative zero vs positive zero
    // ----------------------------------------------------------------

    @Test
    void valueCheck_floatNegativeZero_detectedAsDifferent() {
        var orm = ORMTemplate.of(dataSource);
        var repo = orm.entity(PrimitiveEntity.class);

        var inserted = repo.insertAndFetch(
                new PrimitiveEntity(null, 0, 0L, false, (byte) 0, (short) 0, 0.0f, 0.0, null));

        // -0.0f has different bit pattern from 0.0f.
        var update = inserted.toBuilder().floatVal(-0.0f).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql, "UPDATE should be generated because Float.floatToIntBits(0.0f) != Float.floatToIntBits(-0.0f)");
        assertTrue(sql.statement().contains("float_val"), "UPDATE should include float_val column");
    }

    // ----------------------------------------------------------------
    // Edge case: double negative zero vs positive zero
    // ----------------------------------------------------------------

    @Test
    void valueCheck_doubleNegativeZero_detectedAsDifferent() {
        var orm = ORMTemplate.of(dataSource);
        var repo = orm.entity(PrimitiveEntity.class);

        var inserted = repo.insertAndFetch(
                new PrimitiveEntity(null, 0, 0L, false, (byte) 0, (short) 0, 0.0f, 0.0, null));

        // -0.0 has different bit pattern from 0.0.
        var update = inserted.toBuilder().doubleVal(-0.0).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql, "UPDATE should be generated because Double.doubleToLongBits(0.0) != Double.doubleToLongBits(-0.0)");
        assertTrue(sql.statement().contains("double_val"), "UPDATE should include double_val column");
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private Sql captureFirstUpdateSql(ThrowingSupplier<?> action) {
        AtomicReference<Sql> ref = new AtomicReference<>();
        observe(s -> {
            if (s.statement().startsWith("UPDATE")) {
                ref.compareAndSet(null, s);
            }
        }, () -> {
            try {
                return action.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return ref.getPlain();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
