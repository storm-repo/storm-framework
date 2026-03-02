package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import st.orm.Data;
import st.orm.DbColumn;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.GenerationStrategy;
import st.orm.Inline;
import st.orm.PK;
import st.orm.Ref;
import st.orm.Version;
import st.orm.core.template.SqlTemplateException;
import st.orm.mapping.RecordField;

/**
 * Tests for {@link RecordReflection} utility methods covering field lookup, PK/FK/Version
 * discovery, table and column naming, and type checking.
 */
class RecordReflectionTest {

    // ---- Test model types ----

    public record SimpleEntity(
            @PK Integer id,
            @Nonnull String name
    ) implements Entity<Integer> {}

    public record EntityWithVersion(
            @PK Integer id,
            @Nonnull String name,
            @Version int version
    ) implements Entity<Integer> {}

    public record InlineAddress(String street, String zipCode) {}

    public record EntityWithInline(
            @PK Integer id,
            @Inline InlineAddress address,
            @Nonnull String name
    ) implements Entity<Integer> {}

    public record ReferencedEntity(
            @PK Integer id,
            @Nonnull String name
    ) implements Entity<Integer> {}

    public record EntityWithFk(
            @PK Integer id,
            @FK ReferencedEntity ref
    ) implements Entity<Integer> {}

    public record EntityWithRefFk(
            @PK Integer id,
            @Nullable @FK Ref<ReferencedEntity> ref
    ) implements Entity<Integer> {}

    public record CompoundPk(int partA, int partB) {}

    public record EntityWithCompoundPk(
            @PK CompoundPk id,
            @Nonnull String name
    ) implements Entity<CompoundPk> {}

    @DbTable("custom_table")
    public record CustomTableEntity(
            @PK Integer id,
            @Nonnull String name
    ) implements Entity<Integer> {}

    @DbTable(value = "annotated_table", schema = "my_schema")
    public record SchemaTableEntity(
            @PK Integer id,
            @Nonnull String name
    ) implements Entity<Integer> {}

    public record EntityWithDbColumn(
            @PK @DbColumn("pk_col") Integer id,
            @DbColumn("name_col") String name
    ) implements Entity<Integer> {}

    public record EntityWithFkPk(
            @PK @FK ReferencedEntity ref,
            @Nonnull String extra
    ) implements Entity<Integer> {}

    public record NoVersionEntity(
            @PK Integer id,
            String name
    ) implements Entity<Integer> {}

    public record NestedRecord(
            String inner
    ) {}

    public record EntityWithNested(
            @PK Integer id,
            @Inline NestedRecord nested
    ) implements Entity<Integer> {}

    public record SimpleData(String value) implements Data {}

    public record IdentityGenEntity(
            @PK(generation = GenerationStrategy.IDENTITY) Integer id,
            String name
    ) implements Entity<Integer> {}

    public record NoneGenEntity(
            @PK(generation = GenerationStrategy.NONE) Integer id,
            String name
    ) implements Entity<Integer> {}

    public record SeqGenEntity(
            @PK(generation = GenerationStrategy.SEQUENCE, sequence = "my_seq") Integer id,
            String name
    ) implements Entity<Integer> {}

    // ---- isRecord tests ----

    @Test
    void testIsRecordForRecord() {
        assertTrue(RecordReflection.isRecord(SimpleEntity.class));
    }

    @Test
    void testIsRecordForNonRecord() {
        assertFalse(RecordReflection.isRecord(String.class));
    }

    @Test
    void testIsRecordForInlineRecord() {
        assertTrue(RecordReflection.isRecord(InlineAddress.class));
    }

    // ---- getRecordType tests ----

    @Test
    void testGetRecordType() {
        var recordType = RecordReflection.getRecordType(SimpleEntity.class);
        assertNotNull(recordType);
        assertEquals(SimpleEntity.class, recordType.type());
    }

    // ---- getRecordFields tests ----

    @Test
    void testGetRecordFields() {
        var fields = RecordReflection.getRecordFields(SimpleEntity.class);
        assertEquals(2, fields.size());
        assertEquals("id", fields.get(0).name());
        assertEquals("name", fields.get(1).name());
    }

    @Test
    void testGetRecordFieldsForEntityWithFk() {
        var fields = RecordReflection.getRecordFields(EntityWithFk.class);
        assertEquals(2, fields.size());
        assertEquals("id", fields.get(0).name());
        assertEquals("ref", fields.get(1).name());
    }

    // ---- getRecordField (path-based lookup) tests ----

    @Test
    void testGetRecordFieldSimple() throws SqlTemplateException {
        RecordField field = RecordReflection.getRecordField(SimpleEntity.class, "name");
        assertEquals("name", field.name());
        assertEquals(String.class, field.type());
    }

    @Test
    void testGetRecordFieldPk() throws SqlTemplateException {
        RecordField field = RecordReflection.getRecordField(SimpleEntity.class, "id");
        assertEquals("id", field.name());
        assertTrue(field.isAnnotationPresent(PK.class));
    }

    @Test
    void testGetRecordFieldNestedPath() throws SqlTemplateException {
        RecordField field = RecordReflection.getRecordField(EntityWithNested.class, "nested.inner");
        assertEquals("inner", field.name());
        assertEquals(String.class, field.type());
    }

    @Test
    void testGetRecordFieldEmptyPathThrows() {
        assertThrows(SqlTemplateException.class,
                () -> RecordReflection.getRecordField(SimpleEntity.class, ""));
    }

    @Test
    void testGetRecordFieldNonexistentFieldThrows() {
        assertThrows(SqlTemplateException.class,
                () -> RecordReflection.getRecordField(SimpleEntity.class, "nonexistent"));
    }

    @Test
    void testGetRecordFieldNestedNonRecordThrows() {
        assertThrows(SqlTemplateException.class,
                () -> RecordReflection.getRecordField(SimpleEntity.class, "name.something"));
    }

    // ---- findPkField tests ----

    @Test
    void testFindPkField() {
        Optional<RecordField> pkField = RecordReflection.findPkField(SimpleEntity.class);
        assertTrue(pkField.isPresent());
        assertEquals("id", pkField.get().name());
    }

    @Test
    void testFindPkFieldCompound() {
        Optional<RecordField> pkField = RecordReflection.findPkField(EntityWithCompoundPk.class);
        assertTrue(pkField.isPresent());
        assertEquals("id", pkField.get().name());
        assertEquals(CompoundPk.class, pkField.get().type());
    }

    // ---- getNestedPkFields tests ----

    @Test
    void testGetNestedPkFieldsSimple() {
        List<RecordField> pkFields = RecordReflection.getNestedPkFields(SimpleEntity.class).toList();
        assertEquals(1, pkFields.size());
        assertEquals("id", pkFields.get(0).name());
    }

    @Test
    void testGetNestedPkFieldsCompound() {
        List<RecordField> pkFields = RecordReflection.getNestedPkFields(EntityWithCompoundPk.class).toList();
        assertEquals(2, pkFields.size());
        assertEquals("partA", pkFields.get(0).name());
        assertEquals("partB", pkFields.get(1).name());
    }

    @Test
    void testGetNestedPkFieldsFkPk() {
        // When PK is also FK, the field itself is returned (not the nested PK of the referenced entity).
        List<RecordField> pkFields = RecordReflection.getNestedPkFields(EntityWithFkPk.class).toList();
        assertEquals(1, pkFields.size());
        assertEquals("ref", pkFields.get(0).name());
    }

    // ---- getFkFields tests ----

    @Test
    void testGetFkFields() {
        List<RecordField> fkFields = RecordReflection.getFkFields(EntityWithFk.class).toList();
        assertEquals(1, fkFields.size());
        assertEquals("ref", fkFields.get(0).name());
    }

    @Test
    void testGetFkFieldsWithRef() {
        List<RecordField> fkFields = RecordReflection.getFkFields(EntityWithRefFk.class).toList();
        assertEquals(1, fkFields.size());
        assertEquals("ref", fkFields.get(0).name());
    }

    @Test
    void testGetFkFieldsNoFk() {
        List<RecordField> fkFields = RecordReflection.getFkFields(SimpleEntity.class).toList();
        assertTrue(fkFields.isEmpty());
    }

    // ---- getVersionField tests ----

    @Test
    void testGetVersionField() {
        Optional<RecordField> versionField = RecordReflection.getVersionField(EntityWithVersion.class);
        assertTrue(versionField.isPresent());
        assertEquals("version", versionField.get().name());
    }

    @Test
    void testGetVersionFieldAbsent() {
        Optional<RecordField> versionField = RecordReflection.getVersionField(NoVersionEntity.class);
        assertFalse(versionField.isPresent());
    }

    // ---- getGenerationStrategy tests ----

    @Test
    void testGetGenerationStrategyIdentity() throws SqlTemplateException {
        RecordField pkField = RecordReflection.getRecordField(IdentityGenEntity.class, "id");
        assertEquals(GenerationStrategy.IDENTITY, RecordReflection.getGenerationStrategy(pkField));
    }

    @Test
    void testGetGenerationStrategyNone() throws SqlTemplateException {
        RecordField pkField = RecordReflection.getRecordField(NoneGenEntity.class, "id");
        assertEquals(GenerationStrategy.NONE, RecordReflection.getGenerationStrategy(pkField));
    }

    @Test
    void testGetGenerationStrategySequence() throws SqlTemplateException {
        RecordField pkField = RecordReflection.getRecordField(SeqGenEntity.class, "id");
        assertEquals(GenerationStrategy.SEQUENCE, RecordReflection.getGenerationStrategy(pkField));
    }

    @Test
    void testGetGenerationStrategyNonPkField() throws SqlTemplateException {
        RecordField nameField = RecordReflection.getRecordField(SimpleEntity.class, "name");
        assertEquals(GenerationStrategy.NONE, RecordReflection.getGenerationStrategy(nameField));
    }

    // ---- getSequence tests ----

    @Test
    void testGetSequence() throws SqlTemplateException {
        RecordField pkField = RecordReflection.getRecordField(SeqGenEntity.class, "id");
        assertEquals("my_seq", RecordReflection.getSequence(pkField));
    }

    @Test
    void testGetSequenceEmpty() throws SqlTemplateException {
        RecordField pkField = RecordReflection.getRecordField(IdentityGenEntity.class, "id");
        assertEquals("", RecordReflection.getSequence(pkField));
    }

    @Test
    void testGetSequenceNonPk() throws SqlTemplateException {
        RecordField nameField = RecordReflection.getRecordField(SimpleEntity.class, "name");
        assertEquals("", RecordReflection.getSequence(nameField));
    }

    // ---- isTypePresent tests ----

    @Test
    void testIsTypePresentSelf() throws SqlTemplateException {
        assertTrue(RecordReflection.isTypePresent(SimpleEntity.class, SimpleEntity.class));
    }

    @Test
    void testIsTypePresentFk() throws SqlTemplateException {
        assertTrue(RecordReflection.isTypePresent(EntityWithFk.class, ReferencedEntity.class));
    }

    @Test
    void testIsTypePresentAbsent() throws SqlTemplateException {
        assertFalse(RecordReflection.isTypePresent(SimpleEntity.class, ReferencedEntity.class));
    }

    // ---- getRefDataType tests ----

    @Test
    void testGetRefDataType() throws SqlTemplateException {
        RecordField refField = RecordReflection.getRecordField(EntityWithRefFk.class, "ref");
        Class<? extends Data> refDataType = RecordReflection.getRefDataType(refField);
        assertEquals(ReferencedEntity.class, refDataType);
    }

    // ---- getRefPkType tests ----

    @Test
    void testGetRefPkType() throws SqlTemplateException {
        RecordField refField = RecordReflection.getRecordField(EntityWithRefFk.class, "ref");
        Class<?> refPkType = RecordReflection.getRefPkType(refField);
        assertEquals(Integer.class, refPkType);
    }

    // ---- getTableName tests ----

    @Test
    void testGetTableNameDefault() throws SqlTemplateException {
        var tableName = RecordReflection.getTableName(SimpleEntity.class, type -> {
            // Default resolver: camelCase to snake_case.
            return type.type().getSimpleName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
        });
        assertNotNull(tableName);
        // SimpleEntity -> simple_entity
        assertEquals("simple_entity", tableName.name());
    }

    @Test
    void testGetTableNameWithDbTable() throws SqlTemplateException {
        var tableName = RecordReflection.getTableName(CustomTableEntity.class, type -> type.type().getSimpleName());
        assertEquals("custom_table", tableName.name());
    }

    @Test
    void testGetTableNameWithSchema() throws SqlTemplateException {
        var tableName = RecordReflection.getTableName(SchemaTableEntity.class, type -> type.type().getSimpleName());
        assertEquals("annotated_table", tableName.name());
        assertEquals("my_schema", tableName.schema());
    }

    // ---- getColumnName tests ----

    @Test
    void testGetColumnNameWithDbColumn() throws SqlTemplateException {
        RecordField idField = RecordReflection.getRecordField(EntityWithDbColumn.class, "id");
        ColumnName columnName = RecordReflection.getColumnName(idField, field -> field.name());
        assertEquals("pk_col", columnName.name());
    }

    @Test
    void testGetColumnNameDefault() throws SqlTemplateException {
        RecordField nameField = RecordReflection.getRecordField(SimpleEntity.class, "name");
        ColumnName columnName = RecordReflection.getColumnName(nameField, field -> field.name());
        assertEquals("name", columnName.name());
    }

    // ---- findRecordField tests ----

    @Test
    void testFindRecordFieldByType() throws SqlTemplateException {
        var fields = RecordReflection.getRecordFields(EntityWithFk.class);
        Optional<RecordField> found = RecordReflection.findRecordField(fields, ReferencedEntity.class);
        assertTrue(found.isPresent());
        assertEquals("ref", found.get().name());
    }

    @Test
    void testFindRecordFieldByTypeRef() throws SqlTemplateException {
        var fields = RecordReflection.getRecordFields(EntityWithRefFk.class);
        Optional<RecordField> found = RecordReflection.findRecordField(fields, ReferencedEntity.class);
        assertTrue(found.isPresent());
        assertEquals("ref", found.get().name());
    }

    @Test
    void testFindRecordFieldByTypeMissing() throws SqlTemplateException {
        var fields = RecordReflection.getRecordFields(SimpleEntity.class);
        Optional<RecordField> found = RecordReflection.findRecordField(fields, ReferencedEntity.class);
        assertFalse(found.isPresent());
    }

    // ---- Generation strategy for compound PK ----

    @Test
    void testGetGenerationStrategyCompoundPk() throws SqlTemplateException {
        RecordField pkField = RecordReflection.getRecordField(EntityWithCompoundPk.class, "id");
        // Compound PK (record type) always returns NONE.
        assertEquals(GenerationStrategy.NONE, RecordReflection.getGenerationStrategy(pkField));
    }
}
