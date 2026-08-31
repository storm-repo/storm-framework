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
import javax.sql.DataSource;
import st.orm.Data;
import st.orm.core.template.impl.SchemaValidator;

/**
 * Validates the database schema against the entity model of the test's database.
 *
 * <p>Injected into {@code @StormTest} methods that declare a {@code SchemaValidation} parameter. Validation compares
 * every discovered entity type, or an explicit selection, against the tables the test database actually holds.</p>
 *
 * <pre>{@code
 * @Test
 * void schemaMatches(SchemaValidation validation) {
 *     validation.validateOrThrow();
 * }
 * }</pre>
 *
 * @since 1.14
 */
public final class SchemaValidation {

    private final SchemaValidator validator;

    SchemaValidation(DataSource dataSource) {
        this.validator = SchemaValidator.of(dataSource);
    }

    /**
     * Validates every discovered entity type and returns the mismatches as rendered messages.
     *
     * @return the mismatches, or an empty list when the schema matches the entity model.
     */
    public List<String> validate() {
        return validator.validate().stream().map(Object::toString).toList();
    }

    /**
     * Validates the given entity types and returns the mismatches as rendered messages.
     *
     * @param types the types to validate.
     * @return the mismatches, or an empty list when the schema matches the entity model.
     */
    public List<String> validate(Iterable<Class<? extends Data>> types) {
        return validator.validate(types).stream().map(Object::toString).toList();
    }

    /**
     * Validates every discovered entity type and throws when the schema does not match the entity model.
     *
     * @throws st.orm.PersistenceException when the schema does not match, with every mismatch in the message.
     */
    public void validateOrThrow() {
        validator.validateOrThrow();
    }
}
