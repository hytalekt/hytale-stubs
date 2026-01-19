plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kotest)
}

kotlin {
    jvmToolchain(24)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.plugin.kotlin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.vineflower)
    implementation(libs.javaparser)
    implementation(libs.classgraph)

    testImplementation(gradleTestKit())
    testImplementation(libs.bundles.kotest)
}

tasks.test {
    useJUnitPlatform()
}

configurations.all {
    resolutionStrategy {
        force(libs.javaparser)
    }
}
