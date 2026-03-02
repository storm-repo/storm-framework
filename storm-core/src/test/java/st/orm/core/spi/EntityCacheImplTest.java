package st.orm.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import st.orm.Entity;
import st.orm.PK;

/**
 * Tests for {@link EntityCacheImpl}.
 */
public class EntityCacheImplTest {

    record TestEntity(@PK Integer id, String name) implements Entity<Integer> {}

    @BeforeEach
    public void resetMetrics() {
        EntityCacheMetrics.getInstance().reset();
    }

    @Test
    public void testInternReturnsEntityWhenCacheEmpty() {
        EntityCacheImpl<TestEntity, Integer> cache = new EntityCacheImpl<>(CacheRetention.DEFAULT);
        TestEntity entity = new TestEntity(1, "Alice");
        TestEntity interned = cache.intern(entity);
        assertSame(entity, interned, "Should return the same entity when cache is empty");
    }

    @Test
    public void testInternReturnsCachedInstanceForEqualEntity() {
        EntityCacheImpl<TestEntity, Integer> cache = new EntityCacheImpl<>(CacheRetention.DEFAULT);
        TestEntity entity1 = new TestEntity(1, "Alice");
        TestEntity entity2 = new TestEntity(1, "Alice");

        TestEntity interned1 = cache.intern(entity1);
        TestEntity interned2 = cache.intern(entity2);
        assertSame(interned1, interned2, "Should return cached instance for equal entity");
    }

    @Test
    public void testInternReplacesWhenEntityDiffers() {
        EntityCacheImpl<TestEntity, Integer> cache = new EntityCacheImpl<>(CacheRetention.DEFAULT);
        TestEntity entity1 = new TestEntity(1, "Alice");
        TestEntity entity2 = new TestEntity(1, "Updated Alice");

        cache.intern(entity1);
        TestEntity interned2 = cache.intern(entity2);
        assertSame(entity2, interned2, "Should replace cache entry when entities differ");
    }

    @Test
    public void testGetReturnsEntityAfterIntern() {
        EntityCacheImpl<TestEntity, Integer> cache = new EntityCacheImpl<>(CacheRetention.DEFAULT);
        TestEntity entity = new TestEntity(1, "Alice");
        cache.intern(entity);

        Optional<TestEntity> result = cache.get(1);
        assertTrue(result.isPresent());
        assertSame(entity, result.get());
    }

    @Test
    public void testGetReturnsEmptyForMissingKey() {
        EntityCacheImpl<TestEntity, Integer> cache = new EntityCacheImpl<>(CacheRetention.DEFAULT);
        Optional<TestEntity> result = cache.get(999);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testRemoveEntry() {
        EntityCacheImpl<TestEntity, Integer> cache = new EntityCacheImpl<>(CacheRetention.DEFAULT);
        TestEntity entity = new TestEntity(1, "Alice");
        cache.intern(entity);
        cache.remove(1);

        Optional<TestEntity> result = cache.get(1);
        assertTrue(result.isEmpty(), "Should be empty after removal");
    }

    @Test
    public void testClearRemovesAllEntries() {
        EntityCacheImpl<TestEntity, Integer> cache = new EntityCacheImpl<>(CacheRetention.DEFAULT);
        cache.intern(new TestEntity(1, "Alice"));
        cache.intern(new TestEntity(2, "Bob"));
        cache.intern(new TestEntity(3, "Charlie"));

        cache.clear();
        assertTrue(cache.get(1).isEmpty());
        assertTrue(cache.get(2).isEmpty());
        assertTrue(cache.get(3).isEmpty());
    }

    @Test
    public void testMultipleEntities() {
        EntityCacheImpl<TestEntity, Integer> cache = new EntityCacheImpl<>(CacheRetention.DEFAULT);
        TestEntity alice = new TestEntity(1, "Alice");
        TestEntity bob = new TestEntity(2, "Bob");

        cache.intern(alice);
        cache.intern(bob);

        assertSame(alice, cache.get(1).orElseThrow());
        assertSame(bob, cache.get(2).orElseThrow());
    }

    @Test
    public void testLightRetention() {
        EntityCacheImpl<TestEntity, Integer> cache = new EntityCacheImpl<>(CacheRetention.LIGHT);
        TestEntity entity = new TestEntity(1, "Alice");
        TestEntity interned = cache.intern(entity);
        assertSame(entity, interned);

        // Should still be retrievable immediately.
        Optional<TestEntity> result = cache.get(1);
        assertTrue(result.isPresent());
        assertSame(entity, result.get());
    }

    @Test
    public void testMetricsGetHitRecording() {
        EntityCacheImpl<TestEntity, Integer> cache = new EntityCacheImpl<>(CacheRetention.DEFAULT);
        cache.intern(new TestEntity(1, "Alice"));
        cache.get(1);

        EntityCacheMetrics metrics = EntityCacheMetrics.getInstance();
        assertEquals(1, metrics.getGetHits());
    }

    @Test
    public void testMetricsGetMissRecording() {
        EntityCacheImpl<TestEntity, Integer> cache = new EntityCacheImpl<>(CacheRetention.DEFAULT);
        cache.get(999);

        EntityCacheMetrics metrics = EntityCacheMetrics.getInstance();
        assertEquals(1, metrics.getGetMisses());
    }

    @Test
    public void testMetricsInternHitRecording() {
        EntityCacheImpl<TestEntity, Integer> cache = new EntityCacheImpl<>(CacheRetention.DEFAULT);
        cache.intern(new TestEntity(1, "Alice"));
        cache.intern(new TestEntity(1, "Alice"));

        EntityCacheMetrics metrics = EntityCacheMetrics.getInstance();
        assertEquals(1, metrics.getInternHits());
        assertEquals(1, metrics.getInternMisses());
    }

    @Test
    public void testMetricsInternMissRecording() {
        EntityCacheImpl<TestEntity, Integer> cache = new EntityCacheImpl<>(CacheRetention.DEFAULT);
        cache.intern(new TestEntity(1, "Alice"));

        EntityCacheMetrics metrics = EntityCacheMetrics.getInstance();
        assertEquals(1, metrics.getInternMisses());
    }

    @Test
    public void testMetricsRemovalRecording() {
        EntityCacheImpl<TestEntity, Integer> cache = new EntityCacheImpl<>(CacheRetention.DEFAULT);
        cache.intern(new TestEntity(1, "Alice"));
        cache.remove(1);

        EntityCacheMetrics metrics = EntityCacheMetrics.getInstance();
        assertEquals(1, metrics.getRemovals());
    }

    @Test
    public void testMetricsClearRecording() {
        EntityCacheImpl<TestEntity, Integer> cache = new EntityCacheImpl<>(CacheRetention.DEFAULT);
        cache.intern(new TestEntity(1, "Alice"));
        cache.clear();

        EntityCacheMetrics metrics = EntityCacheMetrics.getInstance();
        assertEquals(1, metrics.getClears());
    }
}
