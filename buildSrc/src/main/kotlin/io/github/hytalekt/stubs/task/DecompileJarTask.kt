@file:Suppress("ktlint:standard:no-wildcard-imports")

package io.github.hytalekt.stubs.task

import io.github.hytalekt.stubs.decompile.DecompilerLogger
import io.github.hytalekt.stubs.decompile.LogLevel
import io.github.hytalekt.stubs.decompile.VineflowerDecompiler
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault

/**
 * Gradle task that decompiles a JAR using Vineflower.
 *
 * This task is cacheable - the decompilation output can be stored in
 * Gradle's build cache for faster subsequent builds.
 */
@CacheableTask
abstract class DecompileJarTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceJar: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "decompilation"
        description = "Decompiles a JAR file using Vineflower"
    }

    @TaskAction
    fun decompile() {
        val jarFile = sourceJar.get().asFile
        val outDir = outputDirectory.get().asFile

        // Clean output directory for reproducible results
        outDir.deleteRecursively()
        outDir.mkdirs()

        logger.lifecycle("Decompiling ${jarFile.name} with Vineflower...")

        val decompiler =
            VineflowerDecompiler(
                outputDir = outDir,
                logger = GradleDecompilerLogger(logger),
            )

        val decompiledFiles = decompiler.decompile(jarFile)
        logger.lifecycle("Decompiled ${decompiledFiles.size} classes to ${outDir.absolutePath}")
    }
}

private class GradleDecompilerLogger(
    private val gradleLogger: org.gradle.api.logging.Logger,
) : DecompilerLogger {
    override fun log(
        level: LogLevel,
        message: String,
    ) {
        when (level) {
            LogLevel.DEBUG -> gradleLogger.debug(message)
            LogLevel.INFO -> gradleLogger.info(message)
            LogLevel.WARN -> gradleLogger.warn(message)
            LogLevel.ERROR -> gradleLogger.error(message)
        }
    }
}
