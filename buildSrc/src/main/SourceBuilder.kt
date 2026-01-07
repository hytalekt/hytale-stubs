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

    private fun buildTypeVariable(typeParam: TypeParameter): TypeVariableName {
        val bounds = mutableListOf<TypeName>()

        typeParam.classBound?.let { bound ->
            val boundStr = bound.toString()
            if (boundStr != "java.lang.Object") {
                bounds.add(parseTypeName(boundStr))
            }
        }

        typeParam.interfaceBounds?.forEach { bound ->
            bounds.add(parseTypeName(bound.toString()))
        }

        return if (bounds.isNotEmpty()) {
            TypeVariableName.get(typeParam.name, *bounds.toTypedArray())
        } else {
            TypeVariableName.get(typeParam.name)
        }
    }

    private fun isSyntheticAccessor(method: MethodInfo): Boolean = method.isSynthetic && method.name.startsWith("access$")

    // ==================== Type Parsing ====================

    private fun parseTypeName(typeString: String): TypeName =
        when (val trimmed = typeString.trim()) {
            "V", "void" -> TypeName.VOID
            "Z", "boolean" -> TypeName.BOOLEAN
            "B", "byte" -> TypeName.BYTE
            "C", "char" -> TypeName.CHAR
            "S", "short" -> TypeName.SHORT
            "I", "int" -> TypeName.INT
            "J", "long" -> TypeName.LONG
            "F", "float" -> TypeName.FLOAT
            "D", "double" -> TypeName.DOUBLE
            else -> parseReferenceType(trimmed)
        }

    private fun parseReferenceType(typeString: String): TypeName {
        // Handle arrays
        if (typeString.endsWith("[]")) {
            return ArrayTypeName.of(parseTypeName(typeString.dropLast(2)))
        }
        if (typeString.startsWith("[")) {
            return ArrayTypeName.of(parseTypeName(typeString.substring(1)))
        }

        // Handle wildcards
        if (typeString == "?") {
            return WildcardTypeName.subtypeOf(OBJECT_TYPE)
        }
        if (typeString.startsWith("? extends ")) {
            return WildcardTypeName.subtypeOf(parseTypeName(typeString.removePrefix("? extends ")))
        }
        if (typeString.startsWith("? super ")) {
            return WildcardTypeName.supertypeOf(parseTypeName(typeString.removePrefix("? super ")))
        }

        // Handle parameterized types
        val genericStart = typeString.indexOf('<')
        if (genericStart != -1) {
            return parseParameterizedType(typeString, genericStart)
        }

        return parseClassName(typeString)
    }

    private fun parseParameterizedType(
        typeString: String,
        genericStart: Int,
    ): TypeName {
        val rawType = typeString.take(genericStart).trim()

        if (rawType.isBlank()) {
            return OBJECT_TYPE
        }

        val lastGenericEnd = typeString.lastIndexOf('>')
        if (lastGenericEnd <= genericStart) {
            return parseClassName(rawType)
        }

        val genericPart = typeString.substring(genericStart + 1, lastGenericEnd)
        val typeArgs =
            splitTypeArguments(genericPart)
                .map { parseTypeName(it) }
                .map { boxPrimitiveIfNeeded(it) }

        if (typeArgs.isEmpty()) {
            return parseClassName(rawType)
        }

        return try {
            ParameterizedTypeName.get(parseClassName(rawType), *typeArgs.toTypedArray())
        } catch (_: IllegalArgumentException) {
            // Fallback to raw type if parameterization fails
            parseClassName(rawType)
        }
    }

    private fun boxPrimitiveIfNeeded(typeName: TypeName): TypeName =
        when (typeName) {
            TypeName.VOID -> ClassName.get("java.lang", "Void")
            TypeName.BOOLEAN -> ClassName.get("java.lang", "Boolean")
            TypeName.BYTE -> ClassName.get("java.lang", "Byte")
            TypeName.CHAR -> ClassName.get("java.lang", "Character")
            TypeName.SHORT -> ClassName.get("java.lang", "Short")
            TypeName.INT -> ClassName.get("java.lang", "Integer")
            TypeName.LONG -> ClassName.get("java.lang", "Long")
            TypeName.FLOAT -> ClassName.get("java.lang", "Float")
            TypeName.DOUBLE -> ClassName.get("java.lang", "Double")
            else -> typeName
        }

    private fun parseClassName(name: String): ClassName {
        val cleaned =
            name
                .removePrefix("L")
                .removeSuffix(";")
                .replace("/", ".")

        return try {
            ClassName.bestGuess(cleaned)
        } catch (_: Exception) {
            OBJECT_TYPE
        }
    }

    private fun splitTypeArguments(args: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()

        for (char in args) {
            when (char) {
                '<' -> {
                    depth++
                    current.append(char)
                }

                '>' -> {
                    depth--
                    current.append(char)
                }

                ',' -> {
                    if (depth == 0) {
                        result.add(current.toString().trim())
                        current.clear()
                    } else {
                        current.append(char)
                    }
                }

                else -> {
                    current.append(char)
                }
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.toString().trim())
        }

        return result
    }
}
