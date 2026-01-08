package io.github.hytalekt.stubs.spec

import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.TypeSpec
import io.github.classgraph.ClassInfo
import io.github.hytalekt.stubs.util.METHOD_BODY
import io.github.hytalekt.stubs.util.buildMethod
import io.github.hytalekt.stubs.util.buildTypeVariable
import io.github.hytalekt.stubs.util.classModifiersOf
import io.github.hytalekt.stubs.util.isSyntheticAccessor
import io.github.hytalekt.stubs.util.parseTypeName
import javax.lang.model.element.Modifier

class RecordTypeSpecBuilder(
    private val classInfo: ClassInfo,
) : TypeSpecBuilder {
    override fun build(): TypeSpec {
        require(classInfo.isRecord) {
            "Attempted to build a record TypeSpec using a non-record class"
        }

        val typeSpec =
            TypeSpec
                .recordBuilder(classInfo.simpleName)
                .addModifiers(Modifier.FINAL)

        addModifiers(typeSpec)
        addTypeParameters(typeSpec)
        addSuperinterfaces(typeSpec)
        addAnnotations(typeSpec)
        addRecordConstructor(typeSpec)
        addMethods(typeSpec)

        return typeSpec.build()
    }

    private fun addModifiers(typeSpec: TypeSpec.Builder) {
        typeSpec.addModifiers(*classModifiersOf(classInfo.modifiers).toTypedArray())
    }

    private fun addTypeParameters(typeSpec: TypeSpec.Builder) {
        classInfo.typeSignature?.typeParameters?.forEach { typeParam ->
            typeSpec.addTypeVariable(buildTypeVariable(typeParam))
        }
    }

    private fun addSuperinterfaces(typeSpec: TypeSpec.Builder) {
        classInfo.interfaces.forEach { interfaceInfo ->
            val interfaceType =
                classInfo.typeSignature
                    ?.superinterfaceSignatures
                    ?.find { it.toString().startsWith(interfaceInfo.name) }
                    ?.toString() ?: interfaceInfo.name
            typeSpec.addSuperinterface(parseTypeName(interfaceType))
        }
    }

    private fun addAnnotations(typeSpec: TypeSpec.Builder) {
        classInfo.annotationInfo?.forEach { annotationInfo ->
            typeSpec.addAnnotation(buildAnnotationSpec(annotationInfo))
        }
    }

    private fun addRecordConstructor(typeSpec: TypeSpec.Builder) {
        // Build record constructor with components as parameters
        // The recordConstructor method defines both the record components and the compact constructor body
        val constructorBuilder =
            MethodSpec
                .compactConstructorBuilder()
                .addStatement(METHOD_BODY)

        // Add visibility modifier to match the record's access level
        val recordModifiers = classModifiersOf(classInfo.modifiers)
        val visibilityModifier =
            recordModifiers.firstOrNull {
                it == Modifier.PUBLIC || it == Modifier.PROTECTED || it == Modifier.PRIVATE
            }
        if (visibilityModifier != null) {
            constructorBuilder.addModifiers(visibilityModifier)
        }

        // Add record components as parameters to the constructor
        classInfo.fieldInfo
            .filter { !it.isSynthetic }
            .forEach { fieldInfo ->
                val componentType =
                    parseTypeName(
                        fieldInfo.typeSignatureOrTypeDescriptor.toString(),
                    )

                // Add component annotations
                val paramBuilder =
                    com.palantir.javapoet.ParameterSpec
                        .builder(componentType, fieldInfo.name)
                fieldInfo.annotationInfo?.forEach { annotationInfo ->
                    paramBuilder.addAnnotation(buildAnnotationSpec(annotationInfo))
                }

                constructorBuilder.addParameter(paramBuilder.build())
            }

        typeSpec.recordConstructor(constructorBuilder.build())
    }

    private fun addMethods(typeSpec: TypeSpec.Builder) {
        classInfo.methodInfo
            .filter { !it.isSynthetic || !isSyntheticAccessor(it) }
            .filter { !it.isBridge } // Filter out bridge methods (type erasure)
            .filter { !it.isConstructor }
            .filter { !isGeneratedRecordAccessor(it) }
            .forEach { methodInfo ->
                typeSpec.addMethod(buildMethod(methodInfo, classInfo))
            }
    }

    private fun isGeneratedRecordAccessor(methodInfo: io.github.classgraph.MethodInfo): Boolean {
        val recordComponentNames =
            classInfo.fieldInfo
                .filter { !it.isSynthetic }
                .map { it.name }
                .toSet()

        return methodInfo.name in recordComponentNames &&
            methodInfo.parameterInfo.isEmpty() &&
            !methodInfo.isStatic
    }
}
