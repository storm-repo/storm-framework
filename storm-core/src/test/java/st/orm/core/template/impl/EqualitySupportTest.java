package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link EqualitySupport} covering compileIsSame and compileIsIdentical
 * for various primitive and reference return types.
 */
class EqualitySupportTest {

    // Test record types with various field types.
    public record IntRecord(int value) {}
    public record LongRecord(long value) {}
    public record BooleanRecord(boolean value) {}
    public record ByteRecord(byte value) {}
    public record ShortRecord(short value) {}
    public record CharRecord(char value) {}
    public record FloatRecord(float value) {}
    public record DoubleRecord(double value) {}
    public record StringRecord(String value) {}
    public record NullableRecord(String value) {}

    private static java.lang.invoke.MethodHandle getHandle(Class<?> recordType) throws Throwable {
        return MethodHandles.publicLookup().findVirtual(
                recordType, "value", MethodType.methodType(recordType.getRecordComponents()[0].getType()));
    }

    // compileIsSame tests

    @Test
    void testIsSameInt() throws Throwable {
        var same = EqualitySupport.compileIsSame(getHandle(IntRecord.class));
        assertNotNull(same);
        assertTrue(same.isSame(new IntRecord(42), new IntRecord(42)));
        assertFalse(same.isSame(new IntRecord(42), new IntRecord(99)));
    }

    @Test
    void testIsSameLong() throws Throwable {
        var same = EqualitySupport.compileIsSame(getHandle(LongRecord.class));
        assertTrue(same.isSame(new LongRecord(100L), new LongRecord(100L)));
        assertFalse(same.isSame(new LongRecord(100L), new LongRecord(200L)));
    }

    @Test
    void testIsSameBoolean() throws Throwable {
        var same = EqualitySupport.compileIsSame(getHandle(BooleanRecord.class));
        assertTrue(same.isSame(new BooleanRecord(true), new BooleanRecord(true)));
        assertFalse(same.isSame(new BooleanRecord(true), new BooleanRecord(false)));
    }

    @Test
    void testIsSameByte() throws Throwable {
        var same = EqualitySupport.compileIsSame(getHandle(ByteRecord.class));
        assertTrue(same.isSame(new ByteRecord((byte) 1), new ByteRecord((byte) 1)));
        assertFalse(same.isSame(new ByteRecord((byte) 1), new ByteRecord((byte) 2)));
    }

    @Test
    void testIsSameShort() throws Throwable {
        var same = EqualitySupport.compileIsSame(getHandle(ShortRecord.class));
        assertTrue(same.isSame(new ShortRecord((short) 10), new ShortRecord((short) 10)));
        assertFalse(same.isSame(new ShortRecord((short) 10), new ShortRecord((short) 20)));
    }

    @Test
    void testIsSameChar() throws Throwable {
        var same = EqualitySupport.compileIsSame(getHandle(CharRecord.class));
        assertTrue(same.isSame(new CharRecord('A'), new CharRecord('A')));
        assertFalse(same.isSame(new CharRecord('A'), new CharRecord('B')));
    }

    @Test
    void testIsSameFloat() throws Throwable {
        var same = EqualitySupport.compileIsSame(getHandle(FloatRecord.class));
        assertTrue(same.isSame(new FloatRecord(3.14f), new FloatRecord(3.14f)));
        assertFalse(same.isSame(new FloatRecord(3.14f), new FloatRecord(2.72f)));
    }

    @Test
    void testIsSameFloatNaN() throws Throwable {
        var same = EqualitySupport.compileIsSame(getHandle(FloatRecord.class));
        // NaN should be equal to NaN with floatToIntBits comparison.
        assertTrue(same.isSame(new FloatRecord(Float.NaN), new FloatRecord(Float.NaN)));
    }

    @Test
    void testIsSameDouble() throws Throwable {
        var same = EqualitySupport.compileIsSame(getHandle(DoubleRecord.class));
        assertTrue(same.isSame(new DoubleRecord(2.718), new DoubleRecord(2.718)));
        assertFalse(same.isSame(new DoubleRecord(2.718), new DoubleRecord(3.14)));
    }

    @Test
    void testIsSameDoubleNaN() throws Throwable {
        var same = EqualitySupport.compileIsSame(getHandle(DoubleRecord.class));
        // NaN should be equal to NaN with doubleToLongBits comparison.
        assertTrue(same.isSame(new DoubleRecord(Double.NaN), new DoubleRecord(Double.NaN)));
    }

    @Test
    void testIsSameString() throws Throwable {
        var same = EqualitySupport.compileIsSame(getHandle(StringRecord.class));
        assertTrue(same.isSame(new StringRecord("hello"), new StringRecord("hello")));
        assertFalse(same.isSame(new StringRecord("hello"), new StringRecord("world")));
    }

    @Test
    void testIsSameStringNull() throws Throwable {
        var same = EqualitySupport.compileIsSame(getHandle(NullableRecord.class));
        assertTrue(same.isSame(new NullableRecord(null), new NullableRecord(null)));
        assertFalse(same.isSame(new NullableRecord("hello"), new NullableRecord(null)));
        assertFalse(same.isSame(new NullableRecord(null), new NullableRecord("hello")));
    }

    // compileIsIdentical tests

    @Test
    void testIsIdenticalInt() throws Throwable {
        var identical = EqualitySupport.compileIsIdentical(getHandle(IntRecord.class));
        // For primitives, identical wraps isSame.
        assertTrue(identical.isIdentical(new IntRecord(42), new IntRecord(42)));
        assertFalse(identical.isIdentical(new IntRecord(42), new IntRecord(99)));
    }

    @Test
    void testIsIdenticalString() throws Throwable {
        var identical = EqualitySupport.compileIsIdentical(getHandle(StringRecord.class));
        String sharedValue = "shared";
        // Reference identity: same String instance.
        assertTrue(identical.isIdentical(new StringRecord(sharedValue), new StringRecord(sharedValue)));
    }

    @Test
    void testIsIdenticalStringDifferentInstances() throws Throwable {
        var identical = EqualitySupport.compileIsIdentical(getHandle(StringRecord.class));
        // Different String instances, even if equal by value, should not be identical.
        String valueA = new String("test");
        String valueB = new String("test");
        assertFalse(identical.isIdentical(new StringRecord(valueA), new StringRecord(valueB)));
    }

    @Test
    void testIsIdenticalNulls() throws Throwable {
        var identical = EqualitySupport.compileIsIdentical(getHandle(NullableRecord.class));
        // Both null: should be identical (same reference: null == null).
        assertTrue(identical.isIdentical(new NullableRecord(null), new NullableRecord(null)));
    }

    @Test
    void testIsIdenticalLong() throws Throwable {
        var identical = EqualitySupport.compileIsIdentical(getHandle(LongRecord.class));
        assertTrue(identical.isIdentical(new LongRecord(100L), new LongRecord(100L)));
        assertFalse(identical.isIdentical(new LongRecord(100L), new LongRecord(200L)));
    }

    @Test
    void testIsIdenticalBoolean() throws Throwable {
        var identical = EqualitySupport.compileIsIdentical(getHandle(BooleanRecord.class));
        assertTrue(identical.isIdentical(new BooleanRecord(true), new BooleanRecord(true)));
        assertFalse(identical.isIdentical(new BooleanRecord(true), new BooleanRecord(false)));
    }

    @Test
    void testIsIdenticalFloat() throws Throwable {
        var identical = EqualitySupport.compileIsIdentical(getHandle(FloatRecord.class));
        assertTrue(identical.isIdentical(new FloatRecord(1.0f), new FloatRecord(1.0f)));
        assertFalse(identical.isIdentical(new FloatRecord(1.0f), new FloatRecord(2.0f)));
    }

    @Test
    void testIsIdenticalDouble() throws Throwable {
        var identical = EqualitySupport.compileIsIdentical(getHandle(DoubleRecord.class));
        assertTrue(identical.isIdentical(new DoubleRecord(1.0), new DoubleRecord(1.0)));
        assertFalse(identical.isIdentical(new DoubleRecord(1.0), new DoubleRecord(2.0)));
    }
}
