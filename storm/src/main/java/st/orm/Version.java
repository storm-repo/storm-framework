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
 * Marks a record component as a version field to detect optimistic locking conflicts.
 *
 * <p>
 * The following numerical types are supported:
 * <ul>
 *     <li>{@code int}</li>
 *     <li>{@code long}</li>
 *     <li>{@code java.lang.Integer}</li>
 *     <li>{@code java.lang.Long}</li>
 *     <li>{@code java.math.BigInteger}</li>
 * </ul>
 * The following types are supported for timestamp fields:
 * <ul>
 *     <li>{@code java.time.Instant}</li>
 *     <li>{@code java.util.Date}</li>
 *     <li>{@code java.util.Calendar}</li>
 *     <li>{@code java.sql.Timestamp}</li>
 * </ul>
 * </p>
 */
@Target({RECORD_COMPONENT, PARAMETER})
@Retention(RUNTIME)
public @interface Version {
}
