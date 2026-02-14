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
import static st.orm.GenerationStrategy.IDENTITY;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a field as the primary key of an entity. This annotation can also specify the database column name
 * for the primary key using either {@link #value()} or {@link #name()}. These attributes are aliases:
 * if both are provided, they must have the same value.
 *
 * <p>Usage examples:
 * <ul>
 *   <li>{@code @PK("id")}</li>
 *   <li>{@code @PK(name="id")}</li>
 *   <li>{@code @PK @DbColumn("id")}</li>
 * </ul>
 * Each sets the primary key column name to {@code "id"}.
 *
 * <p>If no value is specified (i.e., both {@code value()} and {@code name()} are {@code ""}),
 * the automatic column name resolution strategy will be applied.
 *
 * <h2>Composite Primary Keys</h2>
 *
 * <p>For composite primary keys, define a separate record containing the key components and use it as the
 * primary key field:
 * <pre>{@code
 * record UserRolePk(int userId, int roleId) {}
 *
 * record UserRole(@PK UserRolePk pk,
 *                 @FK @Persist(insertable = false, updatable = false) User user,
 *                 @FK @Persist(insertable = false, updatable = false) Role role
 * ) implements Entity<UserRolePk> {}
 * }</pre>
 *
 * <p>The {@link Persist @Persist} annotation indicates that the FK columns overlap with the composite PK columns.
 * The FK fields are used to load the related entities, but the column values come from the PK during insert/update
 * operations.
 *
 * <h2>Primary Key as Foreign Key</h2>
 *
 * <p>A primary key can also be a foreign key, which is useful for dependent one-to-one relationships,
 * extension tables, or table-per-subtype inheritance. Use both {@code @PK} and {@link FK @FK} on the same
 * field with {@code generation = NONE}:
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
 * @see FK
 * @see GenerationStrategy
 */
@Target({RECORD_COMPONENT, PARAMETER})
@Retention(RUNTIME)
public @interface PK {

    /**
     * The database column name for the primary key.
     * Acts as an alias for {@link #name()}.
     */
    String value() default "";

    /**
     * The database column name for the primary key.
     * Acts as an alias for {@link #value()}.
     */
    String name() default "";

    /**
     * The primary key generation strategy.
     *
     * Default value is {@link GenerationStrategy#IDENTITY}.
     * @return the primary key generation strategy.
     */
    GenerationStrategy generation() default IDENTITY;

    /**
     * The sequence name for the primary key.
     *
     * The sequence must only be used with {@link GenerationStrategy#SEQUENCE}. Default value is {@code ""}.
     *
     * @return the sequence name.
     */
    String sequence() default "";
}
