package io.github.hytalekt.stubs.spec

import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.TypeSpec
import io.github.classgraph.ClassInfo
import io.github.hytalekt.stubs.util.buildMethod
import io.github.hytalekt.stubs.util.classModifiersOf
import io.github.hytalekt.stubs.util.isSyntheticAccessor
import io.github.hytalekt.stubs.util.methodModifiersOf
import io.github.hytalekt.stubs.util.parseTypeName

class EnumTypeSpecBuilder(
    private val classInfo: ClassInfo,
) : TypeSpecBuilder {
    override fun build(): TypeSpec {
        require(classInfo.isEnum) {
            "Attempted to build an enum TypeSpec using a non-enum class"
        }

        val typeSpec = TypeSpec.enumBuilder(classInfo.simpleName)

        addModifiers(typeSpec)
        addAnnotations(typeSpec)
        addEnumConstants(typeSpec, classInfo)
        addFields(typeSpec)
        addMethods(typeSpec)
        addInnerClasses(typeSpec)

        return typeSpec.build()
    }

    private fun addModifiers(typeSpec: TypeSpec.Builder) {
        // Enums are implicitly final, so we filter out the FINAL modifier
        val modifiers =
            classModifiersOf(classInfo.modifiers)
                .filter { it != javax.lang.model.element.Modifier.FINAL }
        typeSpec.addModifiers(*modifiers.toTypedArray())
    }

    private fun addAnnotations(typeSpec: TypeSpec.Builder) {
        classInfo.annotationInfo?.forEach { annotationInfo ->
            typeSpec.addAnnotation(buildAnnotationSpec(annotationInfo))
        }
    }

    private fun addFields(typeSpec: TypeSpec.Builder) {
        // Add non-enum-constant fields (e.g., private final String description)
        classInfo.fieldInfo
            .filter { !it.isEnum && !it.isSynthetic }
            .forEach { fieldInfo ->
                val fieldType =
                    parseTypeName(
                        fieldInfo.typeSignatureOrTypeDescriptor.toString(),
                    )
                val fieldSpec =
                    FieldSpec.builder(
                        fieldType,
                        fieldInfo.name,
                        *methodModifiersOf(fieldInfo.modifiers).toTypedArray(),
                    )

                fieldInfo.annotationInfo?.forEach { annotationInfo ->
                    fieldSpec.addAnnotation(buildAnnotationSpec(annotationInfo))
                }

                typeSpec.addField(fieldSpec.build())
            }
    }

    private fun addMethods(typeSpec: TypeSpec.Builder) {
        // Add constructors
        classInfo.constructorInfo
            .filter { !it.isSynthetic }
            .forEach { constructorInfo ->
                typeSpec.addMethod(buildMethod(constructorInfo, classInfo))
            }

        // Add regular methods (filter out compiler-generated methods starting with $)
        classInfo.methodInfo
            .filter { !it.isSynthetic || !isSyntheticAccessor(it) }
            .filter { !it.name.startsWith("$") }
            .forEach { methodInfo ->
                typeSpec.addMethod(buildMethod(methodInfo, classInfo))
            }
    }

    private fun addInnerClasses(typeSpec: TypeSpec.Builder) {
        classInfo.innerClasses
            .filter { !it.isAnonymousInnerClass && !it.isSynthetic }
            .forEach { innerClassInfo ->
                val innerTypeSpec = buildInnerClassTypeSpec(innerClassInfo)
                typeSpec.addType(innerTypeSpec)
            }
    }
}

private fun addEnumConstants(
    typeSpec: TypeSpec.Builder,
    clazz: ClassInfo,
) {
    clazz.fieldInfo
        .filter { it.isEnum }
        .forEach { typeSpec.addEnumConstant(it.name) }
}
