package io.github.hytalekt.stubs.spec

import com.palantir.javapoet.TypeSpec
import io.github.classgraph.ClassInfo

class AnnotationTypeSpecBuilder(
    private val classInfo: ClassInfo,
) : TypeSpecBuilder {
    override fun build(): TypeSpec {
        require(classInfo.isAnnotation) {
            "Attempted to build an annotation TypeSpec using a non-annotation class"
        }

        val typeSpec = TypeSpec.annotationBuilder(classInfo.simpleName)

        TODO("Not yet implemented")
    }
}
