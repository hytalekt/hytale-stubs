package io.github.hytalekt.stubs.vineflower

import org.jetbrains.java.decompiler.api.plugin.Plugin
import org.jetbrains.java.decompiler.api.plugin.PluginSource

/**
 * Plugin source for programmatic registration of the StubGeneratorPlugin.
 *
 * This allows registering the plugin directly via the Decompiler builder API
 * instead of using ServiceLoader discovery.
 */
object StubGeneratorPluginSource : PluginSource {
    override fun findPlugins(): List<Plugin> = listOf(StubGeneratorPlugin())
}
