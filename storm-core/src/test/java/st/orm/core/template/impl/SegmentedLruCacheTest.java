package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SegmentedLruCache}.
 */
public class SegmentedLruCacheTest {

    @Test
    public void testBasicPutAndGet() {
        SegmentedLruCache<String, String> cache = new SegmentedLruCache<>(100);
        cache.put("a", "alpha");
        assertEquals("alpha", cache.get("a"));
    }

    @Test
    public void testGetReturnsNullForMissingKey() {
        SegmentedLruCache<String, String> cache = new SegmentedLruCache<>(100);
        assertNull(cache.get("nonexistent"));
    }

    @Test
    public void testPutOverwritesExisting() {
        SegmentedLruCache<String, String> cache = new SegmentedLruCache<>(100);
        cache.put("a", "alpha");
        cache.put("a", "updated");
        assertEquals("updated", cache.get("a"));
    }

    @Test
    public void testPutIfAbsentWhenAbsent() {
        SegmentedLruCache<String, String> cache = new SegmentedLruCache<>(100);
        String existing = cache.putIfAbsent("a", "alpha");
        assertNull(existing, "Should return null when key is absent");
        assertEquals("alpha", cache.get("a"));
    }

    @Test
    public void testPutIfAbsentWhenPresent() {
        SegmentedLruCache<String, String> cache = new SegmentedLruCache<>(100);
        cache.put("a", "alpha");
        String existing = cache.putIfAbsent("a", "beta");
        assertEquals("alpha", existing, "Should return existing value");
        assertEquals("alpha", cache.get("a"), "Existing value should not be overwritten");
    }

    @Test
    public void testGetOrComputeWhenAbsent() {
        SegmentedLruCache<String, String> cache = new SegmentedLruCache<>(100);
        String result = cache.getOrCompute("a", () -> "computed");
        assertEquals("computed", result);
        assertEquals("computed", cache.get("a"));
    }

    @Test
    public void testGetOrComputeWhenPresent() {
        SegmentedLruCache<String, String> cache = new SegmentedLruCache<>(100);
        cache.put("a", "alpha");
        String result = cache.getOrCompute("a", () -> "computed");
        assertEquals("alpha", result, "Should return cached value, not recompute");
    }

    @Test
    public void testGetOrComputeWithNullSupplierResult() {
        SegmentedLruCache<String, String> cache = new SegmentedLruCache<>(100);
        String result = cache.getOrCompute("a", () -> null);
        assertNull(result, "Should return null when supplier returns null");
        assertNull(cache.get("a"), "Nothing should be cached for null supplier result");
    }

    @Test
    public void testRemove() {
        SegmentedLruCache<String, String> cache = new SegmentedLruCache<>(100);
        cache.put("a", "alpha");
        String removed = cache.remove("a");
        assertEquals("alpha", removed);
        assertNull(cache.get("a"));
    }

    @Test
    public void testRemoveReturnsNullForMissingKey() {
        SegmentedLruCache<String, String> cache = new SegmentedLruCache<>(100);
        assertNull(cache.remove("nonexistent"));
    }

    @Test
    public void testClear() {
        SegmentedLruCache<String, String> cache = new SegmentedLruCache<>(100);
        cache.put("a", "alpha");
        cache.put("b", "beta");
        cache.put("c", "gamma");
        cache.clear();
        assertNull(cache.get("a"));
        assertNull(cache.get("b"));
        assertNull(cache.get("c"));
    }

    @Test
    public void testSegmentCountIsPowerOfTwo() {
        SegmentedLruCache<String, String> cache = new SegmentedLruCache<>(100, 3);
        // 3 should be rounded up to 4.
        assertEquals(4, cache.segmentCount());
    }

    @Test
    public void testSegmentCountPowerOfTwoStaysUnchanged() {
        SegmentedLruCache<String, String> cache = new SegmentedLruCache<>(100, 8);
        assertEquals(8, cache.segmentCount());
    }

    @Test
    public void testDefaultSegmentCountHeuristic() {
        // For small cache sizes, segment count should be 4 (minimum).
        SegmentedLruCache<String, String> smallCache = new SegmentedLruCache<>(10);
        assertEquals(4, smallCache.segmentCount());

        // For large cache sizes (4096 / 128 = 32), segment count should be 32.
        SegmentedLruCache<String, String> largeCache = new SegmentedLruCache<>(4096);
        assertEquals(32, largeCache.segmentCount());

        // For very large cache sizes, segment count should be capped at 32.
        SegmentedLruCache<String, String> veryLargeCache = new SegmentedLruCache<>(100000);
        assertEquals(32, veryLargeCache.segmentCount());
    }

    @Test
    public void testMaxSizeZeroThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new SegmentedLruCache<>(0));
    }

    @Test
    public void testNegativeMaxSizeThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new SegmentedLruCache<>(-1));
    }

    @Test
    public void testSegmentCountZeroThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new SegmentedLruCache<>(100, 0));
    }

    @Test
    public void testNegativeSegmentCountThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new SegmentedLruCache<>(100, -1));
    }

    @Test
    public void testEvictionWithinSegment() {
        // Small cache with 1 segment so eviction behavior is deterministic.
        SegmentedLruCache<Integer, String> cache = new SegmentedLruCache<>(2, 1);
        cache.put(1, "one");
        cache.put(2, "two");
        // Adding 3 should evict the least recently used entry.
        cache.put(3, "three");
        // One of the earlier entries should be evicted.
        int nonNullCount = 0;
        if (cache.get(1) != null) nonNullCount++;
        if (cache.get(2) != null) nonNullCount++;
        if (cache.get(3) != null) nonNullCount++;
        // Should have at most 2 entries since maxSize is 2.
        assertTrue(nonNullCount <= 2, "Should have at most maxSize entries");
        assertNotNull(cache.get(3), "Most recently added entry should still be present");
    }

    @Test
    public void testMultipleKeysDistributedAcrossSegments() {
        SegmentedLruCache<Integer, String> cache = new SegmentedLruCache<>(100, 4);
        for (int i = 0; i < 50; i++) {
            cache.put(i, "value-" + i);
        }
        for (int i = 0; i < 50; i++) {
            assertEquals("value-" + i, cache.get(i));
        }
    }

    @Test
    public void testMaxSizeOneWithOneSegment() {
        SegmentedLruCache<String, String> cache = new SegmentedLruCache<>(1, 1);
        assertEquals(1, cache.segmentCount());
        cache.put("a", "alpha");
        assertEquals("alpha", cache.get("a"));
        cache.put("b", "beta");
        assertNull(cache.get("a"), "First entry should be evicted when maxSize is 1");
        assertEquals("beta", cache.get("b"));
    }
}
