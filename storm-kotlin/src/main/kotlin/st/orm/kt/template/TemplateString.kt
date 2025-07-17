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
package st.orm.kt.template

/**
 *
 * @since 1.4
 */
sealed interface TemplateString {
    companion object {
        /**
         * Create a new template string from the given [TemplateBuilder].
         */
        @JvmStatic
        fun create(builder: TemplateBuilder): TemplateString = builder.build()

        /**
         * Create a new template string from the given [TemplateBuilder].
         */
        @JvmStatic
        fun raw(str: String): TemplateString =
            TemplateStringHolder(st.orm.core.template.TemplateString.of(str))

        /**
         * Create a new template string from the given [TemplateBuilder].
         */
        @JvmStatic
        fun wrap(value: Any?): TemplateString =
            TemplateStringHolder(st.orm.core.template.TemplateString.wrap(value))

        @JvmStatic
        fun combine(vararg templates: TemplateString): TemplateString =
            TemplateStringHolder(st.orm.core.template.TemplateString.combine(*templates.map { it.unwrap }.toTypedArray()))
    }
}

/**
 * A little alias for “build me a SQL string in the StringTemplate DSL.”
 */
typealias TemplateBuilder = TemplateContext.() -> String

interface TemplateContext {

    fun t(o: Any?): String {
        return insert(o)
    }

    fun insert(o: Any?): String
}

fun TemplateBuilder.build(): TemplateString =
    TemplateStringHolder(st.orm.core.template.TemplateBuilder.create { ctx ->
        with(object : TemplateContext {
            override fun insert(o: Any?) = ctx.insert(o)
        }, this)
    })

internal data class TemplateStringHolder(
    val templateString: st.orm.core.template.TemplateString
) : TemplateString

internal val TemplateString.unwrap: st.orm.core.template.TemplateString get() =
    (this as TemplateStringHolder).templateString

