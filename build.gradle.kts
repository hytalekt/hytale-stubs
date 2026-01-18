import io.github.hytalekt.stubs.task.DecompileJarTask
import io.github.hytalekt.stubs.task.EnhanceWithAITask

plugins {
    java
    `maven-publish`
}

group = "com.github.hytalekt" // JitPack uses com.github.<user>
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

/**
 * Step 1: Decompile JAR using Vineflower.
 * This task is cacheable - results are stored in Gradle build cache.
 */
val decompileJar =
    tasks.register<DecompileJarTask>("decompileJar") {
        sourceJar = file("input.jar")
        outputDirectory = layout.buildDirectory.dir("cache/decompiled")
    }

/**
 * Step 2: Enhance decompiled sources with Gemini AI.
 *
 * Usage:
 *   ./gradlew generateAIStubs -PopenRouterApiKey=YOUR_KEY
 *
 * Options:
 *   -PopenRouterApiKey=KEY    OpenRouter API key (or use OPENROUTER_API_KEY env var)
 *   -PclassFilter=regex       Filter classes by regex pattern
 *   -Pmodel=model-id          Override AI model (default: google/gemini-2.5-flash-preview)
 */
val enhanceWithAI =
    tasks.register<EnhanceWithAITask>("enhanceWithAI") {
        dependsOn(decompileJar)
        decompiledSourcesDir = decompileJar.flatMap { it.outputDirectory }
        outputDirectory = layout.buildDirectory.dir("gen/ai-stubs")
        aiCacheDirectory = layout.buildDirectory.dir("cache/ai-responses")

    /*
    if (project.hasProperty("openRouterApiKey")) {
        openRouterApiKey = project.property("openRouterApiKey") as String
    }
    if (project.hasProperty("model")) {
        model = project.property("model") as String
    }
    if (project.hasProperty("classFilter")) {
        classFilter = project.property("classFilter") as String
    }

     */
    }

tasks.register("generateAIStubs") {
    group = "build"
    description = "Generates AI-enhanced stubs (decompile + AI enhancement)"
    dependsOn(enhanceWithAI)
}

tasks.compileJava {
    dependsOn(enhanceWithAI)
}

// ============================================================================
// Publishing (for JitPack - no authentication required)
// ============================================================================
//
// Users add to their build:
//
//   repositories {
//       maven { url = uri("https://jitpack.io") }
//   }
//
//   dependencies {
//       compileOnly("com.github.hytalekt:hytale-stubs:VERSION")
//   }
//
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

                scm {
                    connection = "scm:git:git://github.com/hytalekt/hytale-stubs.git"
                    url = "https://github.com/hytalekt/hytale-stubs"
                }
            }
        }
    }
}
