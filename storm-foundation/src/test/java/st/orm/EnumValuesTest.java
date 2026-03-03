package st.orm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests that all enum types in storm-foundation define the expected constants and that
 * {@code values()} and {@code valueOf()} behave correctly. These tests ensure that the enum
 * constants are accessible and maintain the expected ordinal positions, catching accidental
 * renames or reorderings that could break serialization or database mappings.
 */
class EnumValuesTest {

    // UpdateMode

    @Test
    void updateModeValues() {
        UpdateMode[] values = UpdateMode.values();
        assertEquals(3, values.length);
        assertEquals(UpdateMode.OFF, values[0]);
        assertEquals(UpdateMode.ENTITY, values[1]);
        assertEquals(UpdateMode.FIELD, values[2]);
    }

    @Test
    void updateModeValueOf() {
        assertEquals(UpdateMode.OFF, UpdateMode.valueOf("OFF"));
        assertEquals(UpdateMode.ENTITY, UpdateMode.valueOf("ENTITY"));
        assertEquals(UpdateMode.FIELD, UpdateMode.valueOf("FIELD"));
    }

    // TemporalType

    @Test
    void temporalTypeValues() {
        TemporalType[] values = TemporalType.values();
        assertEquals(3, values.length);
        assertEquals(TemporalType.DATE, values[0]);
        assertEquals(TemporalType.TIME, values[1]);
        assertEquals(TemporalType.TIMESTAMP, values[2]);
    }

    @Test
    void temporalTypeValueOf() {
        assertEquals(TemporalType.DATE, TemporalType.valueOf("DATE"));
        assertEquals(TemporalType.TIME, TemporalType.valueOf("TIME"));
        assertEquals(TemporalType.TIMESTAMP, TemporalType.valueOf("TIMESTAMP"));
    }

    // SelectMode

    @Test
    void selectModeValues() {
        SelectMode[] values = SelectMode.values();
        assertEquals(3, values.length);
        assertEquals(SelectMode.PK, values[0]);
        assertEquals(SelectMode.DECLARED, values[1]);
        assertEquals(SelectMode.NESTED, values[2]);
    }

    @Test
    void selectModeValueOf() {
        assertEquals(SelectMode.PK, SelectMode.valueOf("PK"));
        assertEquals(SelectMode.DECLARED, SelectMode.valueOf("DECLARED"));
        assertEquals(SelectMode.NESTED, SelectMode.valueOf("NESTED"));
    }

    // ResolveScope

    @Test
    void resolveScopeValues() {
        ResolveScope[] values = ResolveScope.values();
        assertEquals(3, values.length);
        assertEquals(ResolveScope.CASCADE, values[0]);
        assertEquals(ResolveScope.INNER, values[1]);
        assertEquals(ResolveScope.OUTER, values[2]);
    }

    @Test
    void resolveScopeValueOf() {
        assertEquals(ResolveScope.CASCADE, ResolveScope.valueOf("CASCADE"));
        assertEquals(ResolveScope.INNER, ResolveScope.valueOf("INNER"));
        assertEquals(ResolveScope.OUTER, ResolveScope.valueOf("OUTER"));
    }

    // GenerationStrategy

    @Test
    void generationStrategyValues() {
        GenerationStrategy[] values = GenerationStrategy.values();
        assertEquals(3, values.length);
        assertEquals(GenerationStrategy.NONE, values[0]);
        assertEquals(GenerationStrategy.IDENTITY, values[1]);
        assertEquals(GenerationStrategy.SEQUENCE, values[2]);
    }

    @Test
    void generationStrategyValueOf() {
        assertEquals(GenerationStrategy.NONE, GenerationStrategy.valueOf("NONE"));
        assertEquals(GenerationStrategy.IDENTITY, GenerationStrategy.valueOf("IDENTITY"));
        assertEquals(GenerationStrategy.SEQUENCE, GenerationStrategy.valueOf("SEQUENCE"));
    }

    // EnumType

    @Test
    void enumTypeValues() {
        EnumType[] values = EnumType.values();
        assertEquals(2, values.length);
        assertEquals(EnumType.NAME, values[0]);
        assertEquals(EnumType.ORDINAL, values[1]);
    }

    @Test
    void enumTypeValueOf() {
        assertEquals(EnumType.NAME, EnumType.valueOf("NAME"));
        assertEquals(EnumType.ORDINAL, EnumType.valueOf("ORDINAL"));
    }

    // DirtyCheck

    @Test
    void dirtyCheckValues() {
        DirtyCheck[] values = DirtyCheck.values();
        assertEquals(3, values.length);
        assertEquals(DirtyCheck.DEFAULT, values[0]);
        assertEquals(DirtyCheck.INSTANCE, values[1]);
        assertEquals(DirtyCheck.VALUE, values[2]);
    }

    @Test
    void dirtyCheckValueOf() {
        assertEquals(DirtyCheck.DEFAULT, DirtyCheck.valueOf("DEFAULT"));
        assertEquals(DirtyCheck.INSTANCE, DirtyCheck.valueOf("INSTANCE"));
        assertEquals(DirtyCheck.VALUE, DirtyCheck.valueOf("VALUE"));
    }

    // Polymorphic.Strategy

    @Test
    void polymorphicStrategyValues() {
        Polymorphic.Strategy[] values = Polymorphic.Strategy.values();
        assertEquals(2, values.length);
        assertEquals(Polymorphic.Strategy.SINGLE_TABLE, values[0]);
        assertEquals(Polymorphic.Strategy.JOINED, values[1]);
    }

    @Test
    void polymorphicStrategyValueOf() {
        assertEquals(Polymorphic.Strategy.SINGLE_TABLE, Polymorphic.Strategy.valueOf("SINGLE_TABLE"));
        assertEquals(Polymorphic.Strategy.JOINED, Polymorphic.Strategy.valueOf("JOINED"));
    }

    // Discriminator.DiscriminatorType

    @Test
    void discriminatorTypeValues() {
        Discriminator.DiscriminatorType[] values = Discriminator.DiscriminatorType.values();
        assertEquals(3, values.length);
        assertEquals(Discriminator.DiscriminatorType.STRING, values[0]);
        assertEquals(Discriminator.DiscriminatorType.INTEGER, values[1]);
        assertEquals(Discriminator.DiscriminatorType.CHAR, values[2]);
    }

    @Test
    void discriminatorTypeValueOf() {
        assertEquals(Discriminator.DiscriminatorType.STRING, Discriminator.DiscriminatorType.valueOf("STRING"));
        assertEquals(Discriminator.DiscriminatorType.INTEGER, Discriminator.DiscriminatorType.valueOf("INTEGER"));
        assertEquals(Discriminator.DiscriminatorType.CHAR, Discriminator.DiscriminatorType.valueOf("CHAR"));
    }

}
