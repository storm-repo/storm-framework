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

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Specifies the schema name for the table or view.
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface DbTable {

    /**
     * The database table name.
     * Acts as an alias for {@link #name()}.
     */
    String value() default "";

    /**
     * The database table name.
     * Acts as an alias for {@link #value()}.
     */
    String name() default "";

    /**
     * The database schema name.
     */
    String schema() default "";

    /**
     * True to force escaping the schema and table name.
     *
     *<p><strong>Note:</strong> The schema and table names are automatically escaped if they contain special characters
     * or are a reserved keyword.</p>
     */
    boolean escape() default false;
}

