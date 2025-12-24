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
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import java.io.OutputStreamWriter

/**
 * KSP processor for generating metamodel classes and interfaces for data classes
 * annotated with @GenerateMetamodel or implementing the Data interface.
 *
 * @since 1.7
 */
class MetamodelProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    private val generatedFiles = mutableSetOf<String>()

    companion object {
        private const val GENERATE_METAMODEL = "st.orm.GenerateMetamodel"
        private const val DATA = "st.orm.Data"
        private const val FOREIGN_KEY = "st.orm.FK"
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.info("Storm Metamodel KSP is running.")
        try {
            val symbols = resolver.getSymbolsWithAnnotation(GENERATE_METAMODEL)
                .plus(resolver.getAllFiles()
                    .flatMap { it.declarations }
                    .filterIsInstance<KSClassDeclaration>()
                    .filter { it.implementsInterface(DATA) })
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.isDataClass() }
            val deferredSymbols = mutableListOf<KSAnnotated>()
            symbols.forEach { classDeclaration ->
                if (!classDeclaration.validate()) {
                    deferredSymbols.add(classDeclaration)
                } else {
                    try {
                        logger.info("Processing ${classDeclaration.qualifiedName?.asString()}")
                        generateMetamodelInterface(classDeclaration, resolver)
                    } catch (e: Exception) {
                        logger.error("Failed to process metamodel for ${classDeclaration.qualifiedName?.asString()}: ${e.message}\n${e.stackTraceToString()}", classDeclaration)
                        throw e
                    }
                }
            }
            return deferredSymbols
        } catch (e: Exception) {
            logger.error("Critical error in processor: ${e.message}\n${e.stackTraceToString()}")
            throw e
        }
    }

    private fun KSClassDeclaration.isDataClass(): Boolean {
        return modifiers.contains(Modifier.DATA)
    }

    private fun KSClassDeclaration.implementsInterface(interfaceName: String): Boolean {
        return try {
            getAllSuperTypes().any { superType ->
                superType.declaration.qualifiedName?.asString() == interfaceName
            }
        } catch (e: Exception) {
            logger.warn("Error checking interface implementation for ${this.qualifiedName?.asString()}: ${e.message}")
            false
        }
    }

    private fun KSTypeReference.isNestedDataClass(): Boolean {
        return try {
            val declaration = resolve().declaration
            if (declaration !is KSClassDeclaration) return false

            declaration.modifiers.contains(Modifier.DATA) &&
                    declaration.parentDeclaration is KSClassDeclaration
        } catch (e: Exception) {
            logger.warn("Error checking nested data class: ${e.message}")
            false
        }
    }

    private fun KSTypeReference.isDataClass(): Boolean {
        return try {
            val declaration = resolve().declaration
            declaration is KSClassDeclaration &&
                    declaration.modifiers.contains(Modifier.DATA)
        } catch (e: Exception) {
            logger.warn("Error checking data class: ${e.message}")
            false
        }
    }

    private fun getSimpleTypeName(typeReference: KSTypeReference, packageName: String): String {
        return try {
            val resolvedType = typeReference.resolve()
            val declaration = resolvedType.declaration
            val qualifiedName = declaration.qualifiedName?.asString() ?: return declaration.simpleName.asString()
            // Handle Ref<T> - extract the type parameter.
            if (qualifiedName == "st.orm.Ref") {
                val typeArguments = resolvedType.arguments
                if (typeArguments.isNotEmpty()) {
                    val typeArg = typeArguments[0]
                    if (typeArg.type != null) {
                        return getSimpleTypeName(typeArg.type!!, packageName)
                    }
                }
                return "Object" // Fallback if no type argument.
            }
            // If it's in the same package, use simple name.
            if (qualifiedName.startsWith(packageName) && packageName.isNotEmpty()) {
                val simpleName = qualifiedName.substring(packageName.length + 1)
                if (!simpleName.contains(".")) {
                    return simpleName
                }
            }
            // Otherwise use fully qualified name.
            qualifiedName
        } catch (e: Exception) {
            logger.error("Error getting simple type name: ${e.message}")
            "Object"
        }
    }

    private fun getKotlinTypeName(typeReference: KSTypeReference, currentPackage: String): String {
        return try {
            val resolvedType = typeReference.resolve()
            val declaration = resolvedType.declaration
            val qualifiedName = declaration.qualifiedName?.asString() ?: return "Any"
            // Handle Ref<T> - extract the type parameter.
            if (qualifiedName == "st.orm.Ref") {
                val typeArguments = resolvedType.arguments
                if (typeArguments.isNotEmpty()) {
                    val typeArg = typeArguments[0]
                    if (typeArg.type != null) {
                        return getKotlinTypeName(typeArg.type!!, currentPackage)
                    }
                }
                return "Any" // Fallback if no type argument.
            }
            // Handle primitive Kotlin types (these don't need qualification).
            val baseName = when (qualifiedName) {
                "kotlin.String" -> "String"
                "kotlin.Int" -> "Int"
                "kotlin.Long" -> "Long"
                "kotlin.Short" -> "Short"
                "kotlin.Byte" -> "Byte"
                "kotlin.Boolean" -> "Boolean"
                "kotlin.Double" -> "Double"
                "kotlin.Float" -> "Float"
                "kotlin.Char" -> "Char"
                "kotlin.collections.List" -> "List"
                "kotlin.collections.Set" -> "Set"
                "kotlin.collections.Map" -> "Map"
                // Types in java.lang don't need qualification.
                "java.lang.String" -> "String"
                "java.lang.Integer" -> "Int"
                "java.lang.Long" -> "Long"
                "java.lang.Short" -> "Short"
                "java.lang.Byte" -> "Byte"
                "java.lang.Boolean" -> "Boolean"
                "java.lang.Double" -> "Double"
                "java.lang.Float" -> "Float"
                "java.lang.Character" -> "Char"
                "java.lang.Object" -> "Any"
                // If it's in the same package, use simple name.
                else -> {
                    if (qualifiedName.startsWith(currentPackage) && currentPackage.isNotEmpty()) {
                        val simpleName = qualifiedName.substring(currentPackage.length + 1)
                        if (!simpleName.contains(".")) {
                            simpleName
                        } else {
                            qualifiedName
                        }
                    } else {
                        qualifiedName
                    }
                }
            }
            // Add <*> for generic types with type parameters.
            val typeArguments = resolvedType.arguments
            if (typeArguments.isNotEmpty() && typeArguments.all { it.variance != Variance.STAR }) {
                val wildcards = typeArguments.joinToString(", ") { "*" }
                return "$baseName<$wildcards>"
            }
            baseName
        } catch (e: Exception) {
            logger.error("Error getting Kotlin type name for ${typeReference}: ${e.message}\n${e.stackTraceToString()}")
            "Any"
        }
    }

    private fun getJavaTypeName(typeReference: KSTypeReference, currentPackage: String): String {
        return try {
            val resolvedType = typeReference.resolve()
            val declaration = resolvedType.declaration
            val qualifiedName = declaration.qualifiedName?.asString() ?: return "java.lang.Object::class.java"
            // Handle Ref<T> - extract the type parameter.
            if (qualifiedName == "st.orm.Ref") {
                val typeArguments = resolvedType.arguments
                if (typeArguments.isNotEmpty()) {
                    val typeArg = typeArguments[0]
                    if (typeArg.type != null) {
                        return getJavaTypeName(typeArg.type!!, currentPackage)
                    }
                }
                return "java.lang.Object::class.java" // Fallback if no type argument.
            }
            // Map Kotlin types to Java types for the metamodel.
            when (qualifiedName) {
                "kotlin.String", "java.lang.String" -> "String::class.java"
                "kotlin.Int", "java.lang.Integer" -> "Int::class.javaObjectType"
                "kotlin.Long", "java.lang.Long" -> "Long::class.javaObjectType"
                "kotlin.Short", "java.lang.Short" -> "Short::class.javaObjectType"
                "kotlin.Byte", "java.lang.Byte" -> "Byte::class.javaObjectType"
                "kotlin.Boolean", "java.lang.Boolean" -> "Boolean::class.javaObjectType"
                "kotlin.Double", "java.lang.Double" -> "Double::class.javaObjectType"
                "kotlin.Float", "java.lang.Float" -> "Float::class.javaObjectType"
                "kotlin.Char", "java.lang.Character" -> "Char::class.javaObjectType"
                "kotlin.collections.List" -> "List::class.java"
                "kotlin.collections.Set" -> "Set::class.java"
                "kotlin.collections.Map" -> "Map::class.java"
                // For all other types, check if in same package.
                else -> {
                    if (qualifiedName.startsWith(currentPackage) && currentPackage.isNotEmpty()) {
                        val simpleName = qualifiedName.substring(currentPackage.length + 1)
                        if (!simpleName.contains(".")) {
                            "$simpleName::class.java"
                        } else {
                            "$qualifiedName::class.java"
                        }
                    } else {
                        "$qualifiedName::class.java"
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting Java type name for ${typeReference}: ${e.message}\n${e.stackTraceToString()}")
            "java.lang.Object::class.java"
        }
    }

    private fun isForeignKey(property: KSPropertyDeclaration): Boolean {
        return try {
            // Check annotations on the property itself.
            val hasPropertyAnnotation = property.annotations.any { annotation ->
                annotation.annotationType.resolve().declaration.qualifiedName?.asString() == FOREIGN_KEY
            }
            if (hasPropertyAnnotation) {
                return true
            }
            // For data classes, also check the primary constructor parameter.
            val parentClass = property.parentDeclaration as? KSClassDeclaration
            if (parentClass != null && parentClass.modifiers.contains(Modifier.DATA)) {
                val primaryConstructor = parentClass.primaryConstructor
                if (primaryConstructor != null) {
                    val parameter = primaryConstructor.parameters.find {
                        it.name?.asString() == property.simpleName.asString()
                    }
                    if (parameter != null) {
                        return parameter.annotations.any { annotation ->
                            annotation.annotationType.resolve().declaration.qualifiedName?.asString() == FOREIGN_KEY
                        }
                    }
                }
            }
            false
        } catch (e: Exception) {
            logger.warn("Error checking foreign key for ${property.simpleName.asString()}: ${e.message}")
            false
        }
    }

    private fun buildInterfaceFields(
        classDeclaration: KSClassDeclaration,
        packageName: String,
        resolver: Resolver
    ): String {
        val builder = StringBuilder()
        val className = classDeclaration.simpleName.asString()

        try {
            classDeclaration.getAllProperties().forEach { property ->
                try {
                    val fieldName = property.simpleName.asString()
                    val typeReference = property.type
                    if (typeReference.isDataClass()) {
                        if (typeReference.isNestedDataClass()) {
                            return@forEach // Skip nested data classes.
                        }
                        // For data classes, use simple name for metamodel reference.
                        val simpleTypeName = getSimpleTypeName(typeReference, packageName)
                        // Ensure generation of metamodel for referenced data class.
                        val referencedDeclaration = typeReference.resolve().declaration
                        if (referencedDeclaration is KSClassDeclaration) {
                            generateMetamodelInterface(referencedDeclaration, resolver)
                        }
                        if (isForeignKey(property)) {
                            builder.append("        /** Represents the $className.$fieldName foreign key. */\n")
                            builder.append("        val $fieldName: ${simpleTypeName}Metamodel<$className> = ${simpleTypeName}Metamodel<$className>(\"$fieldName\", Metamodel.root($className::class.java) as Metamodel<$className, *>)\n")
                        } else {
                            builder.append("        /** Represents the inline $className.$fieldName record. */\n")
                            builder.append("        val $fieldName: ${simpleTypeName}Metamodel<$className> = ${simpleTypeName}Metamodel<$className>(\"\", \"$fieldName\", true, Metamodel.root($className::class.java) as Metamodel<$className, *>)\n")
                        }
                    } else {
                        // For regular fields, use type name (simplified for same package).
                        val kotlinTypeName = getKotlinTypeName(typeReference, packageName)
                        builder.append("        /** Represents the $className.$fieldName field. */\n")
                        builder.append("        val $fieldName: Metamodel<$className, $kotlinTypeName> = ${className}Metamodel<$className>().$fieldName\n")
                    }
                } catch (e: Exception) {
                    logger.error("Error processing property ${property.simpleName.asString()}: ${e.message}\n${e.stackTraceToString()}")
                }
            }
        } catch (e: Exception) {
            logger.error("Error building interface fields for $className: ${e.message}\n${e.stackTraceToString()}")
        }
        if (!builder.isEmpty()) {
            // Remove trailing newline.
            builder.setLength(builder.length - 1)
        }
        return builder.toString()
    }

    private fun buildClassFields(
        classDeclaration: KSClassDeclaration,
        packageName: String
    ): String {
        val builder = StringBuilder()
        try {
            classDeclaration.getAllProperties().forEach { property ->
                try {
                    val fieldName = property.simpleName.asString()
                    val typeReference = property.type
                    if (typeReference.isDataClass()) {
                        if (typeReference.isNestedDataClass()) {
                            return@forEach // Skip nested data classes.
                        }
                        val simpleTypeName = getSimpleTypeName(typeReference, packageName)
                        builder.append("    val $fieldName: ${simpleTypeName}Metamodel<T>\n")
                    } else {
                        val kotlinTypeName = getKotlinTypeName(typeReference, packageName)
                        builder.append("    val $fieldName: Metamodel<T, $kotlinTypeName>\n")
                    }
                } catch (e: Exception) {
                    logger.error("Error building class field ${property.simpleName.asString()}: ${e.message}\n${e.stackTraceToString()}")
                }
            }
        } catch (e: Exception) {
            logger.error("Error building class fields: ${e.message}\n${e.stackTraceToString()}")
        }
        return builder.toString()
    }

    private fun initClassFields(
        classDeclaration: KSClassDeclaration,
        packageName: String
    ): String {
        val builder = StringBuilder()
        try {
            classDeclaration.getAllProperties().forEach { property ->
                try {
                    val fieldName = property.simpleName.asString()
                    val typeReference = property.type
                    if (typeReference.isDataClass()) {
                        if (typeReference.isNestedDataClass()) {
                            return@forEach // Skip nested data classes.
                        }
                        val simpleTypeName = getSimpleTypeName(typeReference, packageName)
                        if (isForeignKey(property)) {
                            builder.append("        $fieldName = ${simpleTypeName}Metamodel(subPath, fieldBase + \"$fieldName\", false, this)\n")
                        } else {
                            builder.append("        $fieldName = ${simpleTypeName}Metamodel(subPath, fieldBase + \"$fieldName\", true, this)\n")
                        }
                    } else {
                        val javaTypeName = getJavaTypeName(typeReference, packageName)
                        val kotlinTypeName = getKotlinTypeName(typeReference, packageName)
                        builder.append("        $fieldName = object : AbstractMetamodel<T, $kotlinTypeName>($javaTypeName, subPath, fieldBase + \"$fieldName\", false, this) {}\n")
                    }
                } catch (e: Exception) {
                    logger.error("Error initializing field ${property.simpleName.asString()}: ${e.message}\n${e.stackTraceToString()}")
                }
            }
        } catch (e: Exception) {
            logger.error("Error initializing class fields: ${e.message}\n${e.stackTraceToString()}")
        }
        if (!builder.isEmpty()) {
            // Remove trailing newline.
            builder.setLength(builder.length - 1)
        }
        return builder.toString()
    }

    private fun generateMetamodelInterface(
        classDeclaration: KSClassDeclaration,
        resolver: Resolver
    ) {
        val qualifiedName = classDeclaration.qualifiedName?.asString() ?: return
        if (!generatedFiles.add(qualifiedName)) {
            return
        }
        generateMetamodelClass(classDeclaration)
        val packageName = classDeclaration.packageName.asString()
        val className = classDeclaration.simpleName.asString()
        val metaInterfaceName = "${className}_"
        try {
            // Handle classes without containing file (e.g., from dependencies).
            val containingFile = classDeclaration.containingFile
            val dependencies = if (containingFile != null) {
                Dependencies(true, containingFile)
            } else {
                Dependencies(false)
            }
            val file = codeGenerator.createNewFile(
                dependencies = dependencies,
                packageName = packageName,
                fileName = metaInterfaceName
            )
            OutputStreamWriter(file).use { writer ->
                writer.write("""
                |package $packageName
                |
                |import st.orm.Metamodel
                |import javax.annotation.processing.Generated
                |
                |/**
                 | * Metamodel for $className.
                 | *
                 | * @param T the record type of the root table of the entity graph.
                 | */
                |@Generated("${this::class.java.name}")
                |interface $metaInterfaceName<T> : Metamodel<$className, $className> {
                |    companion object {
                |${buildInterfaceFields(classDeclaration, packageName, resolver)}
                |    }
                |}
            """.trimMargin())
            }
        } catch (e: Exception) {
            logger.error("Failed to generate metamodel interface for $className: ${e.message}\n${e.stackTraceToString()}", classDeclaration)
            throw e
        }
    }

    private fun generateMetamodelClass(
        classDeclaration: KSClassDeclaration,
    ) {
        val packageName = classDeclaration.packageName.asString()
        val className = classDeclaration.simpleName.asString()
        val metaClassName = "${className}Metamodel"
        try {
            // Handle classes without containing file (e.g., from dependencies).
            val containingFile = classDeclaration.containingFile
            val dependencies = if (containingFile != null) {
                Dependencies(true, containingFile)
            } else {
                Dependencies(false)
            }
            val file = codeGenerator.createNewFile(
                dependencies = dependencies,
                packageName = packageName,
                fileName = metaClassName
            )
            OutputStreamWriter(file).use { writer ->
                writer.write("""
                |package $packageName
                |
                |import st.orm.Metamodel
                |import st.orm.AbstractMetamodel
                |import javax.annotation.processing.Generated
                |
                |@Generated("${this::class.java.name}")
                |class $metaClassName<T>(
                |    path: String,
                |    field: String,
                |    inline: Boolean,
                |    parent: Metamodel<T, *>
                |) : AbstractMetamodel<T, $className>($className::class.java, path, field, inline, parent) {
                |
                |${buildClassFields(classDeclaration, packageName)}
                |    init {
                |        val subPath = if (inline) path else if (field.isEmpty()) path else if (path.isEmpty()) field else "${'$'}path.${'$'}field"
                |        val fieldBase = if (inline) if (field.isEmpty()) "" else "${'$'}field." else ""
                |
                |${initClassFields(classDeclaration, packageName)}
                |    }
                |
                |    @Suppress("UNCHECKED_CAST")
                |    constructor() : this("", "", false, Metamodel.root($className::class.java) as Metamodel<T, *>)
                |    constructor(field: String, parent: Metamodel<T, *>) : this("", field, false, parent)
                |    constructor(path: String, field: String, parent: Metamodel<T, *>) : this(path, field, false, parent)
                |}
            """.trimMargin())
            }
        } catch (e: Exception) {
            logger.error("Failed to generate metamodel class for $className: ${e.message}\n${e.stackTraceToString()}", classDeclaration)
            throw e
        }
    }
}