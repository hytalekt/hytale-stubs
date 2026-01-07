package io.github.hytalekt.stubs.util

import com.palantir.javapoet.ClassName
import java.lang.reflect.Modifier
import javax.lang.model.element.Modifier as JaxModifier

val OBJECT_CLASS: ClassName = ClassName.get("java.lang", "Object")

val STUB_EXCEPTION_CLASS: ClassName =
    ClassName.get("io.github.kytale.stubs", "GeneratedStubException")

fun classModifiersOf(modifiers: Int): List<JaxModifier> =
    listOfNotNull(
        JaxModifier.PUBLIC.takeIf { Modifier.isPublic(modifiers) },
        JaxModifier.PRIVATE.takeIf { Modifier.isPrivate(modifiers) },
        JaxModifier.PROTECTED.takeIf { Modifier.isProtected(modifiers) },
        JaxModifier.STATIC.takeIf { Modifier.isStatic(modifiers) },
        JaxModifier.FINAL.takeIf { Modifier.isFinal(modifiers) },
        JaxModifier.ABSTRACT.takeIf { Modifier.isAbstract(modifiers) },
        JaxModifier.STRICTFP.takeIf { Modifier.isStrict(modifiers) },
    )
