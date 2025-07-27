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

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Specifies the name for the column, table or view.
 */
@Target({RECORD_COMPONENT, PARAMETER})
@Retention(RUNTIME)
@Repeatable(DbColumns.class)
public @interface DbColumn {

    /**
     * The database column name.
     * Acts as an alias for {@link #name()}.
     */
    String value() default "";

    /**
     * The database column name.
     * Acts as an alias for {@link #value()}.
     */
    String name() default "";

    /**
     * True to force escaping the column name.
     *
     * <p><strong>Note:</strong> The column name is automatically escaped if it contains special characters or is a
     * reserved keyword.</p>
     */
    boolean escape() default false;
}

