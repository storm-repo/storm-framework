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

import st.orm.Element;
import st.orm.core.template.impl.Elements.Expression;

/**
 * Wrapper element that marks an {@link Expression} as cacheable during template compilation.
 *
 * <p>Expressions are only allowed to appear in a template when they are part of a {@code WHERE} clause. When the
 * shared compilation logic encounters an expression, it is propagated as a {@code Cacheable} element.</p>
 *
 * <p>The wrapped expression is not rendered directly into SQL. It is included as part of the overall compilation key so
 * that templates containing expressions can be cached and compared correctly.</p>
 *
 * @param expression the expression contributing to the compilation key.
 * @since 1.8
 */
public record Cacheable(Expression expression) implements Element {
}