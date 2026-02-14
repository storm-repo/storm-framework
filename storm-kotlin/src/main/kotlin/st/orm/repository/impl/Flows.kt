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
package st.orm.repository.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

//
// Replicating `chunked`, `flatMapConcat` and `flattenConcat` functions from Kotlin's experimental Flow API.
// May be removed in the future.
//

/**
 * Splits the given flow into a flow of non-overlapping lists each not exceeding the given [size] but never empty.
 * The last emitted list may have fewer elements than the given size.
 *
 * Example of usage:
 * ```
 * flowOf("a", "b", "c", "d", "e")
 *     .chunked(2) // ["a", "b"], ["c", "d"], ["e"]
 *     .map { it.joinToString(separator = "") }
 *     .collect {
 *         println(it) // Prints "ab", "cd", e"
 *     }
 * ```
 *
 * @throws IllegalArgumentException if [size] is not positive.
 */
fun <T> Flow<T>.chunked(size: Int): Flow<List<T>> {
    require(size >= 1) { "Expected positive chunk size, but got $size" }
    return flow {
        var result: ArrayList<T>? = null // Do not preallocate anything.
        collect { value ->
            // Allocate if needed.
            val acc = result ?: ArrayList<T>(size).also { result = it }
            acc.add(value)
            if (acc.size == size) {
                emit(acc)
                // Cleanup, but don't allocate -- it might've been the case this is the last element.
                result = null
            }
        }
        result?.let { emit(it) }
    }
}

/**
 * Transforms elements emitted by the original flow by applying [transform], that returns another flow,
 * and then concatenating and flattening these flows.
 *
 * This method is a shortcut for `map(transform).flattenConcat()`. See [flattenConcat].
 *
 * Note that even though this operator looks very familiar, we discourage its usage in a regular application-specific flows.
 * Most likely, suspending operation in [map] operator will be sufficient and linear transformations are much easier to reason about.
 */
fun <T, R> Flow<T>.flatMapConcat(transform: suspend (value: T) -> Flow<R>): Flow<R> = map(transform).flattenConcat()

/**
 * Flattens the given flow of flows into a single flow in a sequential manner, without interleaving nested flows.
 *
 * Inner flows are collected by this operator *sequentially*.
 */
fun <T> Flow<Flow<T>>.flattenConcat(): Flow<T> = flow {
    collect { value -> emitAll(value) }
}
