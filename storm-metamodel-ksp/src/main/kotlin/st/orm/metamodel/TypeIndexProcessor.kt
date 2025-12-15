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
package st.orm.metamodel

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration

class TypeIndexProcessor(
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {

    /**
     * typeFqName -> set of discovered subtypes
     */
    private val indexEntries: MutableMap<String, MutableSet<String>> =
        INDEXED_TYPES.associateWith { linkedSetOf<String>() }.toMutableMap()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getAllFiles().forEach { file ->
            file.declarations
                .filterIsInstance<KSClassDeclaration>()
                .forEach { clazz ->
                    val fqName = clazz.qualifiedName?.asString() ?: return@forEach
                    INDEXED_TYPES.forEach { typeFqName ->
                        if (clazz.isSubtypeOfFqName(typeFqName)) {
                            indexEntries.getValue(typeFqName) += fqName
                        }
                    }
                }
        }
        return emptyList()
    }

    override fun finish() {
        indexEntries.forEach { (typeFqName, lines) ->
            writeIndex(typeFqName, lines)
        }
    }

    private fun writeIndex(typeFqName: String, lines: Set<String>) {
        if (lines.isEmpty()) return
        val file = codeGenerator.createNewFile(
            Dependencies.ALL_FILES,
            INDEX_PACKAGE,
            typeFqName,
            "idx"
        )
        file.bufferedWriter().use { out ->
            lines.sorted().forEach { fqcn ->
                out.appendLine(fqcn)
            }
        }
    }

    companion object {
        private const val INDEX_PACKAGE = "META-INF.storm"

        private val INDEXED_TYPES = listOf(
            "st.orm.Data",
            "st.orm.Converter"
        )
    }
}

private fun KSClassDeclaration.isSubtypeOfFqName(targetFqName: String): Boolean {
    val selfFqName = this.qualifiedName?.asString()
    if (selfFqName == targetFqName) return true
    return getAllSuperTypes().any {
        it.declaration.qualifiedName?.asString() == targetFqName
    }
}