package io.github.hytalekt.stubs.util

import io.github.classgraph.ClassInfo
import io.github.classgraph.MethodInfo
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.lang.reflect.Modifier

/**
 * Extracts method parameter names from bytecode using ASM.
 * Reads LocalVariableTable to get actual parameter names even when
 * code wasn't compiled with -parameters flag.
 */
object ParameterNameExtractor {
    // Cache to avoid re-parsing the same class bytecode multiple times
    private val cache = mutableMapOf<String, Map<String, List<String>>>()

    /**
     * Extract parameter names for a method using ASM LocalVariableTable.
     * Falls back through multiple strategies:
     * 1. ClassGraph parameter names (if compiled with -parameters)
     * 2. ASM LocalVariableTable parsing
     * 3. Generic names: param0, param1, etc.
     *
     * @param classInfo ClassGraph ClassInfo for the containing class
     * @param methodInfo ClassGraph MethodInfo for the target method
     * @param skipSyntheticParams Number of synthetic parameters already skipped (e.g., enum name/ordinal)
     * @return List of parameter names in order
     */
    fun extractParameterNames(
        classInfo: ClassInfo,
        methodInfo: MethodInfo,
        skipSyntheticParams: Int = 0,
    ): List<String> {
        val paramCount = methodInfo.parameterInfo.size - skipSyntheticParams

        // Try ClassGraph first (works if compiled with -parameters flag)
        val classGraphNames =
            methodInfo.parameterInfo.drop(skipSyntheticParams).mapNotNull { it.name }
        if (classGraphNames.size == paramCount) {
            return classGraphNames
        }

        // Try ASM LocalVariableTable parsing
        val asmNames = extractFromBytecode(classInfo, methodInfo, skipSyntheticParams)
        if (asmNames.size == paramCount) {
            return asmNames
        }

        // Fallback to generic names
        return List(paramCount) { "param$it" }
    }

    /**
     * Extract parameter names from bytecode using ASM ClassReader.
     */
    private fun extractFromBytecode(
        classInfo: ClassInfo,
        methodInfo: MethodInfo,
        skipSyntheticParams: Int,
    ): List<String> {
        // Check cache first
        val className = classInfo.name
        val methodKey = buildMethodKey(methodInfo)

        val cachedClass = cache[className]
        if (cachedClass != null) {
            return cachedClass[methodKey] ?: emptyList()
        }

        // Parse bytecode with ASM
        try {
            val methodMap = mutableMapOf<String, List<String>>()
            val bytecode = classInfo.resource.load()

            val classReader = ClassReader(bytecode)
            val visitor = ParameterNameClassVisitor(methodMap, skipSyntheticParams)
            classReader.accept(visitor, 0)

            // Cache results for this class
            cache[className] = methodMap

            return methodMap[methodKey] ?: emptyList()
        } catch (e: Exception) {
            // If bytecode parsing fails, return empty list to trigger fallback
            return emptyList()
        }
    }

    /**
     * Build a unique key for a method based on its signature.
     * Must match ASM's descriptor format: (paramTypes)returnType
     */
    private fun buildMethodKey(methodInfo: MethodInfo): String {
        val name = methodInfo.name
        // Build JVM descriptor format manually
        // Use typeDescriptor (erased) instead of typeSignatureOrTypeDescriptor to match ASM
        val paramDescriptors = methodInfo.parameterInfo.joinToString("") { param ->
            toJvmDescriptor(param.typeDescriptor.toString())
        }
        val returnDescriptor = toJvmDescriptor(
            methodInfo.typeDescriptor.resultType.toString()
        )
        val descriptor = "($paramDescriptors)$returnDescriptor"
        return "$name$descriptor"
    }

    /**
     * Convert a human-readable type name to JVM descriptor format.
     * Examples:
     *   "int" -> "I"
     *   "java.lang.String" -> "Ljava/lang/String;"
     *   "int[]" -> "[I"
     */
    private fun toJvmDescriptor(typeName: String): String {
        val cleanedType = typeName.trim()

        // Handle arrays
        if (cleanedType.endsWith("[]")) {
            val baseType = cleanedType.substringBeforeLast("[]")
            return "[" + toJvmDescriptor(baseType)
        }

        // Handle primitives
        return when (cleanedType) {
            "void" -> "V"
            "boolean" -> "Z"
            "byte" -> "B"
            "char" -> "C"
            "short" -> "S"
            "int" -> "I"
            "long" -> "J"
            "float" -> "F"
            "double" -> "D"
            else -> {
                // Object type: convert dots to slashes and wrap in L...;
                "L${cleanedType.replace('.', '/')};"
            }
        }
    }

    /**
     * ClassVisitor that collects parameter names from all methods in a class.
     */
    private class ParameterNameClassVisitor(
        private val methodMap: MutableMap<String, List<String>>,
        private val skipSyntheticParams: Int,
    ) : ClassVisitor(Opcodes.ASM9) {
        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor {
            return ParameterNameMethodVisitor(
                access,
                name,
                descriptor,
                methodMap,
                skipSyntheticParams,
            )
        }
    }

    /**
     * MethodVisitor that extracts parameter names from LocalVariableTable.
     */
    private class ParameterNameMethodVisitor(
        private val access: Int,
        private val methodName: String,
        private val descriptor: String,
        private val methodMap: MutableMap<String, List<String>>,
        private val skipSyntheticParams: Int,
    ) : MethodVisitor(Opcodes.ASM9) {
        private val localVariables = mutableListOf<LocalVariable>()

        override fun visitLocalVariable(
            name: String,
            descriptor: String,
            signature: String?,
            start: Label,
            end: Label,
            index: Int,
        ) {
            localVariables.add(LocalVariable(name, descriptor, index))
        }

        override fun visitEnd() {
            // Process the LocalVariableTable to extract parameter names
            val parameterNames = extractParameterNamesFromLVT()
            val methodKey = "$methodName$descriptor"
            methodMap[methodKey] = parameterNames
        }

        private fun extractParameterNamesFromLVT(): List<String> {
            if (localVariables.isEmpty()) {
                return emptyList()
            }

            // Sort by LVT slot index
            val sortedVars = localVariables.sortedBy { it.index }

            // Determine starting slot:
            // - Instance methods (non-static): slot 0 is "this", skip it
            // - Static methods: start from slot 0
            val isStatic = Modifier.isStatic(access)
            val startSlot =
                if (isStatic) {
                    skipSyntheticParams
                } else {
                    1 + skipSyntheticParams // Skip "this"
                }

            // Extract parameters starting from the appropriate slot
            val parameters = mutableListOf<String>()
            var currentSlot = startSlot

            for (localVar in sortedVars) {
                if (localVar.index < startSlot) {
                    continue // Skip "this" and synthetic parameters
                }

                if (localVar.index == currentSlot) {
                    parameters.add(localVar.name)

                    // Wide types (long, double) occupy 2 slots
                    currentSlot +=
                        if (isWideType(localVar.descriptor)) {
                            2
                        } else {
                            1
                        }
                } else if (localVar.index > currentSlot) {
                    // We've moved past expected parameters into local variables
                    break
                }
            }

            return parameters
        }

        private fun isWideType(descriptor: String): Boolean =
            descriptor == "J" || descriptor == "D" // long or double

        private data class LocalVariable(
            val name: String,
            val descriptor: String,
            val index: Int,
        )
    }
}
