import io.github.hytalekt.stubs.task.DecompileJarTask
import io.github.hytalekt.stubs.task.EnhanceWithAITask

plugins {
    java
    `maven-publish`
}

group = "io.github.hytalekt"
version = project.findProperty("version") as String? ?: "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
    withJavadocJar()
}

sourceSets {
    main {
        java {
            srcDir("build/gen/ai-stubs")
        }
    }
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
    sourceJar = file("input.jar")  // Configure this to your input JAR
    outputDirectory = layout.buildDirectory.dir("cache/decompiled")
}

/**
 * Step 2: Enhance decompiled sources with Gemini AI.
 *
 * Usage:
 *   ./gradlew generateAIStubs
 *
 * Options:
 *   -PopenRouterApiKey=YOUR_KEY   Set OpenRouter API key (or use OPENROUTER_API_KEY env var)
 *   -PclassFilter=regex           Filter classes by regex pattern
 *   -Pmodel=model-id              Override the AI model (default: google/gemini-2.5-flash-preview)
 *
 * Example:
 *   ./gradlew generateAIStubs -PopenRouterApiKey=sk-or-... -PclassFilter="com\.example\..*"
 *
 * Caching:
 *   - Decompilation results are cached via Gradle build cache
 *   - AI responses are cached in build/cache/ai-responses (persist this in CI)
 */
val enhanceWithAI = tasks.register<EnhanceWithAITask>("enhanceWithAI") {
    dependsOn(decompileJar)
    decompiledSourcesDir = decompileJar.flatMap { it.outputDirectory }
    outputDirectory = layout.buildDirectory.dir("gen/ai-stubs")
    aiCacheDirectory = layout.buildDirectory.dir("cache/ai-responses")

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
 * Main task - generates AI-enhanced stubs from a JAR.
 */
tasks.register("generateAIStubs") {
    group = "build"
    description = "Generates AI-enhanced stubs (decompile + AI enhancement)"
    dependsOn(enhanceWithAI)
}

// Ensure stubs are generated before compiling
tasks.compileJava {
    dependsOn(enhanceWithAI)
}

// ============================================================================
// Maven Publishing to GitHub Packages
// ============================================================================

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name = "Hytale Stubs"
                description = "AI-enhanced API stubs generated from Hytale game JAR"
                url = "https://github.com/hytalekt/hytale-stubs"

                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }

                developers {
                    developer {
                        id = "hytalekt"
                        name = "Hytale Stubs Contributors"
                    }
                }

                scm {
                    connection = "scm:git:git://github.com/hytalekt/hytale-stubs.git"
                    developerConnection = "scm:git:ssh://github.com/hytalekt/hytale-stubs.git"
                    url = "https://github.com/hytalekt/hytale-stubs"
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/hytalekt/hytale-stubs")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String?
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.token") as String?
            }
        }
    }
}
