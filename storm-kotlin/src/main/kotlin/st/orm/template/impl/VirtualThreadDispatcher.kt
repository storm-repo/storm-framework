package st.orm.template.impl

import kotlinx.coroutines.CoroutineDispatcher
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import kotlin.coroutines.CoroutineContext

/**
 * A [kotlinx.coroutines.CoroutineDispatcher] that uses virtual threads for executing coroutines.
 *
 * This dispatcher is designed to be used in environments that support virtual threads.
 *
 * @since 1.5
 */
class VirtualThreadDispatcher(
    threadNamePrefix: String = "virtual"
) : CoroutineDispatcher(), Closeable {

    private val factory: ThreadFactory = Thread.ofVirtual()
        .name(threadNamePrefix, 0)
        .factory()

    private val executor: ExecutorService = Executors.newThreadPerTaskExecutor(factory)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        executor.execute(block)
    }

    override fun close() {
        executor.close()
    }
}