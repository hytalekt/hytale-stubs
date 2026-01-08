package io.github.hytalekt.stubs.spec

import com.palantir.javapoet.TypeSpec
import io.github.classgraph.ClassInfo
import io.github.hytalekt.stubs.util.buildField
import io.github.hytalekt.stubs.util.buildMethod
import io.github.hytalekt.stubs.util.buildTypeVariable
import io.github.hytalekt.stubs.util.classModifiersOf
import io.github.hytalekt.stubs.util.isSyntheticAccessor
import io.github.hytalekt.stubs.util.parseTypeName

class InterfaceTypeSpecBuilder(
    private val classInfo: ClassInfo,
) : TypeSpecBuilder {
    override fun build(): TypeSpec {
        require(classInfo.isInterface && !classInfo.isAnnotation) {
            "Attempted to build an interface TypeSpec using a non-interface class"
        }

        val typeSpec = TypeSpec.interfaceBuilder(classInfo.simpleName)

        addModifiers(typeSpec)
        addTypeParameters(typeSpec)
        addSuperinterfaces(typeSpec)
        addAnnotations(typeSpec)
        addFields(typeSpec)
        addMethods(typeSpec)
        addInnerClasses(typeSpec)

        return typeSpec.build()
    }

    private fun addModifiers(typeSpec: TypeSpec.Builder) {
        // Interfaces are implicitly abstract, so we filter out the ABSTRACT modifier
        val modifiers =
            classModifiersOf(classInfo.modifiers)
                .filter { it != javax.lang.model.element.Modifier.ABSTRACT }
        typeSpec.addModifiers(*modifiers.toTypedArray())
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

    private fun addFields(typeSpec: TypeSpec.Builder) {
        classInfo.fieldInfo
            .filter { !it.isSynthetic }
            .forEach { fieldInfo ->
                typeSpec.addField(
                    buildField(
                        fieldInfo,
                        includeInitializer = true,
                        constantValue = fieldInfo.constantInitializerValue,
                    ),
                )
            }
    }

    private fun addMethods(typeSpec: TypeSpec.Builder) {
        classInfo.methodInfo
            .filter { !it.isSynthetic || !isSyntheticAccessor(it) }
            .filter { !it.isBridge } // Filter out bridge methods (type erasure)
            .forEach { methodInfo ->
                typeSpec.addMethod(buildMethod(methodInfo, classInfo))
            }
    }

    private fun addInnerClasses(typeSpec: TypeSpec.Builder) {
        classInfo.innerClasses
            .filter { !it.isAnonymousInnerClass && !it.isSynthetic }
            // Only include direct children - filter out nested inner classes
            .filter { innerClassInfo ->
                val outerClass = innerClassInfo.outerClasses?.firstOrNull()
                outerClass?.name == classInfo.name
            }.forEach { innerClassInfo ->
                val innerTypeSpec = buildInnerClassTypeSpec(innerClassInfo)
                typeSpec.addType(innerTypeSpec)
            }
    }
}
