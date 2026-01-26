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
package st.orm.core.template.impl;

import st.orm.core.template.impl.SetProcessor.SetBindHint;
import st.orm.core.template.impl.ValuesProcessor.ValuesBindHint;
import st.orm.core.template.impl.WhereProcessor.WhereBindHint;

/**
 * Marker interface that represents a binding hint used during SQL template binding.
 *
 * <p>A {@code BindHint} allows information discovered during template compilation to be carried over to the binding
 * phase. This avoids having to re-derive the same structural or contextual information while binding parameters.</p>
 *
 * <p>Hints are emitted during compilation and attached to template elements. During binding, they can influence
 * placeholder handling, value ordering, or clause-specific behavior without requiring additional inspection of the
 * model or SQL structure.</p>
 *
 * <p>The concrete hint types are scoped to specific SQL clauses, such as {@code SET}, {@code VALUES}, or {@code WHERE},
 * and are only meaningful within their respective processing phases.</p>
 *
 * <p>The {@link NoBindHint} variant represents the absence of any binding hint and can be used as a neutral
 * default.</p>
 *
 * @since 1.8
 */
public sealed interface BindHint
        permits BindHint.NoBindHint,
                SetBindHint,
                ValuesBindHint,
                WhereBindHint {

    /**
     * Represents the absence of a binding hint.
     *
     * <p>This instance is used when no compilation-time information needs to be propagated to the binding stage. It is
     * implemented as a singleton to avoid unnecessary allocations.</p>
     *
     * @since 1.8
     */
    record NoBindHint() implements BindHint {

        /**
         * Shared singleton instance.
         */
        static final NoBindHint INSTANCE = new NoBindHint();
    }
}