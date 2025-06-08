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
package st.orm.kotlin.repository

import st.orm.kotlin.template.KQueryBuilder
import st.orm.kotlin.template.KQueryBuilder.KPredicateBuilder
import st.orm.kotlin.template.KQueryBuilder.KWhereBuilder


/**
 * Adds a conditional predicate block to this query.
 *
 * You can use the provided DSL inside the [block] to build complex conditions via a [KWhereBuilder], returning a
 * [KPredicateBuilder].
 *
 * @param block a receiver lambda on [KWhereBuilder] to configure one or more predicates.
 * @return this [KQueryBuilder] with the new WHERE clause applied.
 */
fun <T, R, ID> KQueryBuilder<T, R, ID>.where(
    block: KWhereBuilder<T, R, ID>.() -> KPredicateBuilder<*, *, *>
): KQueryBuilder<T, R, ID> where T : Record =
    where{ it.block() }