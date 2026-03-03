package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import st.orm.PersistenceException;
import st.orm.core.template.SqlTemplateException;

/**
 * Tests for {@link PrimitiveMapper} covering factory creation for primitive types.
 */
class PrimitiveMapperTest {

    @Test
    void testIntFactory() throws SqlTemplateException {
        Optional<ObjectMapper<Integer>> factory = PrimitiveMapper.getFactory(1, int.class);
        assertTrue(factory.isPresent());
        ObjectMapper<Integer> mapper = factory.get();
        assertArrayEquals(new Class<?>[] { int.class }, mapper.getParameterTypes());
        assertEquals(42, mapper.newInstance(new Object[] { 42 }));
    }

    @Test
    void testIntFactoryWithLongInput() throws SqlTemplateException {
        Optional<ObjectMapper<Integer>> factory = PrimitiveMapper.getFactory(1, int.class);
        assertTrue(factory.isPresent());
        assertEquals(42, factory.get().newInstance(new Object[] { 42L }));
    }

    @Test
    void testLongFactory() throws SqlTemplateException {
        Optional<ObjectMapper<Long>> factory = PrimitiveMapper.getFactory(1, long.class);
        assertTrue(factory.isPresent());
        assertEquals(100L, factory.get().newInstance(new Object[] { 100L }));
    }

    @Test
    void testLongFactoryWithIntInput() throws SqlTemplateException {
        Optional<ObjectMapper<Long>> factory = PrimitiveMapper.getFactory(1, long.class);
        assertTrue(factory.isPresent());
        assertEquals(100L, factory.get().newInstance(new Object[] { 100 }));
    }

    @Test
    void testFloatFactory() throws SqlTemplateException {
        Optional<ObjectMapper<Float>> factory = PrimitiveMapper.getFactory(1, float.class);
        assertTrue(factory.isPresent());
        assertEquals(3.14f, factory.get().newInstance(new Object[] { 3.14f }));
    }

    @Test
    void testFloatFactoryWithDoubleInput() throws SqlTemplateException {
        Optional<ObjectMapper<Float>> factory = PrimitiveMapper.getFactory(1, float.class);
        assertTrue(factory.isPresent());
        assertEquals(3.14f, factory.get().newInstance(new Object[] { 3.14 }));
    }

    @Test
    void testDoubleFactory() throws SqlTemplateException {
        Optional<ObjectMapper<Double>> factory = PrimitiveMapper.getFactory(1, double.class);
        assertTrue(factory.isPresent());
        assertEquals(2.718, factory.get().newInstance(new Object[] { 2.718 }));
    }

    @Test
    void testBooleanFactory() throws SqlTemplateException {
        Optional<ObjectMapper<Boolean>> factory = PrimitiveMapper.getFactory(1, boolean.class);
        assertTrue(factory.isPresent());
        assertEquals(true, factory.get().newInstance(new Object[] { true }));
    }

    @Test
    void testMultipleColumnCountReturnsEmpty() {
        Optional<ObjectMapper<Integer>> factory = PrimitiveMapper.getFactory(2, int.class);
        assertFalse(factory.isPresent());
    }

    @Test
    void testZeroColumnCountReturnsEmpty() {
        Optional<ObjectMapper<Integer>> factory = PrimitiveMapper.getFactory(0, int.class);
        assertFalse(factory.isPresent());
    }

    @Test
    void testNonPrimitiveTypeThrows() {
        assertThrows(PersistenceException.class, () -> PrimitiveMapper.getFactory(1, Integer.class));
    }

    @Test
    void testNonPrimitiveStringTypeThrows() {
        assertThrows(PersistenceException.class, () -> PrimitiveMapper.getFactory(1, String.class));
    }

    @Test
    void testByteFactory() throws SqlTemplateException {
        Optional<ObjectMapper<Byte>> factory = PrimitiveMapper.getFactory(1, byte.class);
        assertTrue(factory.isPresent());
        assertEquals((byte) 42, factory.get().newInstance(new Object[] { (byte) 42 }));
    }

    @Test
    void testShortFactory() throws SqlTemplateException {
        Optional<ObjectMapper<Short>> factory = PrimitiveMapper.getFactory(1, short.class);
        assertTrue(factory.isPresent());
        assertEquals((short) 42, factory.get().newInstance(new Object[] { (short) 42 }));
    }

    @Test
    void testCharFactory() throws SqlTemplateException {
        Optional<ObjectMapper<Character>> factory = PrimitiveMapper.getFactory(1, char.class);
        assertTrue(factory.isPresent());
        assertEquals('A', factory.get().newInstance(new Object[] { 'A' }));
    }
}
