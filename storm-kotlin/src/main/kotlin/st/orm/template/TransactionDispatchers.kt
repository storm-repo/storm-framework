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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import st.orm.template.TransactionDispatchers.resolveDefault
import st.orm.template.impl.VirtualThreadDispatcher
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference

/**
 * Selects a default dispatcher that prefers Virtual Threads on Java 24+,
 * and falls back to Dispatchers.IO on older JVMs (to avoid VT pinning on Java 21–23).
 *
 * You can override with:
 *   -Dstorm.virtual_threads.enabled=true|false
 *
 * @since 1.5
 */
object TransactionDispatchers {

    // Backing reference allowing atomic updates.
    private val overrideRef = AtomicReference<CoroutineDispatcher?>(null)

    /**
     * Public default dispatcher. Uses override if set; otherwise resolves lazily via [resolveDefault].
     */
    var Default: CoroutineDispatcher
        get() = overrideRef.get() ?: resolveDefault()
        set(value) { overrideRef.set(value) }

    /**
     * Virtual-thread dispatcher (created lazily).
     */
    val Virtual: CoroutineDispatcher by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        VirtualThreadDispatcher(threadNamePrefix = "tx")
    }

    /**
     * Cleanly shuts down the VT dispatcher if it was instantiated.
     */
    fun shutdown() {
        (Virtual as? Closeable)?.close()
    }

    private fun resolveDefault(): CoroutineDispatcher {
        return if (isVirtualThreadsEnabled()) Virtual else Dispatchers.IO
    }

    private fun isVirtualThreadsEnabled(): Boolean {
        // Explicit property takes precedence, otherwise enable only if Java >= 24 to prevent pinning issues with
        // suspendTransaction.
        val propEnabled = System.getProperty("storm.virtual_threads.enabled", "false")
            .equals("true", ignoreCase = true)
        return propEnabled || isJava24OrNewer()
    }

    private fun isJava24OrNewer(): Boolean {
        return try {
            Runtime.version().feature() >= 24
        } catch (_: Throwable) {
            val spec = System.getProperty("java.specification.version") ?: return false
            spec.substringBefore('.').toIntOrNull()?.let { it >= 24 } ?: false
        }
    }
}