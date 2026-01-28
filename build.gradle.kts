plugins {
    id("com.codingfeline.buildkonfig") version "0.17.1" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// Task to build all extensions at once
task("buildAll") {
    dependsOn(subprojects.map { ":${it.name}:build" })
}