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
                .classBuilder(classInfo.simpleName)
                .addModifiers(Modifier.FINAL)

        addModifiers(typeSpec)
        addTypeParameters(typeSpec)
        addSuperinterfaces(typeSpec)
        addAnnotations(typeSpec)
        addRecordComponents(typeSpec)
        addCanonicalConstructor(typeSpec)
        addAccessorMethods(typeSpec)
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

    private fun addRecordComponents(typeSpec: TypeSpec.Builder) {
        classInfo.fieldInfo
            .filter { !it.isSynthetic }
            .forEach { fieldInfo ->
                val componentType =
                    parseTypeName(
                        fieldInfo.typeSignatureOrTypeDescriptor.toString(),
                    )
                val fieldSpec =
                    com.palantir.javapoet.FieldSpec.builder(
                        componentType,
                        fieldInfo.name,
                        Modifier.PRIVATE,
                        Modifier.FINAL,
                    )

                fieldInfo.annotationInfo?.forEach { annotationInfo ->
                    fieldSpec.addAnnotation(buildAnnotationSpec(annotationInfo))
                }

                typeSpec.addField(fieldSpec.build())
            }
    }

    private fun addCanonicalConstructor(typeSpec: TypeSpec.Builder) {
        // Build canonical constructor from record components (fields)
        val constructorBuilder =
            MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)

        classInfo.fieldInfo
            .filter { !it.isSynthetic }
            .forEach { fieldInfo ->
                val componentType =
                    parseTypeName(
                        fieldInfo.typeSignatureOrTypeDescriptor.toString(),
                    )
                constructorBuilder.addParameter(componentType, fieldInfo.name)
            }

        constructorBuilder.addStatement(METHOD_BODY)
        typeSpec.addMethod(constructorBuilder.build())
    }

    private fun addAccessorMethods(typeSpec: TypeSpec.Builder) {
        // Add accessor methods for each record component
        classInfo.fieldInfo
            .filter { !it.isSynthetic }
            .forEach { fieldInfo ->
                val componentType =
                    parseTypeName(
                        fieldInfo.typeSignatureOrTypeDescriptor.toString(),
                    )
                val accessorBuilder =
                    MethodSpec
                        .methodBuilder(fieldInfo.name)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(componentType)
                        .addStatement(METHOD_BODY)

                typeSpec.addMethod(accessorBuilder.build())
            }
    }

    private fun addMethods(typeSpec: TypeSpec.Builder) {
        classInfo.methodInfo
            .filter { !it.isSynthetic || !isSyntheticAccessor(it) }
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
