/*
 * Copyright 2024 - 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package st.orm.core.repository.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.Integer.MAX_VALUE;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterators.spliteratorUnknownSize;

/**
 * Helper class for working with streams.
 *
 * @since 1.7
 */
public final class StreamSupport {
    private StreamSupport() {
    }

    /**
     * Generates a stream of slices, each containing a subset of elements from the original stream up to a specified
     * size. This method is designed to facilitate batch processing of large streams by dividing the stream into
     * smaller manageable slices, which can be processed independently.
     *
     * <p>If the specified size is equal to {@code Integer.MAX_VALUE}, this method will return a single slice containing
     * the original stream, effectively bypassing the slicing mechanism. This is useful for operations that can handle
     * all elements at once without the need for batching.</p>
     *
     * @param <X> the type of elements in the stream.
     * @param stream the original stream of elements to be sliced.
     * @param size the maximum number of elements to include in each slice. If {@code size} is
     * {@code Integer.MAX_VALUE}, only one slice will be returned.
     * @return a stream of slices, where each slice contains up to {@code size} elements from the original stream.
     */
    public static <X> Stream<List<X>> chunked(@Nonnull Stream<X> stream, int size) {
        if (size <= 0) throw new IllegalArgumentException("size must be > 0");
        if (size == MAX_VALUE) {
            return Stream.of(stream.toList());
        }
        // We're lifting the resource closing logic from the input stream to the output stream.
        final Iterator<X> iterator = stream.iterator();
        var it = new Iterator<List<X>>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public List<X> next() {
                Iterator<X> sliceIterator = new Iterator<>() {
                    private int count = 0;

                    @Override
                    public boolean hasNext() {
                        return count < size && iterator.hasNext();
                    }

                    @Override
                    public X next() {
                        if (count >= size) {
                            throw new IllegalStateException("Size exceeded.");
                        }
                        count++;
                        return iterator.next();
                    }
                };
                return java.util.stream.StreamSupport.stream(spliteratorUnknownSize(sliceIterator, 0), false).toList();
            }
        };
        return java.util.stream.StreamSupport.stream(spliteratorUnknownSize(it, 0), false)
                .onClose(stream::close);
    }

    /**
     * A partition produced by {@link #partitioned(Stream, int, Function)} or its overloads.
     * Each partition contains a key and a chunk of elements associated with that key.
     *
     * @param key the partition key.
     * @param chunk the elements belonging to this partition, in encounter order.
     */
    public record Partition<K, V>(K key, List<V> chunk) {}

    /**
     * Partitions a stream into keyed chunks of a fixed maximum size.
     *
     * <p>Elements are grouped by {@code partitionFunction}. For each key, elements are accumulated into chunks of at
     * most {@code size} elements. Chunks are emitted as soon as they reach {@code size}. When the input stream is
     * exhausted, any remaining partial chunks are flushed.</p>
     *
     * <p>This overload does not limit the number of distinct keys (no overflow partition is used).</p>
     *
     * @param stream the input stream.
     * @param size the maximum number of elements per partition chunk.
     * @param partitionFunction function used to derive the partition key from an element.
     * @param <K> the partition key type.
     * @param <V> the element type.
     * @return a stream of partitions.
     * @throws IllegalArgumentException if {@code size <= 0}.
     * @since 1.7
     */
    public static <K, V> Stream<Partition<K, V>> partitioned(
            @Nonnull Stream<V> stream,
            int size,
            @Nonnull Function<V, K> partitionFunction
    ) {
        return partitioned(stream, size, partitionFunction, MAX_VALUE, null);
    }

    /**
     * Partitions a stream into keyed chunks of a fixed maximum size, while limiting the number of distinct partition
     * keys and routing excess keys to a dedicated overflow partition.
     *
     * <p>Elements are grouped by {@code partitionFunction}. At most {@code maxPartitions} distinct partition keys are
     * allowed in total. One slot is always reserved for {@code overflowKey}, even if it is not encountered in the input
     * stream. This means the maximum number of "normal" keys derived from {@code partitionFunction} is
     * {@code maxPartitions - 1}.</p>
     *
     * <p>If a new element would introduce a new "normal" key beyond this limit, the element is assigned to
     * {@code overflowKey} instead. If {@code maxPartitions} is {@code 1}, all elements are assigned to
     * {@code overflowKey}.</p>
     *
     * <p>For each key, elements are accumulated into chunks of at most {@code size} elements. Chunks are emitted as
     * soon as they reach {@code size}. When the input stream is exhausted, any remaining partial chunks are
     * flushed.</p>
     *
     * <p>The encounter order of partitions is stable: keys are emitted in encounter order of their first appearance.
     * The overflow key will appear at the point where it is first needed.</p>
     *
     * @param stream the input stream.
     * @param size the maximum number of elements per partition chunk.
     * @param partitionFunction function used to derive the partition key from an element.
     * @param maxPartitions the maximum number of distinct partition keys allowed in total, including the reserved
     *                      overflow slot.
     * @param overflowKey the key used as a sink for elements whose original partition would
     *                    exceed the configured limit.
     * @param <K> the partition key type.
     * @param <V> the element type.
     * @return a stream of partitions.
     * @throws IllegalArgumentException if {@code size <= 0} or {@code maxPartitions <= 0}.
     * @throws NullPointerException if {@code overflowKey} is null and {@code maxPartitions != Integer.MAX_VALUE}.
     * @since 1.7
     */
    public static <V, K> Stream<Partition<K, V>> partitioned(
            @Nonnull Stream<V> stream,
            int size,
            @Nonnull Function<V, K> partitionFunction,
            int maxPartitions,
            @Nullable K overflowKey
    ) {
        requireNonNull(stream, "stream");
        requireNonNull(partitionFunction, "partitionFunction");
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }
        if (maxPartitions <= 0) {
            throw new IllegalArgumentException("maxPartitions must be > 0");
        }
        if (maxPartitions != MAX_VALUE) {
            requireNonNull(overflowKey, "overflowKey");
        }
        final Iterator<V> iterator = stream.iterator();
        var out = new Iterator<Partition<K, V>>() {
            // Keep buffers in encounter order of first-seen keys.
            private final Map<K, ArrayList<V>> buffers = new LinkedHashMap<>();
            private final Deque<Partition<K, V>> ready = new ArrayDeque<>();
            private boolean finished = false;

            @Override
            public boolean hasNext() {
                fillReadyIfNeeded();
                return !ready.isEmpty();
            }

            @Override
            public Partition<K, V> next() {
                fillReadyIfNeeded();
                return ready.removeFirst();
            }

            private void fillReadyIfNeeded() {
                if (!ready.isEmpty() || finished) {
                    return;
                }
                while (ready.isEmpty() && iterator.hasNext()) {
                    V element = iterator.next();
                    K originalKey = partitionFunction.apply(element);
                    K key = chooseKey(originalKey);
                    List<V> buffer = buffers.computeIfAbsent(key, k -> new ArrayList<>(Math.min(size, 16)));
                    buffer.add(element);
                    if (buffer.size() >= size && size != MAX_VALUE) {
                        ArrayList<V> slice = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) slice.add(buffer.get(i));
                        buffer.subList(0, size).clear();
                        ready.addLast(new Partition<>(key, List.copyOf(slice)));
                        if (buffer.isEmpty()) buffers.remove(key);
                    }
                }
                if (ready.isEmpty() && !finished) {
                    finished = true;
                    for (var entry : buffers.entrySet()) {
                        if (!entry.getValue().isEmpty()) {
                            ready.addLast(new Partition<>(entry.getKey(), List.copyOf(entry.getValue())));
                        }
                    }
                    buffers.clear();
                }
            }

            private K chooseKey(K originalKey) {
                if (maxPartitions == MAX_VALUE) {
                    return originalKey;
                }
                if (buffers.containsKey(originalKey)) {
                    return originalKey;
                }
                // Treat the overflow key as a separate reserved slot.
                if (originalKey != null && originalKey.equals(overflowKey)) {
                    return overflowKey;
                }
                int normalKeys = buffers.containsKey(overflowKey) ? buffers.size() - 1 : buffers.size();
                int maxNormalKeys = Math.max(0, maxPartitions - 1);
                // Allow introducing new normal keys until we hit the normal-key cap.
                if (normalKeys < maxNormalKeys) {
                    return originalKey;
                }
                // Otherwise, route to overflow.
                return overflowKey;
            }
        };
        return java.util.stream.StreamSupport.stream(spliteratorUnknownSize(out, 0), false)
                .onClose(stream::close);
    }
}
