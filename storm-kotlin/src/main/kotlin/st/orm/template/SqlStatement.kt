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
package st.orm.template

import kotlin.time.Duration

/**
 * One statement recorded by a scope.
 *
 * Parameter values are deliberately absent: they are database values, and a summary is meant to be safe to log in
 * production. To see values, raise the `st.orm.sql` logger to `TRACE`.
 *
 * @property operation what the statement does, such as `SELECT`.
 * @property dataType the simple name of the entity or projection it targets, or `-` when it targets none.
 * @property fetch whether the statement was a fetch rather than one the code asked for.
 * @property sql the statement text, with placeholders.
 * @property duration how long the execution took.
 * @property rows the rows the execution produced or affected; a lower bound when not exact.
 * @property exactRows whether that count is exact; false when a driver declined to report a batch entry's count
 *   or a stream closed before its end, which the rendering marks `*`.
 * @since 1.13
 */
data class SqlStatement(
    val operation: String,
    val dataType: String,
    val fetch: Boolean,
    val sql: String,
    val duration: Duration,
    val rows: Long,
    val exactRows: Boolean,
)
