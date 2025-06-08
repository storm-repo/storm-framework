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
package st.orm.kotlin;

import jakarta.annotation.Nonnull;
import kotlin.sequences.Sequence;

import java.util.Iterator;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Sequence} that holds resources requiring explicit closure.
 *
 * <p>Callers of {@link CloseableSequence} must ensure they explicitly invoke {@link #close()}
 * when the sequence is no longer needed, to release any underlying resources.</p>
 *
 * @param <T> The type of elements in the sequence.
 * @since 1.3
 */
public interface CloseableSequence<T> extends Sequence<T>, AutoCloseable {

    /**
     * Creates a new {@link CloseableSequence} from a given {@link Stream}.
     * <p>
     * Note: The caller is responsible for explicitly calling {@link #close()} on the returned
     * sequence to ensure resources are properly released. The sequence does not close itself
     * automatically upon reaching the end.
     * </p>
     *
     * @param stream The stream to wrap into a closeable sequence.
     * @param <T>    The type of elements.
     * @return A new {@link CloseableSequence} wrapping the given stream.
     */
    static <T> CloseableSequence<T> from(@Nonnull Stream<T> stream) {
        requireNonNull(stream);
        return new CloseableSequence<>() {
            @Override
            public void close() {
                stream.close();
            }

            @Override
            public Iterator<T> iterator() {
                return stream.iterator();
            }
        };
    }

    /**
     * Closes this sequence and releases any underlying resources.
     * Must be called explicitly by the caller.
     */
    @Override
    void close();
}
