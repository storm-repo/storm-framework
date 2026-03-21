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
 * <p>The {@code nextScrollable} and {@code previousScrollable} navigation tokens are always provided when the window
 * has content, regardless of whether {@code hasNext} or {@code hasPrevious} is {@code true}. This allows developers
 * to follow the cursor even when no more results were detected at query time, which is useful for polling scenarios
 * where new data may appear after the initial query. The {@code hasNext} and {@code hasPrevious} flags are
 * informational: they indicate whether more results existed at the time of the query, but the decision to follow
 * the cursor is left to the developer.</p>
 *
 * @param content the list of results in this window; never contains {@code null} elements.
 * @param hasNext {@code true} if more results existed beyond this window in the scroll direction at query time.
 * @param hasPrevious {@code true} if this window was fetched with a cursor position (i.e., not the first page).
 * @param nextScrollable the scrollable to fetch the next window, or {@code null} if the window is empty.
 * @param previousScrollable the scrollable to fetch the previous window, or {@code null} if the window is empty.
 * @param <T> the data type of both the results and the {@link Scrollable} navigation tokens.
 * @since 1.11
 */
public record Window<T extends Data>(
        @Nonnull List<T> content,
        boolean hasNext,
        boolean hasPrevious,
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
        return new Window<>(List.of(), false, false, null, null);
    }

    /**
     * Creates a {@code Window} from a {@link MappedWindow} where the result type matches the data type.
     *
     * @param mappedWindow the mapped window to convert.
     * @param <T> the data type.
     * @return a window with the same content and navigation tokens.
     */
    public static <T extends Data> Window<T> of(@Nonnull MappedWindow<T, T> mappedWindow) {
        return new Window<>(mappedWindow.content(), mappedWindow.hasNext(), mappedWindow.hasPrevious(),
                mappedWindow.nextScrollable(), mappedWindow.previousScrollable());
    }

    /**
     * Returns an opaque cursor string for fetching the next window, or {@code null} if there is no next window
     * according to {@link #hasNext()}.
     *
     * <p>This method is a convenience for REST APIs that want to include a cursor only when more results were
     * detected. For polling or streaming use cases where you want to follow the cursor regardless, use
     * {@link #nextScrollable()} directly.</p>
     *
     * @return the cursor string, or {@code null}.
     * @see Scrollable#toCursor()
     * @see Scrollable#fromCursor(Metamodel.Key, String)
     */
    @Nullable
    public String nextCursor() {
        return hasNext && nextScrollable != null ? nextScrollable.toCursor() : null;
    }

    /**
     * Returns an opaque cursor string for fetching the previous window, or {@code null} if this is the first window
     * according to {@link #hasPrevious()}.
     *
     * <p>This method is a convenience for REST APIs that want to include a cursor only when previous results exist.
     * For use cases where you want to follow the cursor regardless, use {@link #previousScrollable()} directly.</p>
     *
     * @return the cursor string, or {@code null}.
     * @see Scrollable#toCursor()
     * @see Scrollable#fromCursor(Metamodel.Key, String)
     */
    @Nullable
    public String previousCursor() {
        return hasPrevious && previousScrollable != null ? previousScrollable.toCursor() : null;
    }
}
