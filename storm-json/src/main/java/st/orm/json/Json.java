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
package st.orm.json;

import st.orm.MetamodelType;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a record component as a json object.
 */
@Target({RECORD_COMPONENT, PARAMETER})
@Retention(RUNTIME)
@MetamodelType(String.class)
public @interface Json {

    /**
     * True if the deserializer should fail if unknown properties are encountered. Default is false.
     */
    boolean failOnUnknown() default false;

    /**
     * True if the deserializer should fail if a required creator property is missing. Default is false.
     */
    boolean failOnMissing() default false;
}
