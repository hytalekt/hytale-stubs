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
    implementation(libs.classgraph)
    implementation(libs.javapoet)

    testImplementation(gradleTestKit())
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
}
