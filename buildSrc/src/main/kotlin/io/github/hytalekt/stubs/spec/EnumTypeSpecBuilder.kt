package io.github.hytalekt.stubs.spec

import com.palantir.javapoet.TypeSpec
import io.github.classgraph.ClassInfo

class EnumTypeSpecBuilder(
    private val classInfo: ClassInfo,
) : TypeSpecBuilder {
    override fun build(): TypeSpec {
        require(classInfo.isEnum) {
            "Attempted to build an enum TypeSpec using a non-enum class"
        }

        val typeSpec = TypeSpec.enumBuilder(classInfo.simpleName)

        typeSpec.addEnumConstants(classInfo)

        return typeSpec.build()
    }
}

private fun TypeSpec.Builder.addEnumConstants(clazz: ClassInfo) {
    clazz.fieldInfo
        .filter { it.isEnum }
        .forEach { addEnumConstant(it.name) }
}
