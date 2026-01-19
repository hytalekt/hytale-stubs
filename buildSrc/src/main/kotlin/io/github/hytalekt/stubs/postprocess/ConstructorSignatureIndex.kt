package io.github.hytalekt.stubs.postprocess

import io.github.classgraph.ClassGraph
import io.github.classgraph.TypeSignature
import java.io.File

/**
 * Index of constructor signatures with full generic type information.
 *
 * Uses ClassGraph to pre-scan a JAR file and extract constructor signatures
 * including generic type parameters that are erased in bytecode descriptors.
 */
class ConstructorSignatureIndex(
    jarFile: File,
) {
    // Map: "com.example.MyClass" -> Map<descriptor, List<TypeSignature>>
    private val constructorSignatures: Map<String, Map<String, List<TypeSignature>>>

    // Map: "com.example.MyClass" -> List of type parameter names (e.g., ["T", "U"])
    private val classTypeParameters: Map<String, List<String>>

    init {
        val (ctors, typeParams) = buildIndex(jarFile)
        constructorSignatures = ctors
        classTypeParameters = typeParams
    }

    private fun buildIndex(jarFile: File): Pair<Map<String, Map<String, List<TypeSignature>>>, Map<String, List<String>>> {
        val ctorMap = mutableMapOf<String, Map<String, List<TypeSignature>>>()
        val typeParamMap = mutableMapOf<String, List<String>>()

        ClassGraph()
            .enableAllInfo()
            .overrideClasspath(jarFile.absolutePath)
            .scan()
            .use { result ->
                result.allClasses.forEach { classInfo ->
                    val qualifiedName = classInfo.name // e.g., "com.example.MyClass"

                    // Extract type parameters
                    val typeParams = classInfo.typeSignature?.typeParameters?.map { it.name } ?: emptyList()
                    if (typeParams.isNotEmpty()) {
                        typeParamMap[qualifiedName] = typeParams
                    }

                    // Extract constructor signatures
                    val ctorSignatures =
                        classInfo.declaredConstructorInfo.associate { ctor ->
                            val descriptor = ctor.typeDescriptorStr
                            val paramTypes =
                                ctor.parameterInfo.map { param ->
                                    param.typeSignatureOrTypeDescriptor
                                }
                            descriptor to paramTypes
                        }
                    ctorMap[qualifiedName] = ctorSignatures
                }
            }

        return ctorMap to typeParamMap
    }

    /**
     * Get the parameter types for a constructor.
     *
     * @param className Fully qualified class name (e.g., "com.example.MyClass")
     * @param descriptor Method descriptor (e.g., "(Ljava/lang/String;I)V")
     * @return List of TypeSignature for each parameter, or null if not found
     */
    fun getParameterTypes(
        className: String,
        descriptor: String,
    ): List<TypeSignature>? = constructorSignatures[className]?.get(descriptor)

    /**
     * Get all constructor signatures for a class.
     *
     * @param className Fully qualified class name (e.g., "com.example.MyClass")
     * @return Map of descriptor to parameter types, or null if class not found
     */
    fun getConstructors(className: String): Map<String, List<TypeSignature>>? = constructorSignatures[className]

    /**
     * Get the type parameter names for a class.
     *
     * @param className Fully qualified class name (e.g., "com.example.MyClass")
     * @return List of type parameter names (e.g., ["T", "U"]), or null if none
     */
    fun getTypeParameters(className: String): List<String>? = classTypeParameters[className]
}
