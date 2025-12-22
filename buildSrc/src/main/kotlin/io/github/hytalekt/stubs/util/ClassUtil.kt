package io.github.hytalekt.stubs.util

import java.lang.reflect.Modifier
import javax.lang.model.element.Modifier as ModifierType

fun classModifiersOf(modifiers: Int): List<ModifierType> =
    listOfNotNull(
        ModifierType.PUBLIC.takeIf { Modifier.isPublic(modifiers) },
        ModifierType.PRIVATE.takeIf { Modifier.isPrivate(modifiers) },
        ModifierType.PROTECTED.takeIf { Modifier.isProtected(modifiers) },
        ModifierType.STATIC.takeIf { Modifier.isStatic(modifiers) },
        ModifierType.FINAL.takeIf { Modifier.isFinal(modifiers) },
        ModifierType.VOLATILE.takeIf { Modifier.isVolatile(modifiers) },
        ModifierType.TRANSIENT.takeIf { Modifier.isTransient(modifiers) },
        ModifierType.NATIVE.takeIf { Modifier.isNative(modifiers) },
        ModifierType.ABSTRACT.takeIf { Modifier.isAbstract(modifiers) },
        ModifierType.STRICTFP.takeIf { Modifier.isStrict(modifiers) },
    )
