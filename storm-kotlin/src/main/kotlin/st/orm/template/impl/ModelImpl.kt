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
package st.orm.template.impl

import st.orm.Metamodel
import st.orm.template.Column
import st.orm.template.Model
import java.util.SequencedMap
import kotlin.reflect.KClass

class ModelImpl<E : Record, ID : Any>(
    internal val core: st.orm.core.template.Model<E, ID>
) : Model<E, ID> {

    override val schema: String                             get() = core.schema()
    override val name: String                               get() = core.name()
    override val type: KClass<E>                            get() = core.type().kotlin
    override val primaryKeyType: KClass<ID>                 get() = core.primaryKeyType().kotlin
    override val columns: List<Column>                      get() = core.columns().map(::ColumnImpl)
    override fun isDefaultPrimaryKey(pk: ID?): Boolean      = core.isDefaultPrimaryKey(pk)
    override fun getValue(column: Column, record: E): Any?  = core.getValue((column as ColumnImpl).core, record)
    override fun getValues(record: E): SequencedMap<Column, Any?> {
        val map = LinkedHashMap<Column, Any?>();
        core.getValues(record).forEach { (c, v) -> map.put(ColumnImpl(c), v) };
        return map;
    }
    override fun getMetamodel(column: Column): Metamodel<E, *> =
        core.getMetamodel((column as ColumnImpl).core)
}