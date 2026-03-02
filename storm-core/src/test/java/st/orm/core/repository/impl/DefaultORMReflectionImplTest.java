package st.orm.core.repository.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import st.orm.Data;
import st.orm.Entity;
import st.orm.PK;
import st.orm.PersistenceException;

/**
 * Tests for {@link DefaultORMReflectionImpl}.
 */
public class DefaultORMReflectionImplTest {

    private final DefaultORMReflectionImpl reflection = new DefaultORMReflectionImpl();

    record SimpleEntity(@PK Integer id, String name) implements Entity<Integer> {}
    record AllDefaults(int value, String text) implements Data {}

    sealed interface SealedData extends Data permits SubData1, SubData2 {}
    record SubData1(int id) implements SealedData {}
    record SubData2(int id) implements SealedData {}

    // -- getId --

    @Test
    public void testGetId() {
        var entity = new SimpleEntity(42, "test");
        Object id = reflection.getId(entity);
        assertEquals(42, id);
    }

    // -- getRecordValue --

    @Test
    public void testGetRecordValue() {
        var entity = new SimpleEntity(1, "hello");
        assertEquals(1, reflection.getRecordValue(entity, 0));
        assertEquals("hello", reflection.getRecordValue(entity, 1));
    }

    // -- findRecordType --

    @Test
    public void testFindRecordType() {
        var result = reflection.findRecordType(SimpleEntity.class);
        assertTrue(result.isPresent());
        assertEquals(2, result.get().fields().size());
    }

    @Test
    public void testFindRecordTypeNonRecord() {
        var result = reflection.findRecordType(String.class);
        assertFalse(result.isPresent());
    }

    // -- getType --

    @Test
    public void testGetType() {
        assertEquals(SimpleEntity.class, reflection.getType(SimpleEntity.class));
    }

    @Test
    public void testGetTypeNotClass() {
        assertThrows(PersistenceException.class, () -> reflection.getType("not a class"));
    }

    @Test
    public void testGetTypeNotData() {
        assertThrows(PersistenceException.class, () -> reflection.getType(String.class));
    }

    // -- getDataType --

    @Test
    public void testGetDataType() {
        assertEquals(SimpleEntity.class, reflection.getDataType(SimpleEntity.class));
    }

    @Test
    public void testGetDataTypeNotClass() {
        assertThrows(PersistenceException.class, () -> reflection.getDataType("not a class"));
    }

    @Test
    public void testGetDataTypeNotData() {
        assertThrows(PersistenceException.class, () -> reflection.getDataType(String.class));
    }

    // -- isDefaultValue --

    @Test
    public void testIsDefaultValueNull() {
        assertTrue(reflection.isDefaultValue(null));
    }

    @Test
    public void testIsDefaultValuePrimitiveDefaults() {
        assertTrue(reflection.isDefaultValue(0));
        assertTrue(reflection.isDefaultValue(0L));
        assertTrue(reflection.isDefaultValue(0.0f));
        assertTrue(reflection.isDefaultValue(0.0));
        assertTrue(reflection.isDefaultValue((short) 0));
        assertTrue(reflection.isDefaultValue((byte) 0));
        assertTrue(reflection.isDefaultValue('\u0000'));
        assertTrue(reflection.isDefaultValue(false));
    }

    @Test
    public void testIsDefaultValuePrimitiveNonDefaults() {
        assertFalse(reflection.isDefaultValue(42));
        assertFalse(reflection.isDefaultValue(1L));
        assertFalse(reflection.isDefaultValue(1.0f));
        assertFalse(reflection.isDefaultValue(1.0));
        assertFalse(reflection.isDefaultValue((short) 1));
        assertFalse(reflection.isDefaultValue((byte) 1));
        assertFalse(reflection.isDefaultValue('A'));
        assertFalse(reflection.isDefaultValue(true));
    }

    @Test
    public void testIsDefaultValueRecordWithDefaults() {
        assertTrue(reflection.isDefaultValue(new AllDefaults(0, null)));
    }

    @Test
    public void testIsDefaultValueRecordWithNonDefaults() {
        assertFalse(reflection.isDefaultValue(new AllDefaults(1, null)));
        assertFalse(reflection.isDefaultValue(new AllDefaults(0, "value")));
    }

    @Test
    public void testIsDefaultValueString() {
        assertFalse(reflection.isDefaultValue("hello"));
    }

    // -- isSupportedType --

    @Test
    public void testIsSupportedType() {
        assertTrue(reflection.isSupportedType(SimpleEntity.class));
    }

    @Test
    public void testIsSupportedTypeNotClass() {
        assertFalse(reflection.isSupportedType("not a class"));
    }

    // -- getPermittedSubclasses --

    @Test
    public void testGetPermittedSubclasses() {
        List<Class<? extends SealedData>> subclasses = reflection.getPermittedSubclasses(SealedData.class);
        assertEquals(2, subclasses.size());
    }

    @Test
    public void testGetPermittedSubclassesNonSealed() {
        List<Class<? extends SimpleEntity>> subclasses = reflection.getPermittedSubclasses(SimpleEntity.class);
        assertTrue(subclasses.isEmpty());
    }

    // -- isDefaultMethod --

    @Test
    public void testIsDefaultMethod() throws NoSuchMethodException {
        var method = Object.class.getMethod("toString");
        assertFalse(reflection.isDefaultMethod(method));
    }

    // -- invoke --

    @Test
    public void testInvoke() {
        var entity = new SimpleEntity(5, "test");
        var recordType = reflection.findRecordType(SimpleEntity.class).orElseThrow();
        var idField = recordType.fields().getFirst();
        assertEquals(5, reflection.invoke(idField, entity));
    }

    // -- execute (default method) --

    interface Greeter {
        default String greet() {
            return "hello";
        }
    }
    record GreeterEntity(@PK Integer id) implements Entity<Integer>, Greeter {}

    @Test
    public void testExecuteDefaultMethod() throws Throwable {
        var entity = new GreeterEntity(1);
        var method = Greeter.class.getMethod("greet");
        assertEquals("hello", reflection.execute(entity, method));
    }
}
