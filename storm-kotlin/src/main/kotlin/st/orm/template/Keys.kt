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
package st.orm.template

import st.orm.Data
import st.orm.Metamodel

/**
 * Returns a [Metamodel.Key] view of this metamodel. If this metamodel already implements [Metamodel.Key], it is
 * returned as-is; otherwise it is wrapped in a delegate that implements [Metamodel.Key].
 *
 * Usage:
 * ```
 * val key = User_.id.key()
 * ```
 *
 * @see Metamodel.key
 */
fun <T : Data, E> Metamodel<T, E>.key(): Metamodel.Key<T, E> = Metamodel.key(this)
