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
package st.orm.metamodel

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Compiles fixture sources with the processor attached and asserts on the generated files. The fixtures compile
 * against the actual storm-foundation classes, so the `st.orm.GenerateMetamodel` annotation the processor matches
 * by name is the real one and the generated code compiles against the real metamodel base classes.
 */
@OptIn(ExperimentalCompilerApi::class)
class MetamodelProcessorTest {

    private fun compile(source: String): KotlinCompilation {
        val compilation = KotlinCompilation().apply {
            sources = listOf(SourceFile.kotlin("CityStats.kt", source))
            useKsp2()
            symbolProcessorProviders = mutableListOf(MetamodelProcessorProvider())
            inheritClassPath = true
            verbose = false
        }
        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        return compilation
    }

    private fun KotlinCompilation.generatedFileNames(): Set<String> = kspSourcesDir.walkTopDown().filter { it.isFile }.map { it.name }.toSet()

    @Test
    fun `generates metamodel for annotated plain data class`() {
        val compilation = compile(
            """
            package com.example

            import st.orm.GenerateMetamodel

            @GenerateMetamodel
            data class CityStats(val name: String, val inhabitants: Int)
            """.trimIndent(),
        )
        val generated = compilation.generatedFileNames()
        assertTrue("CityStatsMetamodel.kt" in generated) {
            "expected a metamodel for the @GenerateMetamodel data class, generated: $generated"
        }
        assertTrue("CityStatsNullableMetamodel.kt" in generated) {
            "expected a nullable-chain metamodel for the @GenerateMetamodel data class, generated: $generated"
        }
        assertTrue("CityStatsInstantiator.kt" in generated) {
            "expected an instantiator for the @GenerateMetamodel data class, generated: $generated"
        }
        assertFalse("CityStats_.kt" in generated) {
            "the root metamodel interface is reserved for Data types, generated: $generated"
        }
        val services = compilation.workingDir.walkTopDown().filter { it.isFile }
        assertTrue(services.any { it.name == "st.orm.mapping.Instantiator" && "CityStatsInstantiator" in it.readText() }) {
            "expected an instantiator service registration"
        }
    }

    @Test
    fun `ignores plain data class without annotation`() {
        val compilation = compile(
            """
            package com.example

            data class CityStats(val name: String, val inhabitants: Int)
            """.trimIndent(),
        )
        val generated = compilation.generatedFileNames()
        assertTrue(generated.isEmpty()) {
            "a plain data class without @GenerateMetamodel should not get generated files, generated: $generated"
        }
    }
}
