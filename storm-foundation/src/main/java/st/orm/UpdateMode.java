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

/**
 * Controls how changes are detected and how UPDATE statements are generated for entities.
 *
 * <p>UpdateMode primarily affects <strong>performance</strong>, but it also influences how likely concurrent updates
 * are to overwrite each other. It does not provide a strict correctness guarantee under concurrency. Correctness must
 * be enforced using optimistic locking via a version column.</p>
 *
 * <p>In practice, more precise update modes, especially field-level updates, often reduce the chance of lost updates by
 * limiting which columns are written. This improvement is situational and must not be relied upon as a formal
 * correctness mechanism.</p>
 *
 * <p>The default update mode is {@link #ENTITY}. A different default can be configured via the
 * {@code storm.update.default_mode} property (see {@link StormConfig}). The update mode can also be configured per
 * entity using {@link DynamicUpdate}.</p>
 *
 * <h2>General rules</h2>
 * <ul>
 *   <li>Dirty checking applies to entities read within a transaction context.</li>
 *   <li>Application of dirty checking is limited to updates performed via the entity repository.</li>
 *   <li>Manual or bulk SQL updates bypass dirty checking and may leave cached entities stale.</li>
 *   <li>Field-level updates are a performance optimization and may fall back to full-row updates to preserve batching
 *       efficiency.</li>
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
     * Full-row updates with column-level dirty detection.
     *
     * <p>Dirty checking is evaluated per updatable column against the state observed when the entity was read. If no
     * column changes are detected, the UPDATE is skipped entirely. If any column is considered dirty, a full-row UPDATE
     * is issued and all mapped columns are included.</p>
     *
     * <p>This mode reduces unnecessary UPDATE statements while keeping SQL shape stable. It does not reduce the number
     * of updated columns when an UPDATE is issued.</p>
     *
     * <p>By skipping redundant writes, contention may be reduced and the probability of conflicting updates may be
     * lowered. This effect is best-effort and must not be relied upon as a correctness mechanism.</p>
     *
     * <p>This is the default and recommended mode.</p>
     */
    ENTITY,

    /**
     * Field-level updates with column-level dirty detection.
     *
     * <p>Dirty checking is evaluated per updatable column against the state observed when the entity was read. UPDATE
     * statements include only the columns considered dirty.</p>
     *
     * <p>This mode often reduces write amplification and lock scope. By limiting updates to changed columns, behavior
     * under concurrent updates can improve, especially when different transactions modify different fields.</p>
     *
     * <p>These benefits are best-effort and situational. Field-level updates improve concurrency characteristics but do
     * not guarantee correctness. Optimistic locking is still required to reliably detect conflicting updates.</p>
     *
     * <p>To prevent performance degradation, a fallback to full-row updates may be used when too many distinct update
     * statement shapes would otherwise be generated.</p>
     */
    FIELD
}