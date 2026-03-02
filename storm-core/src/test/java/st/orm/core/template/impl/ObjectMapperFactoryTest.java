package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Parameter;
import org.junit.jupiter.api.Test;
import st.orm.Data;
import st.orm.Ref;
import st.orm.core.spi.RefFactory;
import st.orm.core.template.SqlTemplateException;

/**
 * Tests for {@link ObjectMapperFactory}.
 */
public class ObjectMapperFactoryTest {

    private static final RefFactory NULL_REF_FACTORY = new RefFactory() {
        @Override
        public <T extends Data, ID> Ref<T> create(Class<T> type, ID pk) {
            return null;
        }
        @Override
        public <T extends Data, ID> Ref<T> create(T record, ID pk) {
            return null;
        }
    };

    // Simple class with a matching constructor.
    public static class SimpleType {
        private final String value;
        public SimpleType(String value) {
            this.value = value;
        }
        public String value() { return value; }
    }

    // Class with no single-arg constructor (only 2-arg).
    public static class TwoArgType {
        private final String first;
        private final String second;
        public TwoArgType(String first, String second) {
            this.first = first;
            this.second = second;
        }
    }

    // Class with a StringBuilder parameter.
    public static class StringBuilderType {
        private final StringBuilder builder;
        public StringBuilderType(StringBuilder builder) {
            this.builder = builder;
        }
        public StringBuilder builder() { return builder; }
    }

    @Test
    public void testGetObjectMapperForPrimitive() throws SqlTemplateException {
        var mapper = ObjectMapperFactory.getObjectMapper(1, int.class, NULL_REF_FACTORY);
        assertTrue(mapper.isPresent());
    }

    @Test
    public void testGetObjectMapperForPrimitiveParameterTypes() throws SqlTemplateException {
        var mapper = ObjectMapperFactory.getObjectMapper(1, int.class, NULL_REF_FACTORY);
        assertTrue(mapper.isPresent());
        assertNotNull(mapper.get().getParameterTypes());
    }

    @Test
    public void testGetObjectMapperForSimpleClass() throws SqlTemplateException {
        var mapper = ObjectMapperFactory.getObjectMapper(1, SimpleType.class, NULL_REF_FACTORY);
        assertTrue(mapper.isPresent());
        SimpleType instance = mapper.get().newInstance(new Object[]{"hello"});
        assertEquals("hello", instance.value());
    }

    @Test
    public void testGetObjectMapperForTwoArgClass() throws SqlTemplateException {
        var mapper = ObjectMapperFactory.getObjectMapper(2, TwoArgType.class, NULL_REF_FACTORY);
        assertTrue(mapper.isPresent());
    }

    @Test
    public void testGetObjectMapperNoMatchingConstructor() throws SqlTemplateException {
        var mapper = ObjectMapperFactory.getObjectMapper(5, SimpleType.class, NULL_REF_FACTORY);
        assertFalse(mapper.isPresent());
    }

    @Test
    public void testGetObjectMapperForStringBuilderType() throws SqlTemplateException {
        var mapper = ObjectMapperFactory.getObjectMapper(1, StringBuilderType.class, NULL_REF_FACTORY);
        assertTrue(mapper.isPresent());
        // The StringBuilder parameter type should be replaced with String in parameterTypes.
        assertArrayEquals(new Class<?>[]{String.class}, mapper.get().getParameterTypes());
        // When creating an instance, a String should be converted to StringBuilder.
        StringBuilderType instance = mapper.get().newInstance(new Object[]{"test"});
        assertEquals("test", instance.builder().toString());
    }

    @Test
    public void testGetObjectMapperForEnum() throws SqlTemplateException {
        var mapper = ObjectMapperFactory.getObjectMapper(1, TestEnum.class, NULL_REF_FACTORY);
        assertTrue(mapper.isPresent());
    }

    enum TestEnum { A, B, C }

    @Test
    public void testIsNonnullWithPKAnnotation() throws NoSuchMethodException {
        // Test parameter annotated with @PK.
        var constructor = AnnotatedRecord.class.getDeclaredConstructors()[0];
        Parameter[] parameters = constructor.getParameters();
        assertTrue(ObjectMapperFactory.isNonnull(parameters[0])); // @PK
    }

    record AnnotatedRecord(@st.orm.PK int id, String name) {}
}
