import io.github.hytalekt.stubs.HytaleStubsPlugin
import io.github.hytalekt.stubs.docs
import io.github.hytalekt.stubs.task.DecompileJarTask
import io.github.hytalekt.stubs.task.EnhanceWithAITask
import io.github.hytalekt.stubs.task.GenerateSourcesTask
import org.gradle.kotlin.dsl.register

plugins {
    java
    idea
}

apply<HytaleStubsPlugin>()

repositories {
    mavenCentral()
}

sourceSets {
    main {
        java {
            srcDir("build/gen/sources")
        }
    }
}

tasks.register<GenerateSourcesTask>("generateSources") {
    docSourceSet = sourceSets.main.get().docs
    sourceJar = File("testingdocs.jar")
    outputDirectory = File("build/gen/sources")
}

// ============================================================================
// AI-Enhanced Stub Generation Pipeline
// ============================================================================
//
// This pipeline uses two separate tasks for optimal caching:
// 1. decompileJar - Cacheable task that decompiles with Vineflower
// 2. enhanceWithAI - Task that enhances with Gemini AI (uses LocalState cache)
//
// For GitHub Actions, configure caching like this:
//
// ```yaml
// - uses: actions/cache@v4
//   with:
//     path: |
//       ~/.gradle/caches
//       build/cache/ai-responses
//     key: ${{ runner.os }}-gradle-ai-${{ hashFiles('**/*.jar') }}
//     restore-keys: |
//       ${{ runner.os }}-gradle-ai-
//       ${{ runner.os }}-gradle-
// ```
// ============================================================================

/**
 * Step 1: Decompile JAR using Vineflower.
 * This task is cacheable - results are stored in Gradle build cache.
 */
val decompileJar = tasks.register<DecompileJarTask>("decompileJar") {
    sourceJar = File("testingdocs.jar")
    outputDirectory = File("build/cache/decompiled")
}

/**
 * Step 2: Enhance decompiled sources with Gemini AI.
 *
 * Usage:
 *   ./gradlew enhanceWithAI
 *
 * Options:
 *   -PopenRouterApiKey=YOUR_KEY   Set OpenRouter API key (or use OPENROUTER_API_KEY env var)
 *   -PclassFilter=regex           Filter classes by regex pattern
 *   -Pmodel=model-id              Override the AI model (default: google/gemini-2.5-flash-preview)
 *
 * Example:
 *   ./gradlew enhanceWithAI -PopenRouterApiKey=sk-or-... -PclassFilter="com\.example\..*"
 *
 * Caching:
 *   - Decompilation results are cached via Gradle build cache
 *   - AI responses are cached in build/cache/ai-responses (persist this in CI)
 */
val enhanceWithAI = tasks.register<EnhanceWithAITask>("enhanceWithAI") {
    dependsOn(decompileJar)
    decompiledSourcesDir = decompileJar.flatMap { it.outputDirectory }
    outputDirectory = File("build/gen/ai-stubs")
    aiCacheDirectory = File("build/cache/ai-responses")

    // Get API key from project property or environment
    if (project.hasProperty("openRouterApiKey")) {
        openRouterApiKey = project.property("openRouterApiKey") as String
    }

    // Optional: custom model
    if (project.hasProperty("model")) {
        model = project.property("model") as String
    }

    // Optional: filter classes by regex
    if (project.hasProperty("classFilter")) {
        classFilter = project.property("classFilter") as String
    }
}

/**
 * Convenience task that runs the full AI stub generation pipeline.
 * Alias for enhanceWithAI with dependencies.
 */
tasks.register("generateAIStubs") {
    group = "documentation"
    description = "Generates AI-enhanced stubs (decompile + AI enhancement)"
    dependsOn(enhanceWithAI)
}

tasks.build {
    dependsOn("generateSources")
}
