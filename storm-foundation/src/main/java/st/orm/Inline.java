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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Indicates that the underlying fields of the record component are inlined in this record.
 *
 * <p>This annotation must only be used on record components that are also records. Note that record components that do
 * not specify {@code @FK} or converter annotations are implicitly regarded as inlined. Note that the {@code @Inline}
 * is optional and can be omitted if the record component is inlined.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * record City(@PK int id, String name, long population)
 *         implements Entity<Integer> {};
 *
 * record Address(String street, String postalCode, @FK City city) {};
 *
 * record User(@PK int id, String email, LocalDate birthDate, @Inline Address address)
 *         implements Entity<Integer> {};
 * }</pre>
 *
 * <p>Is similar to:</p>
 * <pre>{@code
 * record User(@PK int id, String email, LocalDate birthDate,
 *             String street, String postalCode, @FK City city)
 *         implements Entity<Integer> {};
 *
 * record City(@PK int id, String name, long population)
 *         implements Entity<Integer> {};
 * }</pre>
 */
@Target({RECORD_COMPONENT, PARAMETER})
@Retention(RUNTIME)
public @interface Inline {
}
