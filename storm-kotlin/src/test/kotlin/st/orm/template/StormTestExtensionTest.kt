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

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import st.orm.template.model.City
import st.orm.test.CapturedSql.Operation
import st.orm.test.SqlCapture
import st.orm.test.StormTest
import javax.sql.DataSource

@StormTest(scripts = ["/data.sql"])
class StormTestExtensionTest {

    @Test
    fun `data source should be injected`(dataSource: DataSource) {
        dataSource shouldNotBe null
    }

    @Test
    fun `orm template should be resolved`(orm: ORMTemplate) {
        orm shouldNotBe null
    }

    @Test
    fun `orm template should query entities`(orm: ORMTemplate) {
        val cities = orm.entity(City::class).findAll()
        cities.size shouldBe 6
    }

    @Test
    fun `statement capture should record selects`(orm: ORMTemplate, capture: SqlCapture) {
        capture.run { orm.entity(City::class).findAll() }
        capture.count(Operation.SELECT) shouldBe 1
    }

    @Test
    fun `statement capture should record inserts`(orm: ORMTemplate, capture: SqlCapture) {
        capture.run { orm.entity(City::class).insert(City(name = "TestCity")) }
        capture.count(Operation.INSERT) shouldBe 1
    }

    @Test
    fun `statement capture should accumulate and clear`(orm: ORMTemplate, capture: SqlCapture) {
        capture.run { orm.entity(City::class).findAll() }
        capture.run { orm.entity(City::class).findAll() }
        capture.count(Operation.SELECT) shouldBe 2
        capture.clear()
        capture.count() shouldBe 0
    }
}
