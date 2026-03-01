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

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static st.orm.EnumType.NAME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Specifies how an enum should be mapped when reading or writing to a column.
 *
 * <p>By default, enums are mapped using their {@link Enum#name()} value. Use {@code @DbEnum(EnumType.ORDINAL)}
 * to explicitly indicate that the enum's ordinal value should be used instead.</p>
 */
@Target({RECORD_COMPONENT, PARAMETER})
@Retention(RUNTIME)
public @interface DbEnum {

    /**
     * The enum mapping strategy to apply.
     *
     * @return the enum type strategy
     */
    EnumType value() default NAME;
}
