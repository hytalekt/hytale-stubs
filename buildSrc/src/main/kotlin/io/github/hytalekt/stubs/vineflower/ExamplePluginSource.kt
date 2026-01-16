package io.github.hytalekt.stubs.vineflower

import org.jetbrains.java.decompiler.api.plugin.Plugin
import org.jetbrains.java.decompiler.api.plugin.PluginSource

/**
 * PluginSource for programmatically registering Vineflower plugins.
 *
 * Usage in Decompiler.Builder:
 * ```
 * val decompiler = Decompiler.builder()
 *     .pluginSource(ExamplePluginSource())
 *     .inputs(contextSource)
 *     .output(resultSaver)
 *     .build()
 * ```
 */
class ExamplePluginSource : PluginSource {
    override fun findPlugins(): List<Plugin> {
        return listOf(
            ExamplePlugin()
        )
    }
}
