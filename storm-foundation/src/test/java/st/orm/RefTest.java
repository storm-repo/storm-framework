package st.orm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RefTest {

    record TestEntity(int id, String name) implements Data {}

    record SampleEntity(Integer id) implements Entity<Integer> {
        @Override
        public Integer id() {
            return id;
        }
    }

    record SampleProjection(String data) implements Projection<Integer> {}

    @Test
    void detachedRefOfTypeAndPk() {
        Ref<TestEntity> ref = Ref.of(TestEntity.class, 42);
        assertEquals(TestEntity.class, ref.type());
        assertEquals(42, ref.id());
        assertNull(ref.getOrNull());
        assertNull(ref.fetchOrNull());
        assertFalse(ref.isFetchable());
        assertFalse(ref.isLoaded());
    }

    @Test
    void detachedRefUnloadReturnsSameInstance() {
        Ref<TestEntity> ref = Ref.of(TestEntity.class, 42);
        assertSame(ref, ref.unload());
    }

    @Test
    void detachedRefFetchThrowsPersistenceException() {
        Ref<TestEntity> ref = Ref.of(TestEntity.class, 42);
        assertThrows(PersistenceException.class, ref::fetch);
    }

    @Test
    void detachedRefToString() {
        Ref<TestEntity> ref = Ref.of(TestEntity.class, 42);
        assertEquals("TestEntity@42", ref.toString());
    }

    @Test
    void detachedRefEqualityBasedOnTypeAndId() {
        Ref<TestEntity> ref1 = Ref.of(TestEntity.class, 42);
        Ref<TestEntity> ref2 = Ref.of(TestEntity.class, 42);
        assertEquals(ref1, ref2);
        assertEquals(ref1.hashCode(), ref2.hashCode());
    }

    @Test
    void detachedRefInequalityWithDifferentId() {
        Ref<TestEntity> ref1 = Ref.of(TestEntity.class, 1);
        Ref<TestEntity> ref2 = Ref.of(TestEntity.class, 2);
        assertNotEquals(ref1, ref2);
    }

    @Test
    void detachedRefEqualsItself() {
        Ref<TestEntity> ref = Ref.of(TestEntity.class, 42);
        assertEquals(ref, ref);
    }

    @Test
    void detachedRefNotEqualToNull() {
        Ref<TestEntity> ref = Ref.of(TestEntity.class, 42);
        assertNotEquals(ref, null);
    }

    @Test
    void detachedRefNotEqualToNonRef() {
        Ref<TestEntity> ref = Ref.of(TestEntity.class, 42);
        assertNotEquals(ref, "not a ref");
    }

    @Test
    void ofTypeAndPkRejectsNullType() {
        assertThrows(NullPointerException.class, () -> Ref.of((Class<TestEntity>) null, 42));
    }

    @Test
    void ofTypeAndPkRejectsNullPk() {
        assertThrows(NullPointerException.class, () -> Ref.of(TestEntity.class, null));
    }

    @Test
    void entityIdExtraction() {
        SampleEntity entity = new SampleEntity(99);
        Ref<SampleEntity> ref = Ref.of(entity);
        Integer extractedId = Ref.entityId(ref);
        assertEquals(99, extractedId);
    }

    @Test
    void projectionIdExtraction() {
        SampleProjection projection = new SampleProjection("test");
        Ref<SampleProjection> ref = Ref.of(projection, 77);
        Integer extractedId = Ref.projectionId(ref);
        assertEquals(77, extractedId);
    }

    @Test
    void refOfEntityReturnsLoadedRef() {
        SampleEntity entity = new SampleEntity(10);
        Ref<SampleEntity> ref = Ref.of(entity);
        assertEquals(SampleEntity.class, ref.type());
        assertEquals(10, ref.id());
        assertSame(entity, ref.getOrNull());
        assertSame(entity, ref.fetchOrNull());
        assertFalse(ref.isFetchable());
        assertTrue(ref.isLoaded());
    }

    @Test
    void refOfEntityUnloadReturnsDetachedRef() {
        SampleEntity entity = new SampleEntity(10);
        Ref<SampleEntity> ref = Ref.of(entity);
        Ref<SampleEntity> unloaded = ref.unload();
        assertNotNull(unloaded);
        assertEquals(ref.id(), unloaded.id());
        assertNull(unloaded.getOrNull());
        assertFalse(unloaded.isLoaded());
    }

    @Test
    void refOfEntityRejectsNullEntity() {
        assertThrows(NullPointerException.class, () -> Ref.of((SampleEntity) null));
    }

    @Test
    void refOfProjectionReturnsLoadedRef() {
        SampleProjection projection = new SampleProjection("test");
        Ref<SampleProjection> ref = Ref.of(projection, 5);
        assertEquals(SampleProjection.class, ref.type());
        assertEquals(5, ref.id());
        assertSame(projection, ref.getOrNull());
        assertSame(projection, ref.fetchOrNull());
        assertFalse(ref.isFetchable());
        assertTrue(ref.isLoaded());
    }

    @Test
    void refOfProjectionUnloadReturnsDetachedRef() {
        SampleProjection projection = new SampleProjection("test");
        Ref<SampleProjection> ref = Ref.of(projection, 5);
        Ref<SampleProjection> unloaded = ref.unload();
        assertNotNull(unloaded);
        assertEquals(5, unloaded.id());
        assertNull(unloaded.getOrNull());
    }

    @Test
    void refOfProjectionRejectsNullProjection() {
        assertThrows(NullPointerException.class, () -> Ref.of((SampleProjection) null, 5));
    }

    @Test
    void refOfProjectionRejectsNullId() {
        assertThrows(NullPointerException.class, () -> Ref.of(new SampleProjection("test"), null));
    }

    @Test
    void refOfEntityFetchReturnsEntity() {
        SampleEntity entity = new SampleEntity(10);
        Ref<SampleEntity> ref = Ref.of(entity);
        assertEquals(entity, ref.fetch());
    }

    @Test
    void refOfProjectionFetchReturnsProjection() {
        SampleProjection projection = new SampleProjection("data");
        Ref<SampleProjection> ref = Ref.of(projection, 1);
        assertEquals(projection, ref.fetch());
    }

    @Test
    void entityRefAndDetachedRefAreEqualByTypeAndId() {
        SampleEntity entity = new SampleEntity(42);
        Ref<SampleEntity> entityRef = Ref.of(entity);
        Ref<SampleEntity> detachedRef = Ref.of(SampleEntity.class, 42);
        assertEquals(entityRef, detachedRef);
        assertEquals(detachedRef, entityRef);
        assertEquals(entityRef.hashCode(), detachedRef.hashCode());
    }

    record IntIdEntity(Integer id) implements Entity<Integer> {}
    record OtherIntIdEntity(Integer id) implements Entity<Integer> {}
    record IntIdProjection(String data) implements Projection<Integer> {}

    @Test
    void detachedRefsOfDifferentTypesAreNotEqual() {
        Ref<IntIdEntity> ref1 = Ref.of(IntIdEntity.class, 1);
        Ref<OtherIntIdEntity> ref2 = Ref.of(OtherIntIdEntity.class, 1);
        assertNotEquals(ref1, ref2);
    }

    @Test
    void entityRefAndDetachedRefOfDifferentTypesAreNotEqual() {
        IntIdEntity entity = new IntIdEntity(1);
        Ref<IntIdEntity> entityRef = Ref.of(entity);
        Ref<OtherIntIdEntity> otherRef = Ref.of(OtherIntIdEntity.class, 1);
        assertNotEquals(entityRef, otherRef);
    }

    @Test
    void projectionRefToString() {
        IntIdProjection projection = new IntIdProjection("data");
        Ref<IntIdProjection> ref = Ref.of(projection, 42);
        String result = ref.toString();
        assertTrue(result.contains("IntIdProjection"), "ToString should contain type name");
        assertTrue(result.contains("42"), "ToString should contain id");
    }

    @Test
    void detachedRefToStringContainsTypeAndId() {
        Ref<IntIdEntity> ref = Ref.of(IntIdEntity.class, 99);
        assertEquals("IntIdEntity@99", ref.toString());
    }

    @Test
    void entityRefEqualityIsBasedOnTypeAndId() {
        IntIdEntity entity1 = new IntIdEntity(5);
        IntIdEntity entity2 = new IntIdEntity(5);
        Ref<IntIdEntity> ref1 = Ref.of(entity1);
        Ref<IntIdEntity> ref2 = Ref.of(entity2);
        assertEquals(ref1, ref2, "Refs with same type and id should be equal");
        assertEquals(ref1.hashCode(), ref2.hashCode());
    }

    @Test
    void entityRefNotEqualToNull() {
        IntIdEntity entity = new IntIdEntity(1);
        Ref<IntIdEntity> ref = Ref.of(entity);
        assertNotEquals(ref, null);
    }

    @Test
    void entityRefNotEqualToNonRef() {
        IntIdEntity entity = new IntIdEntity(1);
        Ref<IntIdEntity> ref = Ref.of(entity);
        assertNotEquals(ref, "not a ref");
    }

    @Test
    void projectionRefEquality() {
        IntIdProjection projection1 = new IntIdProjection("data1");
        IntIdProjection projection2 = new IntIdProjection("data2");
        Ref<IntIdProjection> ref1 = Ref.of(projection1, 1);
        Ref<IntIdProjection> ref2 = Ref.of(projection2, 1);
        // Same type and id, so should be equal even though projections differ.
        assertEquals(ref1, ref2);
    }

    @Test
    void projectionRefInequalityOnId() {
        IntIdProjection projection = new IntIdProjection("data");
        Ref<IntIdProjection> ref1 = Ref.of(projection, 1);
        Ref<IntIdProjection> ref2 = Ref.of(projection, 2);
        assertNotEquals(ref1, ref2);
    }

    @Test
    void entityRefIsLoadedAndNotFetchable() {
        IntIdEntity entity = new IntIdEntity(1);
        Ref<IntIdEntity> ref = Ref.of(entity);
        assertTrue(ref.isLoaded(), "Entity ref should be loaded");
        assertFalse(ref.isFetchable(), "Entity ref should not be fetchable");
    }

    @Test
    void detachedRefIsNotLoadedAndNotFetchable() {
        Ref<IntIdEntity> ref = Ref.of(IntIdEntity.class, 1);
        assertFalse(ref.isLoaded(), "Detached ref should not be loaded");
        assertFalse(ref.isFetchable(), "Detached ref should not be fetchable");
    }
}
