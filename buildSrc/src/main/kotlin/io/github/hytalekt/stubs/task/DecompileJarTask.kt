@file:Suppress("ktlint:standard:no-wildcard-imports")

package io.github.hytalekt.stubs.task

import io.github.hytalekt.stubs.ai.GeminiStubGenerator
import io.github.hytalekt.stubs.decompile.VineflowerDecompiler
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

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

    @get:Input
    @get:Optional
    abstract val maxCharacters: Property<Int>

    @get:Input
    @get:Optional
    abstract val maxLines: Property<Int>

    init {
        group = "decompilation"
        description = "Decompiles a JAR file using Vineflower"

        maxCharacters.convention(GeminiStubGenerator.DEFAULT_MAX_CHARACTERS)
        maxLines.convention(GeminiStubGenerator.DEFAULT_MAX_LINES)
    }

    @TaskAction
    fun decompile() {
        val jarFile = sourceJar.get().asFile
        val outDir = outputDirectory.get().asFile

        // Clean output directory for reproducible results
        outDir.deleteRecursively()
        outDir.mkdirs()

        logger.lifecycle("Decompiling ${jarFile.name} with Vineflower...")

        val decompiler = VineflowerDecompiler(outputDir = outDir)

        val decompiledFiles = decompiler.decompile(jarFile)
        logger.lifecycle("Decompiled ${decompiledFiles.size} classes to ${outDir.absolutePath}")

        // Analyze file sizes and report which ones exceed AI limits
        printSizeReport(outDir)
    }

    private fun printSizeReport(outDir: File) {
        val maxChars = maxCharacters.get()
        val maxLns = maxLines.get()

        data class OversizedFile(
            val className: String,
            val charCount: Int,
            val lineCount: Int,
            val reason: String,
        )

        val oversized = mutableListOf<OversizedFile>()

        outDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .forEach { file ->
                val content = file.readText()
                val charCount = content.length
                val lineCount = content.lines().size
                val className = file.relativeTo(outDir).path.removeSuffix(".java").replace(File.separatorChar, '.')

                val reason =
                    when {
                        charCount > maxChars -> "Exceeds character limit ($charCount > $maxChars)"
                        lineCount > maxLns -> "Exceeds line limit ($lineCount > $maxLns)"
                        else -> null
                    }

                if (reason != null) {
                    oversized.add(OversizedFile(className, charCount, lineCount, reason))
                }
            }

        if (oversized.isNotEmpty()) {
            logger.lifecycle("")
            logger.lifecycle("========== OVERSIZED FILES REPORT ==========")
            logger.lifecycle("The following ${oversized.size} file(s) will be SKIPPED by AI enhancement:")
            logger.lifecycle("")

            oversized
                .sortedByDescending { it.charCount }
                .forEach { file ->
                    logger.lifecycle("  - ${file.className}")
                    logger.lifecycle("      Reason: ${file.reason}")
                    logger.lifecycle("      Size: ${file.charCount} chars, ${file.lineCount} lines")
                }

            logger.lifecycle("")
            logger.lifecycle("These files require manual review after AI enhancement.")
            logger.lifecycle("=============================================")
        } else {
            logger.lifecycle("All decompiled files are within AI size limits.")
        }
    }
}
