package io.github.hytalekt.stubs.spec

import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.TypeSpec
import io.github.classgraph.AnnotationInfo
import io.github.classgraph.ClassInfo
import io.github.hytalekt.stubs.util.classModifiersOf
import io.github.hytalekt.stubs.util.parseTypeName
import javax.lang.model.element.Modifier

class AnnotationTypeSpecBuilder(
    private val classInfo: ClassInfo,
) : TypeSpecBuilder {
    override fun build(): TypeSpec {
        require(classInfo.isAnnotation) {
            "Attempted to build an annotation TypeSpec using a non-annotation class"
        }

        val typeSpec = TypeSpec.annotationBuilder(classInfo.simpleName)

        addModifiers(typeSpec)
        addAnnotations(typeSpec)
        addAnnotationMembers(typeSpec)

        return typeSpec.build()
    }

    private fun addModifiers(typeSpec: TypeSpec.Builder) {
        typeSpec.addModifiers(*classModifiersOf(classInfo.modifiers).toTypedArray())
    }

    private fun addAnnotations(typeSpec: TypeSpec.Builder) {
        classInfo.annotationInfo?.forEach { annotationInfo ->
            typeSpec.addAnnotation(buildAnnotationSpec(annotationInfo))
        }
    }

    private fun addAnnotationMembers(typeSpec: TypeSpec.Builder) {
        // Get default values for this annotation's members
        val defaultValues =
            classInfo.annotationDefaultParameterValues
                .associateBy { it.name }

        classInfo.methodInfo
            .filter { !it.isSynthetic }
            .forEach { methodInfo ->
                val returnType =
                    parseTypeName(
                        methodInfo.typeSignatureOrTypeDescriptor.resultType.toString(),
                    )
                val methodSpec =
                    MethodSpec
                        .methodBuilder(methodInfo.name)
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(returnType)

                methodInfo.annotationInfo?.forEach { annotationInfo ->
                    methodSpec.addAnnotation(buildAnnotationSpec(annotationInfo))
                }

                // Add default value if present
                defaultValues[methodInfo.name]?.let { defaultParam ->
                    val defaultValue = formatAnnotationValue(defaultParam.value)
                    if (defaultValue != null) {
                        methodSpec.defaultValue(defaultValue)
                    }
                }

                typeSpec.addMethod(methodSpec.build())
            }
    }
}

/**
 * Builds an AnnotationSpec with parameter values from AnnotationInfo.
 */
internal fun buildAnnotationSpec(annotationInfo: AnnotationInfo): AnnotationSpec {
    val builder = AnnotationSpec.builder(ClassName.bestGuess(annotationInfo.name))

    annotationInfo.parameterValues.forEach { param ->
        val formattedValue = formatAnnotationValue(param.value)
        if (formattedValue != null) {
            builder.addMember(param.name, formattedValue)
        }
    }

    return builder.build()
}

/**
 * Formats an annotation parameter value for code generation.
 */
private fun formatAnnotationValue(value: Any?): CodeBlock? =
    when (value) {
        null -> {
            null
        }

        is String -> {
            CodeBlock.of($$"$S", value)
        }

        is Char -> {
            CodeBlock.of($$"'$L'", value)
        }

        is Boolean, is Byte, is Short, is Int, is Long, is Float, is Double -> {
            CodeBlock.of($$"$L", value)
        }

        is Class<*> -> {
            CodeBlock.of($$"$T.class", value)
        }

        is Enum<*> -> {
            CodeBlock.of($$"$T.$L", value.javaClass, value.name)
        }

        is AnnotationInfo -> {
            // Nested annotation
            val nestedSpec = buildAnnotationSpec(value)
            CodeBlock.of($$"$L", nestedSpec)
        }

        is io.github.classgraph.AnnotationEnumValue -> {
            // Enum reference from ClassGraph
            CodeBlock.of($$"$T.$L", ClassName.bestGuess(value.className), value.valueName)
        }

        is io.github.classgraph.AnnotationClassRef -> {
            // Class reference from ClassGraph
            CodeBlock.of($$"$T.class", ClassName.bestGuess(value.name))
        }

        is Array<*> -> {
            // Array of values
            if (value.isEmpty()) {
                CodeBlock.of("{}")
            } else {
                val elements = value.mapNotNull { formatAnnotationValue(it) }
                if (elements.size == 1) {
                    elements[0]
                } else {
                    CodeBlock
                        .builder()
                        .add("{")
                        .add(elements.joinToString(", ") { $$"$L" }, *elements.toTypedArray())
                        .add("}")
                        .build()
                }
            }
        }

        is List<*> -> {
            // List of values (convert to array handling)
            if (value.isEmpty()) {
                CodeBlock.of("{}")
            } else {
                val elements = value.mapNotNull { formatAnnotationValue(it) }
                if (elements.size == 1) {
                    elements[0]
                } else {
                    CodeBlock
                        .builder()
                        .add("{")
                        .add(elements.joinToString(", ") { $$"$L" }, *elements.toTypedArray())
                        .add("}")
                        .build()
                }
            }
        }

        else -> {
            // Unknown type, try to use toString
            CodeBlock.of($$"$L", value)
        }
    }
