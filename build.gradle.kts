import io.github.hytalekt.stubs.HytaleStubsPlugin
import io.github.hytalekt.stubs.docs
import io.github.hytalekt.stubs.task.GenerateSourcesTask
import io.github.hytalekt.stubs.task.GenerateAIStubsTask
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

/**
 * Generate AI-enhanced stubs using Vineflower + Gemini.
 *
 * Usage:
 *   ./gradlew generateAIStubs
 *
 * Options:
 *   -PopenRouterApiKey=YOUR_KEY   Set OpenRouter API key (or use OPENROUTER_API_KEY env var)
 *   -PdryRun=true                 Skip AI enhancement, just decompile
 *   -PclassFilter=regex           Filter classes by regex pattern
 *
 * Example:
 *   ./gradlew generateAIStubs -PopenRouterApiKey=sk-or-... -PclassFilter="com\.example\..*"
 */
tasks.register<GenerateAIStubsTask>("generateAIStubs") {
    sourceJar = File("testingdocs.jar")
    outputDirectory = File("build/gen/ai-stubs")
    cacheDirectory = File("build/cache/ai-stubs")

    // Get API key from project property or environment
    if (project.hasProperty("openRouterApiKey")) {
        openRouterApiKey = project.property("openRouterApiKey") as String
    }

    // Optional: custom model
    if (project.hasProperty("model")) {
        model = project.property("model") as String
    }

    // Optional: dry run (just decompile, skip AI)
    if (project.hasProperty("dryRun")) {
        dryRun = (project.property("dryRun") as String).toBoolean()
    }

    // Optional: filter classes by regex
    if (project.hasProperty("classFilter")) {
        classFilter = project.property("classFilter") as String
    }
}

tasks.build {
    dependsOn("generateSources")
}
