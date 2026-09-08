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

import java.util.List;

/**
 * The row a scroll request continues from, and on which side of it.
 *
 * <p>The values name one row in the ordering of the {@link Scrollable} the position belongs to: one value per
 * sort field, in sort order, and the key value last. A position {@code after} that row asks for the rows that
 * follow it in sort order; a position {@code before} it asks for the rows that precede it. Either way the window
 * comes back in sort order.</p>
 *
 * @param values the values of the sort fields and the key, in that order; never contain {@code null}.
 * @param after {@code true} to continue after the row, {@code false} to continue before it.
 * @since 1.14
 */
public record Position(List<Object> values, boolean after) {

    public Position {
        values = copyOf(values);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("A position needs at least the key value.");
        }
    }
}
