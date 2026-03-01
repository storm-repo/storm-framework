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

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;
import st.orm.PersistenceException;

/**
 * Thrown when schema validation detects one or more mismatches between entity definitions and the database schema.
 *
 * @since 1.9
 */
public class SchemaValidationException extends PersistenceException {

    private final List<SchemaValidationError> errors;

    /**
     * Creates a new schema validation exception with the given errors.
     *
     * @param errors the validation errors (must not be empty).
     */
    public SchemaValidationException(@Nonnull List<SchemaValidationError> errors) {
        super(formatMessage(errors));
        this.errors = List.copyOf(errors);
    }

    /**
     * Returns the list of validation errors.
     *
     * @return an unmodifiable list of validation errors.
     */
    public List<SchemaValidationError> getErrors() {
        return errors;
    }

    private static String formatMessage(@Nonnull List<SchemaValidationError> errors) {
        return "Schema validation failed with %d error(s):\n".formatted(errors.size())
                + errors.stream()
                        .map(error -> "  - " + error.toString())
                        .collect(Collectors.joining("\n"));
    }
}
