import io.github.hytalekt.stubs.task.DecompileJarTask
import io.github.hytalekt.stubs.task.ProcessStubsTask
import org.gradle.kotlin.dsl.register

plugins {
    `java-library`
    `maven-publish`
}

group = "io.github.hytalekt.stubs"
version = "0.1.0-alpha.1"

repositories {
    mavenCentral()
}

dependencies {
    api("com.google.code.findbugs:jsr305:3.0.2")
    api("com.google.code.gson:gson:2.11.0")
    api("org.mongodb:bson:5.3.1")
    api("io.netty:netty-all:4.2.9.Final")
    api("io.netty.incubator:netty-incubator-codec-classes-quic:0.0.74.Final")
    api("com.google.guava:guava:33.4.0-jre")
    api("org.slf4j:slf4j-api:2.0.16")
    api("it.unimi.dsi:fastutil:8.5.15")
    api("org.joml:joml:1.10.8")
    api("com.google.flogger:flogger:0.8")
    api("com.google.flogger:flogger-system-backend:0.8")
    api("io.sentry:sentry:8.29.0")
    api("org.checkerframework:checker-qual:3.48.3")
    api("net.sf.jopt-simple:jopt-simple:5.0.4")
    api("org.jline:jline:3.26.3")
    api("com.nimbusds:nimbus-jose-jwt:9.41.1")
    api("ch.randelshofer:fastdoubleparser:1.0.0")
    api("com.github.luben:zstd-jni:1.5.6-4")
    api("org.bouncycastle:bcprov-jdk18on:1.78.1")
    api("org.bouncycastle:bcutil-jdk18on:1.78.1")
}

sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("gen/sources"))
        }
    }
}

tasks.register<DecompileJarTask>("decompileJar") {
    sourceJar = layout.projectDirectory.file("input.jar")
    outputDirectory = layout.buildDirectory.dir("gen/sources")
}

tasks.register<ProcessStubsTask>("processStubs") {
    dependsOn("decompileJar")
    inputJar = layout.projectDirectory.file("input.jar")
    inputDirectory = layout.buildDirectory.dir("gen/sources")
    patchesDirectory = layout.projectDirectory.dir("patches")
}

tasks.compileJava {
    // dependsOn("processStubs")
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group as String
            artifactId = "hytale-stubs"
            version = project.version as String

            from(components["java"]) // or "kotlin" for Kotlin projects
        }
    }

    repositories {
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("maven-repo"))
        }

        maven {
            name = "hytaleStubs"
            url = uri("https://stubs.oglass.dev/repository/")
            credentials {
                username = findProperty("mavenUsername") as String? ?: System.getenv("MAVEN_USERNAME")
                password = findProperty("mavenPassword") as String? ?: System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}
