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
package st.orm.impl;

import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import st.orm.Position;

/**
 * Lets the engine read the row values behind a {@link Position}.
 *
 * <p>An application sees a position as an opaque token that comes out of a {@link st.orm.Scrollable} and goes
 * back into one. The engine needs the values it names to build the keyset predicate and to write the cursor.
 * {@code Position} registers the reader when it loads, and every caller holds a position, so the reader is in place
 * by the time it is asked for.</p>
 */
public final class PositionAccess {

    private static volatile @Nullable Function<Position, List<Object>> reader;

    private PositionAccess() {}

    /**
     * Registers the reader. Called once, by {@link Position} when it loads.
     *
     * @param reader reads the values of a position.
     * @throws IllegalStateException if a reader is already registered.
     */
    public static void register(Function<Position, List<Object>> reader) {
        if (PositionAccess.reader != null) {
            throw new IllegalStateException("The position reader is already registered.");
        }
        PositionAccess.reader = reader;
    }

    /**
     * Returns the values a position names: one per sort field, in sort order, and the key value last.
     *
     * @param position the position.
     * @return the values; never contain {@code null}.
     */
    public static List<Object> values(Position position) {
        var reader = PositionAccess.reader;
        if (reader == null) {
            throw new IllegalStateException("The position reader is not registered.");
        }
        return reader.apply(position);
    }
}
