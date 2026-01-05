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

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Declares how a {@link st.orm.Converter Converter} should be applied.
 *
 * <p>This annotation supports three use cases:</p>
 *
 * <ul>
 *     <li><strong>Explicit converter</strong>
 *         Specify a concrete converter class through {@link #converter()} when a
 *         component type has multiple applicable converters or when auto-apply
 *         is not desired.</li>
 *     <li><strong>Disable conversion</strong>
 *         Set {@link #disableConversion()} to {@code true} to prevent any
 *         auto-apply or inherited converter from being used.</li>
 *     <li><strong>Fallback to auto-apply</strong>
 *         When the annotation is not present, Storm will attempt to resolve a
 *         single matching converter where {@code autoApply() == true}.</li>
 * </ul>
 *
 * <p>If both {@code converter()} and {@code disableConversion()} are set in a
 * conflicting way, conversion resolution will fail with a clear error.</p>
 *
 * @since 1.7
 */
@Target({RECORD_COMPONENT, PARAMETER})
@Retention(RUNTIME)
public @interface Convert {

    /**
     * Specifies the converter to be applied. A value for this element must be specified if multiple converters would
     * otherwise apply.
     */
    Class<?> converter() default Void.class;

    /**
     * Used to disable an auto-apply or inherited converter. If disableConversion is true, the {@code converter} element
     * should not be specified.
     */
    boolean disableConversion() default false;
}
