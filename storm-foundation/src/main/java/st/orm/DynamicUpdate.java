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
 * Configures how changes are detected and how UPDATE statements are generated for a specific entity.
 *
 * <p>{@code @DynamicUpdate} controls how changes observed within a transaction are interpreted and how UPDATE
 * statements are constructed for the annotated entity.</p>
 *
 * <p>The configuration has two independent concerns:</p>
 * <ul>
 *   <li>The {@link UpdateMode} determines when an UPDATE is issued and whether it updates the whole entity or only
 *       individual columns.</li>
 *   <li>The dirty checking strategy determines how a change is detected for a column.</li>
 * </ul>
 *
 * <p>{@code @DynamicUpdate} primarily affects performance and write behavior. It does not provide a strict correctness
 * guarantee. Correctness under concurrent updates must be enforced using optimistic locking, for example via a version
 * column.</p>
 *
 * <p>If not specified, the default {@link UpdateMode} applies. The default can be configured via the
 * {@code storm.update.default_mode} property (see {@link StormConfig}). If the property is not set, the default
 * update mode is {@link UpdateMode#ENTITY}.</p>
 *
 * <p>The dirty checking strategy can be configured separately. By default, dirty checking is instance-based, meaning
 * that reference changes are detected using instance identity rather than semantic value comparison.</p>
 *
 * <p>Value-based dirty checking can be enabled via the {@code storm.update.dirty_check} property
 * (see {@link StormConfig}), or per entity via this annotation.</p>
 *
 * <p>For example, replacing a referenced entity with a different instance that has the same identifier is considered a
 * change when instance-based dirty checking is used.</p>
 *
 * <p>Dirty checking is evaluated for the entity's updatable columns. Fields that are not updatable are ignored.</p>
 *
 * <p>When available, generated metamodel implementations (Java annotation processor / Kotlin KSP) are used to perform
 * type-specific comparisons that avoid reflective access and prevent boxing for primitive fields. If no generated
 * metamodel is available, reflective accessors are used.</p>
 *
 * <h2>General rules</h2>
 * <ul>
 *   <li>Dirty checking applies to entities read within a transaction context.</li>
 *   <li>Dirty checking is applied only to updates performed via the entity repository.</li>
 *   <li>Manual or bulk SQL updates bypass dirty checking and may leave cached entities stale.</li>
 *   <li>Field-level updates are a performance optimization and may fall back to full-row updates to preserve batching
 *       efficiency. This does not affect dirty detection.</li>
 *   <li>Unless configured otherwise, entity observation is automatically disabled for {@code READ_UNCOMMITTED}
 *       transactions. At this isolation level, the application expects to see uncommitted changes from other
 *       transactions. Caching observed state would mask these changes, contradicting the requested isolation
 *       semantics. When observation is disabled, all entities are treated as dirty, resulting in full-row
 *       updates.</li>
 * </ul>
 *
 * @since 1.7
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface DynamicUpdate {

    /**
     * Defines how changes are detected and how UPDATE statements are generated for the annotated entity.
     *
     * <p>If set to {@link UpdateMode#OFF}, all mapped columns are always updated.</p>
     *
     * <p>If set to {@link UpdateMode#ENTITY}, dirty checking is evaluated per updatable column. A full-row UPDATE is
     * issued if any column is considered dirty. If no column changes are detected, the UPDATE is skipped.</p>
     *
     * <p>If set to {@link UpdateMode#FIELD}, dirty checking is evaluated per updatable column and only the columns
     * considered dirty are included in the UPDATE.</p>
     */
    UpdateMode value();

    /**
     * Defines how dirty checking is performed for this entity.
     *
     * <p>This setting controls whether changes are detected based on instance identity or semantic value comparison.</p>
     *
     * <p>If set to {@link DirtyCheck#DEFAULT}, the configured dirty check strategy applies. The default
     * can be configured via the {@code storm.update.dirty_check} property (see {@link StormConfig}).</p>
     *
     * <p>This setting only affects dirty detection. It does not change the selected {@link UpdateMode}.</p>
     */
    DirtyCheck dirtyCheck() default DirtyCheck.DEFAULT;
}