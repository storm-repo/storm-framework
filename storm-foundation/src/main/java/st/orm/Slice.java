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
import java.util.List;

/**
 * Represents a slice of query results, typically used for keyset-based pagination.
 *
 * <p>A slice contains a list of results and a flag indicating whether more results are available beyond the current
 * slice. Unlike offset-based pagination, keyset pagination provides stable and efficient paging by using a column
 * value as a cursor.</p>
 *
 * @param content the list of results in this slice.
 * @param hasNext {@code true} if there are more results beyond this slice, {@code false} otherwise.
 * @param <R> the type of the results.
 * @since 1.9
 */
public record Slice<R>(@Nonnull List<R> content, boolean hasNext) {
    public Slice {
        content = copyOf(content);
    }
}
