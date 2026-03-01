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
 * Configures discriminator behavior for sealed type hierarchies.
 *
 * <p>This annotation is used in three contexts:</p>
 * <ol>
 *   <li><strong>On a sealed interface</strong>: <em>Required</em> for Single-Table, <em>optional</em> for Joined
 *       Table. Specifies the discriminator column name in the database table. If no column name is provided, defaults
 *       to {@code "dtype"} (consistent with the JPA convention). When omitted for Joined Table, Storm resolves the
 *       concrete type at query time via a CASE expression that checks which extension table has a matching row.
 *       <p>Java:
 *       <pre>{@code
 *       @Discriminator                   // uses default column name "dtype"
 *       sealed interface Pet extends Entity<Integer> permits Cat, Dog {}
 *
 *       @Discriminator(column = "pet_type")   // uses custom column name
 *       sealed interface Pet extends Entity<Integer> permits Cat, Dog {}
 *       }</pre>
 *       <p>Kotlin:
 *       <pre>{@code
 *       @Discriminator                   // uses default column name "dtype"
 *       sealed interface Pet : Entity<Int>
 *
 *       @Discriminator(column = "pet_type")   // uses custom column name
 *       sealed interface Pet : Entity<Int>
 *       }</pre>
 *   </li>
 *   <li><strong>On a concrete subtype</strong>: <em>Optional.</em> Specifies the discriminator value for this subtype.
 *       Defaults to the simple class name for Single-Table/Joined patterns, or the resolved table name for
 *       Polymorphic FK.
 *       <p>Java:
 *       <pre>{@code
 *       @Discriminator("LARGE_DOG")
 *       record Dog(@PK Integer id, String name, int weight) implements Pet {}
 *       }</pre>
 *       <p>Kotlin:
 *       <pre>{@code
 *       @Discriminator("LARGE_DOG")
 *       data class Dog(@PK val id: Int?, val name: String, val weight: Int) : Pet
 *       }</pre>
 *   </li>
 *   <li><strong>On a foreign key field</strong> (Polymorphic FK): <em>Optional.</em> Customizes the discriminator
 *       column name in the referencing entity's table. Defaults to {@code "{fieldName}_type"}.
 *       <p>Java:
 *       <pre>{@code
 *       record Comment(@PK Integer id, String text,
 *                      @FK @Discriminator(column = "content_type") Ref<Commentable> target
 *       ) implements Entity<Integer> {}
 *       }</pre>
 *       <p>Kotlin:
 *       <pre>{@code
 *       data class Comment(@PK val id: Int?, val text: String,
 *                          @FK @Discriminator(column = "content_type") val target: Ref<Commentable>
 *       ) : Entity<Int>
 *       }</pre>
 *   </li>
 * </ol>
 *
 * @see DbTable
 * @see FK
 */
@Target({TYPE, RECORD_COMPONENT, PARAMETER})
@Retention(RUNTIME)
public @interface Discriminator {

    /**
     * The discriminator column name.
     *
     * <p>On sealed interfaces (Single-Table and optionally Joined Table): defaults to {@code "dtype"} (consistent with the JPA
     * convention). On FK fields (Polymorphic FK): defaults to {@code "{fieldName}_type"}.</p>
     */
    String column() default "";

    /**
     * The discriminator value for a concrete subtype.
     *
     * <p>Defaults to the simple class name for Single-Table and Joined patterns, or the resolved table name for
     * Polymorphic FK.</p>
     *
     * <p>When used as the sole attribute, can be specified as the annotation value:
     * {@code @Discriminator("LARGE_DOG")}.</p>
     */
    String value() default "";

    /**
     * The discriminator column type. Only meaningful on the sealed interface (where it defines the column type).
     * On subtypes and FK fields, this attribute is ignored.
     *
     * <p>Defaults to {@link DiscriminatorType#STRING}.</p>
     */
    DiscriminatorType type() default DiscriminatorType.STRING;

    /**
     * The discriminator column types supported for sealed type hierarchies.
     */
    enum DiscriminatorType {

        /**
         * VARCHAR discriminator column. This is the default. The discriminator value is the simple class name
         * or a custom string specified via {@link Discriminator#value()}.
         */
        STRING,

        /**
         * INTEGER discriminator column. The discriminator value is parsed as an integer from the string
         * specified via {@link Discriminator#value()}.
         */
        INTEGER,

        /**
         * CHAR(1) discriminator column. The discriminator value is the first character of the string
         * specified via {@link Discriminator#value()}.
         */
        CHAR
    }
}
