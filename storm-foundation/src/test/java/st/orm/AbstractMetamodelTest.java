package st.orm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AbstractMetamodelTest {

    record TestData(int id) implements Data {}
    record InlineData(String value) implements Data {}

    static class TestMetamodel extends AbstractMetamodel<TestData, TestData, TestData> {
        TestMetamodel(Class<TestData> fieldType) {
            super(fieldType);
        }

        TestMetamodel(Class<TestData> fieldType, String path) {
            super(fieldType, path);
        }

        @Override
        public Object getValue(TestData record) {
            return record;
        }

        @Override
        public boolean isIdentical(TestData a, TestData b) {
            return a == b;
        }

        @Override
        public boolean isSame(TestData a, TestData b) {
            return a.equals(b);
        }
    }

    static class FieldMetamodel extends AbstractMetamodel<TestData, Integer, Integer> {
        FieldMetamodel(String path, String field, boolean inline, Metamodel<TestData, ?> parent) {
            super(Integer.class, path, field, inline, parent);
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

    static class InlineMetamodel extends AbstractMetamodel<TestData, InlineData, InlineData> {
        InlineMetamodel(String path, String field, Metamodel<TestData, ?> parent) {
            super(InlineData.class, path, field, true, parent);
        }

        @Override
        public Object getValue(TestData record) {
            return null;
        }

        @Override
        public boolean isIdentical(TestData a, TestData b) {
            return false;
        }

        @Override
        public boolean isSame(TestData a, TestData b) {
            return false;
        }
    }

    @Test
    void rootMetamodelConstructor() {
        TestMetamodel metamodel = new TestMetamodel(TestData.class);
        assertEquals(TestData.class, metamodel.fieldType());
        assertEquals("", metamodel.path());
        assertEquals("", metamodel.field());
        assertFalse(metamodel.isInline());
        assertFalse(metamodel.isColumn());
    }

    @Test
    void pathMetamodelConstructor() {
        TestMetamodel metamodel = new TestMetamodel(TestData.class, "table_path");
        assertEquals(TestData.class, metamodel.fieldType());
        assertEquals("table_path", metamodel.path());
        assertEquals("", metamodel.field());
        assertFalse(metamodel.isInline());
        assertTrue(metamodel.isColumn());
    }

    @Test
    void rootReturnsFieldTypeForRootMetamodel() {
        TestMetamodel metamodel = new TestMetamodel(TestData.class);
        assertEquals(TestData.class, metamodel.root());
    }

    @Test
    void rootReturnsParentRootForChildMetamodel() {
        TestMetamodel parent = new TestMetamodel(TestData.class);
        FieldMetamodel child = new FieldMetamodel("path", "id", false, parent);
        assertEquals(TestData.class, child.root());
    }

    @Test
    void tableReturnsParentForNonInlineChild() {
        TestMetamodel parent = new TestMetamodel(TestData.class);
        FieldMetamodel child = new FieldMetamodel("path", "id", false, parent);
        assertSame(parent, child.table());
    }

    @Test
    void tableReturnsSelfForRootMetamodel() {
        TestMetamodel metamodel = new TestMetamodel(TestData.class);
        assertSame(metamodel, metamodel.table());
    }

    @Test
    void tableTraversesInlineParentsToFindTable() {
        TestMetamodel root = new TestMetamodel(TestData.class);
        InlineMetamodel inlineParent = new InlineMetamodel("path", "inlineField", root);
        FieldMetamodel child = new FieldMetamodel("path", "nestedField", false, inlineParent);
        assertSame(root, child.table());
    }

    @Test
    void isColumnForFieldWithNonEmptyField() {
        TestMetamodel parent = new TestMetamodel(TestData.class);
        FieldMetamodel child = new FieldMetamodel("path", "id", false, parent);
        assertTrue(child.isColumn());
    }

    @Test
    void isColumnFalseForInline() {
        TestMetamodel parent = new TestMetamodel(TestData.class);
        InlineMetamodel inline = new InlineMetamodel("path", "inlineField", parent);
        assertFalse(inline.isColumn());
    }

    @Test
    void isInline() {
        TestMetamodel parent = new TestMetamodel(TestData.class);
        InlineMetamodel inline = new InlineMetamodel("path", "inlineField", parent);
        assertTrue(inline.isInline());
    }

    @Test
    void equalsBasedOnTableFieldTypePathAndField() {
        TestMetamodel parent = new TestMetamodel(TestData.class);
        FieldMetamodel field1 = new FieldMetamodel("path", "id", false, parent);
        FieldMetamodel field2 = new FieldMetamodel("path", "id", false, parent);
        assertEquals(field1, field2);
        assertEquals(field1.hashCode(), field2.hashCode());
    }

    @Test
    void notEqualWithDifferentField() {
        TestMetamodel parent = new TestMetamodel(TestData.class);
        FieldMetamodel field1 = new FieldMetamodel("path", "id", false, parent);
        FieldMetamodel field2 = new FieldMetamodel("path", "name", false, parent);
        assertNotEquals(field1, field2);
    }

    @Test
    void notEqualWithDifferentPath() {
        TestMetamodel parent = new TestMetamodel(TestData.class);
        FieldMetamodel field1 = new FieldMetamodel("path1", "id", false, parent);
        FieldMetamodel field2 = new FieldMetamodel("path2", "id", false, parent);
        assertNotEquals(field1, field2);
    }

    @Test
    void equalsItself() {
        TestMetamodel metamodel = new TestMetamodel(TestData.class);
        assertEquals(metamodel, metamodel);
    }

    @Test
    void notEqualToNonMetamodel() {
        TestMetamodel metamodel = new TestMetamodel(TestData.class);
        assertNotEquals(metamodel, "not a metamodel");
    }

    @Test
    void toStringContainsRelevantInfo() {
        TestMetamodel metamodel = new TestMetamodel(TestData.class);
        String result = metamodel.toString();
        assertTrue(result.contains("TestData"));
        assertTrue(result.contains("Metamodel"));
    }

    @Test
    void fieldPathWithBothPathAndField() {
        TestMetamodel parent = new TestMetamodel(TestData.class);
        FieldMetamodel child = new FieldMetamodel("entity", "id", false, parent);
        assertEquals("entity.id", child.fieldPath());
    }

    @Test
    void fieldPathWithOnlyPath() {
        TestMetamodel metamodel = new TestMetamodel(TestData.class, "entity");
        assertEquals("entity", metamodel.fieldPath());
    }

    @Test
    void fieldPathWithOnlyField() {
        TestMetamodel parent = new TestMetamodel(TestData.class);
        FieldMetamodel child = new FieldMetamodel("", "id", false, parent);
        assertEquals("id", child.fieldPath());
    }

    @Test
    void fieldPathEmptyForRoot() {
        TestMetamodel metamodel = new TestMetamodel(TestData.class);
        assertEquals("", metamodel.fieldPath());
    }

    @Test
    void tableTypeReturnsFieldTypeOfTable() {
        TestMetamodel metamodel = new TestMetamodel(TestData.class);
        assertEquals(TestData.class, metamodel.tableType());
    }
}
