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
package st.orm.ktor.test

import st.orm.template.ORMTemplate
import st.orm.test.SqlCapture
import javax.sql.DataSource

/**
 * Scope object providing access to Storm infrastructure within a [testStormApplication] block.
 *
 * @since 1.11
 */
class StormTestScope internal constructor(

    /**
     * The H2 in-memory [DataSource] created for this test. Pass this to the Storm plugin configuration via
     * `install(Storm) { dataSource = stormDataSource }`.
     */
    val stormDataSource: DataSource,

    /**
     * A pre-configured [ORMTemplate] backed by [stormDataSource].
     */
    val stormOrm: ORMTemplate,

    /**
     * A fresh [SqlCapture] for verifying the SQL the application executes. It is installed around every call the
     * application handles, so a request's statements are captured wherever its handler runs; statements accumulate
     * across requests, and [SqlCapture.clear] resets between them. Statements the test body executes directly
     * against [stormOrm] stay outside the capture unless wrapped in the suspending `recording` extension.
     */
    val stormSqlCapture: SqlCapture,
)
