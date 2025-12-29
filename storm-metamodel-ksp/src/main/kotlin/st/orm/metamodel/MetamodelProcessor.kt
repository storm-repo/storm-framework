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
 * KSP processor for generating metamodel classes and interfaces for Kotlin data classes.
 *
 * Equality semantics in generated code:
 * - For non-nullable primitive fields (Int, Long, Boolean, etc): compare using primitive operations to avoid boxing.
 *   - Float/Double use toBits() comparisons for stable NaN and -0.0 handling.
 * - For reference-typed fields (including Ref<*> and nullable primitives like Int?): isSame uses ==.
 * - For isIdentical:
 *   - Uses === for normal reference types.
 *   - Uses == for nullable primitives (Int?, Long?, ...).
 *   - Uses == for "value-based" types (for example java.time.Instant) to avoid identity-sensitive operations.
 *
 * Special root isSame behavior:
 * - If the data class has a @PK field, root isSame compares by that PK field:
 *     getter(a).pk == getter(b).pk
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
        private const val REF = "st.orm.Ref"
        private const val PRIMARY_KEY = "st.orm.PK"

        private const val K_BOOLEAN = "kotlin.Boolean"
        private const val K_BYTE = "kotlin.Byte"
        private const val K_SHORT = "kotlin.Short"
        private const val K_INT = "kotlin.Int"
        private const val K_LONG = "kotlin.Long"
        private const val K_CHAR = "kotlin.Char"
        private const val K_FLOAT = "kotlin.Float"
        private const val K_DOUBLE = "kotlin.Double"

        /**
         * Kotlin warns on identity checks (===) for Java "value-based" types (for example java.time.Instant).
         * These types are not meant to have stable identity semantics, so we emit == instead.
         *
         * This list is intentionally small and can be extended when new warnings show up.
         */
        private val VALUE_BASED_QNS: Set<String> = setOf(
            "java.time.Instant",
            "java.time.LocalDate",
            "java.time.LocalTime",
            "java.time.LocalDateTime",
            "java.time.OffsetDateTime",
            "java.time.ZonedDateTime",
            "java.time.Duration",
            "java.time.Period",
            "java.util.Optional",
            "java.util.OptionalInt",
            "java.util.OptionalLong",
            "java.util.OptionalDouble"
        )

        private const val JDK_VALUE_BASED_ANNOTATION = "jdk.internal.ValueBased"

        private val KOTLIN_PRIMITIVE_QNS: Set<String> = setOf(
            K_BOOLEAN, K_BYTE, K_SHORT, K_INT, K_LONG, K_CHAR, K_FLOAT, K_DOUBLE
        )
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.info("Storm Metamodel KSP is running.")
        val deferred = mutableListOf<KSAnnotated>()

        val symbols = resolver.getSymbolsWithAnnotation(GENERATE_METAMODEL)
            .plus(
                resolver.getAllFiles()
                    .flatMap { it.declarations }
                    .filterIsInstance<KSClassDeclaration>()
                    .filter { it.implementsInterface(DATA) }
            )
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.isDataClass() }

        symbols.forEach { clazz ->
            if (!clazz.validate()) {
                deferred.add(clazz)
            } else {
                try {
                    generateMetamodelInterface(clazz, resolver)
                } catch (e: Exception) {
                    logger.error(
                        "Failed to process metamodel for ${clazz.qualifiedName?.asString()}: ${e.message}\n" +
                                e.stackTraceToString(),
                        clazz
                    )
                    throw e
                }
            }
        }

        return deferred
    }

    private fun KSClassDeclaration.isDataClass(): Boolean =
        modifiers.contains(Modifier.DATA)

    private fun KSClassDeclaration.implementsInterface(interfaceName: String): Boolean {
        return try {
            getAllSuperTypes().any { superType ->
                superType.declaration.qualifiedName?.asString() == interfaceName
            }
        } catch (e: Exception) {
            logger.warn("Error checking interface implementation for ${qualifiedName?.asString()}: ${e.message}")
            false
        }
    }

    private fun KSTypeReference.isNestedDataClass(): Boolean {
        return try {
            val decl = resolve().declaration as? KSClassDeclaration ?: return false
            decl.modifiers.contains(Modifier.DATA) && decl.parentDeclaration is KSClassDeclaration
        } catch (e: Exception) {
            logger.warn("Error checking nested data class: ${e.message}")
            false
        }
    }

    private fun KSTypeReference.isDataClass(): Boolean {
        return try {
            val decl = resolve().declaration as? KSClassDeclaration ?: return false
            decl.modifiers.contains(Modifier.DATA)
        } catch (e: Exception) {
            logger.warn("Error checking data class: ${e.message}")
            false
        }
    }

    /**
     * Unwrap Ref<X> -> X for the metamodel generic E.
     */
    private fun unwrapRef(typeReference: KSTypeReference): KSTypeReference {
        val resolved = typeReference.resolve()
        val qn = resolved.declaration.qualifiedName?.asString() ?: return typeReference
        if (qn != REF) return typeReference
        val arg = resolved.arguments.firstOrNull()?.type
        return arg ?: typeReference
    }

    private fun getSimpleTypeName(typeReference: KSTypeReference, packageName: String): String {
        return try {
            val effective = unwrapRef(typeReference)
            val resolvedType = effective.resolve()
            val decl = resolvedType.declaration
            val qualifiedName = decl.qualifiedName?.asString() ?: return decl.simpleName.asString()
            if (qualifiedName.startsWith(packageName) && packageName.isNotEmpty()) {
                val simpleName = qualifiedName.substring(packageName.length + 1)
                if (!simpleName.contains(".")) return simpleName
            }
            qualifiedName
        } catch (e: Exception) {
            logger.error("Error getting simple type name: ${e.message}")
            "Object"
        }
    }

    private fun getKotlinTypeName(typeReference: KSTypeReference, currentPackage: String): String {
        return try {
            val effective = unwrapRef(typeReference)
            val resolvedType = effective.resolve()
            val decl = resolvedType.declaration
            val qualifiedName = decl.qualifiedName?.asString() ?: return "Any"

            val baseName = when (qualifiedName) {
                "kotlin.String", "java.lang.String" -> "String"
                "kotlin.Int", "java.lang.Integer" -> "Int"
                "kotlin.Long", "java.lang.Long" -> "Long"
                "kotlin.Short", "java.lang.Short" -> "Short"
                "kotlin.Byte", "java.lang.Byte" -> "Byte"
                "kotlin.Boolean", "java.lang.Boolean" -> "Boolean"
                "kotlin.Double", "java.lang.Double" -> "Double"
                "kotlin.Float", "java.lang.Float" -> "Float"
                "kotlin.Char", "java.lang.Character" -> "Char"
                "java.lang.Object" -> "Any"
                else -> {
                    if (qualifiedName.startsWith(currentPackage) && currentPackage.isNotEmpty()) {
                        val simpleName = qualifiedName.substring(currentPackage.length + 1)
                        if (!simpleName.contains(".")) simpleName else qualifiedName
                    } else qualifiedName
                }
            }

            val typeArguments = resolvedType.arguments
            if (typeArguments.isNotEmpty() && typeArguments.all { it.variance != Variance.STAR }) {
                val wildcards = typeArguments.joinToString(", ") { "*" }
                "$baseName<$wildcards>"
            } else baseName
        } catch (e: Exception) {
            logger.error(
                "Error getting Kotlin type name for $typeReference: ${e.message}\n${e.stackTraceToString()}"
            )
            "Any"
        }
    }

    /**
     * Kotlin value type name for *actual* runtime value returned by getValue() for fields.
     *
     * Unlike getKotlinTypeName(..), this does NOT unwrap Ref<X> -> X.
     * It also renders Ref as `Ref` (not `st.orm.Ref`) so the generated code can rely on `import st.orm.Ref`.
     */
    private fun getKotlinValueTypeName(typeReference: KSTypeReference, currentPackage: String): String {
        fun render(type: KSType): String {
            val decl = type.declaration
            val qualifiedName = decl.qualifiedName?.asString() ?: decl.simpleName.asString()

            val baseName = when (qualifiedName) {
                "st.orm.Ref" -> "Ref"
                "kotlin.String", "java.lang.String" -> "String"
                "kotlin.Int", "java.lang.Integer" -> "Int"
                "kotlin.Long", "java.lang.Long" -> "Long"
                "kotlin.Short", "java.lang.Short" -> "Short"
                "kotlin.Byte", "java.lang.Byte" -> "Byte"
                "kotlin.Boolean", "java.lang.Boolean" -> "Boolean"
                "kotlin.Double", "java.lang.Double" -> "Double"
                "kotlin.Float", "java.lang.Float" -> "Float"
                "kotlin.Char", "java.lang.Character" -> "Char"
                "java.lang.Object" -> "Any"
                else -> {
                    if (qualifiedName.startsWith(currentPackage) && currentPackage.isNotEmpty()) {
                        val simpleName = qualifiedName.substring(currentPackage.length + 1)
                        if (!simpleName.contains(".")) simpleName else qualifiedName
                    } else qualifiedName
                }
            }

            val args = type.arguments
            val typeArgs = if (args.isNotEmpty()) {
                args.joinToString(", ", prefix = "<", postfix = ">") { arg ->
                    val t = arg.type?.resolve()
                    when {
                        arg.variance == Variance.STAR || t == null -> "*"
                        else -> render(t)
                    }
                }
            } else ""
            val nullableSuffix = if (type.isMarkedNullable) "?" else ""
            return baseName + typeArgs + nullableSuffix
        }
        return render(typeReference.resolve())
    }

    private fun getJavaTypeName(typeReference: KSTypeReference, currentPackage: String): String {
        return try {
            val effective = unwrapRef(typeReference)
            val resolvedType = effective.resolve()
            val decl = resolvedType.declaration
            val qualifiedName = decl.qualifiedName?.asString() ?: return "java.lang.Object::class.java"
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
                else -> {
                    if (qualifiedName.startsWith(currentPackage) && currentPackage.isNotEmpty()) {
                        val simpleName = qualifiedName.substring(currentPackage.length + 1)
                        if (!simpleName.contains(".")) "$simpleName::class.java" else "$qualifiedName::class.java"
                    } else "$qualifiedName::class.java"
                }
            }
        } catch (e: Exception) {
            logger.error(
                "Error getting Java type name for $typeReference: ${e.message}\n${e.stackTraceToString()}"
            )
            "java.lang.Object::class.java"
        }
    }

    private fun isForeignKey(property: KSPropertyDeclaration): Boolean {
        return try {
            val hasPropertyAnnotation = property.annotations.any { ann ->
                ann.annotationType.resolve().declaration.qualifiedName?.asString() == FOREIGN_KEY
            }
            if (hasPropertyAnnotation) return true

            val parentClass = property.parentDeclaration as? KSClassDeclaration
            if (parentClass != null && parentClass.modifiers.contains(Modifier.DATA)) {
                val ctor = parentClass.primaryConstructor
                val param = ctor?.parameters?.find { it.name?.asString() == property.simpleName.asString() }
                if (param != null) {
                    return param.annotations.any { ann ->
                        ann.annotationType.resolve().declaration.qualifiedName?.asString() == FOREIGN_KEY
                    }
                }
            }
            false
        } catch (e: Exception) {
            logger.warn("Error checking foreign key for ${property.simpleName.asString()}: ${e.message}")
            false
        }
    }

    private fun hasAnnotationOrMeta(annotated: KSAnnotated, annotationQn: String): Boolean {
        for (ann in annotated.annotations) {
            val annDecl = ann.annotationType.resolve().declaration as? KSClassDeclaration ?: continue
            val annQn = annDecl.qualifiedName?.asString()
            if (annQn == annotationQn) return true

            if (annDecl.annotations.any { meta ->
                    meta.annotationType.resolve().declaration.qualifiedName?.asString() == annotationQn
                }
            ) return true
        }
        return false
    }

    private fun isPrimaryKey(prop: KSPropertyDeclaration): Boolean {
        if (hasAnnotationOrMeta(prop, PRIMARY_KEY)) return true

        val parent = prop.parentDeclaration as? KSClassDeclaration ?: return false
        val ctor = parent.primaryConstructor ?: return false
        val param = ctor.parameters.firstOrNull { it.name?.asString() == prop.simpleName.asString() } ?: return false
        return hasAnnotationOrMeta(param, PRIMARY_KEY)
    }

    private fun findPrimaryKeyProperty(clazz: KSClassDeclaration): KSPropertyDeclaration? =
        clazz.getAllProperties().firstOrNull { isPrimaryKey(it) }

    private enum class PrimitiveKind { BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE }

    private fun primitiveKind(typeRef: KSTypeReference): PrimitiveKind? {
        val t = typeRef.resolve()
        if (t.isMarkedNullable) return null

        val qn = t.declaration.qualifiedName?.asString() ?: return null
        return when (qn) {
            K_BOOLEAN -> PrimitiveKind.BOOLEAN
            K_BYTE -> PrimitiveKind.BYTE
            K_SHORT -> PrimitiveKind.SHORT
            K_INT -> PrimitiveKind.INT
            K_LONG -> PrimitiveKind.LONG
            K_CHAR -> PrimitiveKind.CHAR
            K_FLOAT -> PrimitiveKind.FLOAT
            K_DOUBLE -> PrimitiveKind.DOUBLE
            else -> null
        }
    }

    private fun isNullableKotlinPrimitive(typeRef: KSTypeReference): Boolean {
        val t = typeRef.resolve()
        if (!t.isMarkedNullable) return false
        val qn = t.declaration.qualifiedName?.asString() ?: return false
        return qn in KOTLIN_PRIMITIVE_QNS
    }

    private fun isValueBasedType(typeRef: KSTypeReference): Boolean {
        val decl = typeRef.resolve().declaration as? KSClassDeclaration ?: return false
        val qn = decl.qualifiedName?.asString() ?: return false
        if (qn in VALUE_BASED_QNS) return true

        return decl.annotations.any { ann ->
            ann.annotationType.resolve().declaration.qualifiedName?.asString() == JDK_VALUE_BASED_ANNOTATION
        }
    }

    private fun sameExpr(left: String, right: String, typeRef: KSTypeReference): String {
        return when (primitiveKind(typeRef)) {
            PrimitiveKind.FLOAT -> "($left).toBits() == ($right).toBits()"
            PrimitiveKind.DOUBLE -> "($left).toBits() == ($right).toBits()"
            PrimitiveKind.BOOLEAN,
            PrimitiveKind.BYTE,
            PrimitiveKind.SHORT,
            PrimitiveKind.INT,
            PrimitiveKind.LONG,
            PrimitiveKind.CHAR -> "$left == $right"
            null -> "$left == $right"
        }
    }

    private fun identicalExpr(left: String, right: String, typeRef: KSTypeReference): String {
        return when (primitiveKind(typeRef)) {
            PrimitiveKind.FLOAT -> "($left).toBits() == ($right).toBits()"
            PrimitiveKind.DOUBLE -> "($left).toBits() == ($right).toBits()"
            PrimitiveKind.BOOLEAN,
            PrimitiveKind.BYTE,
            PrimitiveKind.SHORT,
            PrimitiveKind.INT,
            PrimitiveKind.LONG,
            PrimitiveKind.CHAR -> "$left == $right"
            null -> {
                if (isNullableKotlinPrimitive(typeRef)) return "$left == $right"
                if (isValueBasedType(typeRef)) "$left == $right" else "$left === $right"
            }
        }
    }

    private fun buildInterfaceFields(
        classDeclaration: KSClassDeclaration,
        packageName: String,
        resolver: Resolver
    ): String {
        val builder = StringBuilder()
        val className = classDeclaration.simpleName.asString()

        classDeclaration.getAllProperties().forEach { prop ->
            val fieldName = prop.simpleName.asString()
            val typeRef = prop.type

            if (typeRef.isDataClass()) {
                if (typeRef.isNestedDataClass()) return@forEach

                val simpleTypeName = getSimpleTypeName(typeRef, packageName)
                val referencedDecl = typeRef.resolve().declaration as? KSClassDeclaration
                if (referencedDecl != null) generateMetamodelInterface(referencedDecl, resolver)

                val inlineFlag = if (isForeignKey(prop)) "false" else "true"
                val getterExpr = "{ t: $className -> t.$fieldName }"

                builder.append("        /** Represents the $className.$fieldName record. */\n")
                builder.append(
                    "        val $fieldName: ${simpleTypeName}Metamodel<$className> = " +
                            "${simpleTypeName}Metamodel(" +
                            "\"\", \"$fieldName\", $inlineFlag, " +
                            "Metamodel.root($className::class.java) as Metamodel<$className, *>) " +
                            getterExpr +
                            "\n"
                )
            } else {
                val kotlinTypeName = getKotlinTypeName(typeRef, packageName)              // E (unwrap Ref)
                val valueKotlinTypeName = getKotlinValueTypeName(typeRef, packageName)   // V (keep Ref)

                builder.append("        /** Represents the $className.$fieldName field. */\n")
                builder.append(
                    "        val $fieldName: AbstractMetamodel<$className, $kotlinTypeName, $valueKotlinTypeName> = " +
                            "${className}Metamodel<$className>().$fieldName\n"
                )
            }
        }

        if (builder.isNotEmpty()) builder.setLength(builder.length - 1)
        return builder.toString()
    }

    private fun buildClassFields(classDeclaration: KSClassDeclaration, packageName: String): String {
        val builder = StringBuilder()
        classDeclaration.getAllProperties().forEach { prop ->
            val fieldName = prop.simpleName.asString()
            val typeRef = prop.type
            if (typeRef.isDataClass()) {
                if (typeRef.isNestedDataClass()) return@forEach
                val simpleTypeName = getSimpleTypeName(typeRef, packageName)
                builder.append("    val $fieldName: ${simpleTypeName}Metamodel<T>\n")
            } else {
                val kotlinTypeName = getKotlinTypeName(typeRef, packageName)            // E
                val valueKotlinTypeName = getKotlinValueTypeName(typeRef, packageName) // V
                builder.append("    val $fieldName: AbstractMetamodel<T, $kotlinTypeName, $valueKotlinTypeName>\n")
            }
        }
        return builder.toString()
    }

    private fun initClassFields(
        classDeclaration: KSClassDeclaration,
        packageName: String,
        metaClassName: String,
        recordClassName: String
    ): String {
        val builder = StringBuilder()
        classDeclaration.getAllProperties().forEach { prop ->
            val fieldName = prop.simpleName.asString()
            val typeRef = prop.type
            if (typeRef.isDataClass()) {
                if (typeRef.isNestedDataClass()) return@forEach
                val simpleTypeName = getSimpleTypeName(typeRef, packageName)
                val inlineFlag = if (isForeignKey(prop)) "false" else "true"

                val getterExpr =
                    "{ t: T & Any -> (this@$metaClassName.getValue(t) as $recordClassName).$fieldName }"

                builder.append(
                    "        $fieldName = ${simpleTypeName}Metamodel(" +
                            "subPath, fieldBase + \"$fieldName\", $inlineFlag, this) $getterExpr" +
                            "\n"
                )
            } else {
                val javaTypeName = getJavaTypeName(typeRef, packageName)                // E (unwrap Ref)
                val kotlinTypeName = getKotlinTypeName(typeRef, packageName)            // E (unwrap Ref)
                val valueKotlinTypeName = getKotlinValueTypeName(typeRef, packageName) // V (keep Ref)

                val leftValue = "ra.$fieldName"
                val rightValue = "rb.$fieldName"
                val isSameExpr = sameExpr(leftValue, rightValue, typeRef)
                val isIdenticalExpr = identicalExpr(leftValue, rightValue, typeRef)
                builder.append(
                    "        $fieldName = object : AbstractMetamodel<T, $kotlinTypeName, $valueKotlinTypeName>(" +
                            "$javaTypeName, subPath, fieldBase + \"$fieldName\", false, this" +
                            ") {\n" +
                            "            override fun getValue(record: T & Any): $valueKotlinTypeName =\n" +
                            "                (this@$metaClassName.getValue(record) as $recordClassName).$fieldName\n\n" +
                            "            override fun isIdentical(a: T & Any, b: T & Any): Boolean {\n" +
                            "                val ra = this@$metaClassName.getValue(a) as $recordClassName\n" +
                            "                val rb = this@$metaClassName.getValue(b) as $recordClassName\n" +
                            "                return $isIdenticalExpr\n" +
                            "            }\n\n" +
                            "            override fun isSame(a: T & Any, b: T & Any): Boolean {\n" +
                            "                val ra = this@$metaClassName.getValue(a) as $recordClassName\n" +
                            "                val rb = this@$metaClassName.getValue(b) as $recordClassName\n" +
                            "                return $isSameExpr\n" +
                            "            }\n" +
                            "        }\n"
                )
            }
        }
        if (builder.isNotEmpty()) {
            builder.setLength(builder.length - 1)
        }
        return builder.toString()
    }

    private fun generateMetamodelInterface(classDeclaration: KSClassDeclaration, resolver: Resolver) {
        val qualifiedName = classDeclaration.qualifiedName?.asString() ?: return
        if (!generatedFiles.add(qualifiedName)) return

        generateMetamodelClass(classDeclaration)
        val packageName = classDeclaration.packageName.asString()
        val className = classDeclaration.simpleName.asString()
        val metaInterfaceName = "${className}_"
        val containingFile = classDeclaration.containingFile
        val deps = if (containingFile != null) Dependencies(true, containingFile) else Dependencies(false)
        val file = codeGenerator.createNewFile(
            dependencies = deps,
            packageName = packageName,
            fileName = metaInterfaceName
        )
        OutputStreamWriter(file).use { writer ->
            writer.write(
                """
                |package $packageName
                |
                |import st.orm.Metamodel
                |import st.orm.AbstractMetamodel
                |import st.orm.Ref
                |import javax.annotation.processing.Generated
                |
                |/**
                | * Metamodel for $className.
                |
                | * @param T the record type of the root table of the entity graph.
                | */
                |@Suppress("ClassName")
                |@Generated("${this::class.java.name}")
                |interface $metaInterfaceName<T> : Metamodel<$className, $className> {
                |    companion object {
                |${buildInterfaceFields(classDeclaration, packageName, resolver)}
                |    }
                |}
                """.trimMargin()
            )
        }
    }

    private fun generateMetamodelClass(classDeclaration: KSClassDeclaration) {
        val packageName = classDeclaration.packageName.asString()
        val className = classDeclaration.simpleName.asString()
        val metaClassName = "${className}Metamodel"

        val pkProp = findPrimaryKeyProperty(classDeclaration)
        val isSameMethod = if (pkProp != null) {
            val pkName = pkProp.simpleName.asString()
            """
            |    override fun isSame(a: T & Any, b: T & Any): Boolean {
            |        val ra = getter(a)
            |        val rb = getter(b)
            |        if (ra == null || rb == null) return ra == rb
            |        return ra.$pkName == rb.$pkName
            |    }
            """.trimMargin()
        } else {
            """
            |    override fun isSame(a: T & Any, b: T & Any): Boolean {
            |        return getter(a) == getter(b)
            |    }
            """.trimMargin()
        }

        val containingFile = classDeclaration.containingFile
        val deps = if (containingFile != null) Dependencies(true, containingFile) else Dependencies(false)
        val file = codeGenerator.createNewFile(
            dependencies = deps,
            packageName = packageName,
            fileName = metaClassName
        )
        OutputStreamWriter(file).use { writer ->
            writer.write(
                """
                |package $packageName
                |
                |import st.orm.Metamodel
                |import st.orm.AbstractMetamodel
                |import st.orm.Ref
                |import javax.annotation.processing.Generated
                |
                |@Generated("${this::class.java.name}")
                |class $metaClassName<T>(
                |    path: String,
                |    field: String,
                |    inline: Boolean,
                |    parent: Metamodel<T, *>,
                |    private val getter: (T & Any) -> $className?
                |) : AbstractMetamodel<T, $className, $className>($className::class.java, path, field, inline, parent) {
                |
                |    override fun getValue(record: T & Any): $className? = getter(record)
                |
                |    override fun isIdentical(a: T & Any, b: T & Any): Boolean {
                |        return getter(a) === getter(b)
                |    }
                |
                |$isSameMethod
                |
                |${buildClassFields(classDeclaration, packageName)}
                |    init {
                |        val subPath = if (inline) path else if (field.isEmpty()) path else if (path.isEmpty()) field else "${'$'}path.${'$'}field"
                |        val fieldBase = if (inline) if (field.isEmpty()) "" else "${'$'}field." else ""
                |
                |${initClassFields(classDeclaration, packageName, metaClassName, className)}
                |    }
                |
                |    @Suppress("UNCHECKED_CAST")
                |    constructor() : this(
                |        "", "", false,
                |        Metamodel.root($className::class.java) as Metamodel<T, *>,
                |        { it as $className }
                |    )
                |
                |    constructor(field: String, parent: Metamodel<T, *>, getter: (T & Any) -> $className)
                |        : this("", field, false, parent, getter)
                |
                |    constructor(path: String, field: String, parent: Metamodel<T, *>, getter: (T & Any) -> $className)
                |        : this(path, field, false, parent, getter)
                |}
                """.trimMargin()
            )
        }
    }
}