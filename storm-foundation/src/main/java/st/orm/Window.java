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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Represents a window of query results from a scrolling operation where the result type matches the data type.
 *
 * <p>A {@code Window} is the scrolling counterpart of {@link Page}. While a {@code Page} contains total counts and
 * page numbers for offset-based navigation, a {@code Window} contains cursor-based navigation tokens that allow
 * sequential traversal through large result sets.</p>
 *
 * <p>This is the common case for entity and projection queries where the result type is the same as the data type.
 * For queries where the result type differs from the data type (e.g., ref queries), see {@link MappedWindow}.</p>
 *
 * <p>Use {@link #hasNext()} and {@link #nextScrollable()} to move forward, and {@link #hasPrevious()} and
 * {@link #previousScrollable()} to move backward. Pass the returned {@link Scrollable} to the repository's
 * {@code scroll} method to fetch the adjacent window.</p>
 *
 * <pre>{@code
 * Window<User> window = userRepository.scroll(Scrollable.of(User_.id, 20));
 * if (window.hasNext()) {
 *     Window<User> next = userRepository.scroll(window.nextScrollable());
 * }
 * }</pre>
 *
 * @param content the list of results in this window; never contains {@code null} elements.
 * @param hasNext {@code true} if more results exist beyond this window in the scroll direction.
 * @param nextScrollable the scrollable to fetch the next window, or {@code null} if there is no next window.
 * @param previousScrollable the scrollable to fetch the previous window, or {@code null} if this is the first window.
 * @param <T> the data type of both the results and the {@link Scrollable} navigation tokens.
 * @since 1.11
 */
public record Window<T extends Data>(
        @Nonnull List<T> content,
        boolean hasNext,
        @Nullable Scrollable<T> nextScrollable,
        @Nullable Scrollable<T> previousScrollable
) {
    public Window {
        content = copyOf(content);
    }

    /**
     * Returns an empty window with no content and no navigation tokens.
     *
     * @param <T> the data type.
     * @return an empty window.
     */
    public static <T extends Data> Window<T> empty() {
        return new Window<>(List.of(), false, null, null);
    }

    /**
     * Creates a {@code Window} from a {@link MappedWindow} where the result type matches the data type.
     *
     * @param mappedWindow the mapped window to convert.
     * @param <T> the data type.
     * @return a window with the same content and navigation tokens.
     */
    public static <T extends Data> Window<T> of(@Nonnull MappedWindow<T, T> mappedWindow) {
        return new Window<>(mappedWindow.content(), mappedWindow.hasNext(),
                mappedWindow.nextScrollable(), mappedWindow.previousScrollable());
    }

    /**
     * Returns {@code true} if there is a previous window before this one.
     *
     * @return {@code true} if a previous window exists.
     */
    public boolean hasPrevious() {
        return previousScrollable != null;
    }

    /**
     * Returns an opaque cursor string for fetching the next window, or {@code null} if there is no next window.
     * Pass this string to {@link Scrollable#fromCursor} to reconstruct the scrollable for the next request.
     *
     * @return the cursor string, or {@code null}.
     * @see Scrollable#toCursor()
     * @see Scrollable#fromCursor(Metamodel.Key, String)
     */
    @Nullable
    public String nextCursor() {
        return nextScrollable != null ? nextScrollable.toCursor() : null;
    }

    /**
     * Returns an opaque cursor string for fetching the previous window, or {@code null} if this is the first window.
     * Pass this string to {@link Scrollable#fromCursor} to reconstruct the scrollable for the previous request.
     *
     * @return the cursor string, or {@code null}.
     * @see Scrollable#toCursor()
     * @see Scrollable#fromCursor(Metamodel.Key, String)
     */
    @Nullable
    public String previousCursor() {
        return previousScrollable != null ? previousScrollable.toCursor() : null;
    }
}
