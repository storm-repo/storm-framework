package st.orm.core.spi;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import st.orm.Entity;
import st.orm.PK;

/**
 * Tests for {@link WeakInterner}.
 */
public class WeakInternerTest {

    // Simple entity for testing.
    record TestEntity(@PK Integer id, String name) implements Entity<Integer> {}

    // A non-entity record for testing.
    record SimpleData(String value) {}

    @Test
    public void testInternNonEntityReturnsCanonicalInstance() {
        WeakInterner interner = new WeakInterner();
        SimpleData data1 = new SimpleData("hello");
        SimpleData data2 = new SimpleData("hello");

        SimpleData interned1 = interner.intern(data1);
        SimpleData interned2 = interner.intern(data2);
        assertSame(interned1, interned2, "Should return the same canonical instance for equal objects");
    }

    @Test
    public void testInternEntityReturnsCanonicalInstance() {
        WeakInterner interner = new WeakInterner();
        TestEntity entity1 = new TestEntity(1, "Alice");
        TestEntity entity2 = new TestEntity(1, "Alice");

        TestEntity interned1 = interner.intern(entity1);
        TestEntity interned2 = interner.intern(entity2);
        assertSame(interned1, interned2, "Should return the same canonical instance for equal entities");
    }

    @Test
    public void testInternEntityDifferentPrimaryKeyReturnsDifferentInstances() {
        WeakInterner interner = new WeakInterner();
        TestEntity entity1 = new TestEntity(1, "Alice");
        TestEntity entity2 = new TestEntity(2, "Bob");

        TestEntity interned1 = interner.intern(entity1);
        TestEntity interned2 = interner.intern(entity2);
        assertSame(entity1, interned1);
        assertSame(entity2, interned2);
    }

    @Test
    public void testInternEntitySamePkReturnsCachedInstance() {
        WeakInterner interner = new WeakInterner();
        TestEntity entity1 = new TestEntity(1, "Alice");
        TestEntity entity2 = new TestEntity(1, "Updated Alice");

        TestEntity interned1 = interner.intern(entity1);
        assertSame(entity1, interned1);
        // entity2 has the same PK, so the interner returns the existing cached instance.
        TestEntity interned2 = interner.intern(entity2);
        assertSame(entity1, interned2, "Should return cached entity for same PK");
    }

    @Test
    public void testInternNullThrowsException() {
        WeakInterner interner = new WeakInterner();
        assertThrows(NullPointerException.class, () -> interner.intern(null));
    }

    @Test
    public void testGetEntityByTypeAndPk() {
        WeakInterner interner = new WeakInterner();
        TestEntity entity = new TestEntity(1, "Alice");
        interner.intern(entity);

        TestEntity cached = interner.get(TestEntity.class, 1);
        assertNotNull(cached, "Should find cached entity");
        assertSame(entity, cached);
    }

    @Test
    public void testGetEntityReturnsNullWhenNotCached() {
        WeakInterner interner = new WeakInterner();
        TestEntity cached = interner.get(TestEntity.class, 999);
        assertNull(cached, "Should return null when entity is not cached");
    }

    @Test
    public void testInternDifferentNonEntityValues() {
        WeakInterner interner = new WeakInterner();
        SimpleData data1 = new SimpleData("hello");
        SimpleData data2 = new SimpleData("world");

        SimpleData interned1 = interner.intern(data1);
        SimpleData interned2 = interner.intern(data2);
        assertSame(data1, interned1);
        assertSame(data2, interned2);
    }

    @Test
    public void testInternStringReturnsCanonicalInstance() {
        WeakInterner interner = new WeakInterner();
        // Use new String() to avoid the JVM string pool.
        String string1 = new String("test");
        String string2 = new String("test");

        String interned1 = interner.intern(string1);
        String interned2 = interner.intern(string2);
        assertSame(interned1, interned2, "Should intern equal strings to the same instance");
    }

    @Test
    public void testInternMultipleEntitiesDifferentTypes() {
        // Test entity of another type.
        record OtherEntity(@PK Integer id, String label) implements Entity<Integer> {}

        WeakInterner interner = new WeakInterner();
        TestEntity testEntity = new TestEntity(1, "Alice");
        OtherEntity otherEntity = new OtherEntity(1, "Other");

        TestEntity internedTest = interner.intern(testEntity);
        OtherEntity internedOther = interner.intern(otherEntity);
        assertSame(testEntity, internedTest);
        assertSame(otherEntity, internedOther);
    }
}
