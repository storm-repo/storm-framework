/*
 * Copyright 2024 the original author or authors.
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
 * Allows the persistence properties of a record component to be configured.
 *
 * <p>This annotation is only relevant in case the record is going to be persisted using an INSERT or UPDATE statement.</p>
 */
@Target({RECORD_COMPONENT, PARAMETER})
@Retention(RUNTIME)
public @interface Persist {

    /**
     * Whether the component will be inserted when the record is persisted.
     *
     * @return true if the component will be inserted, false otherwise.
     */
    boolean insertable() default true;

    /**
     * Whether the component will be updated when the record is persisted.
     *
     * @return true if the component will be updated, false otherwise.
     */
    boolean updatable() default true;
}
