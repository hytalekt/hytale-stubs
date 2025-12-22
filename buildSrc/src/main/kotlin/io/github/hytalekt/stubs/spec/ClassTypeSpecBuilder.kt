package io.github.hytalekt.stubs.spec

import com.palantir.javapoet.TypeSpec
import io.github.classgraph.ClassInfo

class ClassTypeSpecBuilder(
    private val classInfo: ClassInfo,
) : TypeSpecBuilder {
    override fun build(): TypeSpec {
        require(classInfo.isStandardClass && !classInfo.isRecord && !classInfo.isEnum) {
            "Attempted to build a class TypeSpec using a non-standard class"
        }

        val typeSpec = TypeSpec.classBuilder(classInfo.simpleName)

        return typeSpec.build()
    }
}
