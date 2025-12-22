package io.github.hytalekt.stubs.spec

import com.palantir.javapoet.TypeSpec
import io.github.classgraph.ClassInfo

interface TypeSpecBuilder {
    fun build(): TypeSpec
}

fun TypeSpecBuilder(classInfo: ClassInfo): TypeSpecBuilder =
    when {
        classInfo.isEnum -> {
            EnumTypeSpecBuilder(classInfo)
        }

        classInfo.isRecord -> {
            RecordTypeSpecBuilder(classInfo)
        }

        classInfo.isAnnotation -> {
            AnnotationTypeSpecBuilder(classInfo)
        }

        classInfo.isInterface -> {
            InterfaceTypeSpecBuilder(classInfo)
        }

        classInfo.isStandardClass -> {
            ClassTypeSpecBuilder(classInfo)
        }

        else -> {
            throw IllegalStateException("Unknown class type: ${classInfo.name}")
        }
    }
