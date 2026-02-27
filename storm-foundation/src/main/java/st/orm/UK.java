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
 * Marks a field as a unique key. Use this annotation on fields that have a unique constraint in the database.
 * Fields annotated with {@code @UK} indicate that the corresponding column contains unique values, making them
 * suitable for single-result lookups and for use as keyset pagination cursors.
 *
 * <p>The {@link PK @PK} annotation is meta-annotated with {@code @UK}, so primary key fields are automatically
 * recognized as unique without needing an explicit {@code @UK} annotation.</p>
 *
 * <h2>NULL Handling</h2>
 *
 * <p>In standard SQL, {@code NULL != NULL}, so a UNIQUE constraint typically allows multiple rows with NULL
 * values. This breaks the uniqueness guarantee that keyset pagination ({@code slice}/{@code sliceAfter}/
 * {@code sliceBefore}) relies on, because {@code WHERE key > cursor} silently excludes NULL rows.</p>
 *
 * <p>Some databases treat NULLs as equal for uniqueness purposes: PostgreSQL 15+ supports
 * {@code NULLS NOT DISTINCT}, and SQL Server allows only one NULL by default. Since Storm cannot determine the
 * database constraint behavior from the code alone, you can declare it explicitly via {@link #nullsDistinct()}.</p>
 *
 * <p>When a nullable field is annotated with {@code @UK} and {@code nullsDistinct} is {@code true} (the
 * default), the metamodel processor emits a compile-time warning, and {@code slice} methods throw a
 * {@link PersistenceException} at runtime. To suppress the warning and enable keyset pagination, either make
 * the field non-nullable (use a primitive type or add {@code @Nonnull}), or set
 * {@code @UK(nullsDistinct = false)} to indicate that the database constraint prevents duplicate NULLs.</p>
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
 * <p>For compound unique constraints spanning multiple columns, use an inline record annotated with {@code @UK}:
 *
 * <p>Java:
 * <pre>{@code
 * record UserEmailUK(int userId, String email) {}
 *
 * record SomeEntity(@PK Integer id,
 *                   @FK User user,
 *                   String email,
 *                   @UK @Persist(insertable = false, updatable = false) UserEmailUK uniqueKey
 * ) implements Entity<Integer> {}
 * }</pre>
 *
 * <p>Kotlin:
 * <pre>{@code
 * data class UserEmailUK(val userId: Int, val email: String)
 *
 * data class SomeEntity(@PK val id: Int?,
 *                       @FK val user: User,
 *                       val email: String,
 *                       @UK @Persist(insertable = false, updatable = false) val uniqueKey: UserEmailUK
 * ) : Entity<Int>
 * }</pre>
 *
 * <p>The {@code @Persist(insertable = false, updatable = false)} annotation prevents the inline record's columns
 * from being persisted separately when they overlap with other fields on the entity.
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

    /**
     * Indicates whether NULL values are considered distinct for the purpose of the UNIQUE constraint.
     *
     * <p>When {@code true} (the default, matching the SQL standard), the database allows multiple rows with NULL
     * values in the unique column. This means the uniqueness guarantee is broken for nullable fields, which makes
     * keyset pagination unsafe.</p>
     *
     * <p>Set to {@code false} when the database treats NULLs as equal (e.g., PostgreSQL 15+ with
     * {@code NULLS NOT DISTINCT}, or SQL Server which allows only one NULL by default). This tells Storm that
     * the nullable field is safe for keyset pagination.</p>
     *
     * <p>This attribute has no effect on fields that are already non-nullable (primitives, {@code @PK}, or fields
     * annotated with {@code @Nonnull}).</p>
     *
     * @return {@code true} if NULLs are distinct (SQL standard), {@code false} if the database prevents
     *         duplicate NULLs.
     * @since 1.9
     */
    boolean nullsDistinct() default true;
}
