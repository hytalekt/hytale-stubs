package io.github.hytalekt.stubs.util

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterSpec
import io.github.classgraph.ClassInfo
import io.github.classgraph.MethodInfo
import io.github.hytalekt.stubs.spec.buildAnnotationSpec
import java.lang.reflect.Modifier
import javax.lang.model.element.Modifier as JaxModifier

val METHOD_BODY: CodeBlock = CodeBlock.of($$"throw new $T()", STUB_EXCEPTION_CLASS)

fun methodModifiersOf(modifiers: Int): List<JaxModifier> =
    listOfNotNull(
        JaxModifier.PUBLIC.takeIf { Modifier.isPublic(modifiers) },
        JaxModifier.PRIVATE.takeIf { Modifier.isPrivate(modifiers) },
        JaxModifier.PROTECTED.takeIf { Modifier.isProtected(modifiers) },
        JaxModifier.STATIC.takeIf { Modifier.isStatic(modifiers) },
        JaxModifier.FINAL.takeIf { Modifier.isFinal(modifiers) },
        JaxModifier.VOLATILE.takeIf { Modifier.isVolatile(modifiers) },
        JaxModifier.SYNCHRONIZED.takeIf { Modifier.isSynchronized(modifiers) },
        JaxModifier.TRANSIENT.takeIf { Modifier.isTransient(modifiers) },
        JaxModifier.NATIVE.takeIf { Modifier.isNative(modifiers) },
        JaxModifier.ABSTRACT.takeIf { Modifier.isAbstract(modifiers) },
        JaxModifier.STRICTFP.takeIf { Modifier.isStrict(modifiers) },
    )

fun buildMethod(
    methodInfo: MethodInfo,
    clazz: ClassInfo,
): MethodSpec {
    val methodBuilder =
        if (methodInfo.isConstructor) {
            MethodSpec.constructorBuilder()
        } else {
            MethodSpec.methodBuilder(methodInfo.name)
        }

    addMethodModifiers(methodBuilder, methodInfo, clazz)
    addMethodTypeParameters(methodBuilder, methodInfo)
    addMethodAnnotations(methodBuilder, methodInfo, clazz)
    addMethodReturnType(methodBuilder, methodInfo)
    addMethodParameters(methodBuilder, methodInfo, clazz)
    addMethodExceptions(methodBuilder, methodInfo)
    addMethodBody(methodBuilder, methodInfo, clazz)

    return methodBuilder.build()
}

private fun addMethodModifiers(
    methodBuilder: MethodSpec.Builder,
    methodInfo: MethodInfo,
    clazz: ClassInfo,
) {
    val modifiers = methodModifiersOf(methodInfo.modifiers).toMutableList()

    if (clazz.isInterface && methodInfo.isDefault) {
        modifiers.add(JaxModifier.DEFAULT)
    }

    methodBuilder.addModifiers(*modifiers.toTypedArray())
}

private fun addMethodTypeParameters(
    methodBuilder: MethodSpec.Builder,
    methodInfo: MethodInfo,
) {
    methodInfo.typeSignature?.typeParameters?.forEach { typeParam ->
        methodBuilder.addTypeVariable(buildTypeVariable(typeParam))
    }
}

private fun addMethodAnnotations(
    methodBuilder: MethodSpec.Builder,
    methodInfo: MethodInfo,
    clazz: ClassInfo,
) {
    // Add @Override if method overrides a superclass or interface method
    if (shouldAddOverrideAnnotation(methodInfo, clazz)) {
        methodBuilder.addAnnotation(Override::class.java)
    }

    methodInfo.annotationInfo?.forEach { annotationInfo ->
        methodBuilder.addAnnotation(
            buildAnnotationSpec(annotationInfo),
        )
    }
}

private fun shouldAddOverrideAnnotation(
    methodInfo: MethodInfo,
    clazz: ClassInfo,
): Boolean {
    // Don't add @Override to constructors, static methods, or private methods
    if (methodInfo.isConstructor ||
        Modifier.isStatic(methodInfo.modifiers) ||
        Modifier.isPrivate(methodInfo.modifiers)
    ) {
        return false
    }

    // Check if class has a non-Object superclass or implements any interface
    val hasSuperclass = clazz.superclasses?.any { it.name != "java.lang.Object" } == true
    val hasInterfaces = clazz.interfaces.isNotEmpty()

    if (!hasSuperclass && !hasInterfaces) {
        return false
    }

    // Check if any superclass or interface has a method with the same name
    // This is a simplified check - ideally we'd match full signatures
    val methodName = methodInfo.name
    val paramCount = methodInfo.parameterInfo.size

    // Check superclasses
    clazz.superclasses?.forEach { superclass ->
        if (superclass.name != "java.lang.Object") {
            val hasMatchingMethod =
                superclass.methodInfo.any {
                    it.name == methodName && it.parameterInfo.size == paramCount && !it.isPrivate
                }
            if (hasMatchingMethod) return true
        }
    }

    // Check interfaces (including inherited ones)
    clazz.interfaces.forEach { interfaceInfo ->
        val hasMatchingMethod =
            interfaceInfo.methodInfo.any {
                it.name == methodName && it.parameterInfo.size == paramCount
            }
        if (hasMatchingMethod) return true

        // Check parent interfaces recursively
        interfaceInfo.interfaces.forEach { parentInterface ->
            val hasMatchingInParent =
                parentInterface.methodInfo.any {
                    it.name == methodName && it.parameterInfo.size == paramCount
                }
            if (hasMatchingInParent) return true
        }
    }

    return false
}

private fun addMethodReturnType(
    methodBuilder: MethodSpec.Builder,
    methodInfo: MethodInfo,
) {
    if (methodInfo.isConstructor) return
    val returnType = methodInfo.typeSignatureOrTypeDescriptor.resultType.toString()
    methodBuilder.returns(parseTypeName(returnType))
}

private fun addMethodParameters(
    methodBuilder: MethodSpec.Builder,
    methodInfo: MethodInfo,
    clazz: ClassInfo,
) {
    // Skip synthetic parameters:
    // - Enum constructors: first two params are (String name, int ordinal)
    // - Non-static inner class constructors: first param is outer class reference (this$0)
    val paramsToSkip =
        when {
            clazz.isEnum && methodInfo.isConstructor -> 2
            clazz.isInnerClass && !Modifier.isStatic(clazz.modifiers) && methodInfo.isConstructor -> 1
            else -> 0
        }

    // Extract parameter names using ASM LocalVariableTable
    val parameterNames =
        ParameterNameExtractor.extractParameterNames(
            clazz,
            methodInfo,
            paramsToSkip,
        )

    methodInfo.parameterInfo.drop(paramsToSkip).forEachIndexed { index, param ->
        val paramSpec =
            ParameterSpec.builder(
                parseTypeName(param.typeSignatureOrTypeDescriptor.toString()),
                parameterNames.getOrElse(index) { "param$index" },
            )

        param.annotationInfo?.forEach { annotationInfo ->
            paramSpec.addAnnotation(
                buildAnnotationSpec(annotationInfo),
            )
        }

        methodBuilder.addParameter(paramSpec.build())
    }
}

private fun addMethodExceptions(
    methodBuilder: MethodSpec.Builder,
    methodInfo: MethodInfo,
) {
    // Get thrown exception names directly from the method info
    methodInfo.thrownExceptionNames?.forEach { exceptionName ->
        methodBuilder.addException(ClassName.bestGuess(exceptionName))
    }
}

private fun addMethodBody(
    methodBuilder: MethodSpec.Builder,
    methodInfo: MethodInfo,
    clazz: ClassInfo,
) {
    val isAbstract = methodModifiersOf(methodInfo.modifiers).contains(JaxModifier.ABSTRACT)
    val isInterfaceDefaultMethod = clazz.isInterface && methodInfo.isDefault
    val needsBody = !isAbstract && !clazz.isInterface && !clazz.isAnnotation

    if (needsBody || isInterfaceDefaultMethod) {
        // For constructors, add super() call if class has a non-Object superclass
        if (methodInfo.isConstructor) {
            val superclass = clazz.superclasses?.firstOrNull { it.name != "java.lang.Object" }
            if (superclass != null) {
                addSuperConstructorCall(methodBuilder, superclass)
            }
        }
        methodBuilder.addStatement(METHOD_BODY)
    }
}

private fun addSuperConstructorCall(
    methodBuilder: MethodSpec.Builder,
    superclass: ClassInfo,
) {
    // Find a constructor in the superclass, preferring no-arg constructor
    val superConstructor =
        superclass.constructorInfo
            .filter { !it.isSynthetic }
            .minByOrNull { it.parameterInfo.size }

    if (superConstructor != null) {
        // Skip synthetic parameters for enums and inner classes
        val paramsToSkip =
            when {
                superclass.isEnum -> 2
                superclass.isInnerClass && !Modifier.isStatic(superclass.modifiers) -> 1
                else -> 0
            }

        val params = superConstructor.parameterInfo.drop(paramsToSkip)

        if (params.isEmpty()) {
            methodBuilder.addStatement("super()")
        } else {
            // Generate default values for parameters
            val paramValues =
                params.joinToString(", ") { param ->
                    val typeName = param.typeSignatureOrTypeDescriptor.toString()
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
                }
            methodBuilder.addStatement("super($paramValues)")
        }
    }
}
