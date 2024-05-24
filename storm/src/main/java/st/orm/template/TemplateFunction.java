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
package st.orm.template;

import jakarta.annotation.Nonnull;

/**
 * Represents a template function.
 *
 * <p>Template functions are used to implement string interpolation without using Java 21's String Interpolation
 * language feature. Template functions primarily used to support query builders for Kotlin, as Kotlin does not
 * support Java's String Interpolation feature.</p>
 */
@FunctionalInterface
public interface TemplateFunction {

    /**
     * Context object that provides the ability to inject arguments into the string template.
     */
    @FunctionalInterface
    interface Context {

        /**
         * Injects an argument into the underlying string template. The resulting string, the so-called intermediary
         * string, must be placed directly into the string. It will be replaced by the actual value by the time the
         * template is interpolated.
         *
         * @param o the object to inject.
         * @return the intermediary string to be placed into the template.
         */
        String arg(@Nonnull Object o);
    }

    /**
     * Interpolates the template using the given context.
     *
     * @param context the context to use for interpolation.
     * @return the string containing intermediary strings as result of argument injection.
     */
    String interpolate(@Nonnull Context context);
}
