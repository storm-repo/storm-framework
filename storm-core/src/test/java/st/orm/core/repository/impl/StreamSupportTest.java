package st.orm.core.repository.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import st.orm.core.repository.impl.StreamSupport.Partition;

/**
 * Tests for {@link StreamSupport} covering chunked and partitioned stream operations.
 */
class StreamSupportTest {

    // ---- chunked tests ----

    @Test
    void testChunkedBasic() {
        var chunks = StreamSupport.chunked(Stream.of(1, 2, 3, 4, 5), 2).toList();
        assertEquals(3, chunks.size());
        assertEquals(List.of(1, 2), chunks.get(0));
        assertEquals(List.of(3, 4), chunks.get(1));
        assertEquals(List.of(5), chunks.get(2));
    }

    @Test
    void testChunkedExactMultiple() {
        var chunks = StreamSupport.chunked(Stream.of(1, 2, 3, 4), 2).toList();
        assertEquals(2, chunks.size());
        assertEquals(List.of(1, 2), chunks.get(0));
        assertEquals(List.of(3, 4), chunks.get(1));
    }

    @Test
    void testChunkedSingleElement() {
        var chunks = StreamSupport.chunked(Stream.of(1), 3).toList();
        assertEquals(1, chunks.size());
        assertEquals(List.of(1), chunks.get(0));
    }

    @Test
    void testChunkedEmptyStream() {
        var chunks = StreamSupport.chunked(Stream.empty(), 5).toList();
        assertEquals(0, chunks.size());
    }

    @Test
    void testChunkedMaxValue() {
        // Integer.MAX_VALUE should return a single slice.
        var chunks = StreamSupport.chunked(Stream.of(1, 2, 3), Integer.MAX_VALUE).toList();
        assertEquals(1, chunks.size());
        assertEquals(List.of(1, 2, 3), chunks.get(0));
    }

    @Test
    void testChunkedInvalidSize() {
        assertThrows(IllegalArgumentException.class, () -> StreamSupport.chunked(Stream.of(1), 0));
        assertThrows(IllegalArgumentException.class, () -> StreamSupport.chunked(Stream.of(1), -1));
    }

    @Test
    void testChunkedStreamCloses() {
        var closed = new boolean[]{false};
        var stream = Stream.of(1, 2, 3).onClose(() -> closed[0] = true);
        var result = StreamSupport.chunked(stream, 2);
        result.close();
        assertTrue(closed[0]);
    }

    // ---- partitioned tests (without overflow) ----

    @Test
    void testPartitionedBasic() {
        var partitions = StreamSupport.partitioned(
                Stream.of("a1", "b1", "a2", "b2", "a3"),
                2,
                s -> s.substring(0, 1)
        ).toList();

        // "a" should have chunks: [a1, a2] and [a3]
        // "b" should have chunk: [b1, b2]
        long aChunks = partitions.stream().filter(p -> p.key().equals("a")).count();
        long bChunks = partitions.stream().filter(p -> p.key().equals("b")).count();
        assertEquals(2, aChunks); // a has 3 elements, split into chunks of 2
        assertEquals(1, bChunks); // b has 2 elements, one full chunk
    }

    @Test
    void testPartitionedSingleKey() {
        var partitions = StreamSupport.partitioned(
                Stream.of(1, 2, 3, 4, 5),
                3,
                x -> "all"
        ).toList();

        assertEquals(2, partitions.size());
        assertEquals(List.of(1, 2, 3), partitions.get(0).chunk());
        assertEquals(List.of(4, 5), partitions.get(1).chunk());
    }

    @Test
    void testPartitionedEmptyStream() {
        var partitions = StreamSupport.partitioned(
                Stream.<String>empty(),
                5,
                s -> s
        ).toList();

        assertTrue(partitions.isEmpty());
    }

    @Test
    void testPartitionedMaxValueSize() {
        var partitions = StreamSupport.partitioned(
                Stream.of(1, 2, 3),
                Integer.MAX_VALUE,
                x -> "key"
        ).toList();

        assertEquals(1, partitions.size());
        assertEquals(List.of(1, 2, 3), partitions.get(0).chunk());
    }

    @Test
    void testPartitionedInvalidSize() {
        assertThrows(IllegalArgumentException.class,
                () -> StreamSupport.partitioned(Stream.of(1), 0, x -> "key"));
    }

    // ---- partitioned tests (with overflow) ----

    @Test
    void testPartitionedWithOverflow() {
        // maxPartitions=2 means 1 normal key + 1 overflow slot.
        var partitions = StreamSupport.partitioned(
                Stream.of("a1", "b1", "c1", "a2"),
                10,
                s -> s.substring(0, 1),
                2,
                "overflow"
        ).toList();

        // First key "a" takes the single normal slot. "b" and "c" go to overflow.
        long normalPartitions = partitions.stream().filter(p -> !p.key().equals("overflow")).count();
        long overflowPartitions = partitions.stream().filter(p -> p.key().equals("overflow")).count();
        assertTrue(normalPartitions >= 1);
        assertTrue(overflowPartitions >= 1);
    }

    @Test
    void testPartitionedWithOverflowAllToOverflow() {
        // maxPartitions=1 means all elements go to overflow.
        var partitions = StreamSupport.partitioned(
                Stream.of("a1", "b1", "c1"),
                10,
                s -> s.substring(0, 1),
                1,
                "overflow"
        ).toList();

        assertEquals(1, partitions.size());
        assertEquals("overflow", partitions.get(0).key());
        assertEquals(3, partitions.get(0).chunk().size());
    }

    @Test
    void testPartitionedInvalidMaxPartitions() {
        assertThrows(IllegalArgumentException.class,
                () -> StreamSupport.partitioned(Stream.of(1), 1, x -> "key", 0, "overflow"));
    }

    @Test
    void testPartitionedNullOverflowKeyWithLimitedPartitions() {
        assertThrows(NullPointerException.class,
                () -> StreamSupport.partitioned(Stream.of(1), 1, x -> "key", 2, null));
    }

    @Test
    void testPartitionedStreamCloses() {
        var closed = new boolean[]{false};
        var stream = Stream.of("a1", "b1").onClose(() -> closed[0] = true);
        var result = StreamSupport.partitioned(stream, 10, s -> s.substring(0, 1));
        result.close();
        assertTrue(closed[0]);
    }

    @Test
    void testPartitionRecord() {
        Partition<String, Integer> partition = new Partition<>("key", List.of(1, 2, 3));
        assertEquals("key", partition.key());
        assertEquals(List.of(1, 2, 3), partition.chunk());
    }

    @Test
    void testPartitionedWithOverflowKeyInInput() {
        // When overflow key appears in input, it should be treated as overflow key.
        var partitions = StreamSupport.partitioned(
                Stream.of("a1", "overflow1", "b1"),
                10,
                s -> s.startsWith("overflow") ? "overflow" : s.substring(0, 1),
                3,
                "overflow"
        ).toList();

        boolean hasOverflow = partitions.stream().anyMatch(p -> p.key().equals("overflow"));
        assertTrue(hasOverflow);
    }
}
