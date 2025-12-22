package io.github.hytalekt.stubs.spec

import com.palantir.javapoet.TypeSpec
import io.github.classgraph.ClassInfo

class InterfaceTypeSpecBuilder(
    private val classInfo: ClassInfo,
) : TypeSpecBuilder {
    override fun build(): TypeSpec {
        require(classInfo.isInterface) {
            "Attempted to build an interface TypeSpec using a non-interface class"
        }

        val typeSpec = TypeSpec.interfaceBuilder(classInfo.simpleName)

        return typeSpec.build()
    }
}
