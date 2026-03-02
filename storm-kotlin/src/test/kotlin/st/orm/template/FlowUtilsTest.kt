package st.orm.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import st.orm.repository.impl.chunked
import st.orm.repository.impl.flatMapConcat
import st.orm.repository.impl.flattenConcat

class FlowUtilsTest {

    // ======================================================================
    // chunked() tests
    // ======================================================================

    @Test
    fun `chunked should split flow into chunks of specified size`(): Unit = runBlocking {
        val result = flowOf(1, 2, 3, 4, 5).chunked(2).toList()
        result shouldHaveSize 3
        result[0] shouldBe listOf(1, 2)
        result[1] shouldBe listOf(3, 4)
        result[2] shouldBe listOf(5)
    }

    @Test
    fun `chunked with size 1 should emit single-element lists`(): Unit = runBlocking {
        val result = flowOf("a", "b", "c").chunked(1).toList()
        result shouldHaveSize 3
        result[0] shouldBe listOf("a")
        result[1] shouldBe listOf("b")
        result[2] shouldBe listOf("c")
    }

    @Test
    fun `chunked with size larger than flow should emit single chunk`(): Unit = runBlocking {
        val result = flowOf(1, 2, 3).chunked(10).toList()
        result shouldHaveSize 1
        result[0] shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `chunked with exact multiple should emit full chunks`(): Unit = runBlocking {
        val result = flowOf(1, 2, 3, 4, 5, 6).chunked(3).toList()
        result shouldHaveSize 2
        result[0] shouldBe listOf(1, 2, 3)
        result[1] shouldBe listOf(4, 5, 6)
    }

    @Test
    fun `chunked with empty flow should emit nothing`(): Unit = runBlocking {
        val result = emptyFlow<Int>().chunked(5).toList()
        result shouldHaveSize 0
    }

    @Test
    fun `chunked with size zero should throw IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            runBlocking {
                flowOf(1).chunked(0).toList()
            }
        }
    }

    @Test
    fun `chunked with negative size should throw IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            runBlocking {
                flowOf(1).chunked(-1).toList()
            }
        }
    }

    @Test
    fun `chunked should handle single element flow`(): Unit = runBlocking {
        val result = flowOf(42).chunked(5).toList()
        result shouldHaveSize 1
        result[0] shouldBe listOf(42)
    }

    @Test
    fun `chunked with size 2 and 7 elements should produce 4 chunks`(): Unit = runBlocking {
        val result = flowOf(1, 2, 3, 4, 5, 6, 7).chunked(2).toList()
        result shouldHaveSize 4
        result[0] shouldBe listOf(1, 2)
        result[1] shouldBe listOf(3, 4)
        result[2] shouldBe listOf(5, 6)
        result[3] shouldBe listOf(7)
    }

    // ======================================================================
    // flatMapConcat() tests
    // ======================================================================

    @Test
    fun `flatMapConcat should transform and flatten flows`(): Unit = runBlocking {
        val result = flowOf(1, 2, 3).flatMapConcat { value ->
            flowOf(value, value * 10)
        }.toList()
        result shouldBe listOf(1, 10, 2, 20, 3, 30)
    }

    @Test
    fun `flatMapConcat with empty inner flow should skip elements`(): Unit = runBlocking {
        val result = flowOf(1, 2, 3).flatMapConcat { value ->
            if (value == 2) emptyFlow() else flowOf(value)
        }.toList()
        result shouldBe listOf(1, 3)
    }

    @Test
    fun `flatMapConcat with empty outer flow should return empty`(): Unit = runBlocking {
        val result = emptyFlow<Int>().flatMapConcat { value ->
            flowOf(value)
        }.toList()
        result shouldHaveSize 0
    }

    @Test
    fun `flatMapConcat should process inner flows sequentially`(): Unit = runBlocking {
        val result = flowOf("a", "b").flatMapConcat { value ->
            flowOf("${value}1", "${value}2", "${value}3")
        }.toList()
        result shouldBe listOf("a1", "a2", "a3", "b1", "b2", "b3")
    }

    @Test
    fun `flatMapConcat with single element should return inner flow`(): Unit = runBlocking {
        val result = flowOf(5).flatMapConcat { value ->
            flowOf(value * 2, value * 3)
        }.toList()
        result shouldBe listOf(10, 15)
    }

    @Test
    fun `flatMapConcat with all empty inner flows should return empty`(): Unit = runBlocking {
        val result = flowOf(1, 2, 3).flatMapConcat<Int, Int> {
            emptyFlow()
        }.toList()
        result shouldHaveSize 0
    }

    // ======================================================================
    // flattenConcat() tests
    // ======================================================================

    @Test
    fun `flattenConcat should flatten flow of flows`(): Unit = runBlocking {
        val flowOfFlows: Flow<Flow<Int>> = flowOf(
            flowOf(1, 2),
            flowOf(3, 4),
            flowOf(5),
        )
        val result = flowOfFlows.flattenConcat().toList()
        result shouldBe listOf(1, 2, 3, 4, 5)
    }

    @Test
    fun `flattenConcat with empty inner flows should skip them`(): Unit = runBlocking {
        val flowOfFlows: Flow<Flow<Int>> = flowOf(
            emptyFlow(),
            flowOf(1, 2),
            emptyFlow(),
            flowOf(3),
        )
        val result = flowOfFlows.flattenConcat().toList()
        result shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `flattenConcat with empty outer flow should return empty`(): Unit = runBlocking {
        val flowOfFlows: Flow<Flow<Int>> = emptyFlow()
        val result = flowOfFlows.flattenConcat().toList()
        result shouldHaveSize 0
    }

    @Test
    fun `flattenConcat with single inner flow should return its elements`(): Unit = runBlocking {
        val flowOfFlows: Flow<Flow<String>> = flowOf(
            flowOf("hello", "world"),
        )
        val result = flowOfFlows.flattenConcat().toList()
        result shouldBe listOf("hello", "world")
    }

    @Test
    fun `flattenConcat should collect inner flows sequentially`(): Unit = runBlocking {
        val flowOfFlows: Flow<Flow<Int>> = flowOf(
            flowOf(1, 2, 3),
            flowOf(4, 5, 6),
            flowOf(7, 8, 9),
        )
        val result = flowOfFlows.flattenConcat().toList()
        // Sequential collection means order is preserved.
        result shouldBe listOf(1, 2, 3, 4, 5, 6, 7, 8, 9)
    }

    @Test
    fun `flattenConcat with all empty inner flows should return empty`(): Unit = runBlocking {
        val flowOfFlows: Flow<Flow<Int>> = flowOf(
            emptyFlow(),
            emptyFlow(),
            emptyFlow(),
        )
        val result = flowOfFlows.flattenConcat().toList()
        result shouldHaveSize 0
    }

    // ======================================================================
    // Combined chunked + flatMapConcat tests
    // ======================================================================

    @Test
    fun `chunked then flatMapConcat should allow batch processing`(): Unit = runBlocking {
        // Simulate batch processing: chunk items, process each batch, flatten results.
        val result = flowOf(1, 2, 3, 4, 5)
            .chunked(2)
            .flatMapConcat { chunk ->
                flowOf(chunk.sum())
            }
            .toList()
        // Chunks: [1,2]=3, [3,4]=7, [5]=5
        result shouldBe listOf(3, 7, 5)
    }

    @Test
    fun `chunked then flattenConcat should re-flatten batches`(): Unit = runBlocking {
        val result = flowOf(1, 2, 3, 4, 5)
            .chunked(2)
            .flatMapConcat { chunk ->
                // Double each element in the chunk.
                flowOf(*chunk.map { it * 2 }.toTypedArray())
            }
            .toList()
        result shouldBe listOf(2, 4, 6, 8, 10)
    }
}
