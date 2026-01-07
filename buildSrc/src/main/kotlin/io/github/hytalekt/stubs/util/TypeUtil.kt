package io.github.hytalekt.stubs.util

import com.palantir.javapoet.ArrayTypeName
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.TypeVariableName
import com.palantir.javapoet.WildcardTypeName
import io.github.classgraph.MethodInfo
import io.github.classgraph.TypeParameter

internal fun buildTypeVariable(typeParam: TypeParameter): TypeVariableName {
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

internal fun isSyntheticAccessor(method: MethodInfo): Boolean = method.isSynthetic && method.name.startsWith("access$")

internal fun parseTypeName(typeString: String): TypeName =
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

internal fun parseReferenceType(typeString: String): TypeName {
    if (typeString.endsWith("[]")) {
        return ArrayTypeName.of(parseTypeName(typeString.dropLast(2)))
    }
    if (typeString.startsWith("[")) {
        return ArrayTypeName.of(parseTypeName(typeString.substring(1)))
    }

    if (typeString == "?") {
        return WildcardTypeName.subtypeOf(OBJECT_CLASS)
    }
    if (typeString.startsWith("? extends ")) {
        return WildcardTypeName.subtypeOf(parseTypeName(typeString.removePrefix("? extends ")))
    }
    if (typeString.startsWith("? super ")) {
        return WildcardTypeName.supertypeOf(parseTypeName(typeString.removePrefix("? super ")))
    }

    val genericStart = typeString.indexOf('<')
    if (genericStart != -1) {
        return parseParameterizedType(typeString, genericStart)
    }

    return parseClassName(typeString)
}

internal fun parseParameterizedType(
    typeString: String,
    genericStart: Int,
): TypeName {
    val rawType = typeString.take(genericStart).trim()

    if (rawType.isBlank()) {
        return OBJECT_CLASS
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
        parseClassName(rawType)
    }
}

internal fun boxPrimitiveIfNeeded(typeName: TypeName): TypeName =
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

internal fun parseClassName(name: String): ClassName {
    val cleaned =
        name
            .removePrefix("L")
            .removeSuffix(";")
            .replace("/", ".")

    return try {
        ClassName.bestGuess(cleaned)
    } catch (_: Exception) {
        OBJECT_CLASS
    }
}

internal fun splitTypeArguments(args: String): List<String> {
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
