package io.github.hytalekt.stubs.spec

import com.palantir.javapoet.TypeSpec
import io.github.classgraph.ClassInfo
import io.github.hytalekt.stubs.util.buildField
import io.github.hytalekt.stubs.util.buildMethod
import io.github.hytalekt.stubs.util.buildTypeVariable
import io.github.hytalekt.stubs.util.classModifiersOf
import io.github.hytalekt.stubs.util.isSyntheticAccessor
import io.github.hytalekt.stubs.util.parseTypeName
import javax.lang.model.element.Modifier

class ClassTypeSpecBuilder(
    private val classInfo: ClassInfo,
) : TypeSpecBuilder {
    override fun build(): TypeSpec {
        require(classInfo.isStandardClass && !classInfo.isRecord && !classInfo.isEnum) {
            "Attempted to build a class TypeSpec using a non-standard class"
        }

        val typeSpec = TypeSpec.classBuilder(classInfo.simpleName)

        addModifiers(typeSpec)
        addTypeParameters(typeSpec)
        addSuperclass(typeSpec)
        addInterfaces(typeSpec)
        addAnnotations(typeSpec)
        addFields(typeSpec)
        addMethods(typeSpec)
        addInnerClasses(typeSpec)

        return typeSpec.build()
    }

    private fun addModifiers(typeSpec: TypeSpec.Builder) {
        typeSpec.addModifiers(*classModifiersOf(classInfo.modifiers).toTypedArray())
    }

    private fun addTypeParameters(typeSpec: TypeSpec.Builder) {
        classInfo.typeSignature?.typeParameters?.forEach { typeParam ->
            typeSpec.addTypeVariable(buildTypeVariable(typeParam))
        }
    }

    private fun addSuperclass(typeSpec: TypeSpec.Builder) {
        val superclass = classInfo.superclass
        if (superclass != null && superclass.name != "java.lang.Object") {
            val superclassType =
                classInfo.typeSignature?.superclassSignature?.toString()
                    ?: superclass.name
            typeSpec.superclass(parseTypeName(superclassType))
        }
    }

    private fun addInterfaces(typeSpec: TypeSpec.Builder) {
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
                typeSpec.addField(buildField(fieldInfo))
            }
    }

    private fun addMethods(typeSpec: TypeSpec.Builder) {
        // Add constructors
        classInfo.constructorInfo
            .filter { !it.isSynthetic }
            .forEach { constructorInfo ->
                typeSpec.addMethod(buildMethod(constructorInfo, classInfo))
            }

        // Add regular methods
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
                // Check if the immediate outer class is the current class
                val outerClass = innerClassInfo.outerClasses?.firstOrNull()
                outerClass?.name == classInfo.name
            }.forEach { innerClassInfo ->
                val innerTypeSpec = buildInnerClassTypeSpec(innerClassInfo)
                typeSpec.addType(innerTypeSpec)
            }
    }
}

/**
 * Builds a TypeSpec for an inner class, handling modifier conflicts.
 * Inner classes in bytecode may have both PUBLIC and PROTECTED modifiers
 * due to how Java handles inner class accessibility. We need to filter
 * to ensure only one visibility modifier is present.
 */
internal fun buildInnerClassTypeSpec(innerClassInfo: ClassInfo): TypeSpec {
    val innerBuilder = TypeSpecBuilder(innerClassInfo)
    val innerTypeSpec = innerBuilder.build()

    // Get the modifiers using the public getter
    val modifierSet = innerTypeSpec.modifiers()

    // Check if there are conflicting visibility modifiers
    val visibilityModifiers =
        modifierSet.filter {
            it == Modifier.PUBLIC ||
                it == Modifier.PROTECTED ||
                it == Modifier.PRIVATE
        }

    // If there are multiple visibility modifiers, rebuild with corrected modifiers
    if (visibilityModifiers.size > 1) {
        // Prefer the most restrictive: private > protected > public
        val preferredVisibility =
            when {
                visibilityModifiers.contains(Modifier.PRIVATE) -> Modifier.PRIVATE
                visibilityModifiers.contains(Modifier.PROTECTED) -> Modifier.PROTECTED
                else -> Modifier.PUBLIC
            }

        // Rebuild with corrected modifiers - remove all visibility and add preferred
        val otherModifiers = modifierSet.filter { it !in visibilityModifiers }

        // Use toBuilder and rebuild - but we need to work around the private modifiers field
        // JavaPoet doesn't provide a way to clear modifiers, so we rebuild from scratch
        return rebuildTypeSpecWithModifiers(innerTypeSpec, otherModifiers + preferredVisibility)
    }

    return innerTypeSpec
}

/**
 * Rebuilds a TypeSpec with a new set of modifiers.
 * This is needed because JavaPoet's TypeSpec.Builder doesn't allow clearing modifiers.
 */
private fun rebuildTypeSpecWithModifiers(
    original: TypeSpec,
    newModifiers: Collection<Modifier>,
): TypeSpec {
    val builder =
        when (original.kind()) {
            TypeSpec.Kind.CLASS -> {
                TypeSpec
                    .classBuilder(original.name())
            }

            TypeSpec.Kind.INTERFACE -> {
                TypeSpec
                    .interfaceBuilder(original.name())
            }

            TypeSpec.Kind.ENUM -> {
                TypeSpec
                    .enumBuilder(original.name())
            }

            TypeSpec.Kind.ANNOTATION -> {
                TypeSpec
                    .annotationBuilder(original.name())
            }

            else -> {
                TypeSpec
                    .classBuilder(original.name())
            }
        }

    builder.addModifiers(*newModifiers.toTypedArray())
    original.typeVariables().forEach { builder.addTypeVariable(it) }
    original.superclass()?.let { if (it.toString() != "java.lang.Object") builder.superclass(it) }
    original.superinterfaces().forEach { builder.addSuperinterface(it) }
    original.annotations().forEach { builder.addAnnotation(it) }
    original.fieldSpecs().forEach { builder.addField(it) }
    original.methodSpecs().forEach { builder.addMethod(it) }
    original.typeSpecs().forEach { builder.addType(it) }

    // For enums, add enum constants
    if (original.kind() == TypeSpec.Kind.ENUM) {
        original.enumConstants().forEach { (name, spec) ->
            builder.addEnumConstant(name, spec)
        }
    }

    return builder.build()
}
