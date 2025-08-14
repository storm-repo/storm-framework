/*
 * Copyright 2024 - 2025 the original author or authors.
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
import static st.orm.GenerationStrategy.IDENTITY;

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
