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
package st.orm.kotlin.template

import st.orm.kotlin.KTemplates
import st.orm.kotlin.repository.KRepository
import st.orm.template.ORMTemplate

/**
 * Extension to convert an [ORMTemplate] to a [KORMTemplate].
 */
val ORMTemplate.wrap: KORMTemplate
    get() = KTemplates.ORM(this)

/**
 * Extensions for [ORMTemplate] to provide bridged access to [KRepository].
 */
inline fun <reified R : KRepository> ORMTemplate.bridged(): R {
    return wrap.repository(R::class.java)
}