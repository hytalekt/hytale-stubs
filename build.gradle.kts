import io.github.hytalekt.stubs.task.DecompileJarTask
import io.github.hytalekt.stubs.task.ProcessStubsTask
import org.gradle.kotlin.dsl.register

plugins {
    java
    idea
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("com.google.code.gson:gson:2.11.0")
    compileOnly("org.mongodb:bson:5.3.1")
    compileOnly("io.netty:netty-all:4.2.9.Final")
    compileOnly("io.netty.incubator:netty-incubator-codec-classes-quic:0.0.74.Final")
    compileOnly("com.google.guava:guava:33.4.0-jre")
    compileOnly("org.slf4j:slf4j-api:2.0.16")
    compileOnly("it.unimi.dsi:fastutil:8.5.15")
    compileOnly("org.joml:joml:1.10.8")
    compileOnly("com.google.flogger:flogger:0.8")
    compileOnly("com.google.flogger:flogger-system-backend:0.8")
    compileOnly("io.sentry:sentry:8.29.0")
    compileOnly("org.checkerframework:checker-qual:3.48.3")
    compileOnly("net.sf.jopt-simple:jopt-simple:5.0.4")
    compileOnly("org.jline:jline:3.26.3")
    compileOnly("com.nimbusds:nimbus-jose-jwt:9.41.1")
    compileOnly("ch.randelshofer:fastdoubleparser:1.0.0")
    compileOnly("com.github.luben:zstd-jni:1.5.6-4")
    compileOnly("org.bouncycastle:bcprov-jdk18on:1.78.1")
    compileOnly("org.bouncycastle:bcutil-jdk18on:1.78.1")
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
    dependsOn("processStubs")
}

tasks.build {
    // dependsOn("generateSources")
}
