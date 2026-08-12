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
package st.orm.test.kotlin

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import st.orm.template.ORMTemplate
import st.orm.test.CapturedSql.Operation
import st.orm.test.SqlCapture
import st.orm.test.StormTest
import st.orm.test.kotlin.model.City

/**
 * Verifies that a capture follows the coroutine rather than the thread: it keeps recording across a suspension
 * that resumes on another thread, which is the case a thread-bound capture loses.
 */
@StormTest(scripts = ["/data.sql"])
internal class SqlCaptureRecordTest {

    @Test
    fun `the suspending capture returns the block's result`(orm: ORMTemplate, capture: SqlCapture): Unit = runBlocking {
        val cities = capture.recording {
            orm.entity(City::class).findAll()
        }
        cities.shouldNotBeEmpty()
        capture.count(Operation.SELECT) shouldBe 1
    }

    @Test
    fun `a capture survives a suspension that resumes on another thread`(orm: ORMTemplate, capture: SqlCapture): Unit = runBlocking {
        val threads = mutableSetOf<String>()
        capture.recording {
            threads += Thread.currentThread().name
            orm.entity(City::class).findAll()
            // Hop dispatchers: a thread-bound capture stops recording from here on.
            withContext(Dispatchers.IO) {
                threads += Thread.currentThread().name
                orm.entity(City::class).findAll()
            }
            yield()
            threads += Thread.currentThread().name
            orm.entity(City::class).findAll()
        }
        threads.size shouldBeGreaterThan 1
        capture.count(Operation.SELECT) shouldBe 3
    }

    @Test
    fun `a coroutine launched inside the block records into the capture`(orm: ORMTemplate, capture: SqlCapture): Unit = runBlocking {
        capture.recording {
            coroutineScope {
                async(Dispatchers.Default) {
                    orm.entity(City::class).findAll()
                }.await()
            }
        }
        capture.count(Operation.SELECT) shouldBe 1
    }

    @Test
    fun `statements executed after the block are not captured`(orm: ORMTemplate, capture: SqlCapture): Unit = runBlocking {
        capture.recording {
            orm.entity(City::class).findAll()
        }
        orm.entity(City::class).findAll()
        capture.count(Operation.SELECT) shouldBe 1
    }

    @Test
    fun `a blocking block resolves to the member and still records`(orm: ORMTemplate, capture: SqlCapture) {
        capture.record { orm.entity(City::class).findAll() }
        capture.count(Operation.SELECT) shouldBe 1
    }
}
