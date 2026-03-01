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
 * Defines how dirty checking is performed for an entity.
 *
 * <p>This enum controls how field changes are detected when dynamic updates are enabled.</p>
 *
 * <ul>
 *   <li>{@link #DEFAULT} – Use the configured dirty check strategy as defined by the
 *       {@code storm.update.dirty_check} property (see {@link StormConfig}).</li>
 *   <li>{@link #INSTANCE} – Fields are considered dirty as soon as their reference changes.
 *       This mode is fast and avoids potentially expensive value comparisons.</li>
 *   <li>{@link #VALUE} – Fields are compared using semantic equality. A field is only considered
 *       dirty when its value has changed.</li>
 * </ul>
 *
 * @since 1.7
 */
public enum DirtyCheck {

    /**
     * Use the globally configured dirty check strategy.
     */
    DEFAULT,

    /**
     * Use identity-based dirty checking.
     *
     * <p>A field is considered dirty when its reference changes.</p>
     */
    INSTANCE,

    /**
     * Use value-based dirty checking.
     *
     * <p>A field is considered dirty only when its value has changed.</p>
     */
    VALUE
}
