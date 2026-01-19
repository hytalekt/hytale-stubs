@file:Suppress("ktlint:standard:no-wildcard-imports")

package io.github.hytalekt.stubs.task

import io.github.hytalekt.stubs.ai.GeminiStubGenerator
import io.github.hytalekt.stubs.ai.OpenRouterClient
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

/**
 * Gradle task that enhances decompiled Java sources using Gemini AI.
 *
 * Features:
 * - Renames var0/var1 parameters to meaningful names
 * - Adds Javadoc documentation
 * - Replaces method bodies with GeneratedStubException()
 *
 * Caching:
 * - Uses @LocalState for AI response cache - this directory should be
 *   persisted between CI runs using GitHub Actions cache or similar
 * - The cache key is based on content hash, so identical inputs will
 *   reuse cached AI responses
 *
 * For GitHub Actions, add this to your workflow:
 * ```yaml
 * - uses: actions/cache@v4
 *   with:
 *     path: build/cache/ai-responses
 *     key: ai-stubs-${{ hashFiles('path/to/input.jar') }}
 *     restore-keys: ai-stubs-
 * ```
 */
abstract class EnhanceWithAITask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val decompiledSourcesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /**
     * Local state directory for AI response cache.
     * This is not part of the build cache but should be persisted
     * between CI runs for cost savings.
     */
    @get:LocalState
    abstract val aiCacheDirectory: DirectoryProperty

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
    abstract val classFilter: Property<String>

    init {
        group = "documentation"
        description = "Enhances decompiled Java sources with AI-generated documentation and meaningful names"

        // Set defaults
        model.convention("google/gemini-2.5-flash-preview")
        temperature.convention(0.1)
        classFilter.convention("")
    }

    @TaskAction
    fun enhance() {
        val sourcesDir = decompiledSourcesDir.get().asFile
        val outDir = outputDirectory.get().asFile
        val aiCacheDir = aiCacheDirectory.get().asFile

        outDir.mkdirs()
        aiCacheDir.mkdirs()

        // Collect all Java files
        val sourceFiles =
            sourcesDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "java" }
                .map { file ->
                    val relativePath = file.relativeTo(sourcesDir).path
                    val className = relativePath.removeSuffix(".java").replace(File.separatorChar, '.')
                    className to file
                }.toMap()

        // Apply class filter if specified
        val filterPattern = classFilter.get()
        val filteredFiles =
            if (filterPattern.isNotBlank()) {
                val regex = Regex(filterPattern)
                sourceFiles.filter { (className, _) ->
                    regex.containsMatchIn(className)
                }
            } else {
                sourceFiles
            }

        if (filteredFiles.isEmpty()) {
            logger.lifecycle("No classes to process after filtering")
            return
        }

        logger.lifecycle("Enhancing ${filteredFiles.size} classes with Gemini AI...")

        val apiKey =
            openRouterApiKey.orNull
                ?: System.getenv("OPENROUTER_API_KEY")
                ?: throw IllegalStateException(
                    "OpenRouter API key not provided. Set via openRouterApiKey property or OPENROUTER_API_KEY environment variable",
                )

        val client =
            OpenRouterClient(
                apiKey = apiKey,
                cacheDir = aiCacheDir,
                model = model.get(),
                temperature = temperature.get(),
            )

        val generator = GeminiStubGenerator(client)

        // Read decompiled sources
        val decompiledSources =
            filteredFiles.mapValues { (_, file) ->
                file.readText()
            }

        // Check cache status before processing for reporting
        var cacheHits = 0
        var apiCalls = 0
        decompiledSources.forEach { (_, source) ->
            val cacheKey = generator.computeCacheKey(source)
            val cacheFile = File(aiCacheDir, "$cacheKey.txt")
            if (cacheFile.exists()) cacheHits++ else apiCalls++
        }

        logger.lifecycle("Processing ${decompiledSources.size} files ($cacheHits cached, $apiCalls API calls)...")

        // Generate enhanced stubs in parallel with progress callback
        val result =
            generator.generateStubs(decompiledSources) { className, current, total ->
                logger.lifecycle("  [$current/$total] $className")
            }

        // Print report of skipped files
        result.printReport()

        // Write enhanced stubs to output
        logger.lifecycle("Writing ${result.stubs.size} enhanced stubs...")
        result.stubs.forEach { (className, source) ->
            val relativePath = className.replace('.', File.separatorChar) + ".java"
            val targetFile = File(outDir, relativePath)
            targetFile.parentFile.mkdirs()
            targetFile.writeText(source)
        }

        logger.lifecycle("AI enhancement complete!")
        logger.lifecycle("  Processed: ${result.stubs.size}, Skipped: ${result.skipped.size}")
        logger.lifecycle("  Output: ${outDir.absolutePath}")
    }
}
