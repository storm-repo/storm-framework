package st.orm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AbstractKeyMetamodelTest {

    record TestData(int id) implements Data {}

    static class TestKeyMetamodel extends AbstractKeyMetamodel<TestData, Integer, Integer> {
        TestKeyMetamodel(Class<Integer> fieldType) {
            super(fieldType);
        }

        TestKeyMetamodel(Class<Integer> fieldType, String path) {
            super(fieldType, path);
        }

        TestKeyMetamodel(Class<Integer> fieldType, String path, String field, boolean inline, Metamodel<TestData, ?> parent) {
            super(fieldType, path, field, inline, parent);
        }

        TestKeyMetamodel(Class<Integer> fieldType, String path, String field, boolean inline, Metamodel<TestData, ?> parent, boolean isColumn) {
            super(fieldType, path, field, inline, parent, isColumn);
        }

        TestKeyMetamodel(Class<Integer> fieldType, String path, String field, boolean inline, Metamodel<TestData, ?> parent, boolean isColumn, boolean nullable) {
            super(fieldType, path, field, inline, parent, isColumn, nullable);
        }

        @Override
        public Object getValue(TestData record) {
            return record.id();
        }

        @Override
        public boolean isIdentical(TestData a, TestData b) {
            return a == b;
        }

        @Override
        public boolean isSame(TestData a, TestData b) {
            return a.id() == b.id();
        }
    }

    @Test
    void defaultConstructorNotNullable() {
        TestKeyMetamodel metamodel = new TestKeyMetamodel(Integer.class);
        assertFalse(metamodel.isNullable());
        assertEquals(Integer.class, metamodel.fieldType());
    }

    @Test
    void pathConstructorNotNullable() {
        TestKeyMetamodel metamodel = new TestKeyMetamodel(Integer.class, "path");
        assertFalse(metamodel.isNullable());
        assertEquals("path", metamodel.path());
    }

    @Test
    void fiveArgConstructorNotNullable() {
        TestKeyMetamodel metamodel = new TestKeyMetamodel(Integer.class, "path", "field", false, null);
        assertFalse(metamodel.isNullable());
        assertEquals("field", metamodel.field());
    }

    @Test
    void sixArgConstructorNotNullable() {
        TestKeyMetamodel metamodel = new TestKeyMetamodel(Integer.class, "path", "field", false, null, true);
        assertFalse(metamodel.isNullable());
        assertTrue(metamodel.isColumn());
    }

    @Test
    void sevenArgConstructorWithNullableTrue() {
        TestKeyMetamodel metamodel = new TestKeyMetamodel(Integer.class, "path", "field", false, null, true, true);
        assertTrue(metamodel.isNullable());
    }

    @Test
    void sevenArgConstructorWithNullableFalse() {
        TestKeyMetamodel metamodel = new TestKeyMetamodel(Integer.class, "path", "field", false, null, true, false);
        assertFalse(metamodel.isNullable());
    }

    @Test
    void keyMetamodelImplementsKeyInterface() {
        TestKeyMetamodel metamodel = new TestKeyMetamodel(Integer.class);
        assertTrue(metamodel instanceof Metamodel.Key);
    }
}
