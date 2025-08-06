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
package st.orm.kt.template

import kotlinx.coroutines.CoroutineDispatcher
import st.orm.kt.template.TransactionDispatchers.Virtual
import st.orm.kt.template.impl.VirtualThreadDispatcher
import java.util.concurrent.atomic.AtomicReference

/**
 * A singleton object that provides a virtual thread dispatcher for coroutine execution.
 *
 * This dispatcher is designed to be used in environments that support virtual threads.
 *
 * @since 1.5
 */
object TransactionDispatchers {

    // Backing reference allowing atomic updates; null means "use Virtual"
    private val _override = AtomicReference<CoroutineDispatcher?>(null)

    /**
     * The default dispatcher for coroutines. If not explicitly set, this
     * falls back to [Virtual], which is lazily instantiated.
     */
    var Default: CoroutineDispatcher
        get() = _override.get() ?: Virtual
        set(value) = _override.set(value)

    /**
     * A dispatcher that dispatches Kotlin coroutines onto Java virtual threads.
     * Instantiated lazily upon first use to avoid unnecessary resource allocation.
     */
    val Virtual: CoroutineDispatcher by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        VirtualThreadDispatcher()
    }

    /**
     * Shuts down the virtual thread dispatcher if it has been instantiated.
     * Safe to call even if [Virtual] was never accessed.
     */
    fun shutdown() {
        (Virtual as? VirtualThreadDispatcher)?.close()
    }
}