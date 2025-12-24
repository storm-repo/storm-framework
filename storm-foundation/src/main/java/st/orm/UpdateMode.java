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

/**
 * Controls how Storm detects changes and generates UPDATE statements for entities.
 *
 * <p>
 * UpdateMode primarily affects <strong>performance</strong>, but it also influences how likely concurrent updates are
 * to overwrite each other. It does <em>not</em> provide a strict correctness guarantee under concurrency. Correctness
 * must be enforced using optimistic locking via a version column.
 * </p>
 *
 * <p>
 * In practice, more precise update modes, especially field-level updates, often reduce the chance of lost updates by
 * limiting which columns are written. This improvement is situational and must not be relied upon as a formal
 * correctness mechanism.
 * </p>
 *
 * <p>
 * The default update mode is {@link #OFF}. More advanced modes must be explicitly enabled either globally or per
 * entity.
 * </p>
 *
 * <h2>General rules</h2>
 * <ul>
 *   <li>Dirty checking applies to all entities read within a transaction context.</li>
 *   <li>Application of dirty checking is limited to updates performed via the entity repository.</li>
 *   <li>Manual or bulk SQL updates bypass dirty checking and may leave cached entities stale.</li>
 *   <li>Field-level dirty checking is a performance optimization that may fall back to full updates
 *       to preserve batching.</li>
 * </ul>
 *
 * @since 1.7
 */
public enum UpdateMode {

    /**
     * Dirty checking is disabled.
     *
     * <p>All mapped columns are always included in UPDATE statements. No comparisons are performed.</p>
     *
     * <p>This mode provides predictable behavior, optimal batching, and minimal runtime overhead.</p>
     */
    OFF,

    /**
     * Entity-level dirty checking.
     *
     * <p>Storm compares the current entity state with the state observed when the entity was read. If no change is
     * detected, the UPDATE is skipped entirely. If a change is detected, all mapped columns are updated.</p>
     *
     * <p>This mode reduces unnecessary UPDATE statements while keeping SQL shape stable. It does not reduce the number
     * of updated columns.</p>
     *
     * <p>By skipping redundant writes, this mode can indirectly reduce contention and lower the probability of
     * conflicting updates.</p>
     *
     * <p>This is the default and recommended mode.</p>
     */
    ENTITY,

    /**
     * Field-level dirty checking.
     *
     * <p>Storm compares individual fields with the values observed when the entity was read and generates UPDATE
     * statements that include only the modified columns.</p>
     *
     * <p>This mode often reduces write amplification and lock scope. By limiting updates to changed columns, it
     * frequently improves behavior under concurrent updates, especially when different transactions modify different
     * fields.</p>
     *
     * <p>These benefits are best-effort and situational. Field-level dirty checking improves concurrency
     * characteristics but does not guarantee correctness. Optimistic locking is still required to reliably detect
     * conflicting updates.</p>
     *
     * <p>To prevent performance degradation, Storm may automatically fall back to full updates when too many distinct
     * update shapes are detected.</p>
     */
    FIELD
}
