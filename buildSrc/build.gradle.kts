plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kotest)
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.plugin.kotlin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.classgraph)
    implementation(libs.javapoet)
    implementation(libs.asm.core)

    testImplementation(gradleTestKit())
    testImplementation(libs.bundles.kotest)
}

tasks.test {
    useJUnitPlatform()
}
