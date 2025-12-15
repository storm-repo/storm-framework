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
package st.orm.serialization.spi

import st.orm.Json
import st.orm.core.spi.ORMConverter
import st.orm.core.spi.ORMConverterProvider
import st.orm.mapping.RecordField
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberProperties

class JsonORMConverterProviderImpl : ORMConverterProvider {

    override fun getConverter(field: RecordField): Optional<ORMConverter> {
        val jsonAnn = field.getAnnotation(Json::class.java) ?: return Optional.empty()
        val owner = field.declaringType()
        val kType = resolveKType(owner.kotlin, field.name())
            ?: throw IllegalArgumentException(
                "Cannot resolve Kotlin KType for '${owner.name}.${field.name()}'. " +
                        "Add Kotlin metadata access to RecordField or fall back to Java Type resolution."
            )
        return Optional.of(JsonORMConverterImpl(field, kType, jsonAnn))
    }

    private fun resolveKType(kClass: KClass<*>, fieldName: String): KType? {
        return kClass.declaredMemberProperties.firstOrNull { it.name == fieldName }?.returnType
    }
}