import io.github.hytalekt.stubs.HytaleStubsPlugin
import io.github.hytalekt.stubs.docs
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

tasks.build {
    dependsOn("generateSources")
}
