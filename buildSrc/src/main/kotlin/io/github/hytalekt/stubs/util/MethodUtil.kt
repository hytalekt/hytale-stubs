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
    addMethodAnnotations(methodBuilder, methodInfo)
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
) {
    methodInfo.annotationInfo?.forEach { annotationInfo ->
        methodBuilder.addAnnotation(
            buildAnnotationSpec(annotationInfo),
        )
    }
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

    methodInfo.parameterInfo.drop(paramsToSkip).forEachIndexed { index, param ->
        val paramSpec =
            ParameterSpec.builder(
                parseTypeName(param.typeSignatureOrTypeDescriptor.toString()),
                param.name ?: "param$index",
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
        methodBuilder.addStatement(METHOD_BODY)
    }
}
