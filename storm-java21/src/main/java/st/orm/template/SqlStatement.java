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
import java.time.Duration;

/**
 * One statement recorded by a scope.
 *
 * <p>Parameter values are deliberately absent: they are database values, and a summary is meant to be safe to log
 * in production. To see values, raise the {@code st.orm.sql} logger to {@code TRACE}.</p>
 *
 * @param operation what the statement does, such as {@code SELECT}.
 * @param dataType the simple name of the entity or projection it targets, or {@code -} when it targets none.
 * @param fetch whether the statement was a fetch rather than one the code asked for.
 * @param sql the statement text, with placeholders.
 * @param duration how long the execution took.
 * @param rows the rows the execution produced or affected; a lower bound when not exact.
 * @param exactRows whether that count is exact; false when a driver declined to report a batch entry's count or
 *                  a stream closed before its end, which the rendering marks {@code *}.
 * @since 1.13
 */
public record SqlStatement(@Nonnull String operation,
                           @Nonnull String dataType,
                           boolean fetch,
                           @Nonnull String sql,
                           @Nonnull Duration duration,
                           long rows,
                           boolean exactRows) {

    public SqlStatement {
        requireNonNull(operation, "operation");
        requireNonNull(dataType, "dataType");
        requireNonNull(sql, "sql");
        requireNonNull(duration, "duration");
    }
}
