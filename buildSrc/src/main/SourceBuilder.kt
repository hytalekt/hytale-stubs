@file:Suppress("ktlint:standard:no-wildcard-imports")

package io.github.hytalekt.stubs.jar

import com.palantir.javapoet.*
import io.github.classgraph.ClassInfo
import io.github.classgraph.MethodInfo
import io.github.classgraph.TypeParameter
import io.github.hytalekt.stubs.util.modifiersOf
import java.lang.reflect.Modifier
import javax.lang.model.element.Modifier as JaxModifier

class SourceBuilder(
    val packageName: String,
    val classInfo: ClassInfo,
) {
    companion object {
        private val OBJECT_TYPE: ClassName = ClassName.get("java.lang", "Object")
        private val STUB_EXCEPTION: ClassName =
            ClassName.get("io.github.kytale.stubs", "GeneratedStubException")
    }

    fun createJavaFile(): JavaFile = JavaFile.builder(packageName, buildTypeSpec(classInfo)).build()

    // ==================== Type Building ====================

    private fun buildTypeSpec(clazz: ClassInfo): TypeSpec {
        val typeSpec = createTypeSpecBuilder(clazz)

        addModifiers(typeSpec, clazz)
        addTypeParameters(typeSpec, clazz)
        addAnnotations(typeSpec, clazz)
        addSuperclass(typeSpec, clazz)
        addInterfaces(typeSpec, clazz)
        addEnumConstants(typeSpec, clazz)
        addFields(typeSpec, clazz)
        addMethods(typeSpec, clazz)
        addInnerClasses(typeSpec, clazz)

        return typeSpec.build()
    }

    private fun createTypeSpecBuilder(clazz: ClassInfo): TypeSpec.Builder =
        when {
            clazz.isEnum -> {
                TypeSpec.enumBuilder(clazz.simpleName)
            }

            clazz.isRecord -> {
                TypeSpec.recordBuilder(clazz.simpleName)
            }

            clazz.isAnnotation -> {
                TypeSpec.annotationBuilder(clazz.simpleName)
            }

            clazz.isInterface -> {
                TypeSpec.interfaceBuilder(clazz.simpleName)
            }

            clazz.isStandardClass -> {
                TypeSpec.classBuilder(clazz.simpleName)
            }

            else -> {
                throw IllegalStateException("Unknown class type: ${clazz.name}")
            }
        }

    private fun addModifiers(
        typeSpec: TypeSpec.Builder,
        clazz: ClassInfo,
    ) {
        val rawModifiers = clazz.modifiers
        val cleanedModifiers =
            when {
                clazz.isAnnotation || clazz.isInterface -> rawModifiers and Modifier.SYNCHRONIZED.inv()
                else -> rawModifiers xor Modifier.SYNCHRONIZED
            }
        typeSpec.addModifiers(*modifiersOf(cleanedModifiers).toTypedArray())
    }

    private fun addTypeParameters(
        typeSpec: TypeSpec.Builder,
        clazz: ClassInfo,
    ) {
        clazz.typeSignature?.typeParameters?.forEach { typeParam ->
            typeSpec.addTypeVariable(buildTypeVariable(typeParam))
        }
    }

    private fun addAnnotations(
        typeSpec: TypeSpec.Builder,
        clazz: ClassInfo,
    ) {
        clazz.annotationInfo
            ?.filterNot { clazz.isAnnotation && it.name.startsWith("java.lang.annotation.") }
            ?.forEach { typeSpec.addAnnotation(ClassName.bestGuess(it.name)) }
    }

    private fun addSuperclass(
        typeSpec: TypeSpec.Builder,
        clazz: ClassInfo,
    ) {
        if (clazz.isInterface || clazz.isAnnotation || clazz.isEnum || clazz.isRecord) return

        val superclassSig = clazz.typeSignature?.superclassSignature?.toString()
        if (superclassSig != null && superclassSig != "java.lang.Object") {
            typeSpec.superclass(parseTypeName(superclassSig))
            return
        }

        clazz.superclass?.let { superclass ->
            if (superclass.name != "java.lang.Object") {
                typeSpec.superclass(ClassName.bestGuess(superclass.name))
            }
        }
    }

    private fun addInterfaces(
        typeSpec: TypeSpec.Builder,
        clazz: ClassInfo,
    ) {
        val interfaceSigs = clazz.typeSignature?.superinterfaceSignatures
        if (!interfaceSigs.isNullOrEmpty()) {
            interfaceSigs.forEach { sig ->
                typeSpec.addSuperinterface(parseTypeName(sig.toString()))
            }
        } else {
            clazz.interfaces.forEach { iface ->
                typeSpec.addSuperinterface(ClassName.bestGuess(iface.name))
            }
        }
    }

    private fun addEnumConstants(
        typeSpec: TypeSpec.Builder,
        clazz: ClassInfo,
    ) {
        if (!clazz.isEnum) return
        clazz.fieldInfo
            .filter { it.isEnum }
            .forEach { typeSpec.addEnumConstant(it.name) }
    }

    private fun addFields(
        typeSpec: TypeSpec.Builder,
        clazz: ClassInfo,
    ) {
        clazz.declaredFieldInfo
            .filterNot { it.isPrivate || it.isEnum }
            .forEach { fieldInfo -> typeSpec.addField(buildField(fieldInfo)) }
    }

    private fun addMethods(
        typeSpec: TypeSpec.Builder,
        clazz: ClassInfo,
    ) {
        clazz.declaredMethodAndConstructorInfo
            .filterNot { it.isPrivate || it.isBridge || isSyntheticAccessor(it) }
            .forEach { methodInfo -> typeSpec.addMethod(buildMethod(methodInfo, clazz)) }
    }

    private fun addInnerClasses(
        typeSpec: TypeSpec.Builder,
        clazz: ClassInfo,
    ) {
        clazz.innerClasses
            .filterNot { it.isAnonymousInnerClass || it.isPrivate }
            .forEach { innerClass -> typeSpec.addType(buildTypeSpec(innerClass)) }
    }

    // ==================== Field Building ====================

    private fun buildField(fieldInfo: io.github.classgraph.FieldInfo): FieldSpec {
        val fieldSpec =
            FieldSpec
                .builder(
                    parseTypeName(fieldInfo.typeSignatureOrTypeDescriptor.toString()),
                    fieldInfo.name,
                ).addModifiers(*modifiersOf(fieldInfo.modifiers).toTypedArray())

        fieldInfo.annotationInfo?.forEach { annotationInfo ->
            fieldSpec.addAnnotation(ClassName.bestGuess(annotationInfo.name))
        }

        return fieldSpec.build()
    }

    // ==================== Method Building ====================

    // ==================== Type Variable Building ====================

    // ==================== Type Parsing ====================
}
