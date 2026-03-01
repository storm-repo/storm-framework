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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation for capturing and logging SQL statements.
 *
 * <p>Applying this annotation at either the method or type level will intercept execution via the repository proxy,
 * capturing and logging SQL statements.</p>
 *
 * <p>When applied at the type level, the annotation affects all methods within the repository. Method-level annotations
 * override type-level annotations.</p>
 *
 * @since 1.9
 */
@Retention(RUNTIME)
@Target({METHOD, TYPE})
public @interface SqlLog {

    /**
     * If true, SQL parameters are inlined into the logged SQL statements for better readability.
     */
    boolean inlineParameters() default false;

    /**
     * Defines the logging level used when outputting captured SQL. Defaults to {@code INFO}.
     */
    System.Logger.Level level() default System.Logger.Level.INFO;

    /**
     * Optional logger name. If empty, the repository interface name is used.
     */
    String name() default "";
}
