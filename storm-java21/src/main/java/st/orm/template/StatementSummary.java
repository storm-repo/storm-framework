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
package st.orm.template;

import static java.util.Objects.requireNonNull;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.Duration;

/**
 * One distinct statement within a scope, with what it cost in total.
 *
 * <p>A high execution count against a modest duration each is repetition; ranking by total duration puts the
 * statement that actually cost the time first, whether it was slow once or cheap many times.</p>
 *
 * @param statement the statement text, with placeholders.
 * @param dataType the simple name of the entity or projection the statement targets, or {@code -} when it
 *                 targets none.
 * @param fetch whether it resolved a reference.
 * @param executions how many times it ran.
 * @param duration the summed duration of those executions.
 * @param rows the rows the executions produced or affected, in total.
 * @param exactRows whether that count is exact; false when a driver declined to report a batch entry's count or
 *                  a stream closed before its end, which the rendering marks {@code *}.
 * @param callSite the application frame the executions came from, or {@code null} when the scope does not record
 *                 call sites; the first seen when a group covers several.
 * @param sites how many distinct call sites the group covers.
 * @since 1.13
 */
public record StatementSummary(@Nonnull String statement,
                               @Nonnull String dataType,
                               boolean fetch,
                               int executions,
                               int variants,
                               @Nonnull Duration duration,
                               long rows,
                               boolean exactRows,
                               @Nullable String callSite,
                               int sites) {

    public StatementSummary {
        requireNonNull(statement, "statement");
        requireNonNull(dataType, "dataType");
        requireNonNull(duration, "duration");
    }
}
