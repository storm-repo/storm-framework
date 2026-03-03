package st.orm.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.annotation.Nullable;
import java.util.List;
import org.junit.jupiter.api.Test;
import st.orm.Converter;
import st.orm.Data;
import st.orm.DbTable;
import st.orm.PK;
import st.orm.core.template.SqlTemplateException;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;

/**
 * Unit tests for {@link DefaultORMConverterImpl} covering toDatabase, fromDatabase,
 * getParameterCount, getParameterTypes, and getColumns methods.
 */
public class DefaultORMConverterImplTest {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    // Converter implementations

    public static class IntToStringConverter implements Converter<String, Integer> {
        @Override
        public String toDatabase(@Nullable Integer value) {
            return value == null ? null : "NUM:" + value;
        }

        @Override
        public Integer fromDatabase(@Nullable String dbValue) {
            return dbValue == null ? null : Integer.parseInt(dbValue.substring(4));
        }
    }

    // Test records

    @DbTable("converter_test")
    record ConverterTestEntity(
            @PK int id,
            Integer convertedField
    ) implements Data {}

    // Helper

    private RecordField getField(Class<?> recordClass, String fieldName) {
        RecordType recordType = REFLECTION.getRecordType(recordClass);
        return recordType.fields().stream()
                .filter(field -> field.name().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Field not found: " + fieldName));
    }

    // Tests

    @Test
    public void testGetParameterCountReturnsOne() {
        RecordField field = getField(ConverterTestEntity.class, "convertedField");
        DefaultORMConverterImpl<String, Integer> converter =
                new DefaultORMConverterImpl<>(field, new IntToStringConverter(), String.class);
        assertEquals(1, converter.getParameterCount());
    }

    @Test
    public void testGetParameterTypesReturnsDatabaseType() throws SqlTemplateException {
        RecordField field = getField(ConverterTestEntity.class, "convertedField");
        DefaultORMConverterImpl<String, Integer> converter =
                new DefaultORMConverterImpl<>(field, new IntToStringConverter(), String.class);
        assertEquals(List.of(String.class), converter.getParameterTypes());
    }

    @Test
    public void testFromDatabaseConvertsNonNullValue() throws SqlTemplateException {
        RecordField field = getField(ConverterTestEntity.class, "convertedField");
        DefaultORMConverterImpl<String, Integer> converter =
                new DefaultORMConverterImpl<>(field, new IntToStringConverter(), String.class);
        // Provide a mock RefFactory since it's unused by this converter.
        RefFactory refFactory = new RefFactory() {
            @Override
            public <T extends Data, ID> st.orm.Ref<T> create(Class<T> type, ID pk) {
                throw new UnsupportedOperationException();
            }
            @Override
            public <T extends Data, ID> st.orm.Ref<T> create(T record, ID pk) {
                throw new UnsupportedOperationException();
            }
        };
        Object result = converter.fromDatabase(new Object[]{"NUM:42"}, refFactory);
        assertNotNull(result);
        assertEquals(42, result);
    }

    @Test
    public void testFromDatabaseReturnsNullForNullValue() throws SqlTemplateException {
        RecordField field = getField(ConverterTestEntity.class, "convertedField");
        DefaultORMConverterImpl<String, Integer> converter =
                new DefaultORMConverterImpl<>(field, new IntToStringConverter(), String.class);
        RefFactory refFactory = new RefFactory() {
            @Override
            public <T extends Data, ID> st.orm.Ref<T> create(Class<T> type, ID pk) {
                throw new UnsupportedOperationException();
            }
            @Override
            public <T extends Data, ID> st.orm.Ref<T> create(T record, ID pk) {
                throw new UnsupportedOperationException();
            }
        };
        Object result = converter.fromDatabase(new Object[]{null}, refFactory);
        assertNull(result);
    }

    @Test
    public void testFromDatabaseReturnsNullForEmptyValues() throws SqlTemplateException {
        RecordField field = getField(ConverterTestEntity.class, "convertedField");
        DefaultORMConverterImpl<String, Integer> converter =
                new DefaultORMConverterImpl<>(field, new IntToStringConverter(), String.class);
        RefFactory refFactory = new RefFactory() {
            @Override
            public <T extends Data, ID> st.orm.Ref<T> create(Class<T> type, ID pk) {
                throw new UnsupportedOperationException();
            }
            @Override
            public <T extends Data, ID> st.orm.Ref<T> create(T record, ID pk) {
                throw new UnsupportedOperationException();
            }
        };
        // Empty values array should return null.
        Object result = converter.fromDatabase(new Object[]{}, refFactory);
        assertNull(result);
    }

    @Test
    public void testToDatabaseConvertsNonNullRecord() throws SqlTemplateException {
        RecordField field = getField(ConverterTestEntity.class, "convertedField");
        DefaultORMConverterImpl<String, Integer> converter =
                new DefaultORMConverterImpl<>(field, new IntToStringConverter(), String.class);
        ConverterTestEntity entity = new ConverterTestEntity(1, 42);
        List<Object> result = converter.toDatabase(entity);
        assertEquals(1, result.size());
        assertEquals("NUM:42", result.get(0));
    }

    @Test
    public void testToDatabaseHandlesNullRecord() throws SqlTemplateException {
        RecordField field = getField(ConverterTestEntity.class, "convertedField");
        DefaultORMConverterImpl<String, Integer> converter =
                new DefaultORMConverterImpl<>(field, new IntToStringConverter(), String.class);
        List<Object> result = converter.toDatabase(null);
        assertEquals(1, result.size());
        assertNull(result.get(0));
    }

    @Test
    public void testFromDatabaseThrowsSqlTemplateExceptionOnConversionError() {
        RecordField field = getField(ConverterTestEntity.class, "convertedField");
        // Create a converter that will throw on fromDatabase.
        Converter<String, Integer> failingConverter = new Converter<>() {
            @Override
            public String toDatabase(@Nullable Integer value) {
                return null;
            }
            @Override
            public Integer fromDatabase(@Nullable String dbValue) {
                throw new RuntimeException("Conversion error");
            }
        };
        DefaultORMConverterImpl<String, Integer> converter =
                new DefaultORMConverterImpl<>(field, failingConverter, String.class);
        RefFactory refFactory = new RefFactory() {
            @Override
            public <T extends Data, ID> st.orm.Ref<T> create(Class<T> type, ID pk) {
                throw new UnsupportedOperationException();
            }
            @Override
            public <T extends Data, ID> st.orm.Ref<T> create(T record, ID pk) {
                throw new UnsupportedOperationException();
            }
        };
        assertThrows(SqlTemplateException.class,
                () -> converter.fromDatabase(new Object[]{"some value"}, refFactory));
    }
}
