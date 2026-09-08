/*
 * Copyright 2024 - 2026 the original author or authors.
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
package st.orm;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * A contiguous part of a result, with the two flags that say whether the result continues on either side.
 *
 * <p>A {@link Page} is a slice with a total count and a page number, a {@link Window} is a slice with the
 * navigation tokens of keyset scrolling, and {@code slice(size)} on the query builder returns a slice that carries
 * neither: its content, and whether rows exist before and after it. A slice iterates over its content, so a loop
 * reads {@code for (User user : window)} rather than through {@code content()}.</p>
 *
 * @param <R> the result type.
 * @since 1.11
 */
public interface Slice<R> extends Iterable<R> {

    /**
     * Returns the list of results in this slice. The list is immutable and never contains {@code null} elements.
     *
     * @return the results.
     */
    List<R> content();

    /**
     * Returns {@code true} if more results exist beyond this slice in the forward direction.
     *
     * @return whether more results exist.
     */
    boolean hasNext();

    /**
     * Returns {@code true} if results exist before this slice.
     *
     * @return whether previous results exist.
     */
    boolean hasPrevious();

    /**
     * Returns the number of results in this slice.
     *
     * @return the number of results.
     * @since 1.14
     */
    default int size() {
        return content().size();
    }

    /**
     * Returns {@code true} if this slice has no results.
     *
     * @return whether the slice is empty.
     * @since 1.14
     */
    default boolean isEmpty() {
        return content().isEmpty();
    }

    /**
     * Returns an iterator over the results in this slice.
     *
     * @return an iterator over the content.
     * @since 1.14
     */
    @Override
    default Iterator<R> iterator() {
        return content().iterator();
    }

    /**
     * Returns the results in this slice as a stream.
     *
     * @return a stream over the content.
     * @since 1.14
     */
    default Stream<R> stream() {
        return content().stream();
    }

    /**
     * Returns a slice with the given content and flags, and nothing to navigate by.
     *
     * @param content the results.
     * @param hasNext whether rows exist after this slice.
     * @param hasPrevious whether rows exist before this slice.
     * @param <R> the result type.
     * @return the slice.
     * @since 1.14
     */
    static <R> Slice<R> of(List<R> content, boolean hasNext, boolean hasPrevious) {
        return new SimpleSlice<>(content, hasNext, hasPrevious);
    }
}
