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
package st.orm.template.impl

import st.orm.Data
import st.orm.template.PreparedQuery
import java.util.stream.Stream
import kotlin.reflect.KClass

class PreparedQueryImpl(private val core: st.orm.core.template.PreparedQuery) : QueryImpl(core), PreparedQuery {
    override fun addBatch(record: Data) {
        core.addBatch(record)
    }

    override fun <ID : Any> getGeneratedKeys(type: KClass<ID>): Stream<ID> {
        return core.getGeneratedKeys<ID>(type.java)
    }

    override fun close() {
        core.close()
    }
}
