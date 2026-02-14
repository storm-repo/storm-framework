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

/**
 * Represents a compiled SQL template string that can be passed to Storm's query engine for execution.
 *
 * A `TemplateString` is typically produced by a [TemplateBuilder] lambda (via the [build] extension function)
 * or by the companion factory methods [raw], [wrap], and [combine]. It encapsulates both the SQL fragments and
 * any interpolated parameters, allowing the query engine to generate a safe, parameterized SQL statement.
 *
 * ```
 * val template = TemplateString.raw { "SELECT ${t(User::class)} FROM ${t(User::class)} WHERE ${t(User::class)}.name = ${t("Alice")}" }
 * orm.query(template).getResultList(User::class)
 * ```
 *
 * @since 1.4
 * @see TemplateBuilder
 * @see TemplateContext
 */
sealed interface TemplateString {
    companion object {
        /**
         * Create a new template string from the given [TemplateBuilder].
         */
        fun raw(builder: TemplateBuilder): TemplateString = builder.build()

        /**
         * Create a new template string from a raw SQL string. No parameter interpolation is performed.
         */
        fun raw(str: String): TemplateString = TemplateStringHolder(st.orm.core.template.TemplateString.of(str))

        /**
         * Create a new template string that wraps a single value as a bound parameter.
         */
        fun wrap(value: Any?): TemplateString = TemplateStringHolder(st.orm.core.template.TemplateString.wrap(value))

        /**
         * Combine multiple template strings into a single template string.
         */
        fun combine(vararg templates: TemplateString): TemplateString = TemplateStringHolder(st.orm.core.template.TemplateString.combine(*templates.map { it.unwrap }.toTypedArray()))
    }
}

/**
 * A function type alias for building SQL template strings. The receiver [TemplateContext] provides the [t][TemplateContext.t]
 * function used to interpolate values, types, and elements into the template.
 *
 * ```
 * val builder: TemplateBuilder = { "SELECT ${t(User::class)} FROM ${t(User::class)}" }
 * ```
 *
 * @see TemplateContext
 * @see TemplateString
 */
typealias TemplateBuilder = TemplateContext.() -> String

/**
 * Provides the interpolation context for building SQL template strings.
 *
 * Within a [TemplateBuilder] lambda, use [t] (or its longer form [insert]) to interpolate values, types,
 * and SQL elements into the template. The interpolated objects are processed by the Storm template engine,
 * which resolves them into the appropriate SQL fragments and bound parameters based on their position in the query.
 *
 * @see TemplateBuilder
 * @see TemplateString
 */
interface TemplateContext {

    /**
     * Interpolates the given object into the SQL template. Shorthand for [insert].
     *
     * @param o the object to interpolate (a value, [KClass][kotlin.reflect.KClass], [Element], record, etc.).
     * @return a placeholder string that the template engine replaces with the appropriate SQL fragment.
     */
    fun t(o: Any?): String = insert(o)

    /**
     * Interpolates the given object into the SQL template.
     *
     * @param o the object to interpolate.
     * @return a placeholder string that the template engine replaces with the appropriate SQL fragment.
     */
    fun insert(o: Any?): String
}

/**
 * Builds this [TemplateBuilder] into a [TemplateString] ready for use with the Storm query engine.
 */
fun TemplateBuilder.build(): TemplateString = TemplateStringHolder(
    st.orm.core.template.TemplateBuilder.create { ctx ->
        with(
            object : TemplateContext {
                override fun insert(o: Any?) = ctx.insert(o)
            },
            this,
        )
    },
)

internal data class TemplateStringHolder(
    val templateString: st.orm.core.template.TemplateString,
) : TemplateString

internal val TemplateString.unwrap: st.orm.core.template.TemplateString get() =
    (this as TemplateStringHolder).templateString
