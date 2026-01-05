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

import jakarta.annotation.Nonnull;

/**
 * Identity comparison on an extracted field value.
 *
 * <p>Implementations must not perform boxing or coercion for primitive-typed fields. For primitive-typed fields,
 * identity comparison is not defined and must be rejected at construction time.</p>
 *
 * @since 1.7
 */
@FunctionalInterface
public interface Identical<T> {

    /**
     * Returns {@code true} if and only if the value extracted from {@code a} and {@code b} is the same object instance.
     */
    boolean isIdentical(@Nonnull T a, @Nonnull T b) throws Throwable;
}