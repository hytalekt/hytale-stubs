import io.github.hytalekt.stubs.HytaleStubsPlugin

plugins {
    java
    idea
}

repositories {
    mavenCentral()
}

apply<HytaleStubsPlugin>()
