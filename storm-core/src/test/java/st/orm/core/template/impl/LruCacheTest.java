package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LruCache}.
 */
public class LruCacheTest {

    @Test
    public void testBasicPutAndGet() {
        LruCache<String, String> cache = new LruCache<>(3);
        cache.put("a", "alpha");
        cache.put("b", "beta");
        cache.put("c", "gamma");
        assertEquals("alpha", cache.get("a"));
        assertEquals("beta", cache.get("b"));
        assertEquals("gamma", cache.get("c"));
    }

    @Test
    public void testEvictionWhenMaxSizeExceeded() {
        LruCache<String, String> cache = new LruCache<>(2);
        cache.put("a", "alpha");
        cache.put("b", "beta");
        // Adding a third entry should evict the least recently used (a).
        cache.put("c", "gamma");
        assertNull(cache.get("a"), "Eldest entry should have been evicted");
        assertEquals("beta", cache.get("b"));
        assertEquals("gamma", cache.get("c"));
    }

    @Test
    public void testAccessOrderAffectsEviction() {
        LruCache<String, String> cache = new LruCache<>(2);
        cache.put("a", "alpha");
        cache.put("b", "beta");
        // Access "a" so it becomes most recently used.
        cache.get("a");
        // Adding "c" should now evict "b" instead of "a".
        cache.put("c", "gamma");
        assertEquals("alpha", cache.get("a"), "Accessed entry should not be evicted");
        assertNull(cache.get("b"), "Least recently used entry should be evicted");
        assertEquals("gamma", cache.get("c"));
    }

    @Test
    public void testMaxSizeOfOne() {
        LruCache<Integer, String> cache = new LruCache<>(1);
        cache.put(1, "one");
        assertEquals("one", cache.get(1));
        cache.put(2, "two");
        assertNull(cache.get(1), "Previous entry should be evicted when maxSize is 1");
        assertEquals("two", cache.get(2));
    }

    @Test
    public void testMaxSizeZeroThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new LruCache<>(0));
    }

    @Test
    public void testNegativeMaxSizeThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new LruCache<>(-1));
    }

    @Test
    public void testOverwriteExistingKey() {
        LruCache<String, String> cache = new LruCache<>(3);
        cache.put("a", "alpha");
        cache.put("a", "updated");
        assertEquals("updated", cache.get("a"));
        assertEquals(1, cache.size());
    }

    @Test
    public void testSizeReflectsCurrentEntries() {
        LruCache<String, String> cache = new LruCache<>(3);
        assertEquals(0, cache.size());
        cache.put("a", "alpha");
        assertEquals(1, cache.size());
        cache.put("b", "beta");
        assertEquals(2, cache.size());
        cache.put("c", "gamma");
        assertEquals(3, cache.size());
        // Should not grow beyond maxSize.
        cache.put("d", "delta");
        assertEquals(3, cache.size());
    }

    @Test
    public void testContainsKey() {
        LruCache<String, String> cache = new LruCache<>(3);
        cache.put("a", "alpha");
        assertTrue(cache.containsKey("a"));
        assertFalse(cache.containsKey("b"));
    }

    @Test
    public void testRemove() {
        LruCache<String, String> cache = new LruCache<>(3);
        cache.put("a", "alpha");
        cache.put("b", "beta");
        cache.remove("a");
        assertNull(cache.get("a"));
        assertEquals(1, cache.size());
    }

    @Test
    public void testClear() {
        LruCache<String, String> cache = new LruCache<>(3);
        cache.put("a", "alpha");
        cache.put("b", "beta");
        cache.clear();
        assertEquals(0, cache.size());
        assertNull(cache.get("a"));
    }

    @Test
    public void testConstructorWithFullParameters() {
        LruCache<String, String> cache = new LruCache<>(5, 16, 0.75f);
        cache.put("a", "alpha");
        assertEquals("alpha", cache.get("a"));
    }

    @Test
    public void testEvictionSequence() {
        LruCache<Integer, String> cache = new LruCache<>(3);
        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");
        // All present.
        assertEquals(3, cache.size());
        // Add 4, evicts 1 (oldest).
        cache.put(4, "four");
        assertNull(cache.get(1));
        assertEquals("two", cache.get(2));
        // Add 5, evicts 3 (2 was just accessed by get).
        cache.put(5, "five");
        assertNull(cache.get(3));
        assertEquals("two", cache.get(2));
        assertEquals("four", cache.get(4));
        assertEquals("five", cache.get(5));
    }
}
