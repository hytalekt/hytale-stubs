@file:Suppress("ktlint:standard:no-wildcard-imports")

package io.github.hytalekt.stubs.task

import io.github.hytalekt.stubs.ai.GeminiStubGenerator
import io.github.hytalekt.stubs.ai.OpenRouterClient
import io.github.hytalekt.stubs.decompile.DecompilerLogger
import io.github.hytalekt.stubs.decompile.LogLevel
import io.github.hytalekt.stubs.decompile.VineflowerDecompiler
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

/**
 * Gradle task that:
 * 1. Decompiles a JAR using Vineflower
 * 2. Sends decompiled code to Gemini 2.5 Flash via OpenRouter
 * 3. Generates improved stubs with documentation and meaningful parameter names
 *
 * Includes caching at multiple levels:
 * - Vineflower decompilation output (Gradle up-to-date checking)
 * - Gemini API responses (disk cache by content hash)
 */
abstract class GenerateAIStubsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceJar: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Internal
    abstract val cacheDirectory: DirectoryProperty

    @get:Internal
    abstract val openRouterApiKey: Property<String>

    @get:Input
    @get:Optional
    abstract val model: Property<String>

    @get:Input
    @get:Optional
    abstract val temperature: Property<Double>

    @get:Input
    @get:Optional
    abstract val dryRun: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val classFilter: Property<String>

    init {
        group = "documentation"
        description = "Generates AI-enhanced stubs with documentation from a JAR using Vineflower and Gemini"

        // Set defaults
        model.convention("google/gemini-2.5-flash-preview")
        temperature.convention(0.1)
        dryRun.convention(false)
        classFilter.convention("")
    }

    @TaskAction
    fun generate() {
        val outDir = outputDirectory.get().asFile
        val cacheDir = cacheDirectory.get().asFile
        val jarFile = sourceJar.get().asFile

        outDir.mkdirs()
        cacheDir.mkdirs()

        val decompileDir = File(cacheDir, "decompiled")
        val aiCacheDir = File(cacheDir, "ai-responses")

        logger.lifecycle("Starting AI stub generation for ${jarFile.name}")

        // Step 1: Decompile with Vineflower
        logger.lifecycle("Step 1/3: Decompiling with Vineflower...")
        val decompiler = VineflowerDecompiler(
            outputDir = decompileDir,
            logger = GradleDecompilerLogger(logger),
        )

        val decompiledFiles = decompiler.decompile(jarFile)
        logger.lifecycle("  Decompiled ${decompiledFiles.size} classes")

        // Apply class filter if specified
        val filterPattern = classFilter.get()
        val filteredFiles = if (filterPattern.isNotBlank()) {
            val regex = Regex(filterPattern)
            decompiledFiles.filter { (className, _) ->
                regex.containsMatchIn(className)
            }
        } else {
            decompiledFiles
        }

        if (filteredFiles.isEmpty()) {
            logger.lifecycle("No classes to process after filtering")
            return
        }

        logger.lifecycle("  Processing ${filteredFiles.size} classes after filtering")

        // Step 2: Generate AI-enhanced stubs
        if (dryRun.get()) {
            logger.lifecycle("Step 2/3: Dry run - skipping AI enhancement")
            // Just copy decompiled files to output
            filteredFiles.forEach { (className, sourceFile) ->
                val targetFile = getOutputFile(outDir, className)
                targetFile.parentFile.mkdirs()
                sourceFile.copyTo(targetFile, overwrite = true)
            }
        } else {
            logger.lifecycle("Step 2/3: Enhancing with Gemini AI...")

            val apiKey = openRouterApiKey.orNull
                ?: System.getenv("OPENROUTER_API_KEY")
                ?: throw IllegalStateException(
                    "OpenRouter API key not provided. Set via openRouterApiKey property or OPENROUTER_API_KEY environment variable"
                )

            val client = OpenRouterClient(
                apiKey = apiKey,
                cacheDir = aiCacheDir,
                model = model.get(),
                temperature = temperature.get(),
            )

            val generator = GeminiStubGenerator(client)

            // Read decompiled sources
            val decompiledSources = filteredFiles.mapValues { (_, file) ->
                file.readText()
            }

            // Generate enhanced stubs with progress
            val enhancedStubs = generator.generateStubs(decompiledSources) { className, current, total ->
                logger.lifecycle("  [$current/$total] Processing $className")
            }

            // Write enhanced stubs to output
            logger.lifecycle("Step 3/3: Writing enhanced stubs...")
            enhancedStubs.forEach { (className, source) ->
                val targetFile = getOutputFile(outDir, className)
                targetFile.parentFile.mkdirs()
                targetFile.writeText(source)
            }
        }

        logger.lifecycle("AI stub generation complete! Output: ${outDir.absolutePath}")
    }

    private fun getOutputFile(outDir: File, className: String): File {
        val relativePath = className.replace('.', File.separatorChar) + ".java"
        return File(outDir, relativePath)
    }
}

private class GradleDecompilerLogger(
    private val gradleLogger: org.gradle.api.logging.Logger,
) : DecompilerLogger {
    override fun log(level: LogLevel, message: String) {
        when (level) {
            LogLevel.DEBUG -> gradleLogger.debug(message)
            LogLevel.INFO -> gradleLogger.info(message)
            LogLevel.WARN -> gradleLogger.warn(message)
            LogLevel.ERROR -> gradleLogger.error(message)
        }
    }
}
