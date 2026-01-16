package io.github.hytalekt.stubs.task

import io.github.hytalekt.stubs.postprocess.StubPostProcessor
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.jetbrains.java.decompiler.api.Decompiler
import org.jetbrains.java.decompiler.main.decompiler.DirectoryResultSaver
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences

abstract class GenerateSourcesTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceJar: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "documentation"
        description = "Generates stub sources from a JAR using Vineflower decompiler"
    }

    @TaskAction
    fun generateSources() {
        val jarFile = sourceJar.get().asFile
        val outDir = outputDirectory.get().asFile

        outDir.deleteRecursively()
        outDir.mkdirs()

        // Step 1: Decompile normally with Vineflower
        logger.lifecycle("Decompiling ${jarFile.name}...")
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

        // Step 2: Post-process to convert to stubs
        logger.lifecycle("Post-processing decompiled sources into stubs...")
        StubPostProcessor.processDirectory(outDir)
        logger.lifecycle("Post-processing complete")
    }
}
