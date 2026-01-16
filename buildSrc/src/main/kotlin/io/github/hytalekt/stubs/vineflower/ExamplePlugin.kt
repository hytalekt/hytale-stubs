package io.github.hytalekt.stubs.vineflower

import org.jetbrains.java.decompiler.api.java.JavaPassLocation
import org.jetbrains.java.decompiler.api.java.JavaPassRegistrar
import org.jetbrains.java.decompiler.api.plugin.Plugin
import org.jetbrains.java.decompiler.api.plugin.pass.NamedPass

/**
 * Vineflower plugin that removes method bodies to generate stub code.
 */
class ExamplePlugin : Plugin {
    override fun registerJavaPasses(registrar: JavaPassRegistrar) {
        // EnumPass handles enum-specific transformations (constructors, static initializers)
        registrar.register(
            JavaPassLocation.BEFORE_MAIN,
            NamedPass.of("EnumPass", EnumPass()),
        )
        // RemoveMethodBodiesPass handles all other method bodies (including regular enum methods)
        registrar.register(
            JavaPassLocation.BEFORE_MAIN,
            NamedPass.of("RemoveMethodBodies", RemoveMethodBodiesPass()),
        )
    }

    override fun id(): String = "stub-generator"

    override fun description(): String = "Removes method bodies to generate stub implementations"
}
