package io.github.hytalekt.stubs.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.jetbrains.java.decompiler.api.Decompiler
import org.jetbrains.java.decompiler.main.decompiler.DirectoryResultSaver
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences

abstract class DecompileJarTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceJar: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "stubs"
        description = "Decompiles a JAR to a specified location using Vineflower"
    }

    @TaskAction
    fun generateSources() {
        val jarFile = sourceJar.get().asFile
        val outDir = outputDirectory.get().asFile

        outDir.deleteRecursively()
        outDir.mkdirs()

        logger.lifecycle("Decompiling ${jarFile.name}...")
        val decompiler =
            Decompiler
                .builder()
                .inputs(jarFile)
                .option(IFernflowerPreferences.INCLUDE_ENTIRE_CLASSPATH, false)
                .option(IFernflowerPreferences.SWITCH_EXPRESSIONS, false)
                .option(IFernflowerPreferences.PATTERN_MATCHING, false)
                .option(IFernflowerPreferences.DECOMPILE_COMPLEX_CONDYS, false)
                .option(IFernflowerPreferences.DECOMPILE_ASSERTIONS, false)
                .option(IFernflowerPreferences.TERNARY_CONDITIONS, false)
                .option(IFernflowerPreferences.INCLUDE_ENTIRE_CLASSPATH, true)
                .option(IFernflowerPreferences.SKIP_EXTRA_FILES, true)
                .output(DirectoryResultSaver(outDir))
                .allowedPrefixes("com/hypixel")
                .logger(PrintStreamLogger(System.out))
                .build()

        decompiler.decompile()
        logger.lifecycle("Decompiled ${jarFile.name} to ${outDir.absolutePath}")
    }
}
