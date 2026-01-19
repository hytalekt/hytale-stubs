package io.github.hytalekt.stubs.vineflower

import org.jetbrains.java.decompiler.api.java.JavaPassLocation
import org.jetbrains.java.decompiler.api.java.JavaPassRegistrar
import org.jetbrains.java.decompiler.api.plugin.Plugin
import org.jetbrains.java.decompiler.api.plugin.pass.NamedPass

/**
 * Vineflower plugin for generating legal Java stubs.
 *
 * This plugin registers passes that transform decompiled code into stub-friendly output:
 * - ConstructorStubPass: Replaces super()/this() arguments with default values
 */
class StubGeneratorPlugin : Plugin {
    override fun id(): String = "StubGenerator"

    override fun description(): String =
        "Transforms decompiled code for stub generation by replacing constructor delegation arguments with defaults."

    override fun registerJavaPasses(registrar: JavaPassRegistrar) {
        registrar.register(
            JavaPassLocation.BEFORE_MAIN,
            NamedPass.of("ConstructorStub", ConstructorStubPass()),
        )
    }
}
