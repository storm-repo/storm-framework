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

/**
 * Marks a field as a foreign key. You can specify the foreign key’s database column name using either:
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
 * Each sets the foreign key’s database column name to {@code "user_id"}.
 *
 * <p>If no value is specified (i.e., both {@code value()} and {@code name()} are set to {@code ""}),
 * an automatic column name resolution strategy will be applied.
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