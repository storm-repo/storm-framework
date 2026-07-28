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
 * One distinct statement within a scope, with what it cost in total.
 *
 * A high [executions] count against a modest [duration] each is repetition; ranking by total [duration] puts the
 * statement that actually cost the time first, whether it was slow once or cheap many times.
 *
 * @property statement the statement text, with placeholders.
 * @property dataType the simple name of the entity or projection the statement targets, or `-` when it targets
 *   none.
 * @property fetch whether it resolved a reference.
 * @property executions how many times it ran.
 * @property variants how many distinct texts the group covers; above one, a collection parameter expanded to a
 *   different number of placeholders per execution.
 * @property duration the summed duration of those executions.
 * @property rows the rows the executions produced or affected, in total.
 * @property exactRows whether that count is exact; false when a driver declined to report a batch entry's count
 *   or a stream closed before its end, which the rendering marks `*`.
 * @property callSite the application frame the executions came from, or `null` when the scope does not record
 *   call sites; the first seen when a group covers several.
 * @property sites how many distinct call sites the group covers.
 * @since 1.13
 */
data class StatementSummary(
    val statement: String,
    val dataType: String,
    val fetch: Boolean,
    val executions: Int,
    val variants: Int,
    val duration: Duration,
    val rows: Long,
    val exactRows: Boolean,
    val callSite: String?,
    val sites: Int,
)
