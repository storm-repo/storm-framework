package st.orm;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class EntityCallbackTest {

    record TestEntity(Integer id) implements Entity<Integer> {
        @Override
        public Integer id() {
            return id;
        }
    }

    @Test
    void defaultBeforeInsertReturnsEntity() {
        EntityCallback<TestEntity> callback = new EntityCallback<>() {};
        TestEntity entity = new TestEntity(1);
        assertSame(entity, callback.beforeInsert(entity));
    }

    @Test
    void defaultBeforeUpdateReturnsEntity() {
        EntityCallback<TestEntity> callback = new EntityCallback<>() {};
        TestEntity entity = new TestEntity(1);
        assertSame(entity, callback.beforeUpdate(entity));
    }

    @Test
    void defaultBeforeUpsertDelegatesToBeforeInsert() {
        TestEntity transformed = new TestEntity(99);
        EntityCallback<TestEntity> callback = new EntityCallback<>() {
            @Override
            public TestEntity beforeInsert(TestEntity entity) {
                return transformed;
            }
        };
        TestEntity entity = new TestEntity(1);
        assertSame(transformed, callback.beforeUpsert(entity));
    }

    @Test
    void defaultAfterInsertDoesNotThrow() {
        EntityCallback<TestEntity> callback = new EntityCallback<>() {};
        callback.afterInsert(new TestEntity(1));
    }

    @Test
    void defaultAfterUpdateDoesNotThrow() {
        EntityCallback<TestEntity> callback = new EntityCallback<>() {};
        callback.afterUpdate(new TestEntity(1));
    }

    @Test
    void defaultAfterUpsertDelegatesToAfterInsert() {
        boolean[] called = {false};
        EntityCallback<TestEntity> callback = new EntityCallback<>() {
            @Override
            public void afterInsert(TestEntity entity) {
                called[0] = true;
            }
        };
        callback.afterUpsert(new TestEntity(1));
        org.junit.jupiter.api.Assertions.assertTrue(called[0]);
    }

    @Test
    void defaultBeforeDeleteDoesNotThrow() {
        EntityCallback<TestEntity> callback = new EntityCallback<>() {};
        callback.beforeDelete(new TestEntity(1));
    }

    @Test
    void defaultAfterDeleteDoesNotThrow() {
        EntityCallback<TestEntity> callback = new EntityCallback<>() {};
        callback.afterDelete(new TestEntity(1));
    }
}
