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
package st.orm.spi;

import java.util.Optional;

/**
 * Contributes a comment appended to SQL statements at execution time.
 *
 * <p>The comment is appended after all statement processing and caching, immediately before the statement is
 * prepared, so per-execution content such as the current trace context reaches the database without affecting
 * Storm's template cache. Database-side diagnostics — slow query logs, statement views — then carry the
 * comment, correlating captured statements back to the execution that issued them.</p>
 *
 * <p>Commenters are configured per ORM template via the template builder; they are deliberately not discovered
 * through the {@code ServiceLoader} mechanism. Note that a per-execution comment changes the statement text on
 * every call, which defeats driver-side and server-side prepared statement caching; enable selectively.</p>
 *
 * @since 1.13
 */
@FunctionalInterface
public interface SqlCommenter {

    /**
     * Returns the comment content for the statement that is about to execute, without comment delimiters,
     * or empty when no comment applies. The content must not contain the comment terminator sequence
     * (asterisk followed by slash) or semicolons; following the sqlcommenter convention, values are
     * URL-encoded, which escapes both. The framework pads the emitted comment with spaces, so leading
     * executable-comment and optimizer-hint markers are never interpreted.
     *
     * @return the comment content, such as {@code traceparent='00-4bf92f35-00f067aa-01'}.
     */
    Optional<String> comment();
}
