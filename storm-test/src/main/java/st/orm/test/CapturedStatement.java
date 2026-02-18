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
package st.orm.test;

import java.util.List;

/**
 * Represents a captured SQL statement with its operation type and bound parameters.
 *
 * @param operation the type of SQL operation.
 * @param statement the SQL statement with {@code ?} placeholders.
 * @param parameters the bound parameter values.
 * @since 1.9
 */
public record CapturedStatement(
        Operation operation,
        String statement,
        List<Object> parameters) {

    /**
     * Classifies the type of SQL operation.
     */
    public enum Operation {
        SELECT,
        INSERT,
        UPDATE,
        DELETE,
        UNDEFINED
    }
}
