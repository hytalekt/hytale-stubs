package io.github.hytalekt.stubs.task

import io.github.hytalekt.stubs.vineflower.EnumPostProcessor
import io.github.hytalekt.stubs.vineflower.ExamplePluginSource
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.jetbrains.java.decompiler.api.Decompiler
import org.jetbrains.java.decompiler.main.Fernflower
import org.jetbrains.java.decompiler.main.decompiler.DirectoryResultSaver
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences
import org.jetbrains.java.decompiler.main.plugins.PluginSources
import java.io.File

abstract class GenerateSourcesTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceJar: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "documentation"
        description = "Generates sources from a JAR using Vineflower decompiler"
    }

    @TaskAction
    fun generateSources() {
        val jarFile = sourceJar.get().asFile
        val outDir = outputDirectory.get().asFile

        outDir.deleteRecursively()
        outDir.mkdirs()

        val decompiler =
            Decompiler
                .builder()
                .inputs(jarFile)
                .option(IFernflowerPreferences.INCLUDE_ENTIRE_CLASSPATH, false)
                .output(DirectoryResultSaver(outDir))
                .allowedPrefixes("com/hypixel")
                .logger(PrintStreamLogger(System.out))
                .build()

        decompiler.decompile()

        logger.lifecycle("Decompiled ${jarFile.name} to ${outDir.absolutePath}")

        // Post-process enum declarations to remove fields, constructors, and constant arguments
        logger.lifecycle("Post-processing enum declarations...")
        EnumPostProcessor.processDirectory(outDir)
        logger.lifecycle("Enum post-processing complete")
    }
}
