package st.orm.record

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Enhanced data class detector that checks for copy() and componentN() methods
 */
class DataClassDetector : ClassVisitor(Opcodes.ASM9) {

    private val componentMethods = mutableSetOf<String>()
    private var hasCopyMethod = false

    val isDataClass: Boolean
        get() = hasCopyMethod && componentMethods.isNotEmpty()

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<String>?
    ): MethodVisitor? {
        when {
            name == "copy" && (access and Opcodes.ACC_SYNTHETIC) != 0 -> {
                hasCopyMethod = true
            }
            name.matches(Regex("component\\d+")) -> {
                componentMethods.add(name)
            }
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions)
    }
}