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
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a field as a foreign key. You can specify the foreign key's database column name using either:
 * <ul>
 *   <li>{@link #value()}</li>
 *   <li>{@link #name()}</li>
 * </ul>
 * Both attributes are aliases. If both are specified, they must have the same value.
 *
 * <p>Usage examples:
 * <ul>
 *   <li>{@code @FK("user_id")}</li>
 *   <li>{@code @FK(name="user_id")}</li>
 *   <li>{@code @FK @DbColumn("user_id")}</li>
 * </ul>
 * Each sets the foreign key's database column name to {@code "user_id"}.
 *
 * <p>If no value is specified (i.e., both {@code value()} and {@code name()} are set to {@code ""}),
 * an automatic column name resolution strategy will be applied.
 *
 * <h2>Composite Foreign Keys</h2>
 *
 * <p>When referencing an entity with a composite primary key, Storm automatically generates multi-column
 * join conditions. Use {@link DbColumn @DbColumn} annotations to specify custom column names for each
 * component:
 * <pre>{@code
 * record AuditLog(@PK Integer id,
 *                 String action,
 *                 @FK @DbColumn("audit_user_id") @DbColumn("audit_role_id") UserRole userRole
 * ) implements Entity<Integer> {}
 * }</pre>
 *
 * <h2>Foreign Keys Overlapping with Composite Primary Key</h2>
 *
 * <p>In join tables for many-to-many relationships, the FK columns often overlap with the composite PK columns.
 * Use {@link Persist @Persist} to indicate that the FK fields are only used for loading related entities, not
 * for insert/update operations:
 * <pre>{@code
 * record UserRolePk(int userId, int roleId) {}
 *
 * record UserRole(@PK UserRolePk pk,
 *                 @FK @Persist(insertable = false, updatable = false) User user,
 *                 @FK @Persist(insertable = false, updatable = false) Role role
 * ) implements Entity<UserRolePk> {}
 * }</pre>
 *
 * <h2>Primary Key as Foreign Key</h2>
 *
 * <p>A foreign key can also serve as the primary key, which is useful for dependent one-to-one relationships,
 * extension tables, or table-per-subtype inheritance. Use both {@link PK @PK} and {@code @FK} on the same
 * field:
 * <pre>{@code
 * record UserProfile(@PK(generation = NONE) @FK User user,
 *                    String bio
 * ) implements Entity<User> {}
 * }</pre>
 *
 * <p>Column name resolution when both {@code @PK} and {@code @FK} are present:
 * <ol>
 *   <li>Explicit name in {@code @PK} (e.g., {@code @PK("user_profile_id")})</li>
 *   <li>Explicit name in {@code @DbColumn}</li>
 *   <li>Foreign key naming convention (default)</li>
 * </ol>
 *
 * @see PK
 */
@Target({RECORD_COMPONENT, PARAMETER})
@Retention(RUNTIME)
public @interface FK {

    /**
     * The database column name for the foreign key. Acts as an alias for {@link #name()}.
     */
    String value() default "";

    /**
     * The database column name for the foreign key. Acts as an alias for {@link #value()}.
     */
    String name() default "";
}
