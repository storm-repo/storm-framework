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

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Specifies the inheritance strategy for a sealed entity hierarchy.
 *
 * <p>Place this annotation on a sealed interface that extends {@link Entity} to indicate that the hierarchy uses
 * Joined Table inheritance. Without this annotation, sealed entity hierarchies default to Single-Table inheritance.</p>
 *
 * <pre>{@code
 * @Polymorphic(JOINED)
 * sealed interface Pet extends Entity<Integer> permits Cat, Dog {}
 *
 * record Cat(@PK Integer id, String name, boolean indoor) implements Pet {}
 * record Dog(@PK Integer id, String name, int weight) implements Pet {}
 * }</pre>
 *
 * @see Discriminator
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface Polymorphic {

    /**
     * The inheritance strategy.
     */
    Strategy value();

    /**
     * The inheritance strategies for sealed entity hierarchies.
     */
    enum Strategy {

        /**
         * All subtypes share a single database table with a discriminator column. Subtype-specific columns are NULL
         * for rows of other subtypes.
         */
        SINGLE_TABLE,

        /**
         * A base table holds shared fields and an optional discriminator column. Each subtype has its own extension table with
         * subtype-specific fields, linked by the primary key.
         */
        JOINED
    }
}
