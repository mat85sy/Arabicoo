plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.recloudstream:cloudstream3:master-SNAPSHOT")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    // implementation("com.squareup.okhttp3:okhttp:4.10.0")
}

android {
    compileSdk = 33

    defaultConfig {
        minSdk = 21
        targetSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    sourceSets {
        named("main") {
            java {
                srcDir("src/main/kotlin")
            }
        }
    }
}