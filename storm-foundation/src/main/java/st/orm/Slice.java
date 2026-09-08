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

import static java.util.List.copyOf;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * A slice of query results for offset-based reads without a count.
 *
 * <p>A slice is read the way a {@link Page} is, with OFFSET and LIMIT from a {@link Pageable}, but it fetches one
 * row beyond the page size instead of running a count query: {@link #hasNext()} says whether that row existed, and
 * {@link #hasPrevious()} follows from the page number. Use it for a "load more" that needs no total, and for a query
 * without a unique key, where scrolling is not possible.</p>
 *
 * <p>Page numbers are zero-based: the first slice is page 0.</p>
 *
 * @param content the list of results in this slice; never contains {@code null} elements.
 * @param hasNext {@code true} if a row existed after this slice at query time.
 * @param pageable the request that produced this slice.
 * @param <R> the type of the results.
 * @since 1.14
 */
public record Slice<R>(List<R> content, boolean hasNext, Pageable pageable) implements Iterable<R> {

    public Slice {
        content = copyOf(content);
    }

    /**
     * Creates a slice with the specified content, next flag, page number, and page size.
     *
     * @param content the list of results in this slice.
     * @param hasNext {@code true} if a row existed after this slice at query time.
     * @param pageNumber the zero-based index of this slice.
     * @param pageSize the maximum number of elements per slice.
     */
    public Slice(List<R> content, boolean hasNext, int pageNumber, int pageSize) {
        this(content, hasNext, Pageable.of(pageNumber, pageSize));
    }

    /**
     * Returns the zero-based index of this slice.
     *
     * @return the page number.
     */
    public int pageNumber() {
        return pageable.pageNumber();
    }

    /**
     * Returns the maximum number of elements per slice.
     *
     * @return the page size.
     */
    public int pageSize() {
        return pageable.pageSize();
    }

    /**
     * Returns {@code true} if a slice exists before this one, which is the case for every page number above zero.
     *
     * @return {@code true} if this is not the first slice.
     */
    public boolean hasPrevious() {
        return pageable.pageNumber() > 0;
    }

    /**
     * Returns the request for the slice after this one.
     *
     * @return the request for the next slice.
     */
    public Pageable next() {
        return pageable.next();
    }

    /**
     * Returns the request for the slice before this one, or {@code null} on the first slice.
     *
     * @return the request for the previous slice, or {@code null}.
     */
    @Nullable
    public Pageable previous() {
        return pageable.previous();
    }

    /**
     * Returns the number of results in this slice.
     *
     * @return the number of results.
     */
    public int size() {
        return content.size();
    }

    /**
     * Returns {@code true} if this slice holds no results.
     *
     * @return {@code true} if the slice is empty.
     */
    public boolean isEmpty() {
        return content.isEmpty();
    }

    @Override
    public Iterator<R> iterator() {
        return content.iterator();
    }

    /**
     * Returns the results as a stream.
     *
     * @return a stream over the content.
     */
    public Stream<R> stream() {
        return content.stream();
    }
}
