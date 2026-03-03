package st.orm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeyDelegateTest {

    record TestData(int id) implements Data {}

    static class TestMetamodel extends AbstractMetamodel<TestData, Integer, Integer> {
        TestMetamodel() {
            super(Integer.class, "path", "id", false, null);
        }

        @Override
        public Object getValue(TestData record) {
            return record.id();
        }

        @Override
        public boolean isIdentical(TestData a, TestData b) {
            return a == b;
        }

        @Override
        public boolean isSame(TestData a, TestData b) {
            return a.id() == b.id();
        }
    }

    static class TestKeyMetamodel extends AbstractKeyMetamodel<TestData, Integer, Integer> {
        private final boolean nullable;

        TestKeyMetamodel(boolean nullable) {
            super(Integer.class, "path", "id", false, null, true, nullable);
            this.nullable = nullable;
        }

        @Override
        public boolean isNullable() {
            return nullable;
        }

        @Override
        public Object getValue(TestData record) {
            return record.id();
        }

        @Override
        public boolean isIdentical(TestData a, TestData b) {
            return a == b;
        }

        @Override
        public boolean isSame(TestData a, TestData b) {
            return a.id() == b.id();
        }
    }

    @Test
    void keyDelegateWrapsNonKeyMetamodel() {
        TestMetamodel metamodel = new TestMetamodel();
        Metamodel.Key<TestData, Integer> key = Metamodel.key(metamodel);
        assertNotNull(key);
        assertTrue(key instanceof Metamodel.KeyDelegate);
    }

    @Test
    void keyReturnsExistingKeyInstance() {
        TestKeyMetamodel keyMetamodel = new TestKeyMetamodel(false);
        Metamodel.Key<TestData, Integer> key = Metamodel.key(keyMetamodel);
        assertSame(keyMetamodel, key);
    }

    @Test
    void keyDelegateDelegatesAllMethods() {
        TestMetamodel metamodel = new TestMetamodel();
        Metamodel.Key<TestData, Integer> key = Metamodel.key(metamodel);
        assertEquals(metamodel.isColumn(), key.isColumn());
        assertEquals(metamodel.isInline(), key.isInline());
        assertEquals(metamodel.root(), key.root());
        assertEquals(metamodel.table(), key.table());
        assertEquals(metamodel.path(), key.path());
        assertEquals(metamodel.fieldType(), key.fieldType());
        assertEquals(metamodel.field(), key.field());
    }

    @Test
    void keyDelegateIsNotNullableForNonKeyMetamodel() {
        TestMetamodel metamodel = new TestMetamodel();
        Metamodel.Key<TestData, Integer> key = Metamodel.key(metamodel);
        assertFalse(key.isNullable());
    }

    @Test
    void keyDelegateIsNullableWhenDelegateIsNullableKey() {
        TestKeyMetamodel nullableKey = new TestKeyMetamodel(true);
        Metamodel.KeyDelegate<TestData, Integer> delegate = new Metamodel.KeyDelegate<>(nullableKey);
        assertTrue(delegate.isNullable());
    }

    @Test
    void keyDelegateEqualsWrappedMetamodel() {
        TestMetamodel metamodel = new TestMetamodel();
        Metamodel.KeyDelegate<TestData, Integer> delegate = new Metamodel.KeyDelegate<>(metamodel);
        assertEquals(delegate, metamodel);
        assertEquals(metamodel, delegate);
    }

    @Test
    void keyDelegateEqualsAnotherDelegateWrappingSameMetamodel() {
        TestMetamodel metamodel = new TestMetamodel();
        Metamodel.KeyDelegate<TestData, Integer> delegate1 = new Metamodel.KeyDelegate<>(metamodel);
        Metamodel.KeyDelegate<TestData, Integer> delegate2 = new Metamodel.KeyDelegate<>(metamodel);
        assertEquals(delegate1, delegate2);
    }

    @Test
    void keyDelegateHashCodeMatchesWrappedMetamodel() {
        TestMetamodel metamodel = new TestMetamodel();
        Metamodel.KeyDelegate<TestData, Integer> delegate = new Metamodel.KeyDelegate<>(metamodel);
        assertEquals(metamodel.hashCode(), delegate.hashCode());
    }

    @Test
    void keyDelegateToStringDelegatesToWrapped() {
        TestMetamodel metamodel = new TestMetamodel();
        Metamodel.KeyDelegate<TestData, Integer> delegate = new Metamodel.KeyDelegate<>(metamodel);
        assertEquals(metamodel.toString(), delegate.toString());
    }

    @Test
    void keyDelegateGetValueDelegatesToWrapped() {
        TestMetamodel metamodel = new TestMetamodel();
        Metamodel.Key<TestData, Integer> key = Metamodel.key(metamodel);
        TestData data = new TestData(42);
        assertEquals(42, key.getValue(data));
    }

    @Test
    void keyDelegateIsIdenticalDelegatesToWrapped() {
        TestMetamodel metamodel = new TestMetamodel();
        Metamodel.Key<TestData, Integer> key = Metamodel.key(metamodel);
        TestData dataA = new TestData(1);
        TestData dataB = new TestData(1);
        assertFalse(key.isIdentical(dataA, dataB));
        assertTrue(key.isIdentical(dataA, dataA));
    }

    @Test
    void keyDelegateIsSameDelegatesToWrapped() {
        TestMetamodel metamodel = new TestMetamodel();
        Metamodel.Key<TestData, Integer> key = Metamodel.key(metamodel);
        TestData dataA = new TestData(1);
        TestData dataB = new TestData(1);
        TestData dataC = new TestData(2);
        assertTrue(key.isSame(dataA, dataB));
        assertFalse(key.isSame(dataA, dataC));
    }

    @Test
    void keyRejectsNullMetamodel() {
        assertThrows(NullPointerException.class, () -> Metamodel.key(null));
    }

    @Test
    void keyDelegateConstructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new Metamodel.KeyDelegate<>(null));
    }

    @Test
    void keyDelegateEqualsItself() {
        TestMetamodel metamodel = new TestMetamodel();
        Metamodel.KeyDelegate<TestData, Integer> delegate = new Metamodel.KeyDelegate<>(metamodel);
        assertEquals(delegate, delegate);
    }
}
