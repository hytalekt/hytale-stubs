package io.github.hytalekt.stubs.postprocess

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.type.ArrayType
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.PrimitiveType
import com.github.javaparser.ast.type.ReferenceType
import com.github.javaparser.ast.type.Type
import com.github.javaparser.ast.type.WildcardType
import io.github.classgraph.ArrayTypeSignature
import io.github.classgraph.BaseTypeSignature
import io.github.classgraph.ClassRefTypeSignature
import io.github.classgraph.TypeArgument
import io.github.classgraph.TypeSignature
import io.github.classgraph.TypeVariableSignature

/**
 * Converts ClassGraph TypeSignature objects to JavaParser Type nodes.
 *
 * This enables using full generic type information from bytecode signatures
 * in the post-processing phase.
 */
object TypeSignatureRenderer {
    /**
     * Convert a ClassGraph TypeSignature to a JavaParser Type.
     *
     * @param sig The ClassGraph type signature
     * @param substitutions Map of type variable names to their replacements (e.g., "T" -> "DynamicLayerEntry")
     * @param packageName Current package name for resolving simple type names
     * @param cu Compilation unit for import resolution
     */
    fun toJavaParserType(
        sig: TypeSignature,
        substitutions: Map<String, String> = emptyMap(),
        packageName: String = "",
        cu: CompilationUnit? = null,
    ): Type =
        when (sig) {
            is BaseTypeSignature -> toJavaParserTypeFromBase(sig)
            is ClassRefTypeSignature -> toJavaParserTypeFromClassRef(sig, substitutions, packageName, cu)
            is ArrayTypeSignature -> toJavaParserTypeFromArray(sig, substitutions, packageName, cu)
            is TypeVariableSignature -> toJavaParserTypeFromTypeVariable(sig, substitutions, packageName, cu)
            else -> ClassOrInterfaceType(null, "Object") // Fallback
        }

    private fun toJavaParserTypeFromBase(sig: BaseTypeSignature): Type =
        when (sig.type) {
            Boolean::class.javaPrimitiveType -> PrimitiveType.booleanType()
            Byte::class.javaPrimitiveType -> PrimitiveType.byteType()
            Char::class.javaPrimitiveType -> PrimitiveType.charType()
            Short::class.javaPrimitiveType -> PrimitiveType.shortType()
            Int::class.javaPrimitiveType -> PrimitiveType.intType()
            Long::class.javaPrimitiveType -> PrimitiveType.longType()
            Float::class.javaPrimitiveType -> PrimitiveType.floatType()
            Double::class.javaPrimitiveType -> PrimitiveType.doubleType()
            else -> PrimitiveType.intType() // Should not happen
        }

    private fun toJavaParserTypeFromClassRef(
        sig: ClassRefTypeSignature,
        substitutions: Map<String, String>,
        packageName: String,
        cu: CompilationUnit?,
    ): Type {
        val className = sig.fullyQualifiedClassName
        // ClassGraph uses $ for nested classes (e.g., "com.example.Outer$Inner")
        // Convert to Java source format with . (e.g., "Outer.Inner")
        val simpleName = className.substringAfterLast('.').replace('$', '.')
        val typeArgs = sig.typeArguments

        // Create the base type
        val baseType = ClassOrInterfaceType(null, simpleName)

        // If there are type arguments, add them
        if (typeArgs.isNotEmpty()) {
            val javaParserArgs = NodeList(typeArgs.map { toJavaParserTypeFromTypeArgument(it, substitutions, packageName, cu) })
            baseType.setTypeArguments(javaParserArgs)
        }

        return baseType
    }

    private fun toJavaParserTypeFromArray(
        sig: ArrayTypeSignature,
        substitutions: Map<String, String>,
        packageName: String,
        cu: CompilationUnit?,
    ): Type {
        val elementSig = sig.elementTypeSignature
        val dims = sig.numDimensions

        // Get the element type
        var result = toJavaParserType(elementSig, substitutions, packageName, cu)

        // Wrap in array types
        repeat(dims) {
            result = ArrayType(result)
        }

        return result
    }

    private fun toJavaParserTypeFromTypeVariable(
        sig: TypeVariableSignature,
        substitutions: Map<String, String>,
        packageName: String,
        cu: CompilationUnit?,
    ): Type {
        val varName = sig.name

        // Check if we have a substitution for this type variable
        val substitution = substitutions[varName]
        if (substitution != null) {
            // Use the substituted type name
            return ClassOrInterfaceType(null, substitution)
        }

        // Type variables like "T" cannot be directly used in code for null casts
        // Try to resolve to the bound, otherwise fall back to Object
        val resolved =
            try {
                sig.resolve()
            } catch (_: Exception) {
                null
            }

        if (resolved != null) {
            val bounds = resolved.classBound
            if (bounds != null) {
                return toJavaParserType(bounds, substitutions, packageName, cu)
            }
            val interfaceBounds = resolved.interfaceBounds
            if (interfaceBounds.isNotEmpty()) {
                return toJavaParserType(interfaceBounds.first(), substitutions, packageName, cu)
            }
        }

        return ClassOrInterfaceType(null, "Object")
    }

    private fun toJavaParserTypeFromTypeArgument(
        arg: TypeArgument,
        substitutions: Map<String, String>,
        packageName: String,
        cu: CompilationUnit?,
    ): Type {
        val wildcard = arg.wildcard

        return when (wildcard) {
            TypeArgument.Wildcard.NONE -> {
                // Concrete type argument
                val typeSig = arg.typeSignature
                if (typeSig != null) {
                    toJavaParserType(typeSig, substitutions, packageName, cu)
                } else {
                    // Shouldn't happen for NONE wildcard
                    WildcardType()
                }
            }

            TypeArgument.Wildcard.EXTENDS -> {
                // ? extends X
                val typeSig = arg.typeSignature
                if (typeSig != null) {
                    val boundType = toJavaParserType(typeSig, substitutions, packageName, cu)
                    if (boundType is ReferenceType) {
                        WildcardType(boundType)
                    } else {
                        // Primitives can't be wildcard bounds, shouldn't happen
                        WildcardType()
                    }
                } else {
                    WildcardType()
                }
            }

            TypeArgument.Wildcard.SUPER -> {
                // ? super X
                val typeSig = arg.typeSignature
                if (typeSig != null) {
                    val boundType = toJavaParserType(typeSig, substitutions, packageName, cu)
                    if (boundType is ReferenceType) {
                        WildcardType().apply { setSuperType(boundType) }
                    } else {
                        WildcardType()
                    }
                } else {
                    WildcardType()
                }
            }

            TypeArgument.Wildcard.ANY -> {
                // ?
                WildcardType()
            }

            else -> {
                WildcardType()
            }
        }
    }
}
