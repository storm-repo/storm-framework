package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import st.orm.PersistenceException;
import st.orm.core.template.SqlTemplateException;

/**
 * Tests for {@link EnumMapper}.
 */
public class EnumMapperTest {

    enum Color { RED, GREEN, BLUE }

    @Test
    public void testGetFactoryForNonEnumThrows() {
        assertThrows(PersistenceException.class, () -> EnumMapper.getFactory(1, String.class));
    }

    @Test
    public void testGetFactoryMultiColumnReturnsEmpty() {
        assertTrue(EnumMapper.getFactory(2, Color.class).isEmpty());
    }

    @Test
    public void testGetFactoryReturnsMapperForSingleColumn() {
        var mapperOptional = EnumMapper.getFactory(1, Color.class);
        assertTrue(mapperOptional.isPresent());
    }

    @Test
    public void testMapperParameterTypes() throws SqlTemplateException {
        var mapper = EnumMapper.getFactory(1, Color.class).orElseThrow();
        Class<?>[] types = mapper.getParameterTypes();
        assertArrayEquals(new Class<?>[] { Color.class }, types);
    }

    @Test
    public void testMapperFromString() throws SqlTemplateException {
        var mapper = EnumMapper.getFactory(1, Color.class).orElseThrow();
        Color result = mapper.newInstance(new Object[] { "GREEN" });
        assertEquals(Color.GREEN, result);
    }

    @Test
    public void testMapperFromOrdinal() throws SqlTemplateException {
        var mapper = EnumMapper.getFactory(1, Color.class).orElseThrow();
        Color result = mapper.newInstance(new Object[] { 2 });
        assertEquals(Color.BLUE, result);
    }

    @Test
    public void testMapperFromNull() throws SqlTemplateException {
        var mapper = EnumMapper.getFactory(1, Color.class).orElseThrow();
        Color result = mapper.newInstance(new Object[] { null });
        assertNull(result);
    }

    @Test
    public void testMapperInvalidStringThrows() {
        var mapper = EnumMapper.getFactory(1, Color.class).orElseThrow();
        assertThrows(SqlTemplateException.class, () -> mapper.newInstance(new Object[] { "PURPLE" }));
    }

    @Test
    public void testMapperInvalidOrdinalThrows() {
        var mapper = EnumMapper.getFactory(1, Color.class).orElseThrow();
        assertThrows(SqlTemplateException.class, () -> mapper.newInstance(new Object[] { 99 }));
    }

    @Test
    public void testMapperInvalidTypeThrows() {
        var mapper = EnumMapper.getFactory(1, Color.class).orElseThrow();
        assertThrows(SqlTemplateException.class, () -> mapper.newInstance(new Object[] { 3.14 }));
    }

    @Test
    public void testMapperNegativeOrdinalThrows() {
        var mapper = EnumMapper.getFactory(1, Color.class).orElseThrow();
        assertThrows(SqlTemplateException.class, () -> mapper.newInstance(new Object[] { -1 }));
    }
}
