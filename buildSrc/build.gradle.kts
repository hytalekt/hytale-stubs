plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlinx.serialization)
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
    implementation(libs.vineflower)
}
