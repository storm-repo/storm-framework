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

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a field as a unique key. Fields annotated with {@code @UK} are guaranteed to contain unique values in the
 * database, making them suitable for single-result lookups and for use as keyset pagination cursors.
 *
 * <p>The {@link PK @PK} annotation is meta-annotated with {@code @UK}, so primary key fields are automatically
 * recognized as unique without needing an explicit {@code @UK} annotation.</p>
 *
 * <p>Usage example (Java):
 * <pre>{@code
 * record User(@PK Integer id,
 *             @UK String email,
 *             String name
 * ) implements Entity<Integer> {}
 * }</pre>
 *
 * <p>Usage example (Kotlin):
 * <pre>{@code
 * data class User(@PK val id: Int?,
 *                 @UK val email: String,
 *                 val name: String
 * ) : Entity<Int>
 * }</pre>
 *
 * <p>The metamodel processor generates {@link Metamodel.Key} instances for fields annotated with {@code @UK},
 * enabling type-safe keyset pagination and unique field lookups via repository methods like
 * {@code findBy(Metamodel.Key, value)} and {@code getBy(Metamodel.Key, value)}.</p>
 *
 * @see PK
 * @see Metamodel.Key
 * @since 1.9
 */
@Target({RECORD_COMPONENT, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface UK {
}
