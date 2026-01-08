package io.github.hytalekt.stubs.util

import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.FieldSpec
import io.github.classgraph.FieldInfo
import io.github.hytalekt.stubs.spec.buildAnnotationSpec
import java.lang.reflect.Modifier

/**
 * Builds a FieldSpec from a FieldInfo, handling annotations and initializers.
 *
 * @param fieldInfo The ClassGraph FieldInfo to build from
 * @param includeInitializer Whether to add default initializers for final fields
 * @param constantValue Optional constant initializer value (for interface constants)
 * @return The built FieldSpec
 */
fun buildField(
    fieldInfo: FieldInfo,
    includeInitializer: Boolean = true,
    constantValue: Any? = null,
): FieldSpec {
    val fieldType = parseTypeName(fieldInfo.typeSignatureOrTypeDescriptor.toString())
    val fieldSpec =
        FieldSpec.builder(
            fieldType,
            fieldInfo.name,
            *methodModifiersOf(fieldInfo.modifiers).toTypedArray(),
        )

    // Add annotations
    fieldInfo.annotationInfo?.forEach { annotationInfo ->
        fieldSpec.addAnnotation(buildAnnotationSpec(annotationInfo))
    }

    // Add initializer if requested
    if (includeInitializer) {
        // For interface constants, use the constant value if available
        if (constantValue != null) {
            val initializer = formatFieldInitializer(constantValue)
            if (initializer != null) {
                fieldSpec.initializer(initializer)
            }
        } else if (Modifier.isFinal(fieldInfo.modifiers)) {
            // For final fields without a constant value, use default initializer
            val initializer = getDefaultInitializer(fieldInfo.typeSignatureOrTypeDescriptor.toString())
            fieldSpec.initializer(initializer)
        }
    }

    return fieldSpec.build()
}

/**
 * Gets the default initializer string for a given type.
 * Used for final fields that require initialization.
 */
fun getDefaultInitializer(typeName: String): String =
    when (typeName) {
        "boolean", "Z" -> "false"
        "byte", "B" -> "(byte) 0"
        "short", "S" -> "(short) 0"
        "int", "I" -> "0"
        "long", "J" -> "0L"
        "float", "F" -> "0.0f"
        "double", "D" -> "0.0"
        "char", "C" -> "'\\u0000'"
        else -> "null"
    }

/**
 * Formats a field constant initializer value for code generation.
 * Used for interface constants and other compile-time constants.
 */
fun formatFieldInitializer(value: Any?): CodeBlock? =
    when (value) {
        null -> null
        is String -> CodeBlock.of($$"$S", value)
        is Char -> CodeBlock.of($$"'$L'", value)
        is Boolean, is Byte, is Short, is Int, is Long -> CodeBlock.of($$"$L", value)
        is Float -> CodeBlock.of($$"$Lf", value)
        is Double -> CodeBlock.of($$"$L", value)
        else -> CodeBlock.of($$"$L", value)
    }
