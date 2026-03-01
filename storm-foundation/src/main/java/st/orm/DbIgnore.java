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

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Suppresses schema validation for the annotated entity type or record component.
 *
 * <p>When placed on an entity type, all schema validation checks are skipped for that entity. When placed on a
 * record component, validation checks for the corresponding column (or columns, in the case of inline records) are
 * skipped.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Suppress all schema validation for a legacy entity.
 * @DbIgnore
 * record LegacyUser(@PK Integer id, String name) implements Entity<Integer> {}
 *
 * // Suppress schema validation for a specific field.
 * record User(
 *     @PK Integer id,
 *     @DbIgnore("DB uses FLOAT, but column only stores whole numbers")
 *     Integer age
 * ) implements Entity<Integer> {}
 * }</pre>
 *
 * @since 1.9
 */
@Target({TYPE, RECORD_COMPONENT, PARAMETER})
@Retention(RUNTIME)
public @interface DbIgnore {

    /**
     * Optional reason for ignoring schema validation.
     *
     * <p>Documents why the mismatch is acceptable, for example:</p>
     * <pre>{@code
     * @DbIgnore("DB uses FLOAT, but column only stores whole numbers")
     * Integer age
     * }</pre>
     */
    String value() default "";
}
