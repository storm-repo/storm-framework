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

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static st.orm.UpdateMode.OFF;

/**
 * Configures how Storm detects changes and generates UPDATE statements for a specific entity.
 *
 * <p>{@code @DynamicUpdate} controls whether and how Storm compares entity state observed at read time with the
 * current state when an update is applied. It primarily affects <strong>performance</strong>, but it can
 * also influence how concurrent updates interact.</p>
 *
 * <p>{@code @DynamicUpdate} does <em>not</em> provide a strict correctness guarantee. Correctness under
 * concurrent updates must be enforced using optimistic locking, for example via a version column.</p>
 *
 * <p>In practice, more precise update modes, especially field-level updates, often reduce the chance of lost
 * updates by limiting which columns are written. This improvement is situational and must not be relied
 * upon as a formal correctness mechanism.</p>
 *
 * <p>If not specified, the global default {@link UpdateMode} applies. The global default can be configured
 * using the system property {@code storm.update.defaultMode}. If the property is not set, the default update mode
 * is {@link UpdateMode#ENTITY}.</p>
 *
 * <h2>General rules</h2>
 * <ul>
 *   <li>Dirty checking applies to all entities read within a transaction context.</li>
 *   <li>Application of dirty checking is limited to updates performed via the entity repository.</li>
 *   <li>Manual or bulk SQL updates bypass dirty checking and may leave cached entities stale.</li>
 *   <li>
 *     Field-level dirty checking is a performance optimization that may fall back to full updates to
 *     preserve batching.
 *   </li>
 * </ul>
 *
 * @since 1.7
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface DynamicUpdate {

    /**
     * Defines how Storm should detect changes and generate UPDATE statements
     * for the annotated entity.
     *
     * <p>
     * If set to {@link UpdateMode#OFF}, all mapped columns are always updated.
     * If set to {@link UpdateMode#ENTITY}, Storm compares the entity as a whole
     * and skips the UPDATE if no change is detected.
     * If set to {@link UpdateMode#FIELD}, Storm compares individual fields and
     * updates only the modified columns.
     * </p>
     */
    UpdateMode value() default OFF;
}
