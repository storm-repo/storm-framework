package st.orm.kt.template.impl

import kotlinx.coroutines.CoroutineDispatcher
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

/**
 * A [kotlinx.coroutines.CoroutineDispatcher] that uses virtual threads for executing coroutines.
 *
 * This dispatcher is designed to be used in environments that support virtual threads.
 * @since 1.5
 */
class VirtualThreadDispatcher : CoroutineDispatcher(), Closeable {

    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        executor.execute(block)
    }

    override fun close() {
        executor.close()
    }
}