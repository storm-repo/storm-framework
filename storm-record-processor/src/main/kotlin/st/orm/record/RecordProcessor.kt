package st.orm.record

import org.objectweb.asm.*
import java.io.File

class RecordProcessor(
    private val logger: ((String) -> Unit)? = null
) {

    // Hardcoded list of Storm interfaces to check
    private val stormInterfaces = setOf(
        "st/orm/Data",
        "st/orm/Entity",
        "st/orm/Projection"
    )

    private val jvmRecordAnnotationDescriptor = "Lkotlin/jvm/JvmRecord;"

    private val classInfoMap = mutableMapOf<String, ClassInfo>()
    private val classesToTransform = mutableSetOf<String>()

    private fun log(message: String) {
        logger?.invoke(message) ?: println(message)
    }

    fun processDirectory(outputDir: File, verbose: Boolean = false, debug: Boolean = false): ProcessResult {
        classInfoMap.clear()
        classesToTransform.clear()
        val allClassFiles = outputDir.walkTopDown()
            .filter { it.extension == "class" }
            .toList()
        if (verbose) log("Storm: Analyzing Data classes.")
        // Analyze all classes.
        allClassFiles.forEach { analyzeClass(it) }
        if (debug) {
            val stormImplementors = classInfoMap.values.filter { it.implementsStorm }
            stormImplementors.forEach {
                log("  - ${it.internalName} (isDataClass=${it.isDataClass}, isValid=${it.isValidForRecord}, isRecord=${it.isAlreadyRecord}, components=${it.recordComponents.size})")
            }
            val dataClasses = classInfoMap.values.filter { it.isDataClass }
            log("Storm: Data classes found: ${dataClasses.size}")
            dataClasses.forEach {
                log("  - ${it.internalName} (implementsStorm=${it.implementsStorm}, isValid=${it.isValidForRecord}, isRecord=${it.isAlreadyRecord})")
            }
        }
        // Build transformation list.
        buildTransformationList()
        if (debug) {
            log("Storm: Classes to transform: ${classesToTransform.size}")
            classesToTransform.forEach { log("  - $it") }
        }
        // Transform classes.
        var transformedCount = 0
        var skippedCount = 0
        val transformedClasses = mutableListOf<String>()

        classesToTransform.forEach { internalName ->
            val classFile = findClassFile(outputDir, internalName)
            if (classFile.exists()) {
                val classInfo = classInfoMap[internalName]
                if (classInfo != null && transformClass(classFile, classInfo, verbose)) {
                    transformedCount++
                    transformedClasses.add(internalName.replace('/', '.'))
                } else {
                    skippedCount++
                    if (verbose) log("Storm: ✗ Skipped $internalName")
                }
            }
        }

        return ProcessResult(
            totalScanned = allClassFiles.size,
            transformed = transformedCount,
            skipped = skippedCount,
            transformedClasses = transformedClasses
        )
    }

    private fun analyzeClass(classFile: File) {
        try {
            val reader = ClassReader(classFile.readBytes())
            val analyzer = ClassAnalyzer()
            reader.accept(analyzer, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            analyzer.classInfo?.let { info ->
                classInfoMap[info.internalName] = info
            }
        } catch (_: Exception) {
            // Skip problematic classes silently.
        }
    }

    private fun buildTransformationList() {
        // Find all classes that implement Storm interfaces.
        val stormImplementors = classInfoMap.values
            .filter { it.implementsStorm }
            .map { it.internalName }

        // BFS to find all data classes in the object graph.
        val queue = ArrayDeque(stormImplementors)
        val visited = mutableSetOf<String>()

        while (queue.isNotEmpty()) {
            val className = queue.removeFirst()
            if (className in visited) continue
            visited.add(className)
            val classInfo = classInfoMap[className] ?: continue
            // Only transform if it's a valid data class that isn't already a record.
            if (classInfo.isDataClass &&
                !classInfo.isAlreadyRecord &&
                classInfo.isValidForRecord &&
                classInfo.recordComponents.isNotEmpty()) {
                classesToTransform.add(className)
            }
            // Add referenced data classes to queue.
            classInfo.referencedClasses
                .filter { refClass -> classInfoMap[refClass]?.isDataClass == true }
                .forEach { queue.add(it) }
        }
    }

    private fun transformClass(classFile: File, classInfo: ClassInfo, verbose: Boolean): Boolean {
        try {
            val originalBytes = classFile.readBytes()
            val reader = ClassReader(originalBytes)
            // Check Java version.
            val version = (originalBytes[6].toInt() shl 8) or (originalBytes[7].toInt() and 0xFF)
            if (version < Opcodes.V16) {
                if (verbose) {
                    log("Storm: Skipping ${classInfo.internalName} - requires Java 16+")
                }
                return false
            }
            val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
            val transformer = RecordTransformer(writer, classInfo)
            reader.accept(transformer, 0)
            val newBytes = writer.toByteArray()
            classFile.writeBytes(newBytes)
            return true
        } catch (e: Exception) {
            log("Storm: Error transforming ${classFile.name}: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    private fun findClassFile(outputDir: File, internalName: String): File {
        return File(outputDir, "$internalName.class")
    }

    data class ClassInfo(
        val internalName: String,
        val isDataClass: Boolean,
        val implementsStorm: Boolean,
        val isAlreadyRecord: Boolean,
        val isValidForRecord: Boolean,
        val referencedClasses: Set<String>,
        val recordComponents: List<RecordComponent>
    )

    data class RecordComponent(
        val name: String,
        val descriptor: String,
        val signature: String?
    )

    private inner class ClassAnalyzer : ClassVisitor(Opcodes.ASM9) {
        var classInfo: ClassInfo? = null

        private var currentClassName = ""
        private var isDataClass = false
        private var implementsStorm = false
        private var isAlreadyRecord = false
        private var isValidForRecord = true
        private val referencedClasses = mutableSetOf<String>()
        private val recordComponents = mutableListOf<RecordComponent>()
        private var isInterface = false
        private var isAbstract = false

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<String>?
        ) {
            currentClassName = name
            isInterface = (access and Opcodes.ACC_INTERFACE) != 0
            isAbstract = (access and Opcodes.ACC_ABSTRACT) != 0
            // Check if already a record (either extends java.lang.Record or has @JvmRecord).
            if (superName == "java/lang/Record") {
                isAlreadyRecord = true
            }
            // Check if implements any Storm interface directly.
            if (interfaces != null) {
                implementsStorm = interfaces.any { it in stormInterfaces }
            }
            // Check superclass for Storm interfaces.
            if (superName != null && superName != "java/lang/Object" && superName != "java/lang/Record") {
                val superInfo = classInfoMap[superName]
                if (superInfo?.implementsStorm == true) {
                    implementsStorm = true
                }
            }
            super.visit(version, access, name, signature, superName, interfaces)
        }

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            when (descriptor) {
                "Lkotlin/Metadata;" -> {
                    return KotlinMetadataAnalyzer { isData ->
                        isDataClass = isData
                    }
                }
                jvmRecordAnnotationDescriptor -> {
                    isAlreadyRecord = true
                }
            }
            return super.visitAnnotation(descriptor, visible)
        }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?
        ): FieldVisitor? {
            // Check for var properties (mutable = not valid for record).
            if ((access and Opcodes.ACC_FINAL) == 0 &&
                !name.startsWith("$") &&
                (access and Opcodes.ACC_STATIC) == 0) {
                isValidForRecord = false
            }
            // Collect val properties as potential record components.
            if ((access and Opcodes.ACC_FINAL) != 0 &&
                (access and Opcodes.ACC_PRIVATE) != 0 &&
                !name.startsWith("$") &&
                (access and Opcodes.ACC_STATIC) == 0) {
                recordComponents.add(RecordComponent(name, descriptor, signature))
            }
            extractReferencedClass(descriptor)?.let { referencedClasses.add(it) }
            return super.visitField(access, name, descriptor, signature, value)
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<String>?
        ): MethodVisitor? {
            // Detect data class by presence of copy() and componentN() methods
            when {
                name == "copy" && (access and Opcodes.ACC_SYNTHETIC) != 0 -> {
                    isDataClass = true
                }
                name.matches(Regex("component\\d+")) -> {
                    isDataClass = true
                }
            }
            // Track referenced classes from method signatures
            Type.getArgumentTypes(descriptor).forEach { type ->
                extractReferencedClass(type.descriptor)?.let { referencedClasses.add(it) }
            }
            extractReferencedClass(Type.getReturnType(descriptor).descriptor)?.let {
                referencedClasses.add(it)
            }
            return super.visitMethod(access, name, descriptor, signature, exceptions)
        }

        override fun visitEnd() {
            // Interfaces and abstract classes can't be records.
            if (isInterface || isAbstract) {
                isValidForRecord = false
            }
            classInfo = ClassInfo(
                internalName = currentClassName,
                isDataClass = isDataClass,
                implementsStorm = implementsStorm,
                isAlreadyRecord = isAlreadyRecord,
                isValidForRecord = isValidForRecord && isDataClass,
                referencedClasses = referencedClasses,
                recordComponents = recordComponents  // Just use the fields we collected
            )
            super.visitEnd()
        }

        private fun extractReferencedClass(descriptor: String): String? {
            return when {
                descriptor.startsWith("L") && descriptor.endsWith(";") -> {
                    descriptor.substring(1, descriptor.length - 1)
                }
                descriptor.startsWith("[L") && descriptor.endsWith(";") -> {
                    descriptor.substring(2, descriptor.length - 1)
                }
                else -> null
            }
        }
    }

    private class KotlinMetadataAnalyzer(
        private val onComplete: (Boolean) -> Unit
    ) : AnnotationVisitor(Opcodes.ASM9) {

        private var kind: Int? = null

        override fun visit(name: String?, value: Any?) {
            when (name) {
                "k" -> kind = value as? Int
            }
            super.visit(name, value)
        }

        override fun visitEnd() {
            val isClass = kind == 1
            onComplete(isClass)
            super.visitEnd()
        }
    }

    private inner class RecordTransformer(
        cv: ClassVisitor,
        private val classInfo: ClassInfo
    ) : ClassVisitor(Opcodes.ASM9, cv) {

        private var originalSuperName: String? = null
        private val existingMethods = mutableSetOf<String>()

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<String>?
        ) {
            originalSuperName = superName
            // Change superclass to java/lang/Record and make it final.
            super.visit(
                version,
                access or Opcodes.ACC_FINAL,
                name,
                signature,
                "java/lang/Record",
                interfaces
            )
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<String>?
        ): MethodVisitor? {
            existingMethods.add(name + descriptor)
            val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
            // Fix constructor to call Record.<init>() instead of original super.
            if (name == "<init>" && mv != null) {
                return RecordConstructorAdapter(mv, originalSuperName ?: "java/lang/Object")
            }
            return mv
        }

        override fun visitEnd() {
            // Add @JvmRecord annotation.
            val av = cv.visitAnnotation(jvmRecordAnnotationDescriptor, true)
            av?.visitEnd()
            // Add Record components.
            classInfo.recordComponents.forEach { component ->
                val rcv = cv.visitRecordComponent(
                    component.name,
                    component.descriptor,
                    component.signature
                )
                rcv?.visitEnd()
                // Add record accessor method if it doesn't exist.
                // Record accessors are named exactly like the field (e.g., id(), not getId()).
                val accessorDescriptor = "()" + component.descriptor
                val accessorSignature = component.name + accessorDescriptor
                if (accessorSignature !in existingMethods) {
                    generateRecordAccessor(component)
                }
            }
            super.visitEnd()
        }

        private fun generateRecordAccessor(component: RecordComponent) {
            val mv = cv.visitMethod(
                Opcodes.ACC_PUBLIC,
                component.name,  // Method name is just the field name
                "()" + component.descriptor,  // Returns the field type
                component.signature?.let { "()$it" },
                null
            )
            mv.visitCode()
            // Load 'this'.
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            // Get the private field.
            mv.visitFieldInsn(
                Opcodes.GETFIELD,
                classInfo.internalName,
                component.name,
                component.descriptor
            )
            // Return appropriate type.
            val returnOpcode = when (component.descriptor) {
                "J" -> Opcodes.LRETURN  // long
                "D" -> Opcodes.DRETURN  // double
                "F" -> Opcodes.FRETURN  // float
                "I", "Z", "B", "C", "S" -> Opcodes.IRETURN  // int, boolean, byte, char, short
                else -> Opcodes.ARETURN  // objects
            }
            mv.visitInsn(returnOpcode)
            mv.visitMaxs(0, 0)  // Auto-computed with COMPUTE_MAXS.
            mv.visitEnd()
        }
    }

    private class RecordConstructorAdapter(
        mv: MethodVisitor,
        private val originalSuperName: String
    ) : MethodVisitor(Opcodes.ASM9, mv) {

        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
            isInterface: Boolean
        ) {
            // Replace super() call to original parent with java/lang/Record.
            if (opcode == Opcodes.INVOKESPECIAL &&
                name == "<init>" &&
                owner == originalSuperName) {
                super.visitMethodInsn(
                    opcode,
                    "java/lang/Record",
                    name,
                    "()V",
                    false
                )
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
            }
        }
    }
}

data class ProcessResult(
    val totalScanned: Int,
    val transformed: Int,
    val skipped: Int,
    val transformedClasses: List<String>
) {
    override fun toString(): String {
        return "Scanned $totalScanned classes, transformed $transformed, skipped $skipped"
    }
}