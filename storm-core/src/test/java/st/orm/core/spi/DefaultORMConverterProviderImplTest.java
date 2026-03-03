package st.orm.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import st.orm.Convert;
import st.orm.Converter;
import st.orm.Data;
import st.orm.DbTable;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;

/**
 * Tests for {@link DefaultORMConverterProviderImpl}, covering converter discovery,
 * explicit converter creation, and error paths.
 */
public class DefaultORMConverterProviderImplTest {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    // ==================== Converter implementations for testing ====================

    /**
     * A valid, concrete converter that converts between String (database) and Integer (entity).
     */
    public static class StringToIntegerConverter implements Converter<String, Integer> {
        @Override
        public String toDatabase(@Nullable Integer value) {
            return value == null ? null : value.toString();
        }

        @Override
        public Integer fromDatabase(@Nullable String dbValue) {
            return dbValue == null ? null : Integer.parseInt(dbValue);
        }
    }

    /**
     * A converter class that does not implement the Converter interface.
     */
    public static class NotAConverter {
        // Intentionally does not implement Converter.
    }

    /**
     * A converter that has no no-arg constructor.
     */
    public static class NoDefaultConstructorConverter implements Converter<String, Integer> {
        private final String prefix;

        public NoDefaultConstructorConverter(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String toDatabase(@Nullable Integer value) {
            return value == null ? null : prefix + value;
        }

        @Override
        public Integer fromDatabase(@Nullable String dbValue) {
            return dbValue == null ? null : Integer.parseInt(dbValue);
        }
    }

    /**
     * A converter with raw generics (no type parameters bound), for testing resolveConverterTypes returning null.
     */
    @SuppressWarnings("rawtypes")
    public static class RawGenericConverter implements Converter {
        @Override
        public Object toDatabase(@Nullable Object value) {
            return value;
        }

        @Override
        public Object fromDatabase(@Nullable Object dbValue) {
            return dbValue;
        }
    }

    // ==================== Test records ====================

    /**
     * Record with an explicit @Convert annotation pointing to a valid converter.
     */
    @DbTable("convert_test")
    record EntityWithExplicitConverter(
            @PK int id,
            @Convert(converter = StringToIntegerConverter.class) Integer convertedField
    ) implements Data {}

    /**
     * Record with @Convert(disableConversion = true).
     */
    @DbTable("disable_test")
    record EntityWithDisabledConversion(
            @PK int id,
            @Convert(disableConversion = true) String disabledField
    ) implements Data {}

    /**
     * Record with @Convert pointing to a class that is not a Converter.
     */
    @DbTable("invalid_converter_test")
    record EntityWithInvalidConverter(
            @PK int id,
            @Convert(converter = NotAConverter.class) String badField
    ) implements Data {}

    /**
     * Record with @Convert pointing to a converter with no default constructor.
     */
    @DbTable("no_ctor_test")
    record EntityWithNoCtorConverter(
            @PK int id,
            @Convert(converter = NoDefaultConstructorConverter.class) Integer noCtorField
    ) implements Data {}

    /**
     * Record with @Convert pointing to a converter with raw generics (no resolvable type args).
     */
    @DbTable("raw_generics_test")
    record EntityWithRawGenericsConverter(
            @PK int id,
            @Convert(converter = RawGenericConverter.class) String rawField
    ) implements Data {}

    /**
     * Record with no @Convert annotation (for testing default converter resolution).
     */
    @DbTable("plain_test")
    record PlainEntity(
            @PK int id,
            String name
    ) implements Data {}

    // ==================== Helper ====================

    private RecordField getField(Class<?> recordClass, String fieldName) {
        RecordType recordType = REFLECTION.getRecordType(recordClass);
        return recordType.fields().stream()
                .filter(field -> field.name().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Field not found: " + fieldName));
    }

    // ==================== Tests ====================

    @Test
    public void testExplicitConverterSucceeds() throws Exception {
        // @Convert(converter = StringToIntegerConverter.class) should resolve correctly.
        // Covers lines 146, 149, 196, 205-217.
        DefaultORMConverterProviderImpl provider = new DefaultORMConverterProviderImpl();
        RecordField field = getField(EntityWithExplicitConverter.class, "convertedField");
        Optional<ORMConverter> result = provider.getConverter(field);
        assertTrue(result.isPresent(), "Expected converter to be resolved for explicit @Convert");
        ORMConverter converter = result.get();
        assertEquals(1, converter.getParameterCount());
        assertEquals(List.of(String.class), converter.getParameterTypes());
    }

    @Test
    public void testDisableConversionReturnsEmpty() {
        // @Convert(disableConversion = true) should return empty.
        // Covers line 142-144.
        DefaultORMConverterProviderImpl provider = new DefaultORMConverterProviderImpl();
        RecordField field = getField(EntityWithDisabledConversion.class, "disabledField");
        Optional<ORMConverter> result = provider.getConverter(field);
        assertTrue(result.isEmpty(), "Expected empty for disabled conversion");
    }

    @Test
    public void testInvalidConverterClassThrows() {
        // @Convert(converter = NotAConverter.class) should throw PersistenceException.
        // Covers lines 187-192.
        DefaultORMConverterProviderImpl provider = new DefaultORMConverterProviderImpl();
        RecordField field = getField(EntityWithInvalidConverter.class, "badField");
        var exception = assertThrows(PersistenceException.class, () -> provider.getConverter(field));
        assertTrue(exception.getMessage().contains("does not implement"),
                "Expected message about not implementing Converter, got: " + exception.getMessage());
    }

    @Test
    public void testNoDefaultConstructorThrows() {
        // @Convert(converter = NoDefaultConstructorConverter.class) should throw PersistenceException.
        // Covers lines 197-202.
        DefaultORMConverterProviderImpl provider = new DefaultORMConverterProviderImpl();
        RecordField field = getField(EntityWithNoCtorConverter.class, "noCtorField");
        var exception = assertThrows(PersistenceException.class, () -> provider.getConverter(field));
        assertTrue(exception.getMessage().contains("Failed to instantiate converter"),
                "Expected instantiation failure message, got: " + exception.getMessage());
    }

    @Test
    public void testRawGenericsConverterThrows() {
        // @Convert(converter = RawGenericConverter.class) should throw because types cannot be resolved.
        // Covers lines 206-211.
        DefaultORMConverterProviderImpl provider = new DefaultORMConverterProviderImpl();
        RecordField field = getField(EntityWithRawGenericsConverter.class, "rawField");
        var exception = assertThrows(PersistenceException.class, () -> provider.getConverter(field));
        assertTrue(exception.getMessage().contains("Cannot resolve generic types"),
                "Expected generic type resolution failure, got: " + exception.getMessage());
    }

    @Test
    public void testPlainFieldWithNoMatchingDefaultConverterReturnsEmpty() {
        // A field with no @Convert annotation and no matching default converter should return empty.
        // Covers the resolveDefaultConverter path returning Optional.empty().
        DefaultORMConverterProviderImpl provider = new DefaultORMConverterProviderImpl();
        RecordField field = getField(PlainEntity.class, "name");
        Optional<ORMConverter> result = provider.getConverter(field);
        // String type has no default converter registered, so should be empty.
        assertTrue(result.isEmpty(), "Expected empty for plain String field with no default converter");
    }

    @Test
    public void testNullFieldThrowsNullPointerException() {
        // Passing null should throw NullPointerException (requireNonNull).
        // Covers line 139.
        DefaultORMConverterProviderImpl provider = new DefaultORMConverterProviderImpl();
        assertThrows(NullPointerException.class, () -> provider.getConverter(null));
    }
}
