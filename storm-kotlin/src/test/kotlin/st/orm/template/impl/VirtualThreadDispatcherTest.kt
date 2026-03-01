package st.orm.template.impl

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext

class VirtualThreadDispatcherTest {

    @Test
    fun `constructor with default thread name prefix should create dispatcher`() {
        VirtualThreadDispatcher().use { dispatcher ->
            dispatcher.shouldNotBe(null)
            dispatcher.shouldBeInstanceOf<CoroutineDispatcher>()
        }
    }

    @Test
    fun `constructor with custom thread name prefix should create dispatcher`() {
        VirtualThreadDispatcher("custom-prefix").use { dispatcher ->
            dispatcher.shouldNotBe(null)
            dispatcher.shouldBeInstanceOf<CoroutineDispatcher>()
        }
    }

    @Test
    fun `dispatch should execute runnable`() {
        VirtualThreadDispatcher().use { dispatcher ->
            val latch = CountDownLatch(1)
            val executed = AtomicBoolean(false)

            dispatcher.dispatch(
                EmptyCoroutineContext,
                Runnable {
                    executed.set(true)
                    latch.countDown()
                },
            )

            latch.await(5, TimeUnit.SECONDS) shouldBe true
            executed.get() shouldBe true
        }
    }

    @Test
    fun `dispatch should run on virtual thread`() {
        VirtualThreadDispatcher().use { dispatcher ->
            val latch = CountDownLatch(1)
            val isVirtual = AtomicBoolean(false)

            dispatcher.dispatch(
                EmptyCoroutineContext,
                Runnable {
                    isVirtual.set(Thread.currentThread().isVirtual)
                    latch.countDown()
                },
            )

            latch.await(5, TimeUnit.SECONDS) shouldBe true
            isVirtual.get() shouldBe true
        }
    }

    @Test
    fun `dispatch should use thread name prefix`() {
        VirtualThreadDispatcher("test-vt").use { dispatcher ->
            val latch = CountDownLatch(1)
            val threadName = AtomicReference<String>()

            dispatcher.dispatch(
                EmptyCoroutineContext,
                Runnable {
                    threadName.set(Thread.currentThread().name)
                    latch.countDown()
                },
            )

            latch.await(5, TimeUnit.SECONDS) shouldBe true
            threadName.get().startsWith("test-vt") shouldBe true
        }
    }

    @Test
    fun `dispatch should handle multiple concurrent tasks`() {
        VirtualThreadDispatcher().use { dispatcher ->
            val count = 10
            val latch = CountDownLatch(count)
            val results = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

            for (i in 0 until count) {
                dispatcher.dispatch(
                    EmptyCoroutineContext,
                    Runnable {
                        results[i] = true
                        latch.countDown()
                    },
                )
            }

            latch.await(10, TimeUnit.SECONDS) shouldBe true
            results.size shouldBe count
        }
    }

    @Test
    fun `dispatcher should work as coroutine context`() = runBlocking {
        VirtualThreadDispatcher("coroutine-vt").use { dispatcher ->
            val isVirtual = withContext(dispatcher) {
                Thread.currentThread().isVirtual
            }
            isVirtual shouldBe true
        }
    }

    @Test
    fun `close should not throw`() {
        val dispatcher = VirtualThreadDispatcher()
        dispatcher.close()
    }

    @Test
    fun `close should be idempotent`() {
        val dispatcher = VirtualThreadDispatcher()
        dispatcher.close()
        dispatcher.close()
    }
}
