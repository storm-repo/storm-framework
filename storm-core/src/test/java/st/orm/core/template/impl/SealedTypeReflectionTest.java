package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.Polymorphic.Strategy.JOINED;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import st.orm.Data;
import st.orm.DbTable;
import st.orm.Discriminator;
import st.orm.Discriminator.DiscriminatorType;
import st.orm.Entity;
import st.orm.GenerationStrategy;
import st.orm.PK;
import st.orm.Polymorphic;
import st.orm.core.template.SqlTemplateException;

/**
 * Unit tests for sealed type reflection utilities in RecordReflection.
 */
class SealedTypeReflectionTest {

    // ---- Test model types ----

    // Single-Table (default for sealed Entity)
    @Discriminator
    @DbTable("sta")
    sealed interface STA extends Entity<Integer> permits STCat, STDog {}
    record STCat(@PK Integer id, String name, boolean indoor) implements STA {}
    record STDog(@PK Integer id, String name, int weight) implements STA {}

    // Joined Table (requires @Polymorphic(JOINED))
    @Discriminator
    @Polymorphic(JOINED)
    @DbTable("jta")
    sealed interface JTA extends Entity<Integer> permits JTCat, JTDog {}
    @DbTable("jt_cat") record JTCat(@PK Integer id, String name, boolean indoor) implements JTA {}
    @DbTable("jt_dog") record JTDog(@PK Integer id, String name, int weight) implements JTA {}

    // Polymorphic FK (without @DbTable, using table name resolver)
    sealed interface Commentable extends Data permits TestPost, TestPhoto {}
    record TestPost(@PK Integer id, String title) implements Commentable, Entity<Integer> {}
    record TestPhoto(@PK Integer id, String url) implements Commentable, Entity<Integer> {}

    // Custom discriminator
    @DbTable("custom_animal")
    @Discriminator(column = "animal_type")
    sealed interface CustomAnimal extends Entity<Integer> permits CustomCat {}
    @Discriminator("PERSIAN")
    record CustomCat(@PK Integer id, String name) implements CustomAnimal {}

    // ---- Pattern Detection ----

    @Test
    void detectSingleTablePattern() {
        Optional<RecordReflection.SealedPattern> pattern = RecordReflection.detectSealedPattern(STA.class);
        assertTrue(pattern.isPresent());
        assertEquals(RecordReflection.SealedPattern.SINGLE_TABLE, pattern.get());
    }

    @Test
    void detectJoinedPattern() {
        Optional<RecordReflection.SealedPattern> pattern = RecordReflection.detectSealedPattern(JTA.class);
        assertTrue(pattern.isPresent());
        assertEquals(RecordReflection.SealedPattern.JOINED, pattern.get());
    }

    @Test
    void detectPolymorphicFKPattern() {
        Optional<RecordReflection.SealedPattern> pattern = RecordReflection.detectSealedPattern(Commentable.class);
        assertTrue(pattern.isPresent());
        assertEquals(RecordReflection.SealedPattern.POLYMORPHIC_FK, pattern.get());
    }

    @Test
    void nonSealedTypeReturnsEmpty() {
        Optional<RecordReflection.SealedPattern> pattern = RecordReflection.detectSealedPattern(String.class);
        assertTrue(pattern.isEmpty());
    }

    // ---- Convenience Checks ----

    @Test
    void isSealedEntity() {
        assertTrue(RecordReflection.isSealedEntity(STA.class));
        assertTrue(RecordReflection.isSealedEntity(JTA.class));
        assertFalse(RecordReflection.isSealedEntity(Commentable.class));
        assertFalse(RecordReflection.isSealedEntity(STCat.class));
    }

    @Test
    void isSingleTableEntity() {
        assertTrue(RecordReflection.isSingleTableEntity(STA.class));
        assertFalse(RecordReflection.isSingleTableEntity(JTA.class));
        assertFalse(RecordReflection.isSingleTableEntity(Commentable.class));
    }

    @Test
    void isJoinedEntity() {
        assertFalse(RecordReflection.isJoinedEntity(STA.class));
        assertTrue(RecordReflection.isJoinedEntity(JTA.class));
    }

    @Test
    void isPolymorphicData() {
        assertTrue(RecordReflection.isPolymorphicData(Commentable.class));
        assertFalse(RecordReflection.isPolymorphicData(STA.class));
    }

    // ---- Discriminator Column ----

    @Test
    void defaultDiscriminatorColumn() throws SqlTemplateException {
        assertEquals("dtype", RecordReflection.getDiscriminatorColumn(STA.class));
    }

    @Test
    void customDiscriminatorColumn() throws SqlTemplateException {
        assertEquals("animal_type", RecordReflection.getDiscriminatorColumn(CustomAnimal.class));
    }

    // Missing @Discriminator on sealed Entity
    @DbTable("no_disc")
    sealed interface NoDiscriminator extends Entity<Integer> permits NoDiscSub {}
    record NoDiscSub(@PK Integer id, String name) implements NoDiscriminator {}

    @Test
    void missingDiscriminatorThrows() {
        assertThrows(SqlTemplateException.class, () -> RecordReflection.getDiscriminatorColumn(NoDiscriminator.class));
    }

    @Test
    void missingDiscriminatorFailsValidation() {
        String error = RecordReflection.validateSealedHierarchy(NoDiscriminator.class);
        assertTrue(error.contains("@Discriminator"));
    }

    // ---- Discriminator Value ----

    @Test
    void defaultDiscriminatorValue() {
        assertEquals("STCat", RecordReflection.getDiscriminatorValue(STCat.class, STA.class));
        assertEquals("STDog", RecordReflection.getDiscriminatorValue(STDog.class, STA.class));
    }

    @Test
    void customDiscriminatorValue() {
        assertEquals("PERSIAN", RecordReflection.getDiscriminatorValue(CustomCat.class, CustomAnimal.class));
    }

    @Test
    void polymorphicFKUsesResolvedTableName() {
        assertEquals("test_post", RecordReflection.getDiscriminatorValue(TestPost.class, Commentable.class));
        assertEquals("test_photo", RecordReflection.getDiscriminatorValue(TestPhoto.class, Commentable.class));
    }

    // ---- Resolve Concrete Type ----

    @Test
    void resolveConcreteType() throws SqlTemplateException {
        assertEquals(STCat.class, RecordReflection.resolveConcreteType(STA.class, "STCat"));
        assertEquals(STDog.class, RecordReflection.resolveConcreteType(STA.class, "STDog"));
    }

    @Test
    void resolvePolymorphicConcreteType() throws SqlTemplateException {
        assertEquals(TestPost.class, RecordReflection.resolveConcreteType(Commentable.class, "test_post"));
        assertEquals(TestPhoto.class, RecordReflection.resolveConcreteType(Commentable.class, "test_photo"));
    }

    // ---- Field Partitioning (Joined) ----

    @Test
    void baseFieldNames() {
        List<String> baseFields = RecordReflection.getBaseFieldNames(JTA.class);
        assertTrue(baseFields.contains("id"));
        assertTrue(baseFields.contains("name"));
        assertFalse(baseFields.contains("indoor"));
        assertFalse(baseFields.contains("weight"));
    }

    @Test
    void extensionFieldNames() {
        List<String> catExt = RecordReflection.getExtensionFieldNames(JTCat.class, JTA.class);
        assertEquals(1, catExt.size());
        assertTrue(catExt.contains("indoor"));

        List<String> dogExt = RecordReflection.getExtensionFieldNames(JTDog.class, JTA.class);
        assertEquals(1, dogExt.size());
        assertTrue(dogExt.contains("weight"));
    }

    // ---- Hierarchy Validation ----

    @Test
    void validSingleTableHierarchy() {
        assertEquals("", RecordReflection.validateSealedHierarchy(STA.class));
    }

    @Test
    void validJoinedHierarchy() {
        assertEquals("", RecordReflection.validateSealedHierarchy(JTA.class));
    }

    @Test
    void validPolymorphicFKHierarchy() {
        assertEquals("", RecordReflection.validateSealedHierarchy(Commentable.class));
    }

    // ---- Validation Error Cases ----

    // Mismatched PK generation strategy
    @Discriminator
    sealed interface MismatchedGen extends Entity<Integer> permits MismatchedGenSub1, MismatchedGenSub2 {}
    record MismatchedGenSub1(@PK Integer id, String name) implements MismatchedGen {}
    record MismatchedGenSub2(@PK(generation = GenerationStrategy.NONE) Integer id, String name) implements MismatchedGen {}

    @Test
    void mismatchedPkGenerationStrategyFailsValidation() {
        String error = RecordReflection.validateSealedHierarchy(MismatchedGen.class);
        assertTrue(error.contains("generation strategy"));
    }

    // Duplicate discriminator values
    @Discriminator
    sealed interface DuplicateDisc extends Entity<Integer> permits DupDiscSub1, DupDiscSub2 {}
    @Discriminator("Same") record DupDiscSub1(@PK Integer id) implements DuplicateDisc {}
    @Discriminator("Same") record DupDiscSub2(@PK Integer id) implements DuplicateDisc {}

    @Test
    void duplicateDiscriminatorValueFailsValidation() {
        String error = RecordReflection.validateSealedHierarchy(DuplicateDisc.class);
        assertTrue(error.contains("Duplicate discriminator value"));
        assertTrue(error.contains("Same"));
    }

    // @Discriminator(value) on sealed interface
    @Discriminator("Cat")
    sealed interface DiscValueOnInterface extends Entity<Integer> permits DiscValSub {}
    record DiscValSub(@PK Integer id) implements DiscValueOnInterface {}

    @Test
    void discriminatorValueOnSealedInterfaceFailsValidation() {
        String error = RecordReflection.validateSealedHierarchy(DiscValueOnInterface.class);
        assertTrue(error.contains("value attribute"));
        assertTrue(error.contains("column attribute"));
    }

    // @Discriminator(column) on subtype
    @Discriminator
    sealed interface DiscColumnOnSubtype extends Entity<Integer> permits DiscColSub {}
    @Discriminator(column = "col") record DiscColSub(@PK Integer id) implements DiscColumnOnSubtype {}

    @Test
    void discriminatorColumnOnSubtypeFailsValidation() {
        String error = RecordReflection.validateSealedHierarchy(DiscColumnOnSubtype.class);
        assertTrue(error.contains("column attribute"));
        assertTrue(error.contains("sealed interface"));
    }

    // @Discriminator on Polymorphic FK sealed interface
    @Discriminator
    sealed interface DiscOnPolyFK extends Data permits DiscPolyFKSub {}
    record DiscPolyFKSub(@PK Integer id) implements DiscOnPolyFK, Entity<Integer> {}

    @Test
    void discriminatorOnPolymorphicFKInterfaceFailsValidation() {
        String error = RecordReflection.validateSealedHierarchy(DiscOnPolyFK.class);
        assertTrue(error.contains("@Discriminator"));
        assertTrue(error.contains("@FK field"));
    }

    // @Polymorphic on non-Entity sealed Data interface
    @Polymorphic(JOINED)
    sealed interface PolyOnNonEntity extends Data permits PolyNESub {}
    record PolyNESub(@PK Integer id) implements PolyOnNonEntity, Entity<Integer> {}

    @Test
    void polymorphicOnNonEntityFailsValidation() {
        String error = RecordReflection.validateSealedHierarchy(PolyOnNonEntity.class);
        assertTrue(error.contains("@Polymorphic"));
        assertTrue(error.contains("sealed Entity"));
    }

    // @DbTable on single-table subtype
    @Discriminator
    sealed interface DbTableOnSTSub extends Entity<Integer> permits DbTableSTSub {}
    @DbTable("sub_table") record DbTableSTSub(@PK Integer id) implements DbTableOnSTSub {}

    @Test
    void dbTableOnSingleTableSubtypeFailsValidation() {
        String error = RecordReflection.validateSealedHierarchy(DbTableOnSTSub.class);
        assertTrue(error.contains("@DbTable"));
        assertTrue(error.contains("single-table"));
    }

    // Joined without @Discriminator
    @Polymorphic(JOINED)
    @DbTable("jta_nd")
    sealed interface JTAND extends Entity<Integer> permits JTNDCat, JTNDBird {}
    @DbTable("jtnd_cat") record JTNDCat(@PK Integer id, String name, boolean indoor) implements JTAND {}
    @DbTable("jtnd_bird") record JTNDBird(@PK Integer id, String name) implements JTAND {}

    @Test
    void detectJoinedWithoutDiscriminator() {
        Optional<RecordReflection.SealedPattern> pattern = RecordReflection.detectSealedPattern(JTAND.class);
        assertTrue(pattern.isPresent());
        assertEquals(RecordReflection.SealedPattern.JOINED, pattern.get());
    }

    @Test
    void hasDiscriminatorReturnsFalseForJoinedWithout() {
        assertFalse(RecordReflection.hasDiscriminator(JTAND.class));
    }

    @Test
    void hasDiscriminatorReturnsTrueForJoinedWith() {
        assertTrue(RecordReflection.hasDiscriminator(JTA.class));
    }

    @Test
    void hasDiscriminatorReturnsTrueForSingleTable() {
        assertTrue(RecordReflection.hasDiscriminator(STA.class));
    }

    @Test
    void validJoinedHierarchyWithoutDiscriminator() {
        assertEquals("", RecordReflection.validateSealedHierarchy(JTAND.class));
    }

    @Test
    void joinedWithoutDiscriminatorDefaultColumn() throws SqlTemplateException {
        // Returns default "dtype" as internal key even without @Discriminator.
        assertEquals("dtype", RecordReflection.getDiscriminatorColumn(JTAND.class));
    }

    // Mismatched PK generation strategy in Polymorphic FK
    sealed interface MismatchedGenPolyFK extends Data permits MismatchedGenPolySub1, MismatchedGenPolySub2 {}
    record MismatchedGenPolySub1(@PK Integer id) implements MismatchedGenPolyFK, Entity<Integer> {}
    record MismatchedGenPolySub2(@PK(generation = GenerationStrategy.NONE) Integer id) implements MismatchedGenPolyFK, Entity<Integer> {}

    @Test
    void mismatchedPkGenerationStrategyInPolyFKFailsValidation() {
        String error = RecordReflection.validateSealedHierarchy(MismatchedGenPolyFK.class);
        assertTrue(error.contains("generation strategy"));
    }

    // ---- Discriminator Type Tests (D12) ----

    // INTEGER discriminator type
    @Discriminator(type = DiscriminatorType.INTEGER)
    @DbTable("int_animal")
    sealed interface IntAnimal extends Entity<Integer> permits IntCat, IntDog {}
    @Discriminator("1") record IntCat(@PK Integer id, String name) implements IntAnimal {}
    @Discriminator("2") record IntDog(@PK Integer id, String name) implements IntAnimal {}

    // CHAR discriminator type
    @Discriminator(type = DiscriminatorType.CHAR)
    @DbTable("char_animal")
    sealed interface CharAnimal extends Entity<Integer> permits CharCat, CharDog {}
    @Discriminator("C") record CharCat(@PK Integer id, String name) implements CharAnimal {}
    @Discriminator("D") record CharDog(@PK Integer id, String name) implements CharAnimal {}

    @Test
    void getDiscriminatorTypeReturnsStringByDefault() {
        assertEquals(DiscriminatorType.STRING, RecordReflection.getDiscriminatorType(STA.class));
    }

    @Test
    void getDiscriminatorTypeReturnsInteger() {
        assertEquals(DiscriminatorType.INTEGER, RecordReflection.getDiscriminatorType(IntAnimal.class));
    }

    @Test
    void getDiscriminatorTypeReturnsChar() {
        assertEquals(DiscriminatorType.CHAR, RecordReflection.getDiscriminatorType(CharAnimal.class));
    }

    @Test
    void getDiscriminatorColumnJavaTypeForString() {
        assertEquals(String.class, RecordReflection.getDiscriminatorColumnJavaType(STA.class));
    }

    @Test
    void getDiscriminatorColumnJavaTypeForInteger() {
        assertEquals(Integer.class, RecordReflection.getDiscriminatorColumnJavaType(IntAnimal.class));
    }

    @Test
    void getDiscriminatorColumnJavaTypeForChar() {
        assertEquals(Character.class, RecordReflection.getDiscriminatorColumnJavaType(CharAnimal.class));
    }

    @Test
    void getDiscriminatorValueReturnsCorrectObjectType() {
        // STRING type returns String.
        Object stringValue = RecordReflection.getDiscriminatorValue(STCat.class, STA.class);
        assertEquals("STCat", stringValue);
        assertTrue(stringValue instanceof String);

        // INTEGER type returns Integer.
        Object intValue = RecordReflection.getDiscriminatorValue(IntCat.class, IntAnimal.class);
        assertEquals(1, intValue);
        assertTrue(intValue instanceof Integer);

        // CHAR type returns Character.
        Object charValue = RecordReflection.getDiscriminatorValue(CharCat.class, CharAnimal.class);
        assertEquals('C', charValue);
        assertTrue(charValue instanceof Character);
    }

    @Test
    void resolveConcreteTypeWithIntegerDiscriminator() throws SqlTemplateException {
        assertEquals(IntCat.class, RecordReflection.resolveConcreteType(IntAnimal.class, 1));
        assertEquals(IntDog.class, RecordReflection.resolveConcreteType(IntAnimal.class, 2));
    }

    @Test
    void resolveConcreteTypeWithCharDiscriminator() throws SqlTemplateException {
        assertEquals(CharCat.class, RecordReflection.resolveConcreteType(CharAnimal.class, 'C'));
        assertEquals(CharDog.class, RecordReflection.resolveConcreteType(CharAnimal.class, 'D'));
    }

    @Test
    void normalizeDiscriminatorValueForString() {
        Object result = RecordReflection.normalizeDiscriminatorValue("Cat", DiscriminatorType.STRING);
        assertEquals("Cat", result);
        assertTrue(result instanceof String);
    }

    @Test
    void normalizeDiscriminatorValueForInteger() {
        // From Number (common JDBC result).
        Object fromNumber = RecordReflection.normalizeDiscriminatorValue(1L, DiscriminatorType.INTEGER);
        assertEquals(1, fromNumber);
        assertTrue(fromNumber instanceof Integer);

        // From String (fallback).
        Object fromString = RecordReflection.normalizeDiscriminatorValue("42", DiscriminatorType.INTEGER);
        assertEquals(42, fromString);
        assertTrue(fromString instanceof Integer);
    }

    @Test
    void normalizeDiscriminatorValueForChar() {
        // From String of length 1.
        Object fromString = RecordReflection.normalizeDiscriminatorValue("C", DiscriminatorType.CHAR);
        assertEquals('C', fromString);
        assertTrue(fromString instanceof Character);

        // From Character.
        Object fromChar = RecordReflection.normalizeDiscriminatorValue('D', DiscriminatorType.CHAR);
        assertEquals('D', fromChar);
        assertTrue(fromChar instanceof Character);
    }

    @Test
    void validIntegerDiscriminatorHierarchy() {
        assertEquals("", RecordReflection.validateSealedHierarchy(IntAnimal.class));
    }

    @Test
    void validCharDiscriminatorHierarchy() {
        assertEquals("", RecordReflection.validateSealedHierarchy(CharAnimal.class));
    }

    // ---- @Polymorphic(SINGLE_TABLE) Handling (B3/D12) ----

    @Polymorphic(Polymorphic.Strategy.SINGLE_TABLE)
    @Discriminator
    @DbTable("explicit_sti")
    sealed interface ExplicitSTI extends Entity<Integer> permits ExplicitSTISub {}
    record ExplicitSTISub(@PK Integer id, String name) implements ExplicitSTI {}

    @Test
    void explicitSingleTablePatternDetected() {
        Optional<RecordReflection.SealedPattern> pattern = RecordReflection.detectSealedPattern(ExplicitSTI.class);
        assertTrue(pattern.isPresent());
        assertEquals(RecordReflection.SealedPattern.SINGLE_TABLE, pattern.get());
    }

    @Test
    void explicitSingleTableValidation() {
        assertEquals("", RecordReflection.validateSealedHierarchy(ExplicitSTI.class));
    }

    // ---- Near-miss Field Validation (B4/D12) ----

    @Discriminator
    sealed interface NearMissFields extends Entity<Integer> permits NearMissSub1, NearMissSub2 {}
    record NearMissSub1(@PK Integer id, String name) implements NearMissFields {}
    record NearMissSub2(@PK Integer id, int name) implements NearMissFields {}

    @Test
    void nearMissFieldTypeFailsValidation() {
        String error = RecordReflection.validateSealedHierarchy(NearMissFields.class);
        assertTrue(error.contains("different types"));
        assertTrue(error.contains("name"));
    }
}
