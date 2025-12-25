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

/**
 * Configures how Storm detects changes and generates UPDATE statements for a specific entity.
 *
 * <p>{@code @DynamicUpdate} controls how Storm interprets changes observed within a transaction and how
 * UPDATE statements are constructed for the annotated entity.</p>
 *
 * <p>The configuration has two independent concerns:</p>
 * <ul>
 *   <li>The {@link UpdateMode} determines <em>when</em> an UPDATE is issued and whether it applies to the
 *       whole entity or to individual fields.</li>
 *   <li>The dirty checking strategy determines <em>how</em> Storm decides that a change has occurred.</li>
 * </ul>
 *
 * <p>{@code @DynamicUpdate} primarily affects <strong>performance</strong> and write behavior. It does not
 * provide a strict correctness guarantee. Correctness under concurrent updates must be enforced using
 * optimistic locking, for example via a version column.</p>
 *
 * <p>In practice, more precise update modes, especially field-level updates, can reduce write amplification
 * and limit which columns are written. These improvements are situational and must not be relied upon as a
 * formal correctness mechanism.</p>
 *
 * <p>If not specified, the global default {@link UpdateMode} applies. The global default can be configured
 * using the system property {@code storm.update.defaultMode}. If the property is not set, the default
 * update mode is {@link UpdateMode#ENTITY}.</p>
 *
 * <p>The dirty checking strategy can be configured separately. By default, Storm uses instance-based dirty
 * checking, where a field is considered dirty as soon as its reference changes. Value-based dirty checking
 * can be enabled globally using the system property {@code storm.update.dirtyCheck}, or per entity via
 * this annotation.</p>
 *
 * <p>Dirty checking operates on top-level entity fields only. Nested record components are treated as a
 * single unit and are not inspected field-by-field.</p>
 *
 * <h2>General rules</h2>
 * <ul>
 *   <li>Dirty checking applies to all entities read within a transaction context.</li>
 *   <li>Dirty checking is applied only to updates performed via the entity repository.</li>
 *   <li>Manual or bulk SQL updates bypass dirty checking and may leave cached entities stale.</li>
 *   <li>
 *     Field-level updates are a performance optimization and may fall back to full updates to preserve
 *     batching efficiency.
 *   </li>
 * </ul>
 *
 * @since 1.7
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface DynamicUpdate {

    /**
     * Defines how Storm should detect changes and generate UPDATE statements for the annotated entity.
     *
     * <p>If set to {@link UpdateMode#OFF}, all mapped columns are always updated.
     * If set to {@link UpdateMode#ENTITY}, Storm compares the entity as a whole
     * and skips the UPDATE if no change is detected.
     * If set to {@link UpdateMode#FIELD}, Storm compares individual fields and
     * updates only the modified columns.</p>
     */
    UpdateMode value();

    /**
     * Defines how dirty checking is performed for this entity.
     *
     * <p>This setting controls whether Storm determines changes based on instance identity or semantic value
     * comparison.</p>
     *
     * <p>If set to {@link DirtyCheck#DEFAULT}, the globally configured dirty check strategy applies. The global default
     * can be configured using the system property {@code storm.update.dirtyCheck}.</p>
     *
     * <p>This setting only affects dirty detection. It does not change the selected {@link UpdateMode}.</p>
     */
    DirtyCheck dirtyCheck() default DirtyCheck.DEFAULT;
}
