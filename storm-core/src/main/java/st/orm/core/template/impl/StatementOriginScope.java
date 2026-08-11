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
package st.orm.core.template.impl;

import java.util.function.Supplier;
import st.orm.core.template.StatementOrigin;

/**
 * Attributes statements to what caused them to execute.
 *
 * <p>A reference resolves by running its query to completion inside {@link #resolvingReference}, on the calling
 * thread, with no suspension point in between. A thread-local marker therefore covers exactly the statements
 * that resolution issues, including in a coroutine: the marker is set and cleared within a single blocking
 * call, so it can neither leak past a suspension nor be observed by another thread.</p>
 *
 * @since 1.13
 */
public final class StatementOriginScope {

    /**
     * Nesting depth of reference fetching on the current thread, absent when none is in progress. Reading a
     * thread-local that is unset for all but resolution threads keeps the statement execution path free of both
     * allocation and a thread-local entry.
     */
    private static final ThreadLocal<int[]> FETCH_DEPTH = new ThreadLocal<>();

    private StatementOriginScope() {
    }

    /**
     * Runs the supplier with the statements it executes attributed to {@link StatementOrigin#FETCH}.
     *
     * <p>Depth is counted rather than flagged, because a resolved record can itself carry references that
     * resolve in turn.</p>
     *
     * @param supplier the resolution to run.
     * @param <T> the resolved type.
     * @return the value the supplier produced.
     */
    public static <T> T resolvingReference(Supplier<T> supplier) {
        int[] depth = FETCH_DEPTH.get();
        if (depth == null) {
            depth = new int[1];
            FETCH_DEPTH.set(depth);
        }
        depth[0]++;
        try {
            return supplier.get();
        } finally {
            if (--depth[0] == 0) {
                // Clear the thread-local to prevent memory leaks.
                FETCH_DEPTH.remove();
            }
        }
    }

    /**
     * Returns the origin to attribute a statement executing on the calling thread to.
     *
     * @return the statement origin; never {@code null}.
     */
    public static StatementOrigin current() {
        int[] depth = FETCH_DEPTH.get();
        return depth != null && depth[0] > 0 ? StatementOrigin.FETCH : StatementOrigin.DIRECT;
    }
}
